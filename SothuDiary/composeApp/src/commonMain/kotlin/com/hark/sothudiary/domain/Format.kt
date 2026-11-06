package com.hark.sothudiary.domain

import com.hark.sothudiary.data.Lang
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formats a number with at most [maxFrac] decimals, trimming trailing zeros. */
fun dec(value: Double, maxFrac: Int = 2): String {
    if (value.isNaN() || value.isInfinite()) return "-"
    var factor = 1L
    repeat(maxFrac) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val intPart = scaled / factor
    var frac = (scaled % factor).toString().padStart(maxFrac, '0').trimEnd('0')
    if (maxFrac == 0) frac = ""
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return sign + intPart.toString() + if (frac.isNotEmpty()) ".$frac" else ""
}

/** Indian digit grouping: 1,23,45,678 */
fun indianGroup(n: Long): String {
    val neg = n < 0
    val s = abs(n).toString()
    if (s.length <= 3) return (if (neg) "-" else "") + s
    val last3 = s.takeLast(3)
    var rest = s.dropLast(3)
    val parts = mutableListOf<String>()
    while (rest.length > 2) {
        parts.add(0, rest.takeLast(2))
        rest = rest.dropLast(2)
    }
    if (rest.isNotEmpty()) parts.add(0, rest)
    return (if (neg) "-" else "") + parts.joinToString(",") + "," + last3
}

fun rupees(n: Long): String = "₹" + indianGroup(n)

/** Short price in lakhs / crores: ₹45 L, ₹1.25 Cr (Tamil: லட்சம் / கோடி). */
fun priceShort(n: Long, lang: Lang): String {
    val v = n.toDouble()
    return when {
        n >= 1_00_00_000 -> "₹" + dec(v / 1_00_00_000, 2) + if (lang == Lang.TA) " கோடி" else " Cr"
        n >= 1_00_000 -> "₹" + dec(v / 1_00_000, 2) + if (lang == Lang.TA) " லட்சம்" else " L"
        else -> rupees(n)
    }
}

/** Unit prices like ₹9.2 L or ₹4,842 */
fun unitPriceShort(v: Double, lang: Lang): String = priceShort(v.roundToLong(), lang)

private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val monthsTa = listOf("ஜன", "பிப்", "மார்", "ஏப்", "மே", "ஜூன்", "ஜூலை", "ஆக", "செப்", "அக்", "நவ", "டிச")

fun localDate(ms: Long): LocalDate =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date

fun formatDate(ms: Long, lang: Lang): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val m = (if (lang == Lang.TA) monthsTa else monthsEn)[dt.monthNumber - 1]
    return "${dt.dayOfMonth} $m ${dt.year}"
}

fun formatDateShort(ms: Long, lang: Lang): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val m = (if (lang == Lang.TA) monthsTa else monthsEn)[dt.monthNumber - 1]
    return "${dt.dayOfMonth} $m"
}

fun formatDateTime(ms: Long, lang: Lang): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val h12 = if (dt.hour % 12 == 0) 12 else dt.hour % 12
    val ampm = if (dt.hour < 12) "AM" else "PM"
    return formatDate(ms, lang) + ", " + h12 + ":" + dt.minute.toString().padStart(2, '0') + " " + ampm
}

/** Parses "45,00,000" / "4500000" / "45L" / "45 lakh" / "1.2cr" into rupees. */
fun parsePrice(text: String): Long? {
    val t = text.lowercase().replace(",", "").replace("₹", "").replace("rs", "").trim()
    if (t.isEmpty()) return null
    val num = Regex("""[0-9]+(\.[0-9]+)?""").find(t)?.value?.toDoubleOrNull() ?: return null
    val mult = when {
        "cr" in t || "கோடி" in t -> 1_00_00_000.0
        "l" in t || "லட்ச" in t -> 1_00_000.0
        "k" in t -> 1_000.0
        else -> 1.0
    }
    return (num * mult).roundToLong()
}

fun parseDouble(text: String): Double? = text.trim().replace(",", "").toDoubleOrNull()

/** Date pickers work in UTC midnight; convert a picked day to 10:00 local time on that day. */
fun pickerUtcToLocal(utcMs: Long): Long {
    val day = Instant.fromEpochMilliseconds(utcMs).toLocalDateTime(TimeZone.UTC).date
    return day.atTime(10, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

/** Local timestamp -> UTC midnight of the same local day (what the date picker expects). */
fun localToPickerUtc(ms: Long): Long = localDate(ms).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** Local timestamp for [days] from today at 10:00. */
fun daysFromToday(days: Int, now: Long): Long {
    val d = localDate(now).toEpochDays() + days
    return LocalDate.fromEpochDays(d).atTime(10, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

/** Whole days from today to the date of [ms] (negative = past). */
fun daysUntil(ms: Long, now: Long): Int = localDate(ms).toEpochDays() - localDate(now).toEpochDays()
