package com.hark.sothudiary.ui.screens

import com.hark.sothudiary.domain.ai.llm
import com.hark.sothudiary.domain.ai.hasAiKey
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.LandRecord
import com.hark.sothudiary.data.OnlineRate
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.RateKind
import com.hark.sothudiary.data.ScoreWeights
import com.hark.sothudiary.data.SourceStatus
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.CheckRunner
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.PropertyScore
import com.hark.sothudiary.domain.ScoreFactor
import com.hark.sothudiary.domain.basis
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.group
import com.hark.sothudiary.domain.land.LandRating
import com.hark.sothudiary.domain.land.LandTile
import com.hark.sothudiary.domain.land.VillageDirectory
import com.hark.sothudiary.domain.land.hasAnyData
import com.hark.sothudiary.domain.land.landCheckFor
import com.hark.sothudiary.domain.land.landCheckKey
import com.hark.sothudiary.domain.land.mergedWith
import com.hark.sothudiary.domain.land.parseLandText
import com.hark.sothudiary.domain.pricing.PriceCheck
import com.hark.sothudiary.domain.pricing.USER_RATE_KEY
import com.hark.sothudiary.domain.pricing.convertPastedRate
import com.hark.sothudiary.domain.pricing.parsePastedRates
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.registry.DutyRates
import com.hark.sothudiary.domain.registry.convertGvRate
import com.hark.sothudiary.domain.registry.propertyDuty
import com.hark.sothudiary.domain.registry.toGvUnit
import com.hark.sothudiary.domain.scoreBand
import com.hark.sothudiary.domain.with
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.S4
import com.hark.sothudiary.i18n.Tr
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.slashTr
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.PriceCheckCard
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.components.pctText
import com.hark.sothudiary.ui.components.rateText
import com.hark.sothudiary.ui.theme.LocalBrand
import com.hark.sothudiary.ui.theme.verdictColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ====================================================================== Status / நிலை panel

