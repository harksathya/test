package com.hark.sothudiary.domain.web

import com.hark.sothudiary.data.WebSnippet
import com.hark.sothudiary.domain.social.htmlDecode
import com.hark.sothudiary.domain.urlEncode
import com.hark.sothudiary.httpRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 2.3.0: a free, keyless web search used when Gemini's Google Search grounding is not available on the user's key
 * (Google Search grounding is not part of the free tier for Gemini 3 and newer models). The results – title, page URL
 * and snippet – are given to an ordinary (ungrounded) Gemini call that may only cite these pages, or are shown as they
 * are when Gemini is out of quota too.
 *
 * Sources, tried in order: DuckDuckGo's HTML endpoint (POST), DuckDuckGo Lite, then Bing's RSS output. They are public
 * pages without a key; they may refuse automated traffic now and then (DuckDuckGo answers HTTP 202 with a challenge
 * page), in which case the next one is tried and, at worst, the search finds nothing.
 */
object WebSearch {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** Searches [query]; at most [max] results. Empty on any failure. */
    suspend fun search(query: String, max: Int = 8): List<WebSnippet> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val ddg = runCatching {
            httpRequest(
                "https://html.duckduckgo.com/html/", method = "POST",
                headers = mapOf("User-Agent" to UA, "Content-Type" to "application/x-www-form-urlencoded", "Accept" to "text/html", "Accept-Language" to "en-IN,en;q=0.8"),
                body = "q=" + urlEncode(q) + "&kl=in-en", timeoutMs = 15_000,
            )
        }.getOrNull()
        if (ddg != null && ddg.code == 200 && !isBlockedPage(ddg.body)) parseDuckDuckGoHtml(ddg.body).takeIf { it.isNotEmpty() }?.let { return it.take(max) }
        val lite = runCatching {
            httpRequest("https://lite.duckduckgo.com/lite/?kl=in-en&q=" + urlEncode(q), headers = mapOf("User-Agent" to UA, "Accept" to "text/html"), timeoutMs = 15_000)
        }.getOrNull()
        if (lite != null && lite.code == 200 && !isBlockedPage(lite.body)) parseDuckDuckGoLite(lite.body).takeIf { it.isNotEmpty() }?.let { return it.take(max) }
        val bing = runCatching {
            httpRequest("https://www.bing.com/search?format=rss&cc=IN&setlang=en&q=" + urlEncode(q), headers = mapOf("User-Agent" to UA, "Accept" to "application/rss+xml"), timeoutMs = 15_000)
        }.getOrNull()
        if (bing != null && bing.ok) return relevantOnly(parseBingRss(bing.body), q).take(max)
        return emptyList()
    }

    /** Several queries in parallel, results merged (first query first), one result per page, at most [max]. */
    suspend fun searchAll(queries: List<String>, max: Int = 10): List<WebSnippet> = coroutineScope {
        queries.filter { it.isNotBlank() }.distinct().take(4).map { q -> async { search(q, 6) } }.flatMap { it.await() }
            .let(::dedupeByUrl).take(max)
    }
}

/** DuckDuckGo's "are you a robot" page (HTTP 202 / anomaly modal). */
fun isBlockedPage(html: String?): Boolean {
    if (html == null) return true
    return "anomaly-modal" in html || "challenge-form" in html || "bots use DuckDuckGo" in html
}

private val tagRe = Regex("<[^>]+>")
private val wsRe = Regex("\\s+")

/** Plain text of an HTML fragment (tags removed, entities decoded, whitespace collapsed). */
fun htmlText(fragment: String): String = wsRe.replace(htmlDecode(tagRe.replace(fragment, " ")), " ").trim()

/** Decodes %XX escapes (UTF-8) and '+' in a URL component. */
fun percentDecode(s: String): String {
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val v = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (v != null) { out += v.toByte(); i += 3; continue }
        }
        if (c == '+') out += ' '.code.toByte() else c.toString().encodeToByteArray().forEach { out += it }
        i++
    }
    return out.toByteArray().decodeToString()
}

/** The real target of a DuckDuckGo redirect link ("//duckduckgo.com/l/?uddg=https%3A%2F%2F…&rut=…"); others unchanged. */
fun unwrapDuckLink(href: String): String {
    val h = htmlDecode(href.trim())
    val m = Regex("""[?&]uddg=([^&]+)""").find(h) ?: return if (h.startsWith("//")) "https:$h" else h
    return percentDecode(m.groupValues[1])
}

/** Only real result pages: http(s), not DuckDuckGo / Bing themselves and not their ad redirects. */
fun isResultUrl(u: String): Boolean {
    if (!u.startsWith("http://") && !u.startsWith("https://")) return false
    val host = u.substringAfter("://").substringBefore('/').lowercase()
    return !host.endsWith("duckduckgo.com") && !host.endsWith("bing.com") && "ad_domain" !in u && "/y.js" !in u
}

