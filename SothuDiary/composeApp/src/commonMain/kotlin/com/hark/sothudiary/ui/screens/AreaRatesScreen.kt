package com.hark.sothudiary.ui.screens

import com.hark.sothudiary.domain.ai.llm
import com.hark.sothudiary.domain.ai.hasAiKey
import com.hark.sothudiary.domain.GeoPlace
import androidx.compose.runtime.LaunchedEffect
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.domain.pricing.freshLookup
import com.hark.sothudiary.domain.pricing.latestLookup
import com.hark.sothudiary.domain.pricing.liveLookup
import com.hark.sothudiary.domain.pricing.PriceRules
import com.hark.sothudiary.ui.components.FindOnlineButton
import com.hark.sothudiary.ui.components.GeminiKeyDialog
import com.hark.sothudiary.ui.components.GeminiSetupCard
import com.hark.sothudiary.ui.components.AI_STUDIO_URL
import androidx.compose.material3.Button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.convertRate
import com.hark.sothudiary.domain.groupBasis
import com.hark.sothudiary.domain.normalizeLocation
import com.hark.sothudiary.domain.pricing.AreaPoint
import com.hark.sothudiary.domain.pricing.estimateMarket
import com.hark.sothudiary.domain.pricing.nameMatches
import com.hark.sothudiary.domain.rateUnit
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.groupTr
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.ComparableRow
import com.hark.sothudiary.ui.components.LookupErrorNote
import com.hark.sothudiary.domain.unitPrice
import com.hark.sothudiary.domain.basis
import com.hark.sothudiary.ui.components.ComparablesMap
import com.hark.sothudiary.ui.components.KeyValueRow
import com.hark.sothudiary.ui.components.PriceRangeChart
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.components.altBases
import com.hark.sothudiary.ui.components.rangeText
import com.hark.sothudiary.ui.components.rateText
import com.hark.sothudiary.ui.components.reasoningLines
import kotlinx.coroutines.launch

/** A place the user asked about: a searched point or a property's pin. */
private data class AreaTarget(val name: String, val nameTa: String?, val lat: Double?, val lng: Double?, val district: String = "")

private fun GeoPlace.target(fallback: String) = AreaTarget(locality.ifBlank { fallback }, null, lat, lng, district)

