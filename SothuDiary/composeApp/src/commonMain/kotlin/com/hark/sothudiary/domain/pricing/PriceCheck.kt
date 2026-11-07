package com.hark.sothudiary.domain.pricing

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.GuidelineValue
import com.hark.sothudiary.data.OnlineRate
import com.hark.sothudiary.data.RateKind
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.domain.PriceBasis
import com.hark.sothudiary.domain.basis
import com.hark.sothudiary.domain.group
import com.hark.sothudiary.domain.haversineKm
import com.hark.sothudiary.domain.normalizeLocation
import com.hark.sothudiary.domain.unitPrice
import com.hark.sothudiary.domain.convertRate
import com.hark.sothudiary.domain.groupBasis
import com.hark.sothudiary.domain.rateUnit

enum class Verdict { SUSPICIOUS_LOW, GOOD, FAIR, HIGH, TOO_HIGH, UNKNOWN }

data class Quote(val at: Long, val price: Long)

enum class CompSource {
    /** One of the user's own listings of the same type group. */
    YOUR_LISTING,
    /** Not produced since v2.1 (no bundled dataset); kept so older code paths compile. */
    MARKET,
    /** Not produced since v2.1 (types are never mixed); kept so older code paths compile. */
    MARKET_FALLBACK,
    /** Not produced since v2.1 (no city-wide averages). */
    CITY_AVERAGE,
    /** A rate from the live lookup (Gemini + Google Search), cached in the app. */
    ONLINE,
}

enum class Confidence { HIGH, MEDIUM, LOW }

data class Comparable(
    val label: String,
    val labelTa: String?,
    val distanceKm: Double?,
    /** Rate in the estimate's basis (₹ per cent / sq ft / acre). */
    val rate: Double,
    val min: Double?,
    val max: Double?,
    val source: CompSource,
    val sourceName: String = "",
    val sourceUrl: String = "",
    val asOf: String = "",
    val lowConfidence: Boolean = false,
    val propertyId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val matchedByName: Boolean = false,
    val district: String = "",
    /** In a different district than the property (only ever used when it is inside the distance limit). */
    val otherDistrict: Boolean = false,
)

/** Where a guideline value came from. */
data class GuidelineInfo(
    val value: Double,
    val min: Double?,
    val max: Double?,
    val label: String,
    val fromUser: Boolean,
    val sourceName: String = "",
    val sourceUrl: String = "",
    val asOf: String = "",
    val distanceKm: Double? = null,
)

data class MarketEstimate(
    val basis: PriceBasis,
    val group: Int,
    val comparables: List<Comparable>,
    /** Search radius finally used (km), or null when the place has no coordinates. */
    val radiusKm: Double?,
    val median: Double?,
    val fairLow: Double?,
    val fairHigh: Double?,
    val confidence: Confidence,
    val guideline: GuidelineInfo?,
    /** The radius had to be widened beyond the normal limit for this type (warn the user). */
    val widened: Boolean = false,
    /** Normal radius for this type (km). */
    val baseRadiusKm: Double = PriceRules.baseKm(group),
    /** No usable comparables of the same type nearby: show "not enough nearby data", no verdict. */
    val insufficient: Boolean = false,
    /** The place has a map pin (distances are real) – without one only exact name matches are used. */
    val hasPin: Boolean = true,
)

data class PriceCheck(
    val basis: PriceBasis,
    val unitPrice: Double?,
    val estimate: MarketEstimate,
    val diffVsMarketPct: Double?,
    val diffVsGuidelinePct: Double?,
    val belowGuideline: Boolean,
    val verdict: Verdict,
    val quotes: List<Quote>,
) {
    val marketMedian: Double? get() = estimate.median
    val comparableCount: Int get() = estimate.comparables.size
    val guideline: Double? get() = estimate.guideline?.value
}

