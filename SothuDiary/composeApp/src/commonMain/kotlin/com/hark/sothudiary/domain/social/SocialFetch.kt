package com.hark.sothudiary.domain.social

import com.hark.sothudiary.data.Review
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.domain.BROWSER_USER_AGENT
import com.hark.sothudiary.domain.LinkMeta
import com.hark.sothudiary.domain.parseOpenGraph
import com.hark.sothudiary.domain.urlEncode
import com.hark.sothudiary.domain.youtubeId
import com.hark.sothudiary.domain.youtubeThumb
import com.hark.sothudiary.httpRequest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Link-preview crawlers that Instagram / Facebook usually answer without a login. */
const val UA_FACEBOOK_BOT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
const val UA_TWITTER_BOT = "Twitterbot/1.0"
const val UA_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

/** What could be read from a shared post. Every field is optional. */
data class SocialPost(
    val url: String,
    val platform: SourcePlatform,
    val title: String? = null,
    val caption: String? = null,
    val author: String? = null,
    val image: String? = null,
    val comments: List<Review> = emptyList(),
    /** Which methods produced data, e.g. "og:facebookexternalhit", "instagram-embed" – for diagnostics. */
    val methods: List<String> = emptyList(),
) {
    val hasText: Boolean get() = !caption.isNullOrBlank() || !title.isNullOrBlank()

    /** Fills only what is missing from [other]. */
    fun merge(other: SocialPost?): SocialPost {
        if (other == null) return this
        return copy(
            title = title ?: other.title,
            caption = if (caption.isNullOrBlank() || (other.caption?.length ?: 0) > caption.length + 40) other.caption ?: caption else caption,
            author = author ?: other.author,
            image = image ?: other.image,
            comments = (comments + other.comments).distinctBy { it.author + "|" + it.text.take(60) },
            methods = methods + other.methods,
        )
    }
}

fun platformOf(url: String): SourcePlatform {
    val u = url.lowercase()
    return when {
        "youtube.com" in u || "youtu.be" in u -> SourcePlatform.YOUTUBE
        "instagram.com" in u || "instagr.am" in u -> SourcePlatform.INSTAGRAM
        "facebook.com" in u || "fb.watch" in u || "fb.com" in u || "fb.me" in u -> SourcePlatform.FACEBOOK
        "wa.me" in u || "whatsapp" in u -> SourcePlatform.WHATSAPP
        u.startsWith("http") -> SourcePlatform.OTHER
        else -> SourcePlatform.NONE
    }
}

private val igCodeRe = Regex("""instagr(?:am\.com|\.am)/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]{5,})""")

/** Shortcode of an Instagram post / reel link (reels use the same shortcode as posts). */
fun instagramShortcode(url: String): String? = igCodeRe.find(url)?.groupValues?.get(1)

/** Decodes HTML entities (named, decimal and hex). */
fun htmlDecode(s: String): String {
    if ('&' !in s) return s
    val named = mapOf("amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to " ", "#39" to "'")
    return Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)?.let { codePointToString(it) } ?: m.value
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { codePointToString(it) } ?: m.value
            else -> named[e.lowercase()] ?: m.value
        }
    }
}

private fun codePointToString(cp: Int): String =
    if (cp < 0x10000) cp.toChar().toString()
    else {
        val v = cp - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }

/** Plain text of an HTML fragment (keeps line breaks from <br> / block tags). */
fun htmlToText(fragment: String): String =
    htmlDecode(
        fragment.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</(p|div|li|h\d)>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), ""),
    ).lines().joinToString("\n") { it.trim() }.replace(Regex("""\n{3,}"""), "\n\n").trim()

// ---------------- Open Graph via crawler user agents ----------------

private fun isLoginWall(finalUrl: String, html: String?): Boolean {
    val u = finalUrl.lowercase()
    if ("/login" in u || "accounts/login" in u || "checkpoint" in u) return true
    return html != null && html.length < 6000 && ("login" in html.lowercase()) && "og:description" !in html
}

