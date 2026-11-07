package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.GeoPt
import com.hark.sothudiary.data.LandCheck
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.domain.land.Cat
import com.hark.sothudiary.domain.land.LandAssessment
import com.hark.sothudiary.domain.land.LandRating
import com.hark.sothudiary.domain.land.LandReason
import com.hark.sothudiary.domain.land.LandTile
import com.hark.sothudiary.domain.land.TileResult
import com.hark.sothudiary.domain.land.assessLand
import com.hark.sothudiary.domain.land.distText
import com.hark.sothudiary.domain.land.landCheckFor
import com.hark.sothudiary.domain.land.landCheckKey
import com.hark.sothudiary.domain.land.landSources
import com.hark.sothudiary.domain.land.osmZoning
import com.hark.sothudiary.domain.land.pct0
import com.hark.sothudiary.domain.land.plotOutline
import com.hark.sothudiary.domain.land.polygonAreaSqm
import com.hark.sothudiary.domain.land.runLandCheck
import com.hark.sothudiary.domain.land.sqmToCents
import com.hark.sothudiary.domain.land.SQFT_M2
import com.hark.sothudiary.domain.land.freshLandCheck
import com.hark.sothudiary.domain.parseDouble
import com.hark.sothudiary.domain.sqft
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.Tr
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.FullScreenOverlay
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AI_STUDIO_URL
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.components.GeminiKeyDialog
import com.hark.sothudiary.ui.components.LookupErrorNote
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.map.GeoMap
import com.hark.sothudiary.ui.map.MapPin
import com.hark.sothudiary.ui.map.MapShape
import com.hark.sothudiary.ui.map.fitCamera
import com.hark.sothudiary.ui.map.isValidCoord
import com.hark.sothudiary.ui.map.platformHasMap
import com.hark.sothudiary.ui.theme.LocalBrand
import com.hark.sothudiary.ui.theme.readable
import kotlinx.coroutines.launch

const val TN_ESERVICES_URL = "https://eservices.tn.gov.in/eservicesnew/home.html"

fun ratingColor(r: LandRating): Color = when (r) {
    LandRating.GOOD -> Color(0xFF3D7A45)
    LandRating.FAIR -> Color(0xFFB8741A)
    LandRating.POOR -> Color(0xFFB0352B)
    LandRating.UNKNOWN -> Color(0xFF6B7785)
}

fun LandTile.title(): Tr = when (this) {
    LandTile.BOUNDARY -> S.tBoundary
    LandTile.CONNECTIVITY -> S.tConnectivity
    LandTile.RISK -> S.tRisk
    LandTile.LOCALITY -> S.tLocality
    LandTile.UTILITY -> S.tUtility
    LandTile.GROWTH -> S.tGrowth
}

/** The chip text of a tile ("Verified", "Strong", "High" …). For Risk a POOR rating reads "High". */
fun ratingLabel(t: TileResult): Tr = when (t.tile) {
    LandTile.BOUNDARY -> when (t.reason) {
        LandReason.DOCS_VERIFIED -> S.rtVerified
        LandReason.AREA_MISMATCH -> S.rtMismatch
        LandReason.DRAWN_NOT_VERIFIED -> S.rtDrawn
        else -> S.rtNotVerified
    }
    LandTile.RISK -> when (t.rating) { LandRating.GOOD -> S.rtLow; LandRating.FAIR -> S.rtMedium; LandRating.POOR -> S.rtHigh; LandRating.UNKNOWN -> S.rtUnknown }
    LandTile.UTILITY -> when (t.rating) { LandRating.GOOD -> S.rtAvailable; LandRating.FAIR -> S.rtPartial; else -> if (t.reason == LandReason.NO_MAP_DATA) S.rtUnknown else S.rtNotMapped }
    LandTile.GROWTH -> when (t.rating) { LandRating.GOOD -> S.rtHigh; LandRating.FAIR -> S.rtModerate; LandRating.POOR -> S.rtLow; LandRating.UNKNOWN -> S.rtUnknown }
    else -> when (t.rating) { LandRating.GOOD -> S.rtStrong; LandRating.FAIR -> S.rtModerate; LandRating.POOR -> S.rtWeak; LandRating.UNKNOWN -> S.rtUnknown }
}