/**
 * Parses html.duckduckgo.com results: `<a class="result__a" href="…uddg=…">title</a>` followed by
 * `<a class="result__snippet" …>snippet</a>`. Ads (`result--ad`) are skipped.
 */
fun parseDuckDuckGoHtml(html: String?): List<WebSnippet> {
    if (html.isNullOrBlank()) return emptyList()
    val out = mutableListOf<WebSnippet>()
    // Split into result blocks so a title is paired with its own snippet.
    val blocks = html.split(Regex("""<div[^>]+class="[^"]*\bresult\b""")).drop(1)
    for (b in blocks) {
        if ("result--ad" in b.substringBefore('>') || "badge--ad" in b) continue
        val a = Regex("""<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(b)
            ?: Regex("""<a[^>]*href="([^"]+)"[^>]*class="[^"]*result__a[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).find(b)
            ?: continue
        val url = unwrapDuckLink(a.groupValues[1])
        if (!isResultUrl(url)) continue
        val sn = Regex("""class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</(?:a|div|td)>""", RegexOption.DOT_MATCHES_ALL).find(b)?.groupValues?.get(1).orEmpty()
        out += WebSnippet(htmlText(a.groupValues[2]).take(160), url, htmlText(sn).take(400))
    }
    return dedupeByUrl(out)
}

/** Parses lite.duckduckgo.com: `<a … class='result-link'>title</a>` rows followed by `<td class='result-snippet'>`. */
fun parseDuckDuckGoLite(html: String?): List<WebSnippet> {
    if (html.isNullOrBlank()) return emptyList()
    val links = Regex("""<a[^>]*href=["']([^"']+)["'][^>]*class=["']result-link["'][^>]*>(.*?)</a>|<a[^>]*class=["']result-link["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(html).toList()
    val snippets = Regex("""<td[^>]*class=["']result-snippet["'][^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL).findAll(html).map { it.groupValues[1] }.toList()
    val out = mutableListOf<WebSnippet>()
    links.forEachIndexed { i, m ->
        val href = m.groupValues[1].ifBlank { m.groupValues[3] }
        val title = m.groupValues[2].ifBlank { m.groupValues[4] }
        val url = unwrapDuckLink(href)
        if (isResultUrl(url)) out += WebSnippet(htmlText(title).take(160), url, htmlText(snippets.getOrElse(i) { "" }).take(400))
    }
    return dedupeByUrl(out)
}

/** Parses Bing's RSS output (`<item><title/><link/><description/></item>`). */
fun parseBingRss(xml: String?): List<WebSnippet> {
    if (xml.isNullOrBlank()) return emptyList()
    fun tag(item: String, name: String) = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1).orEmpty()
        .removePrefix("<![CDATA[").removeSuffix("]]>")
    return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.mapNotNull { item ->
        val url = htmlDecode(tag(item, "link").trim())
        if (!isResultUrl(url)) null else WebSnippet(htmlText(tag(item, "title")).take(160), url, htmlText(tag(item, "description")).take(400))
    }.toList().let(::dedupeByUrl)
}

/** Drops results that share no word of 4+ letters with the query (Bing's RSS sometimes answers with unrelated pages). */
fun relevantOnly(hits: List<WebSnippet>, query: String): List<WebSnippet> {
    val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 }.toSet()
    if (words.isEmpty()) return hits
    return hits.filter { h -> val t = (h.title + " " + h.snippet + " " + h.url).lowercase(); words.any { it in t } }
}

fun dedupeByUrl(hits: List<WebSnippet>): List<WebSnippet> = hits.distinctBy { it.url.trimEnd('/').substringBefore('#').lowercase() }

/**
 * Turns a "Use Google Search" prompt into one that may only use the given search results: the instructions and
 * the JSON format stay the same; every source_url must be one of the listed pages.
 */
fun promptWithResults(prompt: String, hits: List<WebSnippet>): String {
    val list = hits.mapIndexed { i, h -> "[${i + 1}] ${h.title}\nURL: ${h.url}\n${h.snippet}" }.joinToString("\n\n")
    val body = prompt.replace("Use Google Search.", "Use ONLY the web search results listed at the end (title, URL and the text shown under it).")
    return body + "\n\nWEB SEARCH RESULTS (the only allowed sources – every source_url must be exactly one of these URLs; if they do not say it, say unknown / leave it out):\n" + list
}

/** True when [url] is one of the search results (ignoring a trailing slash / fragment / case). */
fun citesResult(url: String, hits: List<WebSnippet>): Boolean {
    val u = url.trim().trimEnd('/').substringBefore('#').lowercase()
    if (u.isBlank()) return false
    return hits.any { it.url.trim().trimEnd('/').substringBefore('#').lowercase() == u }
}
