package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.PlatformBackHandler
import com.hark.sothudiary.data.GvUnit
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.OfficialGuideline
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.RegistryInfo
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.domain.parseDouble
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.registry.DutyBreakdown
import com.hark.sothudiary.domain.registry.DutyRates
import com.hark.sothudiary.domain.registry.DutyRules
import com.hark.sothudiary.domain.registry.GV_CAPTURE_JS
import com.hark.sothudiary.domain.registry.GvPlace
import com.hark.sothudiary.domain.registry.ParsedGuideline
import com.hark.sothudiary.domain.registry.captureCandidates
import com.hark.sothudiary.domain.registry.convertGvRate
import com.hark.sothudiary.domain.registry.gvPlace
import com.hark.sothudiary.domain.registry.gvPrefillScript
import com.hark.sothudiary.domain.registry.hasBuilding
import com.hark.sothudiary.domain.registry.officialGuideline
import com.hark.sothudiary.domain.registry.parseGuidelineText
import com.hark.sothudiary.domain.registry.propertyDuty
import com.hark.sothudiary.domain.registry.toGvUnit
import com.hark.sothudiary.domain.registry.withPlace
import com.hark.sothudiary.domain.rupees
import com.hark.sothudiary.domain.basis
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.S4
import com.hark.sothudiary.i18n.Tr
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.FullScreenOverlay
import com.hark.sothudiary.ui.components.ChoiceChips
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.components.KeyValueRow
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.reader.PortalWebView
import com.hark.sothudiary.ui.reader.platformHasWebView
import com.hark.sothudiary.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/*
 * 2.3.1: "Guideline value & stamp duty" card on the property detail screen, right after the price check.
 * The guideline value comes only from tnreginet.gov.in (read by the user in the in-app page) or the user's own entry.
 * Why a WebView and not a direct fetch: the portal's guideline search is a JavaScript form whose district → SRO → village
 * lists load by AJAX (POST /portal/webHP with session cookies / CSRF), results are rendered in the page, a captcha guards
 * other searches on the same page, and there is no public API – an app-side scrape would break silently or need the
 * captcha solved. The user's own browser session on the real page is the reliable, honest route.
 */

fun GvUnit.label(): Tr = when (this) {
    GvUnit.SQFT -> S.gvUnitSqft
    GvUnit.SQM -> S.gvUnitSqm
    GvUnit.CENT -> S.gvUnitCent
    GvUnit.ACRE -> S.gvUnitAcre
    GvUnit.HECTARE -> S.gvUnitHectare
}

/** ₹12,34,567 (₹12.35 L) – full Indian grouping plus the lakh / crore short form for big amounts. */
fun moneyText(n: Long, lang: Lang): String = if (n >= 1_00_000) rupees(n) + "  (" + priceShort(n, lang) + ")" else rupees(n)

