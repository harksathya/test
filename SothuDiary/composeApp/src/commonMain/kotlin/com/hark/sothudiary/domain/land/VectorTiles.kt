package com.hark.sothudiary.domain.land

import com.hark.sothudiary.APP_USER_AGENT
import com.hark.sothudiary.domain.social.arr
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.str
import com.hark.sothudiary.httpBytes
import com.hark.sothudiary.httpCall
import com.hark.sothudiary.ungzipOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * 2.7.0: a second source for the land check's main map layers (water, roads, land use at the pin) when the free Overpass
 * servers are busy or unreachable – the 2.6.x CI probes showed all three failing (overpass-api.de refusing whole networks
 * with 406, the VK Maps mirror answering 504 "busy", private.coffee not answering at all), which left every check
 * without map data.
 *
 * OpenFreeMap (openfreemap.org) serves the same OpenStreetMap data as ready-made vector tiles (OpenMapTiles schema):
 * free, no key, no request limit, and a plain static file per tile, so it does not get "busy" the way a query server
 * does. The tiles do not carry everything Overpass does (no power lines, no proposed roads – only roads under
 * construction – and the protected-area search needs 10 km), so they stand in only for the CORE group; the EXTRA group
 * (amenities, utilities, rail, protected areas) still comes from Overpass alone and stays "unknown" when it fails.
 */
object VectorTileRules {
    const val TILEJSON_URL = "https://tiles.openfreemap.org/planet"
    const val SOURCE = "OpenFreeMap map tiles"
    /** Detailed tiles (all roads, water outlines, land use) around the plot. One z14 tile is ~2.4 km wide in Madurai. */
    const val DETAIL_ZOOM = 14
    /** Wide tiles for main roads / roads under construction within [WIDE_RADIUS_M] (one z12 tile is ~9.6 km wide). */
    const val WIDE_ZOOM = 12
    const val WIDE_RADIUS_M = 5_000.0
    /** At most this many tiles per zoom (a huge plot / radius is cut to the tiles nearest the pin). */
    const val MAX_TILES = 9
    /** Overpass gets this long for the CORE group before the tiles are fetched as well. */
    const val HEDGE_MS = 20_000L
    /** Once the tiles answered, a still-running Overpass CORE query gets this much longer before it is stopped. */
    const val OVERPASS_GRACE_MS = 5_000L
    const val TILE_MAX_BYTES = 4_000_000
}

// ---------------------------------------------------------------------------------------------------------------------
// Protocol buffers – just what Mapbox Vector Tiles (MVT 2.1) need.

internal class PbReader(private val b: ByteArray, private var pos: Int = 0, private val end: Int = b.size) {
    fun hasMore() = pos < end
    fun varint(): Long {
        var r = 0L
        var shift = 0
        while (true) {
            if (pos >= end) throw IllegalArgumentException("truncated varint")
            val x = b[pos++].toInt() and 0xff
            r = r or ((x and 0x7f).toLong() shl shift)
            if (x < 0x80) return r
            shift += 7
            if (shift > 63) throw IllegalArgumentException("bad varint")
        }
    }
    /** Field number and wire type of the next field. */
    fun tag(): Pair<Int, Int> { val t = varint(); return (t ushr 3).toInt() to (t and 7).toInt() }
    private fun len(): Int {
        val n = varint()
        if (n < 0 || pos + n > end) throw IllegalArgumentException("truncated field")
        return n.toInt()
    }
    fun message(): PbReader { val n = len(); val r = PbReader(b, pos, pos + n); pos += n; return r }
    fun string(): String { val n = len(); val s = b.decodeToString(pos, pos + n); pos += n; return s }
    fun fixed64(): Long {
        if (pos + 8 > end) throw IllegalArgumentException("truncated fixed64")
        var r = 0L
        for (i in 0 until 8) r = r or ((b[pos + i].toLong() and 0xff) shl (8 * i))
        pos += 8; return r
    }
    fun fixed32(): Int {
        if (pos + 4 > end) throw IllegalArgumentException("truncated fixed32")
        var r = 0
        for (i in 0 until 4) r = r or ((b[pos + i].toInt() and 0xff) shl (8 * i))
        pos += 4; return r
    }
    fun packed(): LongArray {
        val r = message()
        val out = ArrayList<Long>()
        while (r.hasMore()) out += r.varint()
        return out.toLongArray()
    }
    fun skip(wireType: Int) {
        when (wireType) {
            0 -> varint()
            1 -> pos += 8
            2 -> { val n = len(); pos += n }
            5 -> pos += 4
            else -> throw IllegalArgumentException("wire type $wireType")
        }
        if (pos > end) throw IllegalArgumentException("truncated")
    }
}

