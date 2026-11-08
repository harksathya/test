package com.hark.sothudiary.domain.pricing

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.OnlineRate
import com.hark.sothudiary.data.RateKind
import com.hark.sothudiary.data.RateLookup
import com.hark.sothudiary.data.WebSnippet
import com.hark.sothudiary.data.normalizeGeminiModel
import com.hark.sothudiary.domain.web.WebSearch
import com.hark.sothudiary.domain.ai.ApiStyle
import com.hark.sothudiary.domain.ai.ChatResult
import com.hark.sothudiary.domain.ai.LlmConfig
import com.hark.sothudiary.domain.ai.LlmProviders
import com.hark.sothudiary.domain.ai.chat
import com.hark.sothudiary.domain.ai.listModels
import com.hark.sothudiary.domain.ai.normalizeModel
import com.hark.sothudiary.domain.ai.rankChatModels
import com.hark.sothudiary.domain.web.citesResult
import com.hark.sothudiary.domain.web.promptWithResults
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

/**
 * A lookup for this type done within 1.5 km of the point in the last [PriceRules.CACHE_DAYS] days (reuse it).
 */
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

/**
 * A failed Gemini call: HTTP code, google.rpc status, the API's own message and the suggested wait (seconds).
 * 2.3.0: [quotaId] / [quotaValue] from the QuotaFailure details (e.g. "GenerateRequestsPerDayPerProjectPerModel-FreeTier", "20").
 */
data class GeminiError(val code: Int, val status: String, val message: String, val retryDelaySec: Int?, val quotaId: String = "", val quotaValue: String = "")

/** Parses a Gemini error response (also when the body is not JSON). */
fun parseGeminiError(code: Int, body: String?): GeminiError {
    val root = parseJsonOrNull(body ?: "")
    val msg = root.at("error", "message").str() ?: "HTTP $code"
    val status = root.at("error", "status").str().orEmpty()
    val details = root.at("error", "details").arr()
    val fromDetails = details.firstNotNullOfOrNull { it["retryDelay"].str() }
    val violation = details.flatMap { it["violations"].arr() }.firstOrNull()
    val quotaValue = violation?.get("quotaValue")?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    return GeminiError(code, status, msg, parseRetryDelay(fromDetails) ?: parseRetryDelay(msg), violation["quotaId"].str().orEmpty(), quotaValue)
}

/** 2.3.0: which quota a 429 hit – decides how long the model is skipped. */
enum class QuotaWindow {
    /** Requests per minute: back after the retry delay. */
    MINUTE,
    /** Requests per day: back after midnight Pacific time. */
    DAY,
    /** "limit: 0" – this model (or Google Search grounding on it) is not in the key's tier at all. */
    NOT_IN_TIER,
    UNKNOWN,
}

fun quotaWindow(e: GeminiError): QuotaWindow = when {
    e.quotaValue == "0" || Regex("""limit:\s*0\b""").containsMatchIn(e.message) -> QuotaWindow.NOT_IN_TIER
    e.quotaId.contains("PerDay", true) || e.message.contains("per day", true) -> QuotaWindow.DAY
    e.quotaId.contains("PerMinute", true) || e.message.contains("per minute", true) -> QuotaWindow.MINUTE
    else -> QuotaWindow.UNKNOWN
}

/** Free-tier daily quotas reset at midnight Pacific time; 08:00 UTC is that midnight in winter (and 1 am in summer). */
fun nextPacificMidnight(now: Long): Long {
    val day = 24L * 60 * 60 * 1000
    val offset = 8L * 60 * 60 * 1000
    return ((now - offset) / day + 1) * day + offset
}

