package com.hark.sothudiary.domain

import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.ScoreWeights
import com.hark.sothudiary.domain.land.LandAssessment
import com.hark.sothudiary.domain.land.LandRating
import com.hark.sothudiary.domain.land.LandTile
import com.hark.sothudiary.domain.pricing.PriceCheck
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.registry.DutyBreakdown
import kotlin.math.roundToInt

/**
 * 2.4.0: a simple 0–100 property score built only from the app's own checks, with adjustable weights (Settings):
 *  - price vs area      (price check: % above / below the nearby market)
 *  - water / flood risk (land check: water buffer, ISRO land use, low-lying)
 *  - road access        (land check: major road distance, access road)
 *  - amenities nearby   (land check: schools, hospitals, bus, shops within 3 km)
 *  - guideline-value gap (asking price vs the official guideline value × area)
 * A factor that has not been checked is left out (its weight is not counted) – it is never scored as zero.
 */
enum class ScoreFactor { PRICE, WATER, ROAD, AMENITIES, GUIDELINE }

data class FactorScore(val factor: ScoreFactor, val score: Int?, val weight: Int)

data class PropertyScore(
    /** 0–100, or null when no factor is known yet. */
    val total: Int?,
    val factors: List<FactorScore>,
) {
    val known: Int get() = factors.count { it.score != null && it.weight > 0 }
}

fun ScoreWeights.of(f: ScoreFactor): Int = when (f) {
    ScoreFactor.PRICE -> price
    ScoreFactor.WATER -> water
    ScoreFactor.ROAD -> road
    ScoreFactor.AMENITIES -> amenities
    ScoreFactor.GUIDELINE -> guideline
}

fun ScoreWeights.with(f: ScoreFactor, v: Int): ScoreWeights = when (f) {
    ScoreFactor.PRICE -> copy(price = v)
    ScoreFactor.WATER -> copy(water = v)
    ScoreFactor.ROAD -> copy(road = v)
    ScoreFactor.AMENITIES -> copy(amenities = v)
    ScoreFactor.GUIDELINE -> copy(guideline = v)
}

private fun ratingScore(r: LandRating, good: Int, fair: Int, poor: Int): Int? = when (r) {
    LandRating.GOOD -> good
    LandRating.FAIR -> fair
    LandRating.POOR -> poor
    LandRating.UNKNOWN -> null
}

/** Price vs the nearby market: at market = 70, 20 % below = 100, 20 % above = 40, 50 % above = 0; "too cheap" = 20. */
fun priceFactor(check: PriceCheck?): Int? {
    if (check == null) return null
    if (check.verdict == Verdict.SUSPICIOUS_LOW) return 20
    val d = check.diffVsMarketPct ?: return null
    return (70 - d * 1.5).coerceIn(0.0, 100.0).roundToInt()
}

/**
 * Asking price vs guideline value × area: within 0–30 % above the guideline = 90; much higher falls to 20 at 4×;
 * below the guideline (duty is paid on the guideline anyway, and it is a red flag) = 40.
 */
fun guidelineFactor(d: DutyBreakdown?): Int? {
    val gap = d?.gapPct ?: return null
    val ratio = 1 + gap / 100.0
    return when {
        ratio < 0.9 -> 40
        ratio <= 1.3 -> 90
        ratio <= 2.0 -> (90 - (ratio - 1.3) / 0.7 * 40).roundToInt()
        else -> (50 - (ratio - 2.0) / 2.0 * 30).coerceAtLeast(20.0).roundToInt()
    }
}

fun propertyScore(check: PriceCheck?, land: LandAssessment?, duty: DutyBreakdown?, w: ScoreWeights): PropertyScore {
    val water = land?.tile(LandTile.RISK)?.rating?.let { ratingScore(it, 95, 55, 10) }
    val road = land?.tile(LandTile.CONNECTIVITY)?.rating?.let { ratingScore(it, 90, 60, 25) }
    val amen = land?.tile(LandTile.LOCALITY)?.rating?.let { ratingScore(it, 90, 60, 30) }
    val factors = listOf(
        FactorScore(ScoreFactor.PRICE, priceFactor(check), w.price),
        FactorScore(ScoreFactor.WATER, water, w.water),
        FactorScore(ScoreFactor.ROAD, road, w.road),
        FactorScore(ScoreFactor.AMENITIES, amen, w.amenities),
        FactorScore(ScoreFactor.GUIDELINE, guidelineFactor(duty), w.guideline),
    )
    val used = factors.filter { it.score != null && it.weight > 0 }
    val wsum = used.sumOf { it.weight }
    val total = if (wsum == 0) null else (used.sumOf { it.score!! * it.weight }.toDouble() / wsum).roundToInt().coerceIn(0, 100)
    return PropertyScore(total, factors)
}

/** Band of a score for colours / map legend: 85+, 70–84, 55–69, below 55. */
fun scoreBand(s: Int?): Int = when {
    s == null -> -1
    s >= 85 -> 3
    s >= 70 -> 2
    s >= 55 -> 1
    else -> 0
}

@Suppress("unused")
private fun keepProperty(p: Property) = p