/** og:title / og:description / og:image using a link-preview crawler user agent. Null if login-walled or empty. */
suspend fun fetchOgAs(url: String, userAgent: String): LinkMeta? {
    val r = httpRequest(url, headers = mapOf("User-Agent" to userAgent, "Accept-Language" to "en-IN,en;q=0.9,ta;q=0.8", "Accept" to "text/html"))
        ?: return null
    if (!r.ok || r.body.isNullOrBlank() || isLoginWall(r.finalUrl, r.body)) return null
    val meta = parseOpenGraph(r.body)
    val generic = meta.description?.startsWith("See posts, photos and more on Facebook") == true ||
        meta.description?.contains("Create an account or log in to Instagram") == true
    if (generic) return null
    return meta.takeIf { it.description != null || (it.title != null && it.title !in setOf("Instagram", "Facebook", "Log in or sign up to view")) }
}

private val igOgRe = Regex("""^\s*([\d.,]+[KkMm]?)\s+likes?,\s*([\d.,]+[KkMm]?)\s+comments?\s*-\s*([^\s]+)\s+on\s+([^:]+):\s*["“](.*)["”]\s*\.?\s*$""", RegexOption.DOT_MATCHES_ALL)
private val igOgTitleRe = Regex("""^(.*?)\s+on Instagram:\s*["“](.*)["”]\s*$""", RegexOption.DOT_MATCHES_ALL)

/** Turns Instagram's og text ("3 likes, 1 comments - user on November 7, 2024: "caption"") into a post. */
fun instagramFromOg(url: String, meta: LinkMeta, method: String): SocialPost {
    val d = meta.description?.trim().orEmpty()
    val m = igOgRe.find(d)
    val t = igOgTitleRe.find(meta.title.orEmpty())
    val caption = m?.groupValues?.get(5) ?: t?.groupValues?.get(2) ?: d.ifBlank { null }
    val author = m?.groupValues?.get(3) ?: t?.groupValues?.get(1)
    return SocialPost(
        url, SourcePlatform.INSTAGRAM,
        title = null, caption = caption?.trim(), author = author?.trim(), image = meta.image, methods = listOf(method),
    )
}

/** Facebook og:title is usually "Caption … | Page" or the page name; og:description the post text. */
fun facebookFromOg(url: String, meta: LinkMeta, method: String): SocialPost {
    val title = meta.title?.substringBefore(" | Facebook")?.trim()
    val caption = meta.description?.trim()
    val author = title?.takeIf { caption != null && it.length < 60 && !caption.startsWith(it.take(20)) }
    return SocialPost(url, SourcePlatform.FACEBOOK, title = title, caption = caption, author = author, image = meta.image, methods = listOf(method))
}

// ---------------- Instagram embed page ----------------

