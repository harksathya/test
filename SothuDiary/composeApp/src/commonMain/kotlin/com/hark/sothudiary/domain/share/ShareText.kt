package com.hark.sothudiary.domain.share

import com.hark.sothudiary.AppName
import com.hark.sothudiary.data.Property
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/**
 * 2.4.0: the text that goes with a shared property – it must read well for someone WITHOUT the app (WhatsApp,
 * Instagram, Facebook …): bilingual labels, the place with a Google Maps link, the land details, the check results,
 * the original post and (only when the user chooses) the broker. The app link at the end opens it in the app.
 */
data class ShareFacts(
    val title: String,
    val price: String,
    val unitPrice: String,
    val area: String,
    val place: String,
    val lat: Double?,
    val lng: Double?,
    val land: String,
    val checks: List<String>,
    val sourceUrl: String,
    val broker: String,
    val appLink: String,
)

private fun r6(v: Double) = (v * 1_000_000).roundToLong() / 1_000_000.0

fun mapsLink(lat: Double, lng: Double) = "https://www.google.com/maps/search/?api=1&query=${r6(lat)},${r6(lng)}"

fun buildShareText(f: ShareFacts): String = buildList {
    add("🏡 " + f.title)
    if (f.price.isNotBlank()) add("💰 Price / விலை: " + f.price + (if (f.unitPrice.isNotBlank()) "  (" + f.unitPrice + ")" else ""))
    if (f.area.isNotBlank()) add("📐 Area / பரப்பு: " + f.area)
    if (f.place.isNotBlank()) add("📍 " + f.place)
    if (f.lat != null && f.lng != null) add("🗺️ " + mapsLink(f.lat, f.lng))
    if (f.land.isNotBlank()) add("📄 Land / நிலம்: " + f.land)
    if (f.checks.isNotEmpty()) {
        add("✅ Checks / ஆய்வுகள்:")
        f.checks.forEach { add("   • $it") }
    }
    if (f.sourceUrl.isNotBlank()) add("🔗 Post / பதிவு: " + f.sourceUrl)
    if (f.broker.isNotBlank()) add("👤 Broker / தரகர்: " + f.broker)
    if (f.appLink.isNotBlank()) {
        add("")
        add("📲 ${AppName.EN} / ${AppName.TA}: " + f.appLink)
    }
}.joinToString("\n")

/** File extension of a property file sent from one phone to another. */
const val SOTHU_FILE_EXT = "sothu"
const val SOTHU_MIME = "application/vnd.sothudiary+json"

/** The content of a .sothu file: a small JSON with the same share code the link carries. */
fun shareFileText(code: String, title: String): String = buildJsonObject {
    put("app", "sothudiary")
    put("v", 1)
    put("title", title)
    put("d", code)
}.toString()

/** The share code in a .sothu file (or in any text holding a share link); null when it is not one of ours. */
fun codeFromFileText(text: String?): String? {
    val t = text?.trim().orEmpty()
    if (t.isBlank()) return null
    if (t.startsWith("{")) {
        val o = runCatching { Json.parseToJsonElement(t) as? JsonObject }.getOrNull()
        if (o != null && (o["app"] as? JsonPrimitive)?.contentOrNull == "sothudiary") return (o["d"] as? JsonPrimitive)?.contentOrNull
    }
    return extractShareCode(t)
}

/** A file name from the title ("10 cent plot, Panpoli.sothu"). */
fun shareFileName(title: String): String =
    title.replace(Regex("""[\\/:*?"<>|\n\r]"""), " ").trim().take(40).ifBlank { "property" } + "." + SOTHU_FILE_EXT

@Suppress("unused")
private fun keep(p: Property) = p