fun gvRateText(value: Double, unit: GvUnit, lang: Lang): String =
    rupees(kotlin.math.round(value).toLong()) + " / " + unit.label().of(lang)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidelineDutyCard(ctx: AppContext, p: Property) {
    val lang = LocalLang.current
    val settings = ctx.repo.data.settings
    val rates = DutyRates(settings.stampDutyPct, settings.registrationFeePct)
    val reg = p.registry
    val g = reg.guideline
    var portalOpen by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(false) }
    var editPlace by remember { mutableStateOf(false) }
    var editRates by remember { mutableStateOf(false) }
    val place = gvPlace(p)

    SectionCard(title = S.gvTitle.s(), icon = Icons.Filled.AccountBalance) {
        // ---- Official guideline value ----
        Text(S.gvOfficial.s(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (g == null) {
            Text(S.gvNone.s(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(gvRateText(g.value, g.unit, lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val basisUnit = p.type.basis().toGvUnit()
            if (basisUnit != g.unit) {
                Text(S.gvAlsoIs.f(gvRateText(convertGvRate(g.value, g.unit, basisUnit), basisUnit, lang)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (g.method == OfficialGuideline.METHOD_WEBVIEW) {
                    Pill("TNREGINET", MaterialTheme.colorScheme.primary, filled = true)
                    Spacer(Modifier.width(6.dp))
                    Text(S.gvFromPortal.f(formatDateShort(g.capturedAt, lang)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(S.gvFromUser.f(formatDateShort(g.capturedAt, lang)), style = MaterialTheme.typography.labelSmall, color = LocalBrand.current.warnText)
                }
            }
            if (g.effectiveFrom.isNotBlank()) Text(S.gvEffective.f(g.effectiveFrom), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val placeLine = listOf(place.district, reg.sro, place.village, reg.street, p.surveyNumber.takeIf { it.isNotBlank() }?.let { S.gvSurvey.s() + " " + it })
            .filter { !it.isNullOrBlank() }.joinToString(" • ")
        if (placeLine.isNotBlank()) Text(placeLine, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = {
                // Remember the place filled from the address, so the user sees and can correct it.
                if (reg.district.isBlank() || reg.village.isBlank()) ctx.repo.setRegistry(p.id, reg.withPlace(place))
                if (platformHasWebView) portalOpen = true else { ctx.platform.openUrl(DutyRules.TNREGINET_GUIDELINE_URL); editValue = true }
            }) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.gvOpenPortal.s())
            }
            OutlinedButton(onClick = { editValue = true }) { Text(S.gvEnterValue.s()) }
            TextButton(onClick = { editPlace = true }) {
                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(S.gvEditPlace.s())
            }
        }

        // ---- Calculator ----
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(S.dutyCalc.s(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        val d = propertyDuty(p, rates)
        if (d == null) {
            Text(S.dutyNoValues.s(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DutyTable(d, lang)
            if (d.guidelineValue == null) Text(S.dutyPriceOnly.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
            val gap = d.gapPct
            if (gap != null && gap < 0) Text(S.dutyBelowWarn.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText, modifier = Modifier.padding(top = 4.dp))
            if (d.womenConcession) Text(S.dutyWomenApplied.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        if (p.type.hasBuilding()) Text(S.dutyBuildingNote.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().clickable { ctx.repo.setRegistry(p.id, reg.copy(womenBuyers = !reg.womenBuyers)) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = reg.womenBuyers, onCheckedChange = { ctx.repo.setRegistry(p.id, reg.copy(womenBuyers = it)) })
            Text(S.dutyWomen.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        Text(S.dutyRatesLine.f(dec(rates.stampPct, 2), dec(rates.regPct, 2)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { editRates = true }) { Text(S.dutyEditRates.s()) }
    }

    if (portalOpen) {
        GuidelinePortalOverlay(
            propertyId = p.id,
            onChoices = { d, sro, v ->
                // What the user picked on the portal goes into the shared land record (and from there everywhere).
                val cur = ctx.repo.data.properties.firstOrNull { it.id == p.id }
                if (cur != null) {
                    val l = cur.land
                    val n = l.copy(district = d.ifBlank { l.district }, sro = sro.ifBlank { l.sro }, village = v.ifBlank { l.village },
                        autoFilled = l.autoFilled - setOfNotNull("district".takeIf { d.isNotBlank() }, "village".takeIf { v.isNotBlank() }))
                    if (n != l) ctx.repo.setLand(p.id, n)
                }
            },
            place = gvPlace(p),
            preferredUnit = g?.unit ?: GvUnit.SQFT,
            onSave = { og -> ctx.repo.setRegistry(p.id, ctx.repo.data.properties.firstOrNull { it.id == p.id }?.registry?.copy(guideline = og) ?: reg.copy(guideline = og)); portalOpen = false; ctx.platform.toast(S.gvSaved.of(lang)) },
            onDismiss = { portalOpen = false },
        )
    }
    if (editValue) {
        GuidelineValueDialog(
            current = g, landAreaSqft = reg.landAreaSqft,
            onSave = { og, area -> ctx.repo.setRegistry(p.id, reg.copy(guideline = og, landAreaSqft = area)); editValue = false },
            onRemove = if (g != null) ({ ctx.repo.setRegistry(p.id, reg.copy(guideline = null)); editValue = false }) else null,
            onDismiss = { editValue = false },
        )
    }
    // 2.4.0: the place is the shared Land details record (one editor for the whole app).
    if (editPlace) LandDetailsDialog(ctx, p) { editPlace = false }
    if (editRates) DutyRatesDialog(ctx) { editRates = false }
}

@Composable
private fun DutyTable(d: DutyBreakdown, lang: Lang) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        d.guidelineValue?.let { KeyValueRow(S.dutyGuidelineTotal.s(), moneyText(it, lang)) }
        d.marketValue?.let { KeyValueRow(S.dutyMarket.s(), moneyText(it, lang)) }
        d.gapPct?.let { gap ->
            val t = (if (gap >= 0) S.dutyGapAbove else S.dutyGapBelow).f(dec(kotlin.math.abs(gap), 1) + "%")
            KeyValueRow(S.dutyGap.s(), t, valueColor = if (gap < 0) LocalBrand.current.warnText else androidx.compose.ui.graphics.Color.Unspecified)
        }
        KeyValueRow(S.dutyBase.s(), moneyText(d.dutyBase, lang) + if (d.guidelineValue != null && d.marketValue != null) "\n" + (if (d.baseIsGuideline) S.dutyBaseGv else S.dutyBasePrice).s() else "")
        KeyValueRow(S.dutyStamp.f(dec(d.stampPct, 2)), moneyText(d.stampDuty, lang))
        KeyValueRow(S.dutyReg.f(dec(d.regPct, 2)), moneyText(d.registrationFee, lang))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.dutyTotal.s(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(moneyText(d.total, lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Edits the two rates (Settings and the card share it). */
@Composable
fun DutyRatesDialog(ctx: AppContext, onDismiss: () -> Unit) {
    val st = ctx.repo.data.settings
    var stamp by remember { mutableStateOf(dec(st.stampDutyPct, 3)) }
    var regFee by remember { mutableStateOf(dec(st.registrationFeePct, 3)) }
    val sv = parseDouble(stamp)?.takeIf { it in 0.0..30.0 }
    val rv = parseDouble(regFee)?.takeIf { it in 0.0..30.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.dutyRatesTitle.s()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S.dutyRatesBody.s(), style = MaterialTheme.typography.bodySmall)
                Field(stamp, { stamp = it }, S.dutyStampPct.s(), keyboard = KeyboardType.Decimal, error = if (sv == null) "0 – 30" else null)
                Field(regFee, { regFee = it }, S.dutyRegPct.s(), keyboard = KeyboardType.Decimal, error = if (rv == null) "0 – 30" else null)
                TextButton(onClick = { stamp = dec(DutyRules.STAMP_DUTY_PCT, 3); regFee = dec(DutyRules.REGISTRATION_FEE_PCT, 3) }) { Text(S.dutyResetRates.s()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (sv != null && rv != null) { ctx.repo.setDutyRates(sv, rv); onDismiss() } }, enabled = sv != null && rv != null) { Text(S.save.s()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}

@Composable
private fun GuidelineValueDialog(
    current: OfficialGuideline?,
    landAreaSqft: Double?,
    onSave: (OfficialGuideline, Double?) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current?.value?.let { dec(it, 2) } ?: "") }
    var unit by remember { mutableStateOf(current?.unit ?: GvUnit.SQFT) }
    var effective by remember { mutableStateOf(current?.effectiveFrom ?: "") }
    var area by remember { mutableStateOf(landAreaSqft?.let { dec(it, 1) } ?: "") }
    val v = parseGuidelineText(value)?.value?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.gvOfficial.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(value, { t -> value = t; parseGuidelineText(t)?.unit?.let { unit = it } }, S.gvValueLabel.s(), keyboard = KeyboardType.Text, supporting = S.gvPasteHint.s())
                Text(S.gvUnitLabel.s(), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(GvUnit.entries, unit, { it.label().s() }) { unit = it }
                Field(effective, { effective = it }, S.gvEffectiveLabel.s())
                Field(area, { area = it }, S.gvLandArea.s(), keyboard = KeyboardType.Decimal, supporting = S.gvLandAreaHint.s())
                Text(S.gvNeverGuess.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = v != null, onClick = {
                if (v == null) return@TextButton
                // A value typed here keeps a portal origin only when it is the same value that was read there.
                val same = current != null && current.method == OfficialGuideline.METHOD_WEBVIEW && current.value == v && current.unit == unit
                val og = if (same) current!!.copy(effectiveFrom = effective.trim())
                else officialGuideline(v, unit, OfficialGuideline.METHOD_MANUAL, "", value, effective, nowMillis())
                onSave(og, parseDouble(area)?.takeIf { it > 0 })
            }) { Text(S.save.s()) }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text(S.gvRemove.s(), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(S.cancel.s()) }
            }
        },
    )
}

@Composable
private fun GuidelinePlaceDialog(reg: RegistryInfo, survey: String, onSave: (RegistryInfo, String) -> Unit, onDismiss: () -> Unit) {
    var district by remember { mutableStateOf(reg.district) }
    var sro by remember { mutableStateOf(reg.sro) }
    var village by remember { mutableStateOf(reg.village) }
    var street by remember { mutableStateOf(reg.street) }
    var sy by remember { mutableStateOf(survey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.gvEditPlace.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(S.gvPlaceHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field(district, { district = it }, S.gvDistrict.s())
                Field(sro, { sro = it }, S.gvSro.s())
                Field(village, { village = it }, S.gvVillage.s())
                Field(street, { street = it }, S.gvStreet.s())
                Field(sy, { sy = it }, S.gvSurvey.s())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(reg.copy(district = district.trim(), sro = sro.trim(), village = village.trim(), street = street.trim()), sy)
            }) { Text(S.save.s()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}

// ---------------------------------------------------------------- in-app TNREGINET page

/** A district name read off the (Tamil or English) portal list, in English for the land record. */
private fun englishDistrict(name: String): String {
    val t = name.trim()
    if (t.isBlank()) return ""
    return com.hark.sothudiary.domain.registry.TN_DISTRICTS.entries.firstOrNull { (en, others) -> en.equals(t, true) || others.any { it == t } }?.key ?: t
}

private fun parseCapture(json: String?): Pair<List<String>, String> {
    val o = runCatching { json?.let { Json.parseToJsonElement(it) as? JsonObject } }.getOrNull() ?: return emptyList<String>() to ""
    val sel = (o["sel"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val rows = (o["rows"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    return (listOf(sel) + rows) to ((o["url"] as? JsonPrimitive)?.contentOrNull.orEmpty())
}

/**
 * Full-screen TNREGINET page: fills the guideline form from [place], then lets the user read the value back (rows found on
 * the page, or a pasted value). Saves only what the user picks, with the page address and today's date.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidelinePortalOverlay(
    place: GvPlace, preferredUnit: GvUnit, onSave: (OfficialGuideline) -> Unit, onDismiss: () -> Unit,
    propertyId: String = "", onChoices: (String, String, String) -> Unit = { _, _, _ -> },
) {
    FullScreenOverlay {
        PlatformBackHandler(enabled = true) { onDismiss() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val lang = LocalLang.current
            val prefill = remember(place) { gvPrefillScript(place) }
            var script by remember { mutableStateOf(prefill) }
            var tick by remember { mutableIntStateOf(0) }
            var progress by remember { mutableIntStateOf(0) }
            var loads by remember { mutableIntStateOf(0) }
            var prefillRuns by remember { mutableIntStateOf(0) }
            var retry by remember { mutableIntStateOf(0) }
            var status by remember { mutableStateOf<String?>(null) }
            var candidates by remember { mutableStateOf<List<Pair<String, ParsedGuideline>>?>(null) }
            var pageUrl by remember { mutableStateOf(DutyRules.TNREGINET_GUIDELINE_URL) }
            var pasted by remember { mutableStateOf("") }
            var unit by remember { mutableStateOf(preferredUnit) }
            var effective by remember { mutableStateOf("") }
            var picked by remember { mutableStateOf<String?>(null) }

            // 2.4.0: Status / நிலை lines for this page session.
            var pageLine by remember { mutableStateOf(com.hark.sothudiary.data.SourceStatus("TNREGINET page", null, "loading…")) }
            var formLine by remember { mutableStateOf<com.hark.sothudiary.data.SourceStatus?>(null) }
            var stepDone by remember { mutableStateOf(false) }
            var lastChoices by remember { mutableStateOf(Triple("", "", "")) }
            LaunchedEffect(pageLine, formLine) { if (propertyId.isNotBlank()) GvSession.set(propertyId, listOfNotNull(pageLine, formLine)) }

            fun runPrefill() { script = prefill; prefillRuns = 0; tick++ }
            // After each page load, fill the form (the lists load one after another, so the script runs a few times).
            LaunchedEffect(loads) { if (loads > 0) { delay(1500); runPrefill() } }
            // The next list is loading by AJAX: try the next step a moment later.
            LaunchedEffect(retry) { if (retry > 0) { delay(1200); if (script == prefill) tick++ } }
            // 2.4.0: keep watching while the form is not complete – when the user picks an SRO / village on the page, the
            // next step is filled, and their choices are saved to the land record (every 2.5 s, at most 4 minutes).
            LaunchedEffect(loads) {
                if (loads == 0) return@LaunchedEffect
                repeat(96) {
                    delay(2500)
                    if (!stepDone && script == prefill) tick++
                }
            }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(LocalBrand.current.bar).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s(), tint = LocalBrand.current.onBar) }
                    Column(Modifier.weight(1f)) {
                        Text(S.gvPortalTitle.s(), color = LocalBrand.current.onBar, style = MaterialTheme.typography.titleMedium)
                        Text(pageUrl, color = LocalBrand.current.onBar.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (progress in 1..99) LinearProgressIndicator(Modifier.fillMaxWidth())
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(S.gvPortalHelp.s(), style = MaterialTheme.typography.bodySmall)
                        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { runPrefill() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.gvFillForm.s())
                            }
                            Button(onClick = { script = GV_CAPTURE_JS; tick++ }) { Text(S.gvReadValue.s()) }
                        }
                        val c = candidates
                        if (c != null) {
                            if (c.isEmpty()) Text(S.gvNothingRead.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
                            else {
                                Text(S.gvPickRow.s(), style = MaterialTheme.typography.labelLarge)
                                c.take(12).forEach { (row, pg) ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (picked == row) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            picked = row; pasted = row
                                            pg.unit?.let { unit = it }
                                        },
                                    ) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text(row, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            Text("→ " + gvRateText(pg.value, pg.unit ?: unit, lang), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(pasted, { t -> pasted = t; picked = null; parseGuidelineText(t)?.unit?.let { unit = it } },
                            modifier = Modifier.fillMaxWidth(), placeholder = { Text(S.gvPasteHint.s()) }, singleLine = false, maxLines = 3)
                        ChoiceChips(GvUnit.entries, unit, { it.label().s() }) { unit = it }
                        Field(effective, { effective = it }, S.gvEffectiveLabel.s())
                        val parsed = parseGuidelineText(pasted)
                        Button(
                            enabled = parsed != null,
                            onClick = {
                                val pg = parsed ?: return@Button
                                onSave(officialGuideline(pg.value, unit, OfficialGuideline.METHOD_WEBVIEW, pageUrl, pasted, effective, nowMillis()))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(S.gvUseValue.s() + (parsed?.let { "  " + gvRateText(it.value, unit, lang) } ?: ""))
                        }
                    }
                }
                HorizontalDivider()
                PortalWebView(
                    url = DutyRules.TNREGINET_GUIDELINE_URL,
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                    script = script,
                    scriptTick = tick,
                    onResult = { json ->
                        if (script == GV_CAPTURE_JS) {
                            val (rows, url) = parseCapture(json)
                            if (url.isNotBlank()) pageUrl = url
                            candidates = captureCandidates(rows.firstOrNull().orEmpty(), rows.drop(1))
                        } else {
                            val o = runCatching { json?.let { Json.parseToJsonElement(it) as? JsonObject } }.getOrNull()
                            fun b(k: String) = (o?.get(k) as? JsonPrimitive)?.booleanOrNull == true
                            fun t(k: String) = (o?.get(k) as? JsonPrimitive)?.contentOrNull.orEmpty()
                            val step = t("step")
                            stepDone = step == "done"
                            // The user's own choices on the page -> the shared land record.
                            (o?.get("sel") as? JsonObject)?.let { sel ->
                                fun sv(k: String) = (sel[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
                                val c = Triple(sv("district"), sv("sro"), sv("village"))
                                if (c != lastChoices && (c.first.isNotBlank() || c.second.isNotBlank())) {
                                    lastChoices = c
                                    onChoices(englishDistrict(c.first), c.second, c.third)
                                }
                            }
                            formLine = when {
                                o == null -> com.hark.sothudiary.data.SourceStatus(S4.gvStepForm.of(lang), false, "script error")
                                !b("found") -> com.hark.sothudiary.data.SourceStatus(S4.gvStepForm.of(lang), if (t("ready") == "complete") false else null,
                                    if (t("ready") == "complete") "not found on this page (ids districtList / SROList / villageList)" else S4.gvStepWaiting.of(lang))
                                t("miss").isNotBlank() -> {
                                    val sample = (o["sample"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                                    com.hark.sothudiary.data.SourceStatus(S4.gvStepForm.of(lang) + " · " + step, false,
                                        S4.gvStepNotInList.format(lang, t("miss"), t("options"), sample.take(3).joinToString(", ")), detail = "options: " + sample.joinToString(", "))
                                }
                                b("waiting") -> com.hark.sothudiary.data.SourceStatus(S4.gvStepForm.of(lang) + " · " + step, null, S4.gvStepWaiting.of(lang))
                                else -> com.hark.sothudiary.data.SourceStatus(S4.gvStepForm.of(lang), true, listOfNotNull(
                                    S.gvDistrict.of(lang).takeIf { b("district") }, S.gvSro.of(lang).takeIf { b("sro") },
                                    S.gvVillage.of(lang).takeIf { b("village") }).joinToString(", ") + if (stepDone) " ✓" else "")
                            }
                            if (o == null || !b("found")) {
                                if (prefillRuns >= 1) status = S.gvFormNotFound.of(lang)
                                // 2.4.0: the page may still be building the form: try again (before: gave up after one try).
                                if (prefillRuns < 6) { prefillRuns++; retry++ }
                            } else {
                                val done = listOfNotNull(
                                    S.gvDistrict.of(lang).takeIf { b("district") }, S.gvSro.of(lang).takeIf { b("sro") },
                                    S.gvVillage.of(lang).takeIf { b("village") }, (if (place.survey.isNotBlank()) S.gvSurvey else S.gvStreet).of(lang).takeIf { b("text") },
                                )
                                status = if (done.isEmpty()) null else S.gvFormFilled.format(lang, done.joinToString(", "))
                            }
                            // The next list loads by AJAX: run again while a step is still waiting (at most 8 times).
                            if (o != null && b("found") && b("waiting") && prefillRuns < 8) { prefillRuns++; retry++ }
                        }
                    },
                    onPageLoaded = { u -> pageUrl = u; loads++; if (pageLine.ok != false) pageLine = com.hark.sothudiary.data.SourceStatus(S4.gvStepPage.of(lang), true, "loaded", detail = u) },
                    onPageError = { e -> pageLine = com.hark.sothudiary.data.SourceStatus(S4.gvStepPage.of(lang), false, e, detail = pageUrl) },
                    onProgress = { progress = it },
                )
            }
        }
    }
}