/** Parses https://www.instagram.com/p/<code>/embed/captioned/ (HTML caption block or the embedded JSON). */
fun parseInstagramEmbed(url: String, html: String): SocialPost? {
    var caption: String? = null
    var author: String? = null
    var image: String? = null
    val comments = mutableListOf<Review>()

    // (1) Embedded JSON: "contextJSON":"{...}" or window.__additionalDataLoaded('extra', {...})
    val ctx = jsStringAfter(html, "\"contextJSON\":\"")?.let { parseJsonOrNull(it) }
    val extra = html.indexOf("__additionalDataLoaded").takeIf { it >= 0 }?.let { balancedJson(html, it) }?.let { parseJsonOrNull(it) }
    val media = listOfNotNull(ctx, extra).firstNotNullOfOrNull { root ->
        root.findFirst("shortcode_media") ?: root.findFirst("media")?.takeIf { it["edge_media_to_caption"] != null }
    }
    if (media != null) {
        caption = media.at("edge_media_to_caption", "edges", 0, "node", "text").str()
        author = media.at("owner", "username").str()
        image = media["display_url"].str() ?: media["thumbnail_src"].str()
        val edges = (media.at("edge_media_to_parent_comment", "edges") ?: media.at("edge_media_preview_comment", "edges")).arr()
        edges.forEach { e ->
            val n = e["node"]
            val text = n["text"].str() ?: return@forEach
            comments += Review(
                author = n.at("owner", "username").str() ?: "",
                text = text,
                likes = n.at("edge_liked_by", "count").num(),
                date = n["created_at"].num()?.let { epochToDate(it) } ?: "",
                source = SourcePlatform.INSTAGRAM,
            )
        }
    }
    // (2) Classic HTML caption block.
    if (caption == null) {
        val start = html.indexOf("class=\"Caption\"")
        if (start >= 0) {
            val open = html.indexOf('>', start) + 1
            val end = listOf(html.indexOf("class=\"CaptionComments\"", open), html.indexOf("</div>", open)).filter { it > 0 }.minOrNull() ?: -1
            if (open > 0 && end > open) {
                val block = html.substring(open, end)
                author = author ?: Regex("""class="CaptionUsername"[^>]*>([^<]+)<""").find(block)?.groupValues?.get(1)?.trim()
                caption = htmlToText(block.replace(Regex("""<a[^>]*class="CaptionUsername"[^>]*>[^<]*</a>"""), ""))
                    .removeSuffix("<").trim().ifBlank { null }
            }
        }
    }
    if (image == null) {
        image = Regex("""class="EmbeddedMediaImage"[^>]*src="([^"]+)"""").find(html)?.groupValues?.get(1)?.let(::htmlDecode)
            ?: Regex("""<img[^>]+src="([^"]+)"[^>]*class="EmbeddedMediaImage"""").find(html)?.groupValues?.get(1)?.let(::htmlDecode)
    }
    if (author == null) author = Regex("""class="UsernameText"[^>]*>([^<]+)<""").find(html)?.groupValues?.get(1)?.trim()
    if (caption == null && comments.isEmpty() && image == null) return null
    return SocialPost(url, SourcePlatform.INSTAGRAM, caption = caption, author = author, image = image, comments = comments.take(10), methods = listOf("instagram-embed"))
}

fun epochToDate(seconds: Long): String {
    val ms = seconds * 1000
    return runCatching {
        val i = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
        i.toString().take(10)
    }.getOrDefault("")
}

// ---------------- Facebook ----------------

/** True for Facebook short / share links that only redirect to the real post. */
fun isFacebookShortLink(url: String): Boolean {
    val u = url.lowercase()
    return "fb.watch/" in u || "/share/" in u || "fb.me/" in u || "l.facebook.com/l.php" in u || "facebook.com/story.php" in u && "story_fbid" !in u
}

/** Resolves share/r/, share/v/, share/p/, fb.watch, fb.me links to the post URL (follows redirects without a login). */
suspend fun resolveFacebookUrl(url: String): String {
    if (!isFacebookShortLink(url)) return url
    Regex("""[?&]u=([^&]+)""").find(url)?.let { return percentDecode(it.groupValues[1]) }
    var current = url
    repeat(6) {
        val r = httpRequest(current, headers = mapOf("User-Agent" to UA_FACEBOOK_BOT), followRedirects = false) ?: return current
        val loc = r.location ?: return current
        // A login redirect carries the real target in ?next=
        val next = Regex("""[?&]next=([^&]+)""").find(loc)?.groupValues?.get(1)?.let(::percentDecode)
        current = next ?: loc
        if (!isFacebookShortLink(current) && "/login" !in current) return current
    }
    return current
}

fun percentDecode(s: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val v = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (v != null) { bytes += v.toByte(); i += 3; continue }
        }
        if (c == '+') bytes += ' '.code.toByte() else bytes += c.toString().encodeToByteArray().toList()
        i++
    }
    return bytes.toByteArray().decodeToString()
}

fun facebookVariants(url: String): List<String> {
    val base = url.replace(Regex("""^https?://(www\.|m\.|mbasic\.|web\.|business\.)?facebook\.com"""), "")
    if (base == url) return listOf(url)
    return listOf("https://www.facebook.com$base", "https://m.facebook.com$base", "https://mbasic.facebook.com$base")
}

