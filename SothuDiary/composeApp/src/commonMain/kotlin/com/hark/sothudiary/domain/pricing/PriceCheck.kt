package com.hark.sothudiary.domain.pricing

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.GuidelineValue
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.domain.PriceBasis
import com.hark.sothudiary.domain.SQFT_PER_CENT
import com.hark.sothudiary.domain.basis
import com.hark.sothudiary.domain.group
import com.hark.sothudiary.domain.haversineKm
import com.hark.sothudiary.domain.normalizeLocation
import com.hark.sothudiary.domain.unitPrice

enum class Verdict { SUSPICIOUS_LOW, GOOD, FAIR, HIGH, TOO_HIGH, UNKNOWN }

data class Quote(val at: Long, val price: Long)

data class PriceCheck(
    val basis: PriceBasis,
    val unitPrice: Double?,
    val guideline: Double?,
    val guidelineLocation: String?,
    val marketMedian: Double?,
    val comparableCount: Int,
    val diffVsMarketPct: Double?,
    val diffVsGuidelinePct: Double?,
    val belowGuideline: Boolean,
    val verdict: Verdict,
    val quotes: List<Quote>,
)

object PriceRules {
    const val NEARBY_KM = 5.0
    const val MIN_COMPARABLES = 2
    const val SUSPICIOUS_RATIO = 0.70
    const val FAIR_BAND = 0.15
    const val TOO_HIGH_RATIO = 1.40
}

fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    val mid = s.size / 2
    return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
}

private fun sameArea(a: Property, b: Property): Boolean {
    val la = normalizeLocation(a.locality)
    val lb = normalizeLocation(b.locality)
    if (la.isNotEmpty() && la == lb) return true
    val aLat = a.lat; val aLng = a.lng; val bLat = b.lat; val bLng = b.lng
    if (aLat != null && aLng != null && bLat != null && bLng != null) {
        return haversineKm(aLat, aLng, bLat, bLng) <= PriceRules.NEARBY_KM
    }
    return false
}

private fun locMatches(loc: String, target: String): Boolean {
    val a = normalizeLocation(loc)
    val b = normalizeLocation(target)
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    return (a.length >= 4 && b.length >= 4) && (a.contains(b) || b.contains(a))
}

fun findGuideline(p: Property, guidelines: List<GuidelineValue>): GuidelineValue? =
    guidelines.firstOrNull { locMatches(it.location, p.locality) }
        ?: guidelines.firstOrNull { locMatches(it.location, p.taluk) }

fun guidelineFor(basis: PriceBasis, g: GuidelineValue): Double? = when (basis) {
    PriceBasis.PER_CENT -> g.perCent ?: g.perSqft?.let { it * SQFT_PER_CENT }
    PriceBasis.PER_SQFT -> g.perSqft ?: g.perCent?.let { it / SQFT_PER_CENT }
}

fun quoteHistory(p: Property, data: AppData): List<Quote> =
    data.interactions
        .filter { it.propertyId == p.id && it.quotedPrice != null && it.quotedPrice > 0 }
        .sortedBy { it.at }
        .map { Quote(it.at, it.quotedPrice ?: 0) }

fun checkPrice(p: Property, data: AppData): PriceCheck {
    val basis = p.type.basis()
    val unit = p.unitPrice()
    val g = findGuideline(p, data.guidelines)
    val gv = g?.let { guidelineFor(basis, it) }?.takeIf { it > 0 }

    val comparables = data.properties.filter { o ->
        o.id != p.id && o.type.group() == p.type.group() && o.unitPrice() != null && sameArea(p, o)
    }
    val med = if (comparables.size >= PriceRules.MIN_COMPARABLES) median(comparables.mapNotNull { it.unitPrice() }) else null

    val ratio = if (unit != null && med != null && med > 0) unit / med else null
    val belowGuideline = unit != null && gv != null && unit < gv

    val verdict = when {
        unit == null -> Verdict.UNKNOWN
        belowGuideline -> Verdict.SUSPICIOUS_LOW
        ratio == null -> Verdict.UNKNOWN
        ratio < PriceRules.SUSPICIOUS_RATIO -> Verdict.SUSPICIOUS_LOW
        ratio < 1 - PriceRules.FAIR_BAND -> Verdict.GOOD
        ratio <= 1 + PriceRules.FAIR_BAND -> Verdict.FAIR
        ratio <= PriceRules.TOO_HIGH_RATIO -> Verdict.HIGH
        else -> Verdict.TOO_HIGH
    }

    return PriceCheck(
        basis = basis,
        unitPrice = unit,
        guideline = gv,
        guidelineLocation = g?.location,
        marketMedian = med,
        comparableCount = comparables.size,
        diffVsMarketPct = ratio?.let { (it - 1.0) * 100.0 },
        diffVsGuidelinePct = if (unit != null && gv != null) (unit / gv - 1.0) * 100.0 else null,
        belowGuideline = belowGuideline,
        verdict = verdict,
        quotes = quoteHistory(p, data),
    )
}

