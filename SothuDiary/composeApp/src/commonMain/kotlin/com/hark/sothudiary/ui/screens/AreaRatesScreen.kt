package com.hark.sothudiary.ui.screens

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
import com.hark.sothudiary.data.market.MarketData
import com.hark.sothudiary.data.market.MarketLocality
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.convertRate
import com.hark.sothudiary.domain.groupBasis
import com.hark.sothudiary.domain.normalizeLocation
import com.hark.sothudiary.domain.pricing.AreaPoint
import com.hark.sothudiary.domain.pricing.estimateMarket
import com.hark.sothudiary.domain.pricing.forGroup
import com.hark.sothudiary.domain.pricing.nameMatches
import com.hark.sothudiary.domain.rateUnit
import com.hark.sothudiary.data.market.rangeIn
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
import com.hark.sothudiary.ui.components.ComparablesMap
import com.hark.sothudiary.ui.components.KeyValueRow
import com.hark.sothudiary.ui.components.PriceRangeChart
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.components.altBases
import com.hark.sothudiary.ui.components.rangeText
import com.hark.sothudiary.ui.components.rateText
import com.hark.sothudiary.ui.components.reasoningLines
import kotlinx.coroutines.launch

/** A place the user asked about: a dataset locality or a searched point. */
private data class AreaTarget(val name: String, val nameTa: String?, val lat: Double?, val lng: Double?)

private fun MarketLocality.target() = AreaTarget(name, nameTa, lat, lng)

/**
 * "What is the farmland rate near Jaihindpuram?" – rates for any locality and type, usable before
 * adding a property. Combines the bundled dataset with the user's own listings nearby.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AreaRatesScreen(ctx: AppContext, initialLocality: String?, initialGroup: Int?, onBack: (() -> Unit)?) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val dataset = MarketData.dataset
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf(initialGroup ?: 0) }
    var query by remember { mutableStateOf(initialLocality ?: "") }
    val initialTarget = remember {
        initialLocality?.let { n -> dataset.localities.firstOrNull { nameMatches(it.name, n) }?.target() ?: AreaTarget(n, null, null, null) }
    }
    var target by remember { mutableStateOf(initialTarget) }
    var searching by remember { mutableStateOf(false) }
    var searchMsg by remember { mutableStateOf<String?>(null) }
    val msgNone = S.noPlacesFound.s()

    val q = normalizeLocation(query)
    val suggestions = if (q.length < 2 || (target != null && target?.name == query)) emptyList() else dataset.localities.filter { l ->
        normalizeLocation(l.name).contains(q) || (l.nameTa?.let { normalizeLocation(it).contains(q) } ?: false)
    }.take(8)

    fun searchMap() {
        val text = query.trim()
        if (text.length < 3) return
        searching = true
        searchMsg = null
        scope.launch {
            val r = Geocoder.search(if (text.contains("madurai", ignoreCase = true)) text else "$text Madurai")
                .ifEmpty { Geocoder.search(text) }
            searching = false
            val first = r.firstOrNull()
            if (first == null) searchMsg = msgNone
            else target = AreaTarget(first.locality.ifBlank { text }, null, first.lat, first.lng)
        }
    }

    Scaffold(
        topBar = { AppTopBar(S.tabAreaRates.s(), ctx.toggleLang, onBack = onBack, subtitle = S.areaRatesSub.s()) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; searchMsg = null },
                    placeholder = { Text(S.areaSearchHint.s()) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else if (query.isNotEmpty()) IconButton(onClick = { query = ""; target = null }) { Icon(Icons.Filled.Clear, contentDescription = S.clear.s()) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val exact = suggestions.firstOrNull()
                        if (exact != null) { target = exact.target(); query = exact.name } else searchMap()
                    }),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                suggestions.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { target = l.target(); query = l.name }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(l.name + (l.nameTa?.let { " • $it" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (query.trim().length >= 3 && (target == null || target?.name != query)) {
                    OutlinedButton(onClick = { searchMap() }, enabled = !searching, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(S.findOnMap.f(query.trim()))
                    }
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
                val est = remember(t, group, data) { estimateMarket(AreaPoint(names, t.lat, t.lng), group, data, dataset) }
                val b = est.basis
                SectionCard(title = S.ratesIn.f(if (lang == Lang.TA && t.nameTa != null) t.nameTa else t.name, groupTr(group).s()), icon = Icons.Filled.Insights,
                    accent = MaterialTheme.colorScheme.primary) {
                    val med = est.median
                    if (med == null) {
                        Text(S.noRateData.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // Browse all localities that have data for this type
            val unit = groupBasis(group).rateUnit()
            val rows = dataset.localities.mapNotNull { l ->
                val (tr, fb) = l.rates?.forGroup(group) ?: return@mapNotNull null
                if (fb) return@mapNotNull null
                val r = tr.rangeIn(unit) ?: return@mapNotNull null
                Triple(l, r, tr)
            }.sortedBy { it.first.name }
            SectionCard(title = S.allLocalities.f(groupTr(group).s(), rows.size)) {
                if (rows.isEmpty()) Text(S.noRateData.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                rows.forEach { (l, r, tr) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { target = l.target(); query = l.name }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (lang == Lang.TA && l.nameTa != null) l.nameTa else l.name, fontWeight = FontWeight.SemiBold)
                            Text(listOf(tr.sourceName, tr.asOf).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(rangeText(r.min ?: r.mid, r.max ?: r.mid, groupBasis(group), lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(dataset.notes.take(400), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
