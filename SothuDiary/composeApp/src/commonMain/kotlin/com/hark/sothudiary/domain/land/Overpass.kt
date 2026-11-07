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
        val lu = t["landuse"]?.let { "landuse=$it" } ?: t["natural"]?.let { "natural=$it" } ?: return null
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

/** Public Overpass servers (free, no key). The main one first; the others when it is busy. */
val OVERPASS_ENDPOINTS = listOf(
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
)

/** Outcome of an Overpass fetch: the elements, or a tagged error ("network" / "[other] …"). */
data class OverpassResult(val elements: List<OsmElement>, val error: String? = null)

suspend fun fetchOverpass(query: String): OverpassResult {
    var lastError = "network"
    for (url in OVERPASS_ENDPOINTS) {
        val r = httpRequest(
            url, method = "POST",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "User-Agent" to APP_USER_AGENT, "Accept" to "application/json"),
            body = "data=" + urlEncode(query), timeoutMs = 60_000,
        )
        if (r == null) { lastError = "network"; continue }
        val body = r.body
        if (r.ok && body != null && body.trimStart().startsWith("{")) {
            val root = parseJsonOrNull(body)
            // Overpass reports a timeout / memory error with a 200 and a "remark".
            val remark = root["remark"].str()
            if (remark != null && remark.contains("error", true) && root["elements"].arr().isEmpty()) { lastError = "[other] Overpass: $remark"; continue }
            return OverpassResult(parseOverpass(body))
        }
        lastError = "[other] Overpass HTTP ${r.code}" + (if (r.code == 429 || r.code == 504) " (server busy)" else "")
    }
    return OverpassResult(emptyList(), lastError)
}

/** Open-Meteo elevation (Copernicus 90 m DEM, free, no key) for up to 100 points; null on any error. */
suspend fun fetchElevations(points: List<Pair<Double, Double>>): List<Double>? {
    if (points.isEmpty()) return emptyList()
    val url = "https://api.open-meteo.com/v1/elevation?latitude=" + points.joinToString(",") { r6(it.first).toString() } +
        "&longitude=" + points.joinToString(",") { r6(it.second).toString() }
    val r = httpRequest(url, headers = mapOf("User-Agent" to APP_USER_AGENT, "Accept" to "application/json"), timeoutMs = 20_000) ?: return null
    if (!r.ok) return null
    return parseElevations(r.body)?.takeIf { it.size == points.size }
}

/** {"elevation":[123.0, …]} -> the list. */
fun parseElevations(text: String?): List<Double>? =
    parseJsonOrNull(text)["elevation"].arr().map { (it as? JsonPrimitive)?.doubleOrNull ?: return null }.takeIf { it.isNotEmpty() }
