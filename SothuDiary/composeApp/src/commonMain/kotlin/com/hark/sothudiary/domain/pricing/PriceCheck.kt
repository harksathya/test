package com.hark.sothudiary.domain.pricing

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.GuidelineValue
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
import com.hark.sothudiary.data.market.MarketData
import com.hark.sothudiary.data.market.MarketDataset
import com.hark.sothudiary.data.market.MarketLocality
import com.hark.sothudiary.data.market.MarketRates
import com.hark.sothudiary.data.market.RateRange
import com.hark.sothudiary.data.market.TypeRate
import com.hark.sothudiary.data.market.rangeIn

enum class Verdict { SUSPICIOUS_LOW, GOOD, FAIR, HIGH, TOO_HIGH, UNKNOWN }

data class Quote(val at: Long, val price: Long)

enum class CompSource {
    /** One of the user's own listings of the same type group. */
    YOUR_LISTING,
    /** Bundled market data for a nearby / matching locality, same type. */
    MARKET,
    /** Bundled market data of a related type (e.g. the locality's overall average) – low weight. */
    MARKET_FALLBACK,
    /** No nearby data: average of all localities in the dataset for this type. */
    CITY_AVERAGE,
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
)

data class MarketEstimate(
    val basis: PriceBasis,
    val group: Int,
    val comparables: List<Comparable>,
    /** Radius used for the user's own listings, if coordinates were known. */
    val radiusKm: Double?,
    val median: Double?,
    val fairLow: Double?,
    val fairHigh: Double?,
    val confidence: Confidence,
    val guideline: GuidelineInfo?,
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
    val RADII_KM = listOf(3.0, 5.0, 10.0)
    const val MIN_OWN = 4
    const val MARKET_MAX = 5
    const val MARKET_MIN = 3
    const val MARKET_NEAR_KM = 15.0
    const val GUIDELINE_NEAR_KM = 3.0
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
        .replace("oo", "u").replace(Regex("(.)\\1"), "$1")
    val la = loose(a); val lb = loose(b)
    return la.length >= 5 && lb.length >= 5 && (la == lb || la.contains(lb) || lb.contains(la))
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

/** The dataset entry for a type group, plus whether it is a fallback type. */
fun MarketRates.forGroup(group: Int): Pair<TypeRate, Boolean>? = when (group) {
    0 -> plot?.let { it to false }
    1 -> farmland?.let { it to false }
    2 -> house?.let { it to false } ?: apartment?.let { it to true } ?: overall?.let { it to true }
    3 -> apartment?.let { it to false } ?: overall?.let { it to true } ?: house?.let { it to true }
    else -> null
}

/** A place to estimate the market at: a property, a locality or a searched point. */
data class AreaPoint(val names: List<String>, val lat: Double?, val lng: Double?)

fun Property.areaPoint() = AreaPoint(listOf(locality, taluk).filter { it.isNotBlank() }, lat, lng)

private fun distance(a: AreaPoint, lat: Double?, lng: Double?): Double? {
    val aLat = a.lat; val aLng = a.lng
    return if (aLat != null && aLng != null && lat != null && lng != null) haversineKm(aLat, aLng, lat, lng) else null
}

private fun Double.orNullIfBad(): Double? = if (isNaN() || isInfinite() || this <= 0) null else this

/**
 * Market estimate for a type group at a place. Combines
 *  (a) the user's own listings of the same type group within 3 km (expanding to 5 / 10 km until there are 4),
 *  (b) the bundled dataset: localities matching by name plus the nearest ones by distance (up to 5),
 *  and falls back to the dataset's city-wide average so there is always an answer when the type is covered.
 */
fun estimateMarket(
    point: AreaPoint,
    group: Int,
    data: AppData,
    dataset: MarketDataset = MarketData.dataset,
    excludePropertyId: String? = null,
): MarketEstimate {
    val basis = groupBasis(group)
    val unit = basis.rateUnit()
    val comps = mutableListOf<Comparable>()

    // (a) own listings
    val own = data.properties.filter { o ->
        o.id != excludePropertyId && o.type.group() == group && (o.unitPrice() ?: 0.0) > 0
    }
    var radiusUsed: Double? = null
    val hasCoords = point.lat != null && point.lng != null
    val ownPicked = mutableListOf<Pair<Property, Double?>>()
    if (hasCoords) {
        val withDist = own.mapNotNull { o -> distance(point, o.lat, o.lng)?.let { o to it } }
        for (r in PriceRules.RADII_KM) {
            radiusUsed = r
            val inR = withDist.filter { it.second <= r }
            ownPicked.clear(); ownPicked.addAll(inR.map { it.first to it.second })
            if (inR.size >= PriceRules.MIN_OWN) break
        }
    }
    own.filter { o -> o.lat == null && point.names.any { n -> nameMatches(o.locality, n) } && ownPicked.none { it.first.id == o.id } }
        .forEach { ownPicked += it to null }
    ownPicked.forEach { (o, d) ->
        comps += Comparable(
            label = o.title, labelTa = null, distanceKm = d, rate = o.unitPrice() ?: 0.0, min = null, max = null,
            source = CompSource.YOUR_LISTING, propertyId = o.id, lat = o.lat, lng = o.lng,
            matchedByName = d == null,
        )
    }

    // (b) dataset
    data class Cand(val loc: MarketLocality, val rate: TypeRate, val fallback: Boolean, val range: RateRange, val dist: Double?, val byName: Boolean)
    val cands = dataset.localities.mapNotNull { loc ->
        val (tr, fb) = loc.rates?.forGroup(group) ?: return@mapNotNull null
        val range = tr.rangeIn(unit) ?: return@mapNotNull null
        val byName = point.names.any { n -> nameMatches(loc.name, n) || (loc.nameTa != null && nameMatches(loc.nameTa, n)) }
        Cand(loc, tr, fb, range, distance(point, loc.lat, loc.lng), byName)
    }
    val primary = cands.filter { !it.fallback }
    fun pick(pool: List<Cand>): List<Cand> {
        val named = pool.filter { it.byName }.sortedBy { it.dist ?: 0.0 }
        val near = pool.filter { !it.byName && it.dist != null }.sortedBy { it.dist }
        val within = near.filter { (it.dist ?: 0.0) <= PriceRules.MARKET_NEAR_KM }
        val nearest = if (within.size >= PriceRules.MARKET_MIN || named.isNotEmpty()) within else near.take(PriceRules.MARKET_MIN)
        return (named + nearest).distinctBy { it.loc.name }.take(PriceRules.MARKET_MAX)
    }
    var chosen = pick(primary)
    if (chosen.isEmpty()) chosen = pick(cands.filter { it.fallback })
    chosen.forEach { c ->
        val mid = c.range.mid ?: return@forEach
        comps += Comparable(
            label = c.loc.name, labelTa = c.loc.nameTa, distanceKm = c.dist, rate = mid,
            min = c.range.min, max = c.range.max,
            source = if (c.fallback) CompSource.MARKET_FALLBACK else CompSource.MARKET,
            sourceName = c.rate.sourceName, sourceUrl = c.rate.sourceUrl, asOf = c.rate.asOf,
            lowConfidence = c.fallback || c.rate.confidence.equals("low", ignoreCase = true),
            lat = c.loc.lat, lng = c.loc.lng, matchedByName = c.byName,
        )
    }
    if (comps.none { it.source != CompSource.YOUR_LISTING } && primary.isNotEmpty()) {
        // Nothing near or matching: use the city-wide average for this type.
        val mids = primary.mapNotNull { it.range.mid }
        median(mids)?.let { m ->
            comps += Comparable(
                label = dataset.city.ifBlank { "City" }, labelTa = null, distanceKm = null, rate = m,
                min = median(primary.mapNotNull { it.range.min }), max = median(primary.mapNotNull { it.range.max }),
                source = CompSource.CITY_AVERAGE, sourceName = primary.map { it.rate.sourceName }.distinct().take(3).joinToString(", "),
                asOf = dataset.generated, lowConfidence = true,
            )
        }
    }

    val valid = comps.filter { it.rate > 0 }
    // Fallback / city-average figures only count when nothing better exists.
    val strong = valid.filter { it.source == CompSource.YOUR_LISTING || it.source == CompSource.MARKET }
    val basisSet = strong.ifEmpty { valid }
    val med = median(basisSet.map { it.rate })
    var low = median(basisSet.map { it.min ?: (it.rate * 0.9) })
    var high = median(basisSet.map { it.max ?: (it.rate * 1.1) })
    if (med != null) {
        low = minOf(low ?: med, med)
        high = maxOf(high ?: med, med)
    }

    val near5 = strong.count { (it.distanceKm ?: 99.0) <= 5.0 || it.matchedByName }
    val near10 = strong.count { (it.distanceKm ?: 99.0) <= 10.0 || it.matchedByName }
    val confidence = when {
        near5 >= 4 -> Confidence.HIGH
        near10 >= 2 || (near5 >= 1 && strong.any { !it.lowConfidence && it.source == CompSource.MARKET }) -> Confidence.MEDIUM
        else -> Confidence.LOW
    }

    return MarketEstimate(
        basis = basis, group = group, comparables = valid.sortedWith(compareBy({ it.distanceKm ?: if (it.matchedByName) 0.0 else 999.0 })),
        radiusKm = radiusUsed, median = med, fairLow = low, fairHigh = high, confidence = confidence,
        guideline = guidelineAt(point, group, data, dataset),
    )
}

/** User-entered guideline value first, then the dataset's (land types only). */
fun guidelineAt(point: AreaPoint, group: Int, data: AppData, dataset: MarketDataset = MarketData.dataset): GuidelineInfo? {
    val basis = groupBasis(group)
    val user = point.names.firstNotNullOfOrNull { n -> data.guidelines.firstOrNull { locMatches(it.location, n) } }
    if (user != null) {
        val v = guidelineFor(basis, user)?.orNullIfBad()
        if (v != null) return GuidelineInfo(v, null, null, user.location, fromUser = true)
    }
    // The dataset's guideline values are urban street values for house sites: they are not comparable with
    // built-up prices (house / apartment) nor with agricultural land, so they are used for plots / land only.
    if (group != 0) return null
    val unit = basis.rateUnit()
    val withG = dataset.localities.mapNotNull { loc ->
        val r = loc.rates?.guideline?.rangeIn(unit) ?: return@mapNotNull null
        Triple(loc, r, distance(point, loc.lat, loc.lng))
    }
    val named = withG.firstOrNull { (loc, _, _) -> point.names.any { n -> nameMatches(loc.name, n) || (loc.nameTa != null && nameMatches(loc.nameTa, n)) } }
    val near = withG.filter { (it.third ?: 99.0) <= PriceRules.GUIDELINE_NEAR_KM }.minByOrNull { it.third ?: 99.0 }
    val hit = named ?: near ?: return null
    val (loc, range, _) = hit
    val g = loc.rates?.guideline
    // The village minimum is used as the floor: a specific street can be higher.
    val value = (range.min ?: range.mid)?.orNullIfBad() ?: return null
    return GuidelineInfo(value, range.min, range.max, loc.name, fromUser = false, g?.sourceName ?: "", g?.sourceUrl ?: "", g?.asOf ?: "")
}

fun checkPrice(p: Property, data: AppData, dataset: MarketDataset = MarketData.dataset): PriceCheck {
    val basis = p.type.basis()
    val unit = p.unitPrice()
    val est = estimateMarket(p.areaPoint(), p.type.group(), data, dataset, excludePropertyId = p.id)
    val gv = est.guideline?.value
    val med = est.median
    val ratio = if (unit != null && med != null && med > 0) unit / med else null
    val belowGuideline = unit != null && gv != null && unit < gv
    val high = est.fairHigh

    val verdict = when {
        unit == null -> Verdict.UNKNOWN
        belowGuideline -> Verdict.SUSPICIOUS_LOW
        ratio == null -> Verdict.UNKNOWN
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