private fun catText(cat: String, d: String, lang: Lang): String = when (cat) {
    Cat.SCHOOL -> S.catSchool.format(lang, d)
    Cat.HOSPITAL -> S.catHospital.format(lang, d)
    Cat.BUS -> S.catBus.format(lang, d)
    Cat.MARKET -> S.catMarket.format(lang, d)
    else -> "$cat $d"
}

/** The one-line reason of a tile, in the current language. */
fun tileReason(t: TileResult, lang: Lang): String {
    val a = t.args
    fun arg(i: Int) = a.getOrNull(i).orEmpty()
    fun water(i: Int) = arg(i).ifBlank { S.theWaterBody.of(lang) }
    return when (t.reason) {
        LandReason.DOCS_VERIFIED -> S.lrDocsVerified.of(lang)
        LandReason.AREA_MISMATCH -> S.lrAreaMismatch.format(lang, arg(0), arg(1), arg(2))
        LandReason.DRAWN_NOT_VERIFIED -> S.lrDrawnNotVerified.format(lang, arg(0))
        LandReason.NOT_VERIFIED -> S.lrNotVerified.of(lang)
        LandReason.MAJOR_ROAD_NEAR -> S.lrMajorRoadNear.format(lang, arg(0), arg(1), arg(2))
        LandReason.MAJOR_ROAD_FAR -> S.lrMajorRoadFar.format(lang, arg(0), arg(1))
        LandReason.NO_MAJOR_ROAD -> S.lrNoMajorRoad.of(lang)
        LandReason.PLOT_ON_WATER -> S.lrOnWater.format(lang, arg(0), water(1))
        LandReason.IN_BUFFER -> S.lrInBuffer.format(lang, arg(0), water(1), arg(2))
        LandReason.WATER_NEAR -> S.lrWaterNear.format(lang, arg(0), water(1))
        LandReason.LOW_LYING -> S.lrLowLying.format(lang, arg(0))
        LandReason.NO_WATER_NEAR -> S.lrNoWater.format(lang, arg(0))
        LandReason.AMENITIES -> S.lrAmenities.format(lang, a.chunked(2).joinToString(" · ") { catText(it[0], it.getOrElse(1) { "" }, lang) })
        LandReason.NO_AMENITIES -> S.lrNoAmenities.of(lang)
        LandReason.UTILITIES, LandReason.UTILITIES_DOCS -> {
            fun v(x: String) = when (x) { "" -> S.notMapped.of(lang); "doc" -> S.youVerified.of(lang); else -> x }
            S.lrUtilities.format(lang, v(arg(0)), v(arg(1)))
        }
        LandReason.NO_UTILITIES -> S.lrNoUtilities.of(lang)
        LandReason.GROWTH_AI -> arg(0).ifBlank { S.aiEstimateTag.of(lang) }
        LandReason.GROWTH_NO_KEY -> S.lrGrowthNoKey.of(lang)
        LandReason.GROWTH_UNKNOWN -> S.lrGrowthUnknown.of(lang)
        LandReason.NO_MAP_DATA -> S.lrNoMapData.of(lang)
    }
}

fun noteText(code: String, args: List<String>, lang: Lang): String = when (code) {
    "no_access" -> S.noteNoAccess.of(lang)
    "proposed" -> S.noteProposed.format(lang, args.getOrNull(0).orEmpty(), args.getOrNull(1).orEmpty())
    "rail" -> S.noteRail.format(lang, args.getOrNull(0).orEmpty(), args.getOrNull(1).orEmpty())
    "low_lying" -> S.noteLowLying.format(lang, args.getOrNull(0).orEmpty())
    else -> ""
}

/** "2.4 acres" for ≥ 1 acre, else "45 cents". */
fun areaText(cents: Double, lang: Lang): String =
    if (cents >= 100) dec(cents / 100, 2) + " " + com.hark.sothudiary.data.AreaUnit.ACRES.tr().of(lang)
    else dec(cents, 1) + " " + com.hark.sothudiary.data.AreaUnit.CENTS.tr().of(lang)

