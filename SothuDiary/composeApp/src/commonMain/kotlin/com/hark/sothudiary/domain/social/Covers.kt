package com.hark.sothudiary.domain.social

import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.domain.BROWSER_USER_AGENT
import com.hark.sothudiary.domain.youtubeId
import com.hark.sothudiary.httpBytes

/**
 * Cover image links for a post, best first:
 * YouTube – maxresdefault, then hqdefault; Instagram / Facebook – the post's media (carousel: first slide first).
 */
fun coverCandidates(url: String, post: SocialPost?): List<String> {
    val out = mutableListOf<String>()
    if (platformOf(url) == SourcePlatform.YOUTUBE) {
        youtubeId(url)?.let { id ->
            out += "https://img.youtube.com/vi/$id/maxresdefault.jpg"
            out += "https://img.youtube.com/vi/$id/hqdefault.jpg"
        }
    }
    post?.allImages?.forEach { if (it.startsWith("http")) out += it }
    return out.distinct()
}

/** A downloaded cover: the saved file name and the link it came from. */
data class SavedCover(val file: String, val url: String)

/** Smallest plausible photo; YouTube's "no maxres" placeholder and tracking pixels are smaller. */
private const val MIN_COVER_BYTES = 4_000

fun imageExt(url: String, bytes: ByteArray): String =
    if (bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "png"
    else if (url.substringBefore('?').endsWith(".webp") || (bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte())) "webp"
    else "jpg"

/**
 * Downloads the first candidate that works and saves it with [save] (CDN links of Instagram / Facebook expire,
 * so a local copy is kept). Falls back to [snapshot] (the reader's capture of the post media).
 */
suspend fun downloadCover(candidates: List<String>, snapshot: ByteArray?, save: (ByteArray, String) -> String?): SavedCover? {
    for (u in candidates) {
        val bytes = runCatching { httpBytes(u, BROWSER_USER_AGENT) }.getOrNull() ?: continue
        if (bytes.size < MIN_COVER_BYTES) continue
        val file = save(bytes, imageExt(u, bytes)) ?: continue
        return SavedCover(file, u)
    }
    if (snapshot != null && snapshot.size >= MIN_COVER_BYTES) {
        save(snapshot, if (snapshot.size > 1 && snapshot[0] == 0x89.toByte()) "png" else "jpg")?.let { return SavedCover(it, "") }
    }
    return null
}

/** The second carousel slide (Instagram), kept as an extra photo. */
fun secondSlide(post: SocialPost?): String? = post?.slides?.getOrNull(1)

private var backfillDone = false

/**
 * Once per app session: gives properties saved without a photo a real cover from their source (YouTube thumbnail,
 * Instagram / Facebook post media) and replaces placeholder titles ("Instagram") using the caption.
 */
suspend fun backfillCovers(repo: com.hark.sothudiary.data.JsonRepository, platform: com.hark.sothudiary.PlatformActions) {
    if (backfillDone) return
    backfillDone = true
    val settings = repo.data.settings
    val todo = repo.data.properties.filter {
        it.sourceUrl.startsWith("http") && (it.imageFile.isBlank() || com.hark.sothudiary.domain.isPlaceholderTitle(it.title))
    }.take(25)
    for (p in todo) {
        val plat = platformOf(p.sourceUrl)
        val needTitle = com.hark.sothudiary.domain.isPlaceholderTitle(p.title)
        val post = if (plat == SourcePlatform.YOUTUBE && !needTitle) null
        else runCatching { readPost(p.sourceUrl, settings.youtubeApiKey) }.getOrNull()
        val cover = if (p.imageFile.isBlank()) downloadCover(coverCandidates(p.sourceUrl, post), null) { b, e -> platform.saveImage(b, e) } else null
        val extra = if (p.photos.isEmpty()) secondSlide(post)?.let { u ->
            runCatching { httpBytes(u, BROWSER_USER_AGENT) }.getOrNull()?.let { platform.saveImage(it, imageExt(u, it)) }
        } else null
        val text = listOfNotNull(post?.caption, post?.title).joinToString("\n")
        val title = if (needTitle) com.hark.sothudiary.domain.smartTitle(p.sourceCaption.ifBlank { text }, settings.lang)
            ?: com.hark.sothudiary.domain.smartTitle(text, settings.lang) else null
        if (cover != null || extra != null || title != null) repo.setCover(p.id, cover?.file, cover?.url, extra, title, post?.caption)
    }
}
