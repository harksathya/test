package com.hark.sothudiary.ui.components

import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import com.hark.sothudiary.domain.pricing.PriceRules
import com.hark.sothudiary.i18n.groupTr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.domain.PriceBasis
import com.hark.sothudiary.domain.convertRate
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.CompSource
import com.hark.sothudiary.domain.pricing.Comparable
import com.hark.sothudiary.domain.pricing.Confidence
import com.hark.sothudiary.domain.pricing.MarketEstimate
import com.hark.sothudiary.domain.pricing.PriceCheck
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.unitPriceShort
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.slashTr
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.map.GeoMap
import com.hark.sothudiary.ui.map.isValidCoord
import com.hark.sothudiary.ui.map.MapPin
import com.hark.sothudiary.ui.map.platformHasMap
import com.hark.sothudiary.ui.theme.verdictArgb
import com.hark.sothudiary.ui.theme.verdictColor
import kotlin.math.abs

fun pctText(p: Double): String = (if (p >= 0) "+" else "−") + dec(abs(p), 0) + "%"

/** "₹21.1 L/cent" */
fun rateText(v: Double, basis: PriceBasis, lang: Lang): String = unitPriceShort(v, lang) + basis.slashTr().of(lang)

fun rangeText(lo: Double?, hi: Double?, basis: PriceBasis, lang: Lang): String = when {
    lo != null && hi != null && abs(hi - lo) > 0.5 -> unitPriceShort(lo, lang) + " – " + unitPriceShort(hi, lang) + basis.slashTr().of(lang)
    lo != null -> rateText(lo, basis, lang)
    hi != null -> rateText(hi, basis, lang)
    else -> "-"
}

/** The other units that make sense for a basis (plots: also sq ft; farmland: also per cent). */
fun altBases(basis: PriceBasis): List<PriceBasis> = when (basis) {
    PriceBasis.PER_CENT -> listOf(PriceBasis.PER_SQFT)
    PriceBasis.PER_ACRE -> listOf(PriceBasis.PER_CENT)
    PriceBasis.PER_SQFT -> emptyList()
}

fun Comparable.displayName(lang: Lang): String = if (lang == Lang.TA && !labelTa.isNullOrBlank()) labelTa else label