/** 2.4.0: one line per source the check used (✓ / ✗ / –) and a Details expander, so a failure can be screenshotted. */
@Composable
fun StatusPanel(lines: List<SourceStatus>, runningSince: Long? = null, extra: List<String> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(nowMillis()) }
    if (runningSince != null) LaunchedEffect(runningSince) { while (true) { now = nowMillis(); delay(1000) } }
    SectionCard(title = S4.status.s(), icon = Icons.Filled.FactCheck) {
        if (runningSince != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(S4.runningFor.f(((now - runningSince) / 1000).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
        }
        if (lines.isEmpty() && extra.isEmpty()) {
            Text(S4.noStatusYet.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lines.forEach { l ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                val (mark, color) = when (l.ok) {
                    true -> "✓" to Color(0xFF3D7A45)
                    false -> "✗" to Color(0xFFB0352B)
                    null -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(mark, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.source, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(l.reason + (if (l.ms > 0) "  ·  " + msText(l.ms) else ""), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        extra.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (lines.any { it.detail.isNotBlank() }) {
            TextButton(onClick = { open = !open }) { Text(if (open) S4.hideDetails.s() else S4.details.s()) }
            AnimatedVisibility(open) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    lines.filter { it.detail.isNotBlank() }.forEach {
                        Text(it.source + ": " + it.detail, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
        Text(S4.statusHint.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun msText(ms: Long) = if (ms < 1000) "$ms ms" else dec(ms / 1000.0, 1) + " s"

// ====================================================================== Collapsible section

/** 2.4.0: a section of the property screen that starts closed and shows a one-line summary. */
@Composable
fun CollapsibleSection(
    title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, summary: String = "", initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveableBool(title, initiallyOpen)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().clickable { open.value = !open.value }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (summary.isNotBlank() && !open.value) Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (open.value) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(open.value) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = content)
        }
    }
}

/** Open / closed state of a section, kept while the app runs (per title). */
private val sectionState = mutableMapOf<String, MutableState<Boolean>>()

@Composable
private fun rememberSaveableBool(key: String, initial: Boolean): MutableState<MutableState<Boolean>> =
    remember(key) { mutableStateOf(sectionState.getOrPut(key) { mutableStateOf(initial) }) }

// ====================================================================== Checks row (three tiles)

/** One-line results of the three checks, for the tiles and the share text. */
data class CheckLines(val price: String?, val land: String?, val gv: String?, val landLevel: LandRating?)

fun checkLines(ctx: AppContext, p: Property, check: PriceCheck, lang: com.hark.sothudiary.data.Lang): CheckLines {
    val data = ctx.repo.data
    // Price
    val price = check.unitPrice?.let { up ->
        val d = check.diffVsMarketPct
        val rate = rateText(up, check.basis, lang)
        when {
            d == null -> null
            abs(d) < 3 -> rate + " · " + S4.atArea.of(lang)
            d < 0 -> rate + " · " + S4.belowArea.format(lang, dec(abs(d), 0) + "%")
            else -> rate + " · " + S4.aboveArea.format(lang, dec(d, 0) + "%")
        }
    }
    // Land
    val lc = landCheckFor(data, p.id)?.takeIf { it.key == landCheckKey(p) && it.hasAnyData() }
    var level: LandRating? = null
    val land = lc?.let { c ->
        val a = com.hark.sothudiary.domain.land.assessLand(c, p, data.settings.hasAiKey)
        val r = a.tile(LandTile.RISK).rating
        level = r
        val word = when (r) { LandRating.GOOD -> S4.riskLow; LandRating.FAIR -> S4.riskMedium; LandRating.POOR -> S4.riskHigh; LandRating.UNKNOWN -> S4.riskUnknown }
        S4.waterRisk.format(lang, word.of(lang)) + if (c.osmFailedLayers.isNotEmpty() || !c.osmOk) " (" + S4.partial.of(lang) + ")" else ""
    }
    // Guideline + duty
    val rates = DutyRates(data.settings.stampDutyPct, data.settings.registrationFeePct)
    val duty = propertyDuty(p, rates)
    val g = p.registry.guideline
    val gv = when {
        g != null && duty != null -> {
            val u = p.type.basis().toGvUnit()
            val rate = convertGvRate(g.value, g.unit, u)
            S4.gvLine.format(lang, gvRateText(rate, u, lang), priceShort(duty.total, lang))
        }
        duty != null -> S4.dutyOnly.format(lang, priceShort(duty.total, lang))
        else -> null
    }
    return CheckLines(price, land, gv, level)
}

@Composable
fun CheckTilesRow(ctx: AppContext, p: Property, check: PriceCheck) {
    val lang = LocalLang.current
    val lines = checkLines(ctx, p, check, lang)
    val landRunning = CheckRunner.isRunning("land", p.id)
    Text(S4.checks.s(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckTile(
            Icons.Filled.Insights, S4.priceCheckTile.s(),
            lines.price ?: if (check.estimate.comparables.isEmpty()) S4.noComparables.s() + " · " + S4.notChecked.s().substringAfter("· ") else S4.notChecked.s(),
            if (lines.price != null) verdictColor(check.verdict) else null, Modifier.weight(1f),
        ) { ctx.nav.push(Route.PriceCheck(p.id)) }
        CheckTile(
            Icons.Filled.Layers, S4.landCheckTile.s(),
            when {
                landRunning -> S4.checking.s()
                p.lat == null || p.lng == null -> S4.needPin.s()
                else -> lines.land ?: S4.notChecked.s()
            },
            lines.landLevel?.let(::ratingColor), Modifier.weight(1f),
        ) { ctx.nav.push(Route.LandCheck(p.id)) }
        CheckTile(
            Icons.Filled.AccountBalance, S4.gvTile.s(), lines.gv ?: S4.notChecked.s(),
            if (p.registry.guideline != null) MaterialTheme.colorScheme.primary else null, Modifier.weight(1f),
        ) { ctx.nav.push(Route.Guideline(p.id)) }
    }
}

@Composable
private fun CheckTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, line: String, accent: Color?, modifier: Modifier, onClick: () -> Unit) {
    val c = accent ?: MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick, modifier = modifier.fillMaxHeight(), shape = RoundedCornerShape(14.dp),
        color = if (accent != null) accent.copy(alpha = if (LocalBrand.current.dark) 0.18f else 0.08f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.bodySmall, color = if (accent != null) com.hark.sothudiary.ui.theme.readable(accent) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ====================================================================== Score

fun ScoreFactor.tr(): Tr = when (this) {
    ScoreFactor.PRICE -> S4.fPrice
    ScoreFactor.WATER -> S4.fWater
    ScoreFactor.ROAD -> S4.fRoad
    ScoreFactor.AMENITIES -> S4.fAmenities
    ScoreFactor.GUIDELINE -> S4.fGuideline
}

fun scoreColor(s: Int?): Color = when (scoreBand(s)) {
    3 -> Color(0xFF2E7D4F)
    2 -> Color(0xFF7FA63A)
    1 -> Color(0xFFD08A1E)
    0 -> Color(0xFFB0352B)
    else -> Color(0xFF8A8F98)
}

fun scoreArgb(s: Int?): Long = when (scoreBand(s)) {
    3 -> 0xFF2E7D4F
    2 -> 0xFF7FA63A
    1 -> 0xFFD08A1E
    0 -> 0xFFB0352B
    else -> 0xFF8A8F98
}

/** The property's score from its own checks (price, land, guideline). */
fun scoreFor(ctx: AppContext, p: Property, check: PriceCheck?): PropertyScore {
    val data = ctx.repo.data
    val lc = landCheckFor(data, p.id)?.takeIf { it.key == landCheckKey(p) && it.hasAnyData() }
    val land = lc?.let { com.hark.sothudiary.domain.land.assessLand(it, p, data.settings.hasAiKey) }
    val duty = propertyDuty(p, DutyRates(data.settings.stampDutyPct, data.settings.registrationFeePct))?.takeIf { it.guidelineValue != null }
    return com.hark.sothudiary.domain.propertyScore(check, land, duty, data.settings.scoreWeights)
}

@Composable
fun ScoreBadge(score: Int?, size: Int = 46) {
    val c = scoreColor(score)
    Box(Modifier.size(size.dp).border(3.dp, c, CircleShape).padding(3.dp), contentAlignment = Alignment.Center) {
        Text(score?.toString() ?: "–", fontWeight = FontWeight.ExtraBold, color = c, fontSize = (size / 2.6).sp)
    }
}

@Composable
fun ScoreCard(score: PropertyScore) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
        ScoreBadge(score.total)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(S4.scoreTitle.s(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            val lang = LocalLang.current
            Text(if (score.total == null) S4.scoreNone.s() else score.factors.filter { it.score != null && it.weight > 0 }.joinToString(" · ") { it.factor.tr().of(lang) + " " + it.score },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    AnimatedVisibility(open) {
        Column(Modifier.padding(top = 6.dp)) {
            score.factors.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.factor.tr().s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (f.score != null) {
                        LinearProgressIndicator(progress = { f.score / 100f }, modifier = Modifier.width(80.dp).height(6.dp), color = scoreColor(f.score))
                        Spacer(Modifier.width(6.dp))
                        Text(f.score.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(26.dp))
                    } else Text(S4.notYet.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("×" + f.weight, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                }
            }
            Text(S4.scoreHint.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Settings: the weights of the score factors. */
@Composable
fun ScoreWeightsCard(ctx: AppContext) {
    val w = ctx.repo.data.settings.scoreWeights
    SectionCard(title = S4.scoreWeights.s(), icon = Icons.Filled.Tune, trailing = {
        TextButton(onClick = { ctx.repo.setScoreWeights(ScoreWeights()) }) { Text(S4.resetWeights.s()) }
    }) {
        Text(S4.scoreWeightsHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val total = ScoreFactor.entries.sumOf { wOf(w, it) }.coerceAtLeast(1)
        ScoreFactor.entries.forEach { f ->
            val v = wOf(w, f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.tr().s(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${v * 100 / total}%  ·  $v", style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = v.toFloat(), onValueChange = { ctx.repo.setScoreWeights(w.with(f, it.toInt())) }, valueRange = 0f..50f, steps = 49)
        }
    }
}

private fun wOf(w: ScoreWeights, f: ScoreFactor): Int = with(w) {
    when (f) { ScoreFactor.PRICE -> price; ScoreFactor.WATER -> water; ScoreFactor.ROAD -> road; ScoreFactor.AMENITIES -> amenities; ScoreFactor.GUIDELINE -> guideline }
}

// ====================================================================== Price check screen

@Composable
fun PriceCheckScreen(ctx: AppContext, propertyId: String) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val p = data.properties.firstOrNull { it.id == propertyId }
    if (p == null) {
        Scaffold(topBar = { AppTopBar(S.priceCheck.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }) }) { pad ->
            Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.Insights, S.notFound.s(), "") }
        }
        return
    }
    val check = remember(data) { com.hark.sothudiary.domain.pricing.checkPrice(p, data) }
    val group = p.type.group()
    val mine = data.onlineRates.filter { it.lookupKey == USER_RATE_KEY && it.group == group }
        .filter { r -> val la = r.lat; val ln = r.lng; val pl = p.lat; val pn = p.lng
            la == null || ln == null || pl == null || pn == null || com.hark.sothudiary.domain.haversineKm(la, ln, pl, pn) <= com.hark.sothudiary.domain.pricing.PriceRules.MAX_KM }
    Scaffold(
        topBar = { AppTopBar(S.priceCheck.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }, subtitle = p.title) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 2.6.0 (issue #1): the online price search with the AI provider chosen at setup – only on the user's tap.
            val llm = data.settings.llm()
            val pLat = p.lat; val pLng = p.lng
            val runKey = "price:${p.id}"
            val searching = com.hark.sothudiary.domain.CheckRunner.isRunning("price", p.id)
            val latest = if (pLat != null && pLng != null) com.hark.sothudiary.domain.pricing.latestLookup(data, pLat, pLng, group) else null
            var askKey by remember { mutableStateOf(false) }
            val hint = listOf(p.land.village.ifBlank { p.locality }, p.land.taluk.ifBlank { p.taluk }, p.land.district.ifBlank { p.district })
                .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { p.address }
            PriceCheckCard(
                p, check,
                onOpenProperty = { ctx.nav.push(Route.PropertyDetail(it)) },
                onOpenUrl = { ctx.platform.openUrl(it) },
                onAreaRates = { ctx.nav.push(Route.AreaRates(p.id, group)) },
                onFindOnline = null, needsKey = false,
                latestLookup = latest,
            )
            SectionCard(title = com.hark.sothudiary.i18n.S4.rsSourceOnline.s(), icon = Icons.Filled.Insights) {
                when {
                    !llm.hasKey -> {
                        Text(com.hark.sothudiary.i18n.S6.priceNeedKey.s(), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { askKey = true }, modifier = Modifier.fillMaxWidth()) { Text(com.hark.sothudiary.i18n.S6.addAiKey.s()) }
                    }
                    pLat == null || pLng == null -> Text(com.hark.sothudiary.i18n.S6.needPinForAi.s(), style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        Text(com.hark.sothudiary.i18n.S6.aiOnTapNote.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (latest != null) Text(com.hark.sothudiary.i18n.S6.aiLastRun.f(com.hark.sothudiary.domain.formatDateShort(latest.fetchedAt, lang)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.hark.sothudiary.ui.components.FindOnlineButton(
                            onClick = { com.hark.sothudiary.domain.CheckRunner.price(ctx.repo, runKey, pLat, pLng, group, hint) },
                            busy = searching, prominent = check.estimate.insufficient,
                            label = com.hark.sothudiary.i18n.S6.aiSearchPrice.f(llm.provider.name),
                        )
                        if (searching) Text(com.hark.sothudiary.i18n.S6.aiSearching.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        val err = latest?.error
                        if (!searching && err != null) com.hark.sothudiary.ui.components.LookupErrorNote(err, latest.retryAt)
                        else if (!searching && latest != null && latest.error == null)
                            Text(if (latest.found > 0) S.onlineSaved.format(lang, latest.found, latest.skipped) else S.onlineNone.format(lang, latest.skipped),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (askKey) com.hark.sothudiary.ui.components.GeminiKeyDialog(ctx) { askKey = false }
            SectionCard(title = S4.rsYourRates.s(), icon = Icons.Filled.EditNote) {
                Text(S4.rsAddRateHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                mine.forEach { r -> UserRateRow(ctx, r) }
                AddRateButton(ctx, p.land.village.ifBlank { p.locality }.ifBlank { p.title }, p.lat, p.lng, group, p.land.district.ifBlank { p.district })
            }
            val own = check.estimate.comparables.count { it.source == com.hark.sothudiary.domain.pricing.CompSource.YOUR_LISTING }
            // 2.6.0: rates the user added vs rates found by the online search (both are "ONLINE" comparables).
            val youNames = setOf(S4.rsEnteredByYou.en, S4.rsEnteredByYou.ta)
            val entered = check.estimate.comparables.count { it.source == com.hark.sothudiary.domain.pricing.CompSource.ONLINE && it.sourceName in youNames }
            StatusPanel(
                listOf(
                    SourceStatus(S4.rsSourceOwn.of(lang), own > 0, S4.rsUsedCount.format(lang, own)),
                    SourceStatus(S4.rsSourceEntered.of(lang), entered > 0, S4.rsUsedCount.format(lang, entered)),
                    SourceStatus(S4.gvTile.of(lang), p.registry.guideline != null, if (p.registry.guideline != null) S4.rsGvSaved.of(lang) else S4.rsGvMissing.of(lang)),
                    onlineStatusLine(lang, llm.hasKey, latest),
                ) + latest?.status.orEmpty(),
                runningSince = com.hark.sothudiary.domain.CheckRunner.running[runKey],
            )
        }
    }
}

/** 2.6.0: the "Online price sites" line of the price check's Status panel. */
fun onlineStatusLine(lang: com.hark.sothudiary.data.Lang, hasKey: Boolean, latest: com.hark.sothudiary.data.RateLookup?): SourceStatus {
    val name = S4.rsSourceOnline.of(lang)
    return when {
        !hasKey && latest == null -> SourceStatus(name, null, com.hark.sothudiary.i18n.S6.rsOnlineNoKey.of(lang))
        latest == null -> SourceStatus(name, null, com.hark.sothudiary.i18n.S6.rsOnlineNotRun.of(lang))
        latest.error != null -> SourceStatus(name, false, com.hark.sothudiary.i18n.S6.rsOnlineFailed.format(lang, com.hark.sothudiary.domain.pricing.shortAiError(latest.error)))
        else -> SourceStatus(name, latest.found > 0, com.hark.sothudiary.i18n.S6.rsOnlineFound.format(lang, latest.found,
            com.hark.sothudiary.domain.formatDateShort(latest.fetchedAt, lang)))
    }
}

@Composable
private fun UserRateRow(ctx: AppContext, r: OnlineRate) {
    val lang = LocalLang.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val v = r.typical ?: r.min ?: 0.0
            Text("₹" + com.hark.sothudiary.domain.indianGroup(v.toLong()) + " / " + r.unit + (if (r.kind == RateKind.GUIDELINE) "  (" + S4.gvShort.of(lang) + ")" else ""),
                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(listOf(r.place, r.note).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (r.sourceUrl.isNotBlank()) Text(r.sourceUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { ctx.platform.openUrl(r.sourceUrl) })
        }
        IconButton(onClick = { ctx.repo.deleteOnlineRate(r.id) }) { Icon(Icons.Filled.Delete, contentDescription = S.delete.s()) }
    }
}

/** "Add a rate you saw": paste a listing / message, the rate is read and saved as the user's own comparable. */
@Composable
fun AddRateButton(ctx: AppContext, place: String, lat: Double?, lng: Double?, group: Int, district: String = "") {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S4.rsAddRate.s())
    }
    if (open) AddRateDialog(ctx, place, lat, lng, group, district) { open = false }
}

@Composable
private fun AddRateDialog(ctx: AppContext, place0: String, lat: Double?, lng: Double?, group: Int, district: String, onDone: () -> Unit) {
    val lang = LocalLang.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var place by remember { mutableStateOf(place0) }
    var link by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(if (group == 1) "acre" else if (group >= 2) "sqft" else "cent") }
    var guideline by remember { mutableStateOf(false) }
    val found = remember(text) { parsePastedRates(text) }
    var pick by remember(found) { mutableStateOf(found.firstOrNull()) }
    LaunchedEffect(found) { found.firstOrNull()?.let { unit = it.unit; guideline = it.guideline } }
    val manualValue = manual.replace(",", "").toDoubleOrNull()
    val value = manualValue ?: pick?.let { convertPastedRate(it.value, it.unit, unit) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(S4.rsAddRate.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(S4.rsPasteHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(text, { t -> text = t; if (link.isBlank()) com.hark.sothudiary.domain.extractUrl(t)?.let { link = it } },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("₹1,822/sqft · Rs 3.5 lakh per cent · Rs. 36 lakhs, 22 cents") }, minLines = 2, maxLines = 5)
                TextButton(onClick = { clipboard.getText()?.text?.let { t -> text = t; com.hark.sothudiary.domain.extractUrl(t)?.let { link = it } } }) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(S4.rsPasteNow.s())
                }
                found.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { pick = r; unit = r.unit; guideline = r.guideline; manual = "" }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pick == r && manual.isBlank(), onClick = { pick = r; unit = r.unit; guideline = r.guideline; manual = "" })
                        Text("₹" + com.hark.sothudiary.domain.indianGroup(r.value.toLong()) + " / " + r.unit + "  ← " + r.text, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (text.isNotBlank() && found.isEmpty()) Text(S4.rsNothingFound.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
                Field(manual, { manual = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, S4.rsOrType.s(),
                    keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                com.hark.sothudiary.ui.components.ChoiceChips(listOf("sqft", "cent", "acre"), unit, { it }) { u -> unit = u }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(guideline, { guideline = it })
                    Text(S4.rsIsGuideline.s(), style = MaterialTheme.typography.bodySmall)
                }
                Field(place, { place = it }, S4.rsPlace.s())
                Field(link, { link = it }, S4.rsLink.s())
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = {
                val v = value ?: return@TextButton
                ctx.repo.addOnlineRates(listOf(OnlineRate(
                    id = newId(), place = place.trim().ifBlank { place0 }, district = district, lat = lat, lng = lng, group = group,
                    kind = if (guideline) RateKind.GUIDELINE else RateKind.ASKING, unit = unit, typical = v,
                    sourceName = S4.rsEnteredByYou.of(lang), sourceUrl = link.trim(), asOf = com.hark.sothudiary.domain.formatDateShort(nowMillis(), lang),
                    fetchedAt = nowMillis(), note = (pick?.text ?: text).take(120), lookupKey = USER_RATE_KEY, distanceKm = 0.0,
                )))
                onDone()
            }) { Text(S.save.s(), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(S.cancel.s()) } },
    )
}

// ====================================================================== Guideline value screen

@Composable
fun GuidelineScreen(ctx: AppContext, propertyId: String) {
    val p = ctx.repo.data.properties.firstOrNull { it.id == propertyId }
    Scaffold(
        topBar = { AppTopBar(S.gvTitle.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }, subtitle = p?.title) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (p == null) { Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.AccountBalance, S.notFound.s(), "") }; return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuidelineDutyCard(ctx, p)
            LandDetailsSection(ctx, p, initiallyOpen = true)
            StatusPanel(GvSession.lines(p.id))
        }
    }
}

/** What happened on the TNREGINET page in this app session (for the Status panel). */
object GvSession {
    private val byProperty = mutableStateMapOf<String, List<SourceStatus>>()
    fun lines(id: String): List<SourceStatus> = byProperty[id].orEmpty()
    fun set(id: String, lines: List<SourceStatus>) { byProperty[id] = lines }
}

// ====================================================================== Land details (shared record)

/** One line for the collapsed section and the share text: "Tenkasi · Shenkottai · Vadakarai · S.F. 123/4B · Patta 456". */
fun landSummary(l: LandRecord, lang: com.hark.sothudiary.data.Lang): String = listOfNotNull(
    l.district.ifBlank { null }, l.taluk.ifBlank { null }, l.village.ifBlank { null },
    l.sro.ifBlank { null }?.let { "SRO $it" },
    l.surveyFull.ifBlank { null }?.let { S4.surveyNo.of(lang) + " " + it },
    l.pattaNo.ifBlank { null }?.let { S4.pattaNo.of(lang) + " " + it },
).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandDetailsSection(ctx: AppContext, p: Property, initiallyOpen: Boolean = false) {
    val lang = LocalLang.current
    var editing by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val l = p.land
    CollapsibleSection(S4.landDetails.s(), Icons.Filled.Description, landSummary(l, lang).ifBlank { S4.landEmpty.s() }, initiallyOpen) {
        Text(S4.landDetailsHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        fun row(label: Tr, v: String, key: String? = null) = Triple(label, v, key)
        listOf(
            row(S4.district, l.district, "district"), row(S4.taluk, l.taluk, "taluk"), row(S4.village, l.village, "village"), row(S4.sro, l.sro),
            row(S4.surveyNo, l.surveyFull), row(S4.pattaNo, l.pattaNo),
            row(S4.area, if (p.size > 0) com.hark.sothudiary.ui.components.sizeLine(p) else ""),
        ).forEach { (label, v, key) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(150.dp))
                Text(v.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (key != null && key in l.autoFilled && v.isNotBlank()) Text("✨", fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = { editing = true }) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S4.editLand.s())
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(S4.officialLinks.s(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            fun open(url: String) {
                // The official sites are opened in the browser; the survey / patta number is copied to paste there.
                val copy = listOf(l.surveyFull, l.pattaNo).filter { it.isNotBlank() }.joinToString(" ")
                if (copy.isNotBlank()) { clipboard.setText(AnnotatedString(l.surveyFull.ifBlank { l.pattaNo })); ctx.platform.toast(S4.copied.of(lang) + ": " + l.surveyFull.ifBlank { l.pattaNo }) }
                ctx.platform.openUrl(url)
            }
            AssistChip(onClick = { open(com.hark.sothudiary.domain.registry.DutyRules.TNREGINET_HOME) }, label = { Text(S4.ecLink.s()) })
            AssistChip(onClick = { open(TN_ESERVICES_URL) }, label = { Text(S4.pattaLink.s()) })
            AssistChip(onClick = { open(TNGIS_VIEWER_URL) }, label = { Text(S4.tngisLink.s()) })
        }
    }
    if (editing) LandDetailsDialog(ctx, p) { editing = false }
}

/** Editor of the shared land record: pickers from the bundled LGD village list, paste-and-read, fill from the pin. */
@Composable
fun LandDetailsDialog(ctx: AppContext, p: Property, onDone: () -> Unit) {
    val lang = LocalLang.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val dir = VillageDirectory.instance
    var rec by remember { mutableStateOf(p.land) }
    var street by remember { mutableStateOf(p.registry.street) }
    var survey by remember { mutableStateOf(p.land.surveyFull) }
    var paste by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf<String?>(null) }
    var filling by remember { mutableStateOf(false) }
    fun applyPaste(t: String) {
        val found = parseLandText(t)
        rec = rec.mergedWith(found)
        if (found.surveyNo.isNotBlank()) survey = found.surveyFull
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(S4.landDetails.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(S4.landDetailsHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Paste a patta / EC / guideline text: every field found is filled in.
                OutlinedTextField(paste, { t -> paste = t; applyPaste(t) }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(S4.landPasteHint.s()) }, minLines = 1, maxLines = 4,
                    trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let { t -> paste = t; applyPaste(t) } }) { Icon(Icons.Filled.ContentPaste, contentDescription = S4.rsPasteNow.s()) } })
                if (p.lat != null && p.lng != null) {
                    TextButton(enabled = !filling, onClick = {
                        filling = true
                        scope.launch {
                            val g = runCatching { Geocoder.reverse(p.lat, p.lng) }.getOrNull()
                            if (g != null) {
                                val d = dir?.district(g.district) ?: g.district
                                val t = dir?.taluk(d, g.taluk) ?: g.taluk
                                rec = rec.fillFromMapLocal(d, t, g.locality)
                            }
                            filling = false
                        }
                    }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (filling) S4.checking.s() else S4.fillFromPin.s())
                    }
                }
                PickField(S4.district.s(), rec.district, "district" in rec.autoFilled, dir != null, onPick = { picker = "district" },
                    onChange = { rec = rec.copy(district = it, autoFilled = rec.autoFilled - "district") })
                PickField(S4.taluk.s(), rec.taluk, "taluk" in rec.autoFilled, dir != null && rec.district.isNotBlank(), onPick = { picker = "taluk" },
                    onChange = { rec = rec.copy(taluk = it, autoFilled = rec.autoFilled - "taluk") })
                PickField(S4.village.s(), rec.village, "village" in rec.autoFilled, dir != null && rec.district.isNotBlank(), onPick = { picker = "village" },
                    onChange = { rec = rec.copy(village = it, autoFilled = rec.autoFilled - "village") })
                Field(rec.sro, { rec = rec.copy(sro = it) }, S4.sro.s())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(survey, { t -> survey = t
                        val (n, sub) = com.hark.sothudiary.data.splitSurvey(t)
                        rec = rec.copy(surveyNo = n, subdivision = sub) }, S4.surveyNo.s() + " / " + S4.subdivision.s(), Modifier.weight(1f),
                        supporting = "123/4B")
                    Field(rec.pattaNo, { rec = rec.copy(pattaNo = it.filter { c -> c.isLetterOrDigit() || c == '/' || c == '-' }) }, S4.pattaNo.s(), Modifier.weight(0.7f),
                        keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
                }
                Field(street, { street = it }, S4.street.s())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.repo.setLand(p.id, rec.copy(district = rec.district.trim(), taluk = rec.taluk.trim(), village = rec.village.trim(), sro = rec.sro.trim(),
                    surveyNo = rec.surveyNo.trim(), subdivision = rec.subdivision.trim().uppercase(), pattaNo = rec.pattaNo.trim()))
                val cur = ctx.repo.data.properties.firstOrNull { it.id == p.id }
                if (cur != null && street.trim() != cur.registry.street) ctx.repo.setRegistry(p.id, cur.registry.copy(street = street.trim()))
                onDone()
            }) { Text(S.save.s(), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(S.cancel.s()) } },
    )
    val pk = picker
    if (pk != null && dir != null) {
        val options = when (pk) {
            "district" -> dir.districts
            "taluk" -> dir.taluks(rec.district)
            else -> dir.villages(rec.district, rec.taluk)
        }
        NamePicker(title = when (pk) { "district" -> S4.district; "taluk" -> S4.taluk; else -> S4.village }.s(), options = options, dir = dir,
            onPick = { v ->
                rec = when (pk) {
                    "district" -> rec.copy(district = v, taluk = if (dir.taluk(v, rec.taluk) == null) "" else rec.taluk, autoFilled = rec.autoFilled - "district")
                    "taluk" -> rec.copy(taluk = v, autoFilled = rec.autoFilled - "taluk")
                    else -> rec.copy(village = v, taluk = rec.taluk.ifBlank { dir.talukOf(rec.district, v).orEmpty() }, autoFilled = rec.autoFilled - "village")
                }
                picker = null
            },
            onDismiss = { picker = null })
    }
}

/** Fills district / taluk / village from the pin, keeping what the user typed (auto-filled values may be replaced). */
private fun LandRecord.fillFromMapLocal(district: String, taluk: String, village: String): LandRecord {
    val auto = autoFilled.toMutableSet()
    fun choose(cur: String, v: String, key: String) = if ((cur.isBlank() || key in autoFilled) && v.isNotBlank()) { auto += key; v } else cur
    val d = choose(this.district, district, "district"); val t = choose(this.taluk, taluk, "taluk"); val v = choose(this.village, village, "village")
    return copy(district = d, taluk = t, village = v, autoFilled = auto)
}

@Composable
private fun PickField(label: String, value: String, auto: Boolean, canPick: Boolean, onPick: () -> Unit, onChange: (String) -> Unit) {
    Field(value, onChange, label, autoFilled = auto, trailingIcon = if (canPick) ({
        IconButton(onClick = onPick) { Icon(Icons.Filled.List, contentDescription = label) }
    }) else null)
}

/** A searchable list of names (districts / taluks / villages from the bundled directory). */
@Composable
private fun NamePicker(title: String, options: List<String>, dir: VillageDirectory, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, options) { dir.filter(options, q, 200) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) })
                Text(S4.lgdCredit.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown) { o ->
                        Text(o, modifier = Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 10.dp, horizontal = 4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { if (q.isNotBlank()) TextButton(onClick = { onPick(q.trim()) }) { Text(S4.useTyped.f(q.trim())) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}
