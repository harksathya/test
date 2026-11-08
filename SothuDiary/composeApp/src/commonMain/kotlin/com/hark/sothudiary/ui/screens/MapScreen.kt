package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.domain.DEFAULT_MAP_CENTER
import com.hark.sothudiary.domain.group
import com.hark.sothudiary.domain.land.LandRating
import com.hark.sothudiary.domain.land.LandTile
import com.hark.sothudiary.domain.land.assessLand
import com.hark.sothudiary.domain.land.hasAnyData
import com.hark.sothudiary.domain.land.landCheckFor
import com.hark.sothudiary.domain.land.landCheckKey
import com.hark.sothudiary.domain.pricePerSqft
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.USER_RATE_KEY
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.domain.pricing.isFresh
import com.hark.sothudiary.domain.sqft
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.S4
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.groupTr
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.shortTr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.PropertyImage
import com.hark.sothudiary.ui.components.locationLine
import com.hark.sothudiary.ui.components.propertyImageModel
import com.hark.sothudiary.ui.map.CLUSTER_PREFIX
import com.hark.sothudiary.ui.map.GeoMap
import com.hark.sothudiary.ui.map.MapItem
import com.hark.sothudiary.ui.map.MapViewport
import com.hark.sothudiary.ui.map.clusterItems
import com.hark.sothudiary.ui.map.isValidCoord
import com.hark.sothudiary.ui.map.shortRupees
import com.hark.sothudiary.ui.theme.verdictArgb
import com.hark.sothudiary.ui.theme.verdictColor
import kotlinx.coroutines.launch

/** 2.4.0: what the pins are coloured by. */
enum class ColorBy { VERDICT, PRICE, WATER, SCORE }

/** Budget filter steps (total asking price, ₹). */
private val budgets: List<Pair<Long, Long>> = listOf(0L to 10_00_000L, 10_00_000L to 25_00_000L, 25_00_000L to 50_00_000L, 50_00_000L to 1_00_00_000L, 1_00_00_000L to Long.MAX_VALUE)

private fun budgetLabel(b: Pair<Long, Long>, lang: com.hark.sothudiary.data.Lang): String = when {
    b.first == 0L -> "< " + priceShort(b.second, lang)
    b.second == Long.MAX_VALUE -> "> " + priceShort(b.first, lang)
    else -> priceShort(b.first, lang) + "–" + priceShort(b.second, lang)
}

/** Rate shown on a property's tag: ₹ per sq ft (₹ per acre for farmland). */
private fun tagRate(p: Property): Pair<Double, String>? {
    val farm = p.type == PropertyType.FARMLAND || p.type == PropertyType.AGRI_LAND
    if (farm) {
        val acres = p.sqft / 43_560.0
        return if (p.askingPrice > 0 && acres > 0) (p.askingPrice / acres) to "/ac" else null
    }
    return p.pricePerSqft()?.let { it to "" }
}

/** Green (cheap for the area) to red (dear) over the visible properties' ₹/sq ft. */
private fun priceArgb(v: Double?, lo: Double, hi: Double): Long {
    if (v == null) return 0xFF8A8F98
    val t = if (hi <= lo) 0.5 else ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)
    return when {
        t < 0.25 -> 0xFF2E7D4F
        t < 0.5 -> 0xFF7FA63A
        t < 0.75 -> 0xFFD08A1E
        else -> 0xFFB0352B
    }
}

private fun waterArgb(r: LandRating?): Long = when (r) {
    LandRating.GOOD -> 0xFF2E7D4F
    LandRating.FAIR -> 0xFFD08A1E
    LandRating.POOR -> 0xFFB0352B
    else -> 0xFF8A8F98
}