// ---------------- YouTube ----------------

private const val YT_CLIENT_VERSION = "2.20241101.01.00"

private fun ytContext() = """{"client":{"clientName":"WEB","clientVersion":"$YT_CLIENT_VERSION","hl":"en","gl":"IN"}}"""

private suspend fun ytPost(endpoint: String, body: String): JsonElement? {
    val r = httpRequest(
        "https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false",
        method = "POST",
        headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to UA_DESKTOP,
            "X-YouTube-Client-Name" to "1",
            "X-YouTube-Client-Version" to YT_CLIENT_VERSION,
            "Origin" to "https://www.youtube.com",
        ),
        body = body,
    ) ?: return null
    return if (r.ok) parseJsonOrNull(r.body) else null
}

/** Title, channel and full description of a YouTube video (innertube player, no key). */
suspend fun youtubeDetails(videoId: String): SocialPost? {
    val j = ytPost("player", """{"context":${ytContext()},"videoId":"$videoId"}""") ?: return null
    val d = j["videoDetails"] ?: return null
    return SocialPost(
        "https://youtu.be/$videoId", SourcePlatform.YOUTUBE,
        title = d["title"].str(), caption = d["shortDescription"].str(), author = d["author"].str(),
        image = "https://img.youtube.com/vi/$videoId/hqdefault.jpg", methods = listOf("youtube-innertube-player"),
    )
}

/** Parses comments from an innertube "next" response (new commentEntityPayload and old commentRenderer shapes). */
fun parseYoutubeComments(root: JsonElement?): List<Review> {
    val out = mutableListOf<Review>()
    root.findAll("commentEntityPayload").forEach { p ->
        val text = p.at("properties", "content", "content").str() ?: return@forEach
        out += Review(
            author = p.at("author", "displayName").str() ?: "",
            text = text,
            likes = parseCount(p.at("toolbar", "likeCountNotliked").str() ?: p.at("toolbar", "likeCountA11y").str()),
            date = p.at("properties", "publishedTime").str() ?: "",
            source = SourcePlatform.YOUTUBE,
        )
    }
    if (out.isEmpty()) root.findAll("commentRenderer").forEach { c ->
        val text = c["contentText"].ytText() ?: return@forEach
        out += Review(
            author = c["authorText"].ytText() ?: "",
            text = text,
            likes = parseCount(c["voteCount"].ytText()),
            date = c["publishedTimeText"].ytText() ?: "",
            source = SourcePlatform.YOUTUBE,
        )
    }
    return out.distinctBy { it.author + it.text }
}

/** "1.2K" -> 1200, "15" -> 15. */
fun parseCount(s: String?): Long? {
    val t = s?.trim()?.replace(",", "")?.substringBefore(" ") ?: return null
    if (t.isEmpty()) return null
    val mult = when (t.last().uppercaseChar()) { 'K' -> 1_000.0; 'M' -> 1_000_000.0; else -> 1.0 }
    val num = (if (mult > 1) t.dropLast(1) else t).toDoubleOrNull() ?: return null
    return (num * mult).toLong()
}

private fun findCommentsContinuation(root: JsonElement?): String? {
    // The comments section is an itemSectionRenderer with sectionIdentifier "comment-item-section".
    val sections = root.findAll("itemSectionRenderer")
    for (s in sections) {
        if (s["sectionIdentifier"].str() != "comment-item-section") continue
        s["contents"].arr().forEach { c ->
            c.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").str()?.let { return it }
        }
    }
    // Fallback: the engagement panel for comments.
    root.findAll("engagementPanelSectionListRenderer").forEach { p ->
        if (p["panelIdentifier"].str()?.contains("comment") == true) {
            p.findAll("continuationCommand").firstNotNullOfOrNull { it["token"].str() }?.let { return it }
        }
    }
    return null
}

