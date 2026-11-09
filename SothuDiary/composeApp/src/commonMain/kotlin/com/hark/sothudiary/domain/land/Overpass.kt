package com.hark.sothudiary.domain.land

import com.hark.sothudiary.APP_USER_AGENT
import com.hark.sothudiary.domain.social.arr
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.str
import com.hark.sothudiary.domain.urlEncode
import com.hark.sothudiary.httpRequest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

/** Categories of mapped things the land check looks for. */
object Cat {
    const val WATER = "water"
    const val MAJOR_ROAD = "major_road"
    const val ROAD = "road"
    const val PROPOSED_ROAD = "proposed_road"
    const val RAIL = "rail"
    const val SCHOOL = "school"
    const val HOSPITAL = "hospital"
    const val BUS = "bus"
    const val MARKET = "market"
    const val POWER = "power"
    const val WATER_SUPPLY = "water_supply"
    const val LANDUSE = "landuse"
    // 2.3.0
    /** A railway line (railway=rail / light_rail / narrow_gauge). */
    const val RAIL_LINE = "rail_line"
    /** A level crossing / crossing of a railway (railway=level_crossing / crossing). */
    const val LEVEL_CROSSING = "level_crossing"
    /** Protected area, national park, sanctuary or nature reserve. */
    const val PROTECTED = "protected"
    /** Forest land (landuse=forest – reserved / protected forests are mapped this way). */
    const val FOREST = "forest"
}

/**
 * One OpenStreetMap element from an Overpass answer: its tags, a point (node / centre) and its geometry as
 * lat/lng paths (a way = one path, a relation = one path per member way). A path that comes back clipped
 * (null nodes outside the box) is split into pieces.
 */
data class OsmElement(
    val type: String,
    val id: Long,
    val tags: Map<String, String>,
    val lat: Double? = null,
    val lng: Double? = null,
    val paths: List<List<Pair<Double, Double>>> = emptyList(),
    /** Role of each path (relation members: "outer" / "inner"; ways: ""). */
    val roles: List<String> = emptyList(),
) {
    val name: String get() = tags["name:en"] ?: tags["name"] ?: tags["name:ta"] ?: ""
    /** A point for distances: the node itself, the element's centre, or its first geometry point. */
    fun anyPoint(): Pair<Double, Double>? =
        if (lat != null && lng != null) lat to lng else paths.firstOrNull { it.isNotEmpty() }?.first()
}

/** A classified element: what it is for the land check. */
data class Classified(val category: String, val kind: String, val element: OsmElement)

private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull

