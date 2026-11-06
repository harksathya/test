package com.hark.sothudiary.domain.pricing

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.OnlineRate
import com.hark.sothudiary.data.RateKind
import com.hark.sothudiary.data.RateLookup
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.GeoPlace
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.haversineKm
import com.hark.sothudiary.domain.social.arr
import com.hark.sothudiary.domain.social.at
import com.hark.sothudiary.domain.social.balancedJson
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.str
import com.hark.sothudiary.domain.urlEncode
import com.hark.sothudiary.httpRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/** Outcome of a live lookup. */
data class LiveLookupResult(
    val lookup: RateLookup,
    val rates: List<OnlineRate>,
    val error: String? = null,
)

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Cache key of a location + type: coordinates rounded to ~1 km. */
fun lookupKey(lat: Double, lng: Double, group: Int): String {
    fun r(v: Double) = (v * 100).roundToLong() / 100.0
    return "${r(lat)},${r(lng)}|$group"
}

/** A lookup for this type done within 1.5 km of the point in the last [PriceRules.CACHE_DAYS] days (reuse it). */
fun freshLookup(data: AppData, lat: Double, lng: Double, group: Int, now: Long = nowMillis()): RateLookup? =
    data.rateLookups
        .filter { it.group == group && it.error == null && now - it.fetchedAt < PriceRules.CACHE_DAYS * DAY_MS }
        .filter { haversineKm(lat, lng, it.lat, it.lng) <= 1.5 }
        .maxByOrNull { it.fetchedAt }

/** The latest lookup near the point (any age, errors included) – for showing the status. */
fun latestLookup(data: AppData, lat: Double, lng: Double, group: Int): RateLookup? =
    data.rateLookups.filter { it.group == group && haversineKm(lat, lng, it.lat, it.lng) <= 1.5 }.maxByOrNull { it.fetchedAt }

fun isFresh(fetchedAt: Long, now: Long = nowMillis()) = now - fetchedAt < PriceRules.CACHE_DAYS * DAY_MS

private fun groupWords(group: Int): String = when (group) {
    1 -> "agricultural land / farmland (rates per acre or per cent)"
    2 -> "independent houses (rate per sq ft of built-up area, or total price with the built-up area)"
    3 -> "apartments / flats (rate per sq ft)"
    else -> "residential plots / house sites / vacant land (rates per cent or per sq ft)"
}

private fun r4(v: Double) = (v * 10000).roundToLong() / 10000.0

fun describePlace(g: GeoPlace?, hint: String): String {
    if (g == null) return hint
    val parts = listOfNotNull(
        hint.takeIf { it.isNotBlank() },
        g.locality.takeIf { it.isNotBlank() && !hint.contains(it, true) }?.let { "village/locality $it" },
        g.taluk.takeIf { it.isNotBlank() }?.let { "$it taluk" },
        g.district.takeIf { it.isNotBlank() }?.let { "$it district" },
        g.state.takeIf { it.isNotBlank() },
    )
    return parts.joinToString(", ")
}

fun buildLivePrompt(placeDescription: String, lat: Double, lng: Double, group: Int): String = """
You help a home buyer in India check whether a quoted property price is fair. Use Google Search.
The property: ${groupWords(group)} at $placeDescription (coordinates ${r4(lat)}, ${r4(lng)}).
Find CURRENT prices for the SAME property type ONLY, at places within 10 km of these coordinates (closer is better):
1. Asking prices from recent listings (99acres, MagicBricks, Housing.com, NoBroker, OLX, local broker sites, real-estate posts quoted on web pages).
2. The state government guideline value for the same villages, if you can find it.
Rules: never invent or estimate numbers – give only figures you found, each with the page it is on. Do not use city-wide or district-wide averages. Do not include places more than 10 km away. If nothing is found return an empty list.
Convert lakh / crore to plain rupees (2.5 lakh = 250000). Give a rate per unit, not a total: divide a total price by its size when the listing gives both.
For each comparable give the locality (village / area name as on the page) and its approximate coordinates if you know them.
Reply with ONLY this JSON (no other text):
{"comparables":[{"locality":"village or area","district":"district","lat":number or null,"lng":number or null,"kind":"asking or guideline","unit":"cent or sqft or acre","min":number or null,"max":number or null,"typical":number or null,"source_name":"site name","source_url":"https://...","date":"date shown on the page","note":"very short, e.g. 3 listings, 5 cent plots"}]}
""".trimIndent()

