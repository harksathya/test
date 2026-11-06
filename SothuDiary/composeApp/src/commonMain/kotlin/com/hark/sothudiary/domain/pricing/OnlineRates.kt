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

// ---------------- Gemini errors ----------------

/** What kind of failure a live lookup hit – drives the short, friendly message shown to the user. */
enum class LookupErrorKind { QUOTA, BAD_KEY, NETWORK, MODEL_UNAVAILABLE, NO_KEY, OTHER }

/** A failed Gemini call: HTTP code, google.rpc status, the API's own message and the suggested wait (seconds). */
data class GeminiError(val code: Int, val status: String, val message: String, val retryDelaySec: Int?)

/** Parses a Gemini error response (also when the body is not JSON). */
fun parseGeminiError(code: Int, body: String?): GeminiError {
    val root = parseJsonOrNull(body ?: "")
    val msg = root.at("error", "message").str() ?: "HTTP $code"
    val status = root.at("error", "status").str().orEmpty()
    val fromDetails = root.at("error", "details").arr().firstNotNullOfOrNull { it["retryDelay"].str() }
    return GeminiError(code, status, msg, parseRetryDelay(fromDetails) ?: parseRetryDelay(msg))
}

/** "37s", "37.5s" or "… Please retry in 37.12s." -> 38 (seconds, rounded up). */
fun parseRetryDelay(text: String?): Int? {
    if (text.isNullOrBlank()) return null
    val t = text.trim()
    val scope = when {
        Regex("""^\d+(?:\.\d+)?s$""").matches(t) -> t
        t.contains("retry", true) -> t.substring(t.lowercase().indexOf("retry"))
        else -> return null
    }
    val m = Regex("""(\d+(?:\.\d+)?)\s*s\b""").find(scope) ?: return null
    val v = m.groupValues[1].toDoubleOrNull() ?: return null
    return kotlin.math.ceil(v).toInt().takeIf { it in 1..(48 * 3600) }
}

fun isQuotaError(code: Int, status: String, message: String): Boolean =
    code == 429 || status.equals("RESOURCE_EXHAUSTED", true) || message.contains("quota", true) ||
        message.contains("rate limit", true) || message.contains("resource_exhausted", true) || message.contains("resource has been exhausted", true)

fun isBadKeyError(code: Int, status: String, message: String): Boolean =
    message.contains("API key not valid", true) || message.contains("API_KEY_INVALID", true) || message.contains("API key expired", true) ||
        message.contains("invalid api key", true) || (code == 403 && !message.contains("model", true)) || status.equals("UNAUTHENTICATED", true)

fun isModelUnavailableError(code: Int, message: String): Boolean =
    code == 404 || (code == 400 && message.contains("model", true)) || message.contains("not found", true) ||
        message.contains("not supported", true) || message.contains("no longer", true) || message.contains("deprecated", true) ||
        message.contains("is not available", true)

/** Errors are stored as "[kind] raw API text" so the UI can show a short message and keep the raw text under "Details". */
fun taggedError(kind: LookupErrorKind, raw: String): String = "[" + kind.name.lowercase() + "] " + raw

/** The raw API text of a stored error (without its tag). */
fun rawErrorText(stored: String): String = Regex("""^\[[a-z_]+]\s*""").replace(stored, "")

/** Classifies a stored lookup error (tagged by this version, or a plain message saved by older versions). */
fun classifyLookupError(stored: String?): LookupErrorKind {
    if (stored.isNullOrBlank()) return LookupErrorKind.OTHER
    Regex("""^\[([a-z_]+)]""").find(stored)?.groupValues?.get(1)?.let { tag ->
        LookupErrorKind.entries.firstOrNull { it.name.lowercase() == tag }?.let { return it }
    }
    val s = stored.trim()
    return when {
        s == "no_key" -> LookupErrorKind.NO_KEY
        s == "network" || s.contains("timeout", true) || s.contains("unable to resolve host", true) -> LookupErrorKind.NETWORK
        isQuotaError(0, "", s) -> LookupErrorKind.QUOTA
        isBadKeyError(0, "", s) -> LookupErrorKind.BAD_KEY
        isModelUnavailableError(0, s) -> LookupErrorKind.MODEL_UNAVAILABLE
        else -> LookupErrorKind.OTHER
    }
}

private const val QUOTA_MEMORY_MS = 60L * 60 * 1000

/** Models that hit their (free-tier) quota recently, per key: model -> blocked until (epoch ms). Session memory. */
private val exhaustedUntil = mutableMapOf<String, Long>()

private fun quotaKey(apiKey: String, model: String) = apiKey.trim().takeLast(8) + "|" + model

internal fun markExhausted(apiKey: String, model: String, now: Long, retryDelaySec: Int?) {
    val wait = maxOf(QUOTA_MEMORY_MS, (retryDelaySec ?: 0) * 1000L)
    exhaustedUntil[quotaKey(apiKey, model)] = now + wait
}