/** Parses an Overpass API JSON answer ({"elements":[…]}). Unknown or broken elements are skipped. */
fun parseOverpass(text: String?): List<OsmElement> {
    val root = parseJsonOrNull(text) ?: return emptyList()
    return root["elements"].arr().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val type = o["type"].str() ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
        val tags = (o["tags"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap() ?: emptyMap()
        var lat = o["lat"].dbl()
        var lng = o["lon"].dbl()
        if (lat == null || lng == null) {
            lat = o["center"]["lat"].dbl(); lng = o["center"]["lon"].dbl()
        }
        val paths = mutableListOf<List<Pair<Double, Double>>>()
        val roles = mutableListOf<String>()
        fun addGeometry(g: JsonElement?, role: String) {
            var cur = mutableListOf<Pair<Double, Double>>()
            for (pt in g.arr()) {
                val la = pt["lat"].dbl(); val ln = pt["lon"].dbl()
                if (la == null || ln == null) {
                    if (cur.isNotEmpty()) { paths += cur; roles += role }
                    cur = mutableListOf()
                } else cur += la to ln
            }
            if (cur.isNotEmpty()) { paths += cur; roles += role }
        }
        addGeometry(o["geometry"], "")
        for (m in o["members"].arr()) {
            if (m["type"].str() == "way") addGeometry(m["geometry"], m["role"].str() ?: "")
        }
        OsmElement(type, id, tags, lat, lng, paths, roles)
    }
}

private val MAJOR = Regex("^(motorway|trunk|primary|secondary)(_link)?$")
private val MINOR = Regex("^(tertiary|unclassified|residential|service|track|living_street|road)(_link)?$")

/** What an element is for the land check, or null when it is not interesting. Pure: unit-tested. */
fun classify(e: OsmElement): Classified? {
    val t = e.tags
    if (e.type == "area") {
        val lu = t["landuse"]?.let { "landuse=$it" } ?: t["natural"]?.let { "natural=$it" }
            // 2.3.0: the pin inside a protected area / nature reserve (no landuse / natural tag on it).
            ?: t["boundary"]?.takeIf { it == "protected_area" || it == "national_park" }?.let { "boundary=$it" }
            ?: t["leisure"]?.takeIf { it == "nature_reserve" }?.let { "leisure=$it" }
            ?: return null
        return Classified(Cat.LANDUSE, lu, e)
    }
    val hw = t["highway"]
    when {
        t["natural"] == "water" -> return Classified(Cat.WATER, t["water"] ?: "water", e)
        t["natural"] == "wetland" -> return Classified(Cat.WATER, "wetland", e)
        t["waterway"] in setOf("river", "riverbank", "canal", "stream", "drain") -> return Classified(Cat.WATER, t["waterway"]!!, e)
        t["landuse"] in setOf("reservoir", "basin") -> return Classified(Cat.WATER, t["landuse"]!!, e)
        hw == "construction" || hw == "proposed" -> return Classified(Cat.PROPOSED_ROAD, t["construction"] ?: t["proposed"] ?: hw, e)
        hw != null && MAJOR.matches(hw) -> return Classified(Cat.MAJOR_ROAD, hw.removeSuffix("_link"), e)
        hw == "bus_stop" || t["amenity"] == "bus_station" -> return Classified(Cat.BUS, hw ?: "bus_station", e)
        hw != null && MINOR.matches(hw) -> return Classified(Cat.ROAD, hw.removeSuffix("_link"), e)
        t["railway"] in setOf("station", "halt") -> return Classified(Cat.RAIL, t["railway"]!!, e)
        // 2.3.0: railway lines, level crossings, protected areas and forests (only asked for by the 2.3.0 layers).
        t["railway"] in setOf("rail", "light_rail", "narrow_gauge") -> return Classified(Cat.RAIL_LINE, t["railway"]!!, e)
        t["railway"] in setOf("level_crossing", "crossing") -> return Classified(Cat.LEVEL_CROSSING, t["railway"]!!, e)
        t["boundary"] in setOf("protected_area", "national_park") || t["leisure"] == "nature_reserve" ->
            return Classified(Cat.PROTECTED, protectedKind(t), e)
        t["landuse"] == "forest" || t["boundary"] == "forest" -> return Classified(Cat.FOREST, "forest", e)
    }
    val am = t["amenity"]
    return when {
        am in setOf("school", "college", "university", "kindergarten") -> Classified(Cat.SCHOOL, am!!, e)
        am in setOf("hospital", "clinic", "doctors") -> Classified(Cat.HOSPITAL, am!!, e)
        am == "marketplace" -> Classified(Cat.MARKET, am, e)
        t["shop"] in setOf("supermarket", "convenience", "general", "grocery", "greengrocer") -> Classified(Cat.MARKET, t["shop"]!!, e)
        t["power"] in setOf("line", "minor_line", "substation", "transformer") -> Classified(Cat.POWER, t["power"]!!, e)
        t["man_made"] in setOf("water_tower", "water_works", "storage_tank", "reservoir_covered", "water_well") -> Classified(Cat.WATER_SUPPLY, t["man_made"]!!, e)
        am == "drinking_water" || am == "water_point" -> Classified(Cat.WATER_SUPPLY, am, e)
        else -> null
    }
}

/** "national_park", "wildlife_sanctuary", "reserved_forest", "nature_reserve", "protected_area" (for the UI). */
fun protectedKind(t: Map<String, String>): String {
    val name = (t["name:en"] ?: t["name"] ?: "").lowercase()
    val title = t["protection_title"]?.lowercase().orEmpty()
    return when {
        t["boundary"] == "national_park" || "national park" in name || "national park" in title -> "national_park"
        "tiger" in name || "tiger" in title -> "tiger_reserve"
        "sanctuary" in name || "sanctuary" in title || t["protect_class"] == "4" -> "wildlife_sanctuary"
        "reserve forest" in name || "reserved forest" in name || "reserved forest" in title || "reserve forest" in title -> "reserved_forest"
        t["leisure"] == "nature_reserve" -> "nature_reserve"
        t["protect_class"] == "2" -> "national_park"
        else -> "protected_area"
    }
}

private fun r6(v: Double) = (v * 1_000_000).roundToLong() / 1_000_000.0

/** Bounding box "s,w,n,e" of [radiusM] around a point. */
internal fun bbox(lat: Double, lng: Double, radiusM: Double): String {
    val dLat = radiusM / 111_320.0
    val dLng = radiusM / (111_320.0 * kotlin.math.cos(lat * kotlin.math.PI / 180.0))
    return "${r6(lat - dLat)},${r6(lng - dLng)},${r6(lat + dLat)},${r6(lng + dLng)}"
}

/**
 * One Overpass query for every layer: water (full outlines, so "inside" works), major and proposed roads (5 km,
 * clipped), access roads (400 m), schools / hospitals / bus / markets (3 km), water supply (1.5 km), power (1 km),
 * and the landuse areas the pin is in.
 */
fun buildOverpassQuery(lat: Double, lng: Double, waterRadiusM: Int = 1000): String {
    val a = "${r6(lat)},${r6(lng)}"
    return """
[out:json][timeout:50];
(
  nwr(around:$waterRadiusM,$a)["natural"~"^(water|wetland)${'$'}"];
  nwr(around:$waterRadiusM,$a)["waterway"~"^(river|riverbank|canal|stream|drain)${'$'}"];
  nwr(around:$waterRadiusM,$a)["landuse"~"^(reservoir|basin)${'$'}"];
);
out tags geom;
(
  way(around:5000,$a)["highway"~"^(motorway|trunk|primary|secondary)(_link)?${'$'}"];
  way(around:5000,$a)["highway"~"^(construction|proposed)${'$'}"];
);
out tags geom(${bbox(lat, lng, 5200.0)});
way(around:400,$a)["highway"~"^(tertiary|unclassified|residential|service|track|living_street|road)(_link)?${'$'}"];
out tags geom(${bbox(lat, lng, 600.0)});
(
  nwr(around:3000,$a)["amenity"~"^(school|college|university|kindergarten|hospital|clinic|doctors|marketplace|bus_station|drinking_water|water_point)${'$'}"];
  node(around:3000,$a)["highway"="bus_stop"];
  nwr(around:3000,$a)["shop"~"^(supermarket|convenience|general|grocery|greengrocer)${'$'}"];
  nwr(around:5000,$a)["railway"~"^(station|halt)${'$'}"];
  nwr(around:1500,$a)["man_made"~"^(water_tower|water_works|storage_tank|reservoir_covered|water_well)${'$'}"];
  nwr(around:1000,$a)["power"~"^(substation|transformer)${'$'}"];
);
out tags center;
way(around:1000,$a)["power"~"^(line|minor_line)${'$'}"];
out tags geom(${bbox(lat, lng, 1200.0)});
is_in($a)->.inside;
area.inside["landuse"];
out tags;
area.inside["natural"];
out tags;
""".trim()
}

/**
 * Public Overpass servers with worldwide data (free, no key – see wiki.openstreetmap.org/wiki/Overpass_API).
 * overpass.private.coffee (formerly overpass.kumi.systems) and the VK Maps mirror state no rate limit; the main
 * overpass-api.de instance is often overloaded, so it gets the fewest first requests. overpass.osm.ch is not used:
 * it only holds Switzerland.
 */
val OVERPASS_ENDPOINTS = listOf(
    // 2.4.0: the main server first again – it is the fastest when the request carries an identifying User-Agent (a
    // 2.3 request got HTTP 406 there). From a test machine on 2026-11-08 the VK mirror answered in 16–40 s and
    // private.coffee did not answer within 40 s, so they are the hedges, not the first choice.
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
)

/** Outcome of an Overpass fetch: the elements, or a tagged error ("network" / "[other] …"). */
data class OverpassResult(val elements: List<OsmElement>, val error: String? = null)

/** 2.3.0: the land check's map data is fetched as separate, small layers so that one slow layer does not lose the rest. */
object OsmLayer {
    const val WATER = "water"
    const val ROADS = "roads"
    const val AMENITIES = "amenities"
    const val UTILITIES = "utilities"
    const val RAIL = "rail"
    const val PROTECTED = "protected"
    const val LANDUSE = "landuse"
    val ALL = listOf(WATER, ROADS, AMENITIES, UTILITIES, RAIL, PROTECTED, LANDUSE)

    /** The feature categories each layer produces (a failed layer leaves these unknown). */
    fun categories(layer: String): Set<String> = when (layer) {
        WATER -> setOf(Cat.WATER)
        ROADS -> setOf(Cat.MAJOR_ROAD, Cat.ROAD, Cat.PROPOSED_ROAD)
        AMENITIES -> setOf(Cat.SCHOOL, Cat.HOSPITAL, Cat.BUS, Cat.MARKET)
        UTILITIES -> setOf(Cat.POWER, Cat.WATER_SUPPLY)
        RAIL -> setOf(Cat.RAIL, Cat.RAIL_LINE, Cat.LEVEL_CROSSING)
        PROTECTED -> setOf(Cat.PROTECTED, Cat.FOREST)
        LANDUSE -> setOf(Cat.LANDUSE)
        else -> emptySet()
    }

    fun ofCategory(cat: String): String? = ALL.firstOrNull { cat in categories(it) }
}

/** Search radius for protected areas / sanctuaries (the default eco-sensitive zone distance is up to 10 km). */
const val PROTECTED_RADIUS_M = 10_000

/**
 * 2.3.0: the land check's Overpass queries, one per layer (same filters as [buildOverpassQuery] plus railway lines,
 * level crossings, protected areas and forests). Each asks the server for at most [timeoutSec] seconds.
 */
fun buildLayerQueries(lat: Double, lng: Double, waterRadiusM: Int = 1000, timeoutSec: Int = 25): Map<String, String> {
    val a = "${r6(lat)},${r6(lng)}"
    val head = "[out:json][timeout:$timeoutSec];"
    return linkedMapOf(
        OsmLayer.WATER to """
$head
(
  nwr(around:$waterRadiusM,$a)["natural"~"^(water|wetland)${'$'}"];
  nwr(around:$waterRadiusM,$a)["waterway"~"^(river|riverbank|canal|stream|drain)${'$'}"];
  nwr(around:$waterRadiusM,$a)["landuse"~"^(reservoir|basin)${'$'}"];
);
out tags geom;
""".trim(),
        OsmLayer.ROADS to """
$head
(
  way(around:5000,$a)["highway"~"^(motorway|trunk|primary|secondary)(_link)?${'$'}"];
  way(around:5000,$a)["highway"~"^(construction|proposed)${'$'}"];
);
out tags geom(${bbox(lat, lng, 5200.0)});
way(around:400,$a)["highway"~"^(tertiary|unclassified|residential|service|track|living_street|road)(_link)?${'$'}"];
out tags geom(${bbox(lat, lng, 600.0)});
""".trim(),
        OsmLayer.AMENITIES to """
$head
(
  nwr(around:3000,$a)["amenity"~"^(school|college|university|kindergarten|hospital|clinic|doctors|marketplace|bus_station)${'$'}"];
  node(around:3000,$a)["highway"="bus_stop"];
  nwr(around:3000,$a)["shop"~"^(supermarket|convenience|general|grocery|greengrocer)${'$'}"];
);
out tags center;
""".trim(),
        OsmLayer.UTILITIES to """
$head
(
  nwr(around:1500,$a)["man_made"~"^(water_tower|water_works|storage_tank|reservoir_covered|water_well)${'$'}"];
  nwr(around:1500,$a)["amenity"~"^(drinking_water|water_point)${'$'}"];
  nwr(around:1000,$a)["power"~"^(substation|transformer)${'$'}"];
);
out tags center;
way(around:1000,$a)["power"~"^(line|minor_line)${'$'}"];
out tags geom(${bbox(lat, lng, 1200.0)});
""".trim(),
        OsmLayer.RAIL to """
$head
nwr(around:5000,$a)["railway"~"^(station|halt)${'$'}"];
out tags center;
way(around:3000,$a)["railway"~"^(rail|light_rail|narrow_gauge)${'$'}"];
out tags geom(${bbox(lat, lng, 3200.0)});
node(around:3000,$a)["railway"~"^(level_crossing|crossing)${'$'}"];
out tags;
""".trim(),
        OsmLayer.PROTECTED to """
$head
(
  wr(around:$PROTECTED_RADIUS_M,$a)["boundary"~"^(protected_area|national_park)${'$'}"];
  wr(around:$PROTECTED_RADIUS_M,$a)["leisure"="nature_reserve"];
  wr(around:2000,$a)["landuse"="forest"];
);
out tags geom(${bbox(lat, lng, PROTECTED_RADIUS_M + 200.0)});
""".trim(),
        OsmLayer.LANDUSE to """
$head
is_in($a)->.inside;
area.inside["landuse"];
out tags;
area.inside["natural"];
out tags;
area.inside["boundary"~"^(protected_area|national_park)${'$'}"];
out tags;
area.inside["leisure"="nature_reserve"];
out tags;
""".trim(),
    )
}

/** Merged result of the layer queries: unique elements, the layers that loaded and those that failed (with the error). */
data class LayeredOsm(val elements: List<OsmElement>, val okLayers: List<String>, val failed: Map<String, String>,
                      /** 2.4.0: every server call made, for the Status / நிலை panel. */
                      val attempts: List<SourceStatusLine> = emptyList()) {
    val anyOk: Boolean get() = okLayers.isNotEmpty()
    /** One error for the whole fetch when nothing loaded (shown like before). */
    val error: String? get() = if (anyOk) null else (failed.values.firstOrNull { it != "network" } ?: failed.values.firstOrNull() ?: "network")
}

/** Joins the layer answers; an element returned by two layers is kept once (same type + id, the richer copy wins). */
fun mergeLayers(results: Map<String, OverpassResult>): LayeredOsm {
    val seen = LinkedHashMap<String, OsmElement>()
    val ok = mutableListOf<String>()
    val failed = LinkedHashMap<String, String>()
    for ((layer, r) in results) {
        if (r.error != null) { failed[layer] = r.error; continue }
        ok += layer
        for (e in r.elements) {
            val k = e.type + "/" + e.id + (if (e.id == 0L) "/" + e.tags.hashCode() + "/" + e.lat + "/" + e.lng else "")
            val prev = seen[k]
            if (prev == null || e.paths.sumOf { it.size } > prev.paths.sumOf { it.size }) seen[k] = e
        }
    }
    return LayeredOsm(seen.values.toList(), ok, failed)
}

/** Should a failed Overpass answer be tried again (on another server)? 429 / 5xx / timeouts yes, 400 (bad query) no. */
fun overpassRetryable(code: Int?): Boolean = code == null || code == 429 || code == 408 || code >= 500 || code == 0

/** Back-off before attempt [attempt] (1-based) after a busy answer: 1 s, 3 s, 6 s … (capped at 8 s). */
fun overpassBackoffMs(attempt: Int, busy: Boolean): Long = if (!busy) 0L else minOf(8_000L, 1_000L * (attempt * (attempt + 1) / 2))

/** Order of servers for layer number [index]: start on a different server per layer, then the others. */
fun endpointOrder(index: Int, endpoints: List<String> = OVERPASS_ENDPOINTS): List<String> =
    if (endpoints.isEmpty()) emptyList() else endpoints.indices.map { endpoints[(index + it) % endpoints.size] }

/** Classifies one Overpass HTTP answer: elements, or an error text and whether the server was busy. */
internal fun readOverpassAnswer(code: Int, body: String?): Pair<OverpassResult, Boolean> {
    if (code in 200..299 && body != null && body.trimStart().startsWith("{")) {
        val root = parseJsonOrNull(body)
        // Overpass reports a timeout / memory error with a 200 and a "remark".
        val remark = root["remark"].str()
        if (remark != null && remark.contains("error", true) && root["elements"].arr().isEmpty())
            return OverpassResult(emptyList(), "[other] Overpass: $remark") to true
        return OverpassResult(parseOverpass(body)) to false
    }
    val busy = code == 429 || code == 504 || code == 503 || code == 502 || (code in 200..299)
    val why = when (code) { 406 -> " (refused: User-Agent policy)"; else -> if (busy) " (server busy)" else "" }
    return OverpassResult(emptyList(), "[other] Overpass HTTP $code$why") to busy
}

private suspend fun overpassOnce(url: String, query: String, timeoutMs: Int): Pair<OverpassResult, Boolean> {
    val r = overpassCall(url, query, timeoutMs)
    if (r.code < 0) return OverpassResult(emptyList(), "network") to true
    return readOverpassAnswer(r.code, r.body)
}

/** Previous behaviour, kept for callers of the single big query: the servers one after the other. */
suspend fun fetchOverpass(query: String): OverpassResult {
    var lastError = "network"
    for (url in OVERPASS_ENDPOINTS) {
        val (res, _) = overpassOnce(url, query, 60_000)
        if (res.error == null) return res
        lastError = res.error
    }
    return OverpassResult(emptyList(), lastError)
}

/**
 * 2.4.0: the layers are sent as two combined queries instead of seven. Seven parallel queries from one phone tripped the
 * per-IP limits of the free servers (overpass-api.de gives each IP two slots and answers 429 beyond that), so most layers
 * of a check failed. CORE answers the main question (water, roads, land use at the pin); EXTRA adds amenities,
 * utilities, railway and protected areas. A failed group only marks its own layers unknown.
 */
object OsmGroup {
    const val CORE = "core"
    const val EXTRA = "extra"
    val layers: Map<String, List<String>> = linkedMapOf(
        CORE to listOf(OsmLayer.WATER, OsmLayer.ROADS, OsmLayer.LANDUSE),
        EXTRA to listOf(OsmLayer.AMENITIES, OsmLayer.UTILITIES, OsmLayer.RAIL, OsmLayer.PROTECTED),
    )
}

/** Joins layer queries (each "[out:json][timeout:N];" + statements) into one query per [OsmGroup]. */
fun groupQueries(layerQueries: Map<String, String>): Map<String, String> = OsmGroup.layers.mapValues { (_, layers) ->
    val parts = layers.mapNotNull { layerQueries[it] }
    if (parts.isEmpty()) "" else {
        val head = parts.first().lineSequence().first()
        head + "\n" + parts.joinToString("\n") { q -> q.lineSequence().drop(1).joinToString("\n") }
    }
}.filterValues { it.isNotBlank() }

/** Host name of a server URL ("overpass-api.de"). */
fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/')

/** Plain-words reason for an Overpass HTTP answer (Status / நிலை panel). */
fun overpassReason(code: Int, error: String?): String = when {
    code < 0 -> error ?: "no answer"
    code == 406 -> "HTTP 406 – server refused this app (User-Agent policy)"
    code == 429 -> "HTTP 429 – too many requests from this network, wait a minute"
    code == 504 || code == 503 || code == 502 -> "HTTP $code – server busy"
    code == 400 -> "HTTP 400 – query rejected"
    code in 200..299 -> "busy (query timed out on the server)"
    else -> "HTTP $code"
}

/** Servers that refused us (406: one hour) or asked us to slow down (429: one minute), per app session. */
private val serverPause = mutableMapOf<String, Long>()
/** One request at a time per server from this app (the free servers count slots per IP). */
private val serverGates = OVERPASS_ENDPOINTS.associateWith { kotlinx.coroutines.sync.Semaphore(1) }

private fun nowMs(): Long = com.hark.sothudiary.data.nowMillis()

/** One Overpass HTTP call with its status line. */
private suspend fun overpassAttempt(url: String, query: String, timeoutMs: Int, label: String): Pair<OverpassResult, SourceStatusLine> {
    val gate = serverGates[url]
    val r = if (gate != null) gate.withPermit { overpassCall(url, query, timeoutMs) } else overpassCall(url, query, timeoutMs)
    val host = hostOf(url)
    if (r.code == 406) serverPause[url] = nowMs() + 60 * 60_000L
    if (r.code == 429) serverPause[url] = nowMs() + 60_000L
    val (res, _) = if (r.code < 0) OverpassResult(emptyList(), "network") to true else readOverpassAnswer(r.code, r.body)
    val ok = res.error == null
    val line = SourceStatusLine(
        source = "Overpass $label · $host", ok = ok,
        reason = if (ok) "${res.elements.size} map features" else overpassReason(r.code, r.error),
        ms = r.ms, detail = "POST $url · HTTP ${r.code}" + (r.error?.let { " · $it" } ?: "") +
            (if (!ok && r.body != null) " · " + r.body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(160) else ""),
    )
    return res to line
}

/** A status line before it is turned into the saved [com.hark.sothudiary.data.SourceStatus]. */
data class SourceStatusLine(val source: String, val ok: Boolean?, val reason: String, val ms: Long = 0, val detail: String = "") {
    fun toStatus() = com.hark.sothudiary.data.SourceStatus(source, ok, reason, ms, detail)
}

private suspend fun overpassCall(url: String, query: String, timeoutMs: Int): com.hark.sothudiary.HttpResult =
    com.hark.sothudiary.httpCall(
        url, method = "POST",
        // "Accept: */*": overpass-api.de's filter also refuses some clients that only accept application/json.
        headers = mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8", "User-Agent" to APP_USER_AGENT, "Accept" to "*/*"),
        body = "data=" + urlEncode(query), timeoutMs = timeoutMs,
    )

/**
 * One group query raced across the servers: it starts on one server and, when that has not answered within [hedgeMs]
 * (or failed), also asks the next one – the first good answer wins and the others are cancelled (their sockets closed).
 * A server that refused us (406) or rate-limited us (429) is skipped for a while. When every server failed quickly and
 * time is left, the first one is asked once more after a short back-off. Each try gets at most [perTryMs].
 */
suspend fun fetchGroup(
    query: String, index: Int, label: String, budgetMs: Long = LandRules.OSM_BUDGET_MS, hedgeMs: Long = 9_000, perTryMs: Int = OVERPASS_TRY_MS,
    endpoints: List<String> = OVERPASS_ENDPOINTS,
    attempt: suspend (String, String, Int, String) -> Pair<OverpassResult, SourceStatusLine> = ::overpassAttempt,
): Pair<OverpassResult, List<SourceStatusLine>> {
    val now = nowMs()
    val all = endpointOrder(index, endpoints)
    val order = all.filter { (serverPause[it] ?: 0L) <= now }.ifEmpty { all }
    val lines = mutableListOf<SourceStatusLine>()
    var lastError = "network"
    val started = nowMs()
    val result = kotlinx.coroutines.withTimeoutOrNull(budgetMs) {
        kotlinx.coroutines.coroutineScope {
            val winner = kotlinx.coroutines.CompletableDeferred<OverpassResult>()
            val done = kotlinx.coroutines.channels.Channel<Pair<OverpassResult, SourceStatusLine>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            val jobs = mutableListOf<kotlinx.coroutines.Job>()
            fun launchOn(url: String) {
                jobs += launch {
                    val left = (budgetMs - (nowMs() - started)).toInt()
                    val ans = attempt(url, query, minOf(perTryMs, maxOf(5_000, left - 500)), label)
                    if (ans.first.error == null) winner.complete(ans.first)
                    done.trySend(ans)
                }
            }
            launchOn(order[0])
            var next = 1
            var finished = 0
            var retries = 0
            while (!winner.isCompleted) {
                val r = kotlinx.coroutines.withTimeoutOrNull(hedgeMs) { done.receive() }
                if (r != null) {
                    finished++
                    lines += r.second
                    r.first.error?.let { lastError = it }
                }
                if (winner.isCompleted) break
                if (next < order.size) launchOn(order[next++])
                else if (finished >= next) {
                    // 2.6.0: every server failed (CI probe 2026-11-08: the free mirrors answered 504 "busy" within a
                    // second or timed out). Up to [OVERPASS_RETRIES] more tries after a growing pause (3 s, then 10 s –
                    // the operators ask for a pause after a busy answer), each on the next server that has not
                    // refused us, while the time budget allows.
                    if (retries >= OVERPASS_RETRIES) break
                    val wait = if (retries == 0) 3_000L else 10_000L
                    if (budgetMs - (nowMs() - started) < wait + 6_000) break
                    kotlinx.coroutines.delay(wait)
                    val usable = order.filter { (serverPause[it] ?: 0L) <= nowMs() }.ifEmpty { order }
                    launchOn(usable[retries % usable.size]); next++
                    retries++
                }
            }
            // Lines of tries that ended together with the winner.
            while (true) { val r = done.tryReceive().getOrNull() ?: break; lines += r.second }
            val r = if (winner.isCompleted) winner.await() else null
            jobs.forEach { it.cancel() }
            r
        }
    }
    if (result == null && lines.none { it.ok == false && it.reason.isNotBlank() })
        lines += SourceStatusLine("Overpass $label", false, "no answer within ${budgetMs / 1000} s")
    return (result ?: OverpassResult(emptyList(), lastError.takeIf { it != "network" } ?: "[other] Overpass: no answer in ${budgetMs / 1000} s (server busy)")) to lines.toList()
}

/** 2.6.0: extra rounds after every server failed ([fetchGroup]). */
const val OVERPASS_RETRIES = 2

/**
 * 2.6.2: longest wait for one server's answer (was 25 s). CI probe 2026-11-08: the VK Maps mirror needed 14–18 s for
 * each single layer, so the combined (water + roads + land use) query was cut off at 25 s every time.
 */
const val OVERPASS_TRY_MS = 40_000

/** Kept for older callers: one query raced across the servers. */
suspend fun fetchLayer(query: String, index: Int, budgetMs: Long = 40_000, hedgeMs: Long = 7_000): OverpassResult =
    fetchGroup(query, index, "", budgetMs, hedgeMs).first

/**
 * All layers within one time budget, as the two [OsmGroup] queries in parallel (each starting on a different server);
 * partial results are kept: a failed group marks only its own layers as failed.
 *
 * 2.7.0: [coreFallback] (the land check passes [fetchTileCore]) is a second source for the CORE layers. When Overpass has
 * not answered the CORE group within [VectorTileRules.HEDGE_MS] (or failed sooner), the fallback is fetched; once it has
 * the data, Overpass gets [VectorTileRules.OVERPASS_GRACE_MS] more and is then stopped. Overpass's answer is still
 * preferred whenever it comes (it also has proposed roads); the fallback fills only CORE layers Overpass did not deliver.
 */
suspend fun fetchOsmLayers(
    queries: Map<String, String>, budgetMs: Long = LandRules.OSM_BUDGET_MS,
    coreFallback: (suspend () -> Pair<Map<String, OverpassResult>, List<SourceStatusLine>>)? = null,
    hedgeMs: Long = VectorTileRules.HEDGE_MS, graceMs: Long = VectorTileRules.OVERPASS_GRACE_MS,
): LayeredOsm {
    val groups = groupQueries(queries)
    val coreLabel = "(water, roads, land use)"
    var fallback: Pair<Map<String, OverpassResult>, List<SourceStatusLine>>? = null
    var coreStopped = false
    val answers = kotlinx.coroutines.coroutineScope {
        val deferred = groups.entries.mapIndexed { i, (g, q) ->
            g to async { fetchGroup(q, i, if (g == OsmGroup.CORE) coreLabel else "(amenities, rail, forest)", budgetMs) }
        }.toMap()
        val core = deferred[OsmGroup.CORE]
        var coreAns: Pair<OverpassResult, List<SourceStatusLine>>? = null
        if (coreFallback != null && core != null) {
            val early = kotlinx.coroutines.withTimeoutOrNull(hedgeMs) { core.await() }
            coreAns = early
            if (early == null || early.first.error != null) {
                fallback = try {
                    coreFallback()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    OsmGroup.layers.getValue(OsmGroup.CORE).associateWith { OverpassResult(emptyList(), "[other] ${VectorTileRules.SOURCE}: ${e.message}") } to
                        listOf(SourceStatusLine(VectorTileRules.SOURCE, false, e.message ?: "error"))
                }
                val tilesOk = fallback?.first?.values?.any { it.error == null } == true
                if (early == null) {
                    coreAns = if (tilesOk) {
                        kotlinx.coroutines.withTimeoutOrNull(graceMs) { core.await() } ?: run { core.cancel(); coreStopped = true; null }
                    } else core.await()
                }
            }
        }
        deferred.mapValues { (g, d) -> if (g == OsmGroup.CORE && coreFallback != null) coreAns else d.await() }
    }
    val perLayer = LinkedHashMap<String, OverpassResult>()
    for (g in groups.keys) {
        val ans = answers[g]
        val layers = OsmGroup.layers[g].orEmpty()
        layers.forEachIndexed { i, layer ->
            val r = when {
                ans == null -> OverpassResult(emptyList(), "[other] Overpass: stopped")
                // The group's elements are given once (to its first layer); its other layers count as loaded too.
                ans.first.error != null -> ans.first
                i == 0 -> ans.first
                else -> OverpassResult(emptyList())
            }
            val alt = fallback?.first?.get(layer)
            perLayer[layer] = if (r.error != null && alt != null && alt.error == null) alt else r
        }
    }
    val lines = answers.values.filterNotNull().flatMap { it.second } +
        (if (coreStopped) listOf(SourceStatusLine("Overpass $coreLabel", false, "stopped – the map tiles answered first")) else emptyList()) +
        fallback?.second.orEmpty()
    return mergeLayers(perLayer).copy(attempts = lines)
}

/** Open-Meteo elevation (Copernicus 90 m DEM, free, no key) for up to 100 points; null on any error. */
suspend fun fetchElevations(points: List<Pair<Double, Double>>): List<Double>? {
    if (points.isEmpty()) return emptyList()
    val url = elevationUrl(points)
    val r = httpRequest(url, headers = mapOf("User-Agent" to APP_USER_AGENT, "Accept" to "application/json"), timeoutMs = 20_000) ?: return null
    if (!r.ok) return null
    return parseElevations(r.body)?.takeIf { it.size == points.size }
}

/** Open-Meteo elevation request for the points (lat / lng lists). */
fun elevationUrl(points: List<Pair<Double, Double>>): String =
    "https://api.open-meteo.com/v1/elevation?latitude=" + points.joinToString(",") { r6(it.first).toString() } +
        "&longitude=" + points.joinToString(",") { r6(it.second).toString() }

/** {"elevation":[123.0, …]} -> the list. */
fun parseElevations(text: String?): List<Double>? =
    parseJsonOrNull(text)["elevation"].arr().map { (it as? JsonPrimitive)?.doubleOrNull ?: return null }.takeIf { it.isNotEmpty() }