/** Pulls the {"comparables":[…]} (or older {"rates":[…]}) JSON out of the model's text. */
fun parseComparablesJson(text: String): List<JsonObject> {
    val fenced = Regex("""```(?:json)?\s*(\{.*?\})\s*```""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
    val start = listOf(text.indexOf("{\"comparables\""), text.indexOf("{\"rates\""), text.indexOf('{')).firstOrNull { it >= 0 } ?: return emptyList()
    val raw = fenced ?: balancedJson(text, start) ?: return emptyList()
    val obj = parseJsonOrNull(raw)
    return (obj["comparables"].arr().ifEmpty { obj["rates"].arr() }).mapNotNull { it as? JsonObject }
}

private fun JsonObject.d(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.replace(",", "")?.toDoubleOrNull()
private fun JsonObject.s(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().let { if (it == "null") "" else it }

/**
 * Live price lookup for a location + type (always for the property's own coordinates):
 * reverse-geocodes the point, asks Gemini with Google Search grounding for same-type comparables within 10 km,
 * places every comparable on the map (Nominatim near the point; the model's coordinates only as a fallback),
 * and keeps only those within [PriceRules.MAX_KM] km by computed distance.
 */
suspend fun liveLookup(
    apiKey: String,
    model: String,
    lat: Double,
    lng: Double,
    group: Int,
    placeHint: String,
): LiveLookupResult {
    val key = lookupKey(lat, lng, group)
    val now = nowMillis()
    if (apiKey.isBlank()) return LiveLookupResult(RateLookup(key, lat, lng, group, placeHint, now, error = "no_key"), emptyList(), "no_key")
    val rev = runCatching { Geocoder.reverse(lat, lng) }.getOrNull()
    val place = describePlace(rev, placeHint)
    val prompt = buildLivePrompt(place, lat, lng, group)
    val body = buildJsonObject {
        put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) })
        put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject { }) }) })
        put("generationConfig", buildJsonObject { put("temperature", 0.1) })
    }.toString()
    val models = listOf(model.ifBlank { "gemini-2.5-flash" }, "gemini-2.5-flash", "gemini-2.0-flash").distinct()
    var lastError: String? = null
    for (m in models) {
        val r = httpRequest(
            "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=" + urlEncode(apiKey.trim()),
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = body,
            timeoutMs = 90_000,
        )
        if (r == null) { lastError = "network"; break }
        if (!r.ok) {
            val msg = parseJsonOrNull(r.body).at("error", "message").str() ?: "HTTP ${r.code}"
            lastError = msg
            if (r.code == 404 || msg.contains("not found", true) || msg.contains("not supported", true)) continue
            break
        }
        val root = parseJsonOrNull(r.body)
        val cand = root.at("candidates", 0)
        val text = cand.at("content", "parts").arr().mapNotNull { it["text"].str() }.joinToString("\n")
        val sources = cand.at("groundingMetadata", "groundingChunks").arr().mapNotNull { c -> c.at("web", "uri").str() }
        val out = mutableListOf<OnlineRate>()
        var skipped = 0
        for (o in parseComparablesJson(text)) {
            val name = o.s("locality").ifBlank { o.s("place") }
            val mn = o.d("min")?.takeIf { it > 0 }; val mx = o.d("max")?.takeIf { it > 0 }; val ty = o.d("typical")?.takeIf { it > 0 }
            if (name.isBlank() || (mn == null && mx == null && ty == null)) { skipped++; continue }
            val unitRaw = o.s("unit").lowercase()
            val unit = when { "acre" in unitRaw -> "acre"; "sq" in unitRaw || "ft" in unitRaw -> "sqft"; else -> "cent" }
            val district = o.s("district").ifBlank { rev?.district.orEmpty() }
            // Place it: Nominatim near the point first (names are reliable, model coordinates often are not).
            val g = runCatching {
                Geocoder.search(listOf(name, district).filter { it.isNotBlank() }.joinToString(", "), limit = 5, nearLat = lat, nearLng = lng)
            }.getOrDefault(emptyList()).minByOrNull { haversineKm(lat, lng, it.lat, it.lng) }
            var pLat = g?.lat
            var pLng = g?.lng
            if (pLat == null || pLng == null) {
                val mLat = o.d("lat"); val mLng = o.d("lng")
                if (mLat != null && mLng != null && mLat in 6.0..37.5 && mLng in 68.0..97.5) { pLat = mLat; pLng = mLng }
            }
            if (pLat == null || pLng == null) { skipped++; continue }
            val dist = haversineKm(lat, lng, pLat, pLng)
            if (dist > PriceRules.MAX_KM) { skipped++; continue }
            val url = o.s("source_url").takeIf { it.startsWith("http") } ?: sources.firstOrNull().orEmpty()
            out += OnlineRate(
                id = newId(), place = name, district = district, lat = pLat, lng = pLng, group = group,
                kind = if (o.s("kind").contains("guide", true)) RateKind.GUIDELINE else RateKind.ASKING,
                unit = unit, min = mn, max = mx, typical = ty,
                sourceName = o.s("source_name"), sourceUrl = url, asOf = o.s("date"),
                fetchedAt = now, note = o.s("note").take(120), lookupKey = key, distanceKm = dist,
            )
        }
        val lookup = RateLookup(key, lat, lng, group, place, now, out.size, skipped, m, null, sources.take(12))
        return LiveLookupResult(lookup, out)
    }
    val err = lastError ?: "failed"
    return LiveLookupResult(RateLookup(key, lat, lng, group, place, now, model = models.first(), error = err), emptyList(), err)
}