fun checkAll(data: AppData): Map<String, PriceCheck> = data.properties.associate { it.id to checkPrice(it, data) }

// ---------------- Worth it? (Go / Hold / Drop) ----------------

enum class Advice { GO, HOLD, DROP }

enum class ReasonCode {
    STATUS_DROPPED, STATUS_BOUGHT,
    PRICE_SUSPICIOUS, PRICE_GOOD, PRICE_FAIR, PRICE_HIGH, PRICE_TOO_HIGH, PRICE_UNKNOWN,
    DOCS_MISSING, DOCS_OK,
    PRICE_RAISED, PRICE_REDUCED,
    BROKER_FLAGGED, FOLLOWUPS_OVERDUE,
}

data class Reason(
    val code: ReasonCode,
    val severity: Advice,
    val pct: Double? = null,
    val count: Int? = null,
    val docs: List<DocItem> = emptyList(),
)

data class WorthIt(val advice: Advice, val reasons: List<Reason>)

fun keyDocs(type: PropertyType): List<DocItem> = buildList {
    add(DocItem.PATTA)
    add(DocItem.EC)
    add(DocItem.ROAD_ACCESS)
    if (type == PropertyType.PLOT || type == PropertyType.APARTMENT) add(DocItem.APPROVAL)
    if (type == PropertyType.FARMLAND || type == PropertyType.AGRI_LAND) add(DocItem.CHITTA)
}

fun worthIt(p: Property, check: PriceCheck, data: AppData, allChecks: Map<String, PriceCheck>, now: Long): WorthIt {
    val reasons = mutableListOf<Reason>()

    when (p.status) {
        DealStatus.DROPPED -> reasons += Reason(ReasonCode.STATUS_DROPPED, Advice.DROP)
        DealStatus.BOUGHT -> reasons += Reason(ReasonCode.STATUS_BOUGHT, Advice.GO)
        else -> {}
    }

    reasons += when (check.verdict) {
        Verdict.SUSPICIOUS_LOW -> Reason(ReasonCode.PRICE_SUSPICIOUS, Advice.DROP, pct = check.diffVsMarketPct ?: check.diffVsGuidelinePct)
        Verdict.TOO_HIGH -> Reason(ReasonCode.PRICE_TOO_HIGH, Advice.DROP, pct = check.diffVsMarketPct)
        Verdict.HIGH -> Reason(ReasonCode.PRICE_HIGH, Advice.HOLD, pct = check.diffVsMarketPct)
        Verdict.FAIR -> Reason(ReasonCode.PRICE_FAIR, Advice.GO, pct = check.diffVsMarketPct)
        Verdict.GOOD -> Reason(ReasonCode.PRICE_GOOD, Advice.GO, pct = check.diffVsMarketPct)
        Verdict.UNKNOWN -> Reason(ReasonCode.PRICE_UNKNOWN, Advice.HOLD, count = check.comparableCount)
    }

    val missing = keyDocs(p.type).filterNot { it in p.docs }
    reasons += if (missing.isNotEmpty()) Reason(ReasonCode.DOCS_MISSING, Advice.HOLD, docs = missing)
    else Reason(ReasonCode.DOCS_OK, Advice.GO)

    val first = check.quotes.firstOrNull()?.price
    val latest = if (p.askingPrice > 0) p.askingPrice else check.quotes.lastOrNull()?.price
    if (first != null && latest != null && first > 0) {
        val change = (latest.toDouble() / first - 1.0) * 100.0
        if (change > 5.0) reasons += Reason(ReasonCode.PRICE_RAISED, Advice.HOLD, pct = change)
        else if (change < -2.0) reasons += Reason(ReasonCode.PRICE_REDUCED, Advice.GO, pct = change)
    }

    val brokerId = p.brokerId
    if (brokerId != null) {
        val flagged = data.properties.count { o ->
            o.id != p.id && o.brokerId == brokerId && allChecks[o.id]?.verdict == Verdict.SUSPICIOUS_LOW
        }
        if (flagged > 0) reasons += Reason(ReasonCode.BROKER_FLAGGED, Advice.HOLD, count = flagged)
    }

    val overdue = data.interactions.count {
        it.propertyId == p.id && !it.followUpDone && it.followUpAt != null && it.followUpAt < now
    }
    if (overdue > 0) reasons += Reason(ReasonCode.FOLLOWUPS_OVERDUE, Advice.HOLD, count = overdue)

    val advice = when {
        p.status == DealStatus.BOUGHT -> Advice.GO
        reasons.any { it.severity == Advice.DROP } -> Advice.DROP
        reasons.any { it.severity == Advice.HOLD } -> Advice.HOLD
        else -> Advice.GO
    }
    // Show the most serious reasons first.
    val ordered = reasons.sortedBy { when (it.severity) { Advice.DROP -> 0; Advice.HOLD -> 1; Advice.GO -> 2 } }
    return WorthIt(advice, ordered)
}