/** One MVT feature: geometry type (1 point, 2 line, 3 polygon), its tags and its parts in tile coordinates. */
data class MvtFeature(val id: Long, val type: Int, val tags: Map<String, String>, val parts: List<List<Pair<Int, Int>>>)
data class MvtLayer(val name: String, val extent: Int, val features: List<MvtFeature>)

private fun zigzag(n: Long): Int = ((n ushr 1) xor -(n and 1)).toInt()

/** MVT geometry commands -> parts (each MoveTo starts one; ClosePath repeats the first point so rings are closed). */
internal fun decodeGeometry(g: LongArray): List<List<Pair<Int, Int>>> {
    val parts = mutableListOf<MutableList<Pair<Int, Int>>>()
    var x = 0; var y = 0
    var cur: MutableList<Pair<Int, Int>>? = null
    var i = 0
    while (i < g.size) {
        val cmd = g[i++].toInt()
        val id = cmd and 7
        val count = cmd ushr 3
        when (id) {
            1, 2 -> repeat(count) {
                if (i + 1 >= g.size) return parts
                x += zigzag(g[i++]); y += zigzag(g[i++])
                if (id == 1) { cur = mutableListOf(x to y); parts += cur!! } else cur?.add(x to y)
            }
            7 -> cur?.let { if (it.isNotEmpty()) it.add(it.first()) }
            else -> return parts
        }
    }
    return parts
}

private fun valueText(r: PbReader): String? {
    var v: String? = null
    while (r.hasMore()) {
        val (f, wt) = r.tag()
        v = when (f) {
            1 -> r.string()
            2 -> Float.fromBits(r.fixed32()).let { if (it == floor(it.toDouble()).toFloat()) it.toLong().toString() else it.toString() }
            3 -> Double.fromBits(r.fixed64()).let { if (it == floor(it)) it.toLong().toString() else it.toString() }
            4, 5 -> r.varint().toString()
            6 -> zigzag(r.varint()).toString()
            7 -> (r.varint() != 0L).toString()
            else -> { r.skip(wt); v }
        }
    }
    return v
}

/** Decodes a (not gzipped) vector tile; only the layers in [wanted] when given. Throws on a broken tile. */
fun decodeMvt(bytes: ByteArray, wanted: Set<String>? = null): List<MvtLayer> {
    val tile = PbReader(bytes)
    val layers = mutableListOf<MvtLayer>()
    while (tile.hasMore()) {
        val (f, wt) = tile.tag()
        if (f != 3 || wt != 2) { tile.skip(wt); continue }
        val lr = tile.message()
        var name = ""
        var extent = 4096
        val keys = mutableListOf<String>()
        val values = mutableListOf<String?>()
        val rawFeatures = mutableListOf<PbReader>()
        while (lr.hasMore()) {
            val (lf, lwt) = lr.tag()
            when (lf) {
                1 -> name = lr.string()
                2 -> rawFeatures += lr.message()
                3 -> keys += lr.string()
                4 -> values += valueText(lr.message())
                5 -> extent = lr.varint().toInt()
                else -> lr.skip(lwt)
            }
        }
        if (wanted != null && name !in wanted) continue
        val features = rawFeatures.map { fr ->
            var id = 0L; var type = 0
            var tags = LongArray(0); var geom = LongArray(0)
            while (fr.hasMore()) {
                val (ff, fwt) = fr.tag()
                when (ff) {
                    1 -> id = fr.varint()
                    2 -> tags = fr.packed()
                    3 -> type = fr.varint().toInt()
                    4 -> geom = fr.packed()
                    else -> fr.skip(fwt)
                }
            }
            val t = LinkedHashMap<String, String>()
            var k = 0
            while (k + 1 < tags.size) {
                val key = keys.getOrNull(tags[k].toInt()); val value = values.getOrNull(tags[k + 1].toInt())
                if (key != null && value != null) t[key] = value
                k += 2
            }
            MvtFeature(id, type, t, decodeGeometry(geom))
        }
        layers += MvtLayer(name, extent, features)
    }
    return layers
}

// ---------------------------------------------------------------------------------------------------------------------
// Web-Mercator tile maths.

fun lngToTileX(lng: Double, z: Int): Double = (lng + 180.0) / 360.0 * (1 shl z)
fun latToTileY(lat: Double, z: Int): Double {
    val r = lat * PI / 180.0
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)
}
/** Fractional tile coordinates -> lat / lng. */
fun tileToLatLng(tx: Double, ty: Double, z: Int): Pair<Double, Double> {
    val n = (1 shl z).toDouble()
    val lng = tx / n * 360.0 - 180.0
    val lat = atan(sinh(PI * (1.0 - 2.0 * ty / n))) * 180.0 / PI
    return lat to lng
}

