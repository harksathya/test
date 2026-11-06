package com.hark.sothudiary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.Broker
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.domain.PriceBasis
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.domain.indianGroup
import com.hark.sothudiary.domain.pricePerCent
import com.hark.sothudiary.domain.pricePerSqft
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.Advice
import com.hark.sothudiary.domain.pricing.PriceCheck
import com.hark.sothudiary.domain.pricing.Reason
import com.hark.sothudiary.domain.pricing.ReasonCode
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.WorthIt
import com.hark.sothudiary.domain.sqft
import com.hark.sothudiary.domain.cents
import com.hark.sothudiary.domain.unitPriceShort
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.perTr
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.shortTr
import com.hark.sothudiary.i18n.slashTr
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.theme.adviceColor
import com.hark.sothudiary.ui.theme.statusColor
import com.hark.sothudiary.ui.theme.verdictColor
import kotlin.math.abs
import kotlin.math.roundToLong

fun platformColor(p: SourcePlatform): Color = when (p) {
    SourcePlatform.YOUTUBE -> Color(0xFFD32F2F)
    SourcePlatform.INSTAGRAM -> Color(0xFFC13584)
    SourcePlatform.FACEBOOK -> Color(0xFF1877F2)
    SourcePlatform.WHATSAPP -> Color(0xFF25A244)
    SourcePlatform.OTHER -> Color(0xFF546E7A)
    SourcePlatform.NONE -> Color(0xFF9E9E9E)
}

fun Property.locationLine(): String = listOf(locality, taluk, district).filter { it.isNotBlank() }.distinct().joinToString(", ")

@Composable
fun sizeLine(p: Property): String {
    val lang = LocalLang.current
    val main = dec(p.size, 2) + " " + p.unit.tr().of(lang)
    val alt = when (p.unit) {
        com.hark.sothudiary.data.AreaUnit.SQFT -> dec(p.cents, 2) + " " + S.centsShort.of(lang)
        else -> indianGroup(p.sqft.roundToLong()) + " " + S.sqftShort.of(lang)
    }
    return "$main ($alt)"
}

