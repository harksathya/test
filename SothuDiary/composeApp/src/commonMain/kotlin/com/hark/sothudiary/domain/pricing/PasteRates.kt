package com.hark.sothudiary.domain.pricing

/**
 * 2.4.0: reads a rate the user copied from a page or a message ("₹1,822/sqft", "Rs 3.5 lakh per cent",
 * "Price: Rs. 36 lakhs Land area: 22 cents", "Guideline value Rs 201 per sq.ft"). Nothing is fetched: the user
 * pastes what they saw and confirms it. Used by "Add a rate you saw" on the price check.
 */
data class PastedRate(
    /** Rupees per [unit]. */
    val value: Double,
    /** "sqft", "cent" or "acre". */
    val unit: String,
    /** The text it was read from. */
    val text: String,
    /** The text calls it a guideline (government) value. */
    val guideline: Boolean = false,
    /** Worked out from a total price and an area. */
    val derived: Boolean = false,
)

private val rateRe = Regex(
    """(?i)(?:₹|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*(lakhs?|lacs?|l\b|crores?|cr\b|k\b|thousand)?\s*(?:/-)?\s*(?:/|per|a|each|-)?\s*(?:one\s+)?(sq\.?\s*-?\s*ft\.?|sqft|sq\.?\s*feet|square\s*f(?:ee|oo)t|cents?\b|acres?\b)""",
)
private val totalRe = Regex("""(?i)(?:₹|\brs\.?)\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*(lakhs?|lacs?|l\b|crores?|cr\b)""")
private val areaRe = Regex("""(?i)([0-9][0-9,]*(?:\.[0-9]+)?)\s*(cents?\b|sq\.?\s*-?\s*ft\.?|sqft|sq\.?\s*feet|acres?\b)""")

private fun multiplier(word: String): Double = when (word.lowercase().trimEnd('s')) {
    "lakh", "lac", "l" -> 1_00_000.0
    "crore", "cr" -> 1_00_00_000.0
    "k", "thousand" -> 1_000.0
    else -> 1.0
}

private fun unitOf(word: String): String {
    val w = word.lowercase()
    return when {
        "cent" in w -> "cent"
        "acre" in w -> "acre"
        else -> "sqft"
    }
}

/** Plausible land / house rates, to drop misread numbers (phone numbers, years, totals). */
private fun plausible(value: Double, unit: String): Boolean = when (unit) {
    "sqft" -> value in 50.0..60_000.0
    "cent" -> value in 5_000.0..2_00_00_000.0
    "acre" -> value in 50_000.0..1_00_00_00_000.0
    else -> false
}

private fun guidelineNear(text: String, r: IntRange): Boolean =
    text.substring(maxOf(0, r.first - 100), r.first).contains(Regex("""(?i)guide\s*line|வழிகாட்டி"""))

/** Every rate in [text], direct ones ("₹1,822/sqft") first, then total ÷ area ("Rs. 36 lakhs … 22 cents"). */
fun parsePastedRates(text: String): List<PastedRate> {
    val out = mutableListOf<PastedRate>()
    for (m in rateRe.findAll(text)) {
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
        val v = n * multiplier(m.groupValues[2])
        val u = unitOf(m.groupValues[3])
        if (plausible(v, u)) out += PastedRate(v, u, m.value.trim(), guideline = guidelineNear(text, m.range))
    }
    for (m in totalRe.findAll(text)) {
        val total = (m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue) * multiplier(m.groupValues[2])
        val tail = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 81))
        val a = areaRe.find(tail) ?: continue
        // "₹44 Lac (₹1,822/sqft)": a per-unit figure right after is read directly instead.
        if (tail.substring(0, a.range.first).contains('/')) continue
        val area = a.groupValues[1].replace(",", "").toDoubleOrNull()?.takeIf { it > 0 } ?: continue
        val u = unitOf(a.groupValues[2])
        val v = total / area
        if (plausible(v, u)) out += PastedRate(v, u, (m.value + tail.substring(0, a.range.last + 1)).replace(Regex("\\s+"), " ").trim(),
            guideline = guidelineNear(text, m.range), derived = true)
    }
    return out.distinctBy { it.unit to it.value.toLong() }
}

private const val SQFT_PER_CENT_P = 435.6

/** A rate converted between ₹ per sq ft / cent / acre ("sqft" | "cent" | "acre"). */
fun convertPastedRate(value: Double, from: String, to: String): Double {
    val perSqft = when (from) { "cent" -> value / SQFT_PER_CENT_P; "acre" -> value / (SQFT_PER_CENT_P * 100); else -> value }
    return when (to) { "cent" -> perSqft * SQFT_PER_CENT_P; "acre" -> perSqft * SQFT_PER_CENT_P * 100; else -> perSqft }
}