/** How long to skip a model after a quota error. */
fun quotaSkipUntil(e: GeminiError, now: Long): Long = when (quotaWindow(e)) {
    QuotaWindow.MINUTE -> now + maxOf(60, e.retryDelaySec ?: 60) * 1000L
    QuotaWindow.DAY -> nextPacificMidnight(now)
    QuotaWindow.NOT_IN_TIER -> now + 24L * 60 * 60 * 1000
    QuotaWindow.UNKNOWN -> now + maxOf(QUOTA_MEMORY_MS, (e.retryDelaySec ?: 0) * 1000L)
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
        message.contains("invalid api key", true) || message.contains("invalid x-api-key", true) || message.contains("incorrect api key", true) ||
        code == 401 || (code == 403 && !message.contains("model", true)) || status.equals("UNAUTHENTICATED", true)

fun isModelUnavailableError(code: Int, message: String): Boolean =
    code == 404 || (code == 400 && message.contains("model", true)) || message.contains("not found", true) ||
        message.contains("not supported", true) || message.contains("no longer", true) || message.contains("deprecated", true) ||
        message.contains("is not available", true) || message.contains("does not exist", true) || message.contains("model_not_found", true)

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

/** [mode]: "g" grounded with Google Search, "p" plain generation (2.3.0) – separate quotas on the free tier. */
private fun quotaKey(apiKey: String, model: String, mode: String = "g") = apiKey.trim().takeLast(8) + "|" + model + (if (mode == "g") "" else "|$mode")

internal fun markExhausted(apiKey: String, model: String, now: Long, retryDelaySec: Int?, mode: String = "g", until: Long? = null) {
    val wait = maxOf(QUOTA_MEMORY_MS, (retryDelaySec ?: 0) * 1000L)
    exhaustedUntil[quotaKey(apiKey, model, mode)] = until ?: (now + wait)
}

internal fun exhaustedAt(apiKey: String, model: String, now: Long, mode: String = "g"): Long? =
    exhaustedUntil[quotaKey(apiKey, model, mode)]?.takeIf { it > now }

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
        // 2.4.0: 40 s per call (was 90 s): with up to three models tried, a slow answer no longer holds the check for minutes.
        timeoutMs = 40_000,
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

/** 2.3.0: an ordinary generation request (no tools) – used with our own web search results in the prompt. */
private fun plainBody(prompt: String): String = buildJsonObject {
    put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) }) }) })
    put("generationConfig", buildJsonObject { put("temperature", 0.1) })
}.toString()

/**
 * 2.3.0: model order for GROUNDED calls. Google Search grounding is free (500 requests a day, shared) only on the
 * Gemini 2.5 Flash / Flash-Lite models; on Gemini 3 and newer it is "not available" on the free tier and every call
 * fails with 429 "limit: 0" – which is why the price check always said "quota exhausted" on free keys (Auto picked
 * the newest Flash first). So 2.5 Flash and Flash-Lite go first, then the normal order ([pickModels]).
 */
internal fun groundedModelOrder(ids: List<String>): List<String> {
    val free = ids.filter { Regex("""^gemini-2\.5-flash(-lite)?(-\d{3})?$""").matches(it) }
        .sortedWith(compareBy({ if ("lite" in it) 1 else 0 }, { it.length }))
    return (free + pickModels(ids)).distinct().take(6)
}

/** At most this many grounded calls per lookup (each failed call can still count against a free key's quota). */
const val MAX_GROUNDED_CALLS = 3
const val MAX_PLAIN_CALLS = 3

/** Models that still take the older "google_search_retrieval" grounding tool. */
internal fun legacyRetrievalModels(ids: List<String>): List<String> =
    ids.filter { it.startsWith("gemini-1.5-flash") || it.startsWith("gemini-1.0") || it.startsWith("gemini-1.5-pro") }
        .sortedBy { if ("flash" in it) 0 else 1 }

/** Outcome of a grounded Gemini call ([groundedGenerate]). */
sealed class Grounded {
    /**
     * [model] is the model that answered ("… (search retrieval)" for the older grounding tool, "… (web search)" for
     * the 2.3.0 fallback). [viaSearch]: the answer is from our own web search results ([sources] are those pages).
     */
    data class Ok(val text: String, val sources: List<String>, val model: String, val viaSearch: Boolean = false,
                  val hits: List<WebSnippet> = emptyList()) : Grounded()
    /**
     * [error] is "no_key", "network", "failed" or a tagged error ([taggedError]); [model] the first model tried.
     * [snippets]: 2.3.0 – web search results found when Gemini could not read them (shown as they are).
     */
    data class Failed(val error: String, val retryAt: Long, val model: String, val snippets: List<WebSnippet> = emptyList()) : Grounded()
}

/**
 * One grounded Gemini call (Google Search) with the app's model fallback – shared by the live price check and the
 * 2.2.0 land check.
 *
 * Models: the user's chosen model (if any), then the last one that worked, then what the key can use – stable Flash,
 * then Flash-Lite, then previews. A model out of (free-tier) quota is skipped and remembered for about an hour, so a
 * Refresh does not hammer it. Every call is grounded with Google Search; there is never an ungrounded call.
 */