/** Top comments of a YouTube video without an API key (innertube next + continuation). */
suspend fun youtubeCommentsInnertube(videoId: String, max: Int = 10): List<Review> {
    val first = ytPost("next", """{"context":${ytContext()},"videoId":"$videoId"}""") ?: return emptyList()
    var token = findCommentsContinuation(first) ?: return emptyList()
    val out = mutableListOf<Review>()
    repeat(3) {
        val page = ytPost("next", """{"context":${ytContext()},"continuation":"$token"}""") ?: return out
        out += parseYoutubeComments(page)
        if (out.size >= max) return out.take(max)
        token = page.findAll("continuationCommand").lastOrNull()?.get("token").str() ?: return out
    }
    return out.take(max)
}

/** Top comments via the YouTube Data API v3 (needs a key). */
suspend fun youtubeCommentsApi(videoId: String, apiKey: String, max: Int = 10): List<Review>? {
    val r = httpRequest(
        "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&order=relevance&textFormat=plainText&maxResults=$max&videoId=$videoId&key=" + urlEncode(apiKey),
        headers = mapOf("User-Agent" to BROWSER_USER_AGENT),
    ) ?: return null
    if (!r.ok) return null
    return parseJsonOrNull(r.body)["items"].arr().mapNotNull { it.at("snippet", "topLevelComment", "snippet") }.map { s ->
        Review(
            author = s["authorDisplayName"].str() ?: "",
            text = s["textDisplay"].str() ?: s["textOriginal"].str() ?: "",
            likes = s["likeCount"].num(),
            date = s["publishedAt"].str()?.take(10) ?: "",
            source = SourcePlatform.YOUTUBE,
        )
    }.filter { it.text.isNotBlank() }
}

/** Title / description via the Data API (needs a key). */
suspend fun youtubeDetailsApi(videoId: String, apiKey: String): SocialPost? {
    val r = httpRequest("https://www.googleapis.com/youtube/v3/videos?part=snippet&id=$videoId&key=" + urlEncode(apiKey)) ?: return null
    if (!r.ok) return null
    val s = parseJsonOrNull(r.body).at("items", 0, "snippet") ?: return null
    return SocialPost(
        "https://youtu.be/$videoId", SourcePlatform.YOUTUBE, title = s["title"].str(), caption = s["description"].str(),
        author = s["channelTitle"].str(), image = s.at("thumbnails", "high", "url").str(), methods = listOf("youtube-data-api"),
    )
}

// ---------------- The pipeline ----------------

/**
 * Reads a shared post the best way available without a login:
 * YouTube – oEmbed + innertube player (full description) or the Data API;
 * Instagram – crawler-UA Open Graph, then the public embed page;
 * Facebook – resolve share links, then crawler-UA Open Graph on www / m / mbasic.
 * The WebView reader ("Read post", with login) is the fallback the UI offers when this finds no caption.
 */
