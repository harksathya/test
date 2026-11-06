package com.hark.sothudiary.domain

import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.i18n.tr

private val placeholderRe = Regex(
    """^(?:(?:instagram|facebook|fb|youtube|yt|whatsapp|insta)(?:\s+(?:post|reel|reels|video|photo|story|shorts|watch|page))?|reels?|video|post|photo|shorts|watch|link|untitled|log in or sign up to view|login|log in)$""",
    RegexOption.IGNORE_CASE,
)

/** True for titles that say nothing about the property: blank, a bare source name ("Instagram"), a link. */
fun isPlaceholderTitle(title: String?): Boolean {
    val t = title?.trim()?.trim('"', '“', '”', '.', ':', '-', '|', ' ') ?: return true
    if (t.isEmpty()) return true
    if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.")) return true
    if (placeholderRe.matches(t)) return true
    // "Instagram post by x" / "x on Instagram" with nothing else
    if (Regex("""^(?:instagram|facebook|youtube)\s+(?:post|reel|video)\s+by\s+\S+$""", RegexOption.IGNORE_CASE).matches(t)) return true
    if (Regex("""^\S+\s+on\s+(?:instagram|facebook)$""", RegexOption.IGNORE_CASE).matches(t)) return true
    return t.count { it.isLetter() } < 3
}

private fun unitWord(u: AreaUnit, lang: Lang): String = if (lang == Lang.TA) u.tr().of(lang) else when (u) {
    AreaUnit.CENTS -> "cent"
    AreaUnit.ACRES -> "acre"
    AreaUnit.SQFT -> "sq ft"
}

private fun typeWord(t: PropertyType, lang: Lang): String = if (lang == Lang.TA) t.tr().of(lang) else when (t) {
    PropertyType.LAND -> "land"
    PropertyType.PLOT -> "plot"
    PropertyType.HOUSE -> "house"
    PropertyType.APARTMENT -> "flat"
    PropertyType.FARMLAND -> "farmland"
    PropertyType.AGRI_LAND -> "agricultural land"
}

/** "1.37 acre farmland, Panpoli" from parsed facts; null when there are too few facts. */
fun factsTitle(size: Double?, unit: AreaUnit?, type: PropertyType?, place: String?, lang: Lang, requireSize: Boolean = true): String? {
    if (requireSize && size == null) return null
    if (size == null && type == null) return null
    if (type == null && place.isNullOrBlank()) return null
    val head = listOfNotNull(
        size?.let { dec(it, 2) + " " + unitWord(unit ?: AreaUnit.CENTS, lang) },
        type?.let { typeWord(it, lang) } ?: if (lang == Lang.TA) "நிலம்" else "land",
    ).joinToString(" ")
    val h = if (lang == Lang.EN) head.replaceFirstChar { it.uppercaseChar() } else head
    return if (place.isNullOrBlank()) h else "$h, ${place.trim()}"
}

/**
 * A title for a property read from a post: the parsed facts ("1.37 acre farmland, Panpoli") when size and
 * type / place are known, else the caption's first meaningful line, else partial facts. Never a bare source name.
 */
fun smartTitle(text: String?, lang: Lang = Lang.EN): String? {
    if (text.isNullOrBlank()) return null
    val pl = ListingParser.parse(text)
    val place = pl.placeText ?: pl.district
    factsTitle(pl.size, pl.unit, pl.type, place, lang)?.let { return it }
    pl.title?.takeIf { it.count(Char::isLetter) >= 6 && !isPlaceholderTitle(it) }?.let { return it.take(80) }
    return factsTitle(null, null, pl.type, place, lang, requireSize = false)
}

/** Replaces a placeholder title ("Instagram") with one made from the caption, or type + place. */
fun repairTitle(p: Property, lang: Lang = Lang.EN): Property {
    if (!isPlaceholderTitle(p.title)) return p
    val t = smartTitle(p.sourceCaption, lang)
        ?: factsTitle(p.size.takeIf { it > 0 }, p.unit, p.type, p.locality.ifBlank { p.district }.ifBlank { null }, lang, requireSize = false)
        ?: return p
    return p.copy(title = t)
}
