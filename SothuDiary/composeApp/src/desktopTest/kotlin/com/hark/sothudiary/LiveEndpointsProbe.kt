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
            withTimeoutOrNull(300_000) { runAll() } ?: out("stopped after 300 s")
        }
    }

    private suspend fun runAll() {
        // A village pin near Thirumangalam, Madurai.
        val lat = 9.8236; val lng = 77.9870
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
        val layers = buildLayerQueries(lat, lng)
        val groups = groupQueries(layers)
        val st = httpCall("https://overpass-api.de/api/status", headers = mapOf("User-Agent" to APP_USER_AGENT), timeoutMs = 15_000)
        out("overpass-api.de /api/status: HTTP ${st.code} ${st.error ?: ""} ${st.body.orEmpty().replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").take(160)}")
        // 2.7.0: the OpenFreeMap vector-tile source for water / roads / land use on its own.
        val tt = System.currentTimeMillis()
        val (tiles, tileLines) = com.hark.sothudiary.domain.land.fetchTileCore(lat, lng, 1000)
        out("openfreemap tiles (${(System.currentTimeMillis() - tt) / 1000} s): " +
            tiles.entries.joinToString(" ") { (l, r) -> "$l=" + (r.error ?: "${r.elements.size}") } + " | " +
            tileLines.joinToString("; ") { "${it.reason} ${it.ms} ms" })
        tiles.values.flatMap { it.elements }.mapNotNull { e -> e.tags["name"]?.let { n -> (e.tags["highway"] ?: e.tags["water"] ?: e.tags["waterway"] ?: e.tags["landuse"] ?: "") + ":" + n } }
            .distinct().take(8).forEach { out("   $it") }
        // 3.0.2: the EXTRA layers (amenities, rail, protected areas / forest) from the same tiles.
        val te = System.currentTimeMillis()
        val (extra, extraLines) = com.hark.sothudiary.domain.land.fetchTileExtra(lat, lng)
        out("openfreemap extra tiles (${(System.currentTimeMillis() - te) / 1000} s): " +
            extra.entries.joinToString(" ") { (l, r) -> "$l=" + (r.error ?: "${r.elements.size}") } + " | " +
            extraLines.joinToString("; ") { "${it.reason} ${it.ms} ms" })
        extra.values.flatMap { it.elements }.mapNotNull { e -> e.tags["name"]?.let { n -> (e.tags["amenity"] ?: e.tags["railway"] ?: e.tags["boundary"] ?: e.tags["highway"] ?: e.tags["shop"] ?: "") + ":" + n } }
            .distinct().take(8).forEach { out("   $it") }
        // The land check's own call: both groups raced across all servers, with the app's budget and retries,
        // and (2.7.0) the map tiles as the second source for the core layers – exactly as on the phone.
        val t0 = System.currentTimeMillis()
        val all = com.hark.sothudiary.domain.land.fetchOsmLayers(layers, coreFallback = { com.hark.sothudiary.domain.land.fetchTileCore(lat, lng, 1000) },
            extraFallback = { com.hark.sothudiary.domain.land.fetchTileExtra(lat, lng) })
        out("land check map data (app path, ${(System.currentTimeMillis() - t0) / 1000} s): ok=${all.okLayers} failed=${all.failed.keys} elements=${all.elements.size} | " +
            all.attempts.joinToString("; ") { "${it.source.substringAfter("· ")}: ${it.reason} ${it.ms} ms" })
        // overpass-api.de blocks some cloud networks outright (status page 406 too): then its tries are skipped here.
        val blocked = if (st.code == 406) setOf(OVERPASS_ENDPOINTS.first()) else emptySet()
        for ((g, q) in groups) for ((i, url) in OVERPASS_ENDPOINTS.withIndex()) {
            if (url in blocked) continue
            val host = url.substringAfter("://").substringBefore('/')
            val (res, lines) = fetchGroup(q, i, g, budgetMs = 60_000, endpoints = listOf(url))
            out("overpass $g $host: " + (res.error ?: "${res.elements.size} elements") + " | " + lines.joinToString("; ") { "${it.reason} ${it.ms} ms" })
        }
    }
}