/** The tiles (x, y) at zoom [z] covering [radiusM] around a point, nearest first, at most [max]. */
fun tilesAround(lat: Double, lng: Double, radiusM: Double, z: Int, max: Int = VectorTileRules.MAX_TILES): List<Pair<Int, Int>> {
    val dLat = radiusM / 111_320.0
    val dLng = radiusM / (111_320.0 * cos(lat * PI / 180.0))
    val n = (1 shl z) - 1
    val x0 = floor(lngToTileX(lng - dLng, z)).toInt().coerceIn(0, n); val x1 = floor(lngToTileX(lng + dLng, z)).toInt().coerceIn(0, n)
    val y0 = floor(latToTileY(lat + dLat, z)).toInt().coerceIn(0, n); val y1 = floor(latToTileY(lat - dLat, z)).toInt().coerceIn(0, n)
    val cx = lngToTileX(lng, z); val cy = latToTileY(lat, z)
    val all = (x0..x1).flatMap { x -> (y0..y1).map { y -> x to y } }
    return all.sortedBy { (x, y) -> val dx = x + 0.5 - cx; val dy = y + 0.5 - cy; dx * dx + dy * dy }.take(max)
}

// ---------------------------------------------------------------------------------------------------------------------
// OpenMapTiles schema -> the OSM tags [classify] understands.

private val MAJOR_CLASSES = setOf("motorway", "trunk", "primary", "secondary")
private val MINOR_CLASS_TAG = mapOf("tertiary" to "tertiary", "minor" to "residential", "service" to "service", "track" to "track")
/** Landuse classes (OpenMapTiles `landuse` layer) that are real OSM landuse values. */
private val LANDUSE_CLASSES = setOf("residential", "commercial", "industrial", "retail", "railway", "cemetery", "military", "quarry", "garages", "farmland", "farmyard", "orchard")

/**
 * OSM-style tags for one OpenMapTiles feature, or null when the land check does not use it. [detail] is true for the
 * z14 tiles (water, minor roads, land use), false for the wide z12 tiles (main roads and roads under construction only),
 * so a road is not counted twice.
 */
fun omtTags(layer: String, p: Map<String, String>, detail: Boolean): Map<String, String>? {
    val cls = p["class"] ?: return null
    val sub = p["subclass"]
    val name = (p["name:en"] ?: p["name_en"] ?: p["name"])?.takeIf { it.isNotBlank() }
    val t = LinkedHashMap<String, String>()
    when (layer) {
        "water" -> {
            if (!detail || cls == "swimming_pool") return null
            t["natural"] = "water"
            t["water"] = when (cls) { "ocean" -> "sea"; else -> cls }
            if (p["intermittent"] == "1" || p["intermittent"] == "true") t["intermittent"] = "yes"
        }
        "waterway" -> {
            if (!detail) return null
            t["waterway"] = when (cls) { "river", "stream", "canal", "drain" -> cls; "ditch" -> "drain"; else -> return null }
        }
        "transportation" -> {
            val base = cls.removeSuffix("_construction")
            when {
                cls.endsWith("_construction") -> {
                    if (detail) return null
                    t["highway"] = "construction"
                    t["construction"] = MINOR_CLASS_TAG[base] ?: base
                }
                base in MAJOR_CLASSES -> { if (detail) return null; t["highway"] = base }
                base in MINOR_CLASS_TAG -> { if (!detail) return null; t["highway"] = MINOR_CLASS_TAG.getValue(base) }
                else -> return null
            }
        }
        "landcover" -> {
            if (!detail) return null
            when (cls) {
                "farmland" -> t["landuse"] = when (sub) { "orchard", "vineyard", "plant_nursery", "farmyard" -> sub; else -> "farmland" }
                "wood" -> if (sub == "forest") t["landuse"] = "forest" else t["natural"] = "wood"
                "wetland" -> t["natural"] = "wetland"
                "grass" -> when (sub) {
                    "meadow", "grass", "village_green", "allotments", "recreation_ground" -> t["landuse"] = sub
                    "grassland", "heath", "scrub" -> t["natural"] = sub
                    else -> return null
                }
                "sand", "rock" -> t["natural"] = sub ?: cls
                else -> return null
            }
        }
        "landuse" -> {
            if (!detail || cls !in LANDUSE_CLASSES) return null
            t["landuse"] = cls
        }
        else -> return null
    }
    if (name != null) t["name"] = name
    return t
}