/**
 * "What is the farmland rate near this village?" – a LIVE lookup for any place in India and any type, usable
 * before adding a property: same-type comparables within 5 km (10 km at most) from the Gemini + Google Search
 * lookup (cached 30 days) and the user's own saved properties. No bundled data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AreaRatesScreen(ctx: AppContext, initialPropertyId: String?, initialGroup: Int?, onBack: (() -> Unit)?) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val initialProp = remember { initialPropertyId?.let { id -> data.properties.firstOrNull { it.id == id } } }
    var group by remember { mutableStateOf(initialGroup ?: 0) }
    var query by remember { mutableStateOf(initialProp?.locality ?: "") }
    var target by remember {
        mutableStateOf(initialProp?.let { p -> AreaTarget(p.locality.ifBlank { p.title }, null, p.lat, p.lng, p.district) })
    }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchMsg by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf(false) }
    var lookupMsg by remember { mutableStateOf<String?>(null) }
    var askKey by remember { mutableStateOf(false) }
    val msgNone = S.noPlacesFound.s()

    fun searchMap() {
        val text = query.trim()
        if (text.length < 3) return
        searching = true
        searchMsg = null
        scope.launch {
            val r = Geocoder.search(text, limit = 8, nearLat = target?.lat, nearLng = target?.lng)
            searching = false
            results = r
            if (r.isEmpty()) searchMsg = msgNone
        }
    }

    fun areaKey(la: Double, ln: Double) = "area:" + com.hark.sothudiary.domain.pricing.lookupKey(la, ln, group)

    /**
     * 2.6.0: the online price search (AI provider chosen at setup) runs only when the user taps it – 2.4.0's "no
     * automatic price data" still holds – and in the app scope (CheckRunner), so leaving the screen does not cancel it.
     */
    fun lookup(t: AreaTarget, force: Boolean) {
        if (!force && !PriceRules.ONLINE_LOOKUP) return
        val llm = data.settings.llm()
        val la = t.lat; val ln = t.lng
        if (!llm.hasKey) { if (force) askKey = true; return }
        if (la == null || ln == null) return
        if (!force && freshLookup(data, la, ln, group) != null) return
        lookupMsg = null
        com.hark.sothudiary.domain.CheckRunner.price(ctx.repo, areaKey(la, ln), la, ln, group, t.name)
    }

    Scaffold(
        topBar = { AppTopBar(S.tabAreaRates.s(), ctx.toggleLang, onBack = onBack, subtitle = S.areaRatesSub.s()) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!data.settings.hasAiKey) GeminiSetupCard(ctx, onDismiss = null)
            SectionCard {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; searchMsg = null },
                    placeholder = { Text(S.areaSearchHint.s()) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = emptyList() }) { Icon(Icons.Filled.Clear, contentDescription = S.clear.s()) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { searchMap() }),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.trim().length >= 3) {
                    OutlinedButton(onClick = { searchMap() }, enabled = !searching, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(S.findOnMap.f(query.trim()))
                    }
                }
                results.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clickable { target = g.target(query.trim()); results = emptyList() }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(g.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                searchMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..3).forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = g }, label = { Text(groupTr(g).s()) }, shape = RoundedCornerShape(50))
                    }
                }
            }

            val t = target
            if (t != null) {
                val names = listOfNotNull(t.name, t.nameTa)
                // Opened from a property: never use that property (or a copy of it at the same spot and price) as its own comparable.
                val self = initialProp?.takeIf { it.lat == t.lat && it.lng == t.lng }
                val est = remember(t, group, data, self) {
                    val selfRate = self?.unitPrice()?.let { convertRate(it, self.type.basis(), groupBasis(group)) }
                    estimateMarket(AreaPoint(names, t.lat, t.lng, t.district), group, data,
                        excludePropertyId = self?.id, selfRate = selfRate, selfUrl = self?.sourceUrl.orEmpty())
                }
                val b = est.basis
                SectionCard(title = S.ratesIn.f(t.name, groupTr(group).s()), icon = Icons.Filled.Insights,
                    accent = MaterialTheme.colorScheme.primary) {
                    val med = est.median
                    if (med == null) {
                        Text(S.notEnoughNearby.s(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(rateText(med, b, lang), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text(S.typicalRate.s() + " • " + est.confidence.tr().s(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow(S.fairRange.s(), rangeText(est.fairLow, est.fairHigh, b, lang))
                        altBases(b).forEach { ab ->
                            KeyValueRow(S.inOtherUnit.s(), rangeText(est.fairLow?.let { convertRate(it, b, ab) }, est.fairHigh?.let { convertRate(it, b, ab) }, ab, lang))
                        }
                        KeyValueRow(S.guidelineValueShort.s(), est.guideline?.let { rateText(it.value, b, lang) + " (" + it.label + ")" } ?: S.notSet.s())
                        Spacer(Modifier.height(8.dp))
                        PriceRangeChart(est, null, MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    reasoningLines(est, null).forEach { line ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val la = t.lat; val ln = t.lng
                    val latest = if (la != null && ln != null) latestLookup(data, la, ln, group) else null
                    if (latest != null && latest.error == null) {
                        Text(
                            S.lookupStatus.format(lang, formatDateShort(latest.fetchedAt, lang), latest.found, latest.place.ifBlank { "—" }),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    com.hark.sothudiary.ui.screens.AddRateButton(ctx, t.name, t.lat, t.lng, group)
                    val aiSearching = la != null && ln != null && com.hark.sothudiary.domain.CheckRunner.running.containsKey(areaKey(la, ln))
                    if (data.settings.hasAiKey) {
                        if (la != null && ln != null) FindOnlineButton({ lookup(t, force = true) }, aiSearching, prominent = est.insufficient,
                            label = com.hark.sothudiary.i18n.S6.aiSearchPrice.f(data.settings.llm().provider.name))
                        Text(com.hark.sothudiary.i18n.S6.aiOnTapNote.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else OutlinedButton(onClick = { askKey = true }, modifier = Modifier.fillMaxWidth()) { Text(com.hark.sothudiary.i18n.S6.addAiKey.s()) }
                    if (!aiSearching && latest != null && latest.error == null && latest.fetchedAt > 0)
                        Text(if (latest.found > 0) S.onlineSaved.format(lang, latest.found, latest.skipped) else S.onlineNone.format(lang, latest.skipped),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    val failed = latest?.error
                    if (failed != null && failed != "no_key") LookupErrorNote(failed, latest.retryAt)
                    else lookupMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    // 2.3.0: free web search results when Gemini could not read them.
                    if (latest != null && latest.snippets.isNotEmpty()) WebSnippetList(latest.snippets) { ctx.platform.openUrl(it) }
                    if (est.comparables.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(S.comparablesTitle.f(est.comparables.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp))
                        est.comparables.forEach { c ->
                            ComparableRow(c, b, onOpenProperty = { ctx.nav.push(Route.PropertyDetail(it)) }, onOpenUrl = { ctx.platform.openUrl(it) })
                        }
                        Spacer(Modifier.height(6.dp))
                        ComparablesMap(t.lat, t.lng, t.name, 0xFFD32F2F, est,
                            onPinClick = { id -> if (id != "center" && !id.startsWith("m")) ctx.nav.push(Route.PropertyDetail(id)) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(S.askingPricesNote.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Earlier live lookups (cache) – tap to open
            val recent = data.rateLookups.filter { it.error == null }.sortedByDescending { it.fetchedAt }.take(12)
            if (recent.isNotEmpty()) {
                SectionCard(title = S.recentLookups.s()) {
                    recent.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { group = l.group; target = AreaTarget(l.place.substringBefore(",").ifBlank { "—" }, null, l.lat, l.lng) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(l.place.ifBlank { "${l.lat}, ${l.lng}" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(groupTr(l.group).s() + " • " + formatDateShort(l.fetchedAt, lang) + " • " + S.nComparables.f(l.found),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askKey) {
        GeminiKeyDialog(ctx, onDismiss = { askKey = false })
    }
}
