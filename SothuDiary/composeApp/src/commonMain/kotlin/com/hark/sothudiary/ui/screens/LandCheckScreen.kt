package com.hark.sothudiary.ui.screens

import com.hark.sothudiary.domain.ai.llm
import com.hark.sothudiary.domain.ai.hasAiKey
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
import com.hark.sothudiary.domain.land.PlotEdit
import com.hark.sothudiary.domain.land.moveSketch
import com.hark.sothudiary.domain.land.sketchCorners
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
import com.hark.sothudiary.domain.land.hasAnyData
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
/** 2.3.0: TNGIS "Tamil Nilam" GI viewer – official map with survey numbers (Goto by latitude / longitude). */
const val TNGIS_VIEWER_URL = "https://tngis.tn.gov.in/apps/village_dashboard/"

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
    LandTile.RAILWAY -> S.tRailway
    LandTile.ECO -> S.tEco
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
    LandTile.RAILWAY -> when (t.rating) { LandRating.GOOD -> S.rtClear; LandRating.FAIR -> S.rtNear; LandRating.POOR -> S.rtVeryClose; LandRating.UNKNOWN -> S.rtUnknown }
    LandTile.ECO -> when (t.rating) { LandRating.GOOD -> S.rtClear; LandRating.FAIR -> S.rtCheckEsz; LandRating.POOR -> S.rtRestricted; LandRating.UNKNOWN -> S.rtUnknown }
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
        LandReason.GROWTH_NO_KEY -> com.hark.sothudiary.i18n.S6.lrGrowthNoKey.of(lang)
        LandReason.GROWTH_UNKNOWN -> S.lrGrowthUnknown.of(lang)
        LandReason.GROWTH_NOT_RUN -> com.hark.sothudiary.i18n.S6.lrGrowthNotRun.of(lang)
        LandReason.NO_MAP_DATA -> S.lrNoMapData.of(lang)
        LandReason.RAIL_NOC -> S.lrRailNoc.format(lang, arg(0))
        LandReason.RAIL_NEAR -> S.lrRailNear.format(lang, arg(0))
        LandReason.RAIL_CLEAR -> S.lrRailClear.format(lang, arg(0))
        LandReason.NO_RAIL -> S.lrNoRail.of(lang)
        LandReason.INSIDE_PROTECTED -> S.lrInsideProtected.format(lang, arg(0))
        LandReason.ESZ_LIKELY -> S.lrEszLikely.format(lang, arg(0), arg(1))
        LandReason.ESZ_POSSIBLE -> S.lrEszPossible.format(lang, arg(0), arg(1))
        LandReason.NEAR_FOREST -> S.lrNearForest.format(lang, arg(0), arg(1))
        LandReason.NO_PROTECTED -> S.lrNoProtected.format(lang, arg(0))
        LandReason.NOT_CHECKED -> S.lrNotChecked.of(lang)
        LandReason.LAYER_FAILED -> S.lrLayerFailed.of(lang)
        LandReason.LULC_WATER -> S.lrLulcWater.format(lang, arg(0))
    }
}

fun noteText(code: String, args: List<String>, lang: Lang): String = when (code) {
    "no_access" -> S.noteNoAccess.of(lang)
    "proposed" -> S.noteProposed.format(lang, args.getOrNull(0).orEmpty(), args.getOrNull(1).orEmpty())
    "rail" -> S.noteRail.format(lang, args.getOrNull(0).orEmpty(), args.getOrNull(1).orEmpty())
    "low_lying" -> S.noteLowLying.format(lang, args.getOrNull(0).orEmpty())
    "crossing" -> S.noteCrossing.format(lang, args.getOrNull(0).orEmpty())
    "station" -> S.noteStation.format(lang, args.getOrNull(0).orEmpty(), args.getOrNull(1).orEmpty())
    "lulc_water" -> S.noteLulcWater.format(lang, args.getOrNull(0).orEmpty())
    "lulc_forest" -> S.noteLulcForest.format(lang, args.getOrNull(0).orEmpty())
    else -> ""
}

