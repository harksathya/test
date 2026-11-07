package com.hark.sothudiary.domain.land

import com.hark.sothudiary.APP_USER_AGENT
import com.hark.sothudiary.data.LulcHit
import com.hark.sothudiary.domain.social.arr
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.str
import com.hark.sothudiary.httpRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToLong

/**
 * 2.3.0: ISRO Bhuvan (NRSC) land use / land cover – the SIS-DP v2 1:10,000 map per Tamil Nadu district, read with a
 * public, keyless WMS GetFeatureInfo request (bhuvan-vec1.nrsc.gov.in/bhuvan/wms). Used as a second, independent
 * source next to OpenStreetMap: what the land is at the pin (crop land, built-up, water body, forest, wasteland …).
 *
 * Checked on 2026-11-07: GetFeatureInfo with INFO_FORMAT=application/json answers for these layers without a key; WFS
 * is disabled on the server and the cadastral layer "cadastral:tn_cad" draws nothing (no survey numbers from Bhuvan).
 */
object Bhuvan {
    const val WMS = "https://bhuvan-vec1.nrsc.gov.in/bhuvan/wms"

    /** layer -> west, east, south, north (from the server's GetCapabilities). Newer districts lie inside an older one. */
    val TN_LULC_LAYERS: Map<String, DoubleArray> = mapOf(
        "Ariyalur" to doubleArrayOf(78.939, 79.514, 10.883, 11.42),
        "Chennai" to doubleArrayOf(80.183, 80.304, 12.966, 13.148),
        "Coimbatore" to doubleArrayOf(76.658, 77.503, 10.22, 11.405),
        "Cuddalore" to doubleArrayOf(78.876, 79.813, 11.149, 11.888),
        "Dharmapuri" to doubleArrayOf(77.473, 78.747, 11.772, 12.888),
        "Dindigul" to doubleArrayOf(77.268, 78.338, 10.018, 10.834),
        "Erode" to doubleArrayOf(76.837, 77.932, 10.605, 11.954),
        "Kancheepuram" to doubleArrayOf(79.56, 80.266, 12.229, 13.063),
        "Kanniyakumari" to doubleArrayOf(77.1, 77.591, 8.078, 8.579),
        "Karur" to doubleArrayOf(77.75, 78.594, 10.547, 11.206),
        "Madurai" to doubleArrayOf(77.464, 78.472, 9.563, 10.309),
        "Nagapattinam" to doubleArrayOf(79.51, 79.879, 10.274, 11.417),
        "Namakkal" to doubleArrayOf(77.683, 78.487, 11.011, 11.594),
        "Perambalur" to doubleArrayOf(78.415, 79.17, 10.947, 11.52),
        "Pudukkottai" to doubleArrayOf(78.45, 79.264, 9.847, 10.732),
        "Ramanathapuram" to doubleArrayOf(78.216, 79.427, 9.087, 9.945),
        "Salem" to doubleArrayOf(77.649, 78.845, 11.314, 11.975),
        "Sivaganga" to doubleArrayOf(78.126, 79.015, 9.519, 10.407),
        "Thanjavur" to doubleArrayOf(78.778, 79.574, 10.139, 11.181),
        "The_Nilgiris" to doubleArrayOf(76.233, 77.016, 11.189, 11.705),
        "Theni" to doubleArrayOf(77.167, 77.729, 9.519, 10.215),
        "Thiruvallur" to doubleArrayOf(79.286, 80.346, 12.946, 13.562),
        "Thiruvarur" to doubleArrayOf(79.3, 79.837, 10.3, 11.016),
        "Thoothukkudi" to doubleArrayOf(77.674, 78.388, 8.321, 9.371),
        "Tirunelveli" to doubleArrayOf(77.149, 77.976, 8.143, 9.417),
        "Tiruvannamalai" to doubleArrayOf(78.633, 79.757, 11.98, 12.872),
        "Vellore" to doubleArrayOf(78.408, 79.79, 12.258, 13.208),
        "Viluppuram" to doubleArrayOf(78.627, 80.005, 11.502, 12.449),
        "Virudhunagar" to doubleArrayOf(77.341, 78.419, 9.199, 9.785),
    ).mapKeys { "sisdpv2:TN_${it.key}_lulc_v2" }