/**
 * 2.4.0 Map tab in the style the user liked: saved properties as ₹/sq ft tags, clusters with counts when zoomed out,
 * a "Color by" toggle with its legend, quick filter chips and search, and a "N in view · Best first" sheet of peek cards.
 * Only the user's own data and OpenStreetMap are used.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(ctx: AppContext) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val checks = remember(data) { checkAll(data) }
    var selected by remember { mutableStateOf<String?>(null) }
    var showComps by remember { mutableStateOf(false) }
    var groupFilter by remember { mutableStateOf<Int?>(null) }
    var budget by remember { mutableStateOf<Int?>(null) }
    var colorBy by remember { mutableStateOf(ColorBy.SCORE) }
    var query by remember { mutableStateOf("") }
    var viewport by remember { mutableStateOf<MapViewport?>(null) }
    var fly by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
    var sheetOpen by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val mapped = data.properties.filter { isValidCoord(it.lat, it.lng) }
    val q = query.trim().lowercase()
    val shown = mapped.filter { p ->
        (groupFilter == null || p.type.group() == groupFilter) &&
            (budget == null || budgets[budget!!].let { (lo, hi) -> p.askingPrice in lo until hi }) &&
            (q.isBlank() || listOf(p.title, p.locality, p.taluk, p.district, p.land.village, p.land.taluk, p.land.district).any { it.lowercase().contains(q) })
    }
    val noPin = data.properties.size - mapped.size
    val scores = remember(data) { mapped.associate { it.id to scoreFor(ctx, it, checks[it.id]).total } }
    val water = remember(data) {
        mapped.associate { p ->
            p.id to landCheckFor(data, p.id)?.takeIf { it.key == landCheckKey(p) && it.hasAnyData() }
                ?.let { assessLand(it, p, data.settings.geminiApiKey.isNotBlank()).tile(LandTile.RISK).rating }
        }
    }
    val rates = shown.mapNotNull { p -> tagRate(p)?.takeIf { it.second.isEmpty() }?.first }
    val lo = rates.minOrNull() ?: 0.0
    val hi = rates.maxOrNull() ?: 0.0
    fun colorOf(p: Property): Long = when (colorBy) {
        ColorBy.VERDICT -> verdictArgb(checks[p.id]?.verdict ?: Verdict.UNKNOWN)
        ColorBy.PRICE -> priceArgb(tagRate(p)?.takeIf { it.second.isEmpty() }?.first, lo, hi)
        ColorBy.WATER -> waterArgb(water[p.id])
        ColorBy.SCORE -> scoreArgb(scores[p.id])
    }
    val items = buildList {
        shown.forEach { p ->
            val tr = tagRate(p)
            add(MapItem(p.id, p.lat!!, p.lng!!, tr?.let { shortRupees(it.first) + it.second } ?: (p.title.take(10)), colorOf(p),
                rank = (scores[p.id] ?: -1).toDouble()))
        }
        if (showComps) {
            // Comparables: the rates the user entered (and any still-cached older lookups), as small grey tags.
            data.onlineRates.filter { (it.lookupKey == USER_RATE_KEY || isFresh(it.fetchedAt)) && (groupFilter == null || it.group == groupFilter) }.forEach { r ->
                val la = r.lat; val ln = r.lng
                val v = r.typical ?: r.min ?: r.max
                if (la != null && ln != null && isValidCoord(la, ln) && v != null) {
                    val perSqft = com.hark.sothudiary.domain.pricing.convertPastedRate(v, r.unit, "sqft")
                    add(MapItem("loc:" + r.id, la, ln, shortRupees(perSqft), 0xFF78909C, rank = -2.0, small = true))
                }
            }
        }
    }
    val vp = viewport
    val pins = clusterItems(items, vp?.zoom ?: 11.0).map { if (it.id == selected) it.copy(selected = true) else it }
    val inView = shown.filter { p -> vp == null || vp.contains(p.lat!!, p.lng!!) }
        .sortedWith(compareByDescending<Property> { scores[it.id] ?: -1 }.thenBy { it.title })

    Scaffold(
        topBar = { AppTopBar(S.tabMap.s(), ctx.toggleLang, subtitle = S.mapSubtitle.f(mapped.size, noPin)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            GeoMap(
                modifier = Modifier.fillMaxSize(),
                pins = pins,
                centerLat = DEFAULT_MAP_CENTER.lat, centerLng = DEFAULT_MAP_CENTER.lng, zoom = if (shown.isEmpty()) 6.5 else 11.5,
                cameraKey = shown.map { it.id }.sorted() + groupFilter + budget,
                fitPins = shown.isNotEmpty(),
                onPinClick = { id ->
                    if (id.startsWith(CLUSTER_PREFIX)) {
                        val members = items.filter { it.id in id.removePrefix(CLUSTER_PREFIX).split(',') }
                        if (members.isNotEmpty()) fly = Triple(members.sumOf { it.lat } / members.size, members.sumOf { it.lng } / members.size,
                            ((vp?.zoom ?: 11.0) + 2.0).coerceAtMost(17.0))
                    } else {
                        selected = id
                        sheetOpen = true
                        val i = inView.indexOfFirst { it.id == id }
                        if (i >= 0) scope.launch { listState.animateScrollToItem(i) }
                    }
                },
                onTap = { _, _ -> selected = null },
                onViewport = { viewport = it },
                flyTo = fly,
            )
            // ---- Top: search, filter chips, colour toggle + legend ----
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(S4.searchMapped.f(mapped.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BasicTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
                        }
                        if (query.isNotEmpty()) Icon(Icons.Filled.Close, contentDescription = S.cancel.s(), modifier = Modifier.clickable { query = "" })
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapChip(groupFilter == null, S.allTypes.s()) { groupFilter = null }
                    (0..3).forEach { g -> MapChip(groupFilter == g, groupTr(g).s()) { groupFilter = if (groupFilter == g) null else g } }
                    MapChip(budget != null, "₹ " + (budget?.let { budgetLabel(budgets[it], lang) } ?: S4.budget.s())) {
                        budget = when (val b = budget) { null -> 0; budgets.size - 1 -> null; else -> b + 1 }
                    }
                    MapChip(showComps, S4.comparables.s()) { showComps = !showComps }
                }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 2.dp) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(S4.colorBy.s(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ColorBy.entries.forEach { c ->
                                val label = when (c) { ColorBy.VERDICT -> S4.byVerdict; ColorBy.PRICE -> S4.byPrice; ColorBy.WATER -> S4.byWater; ColorBy.SCORE -> S4.byScore }.s()
                                val on = c == colorBy
                                Text(label, modifier = Modifier.clip(RoundedCornerShape(50))
                                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { colorBy = c }.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                            legendFor(colorBy, lo, hi, lang).forEach { (c, t) -> LegendDot(Color(c.toInt()), t) }
                        }
                    }
                }
            }
            Text(S4.osmCredit.s(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF444444),
                modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp))

            // ---- Bottom sheet: N in view · Best first, peek cards ----
            if (shown.isNotEmpty()) {
                Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                    shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { sheetOpen = !sheetOpen }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(S4.inView.f(inView.size), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(S4.bestFirst.s() + " ↓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(if (sheetOpen) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, contentDescription = null)
                        }
                        if (sheetOpen) {
                            if (inView.isEmpty()) Text(S4.zoomToSee.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp))
                            LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)) {
                                itemsIndexed(inView, key = { _, p -> p.id }) { _, p ->
                                    PeekCard(ctx, p, scores[p.id], checks[p.id]?.verdict ?: Verdict.UNKNOWN, water[p.id], p.id == selected)
                                }
                            }
                        }
                    }
                }
            }
            if (mapped.isEmpty() && !showComps) {
                Surface(Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 4.dp) {
                    Text(S.mapEmpty.s(), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun legendFor(c: ColorBy, lo: Double, hi: Double, lang: com.hark.sothudiary.data.Lang): List<Pair<Long, String>> = when (c) {
    ColorBy.SCORE -> listOf(0xFF2E7D4F to "85+", 0xFF7FA63A to "70", 0xFFD08A1E to "55", 0xFFB0352B to "<55", 0xFF8A8F98 to S4.legendNoData.of(lang))
    ColorBy.WATER -> listOf(0xFF2E7D4F to S4.riskLow.of(lang), 0xFFD08A1E to S4.riskMedium.of(lang), 0xFFB0352B to S4.riskHigh.of(lang), 0xFF8A8F98 to S4.legendNoData.of(lang))
    ColorBy.PRICE -> if (hi <= lo) listOf(0xFF7FA63A to shortRupees(lo) + "/sqft") else listOf(
        0xFF2E7D4F to shortRupees(lo), 0xFF7FA63A to shortRupees(lo + (hi - lo) * 0.25), 0xFFD08A1E to shortRupees(lo + (hi - lo) * 0.5),
        0xFFB0352B to shortRupees(lo + (hi - lo) * 0.75) + "–" + shortRupees(hi))
    ColorBy.VERDICT -> listOf(Verdict.GOOD, Verdict.FAIR, Verdict.HIGH, Verdict.TOO_HIGH, Verdict.SUSPICIOUS_LOW, Verdict.UNKNOWN)
        .map { verdictArgb(it) to it.shortTr().of(lang) }
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PeekCard(ctx: AppContext, p: Property, score: Int?, verdict: Verdict, water: LandRating?, selected: Boolean) {
    val lang = LocalLang.current
    Card(
        onClick = { ctx.nav.select(Route.PropertyDetail(p.id)) },
        modifier = Modifier.width(300.dp).then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PropertyImage(propertyImageModel(ctx.platform, p), p.type, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.locationLine().ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(listOfNotNull(if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else null, tagRate(p)?.let { shortRupees(it.first) + (it.second.ifBlank { "/sqft" }) })
                    .joinToString(" · "), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(verdict.shortTr().s(), style = MaterialTheme.typography.labelSmall, color = verdictColor(verdict))
                    if (water != null && water != LandRating.UNKNOWN) Text(S4.waterRisk.f(when (water) {
                        LandRating.GOOD -> S4.riskLow; LandRating.FAIR -> S4.riskMedium; else -> S4.riskHigh }.s()), style = MaterialTheme.typography.labelSmall,
                        color = Color(waterArgb(water).toInt()))
                }
            }
            ScoreBadge(score, 40)
        }
    }
}

@Composable
private fun MapChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