internal fun exhaustedAt(apiKey: String, model: String, now: Long): Long? =
    exhaustedUntil[quotaKey(apiKey, model)]?.takeIf { it > now }

internal fun clearQuotaMemory() = exhaustedUntil.clear()

private sealed class CallResult {
    class Ok(val text: String, val sources: List<String>) : CallResult()
    class Fail(val e: GeminiError) : CallResult()
    object Offline : CallResult()
}

private suspend fun callGemini(apiKey: String, model: String, body: String): CallResult {
    val r = httpRequest(
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=" + urlEncode(apiKey.trim()),
        method = "POST",
        headers = mapOf("Content-Type" to "application/json"),
        body = body,
        timeoutMs = 90_000,
    ) ?: return CallResult.Offline
    if (!r.ok) return CallResult.Fail(parseGeminiError(r.code, r.body))
    val cand = parseJsonOrNull(r.body).at("candidates", 0)
    val text = cand.at("content", "parts").arr().mapNotNull { it["text"].str() }.joinToString("\n")
    val sources = cand.at("groundingMetadata", "groundingChunks").arr().mapNotNull { c -> c.at("web", "uri").str() }
    return CallResult.Ok(text, sources)
}

private fun requestBody(prompt: String, legacyRetrieval: Boolean): String = buildJsonObject {
    put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) })
    // Grounding is required: without Google Search the model would invent rates. Older (1.x) models only take the
    // "google_search_retrieval" form of the tool.
    put("tools", buildJsonArray { add(buildJsonObject { put(if (legacyRetrieval) "google_search_retrieval" else "google_search", buildJsonObject { }) }) })
    put("generationConfig", buildJsonObject { put("temperature", 0.1) })
}.toString()

/** Models that still take the older "google_search_retrieval" grounding tool. */
internal fun legacyRetrievalModels(ids: List<String>): List<String> =
    ids.filter { it.startsWith("gemini-1.5-flash") || it.startsWith("gemini-1.0") || it.startsWith("gemini-1.5-pro") }
        .sortedBy { if ("flash" in it) 0 else 1 }

