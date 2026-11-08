package com.hark.sothudiary.domain.land

import com.hark.sothudiary.data.AiRoad
import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.GeoPt
import com.hark.sothudiary.data.LandAi
import com.hark.sothudiary.data.LandCheck
import com.hark.sothudiary.data.LandFeature
import com.hark.sothudiary.data.LulcHit
import com.hark.sothudiary.data.WebSnippet
import com.hark.sothudiary.data.SourceStatus
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.SQFT_PER_CENT
import com.hark.sothudiary.domain.haversineKm
import com.hark.sothudiary.domain.pricing.Grounded
import com.hark.sothudiary.domain.pricing.describePlace
import com.hark.sothudiary.domain.pricing.groundedGenerate
import com.hark.sothudiary.domain.social.arr
import com.hark.sothudiary.domain.social.balancedJson
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.str
import com.hark.sothudiary.domain.sqft
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Thresholds of the land check. Distances in metres. */
object LandRules {
    /**
     * TN Combined Development and Building Rules 2019, Rule 27(1): building within 15 m of a tank, reservoir,
     * water-course, river or fresh-water channel needs the competent authority's conditions / NOC; building on the
     * water body (poramboke) itself is not allowed.
     */
    const val WATER_BUFFER_M = 15.0
    /** Water closer than this (but outside the buffer) is a caution: flooding / waterlogging in heavy rain. */
    const val WATER_CAUTION_M = 100.0
    /** Share of the plot in the buffer that makes the risk HIGH. Any overlap at all is at least MEDIUM. */
    const val HIGH_RISK_PCT = 10.0
    /** The plot is this much lower than the land around it (m) -> low-lying. */
    const val LOW_LYING_M = 3.0
    const val MAJOR_ROAD_STRONG_M = 1000.0
    const val MAJOR_ROAD_MODERATE_M = 3000.0
    const val ACCESS_ROAD_M = 300.0
    const val PROPOSED_ROAD_BONUS_M = 2000.0
    const val LOCALITY_RADIUS_M = 3000.0
    const val SCHOOL_NEAR_M = 2000.0
    const val HOSPITAL_NEAR_M = 3000.0
    const val POWER_NEAR_M = 500.0
    const val WATER_SUPPLY_NEAR_M = 1000.0
    /** Drawn area vs the stated size: more than this difference is flagged. */
    const val AREA_MISMATCH_PCT = 15.0
    /** A drawn boundary whose centre is farther than this from the pin is ignored (the pin was moved). */
    const val BOUNDARY_MAX_OFFSET_M = 500.0
    /** Results are reused for this many days (like the price check). */
    const val CACHE_DAYS = 30
    /** Search radius for water around the plot. */
    const val WATER_RADIUS_M = 1000
    // ---- 2.3.0 ----
    /**
     * Indian Railways Works Manual para 827: an open space of about 30 m between railway land and a new building; the
     * track centre line is measured here (railway land is wider than the track), so 50 m from the track is "near".
     */
    const val RAIL_NOC_M = 50.0
    /** A railway line closer than this: noise / vibration caution. */
    const val RAIL_NEAR_M = 200.0
    /**
     * Eco-sensitive zone: at least 1 km around a national park / wildlife sanctuary where no ESZ is notified
     * (Supreme Court, In re T.N. Godavarman, 3 June 2022, modified 26 April 2023), and up to 10 km as a general principle.
     */
    const val ESZ_MIN_M = 1000.0
    const val ESZ_MAX_M = 10_000.0
    /** Forest land this close to the plot: check the boundary with the forest department. */
    const val FOREST_NEAR_M = 100.0
    /** Version of the map layers a 2.3.0 check fetches ([com.hark.sothudiary.data.LandCheck.layersVersion]). */
    const val LAYERS_VERSION = 2
    /** Overall time for the map layers, so the screen never waits for minutes. */
    const val OSM_BUDGET_MS = 45_000L
    /** Overall time for the AI part (grounded search, then the free web search fallback). */
    const val AI_BUDGET_MS = 60_000L
    /** 2.4.0: per-source limits for the quick sources (elevation, ISRO Bhuvan) – one slow source never blocks the rest. */
    const val QUICK_SOURCE_MS = 15_000L
    /**
     * 2.4.0 (user decision, 2026-11-08): only sources whose licences openly allow automatic use are loaded by the app –
     * OpenStreetMap (Overpass, Nominatim; ODbL with attribution) and Open-Meteo. ISRO Bhuvan's terms need NRSC's
     * written permission for automatic use, and the AI web search reads third-party pages, so neither runs on its own.
     * The 2.3.0 code for both is kept unchanged behind these switches.
     */
    const val AUTO_BHUVAN = false
    const val AUTO_AI_SEARCH = false
    /**
     * 2.6.0: the AI web search is no longer only switched off – it runs when the user taps "Search online with AI"
     * on the Land check screen ([runLandAi], with the AI provider chosen at setup, issue #1). Nothing third-party is
     * read automatically (the 2.4.0 decision above still holds); a tap is the user asking for it.
     */
    const val AI_ON_TAP = true
    /** Name of the AI line in [com.hark.sothudiary.data.LandCheck.status] (replaced when the AI part runs again). */
    const val AI_STATUS_PREFIX = "AI web search"
}