suspend fun readPost(url: String, youtubeApiKey: String = ""): SocialPost {
    val platform = platformOf(url)
    var post = SocialPost(url, platform)
    when (platform) {
        SourcePlatform.YOUTUBE -> {
            val id = youtubeId(url)
            if (id != null) {
                if (youtubeApiKey.isNotBlank()) post = post.merge(youtubeDetailsApi(id, youtubeApiKey))
                if (post.caption == null) post = post.merge(youtubeDetails(id))
                if (post.title == null) {
                    val r = httpRequest("https://www.youtube.com/oembed?format=json&url=" + urlEncode(url), headers = mapOf("User-Agent" to BROWSER_USER_AGENT))
                    val j = parseJsonOrNull(r?.body)
                    if (j != null) post = post.merge(SocialPost(url, platform, title = j["title"].str(), author = j["author_name"].str(), methods = listOf("youtube-oembed")))
                }
                if (post.image == null) post = post.copy(image = youtubeThumb(url))
            }
        }
        SourcePlatform.INSTAGRAM -> {
            val code = instagramShortcode(url)
            val canonical = code?.let { "https://www.instagram.com/p/$it/" } ?: url
            for (ua in listOf(UA_FACEBOOK_BOT, UA_TWITTER_BOT)) {
                val meta = fetchOgAs(canonical, ua) ?: continue
                post = post.merge(instagramFromOg(url, meta, "og:" + ua.substringBefore('/')))
                break
            }
            if (code != null && (post.caption == null || post.comments.isEmpty())) {
                for (ua in listOf(BROWSER_USER_AGENT, UA_DESKTOP)) {
                    val r = httpRequest("https://www.instagram.com/p/$code/embed/captioned/", headers = mapOf("User-Agent" to ua, "Accept-Language" to "en-IN,en;q=0.9"))
                    val parsed = r?.body?.takeIf { r.ok }?.let { parseInstagramEmbed(url, it) }
                    if (parsed != null) { post = post.merge(parsed); break }
                }
            }
        }
        SourcePlatform.FACEBOOK -> {
            val resolved = runCatching { resolveFacebookUrl(url) }.getOrDefault(url)
            loop@ for (variant in facebookVariants(resolved)) {
                for (ua in listOf(UA_FACEBOOK_BOT, UA_TWITTER_BOT)) {
                    val meta = fetchOgAs(variant, ua) ?: continue
                    post = post.merge(facebookFromOg(resolved, meta, "og:" + ua.substringBefore('/') + "@" + variant.substringAfter("://").substringBefore('.')))
                    if (post.caption != null) break@loop
                }
            }
        }
        SourcePlatform.OTHER -> {
            val meta = fetchOgAs(url, BROWSER_USER_AGENT) ?: fetchOgAs(url, UA_FACEBOOK_BOT)
            if (meta != null) post = post.merge(SocialPost(url, platform, meta.title, meta.description, meta.author, meta.image, methods = listOf("og:browser")))
        }
        else -> {}
    }
    return post
}

/** Comments of the post as "user reviews": YouTube (API key or innertube), Instagram (embed page). */
suspend fun fetchComments(url: String, youtubeApiKey: String = ""): List<Review> {
    return when (platformOf(url)) {
        SourcePlatform.YOUTUBE -> {
            val id = youtubeId(url) ?: return emptyList()
            (if (youtubeApiKey.isNotBlank()) youtubeCommentsApi(id, youtubeApiKey) else null)?.takeIf { it.isNotEmpty() }
                ?: youtubeCommentsInnertube(id)
        }
        SourcePlatform.INSTAGRAM -> {
            val code = instagramShortcode(url) ?: return emptyList()
            val r = httpRequest("https://www.instagram.com/p/$code/embed/captioned/", headers = mapOf("User-Agent" to BROWSER_USER_AGENT))
            r?.body?.takeIf { r.ok }?.let { parseInstagramEmbed(url, it) }?.comments ?: emptyList()
        }
        else -> emptyList()
    }
}

/** Builds a post from what the in-app WebView reader extracted (JSON produced by the reader script). */
fun postFromReaderJson(url: String, json: String?): SocialPost? {
    val j = parseJsonOrNull(json) as? JsonObject ?: return null
    val comments = j["comments"].arr().mapNotNull { c ->
        val text = c["text"].str() ?: return@mapNotNull null
        Review(author = c["author"].str() ?: "", text = text.take(600), likes = c["likes"].num(), date = c["date"].str() ?: "", source = platformOf(url))
    }
    val caption = j["caption"].str()?.let { cleanReaderCaption(it) }
    return SocialPost(
        url, platformOf(url), title = j["title"].str(), caption = caption, author = j["author"].str(),
        image = j["image"].str(), comments = comments.distinctBy { it.text }.take(10), methods = listOf("webview"),
    ).takeIf { it.hasText || it.comments.isNotEmpty() || it.image != null }
}

private fun cleanReaderCaption(s: String): String {
    val m = igOgRe.find(s)
    return (m?.groupValues?.get(5) ?: s).replace(Regex("""\s*(… ?more|\.\.\. ?more|See more|மேலும் காண்க)\s*$"""), "").trim()
}