/**
 * The land check's CORE layers from decoded tiles: water ([OsmLayer.WATER]: water bodies, waterways and wetlands),
 * roads ([OsmLayer.ROADS]) and the land-use areas the pin is in ([OsmLayer.LANDUSE], like Overpass's is_in). Pieces of one
 * feature cut by tile edges are joined into one element (same layer, id and tags). Pure: unit-tested.
 */
fun tilesToLayers(
    tiles: List<Triple<Int, Pair<Int, Int>, List<MvtLayer>>>, lat: Double, lng: Double,
): Map<String, List<OsmElement>> {
    data class Acc(val layer: String, val tags: Map<String, String>, val paths: MutableList<List<Pair<Double, Double>>>, var point: Pair<Double, Double>?)
    val acc = LinkedHashMap<String, Acc>()
    val pinAreas = LinkedHashMap<String, Map<String, String>>()
    var nextId = -1L
    for ((z, xy, layers) in tiles) {
        val detail = z >= VectorTileRules.DETAIL_ZOOM
        for (layer in layers) {
            val ext = layer.extent.toDouble()
            fun toLatLng(p: Pair<Int, Int>) = tileToLatLng(xy.first + p.first / ext, xy.second + p.second / ext, z)
            for (f in layer.features) {
                val tags = omtTags(layer.name, f.tags, detail) ?: continue
                val paths = f.parts.filter { it.isNotEmpty() }.map { part -> part.map(::toLatLng) }
                if (paths.isEmpty()) continue
                val isArea = f.type == 3
                // Land use / land cover: only the areas the pin is in (Overpass is_in), plus wetlands as water.
                if (layer.name == "landcover" || layer.name == "landuse" || layer.name == "water") {
                    if (isArea && pinInRings(lat, lng, paths)) pinAreas.getOrPut(tags.toString()) { tags }
                    if (layer.name != "water" && tags["natural"] != "wetland") continue
                }
                val bucket = when {
                    layer.name == "transportation" -> OsmLayer.ROADS
                    else -> OsmLayer.WATER
                }
                val key = bucket + "|" + layer.name + "|" + (if (f.id != 0L) f.id.toString() else "n" + (nextId--)) + "|" + tags
                val a = acc.getOrPut(key) { Acc(bucket, tags, mutableListOf(), null) }
                if (f.type == 1) a.point = paths.first().first() else a.paths += paths
            }
        }
    }
    val out = linkedMapOf<String, MutableList<OsmElement>>(OsmLayer.WATER to mutableListOf(), OsmLayer.ROADS to mutableListOf(), OsmLayer.LANDUSE to mutableListOf())
    var id = -1L
    for (a in acc.values) {
        out.getValue(a.layer) += OsmElement("way", id--, a.tags, a.point?.first, a.point?.second, a.paths.toList(), a.paths.map { "" })
    }
    for (t in pinAreas.values) out.getValue(OsmLayer.LANDUSE) += OsmElement("area", id--, t)
    return out
}

/** Is the point inside the polygon made of [rings] (even-odd rule, so holes count as outside)? */
internal fun pinInRings(lat: Double, lng: Double, rings: List<List<Pair<Double, Double>>>): Boolean {
    var inside = false
    for (ring in rings) {
        if (ring.size < 4) continue
        var j = ring.size - 1
        for (i in ring.indices) {
            val (yi, xi) = ring[i]; val (yj, xj) = ring[j]
            if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
    }
    return inside
}

// ---------------------------------------------------------------------------------------------------------------------
// Fetching.

private var tileUrlTemplate: String? = null
private val tileCache = LinkedHashMap<String, ByteArray>()
private val tileGate = Semaphore(4)
private val cacheLock = kotlinx.coroutines.sync.Mutex()

/** The current tile URL template ("…/{z}/{x}/{y}.pbf") from OpenFreeMap's TileJSON (the path changes with each data release). */
internal fun parseTileTemplate(json: String?): String? =
    parseJsonOrNull(json)["tiles"].arr().firstOrNull()?.str()?.takeIf { it.startsWith("https://") && "{z}" in it }

private suspend fun tileTemplate(): Pair<String?, String> {
    tileUrlTemplate?.let { return it to "cached" }
    val r = httpCall(VectorTileRules.TILEJSON_URL, headers = mapOf("User-Agent" to APP_USER_AGENT, "Accept" to "application/json"), timeoutMs = 15_000)
    val t = if (r.ok) parseTileTemplate(r.body) else null
    if (t != null) tileUrlTemplate = t
    return t to (if (t != null) "ok" else if (r.code < 0) (r.error ?: "no answer") else "HTTP ${r.code}")
}

private suspend fun tileBytes(url: String): ByteArray? {
    cacheLock.withLock { tileCache[url] }?.let { return it }
    val raw = tileGate.withPermit { httpBytes(url, APP_USER_AGENT, VectorTileRules.TILE_MAX_BYTES) } ?: return null
    // A gzip answer (some servers send tiles gzipped) is unpacked here.
    val bytes = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) ungzipOrNull(raw) ?: return null else raw
    cacheLock.withLock {
        tileCache[url] = bytes
        while (tileCache.size > 40) tileCache.remove(tileCache.keys.first())
    }
    return bytes
}