object PriceRules {
    /** Normal search radius (km) for every type. */
    fun baseKm(@Suppress("UNUSED_PARAMETER") group: Int): Double = BASE_KM
    /** The radius widens to [MAX_KM] when fewer than [MIN_COMPS] are found – never beyond. */
    fun steps(group: Int): List<Double> = listOf(baseKm(group), MAX_KM)
    const val BASE_KM = 5.0
    /** Hard limit: nothing beyond 10 km is ever used. */
    const val MAX_KM = 10.0
    const val MIN_COMPS = 3
    /** Live lookups are reused for this many days, then refreshed. */
    const val CACHE_DAYS = 30
    const val GUIDELINE_NEAR_KM = 5.0
    const val GUIDELINE_AGRI_NEAR_KM = 10.0
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

private fun locMatches(loc: String, target: String): Boolean {
    val a = normalizeLocation(loc)
    val b = normalizeLocation(target)
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    return (a.length >= 4 && b.length >= 4) && (a.contains(b) || b.contains(a))
}

/** Loose name match, also tolerant of common spelling variants (Thiru/Tiru, -puram/-pura, double letters). */
fun nameMatches(a: String, b: String): Boolean {
    if (locMatches(a, b)) return true
    fun loose(s: String) = normalizeLocation(s).replace("th", "t").replace("aa", "a").replace("ee", "i")
        .replace("oo", "u").replace("zh", "l").replace(Regex("(.)\\1"), "$1")
    val la = loose(a); val lb = loose(b)
    return la.length >= 5 && lb.length >= 5 && (la == lb || la.contains(lb) || lb.contains(la))
}

/** Exact-ish name equality (no substring matching) – used when the property has no map pin. */
fun sameName(a: String, b: String): Boolean {
    fun loose(s: String) = normalizeLocation(s.substringBefore("(")).replace("th", "t").replace("aa", "a").replace("ee", "i")
        .replace("oo", "u").replace("zh", "l").replace(Regex("(.)\\1"), "$1")
    val la = loose(a); val lb = loose(b)
    return la.length >= 4 && la == lb
}

fun sameDistrict(a: String, b: String): Boolean {
    if (a.isBlank() || b.isBlank()) return true
    return nameMatches(a.removeSuffix(" District").removeSuffix(" district"), b.removeSuffix(" District").removeSuffix(" district"))
}

fun findGuideline(p: Property, guidelines: List<GuidelineValue>): GuidelineValue? =
    guidelines.firstOrNull { locMatches(it.location, p.locality) }
        ?: guidelines.firstOrNull { locMatches(it.location, p.taluk) }

fun guidelineFor(basis: PriceBasis, g: GuidelineValue): Double? {
    val pc = g.perCent
    val ps = g.perSqft
    return when {
        basis == PriceBasis.PER_SQFT && ps != null -> ps
        pc != null -> convertRate(pc, PriceBasis.PER_CENT, basis)
        ps != null -> convertRate(ps, PriceBasis.PER_SQFT, basis)
        else -> null
    }
}

fun quoteHistory(p: Property, data: AppData): List<Quote> =
    data.interactions
        .filter { it.propertyId == p.id && it.quotedPrice != null && it.quotedPrice > 0 }
        .sortedBy { it.at }
        .map { Quote(it.at, it.quotedPrice ?: 0) }


/** A place to estimate the market at: a property, a locality or a searched point. */
data class AreaPoint(val names: List<String>, val lat: Double?, val lng: Double?, val district: String = "")

fun Property.areaPoint() = AreaPoint(listOf(locality).filter { it.isNotBlank() }, lat, lng, district)

private fun distance(a: AreaPoint, lat: Double?, lng: Double?): Double? {
    val aLat = a.lat; val aLng = a.lng
    return if (aLat != null && aLng != null && lat != null && lng != null) haversineKm(aLat, aLng, lat, lng) else null
}

private fun Double.orNullIfBad(): Double? = if (isNaN() || isInfinite() || this <= 0) null else this

/** An online rate in the requested basis. */
private fun OnlineRate.inBasis(basis: PriceBasis): Triple<Double?, Double?, Double?> {
    val from = when (unit.lowercase()) {
        "sqft", "sq ft", "per_sqft" -> PriceBasis.PER_SQFT
        "acre", "per_acre" -> PriceBasis.PER_ACRE
        else -> PriceBasis.PER_CENT
    }
    fun c(v: Double?) = v?.let { convertRate(it, from, basis) }
    return Triple(c(min), c(max), c(typical ?: if (min != null && max != null) (min + max) / 2 else min ?: max))
}

/**
 * Market estimate for a type group at a place – live-lookup rules (v2.1):
 *  - SAME type only (plot / land vs plot / land, farmland vs farmland, house vs house, flat vs flat);
 *  - only comparables near the place's own coordinates: 5 km, widening (with a warning) to a hard maximum of
 *    10 km when fewer than 3 are found – nothing beyond 10 km, ever; no bundled data, no city averages;
 *  - sources: the user's own saved properties and the cached live lookup (Gemini + Google Search) rates
 *    that are less than [PriceRules.CACHE_DAYS] days old;
 *  - without a map pin, or when nothing qualifies, the estimate is [MarketEstimate.insufficient]: no verdict.
 */
fun estimateMarket(
    point: AreaPoint,
    group: Int,
    data: AppData,
    excludePropertyId: String? = null,
    now: Long = com.hark.sothudiary.data.nowMillis(),
    /** The checked property's own rate in this group's basis: anything within 50 m at the same rate is that listing itself. */
    selfRate: Double? = null,
    /** The checked property's source post: an online rate quoting that same page is the listing itself. */
    selfUrl: String = "",
): MarketEstimate {
    val basis = groupBasis(group)
    val hasPin = point.lat != null && point.lng != null

    data class Cand(val c: Comparable, val dist: Double?)
    val cands = mutableListOf<Cand>()

    // (a) own saved properties of the same type group
    data.properties.filter { o -> o.id != excludePropertyId && o.type.group() == group && (o.unitPrice() ?: 0.0) > 0 }.forEach { o ->
        val d = distance(point, o.lat, o.lng)
        cands += Cand(
            Comparable(
                label = o.title, labelTa = null, distanceKm = d, rate = o.unitPrice() ?: 0.0, min = null, max = null,
                source = CompSource.YOUR_LISTING, propertyId = o.id, lat = o.lat, lng = o.lng,
                district = o.district, otherDistrict = !sameDistrict(o.district, point.district),
            ),
            d,
        )
    }

    // (b) live-lookup rates (cache) – asking prices of the same group, not older than the cache period
    data.onlineRates.filter { it.group == group && it.kind == RateKind.ASKING && isFresh(it.fetchedAt, now) }.forEach { r ->
        val (mn, mx, mid) = r.inBasis(basis)
        val m = mid?.orNullIfBad() ?: return@forEach
        val d = distance(point, r.lat, r.lng)
        cands += Cand(
            Comparable(
                label = r.place, labelTa = null, distanceKm = d, rate = m, min = mn, max = mx, source = CompSource.ONLINE,
                sourceName = r.sourceName, sourceUrl = r.sourceUrl, asOf = r.asOf, lowConfidence = true,
                lat = r.lat, lng = r.lng, district = r.district,
                otherDistrict = !sameDistrict(r.district, point.district),
            ),
            d,
        )
    }
    // Never compare a property with itself (by id, by its own source page, or a copy at the same spot and price).
    val own = cands.filterNot { isSelf(it.c, it.dist, excludePropertyId, selfRate, selfUrl) }
    // The same listing can come back from two lookups close to each other: keep one.
    val unique = own.distinctBy { c -> if (c.c.propertyId != null) c.c.propertyId else c.c.label.lowercase() + "|" + c.c.sourceUrl + "|" + c.c.rate.toLong() }

    var radiusUsed: Double? = null
    var chosen: List<Cand> = emptyList()
    if (hasPin) {
        for (r in PriceRules.steps(group)) {
            radiusUsed = r
            chosen = unique.filter { it.dist != null && it.dist <= r }
            if (chosen.size >= PriceRules.MIN_COMPS) break
        }
    }
    val comps = chosen.map { it.c }.filter { it.rate > 0 && (it.distanceKm ?: 99.0) <= PriceRules.MAX_KM }
    val widened = hasPin && comps.any { (it.distanceKm ?: 0.0) > PriceRules.BASE_KM }

    val med = median(comps.map { it.rate })
    var low = median(comps.map { it.min ?: (it.rate * 0.9) })
    var high = median(comps.map { it.max ?: (it.rate * 1.1) })
    if (med != null) {
        low = minOf(low ?: med, med)
        high = maxOf(high ?: med, med)
    }
    val near = comps.count { (it.distanceKm ?: 99.0) <= PriceRules.BASE_KM }
    val confidence = when {
        widened && near < 2 -> Confidence.LOW
        near >= 4 && comps.count { !it.lowConfidence } >= 2 -> Confidence.HIGH
        near >= 2 -> Confidence.MEDIUM
        else -> Confidence.LOW
    }

    return MarketEstimate(
        basis = basis, group = group,
        comparables = comps.sortedWith(compareBy({ it.distanceKm ?: 0.0 }, { it.label })),
        radiusKm = radiusUsed, median = med, fairLow = low, fairHigh = high, confidence = confidence,
        guideline = guidelineAt(point, group, data, now),
        widened = widened, insufficient = comps.isEmpty(), hasPin = hasPin,
    )
}

/** Within this distance (km) a comparable at the same rate as the checked property is that property itself. */
const val SELF_MATCH_KM = 0.05

/** True when [c] is the property being checked: same id, same source page, or within 50 m at the same rate. */
fun isSelf(c: Comparable, distKm: Double?, selfId: String?, selfRate: Double?, selfUrl: String = ""): Boolean {
    if (selfId != null && c.propertyId == selfId) return true
    if (selfUrl.isNotBlank() && c.sourceUrl.isNotBlank() && c.sourceUrl.trimEnd('/').equals(selfUrl.trimEnd('/'), true)) return true
    if (selfRate != null && selfRate > 0 && distKm != null && distKm < SELF_MATCH_KM) {
        val tol = maxOf(1.0, selfRate * 0.005)
        if (kotlin.math.abs(c.rate - selfRate) <= tol) return true
    }
    return false
}

/** User-entered guideline value first, then a guideline value from the live lookup near the point. */
fun guidelineAt(point: AreaPoint, group: Int, data: AppData, now: Long = com.hark.sothudiary.data.nowMillis()): GuidelineInfo? {
    val basis = groupBasis(group)
    val user = point.names.firstNotNullOfOrNull { n -> data.guidelines.firstOrNull { locMatches(it.location, n) } }
    if (user != null) {
        val v = guidelineFor(basis, user)?.orNullIfBad()
        if (v != null) return GuidelineInfo(v, null, null, user.location, fromUser = true)
    }
    // Urban street guideline values are for house sites: not comparable with built-up prices (house / apartment).
    if (group == 2 || group == 3) return null
    if (point.lat == null || point.lng == null) return null
    val nearKm = if (group == 1) PriceRules.GUIDELINE_AGRI_NEAR_KM else PriceRules.GUIDELINE_NEAR_KM
    val online = data.onlineRates.filter { it.group == group && it.kind == RateKind.GUIDELINE && isFresh(it.fetchedAt, now) }
        .mapNotNull { r -> distance(point, r.lat, r.lng)?.let { r to it } }
        .filter { it.second <= nearKm }
        .minByOrNull { it.second } ?: return null
    val (r, d) = online
    val (mn, mx, mid) = r.inBasis(basis)
    // The minimum is used as the floor: a specific street / survey number can be higher.
    val v = (mn ?: mid)?.orNullIfBad() ?: return null
    return GuidelineInfo(v, mn, mx, r.place, fromUser = false, r.sourceName, r.sourceUrl, r.asOf, d)
}

fun checkPrice(p: Property, data: AppData): PriceCheck {
    val basis = p.type.basis()
    val unit = p.unitPrice()
    val est0 = estimateMarket(p.areaPoint(), p.type.group(), data, excludePropertyId = p.id,
        selfRate = unit?.let { convertRate(it, basis, groupBasis(p.type.group())) }, selfUrl = p.sourceUrl)
    // 2.3.1: this property's own guideline value (read on TNREGINET or typed by the user) beats an area value.
    val est = com.hark.sothudiary.domain.registry.ownGuidelineInfo(p, est0.basis)?.let { est0.copy(guideline = it) } ?: est0
    val gv = est.guideline?.value
    val med = est.median
    val ratio = if (unit != null && med != null && med > 0) unit / med else null
    val belowGuideline = unit != null && gv != null && unit < gv
    val high = est.fairHigh

    val verdict = when {
        unit == null -> Verdict.UNKNOWN
        belowGuideline -> Verdict.SUSPICIOUS_LOW
        est.insufficient || ratio == null -> Verdict.UNKNOWN
        ratio < PriceRules.SUSPICIOUS_RATIO -> Verdict.SUSPICIOUS_LOW
        ratio < 1 - PriceRules.FAIR_BAND -> Verdict.GOOD
        ratio <= 1 + PriceRules.FAIR_BAND -> Verdict.FAIR
        // Still inside the published market range for the area -> fair.
        high != null && unit <= high -> Verdict.FAIR
        ratio <= PriceRules.TOO_HIGH_RATIO -> Verdict.HIGH
        else -> Verdict.TOO_HIGH
    }

    return PriceCheck(
        basis = basis,
        unitPrice = unit,
        estimate = est,
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
    /** 2.2.0: the land check found the plot on / in the buffer of a water body (only with a land check). */
    LAND_RISK_HIGH,
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

/**
 * [land]: the property's land check (2.2.0), when one was made. Without it the suggestion is exactly as before;
 * with a HIGH water / flood-buffer risk it adds one HOLD reason.
 */
fun worthIt(
    p: Property, check: PriceCheck, data: AppData, allChecks: Map<String, PriceCheck>, now: Long,
    land: com.hark.sothudiary.domain.land.LandAssessment? = null,
): WorthIt {
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

    if (land != null && land.banner.level == com.hark.sothudiary.domain.land.LandRating.POOR) {
        reasons += Reason(ReasonCode.LAND_RISK_HIGH, Advice.HOLD, pct = maxOf(land.banner.bufferPct, 0.0))
    }

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