/**
 * Live price lookup for a location + type (always for the property's own coordinates):
 * reverse-geocodes the point, asks Gemini with Google Search grounding for same-type comparables within 10 km,
 * places every comparable on the map (Nominatim near the point; the model's coordinates only as a fallback),
 * and keeps only those within [PriceRules.MAX_KM] km by computed distance.
 *
 * Models: the user's chosen model (if any), then the last one that worked, then what the key can use – stable Flash,
 * then Flash-Lite, then previews. A model out of (free-tier) quota is skipped and remembered for about an hour, so a
 * Refresh does not hammer it. Every call is grounded with Google Search; there is never an ungrounded call.
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
    val body = requestBody(prompt, legacyRetrieval = false)
    val models = mutableListOf<String>()
    com.hark.sothudiary.data.normalizeGeminiModel(model).takeIf { it != "auto" }?.let { models += it }
    cachedBestModel?.let { if (it !in models) models += it }
    var listed = false
    var available: List<String> = emptyList()
    var lastError: GeminiError? = null
    var offline = false
    var quotaHits = 0
    var retryDelay: Int? = null
    val skippedForQuota = mutableListOf<String>()
    var i = 0

    suspend fun attempt(m: String, legacy: Boolean): LiveLookupResult? {
        when (val res = callGemini(apiKey, m, if (legacy) requestBody(prompt, true) else body)) {
            is CallResult.Offline -> { offline = true; return null }
            is CallResult.Fail -> {
                val e = res.e
                lastError = e
                if (isQuotaError(e.code, e.status, e.message)) {
                    quotaHits++
                    if (e.retryDelaySec != null) retryDelay = maxOf(retryDelay ?: 0, e.retryDelaySec)
                    markExhausted(apiKey, m, now, e.retryDelaySec)
                    return null
                }
                // The error often names the replacement model ("use models/gemini-x-flash").
                if (!legacy) Regex("models/([a-z0-9.\\-]+)").findAll(e.message).map { it.groupValues[1].trimEnd('.') }
                    .filter { it != m && it !in models }.forEach { models.add(i, it) }
                return null
            }
            is CallResult.Ok -> {
                cachedBestModel = m
                val (out, skipped) = placeComparables(res.text, res.sources, lat, lng, group, rev, key, now)
                val label = if (legacy) "$m (search retrieval)" else m
                return LiveLookupResult(RateLookup(key, lat, lng, group, place, now, out.size, skipped, label, null, res.sources.take(12)), out)
            }
        }
    }

    while (true) {
        if (i >= models.size) {
            if (listed) break
            // Models retire often: ask the API which ones this key can use.
            listed = true
            val avail = listGeminiModels(apiKey)
            if (avail == null) { offline = true; break }
            available = avail
            pickModels(avail).forEach { if (it !in models) models += it }
            if (i >= models.size) break
        }
        val m = models[i++]
        if (exhaustedAt(apiKey, m, now) != null) { skippedForQuota += m; continue }
        attempt(m, legacy = false)?.let { return it }
        if (offline) break
        val e = lastError
        if (e != null && !isQuotaError(e.code, e.status, e.message) && !isModelUnavailableError(e.code, e.message)) break
    }
    // Every model is still blocked from an earlier quota error: try the one that frees up first, once (a manual Refresh).
    if (!offline && quotaHits == 0 && lastError == null && skippedForQuota.isNotEmpty()) {
        val m = skippedForQuota.minByOrNull { exhaustedAt(apiKey, it, now) ?: 0L }!!
        attempt(m, legacy = false)?.let { return it }
    }
    // All quota-limited with google_search: older models take the "google_search_retrieval" tool (still grounded).
    if (!offline && quotaHits > 0) {
        legacyRetrievalModels(available).firstOrNull { exhaustedAt(apiKey, it, now) == null }?.let { m ->
            attempt(m, legacy = true)?.let { return it }
        }
    }
    val e = lastError
    val err = when {
        offline && e == null -> "network"
        e == null && skippedForQuota.isNotEmpty() -> taggedError(LookupErrorKind.QUOTA, "All Gemini models for this key are out of free quota for now.")
        e == null -> "failed"
        isQuotaError(e.code, e.status, e.message) || quotaHits > 0 -> taggedError(LookupErrorKind.QUOTA, e.message)
        isBadKeyError(e.code, e.status, e.message) -> taggedError(LookupErrorKind.BAD_KEY, e.message)
        isModelUnavailableError(e.code, e.message) -> taggedError(LookupErrorKind.MODEL_UNAVAILABLE, e.message)
        else -> taggedError(LookupErrorKind.OTHER, e.message)
    }
    val retryAt = when {
        retryDelay != null -> now + retryDelay!! * 1000L
        else -> 0L
    }
    return LiveLookupResult(
        RateLookup(key, lat, lng, group, place, now, model = models.firstOrNull() ?: "auto", error = err, retryAt = retryAt),
        emptyList(), err,
    )
}

/** Turns the model's JSON into comparables placed on the map within 10 km. Returns (kept, dropped count). */
private suspend fun placeComparables(
    text: String, sources: List<String>, lat: Double, lng: Double, group: Int, rev: GeoPlace?, key: String, now: Long,
): Pair<List<OnlineRate>, Int> {
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
    return out to skipped
}

/** The model that last worked in this app session (models retire, so never hard-code one). */
private var cachedBestModel: String? = null

/** Model ids this key can call with generateContent, or null on a network failure. */
private suspend fun listGeminiModels(apiKey: String): List<String>? {
    val r = httpRequest(
        "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=" + urlEncode(apiKey.trim()),
        method = "GET", headers = emptyMap(), body = null, timeoutMs = 30_000,
    ) ?: return null
    if (!r.ok) return emptyList()
    return parseJsonOrNull(r.body).at("models").arr()
        .filter { m -> m.at("supportedGenerationMethods").arr().any { it.str() == "generateContent" } }
        .mapNotNull { it.at("name").str()?.removePrefix("models/") }
}

/**
 * Order to try models in, for a free AI Studio key: stable Flash first (the "gemini-flash-latest" alias before
 * versioned ids, newest version first), then stable Flash-Lite, then preview / experimental Flash and Flash-Lite.
 * Pro models (little or no free quota) only when nothing else is listed. Skips image/tts/live/embedding variants.
 */
internal fun pickModels(ids: List<String>): List<String> {
    val bad = listOf("image", "tts", "audio", "live", "embed", "vision", "thinking", "learnlm", "gemma", "aqa", "robotics", "computer", "native")
    fun version(id: String): Double = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    fun isPreview(id: String) = "preview" in id || "exp" in id
    val usable = ids.filter { id -> id.startsWith("gemini-") && bad.none { it in id } }
    fun rank(id: String): Int {
        val lite = "lite" in id
        val flash = "flash" in id
        return when {
            flash && !lite && !isPreview(id) -> 0
            flash && lite && !isPreview(id) -> 1
            flash && !lite -> 2
            flash -> 3
            "pro" in id && !isPreview(id) -> 4
            else -> 5
        }
    }
    val sorted = usable.sortedWith(
        compareBy<String>({ rank(it) }, { if (it.endsWith("-latest")) 0 else 1 }, { -version(it) }, { it.length })
    )
    val flashes = sorted.filter { rank(it) <= 3 }
    return (if (flashes.isNotEmpty()) flashes else sorted).take(6)
}