private val WANTED = setOf("water", "waterway", "transportation", "landcover", "landuse")

/**
 * The CORE layers (water, roads, land use) from OpenFreeMap vector tiles for a pin, with one status line. Water and land
 * use need every detailed tile; the main roads need every wide tile (else [OsmLayer.ROADS] is reported failed).
 */
suspend fun fetchTileCore(lat: Double, lng: Double, waterRadiusM: Int): Pair<Map<String, OverpassResult>, List<SourceStatusLine>> {
    val t0 = com.hark.sothudiary.data.nowMillis()
    val (template, why) = tileTemplate()
    val label = "${VectorTileRules.SOURCE} (water, roads, land use) · openfreemap.org"
    if (template == null) {
        val err = "[other] ${VectorTileRules.SOURCE}: $why"
        return OsmGroup.layers.getValue(OsmGroup.CORE).associateWith { OverpassResult(emptyList(), err) } to
            listOf(SourceStatusLine(label, false, "tile list: $why", com.hark.sothudiary.data.nowMillis() - t0, VectorTileRules.TILEJSON_URL))
    }
    val detail = tilesAround(lat, lng, maxOf(waterRadiusM.toDouble(), 600.0), VectorTileRules.DETAIL_ZOOM)
    val wide = tilesAround(lat, lng, VectorTileRules.WIDE_RADIUS_M, VectorTileRules.WIDE_ZOOM)
    fun url(z: Int, xy: Pair<Int, Int>) = template.replace("{z}", z.toString()).replace("{x}", xy.first.toString()).replace("{y}", xy.second.toString())
    val jobs = (detail.map { VectorTileRules.DETAIL_ZOOM to it } + wide.map { VectorTileRules.WIDE_ZOOM to it })
    val got = coroutineScope {
        jobs.map { (z, xy) ->
            async {
                val b = tileBytes(url(z, xy))
                val layers = b?.let { runCatching { decodeMvt(it, WANTED) }.getOrNull() }
                Triple(z, xy, layers)
            }
        }.awaitAll()
    }
    val ms = com.hark.sothudiary.data.nowMillis() - t0
    val detailOk = got.filter { it.first == VectorTileRules.DETAIL_ZOOM }.all { it.third != null }
    val wideOk = got.filter { it.first == VectorTileRules.WIDE_ZOOM }.all { it.third != null }
    val loaded = got.mapNotNull { (z, xy, l) -> l?.let { Triple(z, xy, it) } }
    val failedErr = "[other] ${VectorTileRules.SOURCE}: some tiles did not load"
    if (!detailOk) {
        return OsmGroup.layers.getValue(OsmGroup.CORE).associateWith { OverpassResult(emptyList(), failedErr) } to
            listOf(SourceStatusLine(label, false, "${got.count { it.third == null }} of ${got.size} tiles did not load", ms, template))
    }
    val layers = tilesToLayers(loaded, lat, lng)
    val res = linkedMapOf(
        OsmLayer.WATER to OverpassResult(layers[OsmLayer.WATER].orEmpty()),
        OsmLayer.ROADS to if (wideOk) OverpassResult(layers[OsmLayer.ROADS].orEmpty()) else OverpassResult(emptyList(), failedErr),
        OsmLayer.LANDUSE to OverpassResult(layers[OsmLayer.LANDUSE].orEmpty()),
    )
    val n = res.values.sumOf { it.elements.size }
    return res to listOf(SourceStatusLine(label, true,
        "$n map features from ${loaded.size} tiles" + (if (!wideOk) " (main roads did not load)" else ""), ms,
        "z${VectorTileRules.DETAIL_ZOOM} " + detail.joinToString(" ") { "${it.first}/${it.second}" } +
            " · z${VectorTileRules.WIDE_ZOOM} " + wide.joinToString(" ") { "${it.first}/${it.second}" } + " · OpenStreetMap data"))
}