suspend fun groundedGenerate(llm: LlmConfig, prompt: String, now: Long = nowMillis(), searchQueries: List<String> = emptyList()): Grounded {
    if (!llm.hasKey) return Grounded.Failed("no_key", 0L, "auto")
    // 2.5.0 (issue #1): providers without built-in Google Search grounding read the app's own free web search
    // results instead (the same rules: every number must cite one of those pages).
    if (!llm.provider.nativeSearch) {
        val none = Grounded.Failed(taggedError(LookupErrorKind.OTHER, "No web search results to read."), 0L, llm.model)
        if (searchQueries.isEmpty()) return none
        return searchFallback(llm, prompt, searchQueries, none, now)
    }
    val g = groundedOnly(llm.apiKey, llm.model, prompt, now)
    if (g is Grounded.Ok || searchQueries.isEmpty()) return g
    g as Grounded.Failed
    // A bad key or no internet: a web search does not help.
    val kind = classifyLookupError(g.error)
    if (g.error == "network" || kind == LookupErrorKind.BAD_KEY || kind == LookupErrorKind.NO_KEY || kind == LookupErrorKind.NETWORK) return g
    return searchFallback(llm, prompt, searchQueries, g, now)
}

/** Gemini-only form kept for older callers. */
suspend fun groundedGenerate(apiKey: String, model: String, prompt: String, now: Long = nowMillis(), searchQueries: List<String> = emptyList()): Grounded =
    groundedGenerate(LlmConfig(LlmProviders.GEMINI, apiKey, model), prompt, now, searchQueries)

/** One plain (no tools) call to the configured provider. */
private suspend fun plainCall(llm: LlmConfig, model: String, prompt: String): CallResult =
    if (llm.provider.style == ApiStyle.GEMINI) callGemini(llm.apiKey, model, plainBody(prompt))
    else when (val r = chat(llm.provider, llm.apiKey, model, prompt)) {
        is ChatResult.Ok -> CallResult.Ok(r.text, emptyList())
        is ChatResult.Fail -> CallResult.Fail(r.e)
        ChatResult.Offline -> CallResult.Offline
    }

/**
 * 2.3.0 fallback when grounded calls are not possible (quota / grounding not in the free tier / model gone): our own
 * free web search ([WebSearch]) and an ordinary Gemini call that may only use and cite those results. When Gemini
 * cannot answer either, the search results come back in [Grounded.Failed.snippets] to be shown as they are.
 */
private suspend fun searchFallback(llm: LlmConfig, prompt: String, queries: List<String>, grounded: Grounded.Failed, now: Long): Grounded {
    val apiKey = llm.apiKey
    val hits = runCatching { WebSearch.searchAll(queries) }.getOrDefault(emptyList())
    if (hits.isEmpty()) return grounded
    val text = promptWithResults(prompt, hits)
    val models = mutableListOf<String>()
    normalizeModel(llm.provider, llm.model).takeIf { it != "auto" }?.let { models += it }
    cachedPlainModel[llm.provider.id]?.let { if (it !in models) models += it }
    var listedNothing = false
    if (models.size < MAX_PLAIN_CALLS) {
        val avail = listModels(llm.provider, apiKey)
        if (avail != null) {
            val ranked = rankChatModels(llm.provider, avail)
            listedNothing = ranked.isEmpty()
            ranked.forEach { if (it !in models) models += it }
        }
    }
    if (models.isEmpty()) return Grounded.Failed(
        if (listedNothing) taggedError(LookupErrorKind.BAD_KEY, "${llm.provider.name} listed no usable models for this key.") else "network",
        0L, llm.model, hits)
    var calls = 0
    var last: GeminiError? = null
    var retryDelay: Int? = null
    for (m in models) {
        if (calls >= MAX_PLAIN_CALLS) break
        if (exhaustedAt(apiKey, m, now, "p") != null) continue
        calls++
        when (val r = plainCall(llm, m, text)) {
            is CallResult.Offline -> return Grounded.Failed("network", 0L, m, hits)
            is CallResult.Ok -> if (r.text.isNotBlank()) {
                cachedPlainModel[llm.provider.id] = m
                return Grounded.Ok(r.text, hits.map { it.url }, "$m (web search)", viaSearch = true, hits = hits)
            }
            is CallResult.Fail -> {
                last = r.e
                if (isQuotaError(r.e.code, r.e.status, r.e.message)) {
                    r.e.retryDelaySec?.let { retryDelay = maxOf(retryDelay ?: 0, it) }
                    markExhausted(apiKey, m, now, r.e.retryDelaySec, "p", quotaSkipUntil(r.e, now))
                } else if (!isModelUnavailableError(r.e.code, r.e.message)) break
            }
        }
    }
    val e = last
    val err = when {
        e == null -> grounded.error
        isQuotaError(e.code, e.status, e.message) -> taggedError(LookupErrorKind.QUOTA, e.message)
        isBadKeyError(e.code, e.status, e.message) -> taggedError(LookupErrorKind.BAD_KEY, e.message)
        else -> grounded.error
    }
    return Grounded.Failed(err, retryDelay?.let { now + it * 1000L } ?: grounded.retryAt, if (llm.provider.nativeSearch) grounded.model else (models.firstOrNull() ?: llm.model), hits)
}