/** Plain-language reasoning lines for a market estimate (+ the quoted price if there is one). */
@Composable
fun reasoningLines(est: MarketEstimate, check: PriceCheck?): List<String> {
    val lang = LocalLang.current
    val b = est.basis
    val lines = mutableListOf<String>()
    val unit = check?.unitPrice
    val med = est.median
    val n = est.comparables.size
    if (check != null && unit != null && med != null) {
        val diff = check.diffVsMarketPct ?: 0.0
        lines += if (abs(diff) < 1) S.rsAtMedian.f(rateText(unit, b, lang), n, rateText(med, b, lang))
        else if (diff < 0) S.rsBelowMedian.f(rateText(unit, b, lang), dec(-diff, 0), rateText(med, b, lang), n)
        else S.rsAboveMedian.f(rateText(unit, b, lang), dec(diff, 0), rateText(med, b, lang), n)
        val lo = est.fairLow
        val hi = est.fairHigh
        if (lo != null && hi != null) {
            lines += when {
                unit < lo -> S.rsBelowRange.f(rangeText(lo, hi, b, lang))
                unit > hi -> S.rsAboveRange.f(rangeText(lo, hi, b, lang))
                else -> S.rsInRange.f(rangeText(lo, hi, b, lang))
            }
        }
    } else if (med != null) {
        lines += S.rsTypical.f(rateText(med, b, lang), rangeText(est.fairLow, est.fairHigh, b, lang), n)
    }
    if (est.insufficient) {
        lines += if (est.hasPin) S.rsInsufficient.f(groupTr(est.group).s(), dec(PriceRules.MAX_KM, 0)) else S.rsNoPin.s()
    }
    if (est.widened) lines += S.rsWidened.f(dec(est.baseRadiusKm, 0), dec(est.radiusKm ?: PriceRules.MAX_KM, 0))
    val otherD = est.comparables.filter { it.otherDistrict }.map { it.district }.filter { it.isNotBlank() }.distinct()
    if (otherD.isNotEmpty()) lines += S.rsOtherDistrict.f(otherD.joinToString(", "))
    val nearestMarket = est.comparables.firstOrNull { it.source == CompSource.MARKET || it.source == CompSource.ONLINE }
    if (nearestMarket != null) {
        val where = nearestMarket.distanceKm?.let { S.kmAway.f(dec(it, 1)) } ?: S.nameMatch.s()
        lines += S.rsNearestMarket.f(
            nearestMarket.displayName(lang), where, rangeText(nearestMarket.min ?: nearestMarket.rate, nearestMarket.max ?: nearestMarket.rate, b, lang),
            listOf(nearestMarket.sourceName, nearestMarket.asOf).filter { it.isNotBlank() }.joinToString(", "),
        )
    }
    val online = est.comparables.count { it.source == CompSource.ONLINE }
    if (online > 0) lines += S.rsOnline.f(online)
    lines += S.rsSameType.f(groupTr(est.group).s())
    val own = est.comparables.count { it.source == CompSource.YOUR_LISTING }
    if (own > 0) lines += S.rsOwn.f(own, est.radiusKm?.let { dec(it, 0) } ?: "-")
    val g = est.guideline
    if (g != null) {
        val gText = rateText(g.value, b, lang)
        lines += if (g.fromUser && g.sourceName == com.hark.sothudiary.domain.registry.DutyRules.SOURCE_NAME) S.rsGuidelineOfficial.f(gText, g.label)
        else if (g.fromUser) S.rsGuidelineUser.f(gText, g.label) else S.rsGuidelineData.f(gText, g.label, g.asOf)
        if (check != null && unit != null) {
            if (check.belowGuideline) lines += S.explainBelowGuideline.s()
            else lines += S.rsTimesGuideline.f(dec(unit / g.value, 1))
        }
    } else if (est.group == 0) {
        lines += S.rsNoGuideline.s()
    }
    lines += S.rsConfidence.f(est.confidence.tr().s())
    if (check != null && unit != null && med != null) {
        lines += when (check.verdict) {
            Verdict.SUSPICIOUS_LOW -> if (check.belowGuideline) S.adviceBelowGuideline.s() else S.explainSuspicious.s()
            Verdict.GOOD -> S.explainGood.s()
            Verdict.FAIR -> S.explainFair.s()
            Verdict.HIGH -> S.explainHigh.s()
            Verdict.TOO_HIGH -> S.explainTooHigh.s()
            Verdict.UNKNOWN -> ""
        }
    }
    return lines.filter { it.isNotBlank() }
}

/**
 * Horizontal range chart: fair range band, median tick, guideline (dashed) and the quoted price marker.
 */
