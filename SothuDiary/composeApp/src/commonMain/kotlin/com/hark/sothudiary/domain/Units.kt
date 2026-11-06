package com.hark.sothudiary.domain

import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.SourcePlatform
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val SQFT_PER_CENT = 435.6
const val CENTS_PER_ACRE = 100.0

fun toCents(size: Double, unit: AreaUnit): Double = when (unit) {
    AreaUnit.CENTS -> size
    AreaUnit.ACRES -> size * CENTS_PER_ACRE
    AreaUnit.SQFT -> size / SQFT_PER_CENT
}

fun toSqft(size: Double, unit: AreaUnit): Double = toCents(size, unit) * SQFT_PER_CENT

val Property.cents: Double get() = toCents(size, unit)
val Property.sqft: Double get() = toSqft(size, unit)

fun Property.pricePerCent(): Double? = if (askingPrice > 0 && cents > 0) askingPrice / cents else null
fun Property.pricePerSqft(): Double? = if (askingPrice > 0 && sqft > 0) askingPrice / sqft else null

/** Houses and apartments are compared per sq ft (built-up); land is compared per cent. */
enum class PriceBasis { PER_CENT, PER_SQFT }

fun PropertyType.basis(): PriceBasis = when (this) {
    PropertyType.HOUSE, PropertyType.APARTMENT -> PriceBasis.PER_SQFT
    else -> PriceBasis.PER_CENT
}

/** Types that are compared with each other (a plot is compared with land, farmland with agricultural land). */
fun PropertyType.group(): Int = when (this) {
    PropertyType.LAND, PropertyType.PLOT -> 0
    PropertyType.FARMLAND, PropertyType.AGRI_LAND -> 1
    PropertyType.HOUSE -> 2
    PropertyType.APARTMENT -> 3
}

fun Property.unitPrice(): Double? = when (type.basis()) {
    PriceBasis.PER_CENT -> pricePerCent()
    PriceBasis.PER_SQFT -> pricePerSqft()
}

fun normalizeLocation(s: String): String =
    s.lowercase().filter { it.isLetterOrDigit() }

fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    fun rad(d: Double) = d * PI / 180.0
    val dLat = rad(lat2 - lat1)
    val dLng = rad(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}

private val urlRegex = Regex("""https?://\S+""")

fun extractUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

fun detectPlatform(url: String): SourcePlatform {
    val u = url.lowercase()
    return when {
        u.isBlank() -> SourcePlatform.NONE
        "youtube.com" in u || "youtu.be" in u -> SourcePlatform.YOUTUBE
        "instagram.com" in u || "instagr.am" in u -> SourcePlatform.INSTAGRAM
        "facebook.com" in u || "fb.watch" in u || "fb.com" in u || "fb.me" in u -> SourcePlatform.FACEBOOK
        "wa.me" in u || "whatsapp" in u -> SourcePlatform.WHATSAPP
        else -> SourcePlatform.OTHER
    }
}

/** Digits only, Indian mobile normalised to 91XXXXXXXXXX for wa.me links. */
fun waNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when {
        digits.length == 10 -> "91$digits"
        digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
        else -> digits
    }
}
