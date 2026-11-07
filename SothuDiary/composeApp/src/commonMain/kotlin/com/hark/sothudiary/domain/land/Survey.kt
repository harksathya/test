package com.hark.sothudiary.domain.land

/** 2.3.0: a Tamil Nadu survey field number with its sub-division, e.g. 123 / 4B (S.F. No. 123/4B). */
data class SurveyNo(val number: String, val subdivision: String = "") {
    override fun toString() = if (subdivision.isBlank()) number else "$number/$subdivision"
}

private val surveyRe = Regex(
    """(?i)(?:s\.?\s*f\.?\s*(?:no\.?)?|sy\.?\s*(?:no\.?)?|survey\s*(?:no\.?|number)?|புல\s*எண்|சர்வே\s*எண்)?\s*[:.\-]?\s*(\d{1,5})(?:\s*[/\-]\s*([0-9]{0,3}\s*[A-Za-z]{0,2}\s*[0-9]{0,3}))?"""
)

/**
 * Reads a survey number typed or pasted by the user: "S.F. No. 123/4B", "Sy.No 45/2A1", "புல எண் 67/3", "123 - 4 b",
 * "88". Returns null when there is no number. Letters are upper-cased and spaces removed ("4 b" -> "4B").
 */
fun parseSurveyNumber(text: String?): SurveyNo? {
    val t = text?.trim().orEmpty()
    if (t.isBlank()) return null
    val m = surveyRe.find(t) ?: return null
    val num = m.groupValues[1].trimStart('0').ifBlank { "0" }
    val sub = m.groupValues.getOrNull(2).orEmpty().replace(" ", "").uppercase()
    return SurveyNo(num, sub)
}

/** The survey number as saved: normalised when it can be read, else the user's text as typed (trimmed). */
fun normalizeSurveyNumber(text: String): String = parseSurveyNumber(text)?.toString() ?: text.trim()