@Composable
fun PriceRangeChart(est: MarketEstimate, quoted: Double?, quotedColor: Color, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    val lo = est.fairLow
    val hi = est.fairHigh
    val med = est.median ?: return
    val g = est.guideline?.value
    val values = listOfNotNull(lo, hi, med, g, quoted) + est.comparables.map { it.rate }
    val minV = values.minOrNull() ?: return
    val maxV = values.maxOrNull() ?: return
    val span = (maxV - minV).takeIf { it > 0 } ?: (med * 0.5)
    val start = (minV - span * 0.12).coerceAtLeast(0.0)
    val end = maxV + span * 0.12
    val measurer = rememberTextMeasurer()
    val bandColor = Color(0xFF2E7D32)
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val compColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val medianLabel = S.chartMedian.s()
    val guideLabel = S.chartGuideline.s()
    val quotedLabel = S.chartQuoted.s()
    val small = TextStyle(fontSize = 10.sp, color = labelColor)
    Canvas(modifier.fillMaxWidth().height(92.dp)) {
        fun x(v: Double) = ((v - start) / (end - start) * size.width).toFloat()
        val axisY = size.height * 0.52f
        // axis
        drawLine(axisColor, Offset(0f, axisY), Offset(size.width, axisY), strokeWidth = 2f)
        // fair band
        if (lo != null && hi != null) {
            drawRoundRect(
                bandColor.copy(alpha = 0.22f), topLeft = Offset(x(lo), axisY - 12.dp.toPx()),
                size = Size((x(hi) - x(lo)).coerceAtLeast(4f), 24.dp.toPx()), cornerRadius = CornerRadius(8f, 8f),
            )
        }
        // comparables as small dots
        est.comparables.forEach { c -> drawCircle(compColor.copy(alpha = 0.55f), radius = 3.5.dp.toPx(), center = Offset(x(c.rate), axisY)) }
        // median
        drawLine(bandColor, Offset(x(med), axisY - 14.dp.toPx()), Offset(x(med), axisY + 14.dp.toPx()), strokeWidth = 3.dp.toPx())
        val mt = measurer.measure(medianLabel + " " + unitPriceShort(med, lang), small)
        drawText(mt, topLeft = Offset((x(med) - mt.size.width / 2f).coerceIn(0f, size.width - mt.size.width), axisY + 16.dp.toPx()))
        // guideline
        if (g != null) {
            drawLine(
                guideColor, Offset(x(g), axisY - 18.dp.toPx()), Offset(x(g), axisY + 18.dp.toPx()), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
            val gt = measurer.measure(guideLabel, small)
            drawText(gt, topLeft = Offset((x(g) - gt.size.width / 2f).coerceIn(0f, size.width - gt.size.width), axisY + 30.dp.toPx()))
        }
        // quoted price marker (triangle pointing down onto the axis)
        if (quoted != null) {
            val qx = x(quoted)
            val tip = axisY - 14.dp.toPx()
            val path = Path().apply {
                moveTo(qx, tip)
                lineTo(qx - 8.dp.toPx(), tip - 12.dp.toPx())
                lineTo(qx + 8.dp.toPx(), tip - 12.dp.toPx())
                close()
            }
            drawPath(path, quotedColor)
            drawLine(quotedColor, Offset(qx, tip), Offset(qx, axisY + 12.dp.toPx()), strokeWidth = 2.dp.toPx())
            val qt = measurer.measure(quotedLabel + " " + unitPriceShort(quoted, lang), TextStyle(fontSize = 11.sp, color = quotedColor, fontWeight = FontWeight.Bold))
            drawText(qt, topLeft = Offset((qx - qt.size.width / 2f).coerceIn(0f, size.width - qt.size.width), 0f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComparableRow(c: Comparable, basis: PriceBasis, onOpenProperty: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    val lang = LocalLang.current
    val color = when (c.source) {
        CompSource.YOUR_LISTING -> MaterialTheme.colorScheme.primary
        CompSource.MARKET, CompSource.ONLINE -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val click: (() -> Unit)? = when {
        c.propertyId != null -> ({ onOpenProperty(c.propertyId) })
        c.sourceUrl.isNotBlank() -> ({ onOpenUrl(c.sourceUrl) })
        else -> null
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).let { if (click != null) it.clickable(onClick = click) else it }
            .padding(vertical = 7.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(c.displayName(lang), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(rateText(c.rate, basis, lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(Modifier.padding(start = 17.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.source.tr().s(), style = MaterialTheme.typography.labelSmall, color = color)
            val dist = c.distanceKm
            Text("• " + (if (dist != null) S.kmAway.f(dec(dist, 1)) else if (c.matchedByName) S.nameMatch.s() else "—"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.min != null || c.max != null) {
                Text("• " + rangeText(c.min, c.max, basis, lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.lowConfidence) Pill(S.lowConfidenceTag.s(), Color(0xFFEF8F00))
            if (c.otherDistrict && c.district.isNotBlank()) Pill(S.otherDistrictTag.f(c.district), Color(0xFF8E24AA))
        }
        val src = listOf(c.sourceName, c.asOf.let { if (it.isNotBlank()) S.asOf.f(it) else "" }).filter { it.isNotBlank() }.joinToString(" • ")
        if (src.isNotBlank()) {
            Text(src, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 17.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Map of the property (or searched point) and its comparables. */
@Composable
fun ComparablesMap(centerLat: Double?, centerLng: Double?, centerTitle: String, centerColor: Long, est: MarketEstimate, modifier: Modifier = Modifier, onPinClick: ((String) -> Unit)? = null) {
    if (!platformHasMap) return
    // The map is always centred on the property / searched point: without a valid pin there is no map.
    if (!isValidCoord(centerLat, centerLng)) return
    val cLat = centerLat!!
    val cLng = centerLng!!
    val pins = buildList {
        est.comparables.forEachIndexed { i, c ->
            val la = c.lat; val ln = c.lng
            if (isValidCoord(la, ln)) {
                add(MapPin(c.propertyId ?: "m$i", la!!, ln!!, c.label, c.source.name, if (c.source == CompSource.YOUR_LISTING) 0xFF1E88E5 else 0xFF00897B, small = true))
            }
        }
        add(MapPin("center", cLat, cLng, centerTitle, color = centerColor))
    }
    Box(modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.medium)) {
        GeoMap(
            modifier = Modifier.fillMaxSize(), pins = pins,
            centerLat = cLat, centerLng = cLng, zoom = 14.0,
            cameraKey = pins.map { it.id } + "$cLat,$cLng", fitPins = true, onPinClick = onPinClick, fitAroundCenter = true,
        )
    }
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(Color(centerColor), S.legendThis.s())
        LegendDot(Color(0xFF1E88E5), S.legendYours.s())
        LegendDot(Color(0xFF00897B), S.legendMarket.s())
    }
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(c, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Detailed price check card for a property. */
@Composable
fun PriceCheckCard(
    p: Property,
    check: PriceCheck,
    onOpenProperty: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAreaRates: () -> Unit,
    onFindOnline: (() -> Unit)? = null,
    findingOnline: Boolean = false,
    onlineMessage: String? = null,
    lookupStatus: String? = null,
    needsKey: Boolean = false,
    onSetupKey: (() -> Unit)? = null,
    /** The latest live lookup when it failed: shown once, as a short friendly message with a Details expander. */
    lookupError: com.hark.sothudiary.data.RateLookup? = null,
    /** 2.3.0: the latest lookup (to show "found with a free web search" and its raw results when the AI failed). */
    latestLookup: com.hark.sothudiary.data.RateLookup? = null,
) {
    val lang = LocalLang.current
    val color = verdictColor(check.verdict)
    val est = check.estimate
    val b = check.basis
    var showAll by remember { mutableStateOf(false) }
    SectionCard(title = S.priceCheck.s(), accent = color, icon = Icons.Filled.Insights) {
        Box(Modifier.fillMaxWidth().background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(12.dp)) {
            Column {
                Text(check.verdict.tr().s(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                check.diffVsMarketPct?.let {
                    Text(S.vsMarketLine.f(pctText(it)), color = color, style = MaterialTheme.typography.bodySmall)
                }
                Text(est.confidence.tr().s() + " • " + S.nComparables.f(est.comparables.size), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (est.insufficient || est.widened) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFFEF8F00).copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (est.insufficient) S.notEnoughNearby.s() else S.widenedTitle.f(dec(est.radiusKm ?: PriceRules.MAX_KM, 0)),
                    fontWeight = FontWeight.Bold, color = Color(0xFFB26A00),
                )
                Text(
                    if (est.insufficient) (if (est.hasPin) S.notEnoughNearbyBody.f(groupTr(est.group).s(), dec(PriceRules.MAX_KM, 0)) else S.rsNoPin.s())
                    else S.widenedBody.s(),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (needsKey && onSetupKey != null) {
                    Text(S.needKeyForCheck.s(), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onSetupKey, modifier = Modifier.fillMaxWidth()) { Text(S.setUpLiveCheck.s()) }
                } else if (onFindOnline != null) FindOnlineButton(onFindOnline, findingOnline, prominent = true)
                if (lookupError?.error != null && !needsKey) LookupErrorNote(lookupError.error, lookupError.retryAt)
                else if (onlineMessage != null) Text(onlineMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
        }
        val unit = check.unitPrice
        if (unit == null) {
            Text(S.explainNoPrice.s(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        } else {
            KeyValueRow(S.quotedRate.s(), (listOf(b) + altBases(b)).joinToString("  •  ") { rateText(convertRate(unit, b, it), it, lang) }, color)
        }
        est.median?.let { med ->
            KeyValueRow(S.fairRange.s(), rangeText(est.fairLow, est.fairHigh, b, lang))
            KeyValueRow(S.medianRate.s(), (listOf(b) + altBases(b)).joinToString("  •  ") { rateText(convertRate(med, b, it), it, lang) })
        }
        KeyValueRow(
            S.guidelineValueShort.s(),
            est.guideline?.let { g -> rateText(g.value, b, lang) + " (" + g.label + ")" } ?: S.notSet.s(),
        )
        check.diffVsGuidelinePct?.let { KeyValueRow(S.vsGuideline.s(), pctText(it), if (it < 0) verdictColor(Verdict.SUSPICIOUS_LOW) else Color.Unspecified) }
        if (est.median != null) {
            Spacer(Modifier.height(8.dp))
            PriceRangeChart(est, unit, color)
        }
        Spacer(Modifier.height(8.dp))
        Text(S.whyTitle.s(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        reasoningLines(est, check).forEach { line ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("•", color = color, modifier = Modifier.padding(end = 6.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (est.comparables.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))
            Text(S.comparablesTitle.f(est.comparables.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            val shown = if (showAll) est.comparables else est.comparables.take(5)
            shown.forEach { c -> ComparableRow(c, b, onOpenProperty, onOpenUrl) }
            if (est.comparables.size > 5) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) S.showLess.s() else S.showAllN.f(est.comparables.size)) }
            }
            Spacer(Modifier.height(6.dp))
            ComparablesMap(p.lat, p.lng, p.title, verdictArgb(check.verdict), est, onPinClick = { id -> if (id != "center" && !id.startsWith("m")) onOpenProperty(id) })
        }
        if (check.quotes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(S.quotedHistory.s(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val first = check.quotes.first()
            if (p.askingPrice > 0 && first.price != p.askingPrice) {
                val up = p.askingPrice > first.price
                Text(
                    S.quotedThenNow.f(priceShort(first.price, lang), formatDateShort(first.at, lang), priceShort(p.askingPrice, lang),
                        pctText((p.askingPrice.toDouble() / first.price - 1) * 100)),
                    color = if (up) verdictColor(Verdict.TOO_HIGH) else verdictColor(Verdict.FAIR),
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                )
            }
            check.quotes.forEach { q ->
                Text("• " + formatDateShort(q.at, lang) + ": " + priceShort(q.price, lang), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (lookupStatus != null || (onFindOnline != null && !est.insufficient && !est.widened)) {
            Spacer(Modifier.height(8.dp))
            if (lookupStatus != null) Text(lookupStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onFindOnline != null && !est.insufficient && !est.widened && !needsKey) {
                FindOnlineButton(onFindOnline, findingOnline, prominent = false)
                if (lookupError?.error != null) LookupErrorNote(lookupError.error, lookupError.retryAt)
                else if (onlineMessage != null) Text(onlineMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        // 2.3.0: rates found through our own free web search, or its raw results when Gemini could not read them.
        if (latestLookup != null && latestLookup.viaSearch && latestLookup.found > 0)
            Text(S.viaSearchNote.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        if (latestLookup != null && latestLookup.snippets.isNotEmpty())
            com.hark.sothudiary.ui.screens.WebSnippetList(latestLookup.snippets, onOpenUrl)
        Spacer(Modifier.height(8.dp))
        Text(S.askingPricesNote.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(onClick = onAreaRates) {
                Icon(Icons.Filled.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.seeAreaRates.s())
            }
            TextButton(onClick = { onOpenUrl(TN_REGINET) }) { Text(S.openTnReginet.s()) }
        }
    }
}

const val TN_REGINET = "https://tnreginet.gov.in/portal/"

@Composable
fun FindOnlineButton(onClick: () -> Unit, busy: Boolean, prominent: Boolean) {
    val content: @Composable () -> Unit = {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (busy) S.findingOnline.s() else S.refreshLiveRates.s())
    }
    if (prominent) Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { content() }
    else OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { content() }
}

@Suppress("unused")
private val keepConfidence = Confidence.LOW