    /** The district layers whose extent holds the point (usually one, two near a border). */
    fun layersAt(lat: Double, lng: Double): List<String> =
        TN_LULC_LAYERS.filter { (_, b) -> lng >= b[0] && lng <= b[1] && lat >= b[2] && lat <= b[3] }.keys.toList()

    private fun f6(v: Double) = ((v * 1_000_000).roundToLong() / 1_000_000.0).toString()

    /** WMS 1.3.0 GetFeatureInfo at the centre pixel of a ~100 m box around the point (EPSG:4326 axis order lat,lng). */
    fun featureInfoUrl(layers: List<String>, lat: Double, lng: Double): String {
        val d = 0.0005
        val l = layers.joinToString(",")
        return "$WMS?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS=$l&QUERY_LAYERS=$l&STYLES=&CRS=EPSG:4326" +
            "&BBOX=${f6(lat - d)},${f6(lng - d)},${f6(lat + d)},${f6(lng + d)}&WIDTH=101&HEIGHT=101&I=50&J=50" +
            "&INFO_FORMAT=application/json&FEATURE_COUNT=3&PROPERTYNAME=lc_code,dscr1,dscr2,dscr3"
    }

    /** {"features":[{"properties":{"lc_code":"AGCR","dscr1":"Agricultural land",…}}]} -> the first class, or null. */
    fun parseFeatureInfo(text: String?, lat: Double, lng: Double): LulcHit? {
        val root = parseJsonOrNull(text) ?: return null
        val f = root["features"].arr().firstOrNull() ?: return null
        val p = f["properties"]
        val code = p["lc_code"].str().orEmpty().trim()
        val l1 = p["dscr1"].str().orEmpty().trim()
        if (code.isBlank() && l1.isBlank()) return null
        return LulcHit(lat, lng, code, l1, p["dscr2"].str().orEmpty().trim(), p["dscr3"].str().orEmpty().trim())
    }

    /** Land use at each point (pin first); null when Bhuvan did not answer at all, an empty list outside the coverage. */
    suspend fun landUse(points: List<Pair<Double, Double>>): List<LulcHit>? = coroutineScope {
        var answered = false
        val hits = points.take(5).map { (la, ln) ->
            async {
                val layers = layersAt(la, ln)
                if (layers.isEmpty()) { answered = true; return@async null }
                val r = runCatching { httpRequest(featureInfoUrl(layers, la, ln), headers = mapOf("User-Agent" to APP_USER_AGENT, "Accept" to "application/json"), timeoutMs = 15_000) }.getOrNull()
                if (r != null && r.ok) { answered = true; parseFeatureInfo(r.body, la, ln) } else null
            }
        }.map { it.await() }
        if (!answered) null else hits.filterNotNull()
    }
}

/** What a Bhuvan land-use class means for the land check. */
enum class LulcKind { WATER, WETLAND, FOREST, BUILT_UP, AGRICULTURE, WASTELAND, OTHER }

fun lulcKind(h: LulcHit): LulcKind {
    val all = (h.code + " " + h.level1 + " " + h.level2 + " " + h.level3).lowercase()
    return when {
        "wetland" in all -> LulcKind.WETLAND
        "water" in all || h.code.uppercase().startsWith("WB") || "river" in all || "tank" in all || "reservoir" in all -> LulcKind.WATER
        "forest" in all || h.code.uppercase().startsWith("FO") -> LulcKind.FOREST
        "built" in all -> LulcKind.BUILT_UP
        "agri" in all || "crop" in all || "plantation" in all || "fallow" in all -> LulcKind.AGRICULTURE
        "waste" in all || "scrub" in all || "barren" in all -> LulcKind.WASTELAND
        else -> LulcKind.OTHER
    }
}

/** "Agricultural land · Crop land" – the most specific distinct names. */
fun lulcLabel(h: LulcHit): String = listOf(h.level1, h.level2, h.level3).filter { it.isNotBlank() }.distinct()
    .let { if (it.size >= 2 && it[1].contains(it[0], true)) it.drop(1) else it }.take(2).joinToString(" · ")
