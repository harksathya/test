package com.hark.sothudiary.domain.share

import com.hark.sothudiary.data.Broker
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.GeoPt
import com.hark.sothudiary.data.LandCheck
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.domain.land.landCheckKey
import com.hark.sothudiary.domain.land.thin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/**
 * 2.3.0 "Share property": the property travels inside the link itself – no server. Its data (JSON, raw-deflate
 * compressed, base64url) is put in the URL fragment of a small static page on GitHub Pages:
 *
 *   https://harksathya.github.io/test/p/#1.<data>
 *
 * The fragment never reaches a server. The page shows a preview, opens the app through the custom scheme
 * `sothudiary://p?d=1.<data>` (Android intent with the APK download as fallback) and offers the APK.
 */
object ShareLink {
    const val PAGE = "https://harksathya.github.io/test/p/"
    const val SCHEME = "sothudiary"
    const val APK_URL = "https://github.com/harksathya/test/releases/download/sothu-diary-latest/SothuDiary.apk"
    /** Format version, the part before the dot. */
    const val VERSION = "1"

    fun httpsLink(code: String) = "$PAGE#$code"
    fun appLink(code: String) = "$SCHEME://p?d=$code"
}

/** What a shared link carries. Short field names keep the link short. */
@Serializable
data class SharedProperty(
    val p: Property,
    /** The land check made for this property (thinned), when the sender included it. */
    val lc: LandCheck? = null,
    /** Broker name / phone, when the sender included them. */
    val bn: String = "",
    val bp: String = "",
    /** Who shared it (optional, typed by the sender). */
    val by: String = "",
)

/** What the sender chose to include. */
data class ShareOptions(val notes: Boolean = false, val landCheck: Boolean = true, val broker: Boolean = false, val sharedBy: String = "")

private val shareJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false; coerceInputValues = true }

private fun r6(v: Double) = (v * 1_000_000).roundToLong() / 1_000_000.0
private fun GeoPt.r() = GeoPt(r6(lat), r6(lng))

/** The property as it travels: no local files, folders, private notes (unless chosen), status or verified docs. */
fun sharedCopy(p: Property, land: LandCheck?, broker: Broker?, o: ShareOptions): SharedProperty {
    val clean = p.copy(
        lat = p.lat?.let(::r6), lng = p.lng?.let(::r6),
        imageFile = "", photos = emptyList(), folderIds = emptySet(), brokerId = null, status = DealStatus.NEW, docs = emptySet(),
        // 2.8.0: uploaded documents and their check are private (names, values) and their files stay on this phone.
        documents = emptyList(), docAnalysis = null, docTexts = emptyList(), docChat = emptyList(),
        notes = if (o.notes) p.notes.take(1500) else "",
        sourceCaption = p.sourceCaption.take(700),
        reviews = p.reviews.take(3).map { it.copy(text = it.text.take(220)) },
        postClaims = p.postClaims, sample = false, sketch = null, boundary = p.boundary.map { it.r() },
        createdAt = 0, updatedAt = 0, importedAt = 0,
    )
    val lc = if (o.landCheck && land != null && land.osmOk) land.copy(
        plot = land.plot.map { it.r() },
        waterOutlines = land.waterOutlines.take(3).map { w -> thin(w, 40).map { it.r() } },
        features = land.features.take(40).map { it.copy(lat = it.lat?.let(::r6), lng = it.lng?.let(::r6)) },
        sources = land.sources.take(6), webSnippets = emptyList(),
    ) else null
    return SharedProperty(clean, lc, if (o.broker) broker?.name.orEmpty() else "", if (o.broker) broker?.phone.orEmpty() else "", o.sharedBy.take(60))
}

/** Encodes a shared property: "1." + base64url(raw deflate(JSON)). */
fun encodeShare(sp: SharedProperty): String =
    ShareLink.VERSION + "." + base64UrlEncode(deflateRaw(shareJson.encodeToString(SharedProperty.serializer(), sp).encodeToByteArray()))

/** Decodes a code made by [encodeShare]; null when it is broken or from an unknown future format. */
fun decodeShare(code: String): SharedProperty? {
    val c = code.trim()
    if (!c.startsWith(ShareLink.VERSION + ".")) return null
    val bytes = base64UrlDecode(c.substringAfter('.')) ?: return null
    val json = inflateRaw(bytes)?.decodeToString() ?: return null
    return runCatching { shareJson.decodeFromString(SharedProperty.serializer(), json) }.getOrNull()
        ?.takeIf { it.p.id.isNotBlank() && it.p.title.isNotBlank() }
}

private val codeRe = Regex("""(?:#|[?&]d=)(${ShareLink.VERSION}\.[A-Za-z0-9_-]{8,})""")

/** Finds a share code in a link or a pasted WhatsApp message (https page link or sothudiary:// link). */
fun extractShareCode(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val t = text.trim()
    if (!t.contains("harksathya.github.io/test/p") && !t.contains("${ShareLink.SCHEME}://")) return null
    return codeRe.find(t)?.groupValues?.get(1)
}

