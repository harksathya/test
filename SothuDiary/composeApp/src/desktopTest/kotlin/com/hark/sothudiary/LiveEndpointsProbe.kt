package com.hark.sothudiary

import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.ai.LlmProviders
import com.hark.sothudiary.domain.ai.listModels
import com.hark.sothudiary.domain.land.OVERPASS_ENDPOINTS
import com.hark.sothudiary.domain.land.buildLayerQueries
import com.hark.sothudiary.domain.land.fetchElevations
import com.hark.sothudiary.domain.land.fetchGroup
import com.hark.sothudiary.domain.land.groupQueries
import com.hark.sothudiary.domain.registry.DutyRules
import com.hark.sothudiary.domain.web.WebSearch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

/**
 * 2.6.0: a live check of every outside service the Land / Guideline / Price checks use, through the app's own code
 * (same requests, User-Agent and parsing as on the phone). It only prints what it finds – it never fails the build,
 * since a free server being busy for a minute is not a bug – and it runs only on CI (env CI=true) or with
 * SOTHU_LIVE=1, so local test runs stay offline. Read the "LIVE PROBE" lines in the CI log.
 */
class LiveEndpointsProbe {
    private fun out(s: String) = println("LIVE PROBE | $s")

    @Test
    fun probe() {
        if (System.getenv("CI") != "true" && System.getenv("SOTHU_LIVE") != "1") return
        runBlocking {
            withTimeoutOrNull(480_000) { runAll() } ?: out("stopped after 480 s")
        }
    }

    private suspend fun runAll() {
        // A village pin near Thirumangalam, Madurai.
        val lat = 9.8236; val lng = 77.9870
        val layers = buildLayerQueries(lat, lng)
        val groups = groupQueries(layers)
        val st = httpCall("https://overpass-api.de/api/status", headers = mapOf("User-Agent" to APP_USER_AGENT), timeoutMs = 15_000)
        out("overpass-api.de /api/status: HTTP ${st.code} ${st.error ?: ""} ${st.body.orEmpty().replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").take(160)}")
        for ((g, q) in groups) for ((i, url) in OVERPASS_ENDPOINTS.withIndex()) {
            val host = url.substringAfter("://").substringBefore('/')
            val (res, lines) = fetchGroup(q, i, g, budgetMs = 45_000, endpoints = listOf(url))
            out("overpass $g $host: " + (res.error ?: "${res.elements.size} elements") + " | " + lines.joinToString("; ") { "${it.reason} ${it.ms} ms" })
            // A group that failed on a server that did not refuse us: which of its layers is the slow one?
            if (res.error != null && lines.none { "406" in it.reason }) for (layer in com.hark.sothudiary.domain.land.OsmGroup.layers[g].orEmpty()) {
                val lq = layers[layer] ?: continue
                val (lr, ll) = fetchGroup(lq, 0, layer, budgetMs = 30_000, endpoints = listOf(url))
                out("   layer $layer on $host: " + (lr.error ?: "${lr.elements.size} elements") + " | " + ll.joinToString("; ") { "${it.reason} ${it.ms} ms" })
            }
        }
        val elev = runCatching { fetchElevations(listOf(lat to lng, lat + 0.003 to lng)) }.getOrNull()
        out("open-meteo elevation: ${elev ?: "FAILED"}")
        val rev = runCatching { Geocoder.reverse(lat, lng) }.getOrNull()
        out("nominatim reverse: ${rev?.let { listOf(it.locality, it.taluk, it.district, it.state).joinToString(", ") } ?: "FAILED"}")
        for (q in listOf("Thirumangalam Madurai plot for sale price per cent", "Madurai guideline value 2026")) {
            val (hits, lines) = WebSearch.searchWithStatus(q)
            out("web search '$q': ${hits.size} hits | " + lines.joinToString("; ") { "${it.source}: ${it.reason} ${it.ms} ms" })
            hits.take(3).forEach { out("   ${it.url}") }
        }
        val t = httpCall(DutyRules.TNREGINET_GUIDELINE_URL, headers = mapOf("User-Agent" to com.hark.sothudiary.domain.BROWSER_USER_AGENT), timeoutMs = 40_000)
        val body = t.body.orEmpty()
        val ids = listOf("districtList", "SROList", "villageList", "streetName", "surveyName", "surveyRadio")
        out("tnreginet guideline page: HTTP ${t.code} ${t.ms} ms ${t.error ?: ""} size ${body.length} | " +
            ids.joinToString(" ") { "$it=" + ("id=\"$it\"" in body || "id='$it'" in body) })
        for (p in LlmProviders.all) {
            val m = runCatching { listModels(p, "invalid-key-for-probe") }.getOrNull()
            out("llm ${p.id}: " + when { m == null -> "NO ANSWER (network)"; m.isEmpty() -> "reachable (key refused, as expected)"; else -> "${m.size} models listed" })
        }
    }
}