/** The current, matching land check of a property (any age), for the detail screen and Worth it. */
fun currentLandAssessment(ctx: AppContext, p: Property): Pair<LandCheck, LandAssessment>? {
    val c = landCheckFor(ctx.repo.data, p.id)?.takeIf { it.key == landCheckKey(p) && it.osmOk } ?: return null
    return c to assessLand(c, p, ctx.repo.data.settings.geminiApiKey.isNotBlank())
}

// ---------------------------------------------------------------- detail screen card

/** Compact land-check card on the property detail screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandCheckCard(ctx: AppContext, p: Property) {
    val lang = LocalLang.current
    val saved = landCheckFor(ctx.repo.data, p.id)
    val current = currentLandAssessment(ctx, p)
    SectionCard(title = S.landCheck.s(), icon = Icons.Filled.Layers, accent = current?.second?.banner?.level?.let(::ratingColor)) {
        if (p.lat == null || p.lng == null) {
            Text(S.landNeedPin.s(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { ctx.nav.push(Route.PropertyEdit(p.id)) }) { Text(S.pickOnMap.s()) }
            return@SectionCard
        }
        if (current == null) {
            Text(S.landCheckSub.s(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (saved != null && saved.osmOk) Text(S.landStale.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ctx.nav.push(Route.LandCheck(p.id)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.runLandCheck.s())
            }
            return@SectionCard
        }
        val (c, a) = current
        BannerBox(a, c, compact = true)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            a.tiles.forEach { t -> Pill(t.tile.title().s() + ": " + ratingLabel(t).s(), ratingColor(t.rating)) }
        }
        Text(S.landCheckedOn.f(formatDateShort(c.fetchedAt, lang)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        OutlinedButton(onClick = { ctx.nav.push(Route.LandCheck(p.id)) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(S.openLandCheck.s())
        }
    }
}

// ---------------------------------------------------------------- banner & tiles

@Composable
private fun BannerBox(a: LandAssessment, c: LandCheck, compact: Boolean) {
    val lang = LocalLang.current
    val b = a.banner
    val color = ratingColor(b.level)
    val title = when (b.level) {
        LandRating.POOR -> S.highRisk
        LandRating.FAIR -> S.caution
        LandRating.GOOD -> S.lowRiskBanner
        LandRating.UNKNOWN -> S.riskUnknownBanner
    }
    val water = c.nearestWaterName.ifBlank { S.theWaterBody.of(lang) }
    val lines = mutableListOf<String>()
    val r = b.risk
    when (r.reason) {
        LandReason.PLOT_ON_WATER -> lines += S.bannerOnWater.format(lang, r.args.getOrNull(0).orEmpty(), water)
        LandReason.IN_BUFFER -> {
            lines += S.bannerBuffer.format(lang, pct0(b.bufferPct), water, distText(c.bufferM))
            val paid = b.paidCents; val usable = b.usableCents
            if (paid != null && usable != null) lines += S.bannerPayBuild.format(lang, areaText(paid, lang), areaText(usable, lang))
        }
        LandReason.WATER_NEAR -> lines += S.bannerWaterNear.format(lang, r.args.getOrNull(0).orEmpty(), water)
        LandReason.LOW_LYING -> lines += S.bannerLowLying.format(lang, r.args.getOrNull(0).orEmpty())
        LandReason.NO_WATER_NEAR -> lines += S.bannerClear.format(lang, r.args.getOrNull(0).orEmpty())
        else -> lines += S.bannerNoData.of(lang)
    }
    if (r.reason == LandReason.PLOT_ON_WATER) {
        val paid = b.paidCents; val usable = b.usableCents
        if (paid != null && usable != null && b.bufferPct < 100) lines += S.bannerPayBuild.format(lang, areaText(paid, lang), areaText(usable, lang))
    }
    r.notes.forEach { n -> noteText(n.code, n.args, lang).takeIf { it.isNotBlank() }?.let { lines += it } }
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (b.level == LandRating.POOR || b.level == LandRating.FAIR) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = readable(color), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            }
            Text(title.s(), color = readable(color), fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, style = MaterialTheme.typography.titleSmall)
        }
        lines.forEachIndexed { i, l ->
            if (!compact || i < 2) Text(l, style = if (i == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (i == 1 && r.reason == LandReason.IN_BUFFER) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TileCard(t: TileResult, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    val color = ratingColor(t.rating)
    Column(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, color.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape)); Spacer(Modifier.width(6.dp))
            Text(t.tile.title().s(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(ratingLabel(t).s(), color = readable(color), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(tileReason(t, lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        t.notes.forEach { n ->
            if (t.tile != LandTile.RISK) noteText(n.code, n.args, lang).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (t.ai) Pill(S.aiEstimateTag.s(), Color(0xFF6F5E9A), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TileGrid(tiles: List<TileResult>, wide: Boolean) {
    val perRow = if (wide) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { TileCard(it, Modifier.weight(1f).heightIn(min = 118.dp)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- map

private const val PLOT_STROKE = 0xFFB0352B
private const val PLOT_STROKE_OK = 0xFF1F5F57
private const val WATER_STROKE = 0xFF1E6FB8
private const val WATER_FILL = 0x551E88E5L

@Composable
private fun LandMap(p: Property, c: LandCheck?, plot: List<GeoPt>, level: LandRating?) {
    val lat = p.lat ?: return
    val lng = p.lng ?: return
    if (!isValidCoord(lat, lng)) return
    val bad = level == LandRating.POOR || level == LandRating.FAIR
    val shapes = buildList {
        c?.waterOutlines?.forEachIndexed { i, o ->
            add(MapShape("w$i", o.map { it.lat to it.lng }, WATER_STROKE, WATER_FILL, closed = o.size >= 4 && o.first() == o.last(), widthDp = 2f))
        }
        if (plot.size >= 3) add(MapShape("plot", plot.map { it.lat to it.lng }, if (bad) PLOT_STROKE else PLOT_STROKE_OK,
            if (bad) 0x33B0352BL else 0x331F5F57L, closed = true, widthDp = 2.5f))
    }
    // Fit the plot and the water close to it; never centre anywhere but the pin.
    val near = (plot.map { it.lat to it.lng } + (c?.waterOutlines.orEmpty().flatten().map { it.lat to it.lng }
        .filter { com.hark.sothudiary.domain.haversineKm(lat, lng, it.first, it.second) < 0.6 }))
    val cam = fitCamera(near, lat, lng, halfExtentDp = 105.0, minZoom = 13.0, maxZoom = 18.0)
    Box(Modifier.fillMaxWidth().height(230.dp).clip(MaterialTheme.shapes.medium)) {
        GeoMap(
            modifier = Modifier.fillMaxSize(),
            pins = listOf(MapPin(p.id, lat, lng, p.title, color = if (bad) PLOT_STROKE else PLOT_STROKE_OK, small = plot.size >= 3)),
            centerLat = cam?.lat ?: lat, centerLng = cam?.lng ?: lng, zoom = cam?.zoom ?: 16.0,
            cameraKey = "land-${landCheckKey(p)}-${c?.fetchedAt}",
            shapes = shapes,
        )
    }
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(Color(if (bad) PLOT_STROKE else PLOT_STROKE_OK), S.legendPlot.s())
        if (!c?.waterOutlines.isNullOrEmpty()) Legend(Color(WATER_STROKE), S.legendWater.s())
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c.copy(alpha = 0.5f), RoundedCornerShape(3.dp)).border(1.5.dp, c, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- the screen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LandCheckScreen(ctx: AppContext, propertyId: String) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val p = data.properties.firstOrNull { it.id == propertyId }
    if (p == null) {
        Scaffold(topBar = { AppTopBar(S.landCheck.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }) }) { pad ->
            Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.Layers, S.notFound.s(), "") }
        }
        return
    }
    val scope = rememberCoroutineScope()
    var running by remember(p.id) { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var askKey by remember { mutableStateOf(false) }
    val hasKey = data.settings.geminiApiKey.isNotBlank()
    val saved = landCheckFor(data, p.id)
    val current = saved?.takeIf { it.key == landCheckKey(p) }
    val assessment = current?.takeIf { it.osmOk || it.ai != null }?.let { assessLand(it, p, hasKey) }
    val typeText = p.type.tr().of(Lang.EN)

    fun run() {
        if (running || p.lat == null || p.lng == null) return
        running = true
        scope.launch {
            val r = runCatching {
                runLandCheck(p, data.settings.geminiApiKey, data.settings.geminiModel, typeText, landCheckFor(ctx.repo.data, p.id))
            }.getOrNull()
            if (r != null) ctx.repo.saveLandCheck(r)
            running = false
        }
    }
    // Like the price check: run when there is no recent check for this pin / outline / size.
    LaunchedEffect(p.id, landCheckKey(p)) { if (freshLandCheck(ctx.repo.data, p) == null) run() }

    Scaffold(
        topBar = {
            AppTopBar(S.landCheck.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }, subtitle = p.title, actions = {
                IconButton(onClick = { run() }, enabled = !running && p.lat != null) { Icon(Icons.Filled.Refresh, contentDescription = S.refreshLandCheck.s()) }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (p.lat == null || p.lng == null) {
                SectionCard {
                    Text(S.landNeedPin.s())
                    TextButton(onClick = { ctx.nav.push(Route.PropertyEdit(p.id)) }) { Text(S.pickOnMap.s()) }
                }
                return@Column
            }
            val plot = current?.plot?.takeIf { it.size >= 3 } ?: plotOutline(p)?.first.orEmpty()
            val drawn = plotOutline(p)?.second == true
            val stated = p.sqft * SQFT_M2

            // ---- Map + plot summary ----
            SectionCard {
                if (platformHasMap || plot.isNotEmpty()) LandMap(p, current, plot, assessment?.banner?.level)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val areaSqm = if (stated > 0) stated else if (plot.size >= 3) polygonAreaSqm(plot.map { it.lat to it.lng }) else 0.0
                    val zone = current?.ai?.zoning?.takeIf { it.isNotBlank() && it != "unknown" }?.let { S.zoneChipAi.f(it.replaceFirstChar { ch -> ch.uppercase() }) }
                        ?: current?.let(::osmZoning)?.let { S.zoneChipOsm.f(it.replaceFirstChar { ch -> ch.uppercase() }) }
                    if (areaSqm > 0) Pill(listOfNotNull(areaText(sqmToCents(areaSqm), lang), zone).joinToString(" · "), MaterialTheme.colorScheme.primary, filled = true)
                    else if (zone != null) Pill(zone, MaterialTheme.colorScheme.primary, filled = true)
                    val b = assessment?.banner
                    if (b != null && b.bufferPct > 0 && b.paidCents != null) {
                        Pill(S.inBufferChip.f(areaText(b.paidCents * b.bufferPct / 100.0, lang)), ratingColor(LandRating.POOR), filled = true)
                    }
                }
                Text(
                    when { drawn -> S.drawnAreaOnly.f(dec(sqmToCents(polygonAreaSqm(p.boundary.map { it.lat to it.lng })), 1), dec(sqmToCents(polygonAreaSqm(p.boundary.map { it.lat to it.lng })) / 100, 2))
                        plot.isNotEmpty() -> S.assumedCircle.s(); else -> S.pinOnlyNote.s() },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedButton(onClick = { drawing = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Filled.CropFree, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (p.boundary.size >= 3) S.editCorners.f(p.boundary.size) else S.drawCorners.s())
                }
            }

            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                    Text(S.landChecking.s(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            // OSM error with nothing earlier to show
            val osmErr = current?.osmError
            if (osmErr != null) {
                LookupErrorNote(osmErr, 0L, otherText = S.landOsmBusy)
            }

            if (assessment != null && current != null) {
                BannerBox(assessment, current, compact = false)
                TileGrid(assessment.tiles, ctx.wide)
                Text(S.landCheckedOn.f(formatDateShort(current.fetchedAt, lang)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // ---- AI part ----
                SectionCard(title = S.zoningTitle.s(), trailing = { Pill(S.aiEstimateTag.s(), Color(0xFF6F5E9A)) }) {
                    val ai = current.ai
                    val err = current.aiError
                    if (!hasKey && ai == null) {
                        Text(S.landAiNeedKey.s(), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { askKey = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(S.setUpLiveCheck.s()) }
                    }
                    if (ai != null) {
                        val z = ai.zoning.takeIf { it.isNotBlank() && it != "unknown" }
                        Text(if (z != null) S.zoneChipAi.f(z.replaceFirstChar { it.uppercase() }) else S.zoningNone.s(), fontWeight = FontWeight.SemiBold)
                        listOf(ai.zoningPlan, ai.zoningNote).filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        osmZoning(current)?.let { Text(S.zoneChipOsm.f(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (ai.roads.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(S.announcedRoads.s(), style = MaterialTheme.typography.labelLarge)
                            ai.roads.forEach { r ->
                                Column(Modifier.fillMaxWidth().clickable(enabled = r.url.isNotBlank()) { ctx.platform.openUrl(r.url) }.padding(vertical = 4.dp)) {
                                    Text(r.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(listOfNotNull(r.status.ifBlank { null }, r.distanceKm?.let { S.kmFromPlot.f(dec(it, 1)) }, r.date.ifBlank { null }).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else if (hasKey && err == null && !running) {
                        Text(S.zoningNone.s(), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (err != null && hasKey) LookupErrorNote(err, current.aiRetryAt, Modifier.padding(top = 6.dp), otherText = S.landAiOther)
                }

                // ---- Nearest mapped things ----
                if (current.features.isNotEmpty()) {
                    SectionCard(title = S.nearbyTitle.s()) {
                        current.features.groupBy { it.category }.forEach { (_, list) ->
                            val f = list.minBy { it.distanceM }
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(categoryLabel(f.category).s() + (if (f.name.isNotBlank()) " – " + f.name else if (f.kind.isNotBlank() && f.category in setOf(Cat.ROAD, Cat.POWER, Cat.WATER_SUPPLY)) " (" + f.kind.replace('_', ' ') + ")" else ""),
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(distText(f.distanceM), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // ---- Sources ----
                SectionCard(title = S.sourcesTitle.s()) {
                    Text("• " + S.srcOsm.s(), style = MaterialTheme.typography.bodySmall)
                    if (current.elevationM != null) Text("• " + S.srcElevation.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("• " + S.srcRules.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    val urls = landSources(current)
                    if (urls.isNotEmpty()) {
                        Text("• " + S.srcAi.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        urls.take(10).forEach { u ->
                            Row(Modifier.fillMaxWidth().clickable { ctx.platform.openUrl(u) }.padding(start = 10.dp, top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                                Text(u, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else if (!running && current?.osmError == null) {
                Text(S.landNotRun.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = { run() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (assessment == null) S.runLandCheck.s() else S.refreshLandCheck.s())
            }
            Text(S.landDisclaimer.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { ctx.platform.openUrl(TN_ESERVICES_URL) }) { Text(S.openEservices.s()) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (drawing) {
        BoundaryEditor(
            p = p,
            onDismiss = { drawing = false },
            onSave = { corners -> ctx.repo.setBoundary(p.id, corners); drawing = false },
        )
    }
    if (askKey) {
        GeminiKeyDialog(
            current = data.settings.geminiApiKey, onDismiss = { askKey = false },
            onSave = { k -> ctx.repo.setApiKeys(data.settings.youtubeApiKey, k, data.settings.geminiModel); askKey = false },
            onOpenStudio = { ctx.platform.openUrl(AI_STUDIO_URL) },
        )
    }
}

private fun categoryLabel(cat: String): Tr = when (cat) {
    Cat.WATER -> S.legendWater
    Cat.MAJOR_ROAD -> Tr("Main road", "முக்கியச் சாலை")
    Cat.ROAD -> Tr("Nearest road", "அருகிலுள்ள சாலை")
    Cat.PROPOSED_ROAD -> Tr("Road under construction / proposed", "கட்டுமானத்தில் / திட்டமிட்ட சாலை")
    Cat.RAIL -> Tr("Railway station", "ரயில் நிலையம்")
    Cat.SCHOOL -> Tr("School / college", "பள்ளி / கல்லூரி")
    Cat.HOSPITAL -> Tr("Hospital / clinic", "மருத்துவமனை / மருந்தகம்")
    Cat.BUS -> Tr("Bus stop", "பேருந்து நிறுத்தம்")
    Cat.MARKET -> Tr("Shop / market", "கடை / சந்தை")
    Cat.POWER -> Tr("Power line / transformer", "மின் கம்பி / மின்மாற்றி")
    Cat.WATER_SUPPLY -> Tr("Water tank / supply", "தண்ணீர்த் தொட்டி / விநியோகம்")
    else -> Tr(cat, cat)
}

// ---------------------------------------------------------------- drawing the plot corners

/** Full-screen editor: tap the plot's corners on the map (drag a corner to move it); desktop: type lat / lng. */
@Composable
fun BoundaryEditor(p: Property, onDismiss: () -> Unit, onSave: (List<GeoPt>) -> Unit) {
    val lang = LocalLang.current
    var corners by remember { mutableStateOf(p.boundary) }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    val lat = p.lat ?: return
    val lng = p.lng ?: return
    FullScreenOverlay {
        com.hark.sothudiary.PlatformBackHandler(enabled = true) { onDismiss() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(LocalBrand.current.bar).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s(), tint = LocalBrand.current.onBar) }
                    Text(S.drawCornersTitle.s(), color = LocalBrand.current.onBar, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSave(corners) }, enabled = corners.size >= 3 || corners.isEmpty()) {
                        Text(S.saveCorners.s(), color = LocalBrand.current.onBar, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (platformHasMap) S.drawCornersHint.s() else S.cornersDesktop.s(), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                if (!platformHasMap) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(latText, { latText = it }, label = { Text(S.latitudeShort.s()) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(lngText, { lngText = it }, label = { Text(S.longitudeShort.s()) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        val la = parseDouble(latText); val ln = parseDouble(lngText)
                        Button(onClick = { if (la != null && ln != null) { corners = corners + GeoPt(la, ln); latText = ""; lngText = "" } },
                            enabled = la != null && ln != null && isValidCoord(la, ln)) { Text(S.addCorner.s()) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val ring = corners.map { it.lat to it.lng }
                    GeoMap(
                        modifier = Modifier.fillMaxSize(),
                        pins = listOf(MapPin("pin", lat, lng, p.title, color = 0xFF6B7785, small = true)) +
                            corners.mapIndexed { i, c -> MapPin("c$i", c.lat, c.lng, "${i + 1}", color = PLOT_STROKE, draggable = true, small = true) },
                        centerLat = lat, centerLng = lng, zoom = 18.0, cameraKey = "boundary-editor",
                        onTap = { la, ln -> corners = corners + GeoPt(la, ln) },
                        onPinDragged = { id, la, ln ->
                            val i = id.removePrefix("c").toIntOrNull()
                            if (i != null && i in corners.indices) corners = corners.toMutableList().also { it[i] = GeoPt(la, ln) }
                        },
                        shapes = if (ring.size >= 2) listOf(MapShape("draft", ring, PLOT_STROKE, 0x33B0352BL, closed = ring.size >= 3, widthDp = 2.5f)) else emptyList(),
                    )
                }
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        val area = if (corners.size >= 3) polygonAreaSqm(corners.map { it.lat to it.lng }) else 0.0
                        val stated = p.sqft
                        Text(
                            when {
                                corners.size < 3 -> S.needThreeCorners.s()
                                stated > 0 -> S.drawnArea.f(dec(sqmToCents(area), 1), dec(sqmToCents(area) / 100, 2), areaText(stated / com.hark.sothudiary.domain.SQFT_PER_CENT, lang))
                                else -> S.drawnAreaOnly.f(dec(sqmToCents(area), 1), dec(sqmToCents(area) / 100, 2))
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { corners = corners.dropLast(1) }, enabled = corners.isNotEmpty()) { Text(S.undo.s()) }
                            OutlinedButton(onClick = { corners = emptyList() }, enabled = corners.isNotEmpty()) { Text(S.clear.s()) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { onSave(corners) }, enabled = corners.size >= 3 || (corners.isEmpty() && p.boundary.isNotEmpty())) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(S.saveCorners.s())
                            }
                        }
                    }
                }
            }
        }
    }
}