/** "2.4 acres" for ≥ 1 acre, else "45 cents". */
fun areaText(cents: Double, lang: Lang): String =
    if (cents >= 100) dec(cents / 100, 2) + " " + com.hark.sothudiary.data.AreaUnit.ACRES.tr().of(lang)
    else dec(cents, 1) + " " + com.hark.sothudiary.data.AreaUnit.CENTS.tr().of(lang)

/** The current, matching land check of a property (any age), for the detail screen and Worth it. */
fun currentLandAssessment(ctx: AppContext, p: Property): Pair<LandCheck, LandAssessment>? {
    val c = landCheckFor(ctx.repo.data, p.id)?.takeIf { it.key == landCheckKey(p) && it.hasAnyData() } ?: return null
    return c to assessLand(c, p, ctx.repo.data.settings.hasAiKey)
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
            a.allTiles.forEach { t -> Pill(t.tile.title().s() + ": " + ratingLabel(t).s(), ratingColor(t.rating)) }
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
        LandReason.LULC_WATER -> lines += S.bannerLulcWater.format(lang, r.args.getOrNull(0).orEmpty())
        LandReason.LAYER_FAILED -> lines += S.bannerLayerFailed.of(lang)
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
private const val RAIL_COLOR = 0xFF6D4C41
private const val ECO_COLOR = 0xFF2E7D32

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
        // 2.3.0: railway line / level crossing / protected area or forest, marked at their point nearest to the plot.
        val langNow = LocalLang.current
        val extraPins = c?.features.orEmpty()
            .filter { it.category in setOf(Cat.RAIL_LINE, Cat.LEVEL_CROSSING, Cat.PROTECTED, Cat.FOREST) && it.lat != null && it.lng != null && it.distanceM > 0.0 }
            .groupBy { it.category }.mapNotNull { (_, l) -> l.minByOrNull { it.distanceM } }
            .map { f -> MapPin("f-${f.category}", f.lat!!, f.lng!!, categoryLabel(f.category).of(langNow) + (if (f.name.isNotBlank()) " – " + f.name else "") + " · " + distText(f.distanceM),
                color = if (f.category == Cat.RAIL_LINE || f.category == Cat.LEVEL_CROSSING) RAIL_COLOR else ECO_COLOR, small = true) }
        GeoMap(
            modifier = Modifier.fillMaxSize(),
            pins = listOf(MapPin(p.id, lat, lng, p.title, color = if (bad) PLOT_STROKE else PLOT_STROKE_OK, small = plot.size >= 3)) + extraPins,
            centerLat = cam?.lat ?: lat, centerLng = cam?.lng ?: lng, zoom = cam?.zoom ?: 16.0,
            cameraKey = "land-${landCheckKey(p)}-${c?.fetchedAt}",
            shapes = shapes,
        )
    }
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(Color(if (bad) PLOT_STROKE else PLOT_STROKE_OK), S.legendPlot.s())
        if (!c?.waterOutlines.isNullOrEmpty()) Legend(Color(WATER_STROKE), S.legendWater.s())
        if (c?.features.orEmpty().any { (it.category == Cat.RAIL_LINE || it.category == Cat.LEVEL_CROSSING) && it.lat != null && it.distanceM > 0 }) Legend(Color(RAIL_COLOR), S.legendRail.s())
        if (c?.features.orEmpty().any { (it.category == Cat.PROTECTED || it.category == Cat.FOREST) && it.lat != null && it.distanceM > 0 }) Legend(Color(ECO_COLOR), S.legendProtected.s())
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
    // 2.4.0: the check runs in the app scope (CheckRunner) – leaving this screen no longer cancels it.
    val running = com.hark.sothudiary.domain.CheckRunner.isRunning("land", p.id)
    var drawing by remember { mutableStateOf(false) }
    var askKey by remember { mutableStateOf(false) }
    val hasKey = data.settings.hasAiKey
    val saved = landCheckFor(data, p.id)
    val current = saved?.takeIf { it.key == landCheckKey(p) }
    val assessment = current?.takeIf { it.hasAnyData() }?.let { assessLand(it, p, hasKey) }
    val typeText = p.type.tr().of(Lang.EN)

    fun run() {
        if (running || p.lat == null || p.lng == null) return
        com.hark.sothudiary.domain.CheckRunner.land(ctx.repo, p, typeText)
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
                Text(com.hark.sothudiary.i18n.S4.osmCredit.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (current != null && current.osmOk && (current.osmFailedLayers.isNotEmpty() || current.osmKeptLayers.isNotEmpty())) {
                // 2.3.0: partial map data – say which layers are missing (or kept from the earlier check).
                val langLayers = LocalLang.current
                Column(Modifier.fillMaxWidth().background(LocalBrand.current.warnText.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                    if (current.osmFailedLayers.isNotEmpty())
                        Text(S.landPartial.f(current.osmFailedLayers.joinToString(", ") { layerName(it).of(langLayers) }), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
                    if (current.osmKeptLayers.isNotEmpty())
                        Text(S.landKept.f(current.osmKeptLayers.joinToString(", ") { layerName(it).of(langLayers) }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (osmErr != null && current?.osmOk != true) {
                LookupErrorNote(osmErr, 0L, otherText = S.landOsmBusy)
            }

            if (assessment != null && current != null) {
                BannerBox(assessment, current, compact = false)
                TileGrid(assessment.allTiles, ctx.wide)
                Text(S.landCheckedOn.f(formatDateShort(current.fetchedAt, lang)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // ---- AI part (2.6.0: always shown; the online search runs when the user taps it, with the chosen provider) ----
                SectionCard(title = S.zoningTitle.s(), trailing = { Pill(S.aiEstimateTag.s(), Color(0xFF6F5E9A)) }) {
                    val ai = current.ai
                    val err = current.aiError
                    if (!hasKey && ai == null) {
                        Text(com.hark.sothudiary.i18n.S6.landAiNeedKey.s(), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { askKey = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(com.hark.sothudiary.i18n.S6.addAiKey.s()) }
                    }
                    if (hasKey) {
                        val aiRunning = com.hark.sothudiary.domain.CheckRunner.isRunning("landai", p.id)
                        val provider = data.settings.llm().provider.name
                        Text(com.hark.sothudiary.i18n.S6.aiOnTapNote.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (current.aiFetchedAt > 0) Text(com.hark.sothudiary.i18n.S6.aiLastRun.f(formatDateShort(current.aiFetchedAt, lang)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.hark.sothudiary.ui.components.FindOnlineButton(
                            onClick = { com.hark.sothudiary.domain.CheckRunner.landAi(ctx.repo, p, typeText, current) },
                            busy = aiRunning, prominent = ai == null,
                            label = com.hark.sothudiary.i18n.S6.aiSearchLand.f(provider),
                        )
                        if (aiRunning) Text(com.hark.sothudiary.i18n.S6.aiSearching.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
                    } else if (hasKey && err == null && !running && current.aiFetchedAt > 0) {
                        Text(S.zoningNone.s(), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (current.aiViaSearch && ai != null) Pill(S.viaSearchTag.s(), MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    if (err != null && hasKey) LookupErrorNote(err, current.aiRetryAt, Modifier.padding(top = 6.dp), otherText = S.landAiOther)
                    if (current.webSnippets.isNotEmpty()) WebSnippetList(current.webSnippets, onOpen = { ctx.platform.openUrl(it) })
                }

                // ---- 2.3.0: ISRO Bhuvan land use ----
                if (current.lulc.isNotEmpty()) {
                    SectionCard(title = S.lulcTitle.s()) {
                        current.lulc.forEachIndexed { i, h ->
                            Text(if (i == 0) S.lulcAtPin.f(com.hark.sothudiary.domain.land.lulcLabel(h)) else S.lulcAtCorner.f(i, com.hark.sothudiary.domain.land.lulcLabel(h)),
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
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
                    if (current.layersVersion >= com.hark.sothudiary.domain.land.LandRules.LAYERS_VERSION)
                        Text("• " + S.srcRail.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    if (current.lulc.isNotEmpty()) Text("• " + S.srcBhuvan.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    val urls = landSources(current)
                    if (urls.isNotEmpty()) {
                        Text("• " + (if (current.aiViaSearch) com.hark.sothudiary.i18n.S6.srcWebSearch else com.hark.sothudiary.i18n.S6.srcAi).s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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

            // ---- 2.4.0: the shared land details (survey number, patta … – one record for the whole app) ----
            LandDetailsSection(ctx, p)

            // ---- 2.4.0: Status / நிலை – every source, ✓ / ✗ with the reason ----
            StatusPanel(current?.status.orEmpty(), runningSince = com.hark.sothudiary.domain.CheckRunner.running["land:" + p.id])

            Button(onClick = { run() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (assessment == null) S.runLandCheck.s() else S.refreshLandCheck.s())
            }
            Text(S.landDisclaimer.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 2.7.1: ODbL attribution for the OpenStreetMap data (Overpass and OpenFreeMap tiles).
            Text(com.hark.sothudiary.i18n.S71.mapCredit.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onSaveSketch = { sk -> ctx.repo.setSketch(p.id, sk) },
            ctx = ctx,
        )
    }
    if (askKey) {
        GeminiKeyDialog(ctx, onDismiss = { askKey = false })
    }
}

/** 2.3.0: a map data layer's name for the "did not load" note. */
fun layerName(layer: String): Tr = when (layer) {
    com.hark.sothudiary.domain.land.OsmLayer.WATER -> S.layerWater
    com.hark.sothudiary.domain.land.OsmLayer.ROADS -> S.layerRoads
    com.hark.sothudiary.domain.land.OsmLayer.AMENITIES -> S.layerAmenities
    com.hark.sothudiary.domain.land.OsmLayer.UTILITIES -> S.layerUtilities
    com.hark.sothudiary.domain.land.OsmLayer.RAIL -> S.layerRail
    com.hark.sothudiary.domain.land.OsmLayer.PROTECTED -> S.layerProtected
    com.hark.sothudiary.domain.land.OsmLayer.LANDUSE -> S.layerLanduse
    else -> Tr(layer, layer)
}

/** 2.3.0: web search results shown as they are (title, snippet, page) when the AI could not read them. */
@Composable
fun WebSnippetList(hits: List<com.hark.sothudiary.data.WebSnippet>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(S.webResultsTitle.s(), style = MaterialTheme.typography.labelLarge)
        hits.take(8).forEach { h ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(h.url) }.padding(vertical = 5.dp)) {
                Text(h.title.ifBlank { h.url }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (h.snippet.isNotBlank()) Text(h.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(h.url.substringAfter("://").substringBefore('/'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 2.3.0 survey number card: reverse-geocoded place, the official TNGIS map, copy coordinates, save what the user finds. */
@Composable
private fun SurveyCard(ctx: AppContext, p: Property) {
    val lat = p.lat ?: return
    val lng = p.lng ?: return
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val lang = LocalLang.current
    var survey by remember(p.id, p.surveyNumber) { mutableStateOf(p.surveyNumber) }
    val coords = "${dec(lat, 6)}, ${dec(lng, 6)}"
    val place = listOf(p.locality, p.taluk, p.district).filter { it.isNotBlank() }.joinToString(" · ")
    SectionCard(title = S.surveyTitle.s()) {
        if (p.surveyNumber.isNotBlank()) Text(S.surveySavedAs.f(p.surveyNumber), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        Text(S.surveyBody.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (place.isNotBlank()) Text(S.surveyPlace.f(place), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(coords, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(coords)); ctx.platform.toast(S.copied.of(lang)) }) { Text(S.copyCoords.s()) }
        }
        OutlinedButton(onClick = { ctx.platform.openUrl(TNGIS_VIEWER_URL) }, modifier = Modifier.fillMaxWidth()) { Text(S.openTngis.s()) }
        OutlinedButton(onClick = { ctx.platform.openUrl(TN_ESERVICES_URL) }, modifier = Modifier.fillMaxWidth()) { Text(S.openFmb.s()) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(survey, { survey = it.take(40) }, label = { Text(S.surveyField.s()) }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Button(onClick = { ctx.repo.setSurveyNumber(p.id, survey) }, enabled = survey.trim() != p.surveyNumber) { Text(S.save.s()) }
        }
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
    Cat.RAIL_LINE -> S.catRailLine
    Cat.LEVEL_CROSSING -> S.catCrossing
    Cat.PROTECTED -> S.catProtected
    Cat.FOREST -> S.catForest
    else -> Tr(cat, cat)
}

// ---------------------------------------------------------------- drawing the plot corners

/**
 * Full-screen editor of the plot corners. 2.2.0: tap the map to add corners (desktop: type lat / lng).
 * 2.3.0: drag a corner at once to move it (area updates live), tap a corner to select it and Delete it, drag or tap
 * the small midpoint handles to insert a corner, and (Android) lay a downloaded FMB sketch over the map to trace it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoundaryEditor(p: Property, onDismiss: () -> Unit, onSave: (List<GeoPt>) -> Unit, onSaveSketch: (com.hark.sothudiary.data.SketchOverlay?) -> Unit = {}, ctx: AppContext? = null) {
    val lang = LocalLang.current
    var corners by remember { mutableStateOf(p.boundary) }
    var selected by remember { mutableStateOf<Int?>(null) }
    /** Index of the corner a midpoint drag inserted (the rest of that drag moves it). */
    var midDrag by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var sketch by remember { mutableStateOf(p.sketch) }
    var aligning by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(5.0) }
    val lat = p.lat ?: return
    val lng = p.lng ?: return
    fun commitSketch(s: com.hark.sothudiary.data.SketchOverlay?) {
        // Removing the sketch also deletes its image from app storage.
        val old = sketch
        if (s == null && old != null) ctx?.platform?.deleteImage(old.file)
        sketch = s; onSaveSketch(s)
    }
    FullScreenOverlay {
        com.hark.sothudiary.PlatformBackHandler(enabled = true) { if (aligning) aligning = false else onDismiss() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(LocalBrand.current.bar).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s(), tint = LocalBrand.current.onBar) }
                    Text(S.drawCornersTitle.s(), color = LocalBrand.current.onBar, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSave(corners) }, enabled = corners.size >= 3 || corners.isEmpty()) {
                        Text(S.saveCorners.s(), color = LocalBrand.current.onBar, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (platformHasMap) S.drawCornersHint2.s() else S.cornersDesktop.s(), style = MaterialTheme.typography.bodySmall,
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
                        val sel = selected
                        Column {
                            Button(onClick = { if (la != null && ln != null) { corners = corners + GeoPt(la, ln); latText = ""; lngText = "" } },
                                enabled = la != null && ln != null && isValidCoord(la, ln)) { Text(S.addCorner.s()) }
                            // 2.3.0: a selected corner can be moved by typing its new position.
                            if (sel != null && sel in corners.indices) OutlinedButton(onClick = {
                                if (la != null && ln != null) { corners = PlotEdit.move(corners, sel, GeoPt(la, ln)); latText = ""; lngText = "" }
                            }, enabled = la != null && ln != null && isValidCoord(la, ln)) { Text(S.updateCorner.s()) }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val ring = corners.map { it.lat to it.lng }
                    val mids = if (aligning) emptyList() else PlotEdit.midpoints(corners)
                    GeoMap(
                        modifier = Modifier.fillMaxSize(),
                        pins = listOf(MapPin("pin", lat, lng, p.title, color = 0xFF6B7785, small = true)) +
                            mids.mapIndexed { i, m -> MapPin(PlotEdit.midId(i), m.lat, m.lng, "+", color = PLOT_STROKE, draggable = true, handle = true) } +
                            corners.mapIndexed { i, c -> MapPin(PlotEdit.cornerId(i), c.lat, c.lng, "${i + 1}", color = if (selected == i) 0xFF1565C0 else PLOT_STROKE, draggable = !aligning, small = true) },
                        centerLat = lat, centerLng = lng, zoom = 18.0, cameraKey = "boundary-editor",
                        onTap = { la, ln -> if (!aligning) { corners = corners + GeoPt(la, ln); selected = null } },
                        onPinClick = { id ->
                            val ci = PlotEdit.cornerIndex(id)
                            val mi = PlotEdit.midIndex(id)
                            when {
                                ci != null -> selected = if (selected == ci) null else ci
                                mi != null && mi < mids.size -> { val (list, at) = PlotEdit.insertAfter(corners, mi, mids[mi]); corners = list; selected = at }
                            }
                        },
                        onPinDragging = { id, la, ln ->
                            val ci = PlotEdit.cornerIndex(id)
                            val mi = PlotEdit.midIndex(id)
                            if (ci != null) { corners = PlotEdit.move(corners, ci, GeoPt(la, ln)); selected = ci }
                            else if (mi != null) {
                                val prev = midDrag?.takeIf { it.first == id }?.second
                                val (list, at) = PlotEdit.dragMid(corners, mi, GeoPt(la, ln), prev)
                                corners = list; midDrag = id to at; selected = at
                            }
                        },
                        onPinDragged = { id, la, ln ->
                            val ci = PlotEdit.cornerIndex(id)
                            val mi = PlotEdit.midIndex(id)
                            if (ci != null) corners = PlotEdit.move(corners, ci, GeoPt(la, ln))
                            else if (mi != null) {
                                val prev = midDrag?.takeIf { it.first == id }?.second
                                corners = PlotEdit.dragMid(corners, mi, GeoPt(la, ln), prev).first
                            }
                            midDrag = null
                        },
                        shapes = if (ring.size >= 2) listOf(MapShape("draft", ring, PLOT_STROKE, 0x33B0352BL, closed = ring.size >= 3, widthDp = 2.5f)) else emptyList(),
                        sketch = sketch?.let { sk -> ctx?.platform?.imagePath(sk.file)?.let { path ->
                            com.hark.sothudiary.ui.map.MapSketch(path, sketchCorners(sk).map { it.lat to it.lng }, sk.alpha) } },
                    )
                }
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        val sk = sketch
                        if (aligning && sk != null) {
                            // ---- 2.3.0: align the FMB sketch (nudge, size, turn, see-through) ----
                            Text(S.sketchTitle.s(), fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                OutlinedButton(onClick = { commitSketch(moveSketch(sk, 0.0, step)) }) { Text("↑ " + S.north.s()) }
                                OutlinedButton(onClick = { commitSketch(moveSketch(sk, 0.0, -step)) }) { Text("↓ " + S.south.s()) }
                                OutlinedButton(onClick = { commitSketch(moveSketch(sk, -step, 0.0)) }) { Text("← " + S.west.s()) }
                                OutlinedButton(onClick = { commitSketch(moveSketch(sk, step, 0.0)) }) { Text("→ " + S.east.s()) }
                                OutlinedButton(onClick = { step = when (step) { 1.0 -> 5.0; 5.0 -> 20.0; else -> 1.0 } }) { Text(S.sketchStep.f(step.toInt())) }
                                OutlinedButton(onClick = { commitSketch(sk.copy(widthM = (sk.widthM * 1.1).coerceAtMost(3000.0))) }) { Text(S.sketchBigger.s()) }
                                OutlinedButton(onClick = { commitSketch(sk.copy(widthM = (sk.widthM / 1.1).coerceAtLeast(5.0))) }) { Text(S.sketchSmaller.s()) }
                                OutlinedButton(onClick = { commitSketch(sk.copy(rotationDeg = (sk.rotationDeg - 2.0) % 360)) }) { Text("⟲ " + S.sketchTurnLeft.s()) }
                                OutlinedButton(onClick = { commitSketch(sk.copy(rotationDeg = (sk.rotationDeg + 2.0) % 360)) }) { Text("⟳ " + S.sketchTurnRight.s()) }
                                OutlinedButton(onClick = { commitSketch(sk.copy(alpha = if (sk.alpha > 0.75f) 0.35f else sk.alpha + 0.2f)) }) { Text(S.sketchOpacity.s() + " " + (sk.alpha * 100).toInt() + "%") }
                                OutlinedButton(onClick = { commitSketch(sk.copy(lat = lat, lng = lng)) }) { Text(S.sketchCentre.s()) }
                                TextButton(onClick = { commitSketch(null); aligning = false }) { Text(S.sketchRemove.s()) }
                            }
                            Button(onClick = { aligning = false }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(S.sketchDoneAlign.s()) }
                            return@Column
                        }
                        val area = PlotEdit.areaSqm(corners)
                        val stated = p.sqft
                        Text(
                            when {
                                corners.size < 3 -> S.needThreeCorners.s()
                                stated > 0 -> S.drawnArea.f(dec(sqmToCents(area), 1), dec(sqmToCents(area) / 100, 2), areaText(stated / com.hark.sothudiary.domain.SQFT_PER_CENT, lang))
                                else -> S.drawnAreaOnly.f(dec(sqmToCents(area), 1), dec(sqmToCents(area) / 100, 2))
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                        // Corner chips: tap to select (desktop has no map to tap on).
                        if (corners.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            corners.indices.forEach { i ->
                                val on = selected == i
                                Pill((i + 1).toString(), if (on) Color(0xFF1565C0) else Color(PLOT_STROKE), filled = on, modifier = Modifier.clickable { selected = if (on) null else i })
                            }
                        }
                        selected?.takeIf { it in corners.indices }?.let { sel ->
                            Text(S.cornerSelected.f(sel + 1) + " · " + dec(corners[sel].lat, 6) + ", " + dec(corners[sel].lng, 6),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val sel = selected?.takeIf { it in corners.indices }
                            if (sel != null) OutlinedButton(onClick = {
                                corners = PlotEdit.delete(corners, sel); selected = PlotEdit.selectionAfterDelete(sel, sel, corners.size)
                            }) { Text(S.deleteCorner.s()) }
                            else OutlinedButton(onClick = { corners = corners.dropLast(1); selected = null }, enabled = corners.isNotEmpty()) { Text(S.undo.s()) }
                            OutlinedButton(onClick = { corners = emptyList(); selected = null }, enabled = corners.isNotEmpty()) { Text(S.clear.s()) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { onSave(corners) }, enabled = corners.size >= 3 || (corners.isEmpty() && p.boundary.isNotEmpty())) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(S.saveCorners.s())
                            }
                        }
                        // ---- 2.3.0: FMB sketch overlay (Android map) ----
                        if (ctx != null) {
                            if (platformHasMap) {
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (sk == null) OutlinedButton(onClick = {
                                        ctx.platform.pickImage { name ->
                                            if (name != null) {
                                                val aspect = ctx.platform.imageAspect(name) ?: 1.0
                                                val widthM = (kotlin.math.sqrt((p.sqft * 0.092903).coerceAtLeast(400.0)) * 3).coerceIn(40.0, 1500.0)
                                                commitSketch(com.hark.sothudiary.data.SketchOverlay(name, lat, lng, widthM, 0.0, 0.6f, aspect)); aligning = true
                                            }
                                        }
                                    }) { Text(S.sketchPick.s()) }
                                    else {
                                        OutlinedButton(onClick = { aligning = true }) { Text(S.sketchAlign.s()) }
                                        TextButton(onClick = { commitSketch(null) }) { Text(S.sketchRemove.s()) }
                                    }
                                }
                                if (sk == null) Text(S.sketchHint.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else Text(S.sketchDesktop.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