/** Half-width of a river / canal / stream mapped only as a centre line (OSM has no width). */
fun lineHalfWidth(kind: String): Double = when (kind) {
    "river" -> 10.0
    "canal" -> 5.0
    else -> 1.5
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Rounded pin + drawn corners + stated size: the land check is redone when any of these change. */
fun landCheckKey(p: Property): String {
    fun r(v: Double) = (v * 100_000).roundToLong()
    val corners = p.boundary.joinToString(";") { "${r(it.lat)},${r(it.lng)}" }
    return "${p.lat?.let(::r)},${p.lng?.let(::r)}|${(p.sqft).roundToLong()}|$corners"
}

fun landCheckFor(data: AppData, propertyId: String): LandCheck? = data.landChecks.firstOrNull { it.propertyId == propertyId }

/** The saved check, if it is for the property's current pin / boundary / size and younger than [LandRules.CACHE_DAYS]. */
fun freshLandCheck(data: AppData, p: Property, now: Long = nowMillis()): LandCheck? =
    landCheckFor(data, p.id)?.takeIf {
        it.key == landCheckKey(p) && it.osmOk && now - it.fetchedAt < LandRules.CACHE_DAYS * DAY_MS &&
            // 2.3.0: a check with layers that did not load is tried again (at most every 10 minutes).
            (it.osmFailedLayers.isEmpty() || now - it.fetchedAt < 10 * 60 * 1000L)
    }

/** The plot outline to check: the drawn corners (if near the pin), else a circle of the stated area, else null. */
fun plotOutline(p: Property): Pair<List<GeoPt>, Boolean>? {
    val lat = p.lat ?: return null
    val lng = p.lng ?: return null
    if (p.boundary.size >= 3) {
        val cLat = p.boundary.sumOf { it.lat } / p.boundary.size
        val cLng = p.boundary.sumOf { it.lng } / p.boundary.size
        if (haversineKm(lat, lng, cLat, cLng) * 1000 <= LandRules.BOUNDARY_MAX_OFFSET_M) return p.boundary to true
    }
    val areaSqm = p.sqft * SQFT_M2
    if (areaSqm <= 0) return null
    val proj = LocalProjection(lat, lng)
    return circleOfArea(XY(0.0, 0.0), areaSqm, 48).map { proj.toLatLng(it).let { (a, b) -> GeoPt(a, b) } } to false
}

const val SQFT_M2 = 0.09290304
fun sqmToCents(sqm: Double) = sqm / SQFT_M2 / SQFT_PER_CENT

// ---------------- turning OSM elements into facts ----------------

/** The facts the land check keeps from the map data (everything except AI and elevation). */
data class OsmFacts(
    val features: List<LandFeature>,
    val bufferPct: Double?,
    val waterPct: Double?,
    val nearestWaterM: Double?,
    val nearestWaterName: String,
    val nearestWaterKind: String,
    val waterOutlines: List<List<GeoPt>>,
    val landuse: List<String>,
)

/** A water element as a [Shape] in the projection: closed rings are areas (relation members are joined first); open waterways are lines. */
fun waterShape(e: OsmElement, kind: String, proj: LocalProjection): Shape {
    val assembled = assembleRings(e.paths)
    fun xy(path: List<Pair<Double, Double>>) = path.map { proj.toXY(it.first, it.second) }
    val rings = assembled.filter { it.size >= 4 && it.first() == it.last() }.map(::xy)
    val lines = assembled.filterNot { it.size >= 4 && it.first() == it.last() }.map(::xy)
    val waterwayLine = e.tags["waterway"] != null && kind != "riverbank"
    return Shape(rings, lines, if (waterwayLine) lineHalfWidth(kind) else 0.0)
}

/**
 * Computes distances and the water-buffer overlap for a plot at [lat]/[lng] with outline [plot] (lat/lng, may be
 * empty: then the pin alone is used). Pure, so it is unit-tested with a hand-made Overpass answer.
 */
fun osmFacts(elements: List<OsmElement>, lat: Double, lng: Double, plot: List<GeoPt>, bufferM: Double = LandRules.WATER_BUFFER_M): OsmFacts {
    val proj = LocalProjection(lat, lng)
    val pin = XY(0.0, 0.0)
    val classified = elements.mapNotNull(::classify)
    val plotXY = plot.map { proj.toXY(it.lat, it.lng) }

    // ---- water ----
    data class W(val c: Classified, val shape: Shape, val dist: Double)
    val byPin = classified.filter { it.category == Cat.WATER }
        .map { c -> c to waterShape(c.element, c.kind, proj) }
        .filter { !it.second.isEmpty() }
        .map { (c, s) -> W(c, s, distanceToShape(pin, s)) }
        .sortedBy { it.dist }
        .take(8)
    // With an outline, distances are from the plot's edge (nearest 8 water bodies by pin distance are enough).
    val waters = if (plotXY.size >= 3) byPin.map { w -> w.copy(dist = overlapWithBuffer(plotXY, listOf(w.shape), bufferM, 300).nearestM ?: w.dist) }.sortedBy { it.dist }
    else byPin
    val overlap = if (plotXY.size >= 3 && waters.isNotEmpty()) overlapWithBuffer(plotXY, waters.map { it.shape }, bufferM) else null
    val nearest = waters.firstOrNull()
    val pinOnly = plotXY.size < 3
    val bufferPct = when {
        overlap != null -> overlap.inBufferPct
        waters.isEmpty() -> 0.0
        pinOnly -> if (nearest != null && nearest.dist <= bufferM) 100.0 else 0.0
        else -> 0.0
    }
    val waterPct = when {
        overlap != null -> overlap.insideWaterPct
        pinOnly && nearest != null && nearest.dist <= 0.0 -> 100.0
        else -> 0.0
    }
    val outlines = waters.take(3).map { w ->
        val longest = assembleRings(w.c.element.paths).maxByOrNull { it.size } ?: emptyList()
        thin(longest, 160).map { GeoPt(it.first, it.second) }
    }.filter { it.size >= 2 }

    // ---- everything else: the nearest of each kind, from the pin ----
    fun distFromPin(e: OsmElement): Double? {
        val ds = e.paths.mapNotNull { path -> distanceToPath(pin, path.map { proj.toXY(it.first, it.second) }) }
        val pointD = if (e.lat != null && e.lng != null) haversineKm(lat, lng, e.lat, e.lng) * 1000 else null
        return (ds + listOfNotNull(pointD)).minOrNull()
    }
    val features = mutableListOf<LandFeature>()
    nearest?.let { w ->
        val pt = w.c.element.anyPoint()
        features += LandFeature(Cat.WATER, w.c.kind, w.c.element.name, w.dist, pt?.first, pt?.second)
    }
    // 2.3.0: protected areas and forests are areas – 0 when the pin is inside the (unclipped) outline.
    fun areaDist(e: OsmElement): Double? {
        val shape = waterShape(e, "", proj)
        val d = if (!shape.isEmpty()) distanceToShape(pin, shape) else null
        return listOfNotNull(d, distFromPin(e)).minOrNull()
    }
    val other = classified.filter { it.category != Cat.WATER && it.category != Cat.LANDUSE }
        .mapNotNull { c -> (if (c.category == Cat.PROTECTED || c.category == Cat.FOREST) areaDist(c.element) else distFromPin(c.element))?.let { c to it } }
        .groupBy { it.first.category }
    for ((cat, list) in other) {
        // Keep the nearest few per category (named ones first among equals) – enough for the reasons and the counts.
        list.sortedBy { it.second }.take(if (cat == Cat.BUS || cat == Cat.MARKET || cat == Cat.SCHOOL) 5 else 3).forEach { (c, d) ->
            // 2.3.0: lines and areas are marked on the map at their point nearest to the plot.
            val pt = if (cat in setOf(Cat.RAIL_LINE, Cat.PROTECTED, Cat.FOREST) && c.element.paths.isNotEmpty())
                closestOnPaths(pin, c.element.paths.map { path -> path.map { proj.toXY(it.first, it.second) } })?.let { proj.toLatLng(it) } ?: c.element.anyPoint()
            else c.element.anyPoint()
            features += LandFeature(cat, c.kind, c.element.name, d, pt?.first, pt?.second)
        }
    }
    val landuse = classified.filter { it.category == Cat.LANDUSE }.map { it.kind }.distinct()
    // 2.3.0: the pin inside a protected area / forest (the Overpass is_in answer) is distance 0.
    classified.filter { it.category == Cat.LANDUSE }.forEach { c ->
        val t = c.element.tags
        val cat = when {
            c.kind == "boundary=protected_area" || c.kind == "boundary=national_park" || c.kind == "leisure=nature_reserve" ||
                t["boundary"] in setOf("protected_area", "national_park") || t["leisure"] == "nature_reserve" -> Cat.PROTECTED
            c.kind == "landuse=forest" -> Cat.FOREST
            else -> null
        } ?: return@forEach
        if (features.none { it.category == cat && it.distanceM <= 0.0 && it.name == c.element.name })
            features += LandFeature(cat, if (cat == Cat.PROTECTED) protectedKind(t) else "forest", c.element.name, 0.0, lat, lng)
    }
    return OsmFacts(
        features = features,
        bufferPct = bufferPct,
        waterPct = waterPct,
        nearestWaterM = nearest?.dist,
        nearestWaterName = nearest?.c?.element?.name.orEmpty(),
        nearestWaterKind = nearest?.c?.kind.orEmpty(),
        waterOutlines = outlines,
        landuse = landuse,
    )
}

// ---------------- ratings ----------------

/** How good a tile is for the buyer. For Risk, POOR means HIGH risk. */
enum class LandRating { GOOD, FAIR, POOR, UNKNOWN }

enum class LandTile { BOUNDARY, CONNECTIVITY, RISK, LOCALITY, UTILITY, GROWTH,
    /** 2.3.0 */ RAILWAY, /** 2.3.0: protected areas / eco-sensitive zone / forest */ ECO }

/** Why a tile got its rating; the UI turns the code + args into Tamil / English. */
enum class LandReason {
    // Boundary
    DOCS_VERIFIED, AREA_MISMATCH, DRAWN_NOT_VERIFIED, NOT_VERIFIED,
    // Connectivity
    MAJOR_ROAD_NEAR, MAJOR_ROAD_FAR, NO_MAJOR_ROAD,
    // Risk
    PLOT_ON_WATER, IN_BUFFER, WATER_NEAR, LOW_LYING, NO_WATER_NEAR,
    // Locality
    AMENITIES,  NO_AMENITIES,
    // Utility
    UTILITIES, UTILITIES_DOCS, NO_UTILITIES,
    // Growth
    GROWTH_AI, GROWTH_NO_KEY, GROWTH_UNKNOWN,
    /** 2.6.0: an AI key is set but the online search has not been run for this pin (it runs on the user's tap). */
    GROWTH_NOT_RUN,
    // any tile
    NO_MAP_DATA,
    // 2.3.0 – Railway
    RAIL_NOC, RAIL_NEAR, RAIL_CLEAR, NO_RAIL,
    // 2.3.0 – Protected areas / ESZ / forest
    INSIDE_PROTECTED, ESZ_LIKELY, ESZ_POSSIBLE, NEAR_FOREST, NO_PROTECTED,
    /** 2.3.0: the layer was not part of this (older) check – refresh. */
    NOT_CHECKED,
    /** 2.3.0: this tile's map layer did not load this time. */
    LAYER_FAILED,
    /** 2.3.0: the water layer did not load, but the ISRO land-use map shows a water body at the plot. */
    LULC_WATER,
}

/**
 * An extra line under a tile: [code] is one of "low_lying", "no_access", "proposed", "rail", and (2.3.0)
 * "crossing", "station", "lulc_water", "lulc_forest".
 */
data class Note(val code: String, val args: List<String> = emptyList())

data class TileResult(
    val tile: LandTile,
    val rating: LandRating,
    val reason: LandReason,
    /** Already formatted values (distances, names, %) for the reason text. */
    val args: List<String> = emptyList(),
    /** Extra notes (e.g. "new road 1.2 km", "no mapped road at the plot"). */
    val notes: List<Note> = emptyList(),
    /** The tile is (partly) an AI estimate. */
    val ai: Boolean = false,
)

/** "850 m" / "1.2 km". */
fun distText(m: Double): String = if (m < 100) "${m.roundToLong().coerceAtLeast(0)} m" else if (m < 950) "${(m / 10).roundToLong() * 10} m" else {
    val km = (m / 100).roundToLong() / 10.0
    (if (km % 1.0 == 0.0) km.toLong().toString() else km.toString()) + " km"
}

private fun LandCheck.nearest(cat: String): LandFeature? = features.filter { it.category == cat }.minByOrNull { it.distanceM }
private fun LandCheck.within(cat: String, m: Double): Int = features.count { it.category == cat && it.distanceM <= m }

/** "25" / "<1" (never "0" for a real overlap). */
fun pct0(v: Double): String = if (v > 0 && v < 1) "<1" else v.roundToLong().toString()

fun lowLyingBy(c: LandCheck): Double? {
    val e = c.elevationM ?: return null
    val s = c.surroundingElevationM ?: return null
    return s - e
}

/** 2.3.0: did the map layer behind [cat] fail in this check? */
fun LandCheck.layerFailed(layer: String): Boolean = layer in osmFailedLayers

/** 2.3.0: Bhuvan land use at the pin / corners says water (or wetland). */
fun lulcWater(c: LandCheck): LulcHit? = c.lulc.firstOrNull { lulcKind(it) == LulcKind.WATER || lulcKind(it) == LulcKind.WETLAND }

fun riskTile(c: LandCheck): TileResult {
    if (!c.osmOk || c.layerFailed(OsmLayer.WATER)) {
        // 2.3.0: the water layer did not load – the ISRO land-use map can still flag a water body at the plot.
        // 2.4.0: the same when no map data loaded at all, and the elevation can still show low-lying land.
        val w = lulcWater(c)
        val low = lowLyingBy(c)
        return when {
            w != null -> TileResult(LandTile.RISK, LandRating.POOR, LandReason.LULC_WATER, listOf(lulcLabel(w)))
            low != null && low >= LandRules.LOW_LYING_M -> TileResult(LandTile.RISK, LandRating.FAIR, LandReason.LOW_LYING, listOf(dec1(low)))
            !c.osmOk -> TileResult(LandTile.RISK, LandRating.UNKNOWN, LandReason.NO_MAP_DATA)
            else -> TileResult(LandTile.RISK, LandRating.UNKNOWN, LandReason.LAYER_FAILED)
        }
    }
    val buf = c.bufferPct ?: 0.0
    val water = c.waterPct ?: 0.0
    val name = c.nearestWaterName
    val pinOnWater = c.landuse.any { it in setOf("landuse=reservoir", "landuse=basin", "natural=water", "natural=wetland") }
    val low = lowLyingBy(c)
    val notes = mutableListOf<Note>()
    if (low != null && low >= LandRules.LOW_LYING_M) notes += Note("low_lying", listOf(dec1(low)))
    // 2.3.0: an independent source (ISRO land-use map) shows water where OpenStreetMap shows none – at least a caution.
    val lw = lulcWater(c)?.takeIf { water <= 0.0 && buf <= 0.0 && !pinOnWater }
    if (lw != null) notes += Note("lulc_water", listOf(lulcLabel(lw)))
    val base = riskBase(c, water, buf, pinOnWater, name, low, notes)
    return if (lw != null && base.rating == LandRating.GOOD) base.copy(rating = LandRating.FAIR) else base
}

private fun riskBase(c: LandCheck, water: Double, buf: Double, pinOnWater: Boolean, name: String, low: Double?, notes: List<Note>): TileResult {
    return when {
        water > 0.0 || pinOnWater -> TileResult(LandTile.RISK, LandRating.POOR, LandReason.PLOT_ON_WATER, listOf(pct0(maxOf(water, if (pinOnWater) 100.0 else 0.0)), name), notes)
        buf >= LandRules.HIGH_RISK_PCT -> TileResult(LandTile.RISK, LandRating.POOR, LandReason.IN_BUFFER, listOf(pct0(buf), name, distText(c.bufferM)), notes)
        buf > 0.0 -> TileResult(LandTile.RISK, LandRating.FAIR, LandReason.IN_BUFFER, listOf(pct0(buf), name, distText(c.bufferM)), notes)
        (c.nearestWaterM ?: Double.MAX_VALUE) <= LandRules.WATER_CAUTION_M ->
            TileResult(LandTile.RISK, LandRating.FAIR, LandReason.WATER_NEAR, listOf(distText(c.nearestWaterM!!), name), notes)
        low != null && low >= LandRules.LOW_LYING_M -> TileResult(LandTile.RISK, LandRating.FAIR, LandReason.LOW_LYING, listOf(dec1(low)), notes.filterNot { it.code == "low_lying" })
        else -> TileResult(LandTile.RISK, LandRating.GOOD, LandReason.NO_WATER_NEAR,
            listOf(c.nearestWaterM?.let(::distText) ?: ("> " + distText(LandRules.WATER_RADIUS_M.toDouble())), name), notes)
    }
}

private fun dec1(v: Double) = ((v * 10).roundToLong() / 10.0).toString()

private fun up(r: LandRating) = when (r) { LandRating.POOR -> LandRating.FAIR; LandRating.FAIR -> LandRating.GOOD; else -> r }
private fun down(r: LandRating) = when (r) { LandRating.GOOD -> LandRating.FAIR; LandRating.FAIR -> LandRating.POOR; else -> r }

/** Nearest proposed / under-construction road: from the map, or (AI estimate) from the web search. */
fun proposedRoad(c: LandCheck): Pair<String, Double>? {
    val osm = c.nearest(Cat.PROPOSED_ROAD)?.let { (it.name.ifBlank { it.kind }) to it.distanceM }
    val ai = c.ai?.roads.orEmpty().filter { it.distanceKm != null && !it.status.contains("complet", true) }
        .minByOrNull { it.distanceKm!! }?.let { it.name to it.distanceKm!! * 1000 }
    return listOfNotNull(osm, ai).minByOrNull { it.second }
}

fun connectivityTile(c: LandCheck): TileResult {
    if (!c.osmOk) return TileResult(LandTile.CONNECTIVITY, LandRating.UNKNOWN, LandReason.NO_MAP_DATA)
    if (c.layerFailed(OsmLayer.ROADS)) return TileResult(LandTile.CONNECTIVITY, LandRating.UNKNOWN, LandReason.LAYER_FAILED)
    val major = c.nearest(Cat.MAJOR_ROAD)
    val access = c.nearest(Cat.ROAD) ?: major?.takeIf { it.distanceM <= LandRules.ACCESS_ROAD_M }
    var rating = when {
        major == null -> LandRating.POOR
        major.distanceM <= LandRules.MAJOR_ROAD_STRONG_M -> LandRating.GOOD
        major.distanceM <= LandRules.MAJOR_ROAD_MODERATE_M -> LandRating.FAIR
        else -> LandRating.POOR
    }
    val notes = mutableListOf<Note>()
    if (access == null || access.distanceM > LandRules.ACCESS_ROAD_M) {
        rating = if (rating == LandRating.GOOD) LandRating.FAIR else rating
        notes += Note("no_access")
    }
    val proposed = proposedRoad(c)
    var ai = false
    if (proposed != null && proposed.second <= LandRules.PROPOSED_ROAD_BONUS_M) {
        rating = up(rating)
        notes += Note("proposed", listOf(proposed.first, distText(proposed.second)))
        ai = c.nearest(Cat.PROPOSED_ROAD)?.distanceM?.let { it > proposed.second + 1 } ?: true
    }
    c.nearest(Cat.RAIL)?.let { notes += Note("rail", listOf(it.name.ifBlank { it.kind }, distText(it.distanceM))) }
    return if (major == null) TileResult(LandTile.CONNECTIVITY, rating, LandReason.NO_MAJOR_ROAD, emptyList(), notes, ai)
    else TileResult(
        LandTile.CONNECTIVITY, rating,
        if (major.distanceM <= LandRules.MAJOR_ROAD_MODERATE_M) LandReason.MAJOR_ROAD_NEAR else LandReason.MAJOR_ROAD_FAR,
        listOf(major.name.ifBlank { major.kind }, distText(major.distanceM), major.kind), notes, ai,
    )
}

fun localityTile(c: LandCheck): TileResult {
    if (!c.osmOk) return TileResult(LandTile.LOCALITY, LandRating.UNKNOWN, LandReason.NO_MAP_DATA)
    if (c.layerFailed(OsmLayer.AMENITIES)) return TileResult(LandTile.LOCALITY, LandRating.UNKNOWN, LandReason.LAYER_FAILED)
    val r = LandRules.LOCALITY_RADIUS_M
    val cats = listOf(Cat.SCHOOL, Cat.HOSPITAL, Cat.BUS, Cat.MARKET)
    val present = cats.filter { c.within(it, r) > 0 }
    val school = c.nearest(Cat.SCHOOL)?.distanceM
    val hospital = c.nearest(Cat.HOSPITAL)?.distanceM
    val rating = when {
        present.size >= 3 && (school ?: 1e9) <= LandRules.SCHOOL_NEAR_M && (hospital ?: 1e9) <= LandRules.HOSPITAL_NEAR_M -> LandRating.GOOD
        present.size >= 2 -> LandRating.FAIR
        else -> LandRating.POOR
    }
    // args: pairs of category + distance for what is within 3 km, nearest first
    val args = present.mapNotNull { cat -> c.nearest(cat)?.let { listOf(cat, distText(it.distanceM)) } }.flatten()
    return TileResult(LandTile.LOCALITY, rating, if (present.isEmpty()) LandReason.NO_AMENITIES else LandReason.AMENITIES, args)
}

fun utilityTile(c: LandCheck, docs: Set<DocItem>): TileResult {
    val powerDoc = DocItem.ELECTRICITY in docs
    val waterDoc = DocItem.WATER in docs
    val power = c.nearest(Cat.POWER)?.takeIf { it.distanceM <= LandRules.POWER_NEAR_M }
    val water = c.nearest(Cat.WATER_SUPPLY)?.takeIf { it.distanceM <= LandRules.WATER_SUPPLY_NEAR_M }
    val hasPower = powerDoc || power != null
    val hasWater = waterDoc || water != null
    val rating = when {
        hasPower && hasWater -> LandRating.GOOD
        hasPower || hasWater -> LandRating.FAIR
        else -> LandRating.UNKNOWN
    }
    val args = listOf(
        when { powerDoc -> "doc"; power != null -> distText(power.distanceM); else -> "" },
        when { waterDoc -> "doc"; water != null -> distText(water.distanceM); else -> "" },
    )
    val reason = when {
        !hasPower && !hasWater && c.osmOk && c.layerFailed(OsmLayer.UTILITIES) -> LandReason.LAYER_FAILED
        !hasPower && !hasWater -> if (c.osmOk) LandReason.NO_UTILITIES else LandReason.NO_MAP_DATA
        powerDoc || waterDoc -> LandReason.UTILITIES_DOCS
        else -> LandReason.UTILITIES
    }
    return TileResult(LandTile.UTILITY, rating, reason, args)
}

/** Boundary: never "verified with records" automatically (TN FMB / patta are not an open API). */
fun boundaryTile(c: LandCheck?, p: Property): TileResult {
    val docsOk = DocItem.FMB_SKETCH in p.docs && DocItem.PATTA in p.docs
    val stated = p.sqft * SQFT_M2
    if (c != null && c.drawn && stated > 0 && c.plotAreaSqm > 0) {
        val diff = (c.plotAreaSqm / stated - 1) * 100
        if (kotlin.math.abs(diff) > LandRules.AREA_MISMATCH_PCT) {
            return TileResult(LandTile.BOUNDARY, LandRating.POOR, LandReason.AREA_MISMATCH,
                listOf(dec1(sqmToCents(c.plotAreaSqm)), dec1(sqmToCents(stated)), (if (diff > 0) "+" else "−") + kotlin.math.abs(diff).roundToLong() + "%"))
        }
    }
    return when {
        docsOk -> TileResult(LandTile.BOUNDARY, LandRating.GOOD, LandReason.DOCS_VERIFIED)
        c != null && c.drawn -> TileResult(LandTile.BOUNDARY, LandRating.FAIR, LandReason.DRAWN_NOT_VERIFIED, listOf(dec1(sqmToCents(c.plotAreaSqm))))
        else -> TileResult(LandTile.BOUNDARY, LandRating.UNKNOWN, LandReason.NOT_VERIFIED)
    }
}

fun growthTile(c: LandCheck, hasKey: Boolean): TileResult {
    val g = c.ai?.growth?.lowercase().orEmpty()
    val rating = when {
        g.startsWith("high") -> LandRating.GOOD
        g.startsWith("moder") || g.startsWith("medium") -> LandRating.FAIR
        g.startsWith("low") -> LandRating.POOR
        else -> LandRating.UNKNOWN
    }
    return when {
        rating != LandRating.UNKNOWN -> TileResult(LandTile.GROWTH, rating, LandReason.GROWTH_AI, listOf(c.ai?.growthReason.orEmpty()), ai = true)
        !hasKey && c.ai == null -> TileResult(LandTile.GROWTH, LandRating.UNKNOWN, LandReason.GROWTH_NO_KEY)
        c.ai == null && c.aiError == null && c.aiFetchedAt == 0L -> TileResult(LandTile.GROWTH, LandRating.UNKNOWN, LandReason.GROWTH_NOT_RUN)
        else -> TileResult(LandTile.GROWTH, LandRating.UNKNOWN, LandReason.GROWTH_UNKNOWN, ai = c.ai != null)
    }
}

/** 2.3.0: railway line / level crossing / station near the plot. GOOD = no railway line close to the plot. */
fun railwayTile(c: LandCheck): TileResult {
    if (!c.osmOk) return TileResult(LandTile.RAILWAY, LandRating.UNKNOWN, LandReason.NO_MAP_DATA)
    if (c.layersVersion < LandRules.LAYERS_VERSION) return TileResult(LandTile.RAILWAY, LandRating.UNKNOWN, LandReason.NOT_CHECKED)
    if (c.layerFailed(OsmLayer.RAIL)) return TileResult(LandTile.RAILWAY, LandRating.UNKNOWN, LandReason.LAYER_FAILED)
    val line = c.nearest(Cat.RAIL_LINE)
    val notes = mutableListOf<Note>()
    c.nearest(Cat.LEVEL_CROSSING)?.takeIf { it.distanceM <= 1500 }?.let { notes += Note("crossing", listOf(distText(it.distanceM))) }
    c.nearest(Cat.RAIL)?.let { notes += Note("station", listOf(it.name.ifBlank { it.kind }, distText(it.distanceM))) }
    return when {
        line == null -> TileResult(LandTile.RAILWAY, LandRating.GOOD, LandReason.NO_RAIL, emptyList(), notes)
        line.distanceM <= LandRules.RAIL_NOC_M -> TileResult(LandTile.RAILWAY, LandRating.POOR, LandReason.RAIL_NOC, listOf(distText(line.distanceM), line.name), notes)
        line.distanceM <= LandRules.RAIL_NEAR_M -> TileResult(LandTile.RAILWAY, LandRating.FAIR, LandReason.RAIL_NEAR, listOf(distText(line.distanceM), line.name), notes)
        else -> TileResult(LandTile.RAILWAY, LandRating.GOOD, LandReason.RAIL_CLEAR, listOf(distText(line.distanceM), line.name), notes)
    }
}

/** True for the kinds of protected area that get an eco-sensitive zone (national parks and wildlife sanctuaries). */
fun eszKind(kind: String): Boolean = kind in setOf("national_park", "wildlife_sanctuary", "tiger_reserve", "protected_area", "nature_reserve")

/** 2.3.0: protected areas, eco-sensitive zone and forest land. GOOD = none near. */
fun ecoTile(c: LandCheck): TileResult {
    if (!c.osmOk) return TileResult(LandTile.ECO, LandRating.UNKNOWN, LandReason.NO_MAP_DATA)
    if (c.layersVersion < LandRules.LAYERS_VERSION) return TileResult(LandTile.ECO, LandRating.UNKNOWN, LandReason.NOT_CHECKED)
    val lulcForest = c.lulc.firstOrNull { lulcKind(it) == LulcKind.FOREST }
    if (c.layerFailed(OsmLayer.PROTECTED) && c.layerFailed(OsmLayer.LANDUSE)) {
        return if (lulcForest != null) TileResult(LandTile.ECO, LandRating.FAIR, LandReason.NEAR_FOREST, listOf("0 m", lulcLabel(lulcForest)), listOf(Note("lulc_forest", listOf(lulcLabel(lulcForest)))))
        else TileResult(LandTile.ECO, LandRating.UNKNOWN, LandReason.LAYER_FAILED)
    }
    val pa = c.features.filter { it.category == Cat.PROTECTED }.minByOrNull { it.distanceM }
    val forest = c.nearest(Cat.FOREST)
    val notes = mutableListOf<Note>()
    if (lulcForest != null) notes += Note("lulc_forest", listOf(lulcLabel(lulcForest)))
    fun nm(f: LandFeature) = f.name.ifBlank { f.kind.replace('_', ' ') }
    return when {
        pa != null && pa.distanceM <= 0.0 -> TileResult(LandTile.ECO, LandRating.POOR, LandReason.INSIDE_PROTECTED, listOf(nm(pa), pa.kind), notes)
        forest != null && forest.distanceM <= 0.0 -> TileResult(LandTile.ECO, LandRating.POOR, LandReason.INSIDE_PROTECTED, listOf(nm(forest), "forest"), notes)
        pa != null && eszKind(pa.kind) && pa.distanceM <= LandRules.ESZ_MIN_M -> TileResult(LandTile.ECO, LandRating.POOR, LandReason.ESZ_LIKELY, listOf(nm(pa), distText(pa.distanceM)), notes)
        pa != null && pa.distanceM <= LandRules.ESZ_MAX_M -> TileResult(LandTile.ECO, LandRating.FAIR, LandReason.ESZ_POSSIBLE, listOf(nm(pa), distText(pa.distanceM)), notes)
        forest != null && forest.distanceM <= LandRules.FOREST_NEAR_M -> TileResult(LandTile.ECO, LandRating.FAIR, LandReason.NEAR_FOREST, listOf(distText(forest.distanceM), nm(forest)), notes)
        lulcForest != null -> TileResult(LandTile.ECO, LandRating.FAIR, LandReason.NEAR_FOREST, listOf("0 m", lulcLabel(lulcForest)), notes)
        else -> TileResult(LandTile.ECO, LandRating.GOOD, LandReason.NO_PROTECTED, listOf(distText(LandRules.ESZ_MAX_M)), notes)
    }
}

/** The banner on top: the main risk in plain words (usable vs paid area). */
data class LandBanner(
    val level: LandRating,
    /** % of the plot in the water buffer (for "you'd pay for X but could build on Y"). */
    val bufferPct: Double,
    /** Area paid for (stated size, else the drawn / assumed outline), in cents. */
    val paidCents: Double?,
    val usableCents: Double?,
    val risk: TileResult,
)

/** [tiles]: the six 2.2.0 tiles; [extra]: the 2.3.0 Railway and Protected-area tiles (shown after them). */
data class LandAssessment(val banner: LandBanner, val tiles: List<TileResult>, val extra: List<TileResult> = emptyList()) {
    fun tile(t: LandTile) = (tiles + extra).first { it.tile == t }
    val allTiles: List<TileResult> get() = tiles + extra
}

fun assessLand(c: LandCheck, p: Property, hasKey: Boolean): LandAssessment {
    val risk = riskTile(c)
    val stated = p.sqft * SQFT_M2
    val paid = (if (stated > 0) stated else c.plotAreaSqm).takeIf { it > 0 }
    val buf = maxOf(c.bufferPct ?: 0.0, c.waterPct ?: 0.0).let { if (it <= 0.0 && risk.reason == LandReason.PLOT_ON_WATER) 100.0 else it }
    val paidC = paid?.let(::sqmToCents)
    val tiles = listOf(boundaryTile(c, p), connectivityTile(c), risk, localityTile(c), utilityTile(c, p.docs), growthTile(c, hasKey))
    return LandAssessment(LandBanner(risk.rating, buf, paidC, paidC?.let { it * (1 - buf / 100.0) }, risk), tiles, listOf(railwayTile(c), ecoTile(c)))
}

/** Zoning hint: OSM landuse at the pin, then the AI's master-plan zoning (labelled as such in the UI). */
fun osmZoning(c: LandCheck): String? = c.landuse.firstOrNull { it.startsWith("landuse=") }?.removePrefix("landuse=")

// ---------------- AI (grounded Gemini) ----------------

fun buildLandPrompt(placeDescription: String, lat: Double, lng: Double, areaText: String, typeText: String): String = """
You help a land buyer in Tamil Nadu, India check a plot before buying. Use Google Search.
The plot: $typeText, $areaText, at $placeDescription (coordinates ${r5(lat)}, ${r5(lng)}).
Find, only from pages you actually found (government notifications, DTCP / CMDA / local planning authority, news):
1. The land-use zone of this exact area in the notified master plan / local planning area (for example DTCP, a Local Planning Area or the Madurai Master Plan): residential, commercial, industrial, agricultural, institutional, mixed, or unknown. Name the plan.
2. Roads near these coordinates (within about 10 km) that are announced, proposed or under construction: ring roads, bypasses, highways, industrial / economic corridors. Give the approximate distance in km from the coordinates and the status.
3. The growth outlook of this locality (new approved layouts, industrial parks, IT parks, airport / rail / metro projects): high, moderate or low, with a one-line reason.
Rules: never guess or invent. If no page says it, use "unknown" and leave the URL empty. Every item needs the URL of the page it is from. Keep text short.
Reply with ONLY this JSON (no other text):
{"zoning":{"value":"residential|commercial|industrial|agricultural|institutional|mixed|unknown","plan":"plan name","note":"very short","source_url":"https://..."},"roads":[{"name":"road / project","status":"announced|proposed|under construction|completed","distance_km":number or null,"date":"date on the page","source_url":"https://..."}],"growth":{"rating":"high|moderate|low|unknown","reason":"one short line","source_url":"https://..."}}
""".trimIndent()

private fun r5(v: Double) = (v * 100_000).roundToLong() / 100_000.0

private fun JsonElement?.s(): String = (this as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().let { if (it == "null") "" else it }
private fun JsonElement?.d(): Double? = (this as? JsonPrimitive)?.contentOrNull?.replace(",", "")?.trim()?.toDoubleOrNull()

/** Parses the model's JSON reply (fenced or not). Unknown / unsourced items are dropped. Null when there is no JSON. */
fun parseLandAi(text: String): LandAi? {
    val fenced = Regex("""```(?:json)?\s*(\{.*?\})\s*```""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
    val start = text.indexOf("{\"zoning\"").takeIf { it >= 0 } ?: text.indexOf('{').takeIf { it >= 0 } ?: return null
    val raw = fenced ?: balancedJson(text, start) ?: return null
    val o = parseJsonOrNull(raw) ?: return null
    fun url(e: JsonElement?) = e.s().takeIf { it.startsWith("http") }.orEmpty()
    val z = o["zoning"]
    val g = o["growth"]
    val roads = o["roads"].arr().mapNotNull { r ->
        val name = r["name"].s()
        val u = url(r["source_url"])
        if (name.isBlank() || name.equals("unknown", true) || u.isBlank()) null
        else AiRoad(name.take(80), r["status"].s().take(30), r["distance_km"].d()?.takeIf { it in 0.0..50.0 }, u, r["date"].s().take(30))
    }.take(5)
    val zoning = z["value"].s().lowercase().let { if (url(z["source_url"]).isBlank()) "unknown" else it }
    val growth = g["rating"].s().lowercase().let { if (url(g["source_url"]).isBlank()) "unknown" else it }
    return LandAi(
        zoning = zoning.take(30), zoningPlan = z["plan"].s().take(80), zoningNote = z["note"].s().take(160), zoningUrl = url(z["source_url"]),
        roads = roads,
        growth = growth.take(20), growthReason = g["reason"].s().take(160), growthUrl = url(g["source_url"]),
    )
}

// ---------------- running the check ----------------

/** 8 points about [radiusM] around the pin (for the low-lying check). */
fun ringPoints(lat: Double, lng: Double, radiusM: Double = 500.0): List<Pair<Double, Double>> {
    val proj = LocalProjection(lat, lng)
    return (0 until 8).map { i ->
        val t = 2 * PI * i / 8
        proj.toLatLng(XY(radiusM * cos(t), radiusM * sin(t)))
    }
}

fun medianOf(v: List<Double>): Double? = com.hark.sothudiary.domain.pricing.median(v)

/** 2.3.0: free web search queries for the land check's AI fallback (zoning, roads, growth). */
fun landSearchQueries(rev: com.hark.sothudiary.domain.GeoPlace?, hint: String): List<String> {
    val local = listOfNotNull(rev?.locality?.takeIf { it.isNotBlank() }, hint.split(',').firstOrNull()?.trim()?.takeIf { it.isNotBlank() }).firstOrNull().orEmpty()
    val district = rev?.district.orEmpty().ifBlank { hint.split(',').map { it.trim() }.lastOrNull().orEmpty() }
    val place = listOf(local, district).filter { it.isNotBlank() }.distinct().joinToString(" ")
    if (place.isBlank()) return emptyList()
    return listOf("$place master plan land use zone DTCP", "$place ring road bypass new highway project", "$place development new layouts industrial park growth")
}

/**
 * 2.3.0: fills the layers that failed this time from the earlier check of the same spot ([prev]), so a busy server
 * does not wipe out what was already known. Returns the merged check (failed layers that could be filled are listed
 * in [LandCheck.osmKeptLayers] and no longer in [LandCheck.osmFailedLayers]).
 */
fun keepEarlierLayers(c: LandCheck, prev: LandCheck?): LandCheck {
    if (prev == null || !prev.osmOk || c.osmFailedLayers.isEmpty()) return c
    val kept = mutableListOf<String>()
    var out = c
    for (layer in c.osmFailedLayers) {
        if (layer in prev.osmFailedLayers) continue
        // A 2.2.x check never had these layers.
        if ((layer == OsmLayer.RAIL || layer == OsmLayer.PROTECTED) && prev.layersVersion < LandRules.LAYERS_VERSION) continue
        val cats = OsmLayer.categories(layer)
        out = when (layer) {
            OsmLayer.WATER -> out.copy(
                features = out.features.filterNot { it.category in cats } + prev.features.filter { it.category in cats },
                bufferPct = prev.bufferPct, waterPct = prev.waterPct, nearestWaterM = prev.nearestWaterM,
                nearestWaterName = prev.nearestWaterName, nearestWaterKind = prev.nearestWaterKind, waterOutlines = prev.waterOutlines,
            )
            OsmLayer.LANDUSE -> out.copy(landuse = prev.landuse,
                features = out.features + prev.features.filter { (it.category == Cat.PROTECTED || it.category == Cat.FOREST) && it.distanceM <= 0.0 && out.features.none { f -> f.category == it.category && f.name == it.name } })
            else -> out.copy(features = out.features.filterNot { it.category in cats } + prev.features.filter { it.category in cats })
        }
        kept += layer
    }
    return out.copy(osmFailedLayers = c.osmFailedLayers - kept.toSet(), osmKeptLayers = kept)
}

/** 2.4.0: does a saved check have anything to show (map data, elevation, ISRO land use or the AI part)? */
fun LandCheck.hasAnyData(): Boolean = osmOk || lulc.isNotEmpty() || elevationM != null || ai != null

private fun secs(ms: Long) = if (ms < 1000) "$ms ms" else "${(ms + 500) / 1000} s"

/** Open-Meteo elevation with its status line. */
private suspend fun elevationWithStatus(points: List<Pair<Double, Double>>): Pair<List<Double>?, SourceStatusLine> {
    val url = elevationUrl(points)
    val r = com.hark.sothudiary.httpCall(url, headers = mapOf("User-Agent" to com.hark.sothudiary.APP_USER_AGENT, "Accept" to "application/json"),
        timeoutMs = LandRules.QUICK_SOURCE_MS.toInt())
    val v = if (r.ok) parseElevations(r.body)?.takeIf { it.size == points.size } else null
    val line = SourceStatusLine(
        "Open-Meteo elevation", v != null,
        when { v != null -> "pin ${v[0].roundToLong()} m"; r.code < 0 -> r.error ?: "no answer"; r.ok -> "unreadable answer"; else -> "HTTP ${r.code}" },
        r.ms, "GET api.open-meteo.com/v1/elevation · HTTP ${r.code}" + (r.error?.let { " · $it" } ?: ""),
    )
    return v to line
}

/** ISRO Bhuvan land use with its status line. */
private suspend fun bhuvanWithStatus(points: List<Pair<Double, Double>>): Pair<List<LulcHit>?, SourceStatusLine> {
    val (lat, lng) = points.first()
    if (Bhuvan.layersAt(lat, lng).isEmpty()) return emptyList<LulcHit>() to SourceStatusLine("ISRO Bhuvan land use", null, "outside the Tamil Nadu map coverage")
    val t0 = nowMillis()
    // Unchanged 2.3.0 call (see the 2.4.0 report: Bhuvan's terms need NRSC permission for automatic use).
    val hits = runCatching { Bhuvan.landUse(points) }.getOrNull()
    val ms = nowMillis() - t0
    return hits to SourceStatusLine(
        "ISRO Bhuvan land use", hits != null,
        when { hits == null -> "no answer"; hits.isEmpty() -> "no land-use class at the pin"; else -> lulcLabel(hits.first()) },
        ms, "WMS GetFeatureInfo " + Bhuvan.layersAt(lat, lng).joinToString(","),
    )
}

/**
 * Runs the land check for [p] (it must have a pin): OpenStreetMap layers (Overpass, 2.4.0: two combined queries raced
 * across the free servers within [LandRules.OSM_BUDGET_MS]), elevation (Open-Meteo) and ISRO Bhuvan land use (each
 * with its own short limit), all in parallel with the grounded AI search (only when a Gemini key is set; falls back to
 * a free web search). A part that fails keeps what an earlier check found for the same pin ([previous]) and records
 * its error. 2.4.0: [onPartial] gets the map / elevation / land-use result as soon as those are in – before the slower
 * AI part – so the screen can show it; every source's outcome is in [LandCheck.status].
 */
suspend fun runLandCheck(
    p: Property, llm: com.hark.sothudiary.domain.ai.LlmConfig, typeText: String, previous: LandCheck?, now: Long = nowMillis(),
    onPartial: suspend (LandCheck) -> Unit = {},
    /** 2.6.0: also run the AI web search now (the screen's "Search online with AI" button sets this through [runLandAi]). */
    withAi: Boolean = LandRules.AUTO_AI_SEARCH,
): LandCheck? {
    val lat = p.lat ?: return null
    val lng = p.lng ?: return null
    val outline = plotOutline(p)
    val plot = outline?.first.orEmpty()
    val area = if (plot.size >= 3) polygonAreaSqm(plot.map { it.lat to it.lng }) else 0.0
    val key = landCheckKey(p)
    val sameSpot = previous?.takeIf { it.key == key }
    return coroutineScope {
        val osmJob = async {
            val radius = maxOf(LandRules.WATER_RADIUS_M, (kotlin.math.sqrt(area / PI) + 300).toInt())
            try {
                fetchOsmLayers(buildLayerQueries(lat, lng, radius), LandRules.OSM_BUDGET_MS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LayeredOsm(emptyList(), emptyList(), OsmLayer.ALL.associateWith { "network" },
                    listOf(SourceStatusLine("Overpass", false, e.message ?: "error")))
            }
        }
        val elevJob = async {
            kotlinx.coroutines.withTimeoutOrNull(LandRules.QUICK_SOURCE_MS + 2_000) { elevationWithStatus(listOf(lat to lng) + ringPoints(lat, lng)) }
                ?: (null to SourceStatusLine("Open-Meteo elevation", false, "timeout after ${LandRules.QUICK_SOURCE_MS / 1000} s"))
        }
        val lulcJob = if (!LandRules.AUTO_BHUVAN) null else async {
            val pts = listOf(lat to lng) + (if (outline?.second == true) plot.take(4).map { it.lat to it.lng } else emptyList())
            kotlinx.coroutines.withTimeoutOrNull(LandRules.QUICK_SOURCE_MS + 5_000) { bhuvanWithStatus(pts) }
                ?: (null to SourceStatusLine("ISRO Bhuvan land use", false, "timeout"))
        }
        val aiJob = if (!llm.hasKey || !withAi) null else async { landAiSearch(p, llm, typeText, area, now) }
        val osm = osmJob.await()
        val (elev, elevLine) = elevJob.await()
        val (lulc, lulcLine) = lulcJob?.await()
            ?: (null to SourceStatusLine("ISRO Bhuvan land use", null, "not loaded automatically (licence needs NRSC permission)"))

        var c = LandCheck(
            propertyId = p.id, key = key, lat = lat, lng = lng, fetchedAt = now,
            plot = plot, drawn = outline?.second == true, plotAreaSqm = area, layersVersion = LandRules.LAYERS_VERSION,
        )
        c = if (osm.anyOk) {
            val f = osmFacts(osm.elements, lat, lng, plot)
            keepEarlierLayers(
                c.copy(osmOk = true, features = f.features, bufferPct = f.bufferPct, waterPct = f.waterPct, nearestWaterM = f.nearestWaterM,
                    nearestWaterName = f.nearestWaterName, nearestWaterKind = f.nearestWaterKind, waterOutlines = f.waterOutlines, landuse = f.landuse,
                    osmFailedLayers = osm.failed.keys.toList(), osmError = if (osm.failed.isEmpty()) null else osm.failed.values.first()),
                sameSpot,
            )
        } else if (sameSpot != null && sameSpot.osmOk) {
            sameSpot.copy(fetchedAt = sameSpot.fetchedAt, osmError = osm.error, ai = null, aiError = null, status = emptyList())
        } else c.copy(osmOk = false, osmError = osm.error)
        if (elev != null && elev.size == 9) c = c.copy(elevationM = elev[0], surroundingElevationM = medianOf(elev.drop(1)))
        else if (sameSpot != null) c = c.copy(elevationM = sameSpot.elevationM, surroundingElevationM = sameSpot.surroundingElevationM)
        c = when {
            lulc != null -> c.copy(lulc = lulc, lulcError = null)
            lulcJob == null -> c.copy(lulc = emptyList(), lulcError = null)
            sameSpot != null && sameSpot.lulc.isNotEmpty() -> c.copy(lulc = sameSpot.lulc, lulcError = "network")
            else -> c.copy(lulcError = "network")
        }
        val mapLines = osm.attempts.map { it.toStatus() } + elevLine.toStatus() + lulcLine.toStatus()
        val aiPending = SourceStatusLine("${LandRules.AI_STATUS_PREFIX} (${llm.provider.name})", null, when {
            aiJob != null -> "working…"
            !llm.hasKey -> "skipped – no AI key (optional)"
            sameSpot?.ai != null -> "earlier result kept – tap “Search online with AI” to search again"
            else -> "not run yet – tap “Search online with AI” (reads public web pages)"
        }).toStatus()
        // Keep the earlier AI part while the new one is running.
        c = c.copy(ai = sameSpot?.ai, aiFetchedAt = sameSpot?.aiFetchedAt ?: 0, sources = sameSpot?.sources.orEmpty(), aiModel = sameSpot?.aiModel.orEmpty(),
            aiViaSearch = sameSpot?.aiViaSearch ?: false, status = mapLines + aiPending)
        if (aiJob != null) runCatching { onPartial(c) }

        val aiRes = aiJob?.await()
        if (aiRes == null) c.copy(status = mapLines + aiPending)
        else applyLandAi(c, aiRes.first, aiRes.second, llm, now)
    }
}

/** 2.6.0: the AI part of the land check – zoning, announced roads, growth – with the chosen provider ([groundedGenerate]). */
private suspend fun landAiSearch(p: Property, llm: com.hark.sothudiary.domain.ai.LlmConfig, typeText: String, areaSqm: Double, now: Long): Pair<Grounded, Long> {
    val lat = p.lat ?: return Grounded.Failed("[other] The property has no map pin.", 0L, llm.model) to 0L
    val lng = p.lng ?: return Grounded.Failed("[other] The property has no map pin.", 0L, llm.model) to 0L
    val rev = runCatching { Geocoder.reverse(lat, lng) }.getOrNull()
    val hint = listOf(p.land.village.ifBlank { p.locality }, p.land.taluk.ifBlank { p.taluk }, p.land.district.ifBlank { p.district })
        .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { p.address }
    val areaText = if (areaSqm > 0) "${dec1(sqmToCents(areaSqm))} cents (${dec1(sqmToCents(areaSqm) / 100)} acres)" else "size not known"
    val t0 = nowMillis()
    val g = kotlinx.coroutines.withTimeoutOrNull(LandRules.AI_BUDGET_MS) {
        groundedGenerate(llm, buildLandPrompt(describePlace(rev, hint), lat, lng, areaText, typeText), now, landSearchQueries(rev, hint))
    } ?: Grounded.Failed("[other] The AI web search took too long.", 0L, "auto")
    return g to (nowMillis() - t0)
}

/**
 * 2.6.0: puts an AI answer into a land check – the parsed result (only cited items with our own web search), or the
 * error and the raw search results – and replaces the AI line of its Status panel. Pure, so it is unit-tested.
 */
fun applyLandAi(base: LandCheck, ai: Grounded, ms: Long, llm: com.hark.sothudiary.domain.ai.LlmConfig, now: Long): LandCheck {
    val c = when (ai) {
        is Grounded.Ok -> {
            val parsed = parseLandAi(ai.text)?.let { if (ai.viaSearch) onlyCitedResults(it, ai.hits) else it }
            if (parsed == null) base.copy(aiError = "[other] The AI reply had no readable result.", aiModel = ai.model,
                webSnippets = if (ai.viaSearch) ai.hits.take(8) else emptyList())
            else base.copy(ai = parsed, aiError = null, aiRetryAt = 0, aiModel = ai.model, aiFetchedAt = now, sources = ai.sources.take(12),
                aiViaSearch = ai.viaSearch, webSnippets = emptyList())
        }
        is Grounded.Failed -> base.copy(aiError = ai.error, aiRetryAt = ai.retryAt, aiModel = ai.model, webSnippets = ai.snippets.take(8))
    }
    val line = when (ai) {
        is Grounded.Ok -> SourceStatusLine("${LandRules.AI_STATUS_PREFIX} (${ai.model})", c.aiError == null,
            if (c.aiError == null) "${c.sources.size} sources" else "reply had no readable result", ms).toStatus()
        is Grounded.Failed -> SourceStatusLine("${LandRules.AI_STATUS_PREFIX} (${llm.provider.name})", false,
            com.hark.sothudiary.domain.pricing.shortAiError(ai.error), ms, ai.error.take(300)).toStatus()
    }
    return c.copy(status = c.status.filterNot { it.source.startsWith(LandRules.AI_STATUS_PREFIX) } + line)
}

/**
 * 2.6.0: "Search online with AI" on the Land check screen – runs only the AI part for the saved check [base] (same
 * pin) with the provider chosen at setup, and returns the check with the AI result or its error.
 */
suspend fun runLandAi(p: Property, llm: com.hark.sothudiary.domain.ai.LlmConfig, typeText: String, base: LandCheck, now: Long = nowMillis()): LandCheck {
    if (!llm.hasKey) return applyLandAi(base, Grounded.Failed("no_key", 0L, llm.model), 0L, llm, now)
    val (g, ms) = landAiSearch(p, llm, typeText, base.plotAreaSqm, now)
    return applyLandAi(base, g, ms, llm, now)
}

/** 2.3.0: with our own web search, an item counts only when it cites one of the pages we fetched. */
fun onlyCitedResults(a: LandAi, hits: List<WebSnippet>): LandAi {
    fun ok(u: String) = u.isNotBlank() && com.hark.sothudiary.domain.web.citesResult(u, hits)
    return a.copy(
        zoning = if (ok(a.zoningUrl)) a.zoning else "unknown", zoningUrl = a.zoningUrl.takeIf(::ok).orEmpty(),
        zoningPlan = if (ok(a.zoningUrl)) a.zoningPlan else "", zoningNote = if (ok(a.zoningUrl)) a.zoningNote else "",
        roads = a.roads.filter { ok(it.url) },
        growth = if (ok(a.growthUrl)) a.growth else "unknown", growthUrl = a.growthUrl.takeIf(::ok).orEmpty(),
        growthReason = if (ok(a.growthUrl)) a.growthReason else "",
    )
}

/** Every web page behind the result (OSM / Open-Meteo credit and the AI's sources), for the Sources list. */
fun landSources(c: LandCheck): List<String> = buildList {
    c.ai?.zoningUrl?.takeIf { it.isNotBlank() }?.let(::add)
    c.ai?.roads?.forEach { if (it.url.isNotBlank()) add(it.url) }
    c.ai?.growthUrl?.takeIf { it.isNotBlank() }?.let(::add)
    addAll(c.sources)
}.distinct()

@Suppress("unused")
private val keepStr: (JsonElement?) -> String? = { it.str() }
