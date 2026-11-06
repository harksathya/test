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
import com.hark.sothudiary.domain.pricePerAcre
import com.hark.sothudiary.domain.basis
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
    if (p.type.basis() == PriceBasis.PER_ACRE) p.pricePerAcre()?.let { parts.add(0, unitPriceShort(it, lang) + PriceBasis.PER_ACRE.slashTr().of(lang)) }
    return parts.joinToString("  •  ")
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