@Composable
fun unitPriceLine(p: Property): String {
    val lang = LocalLang.current
    val perCent = p.pricePerCent()
    val perSqft = p.pricePerSqft()
    val parts = mutableListOf<String>()
    if (perCent != null) parts += unitPriceShort(perCent, lang) + PriceBasis.PER_CENT.slashTr().of(lang)
    if (perSqft != null) parts += unitPriceShort(perSqft, lang) + PriceBasis.PER_SQFT.slashTr().of(lang)
    return parts.joinToString("  •  ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertyCard(
    p: Property,
    broker: Broker?,
    check: PriceCheck?,
    nextFollowUp: Long?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lang = LocalLang.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(p.type.tr().s(), MaterialTheme.colorScheme.primary)
                if (p.sample) {
                    Spacer(Modifier.width(6.dp))
                    Pill(S.sample.s(), Color(0xFF8D6E63))
                }
                Spacer(Modifier.weight(1f))
                if (p.sourcePlatform != SourcePlatform.NONE) {
                    Pill(p.sourcePlatform.tr().s(), platformColor(p.sourcePlatform), filled = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(3.dp))
                Text(p.locationLine().ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else S.priceOnRequest.s(),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(10.dp))
                Text(unitPriceLine(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SquareFoot, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(3.dp))
                Text(sizeLine(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(p.status.tr().s(), statusColor(p.status))
                if (check != null) Pill(check.verdict.shortTr().s(), verdictColor(check.verdict), filled = check.verdict != Verdict.UNKNOWN)
                if (nextFollowUp != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(" " + formatDateShort(nextFollowUp, lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (broker != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" " + broker.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun pctText(p: Double): String = (if (p >= 0) "+" else "−") + dec(abs(p), 0) + "%"

/** Price check card with verdict and the reasoning behind it. */
@Composable
fun PriceCheckCard(p: Property, check: PriceCheck, guidelineLink: () -> Unit) {
    val lang = LocalLang.current
    val color = verdictColor(check.verdict)
    val per = check.basis.perTr().s()
    SectionCard(title = S.priceCheck.s(), accent = color) {
        Box(Modifier.fillMaxWidth().background(color.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).padding(12.dp)) {
            Text(check.verdict.tr().s(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        KeyValueRow(S.thisProperty.f(per), check.unitPrice?.let { unitPriceShort(it, lang) } ?: "-")
        KeyValueRow(
            S.guidelineValue.f(per),
            check.guideline?.let { unitPriceShort(it, lang) + (check.guidelineLocation?.let { l -> " ($l)" } ?: "") } ?: S.notSet.s(),
        )
        check.diffVsGuidelinePct?.let { KeyValueRow(S.vsGuideline.s(), pctText(it), if (it < 0) verdictColor(Verdict.SUSPICIOUS_LOW) else Color.Unspecified) }
        KeyValueRow(
            S.marketMedian.f(per),
            check.marketMedian?.let { unitPriceShort(it, lang) } ?: S.notEnough.s(),
        )
        KeyValueRow(S.comparables.s(), S.nListings.f(check.comparableCount))
        check.diffVsMarketPct?.let { KeyValueRow(S.vsMarket.s(), pctText(it), color) }
        Spacer(Modifier.height(8.dp))
        val explain = when {
            check.unitPrice == null -> S.explainNoPrice.s()
            check.belowGuideline -> S.explainBelowGuideline.s()
            check.marketMedian == null -> S.explainNoMarket.f(check.comparableCount)
            else -> when (check.verdict) {
                Verdict.SUSPICIOUS_LOW -> S.explainSuspicious.s()
                Verdict.GOOD -> S.explainGood.s()
                Verdict.FAIR -> S.explainFair.s()
                Verdict.HIGH -> S.explainHigh.s()
                Verdict.TOO_HIGH -> S.explainTooHigh.s()
                Verdict.UNKNOWN -> S.explainNoMarket.f(check.comparableCount)
            }
        }
        Text(explain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Spacer(Modifier.height(6.dp))
        Text(
            S.guidelineHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).let { m -> m },
        )
        androidx.compose.material3.TextButton(onClick = guidelineLink) { Text(S.openTnReginet.s()) }
    }
}

@Composable
fun reasonText(r: Reason): String {
    val lang = LocalLang.current
    return when (r.code) {
        ReasonCode.STATUS_DROPPED -> S.rDropped.s()
        ReasonCode.STATUS_BOUGHT -> S.rBought.s()
        ReasonCode.PRICE_SUSPICIOUS -> S.rSuspicious.s()
        ReasonCode.PRICE_GOOD -> S.rGood.f(r.pct?.let { pctText(it) } ?: "")
        ReasonCode.PRICE_FAIR -> S.rFair.f(r.pct?.let { pctText(it) } ?: "")
        ReasonCode.PRICE_HIGH -> S.rHigh.f(r.pct?.let { pctText(it) } ?: "")
        ReasonCode.PRICE_TOO_HIGH -> S.rTooHigh.f(r.pct?.let { pctText(it) } ?: "")
        ReasonCode.PRICE_UNKNOWN -> S.rUnknown.s()
        ReasonCode.DOCS_MISSING -> S.rDocsMissing.f(r.docs.joinToString(", ") { it.tr().of(lang) })
        ReasonCode.DOCS_OK -> S.rDocsOk.s()
        ReasonCode.PRICE_RAISED -> S.rRaised.f(r.pct?.let { pctText(it) } ?: "")
        ReasonCode.PRICE_REDUCED -> S.rReduced.f(r.pct?.let { pctText(it) } ?: "")
        ReasonCode.BROKER_FLAGGED -> S.rBrokerFlagged.f(r.count ?: 0)
        ReasonCode.FOLLOWUPS_OVERDUE -> S.rOverdue.f(r.count ?: 0)
    }
}

@Composable
fun WorthItCard(w: WorthIt) {
    val color = adviceColor(w.advice)
    SectionCard(title = S.worthIt.s(), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(w.advice.tr().s().uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(S.suggestionOnly.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        w.reasons.forEach { r ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).background(adviceColor(r.severity), RoundedCornerShape(50)))
                Spacer(Modifier.width(8.dp))
                Text(reasonText(r), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