/** The grounded part of [groundedGenerate] (Google Search tool, model fallback). */
private suspend fun groundedOnly(apiKey: String, model: String, prompt: String, now: Long): Grounded {
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
    var calls = 0

    suspend fun attempt(m: String, legacy: Boolean): Grounded.Ok? {
        calls++
        when (val res = callGemini(apiKey, m, if (legacy) requestBody(prompt, true) else body)) {
            is CallResult.Offline -> { offline = true; return null }
            is CallResult.Fail -> {
                val e = res.e
                lastError = e
                if (isQuotaError(e.code, e.status, e.message)) {
                    quotaHits++
                    if (e.retryDelaySec != null && quotaWindow(e) == QuotaWindow.MINUTE) retryDelay = maxOf(retryDelay ?: 0, e.retryDelaySec)
                    markExhausted(apiKey, m, now, e.retryDelaySec, "g", quotaSkipUntil(e, now))
                    return null
                }
                // The error often names the replacement model ("use models/gemini-x-flash").
                if (!legacy) Regex("models/([a-z0-9.\\-]+)").findAll(e.message).map { it.groupValues[1].trimEnd('.') }
                    .filter { it != m && it !in models }.forEach { models.add(i, it) }
                return null
            }
            is CallResult.Ok -> {
                cachedBestModel = m
                return Grounded.Ok(res.text, res.sources, if (legacy) "$m (search retrieval)" else m)
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
            groundedModelOrder(avail).forEach { if (it !in models) models += it }
            if (i >= models.size) break
        }
        val m = models[i++]
        if (exhaustedAt(apiKey, m, now) != null) { skippedForQuota += m; continue }
        if (calls >= MAX_GROUNDED_CALLS) break
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
    return Grounded.Failed(err, retryAt, models.firstOrNull() ?: "auto")
}

/**
 * Live price lookup for a location + type (always for the property's own coordinates):
 * reverse-geocodes the point, asks Gemini with Google Search grounding for same-type comparables within 10 km,
 * places every comparable on the map (Nominatim near the point; the model's coordinates only as a fallback),
 * and keeps only those within [PriceRules.MAX_KM] km by computed distance. Model fallback: [groundedGenerate].
 */
suspend fun liveLookup(
    llm: LlmConfig,
    lat: Double,
    lng: Double,
    group: Int,
    placeHint: String,
): LiveLookupResult {
    val key = lookupKey(lat, lng, group)
    val now = nowMillis()
    val t0 = nowMillis()
    val rev = runCatching { Geocoder.reverse(lat, lng) }.getOrNull()
    val geoLine = com.hark.sothudiary.data.SourceStatus("OpenStreetMap Nominatim (place name)", rev != null,
        rev?.let { listOf(it.locality, it.taluk, it.district).filter { s -> s.isNotBlank() }.joinToString(", ") } ?: "no answer – using the saved place", nowMillis() - t0)
    val place = describePlace(rev, placeHint)
    // Without a Gemini key there is no online lookup: the price check uses the user's own listings and the saved
    // guideline value (2.4.0 says so plainly instead of silently doing nothing). No price site is fetched automatically.
    val ai = llm.provider.name
    if (!llm.hasKey) return LiveLookupResult(
        RateLookup(key, lat, lng, group, place, now, error = "no_key", status = listOf(geoLine,
            com.hark.sothudiary.data.SourceStatus(if (llm.provider.nativeSearch) "$ai + Google Search" else "Web search + $ai", null, "skipped – no AI key (optional)"))),
        emptyList(), "no_key")
    val prompt = buildLivePrompt(place, lat, lng, group)
    val t1 = nowMillis()
    val g = kotlinx.coroutines.withTimeoutOrNull(75_000) { groundedGenerate(llm, prompt, now, priceSearchQueries(rev, placeHint, group)) }
        ?: Grounded.Failed("[other] The AI search took too long (75 s).", 0L, "auto")
    val aiMs = nowMillis() - t1
    return when (g) {
        is Grounded.Ok -> {
            val (out, skipped) = placeComparables(g.text, g.sources, lat, lng, group, rev, key, now, if (g.viaSearch) g.hits else null)
            val status = listOf(geoLine, com.hark.sothudiary.data.SourceStatus(
                if (g.viaSearch) "Web search + $ai (${g.model})" else "$ai + Google Search (${g.model})", true,
                "${out.size} rates kept within ${PriceRules.MAX_KM.toInt()} km, $skipped dropped", aiMs, "sources: " + g.sources.take(6).joinToString(" ")))
            LiveLookupResult(RateLookup(key, lat, lng, group, place, now, out.size, skipped, g.model, null, g.sources.take(12), viaSearch = g.viaSearch,
                // Nothing usable from the AI: keep the search results so they can still be read.
                snippets = if (g.viaSearch && out.isEmpty()) g.hits.take(8) else emptyList(),
                status = status), out)
        }
        is Grounded.Failed -> LiveLookupResult(
            RateLookup(key, lat, lng, group, place, now, model = g.model, error = g.error, retryAt = g.retryAt, snippets = g.snippets.take(8),
                status = listOf(geoLine, com.hark.sothudiary.data.SourceStatus("$ai (${g.model})", false, shortAiError(g.error), aiMs, g.error.take(300)))),
            emptyList(), g.error,
        )
    }
}

/** 2.4.0: one short line for an AI error (Status panel). */
fun shortAiError(e: String): String = when (classifyLookupError(e)) {
    LookupErrorKind.NO_KEY -> "no AI key"
    LookupErrorKind.NETWORK -> "no internet / timeout"
    LookupErrorKind.QUOTA -> "free quota used up for now"
    LookupErrorKind.BAD_KEY -> "the AI key was refused"
    LookupErrorKind.MODEL_UNAVAILABLE -> "model not available for this key"
    else -> e.substringAfter("] ").take(90)
}

private fun groupQueryWords(group: Int): String = when (group) {
    1 -> "agricultural land for sale price per acre"
    2 -> "independent house for sale price"
    3 -> "flat apartment for sale price per sq ft"
    else -> "plot for sale price per cent"
}

/** 2.3.0: free web search queries for the price check fallback (village / locality, taluk, district). */
fun priceSearchQueries(rev: GeoPlace?, hint: String, group: Int): List<String> {
    val local = listOfNotNull(rev?.locality?.takeIf { it.isNotBlank() }, hint.split(',').firstOrNull()?.trim()?.takeIf { it.isNotBlank() }).firstOrNull().orEmpty()
    val taluk = rev?.taluk.orEmpty()
    val district = rev?.district.orEmpty().ifBlank { hint.split(',').map { it.trim() }.lastOrNull().orEmpty() }
    if (local.isBlank() && district.isBlank()) return emptyList()
    return listOf(
        listOf(local, district, groupQueryWords(group)).filter { it.isNotBlank() }.joinToString(" "),
        listOf(local, taluk.ifBlank { district }, if (group == 1) "land rate per acre" else "land rate per cent 2026").filter { it.isNotBlank() }.joinToString(" "),
        if (group == 2 || group == 3) "" else listOf(local, district, "guideline value").filter { it.isNotBlank() }.joinToString(" "),
    ).filter { it.isNotBlank() }.distinct()
}

/** Turns the model's JSON into comparables placed on the map within 10 km. Returns (kept, dropped count). */
private suspend fun placeComparables(
    text: String, sources: List<String>, lat: Double, lng: Double, group: Int, rev: GeoPlace?, key: String, now: Long,
    /** 2.3.0: the web search results when the answer came from them – every comparable must cite one of them. */
    searchHits: List<WebSnippet>? = null,
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
        val cited = o.s("source_url").takeIf { it.startsWith("http") }
        // Never an unsourced number: with our own search, the cited page must be one of the results we fetched.
        if (searchHits != null && (cited == null || !citesResult(cited, searchHits))) { skipped++; continue }
        val url = cited ?: sources.firstOrNull().orEmpty()
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
/** 2.3.0: the model that last answered a plain (web-search) call. */
private val cachedPlainModel = mutableMapOf<String, String>()

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