/** The WhatsApp-friendly message: title, price, size, place, then the link. */
fun shareMessage(p: Property, link: String, priceText: String, sizeText: String, placeText: String, intro: String, openHint: String): String =
    listOf(
        "🏡 " + p.title,
        listOf(priceText, sizeText).filter { it.isNotBlank() }.joinToString(" · "),
        placeText.takeIf { it.isNotBlank() }?.let { "📍 $it" },
        p.surveyNumber.takeIf { it.isNotBlank() }?.let { "S.F. No. $it" },
        "",
        intro,
        link,
        openHint,
    ).filterNotNull().joinToString("\n").replace("\n\n\n", "\n\n").trim()

/** How a shared property is added. */
enum class ImportMode { ADD_NEW, UPDATE_EXISTING }

/**
 * The property to save for an import. ADD_NEW: a new property (a new [newId] when the id is taken), status New,
 * the sender's notes kept as notes. UPDATE_EXISTING: the user's own record keeps its status, notes, folders, broker,
 * documents, photos and voice notes – only the listing details are refreshed from the shared copy.
 */
fun mergeShared(existing: Property?, incoming: Property, mode: ImportMode, now: Long, newId: () -> String, sharedBy: String = ""): Property {
    val noteFromSender = incoming.notes.takeIf { it.isNotBlank() }?.let { n -> (if (sharedBy.isNotBlank()) "[$sharedBy] " else "") + n }.orEmpty()
    if (mode == ImportMode.UPDATE_EXISTING && existing != null) {
        return existing.copy(
            title = incoming.title, type = incoming.type, locality = incoming.locality, taluk = incoming.taluk, district = incoming.district,
            address = incoming.address, lat = incoming.lat ?: existing.lat, lng = incoming.lng ?: existing.lng,
            size = incoming.size, unit = incoming.unit, askingPrice = incoming.askingPrice,
            sourceUrl = incoming.sourceUrl.ifBlank { existing.sourceUrl }, sourcePlatform = if (incoming.sourceUrl.isBlank()) existing.sourcePlatform else incoming.sourcePlatform,
            sourceCaption = incoming.sourceCaption.ifBlank { existing.sourceCaption },
            imageUrl = incoming.imageUrl.ifBlank { existing.imageUrl },
            boundary = incoming.boundary.ifEmpty { existing.boundary },
            surveyNumber = incoming.surveyNumber.ifBlank { existing.surveyNumber },
            // 2.4.0: the shared land details travel too (district, taluk, village, SRO, survey / subdivision, patta).
            land = if (incoming.land.isEmpty) existing.land else incoming.land.copy(autoFilled = emptySet()),
            reviews = if (existing.reviews.isEmpty()) incoming.reviews else existing.reviews,
            notes = if (existing.notes.isBlank()) noteFromSender else existing.notes,
            updatedAt = now, importedAt = now,
        )
    }
    val id = if (existing != null) newId() else incoming.id
    return incoming.copy(
        id = id, status = DealStatus.NEW, notes = noteFromSender, folderIds = emptySet(), brokerId = null, docs = emptySet(),
        imageFile = "", photos = emptyList(), sketch = null, sample = false, createdAt = now, updatedAt = now, importedAt = now,
        documents = emptyList(), docAnalysis = null, docTexts = emptyList(), docChat = emptyList(),
    )
}

/** The shared land check re-keyed for the saved property (it stays valid while the pin / corners / size match). */
fun importedLandCheck(lc: LandCheck?, saved: Property): LandCheck? = lc?.takeIf { it.osmOk }?.copy(propertyId = saved.id, key = landCheckKey(saved))

// ---------------- base64url without padding (no platform API needed) ----------------

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

fun base64UrlEncode(data: ByteArray): String {
    val sb = StringBuilder((data.size + 2) / 3 * 4)
    var i = 0
    while (i + 2 < data.size) {
        val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
        sb.append(B64[n shr 18 and 63]).append(B64[n shr 12 and 63]).append(B64[n shr 6 and 63]).append(B64[n and 63])
        i += 3
    }
    val rest = data.size - i
    if (rest == 1) {
        val n = (data[i].toInt() and 0xFF) shl 16
        sb.append(B64[n shr 18 and 63]).append(B64[n shr 12 and 63])
    } else if (rest == 2) {
        val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
        sb.append(B64[n shr 18 and 63]).append(B64[n shr 12 and 63]).append(B64[n shr 6 and 63])
    }
    return sb.toString()
}

/** Accepts base64url or standard base64, with or without padding; null on a bad character. */
fun base64UrlDecode(text: String): ByteArray? {
    val s = text.trim().trimEnd('=').replace('+', '-').replace('/', '_')
    val out = ArrayList<Byte>(s.length * 3 / 4)
    var buf = 0
    var bits = 0
    for (ch in s) {
        val v = B64.indexOf(ch)
        if (v < 0) return null
        buf = (buf shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out += ((buf shr bits) and 0xFF).toByte()
        }
    }
    return out.toByteArray()
}

/** Raw DEFLATE (no zlib header) – what the browser's DecompressionStream("deflate-raw") reads on the share page. */
expect fun deflateRaw(data: ByteArray): ByteArray

/** Inflates raw DEFLATE; null when the data is broken. */
expect fun inflateRaw(data: ByteArray): ByteArray?
