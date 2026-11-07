package com.hark.sothudiary

import com.hark.sothudiary.data.AiRoad
import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.GeoPt
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.LandAi
import com.hark.sothudiary.data.LandCheck
import com.hark.sothudiary.data.LandFeature
import com.hark.sothudiary.data.MemoryFileStore
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.land.Cat
import com.hark.sothudiary.domain.land.LandRating
import com.hark.sothudiary.domain.land.LandReason
import com.hark.sothudiary.domain.land.LocalProjection
import com.hark.sothudiary.domain.land.OsmElement
import com.hark.sothudiary.domain.land.Shape
import com.hark.sothudiary.domain.land.XY
import com.hark.sothudiary.domain.land.assembleRings
import com.hark.sothudiary.domain.land.assessLand
import com.hark.sothudiary.domain.land.boundaryTile
import com.hark.sothudiary.domain.land.buildOverpassQuery
import com.hark.sothudiary.domain.land.circleOfArea
import com.hark.sothudiary.domain.land.classify
import com.hark.sothudiary.domain.land.connectivityTile
import com.hark.sothudiary.domain.land.distText
import com.hark.sothudiary.domain.land.distToSegment
import com.hark.sothudiary.domain.land.distanceToShape
import com.hark.sothudiary.domain.land.freshLandCheck
import com.hark.sothudiary.domain.land.growthTile
import com.hark.sothudiary.domain.land.landCheckKey
import com.hark.sothudiary.domain.land.localityTile
import com.hark.sothudiary.domain.land.osmFacts
import com.hark.sothudiary.domain.land.overlapWithBuffer
import com.hark.sothudiary.domain.land.parseElevations
import com.hark.sothudiary.domain.land.parseLandAi
import com.hark.sothudiary.domain.land.parseOverpass
import com.hark.sothudiary.domain.land.plotOutline
import com.hark.sothudiary.domain.land.polygonAreaSqm
import com.hark.sothudiary.domain.land.riskTile
import com.hark.sothudiary.domain.land.sqmToCents
import com.hark.sothudiary.domain.land.utilityTile
import com.hark.sothudiary.domain.pricing.ReasonCode
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.domain.pricing.worthIt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LandCheckTest {
    private val lat0 = 9.92
    private val lng0 = 78.15
    private val proj = LocalProjection(lat0, lng0)
    private fun ll(x: Double, y: Double): Pair<Double, Double> = proj.toLatLng(XY(x, y))
    private fun near(expected: Double, actual: Double, tol: Double, msg: String = "") =
        assertTrue(abs(expected - actual) <= tol, "$msg expected $expected ± $tol but was $actual")

    private fun square(half: Double) = listOf(XY(-half, -half), XY(half, -half), XY(half, half), XY(-half, half))

    // ---------- geometry ----------

    @Test fun polygonArea() {
        near(10_000.0, polygonAreaSqm(square(50.0)), 1e-6, "100 m square")
        // The same square as lat/lng (through the projection) – within 0.5 %.
        val pts = square(50.0).map { ll(it.x, it.y) }
        near(10_000.0, polygonAreaSqm(pts), 50.0, "lat/lng square")
        // An L-shaped (concave) plot.
        val l = listOf(XY(0.0, 0.0), XY(20.0, 0.0), XY(20.0, 10.0), XY(10.0, 10.0), XY(10.0, 20.0), XY(0.0, 20.0))
        near(300.0, polygonAreaSqm(l), 1e-9, "L shape")
        // A circle of the stated area has that area.
        near(9_712.5, polygonAreaSqm(circleOfArea(XY(0.0, 0.0), 9_712.5)), 30.0, "circle of 2.4 acres")
    }

    @Test fun distances() {
        near(5.0, distToSegment(XY(0.0, 5.0), XY(-10.0, 0.0), XY(10.0, 0.0)), 1e-9)
        near(5.0, distToSegment(XY(15.0, 0.0), XY(-10.0, 0.0), XY(10.0, 0.0)), 1e-9, "beyond the end")
        val lake = Shape(rings = listOf(square(100.0)))
        assertEquals(0.0, distanceToShape(XY(0.0, 0.0), lake), "inside the lake")
        near(50.0, distanceToShape(XY(150.0, 0.0), lake), 1e-9)
        // A river mapped as a centre line, 10 m half-width: 25 m from the line = 15 m from the bank.
        val river = Shape(lines = listOf(listOf(XY(-500.0, 0.0), XY(500.0, 0.0))), lineHalfWidthM = 10.0)
        near(15.0, distanceToShape(XY(0.0, 25.0), river), 1e-9)
        assertEquals("850 m", distText(847.0))
        assertEquals("15 m", distText(15.0))
        assertEquals("1.2 km", distText(1_234.0))
        assertEquals("3 km", distText(3_000.0))
    }

    @Test fun bufferOverlap() {
        // 100 m x 100 m plot; a lake whose edge is 30 m inside the plot's east side (x = 20).
        val plot = square(50.0)
        val lake = Shape(rings = listOf(listOf(XY(20.0, -300.0), XY(400.0, -300.0), XY(400.0, 300.0), XY(20.0, 300.0))))
        val o = overlapWithBuffer(plot, listOf(lake), 15.0, 2_500)
        near(30.0, o.insideWaterPct, 1.5, "on water")
        near(45.0, o.inBufferPct, 1.5, "in water + 15 m buffer")
        assertEquals(0.0, o.nearestM)
        // Far lake: no overlap, nearest distance from the plot edge.
        val far = Shape(rings = listOf(listOf(XY(200.0, -50.0), XY(300.0, -50.0), XY(300.0, 50.0), XY(200.0, 50.0))))
        val o2 = overlapWithBuffer(plot, listOf(far), 15.0)
        assertEquals(0.0, o2.inBufferPct)
        near(150.0, o2.nearestM ?: -1.0, 1.0)
    }

    @Test fun ringAssembly() {
        // A multipolygon outer ring split into two member ways (second one reversed).
        val a = listOf(1 to 1, 2 to 1, 2 to 2)
        val b = listOf(1 to 1, 1 to 2, 2 to 2)
        val rings = assembleRings(listOf(a, b))
        assertEquals(1, rings.size)
        assertEquals(rings[0].first(), rings[0].last())
        assertEquals(5, rings[0].size)
        // An open river stays open.
        assertEquals(listOf(listOf(0 to 0, 5 to 5)), assembleRings(listOf(listOf(0 to 0, 5 to 5))))
    }

    // ---------- Overpass parsing ----------

    private fun geom(points: List<Pair<Double, Double>>) = points.joinToString(",", "[", "]") { """{"lat":${it.first},"lon":${it.second}}""" }

    /** A hand-made Overpass answer around the test plot. */
    private fun fixture(): String {
        val lake = listOf(ll(20.0, -300.0), ll(400.0, -300.0), ll(400.0, 300.0), ll(20.0, 300.0), ll(20.0, -300.0))
        val lakeHalf1 = lake.take(3)
        val lakeHalf2 = lake.drop(2)
        val nh = listOf(ll(-2_000.0, -800.0), ll(2_000.0, -800.0))
        val ring = listOf(ll(-3_000.0, 1_200.0), ll(3_000.0, 1_200.0))
        val lane = listOf(ll(-60.0, -55.0), ll(60.0, -55.0))
        val school = ll(-900.0, 300.0)
        val hosp = ll(1_500.0, -1_500.0)
        val bus = ll(0.0, -790.0)
        val shop = ll(-400.0, -600.0)
        val tf = ll(-200.0, -60.0)
        return """
        {"version":0.6,"generator":"Overpass API","elements":[
          {"type":"relation","id":11,"tags":{"type":"multipolygon","natural":"water","water":"reservoir","name":"Vandiyur Kanmoi"},
           "members":[{"type":"way","ref":1,"role":"outer","geometry":${geom(lakeHalf1)}},{"type":"way","ref":2,"role":"outer","geometry":${geom(lakeHalf2)}}]},
          {"type":"way","id":21,"tags":{"highway":"trunk","ref":"NH 44","name":"Madurai–Tuticorin Road"},"geometry":${geom(nh)}},
          {"type":"way","id":22,"tags":{"highway":"construction","construction":"primary","name":"Ring Road extension"},"geometry":${geom(ring)}},
          {"type":"way","id":23,"tags":{"highway":"residential"},"geometry":[{"lat":${lane[0].first},"lon":${lane[0].second}},null,{"lat":${lane[1].first},"lon":${lane[1].second}},{"lat":${lane[1].first},"lon":${lane[1].second + 0.0001}}]},
          {"type":"node","id":31,"lat":${school.first},"lon":${school.second},"tags":{"amenity":"school","name":"Govt Hr Sec School"}},
          {"type":"way","id":32,"center":{"lat":${hosp.first},"lon":${hosp.second}},"tags":{"amenity":"hospital","name":"PHC"}},
          {"type":"node","id":33,"lat":${bus.first},"lon":${bus.second},"tags":{"highway":"bus_stop"}},
          {"type":"node","id":34,"lat":${shop.first},"lon":${shop.second},"tags":{"shop":"convenience"}},
          {"type":"node","id":35,"lat":${tf.first},"lon":${tf.second},"tags":{"power":"transformer"}},
          {"type":"area","id":3600000001,"tags":{"landuse":"residential","name":"Ward 12"}},
          {"type":"node","id":99,"lat":9.9,"lon":78.1,"tags":{"amenity":"bench"}},
          "garbage", {"no":"type"}
        ]}
        """.trimIndent()
    }

    @Test fun parsesOverpassJson() {
        val els = parseOverpass(fixture())
        assertEquals(11, els.size, "broken elements are skipped")
        val rel = els.first { it.type == "relation" }
        assertEquals(2, rel.paths.size)
        assertEquals(listOf("outer", "outer"), rel.roles)
        assertEquals("Vandiyur Kanmoi", rel.name)
        val hosp = els.first { it.id == 32L }
        assertNotNull(hosp.lat, "out center gives a point")
        // A null node in clipped geometry splits the way.
        assertEquals(2, els.first { it.id == 23L }.paths.size)
        assertTrue(parseOverpass("not json").isEmpty())
        assertTrue(parseOverpass("""{"remark":"runtime error: timeout"}""").isEmpty())
    }

    @Test fun classifiesTags() {
        fun c(vararg t: Pair<String, String>, type: String = "way") = classify(OsmElement(type, 1, t.toMap()))
        assertEquals(Cat.WATER to "reservoir", c("natural" to "water", "water" to "reservoir")?.let { it.category to it.kind })
        assertEquals(Cat.WATER, c("waterway" to "canal")?.category)
        assertEquals(Cat.WATER, c("natural" to "wetland")?.category)
        assertNull(c("waterway" to "ditch"))
        assertEquals(Cat.MAJOR_ROAD to "primary", c("highway" to "primary_link")?.let { it.category to it.kind })
        assertEquals(Cat.PROPOSED_ROAD, c("highway" to "proposed")?.category)
        assertEquals(Cat.ROAD, c("highway" to "residential")?.category)
        assertEquals(Cat.BUS, c("highway" to "bus_stop", type = "node")?.category)
        assertEquals(Cat.SCHOOL, c("amenity" to "college")?.category)
        assertEquals(Cat.HOSPITAL, c("amenity" to "clinic")?.category)
        assertEquals(Cat.MARKET, c("shop" to "supermarket")?.category)
        assertEquals(Cat.POWER, c("power" to "minor_line")?.category)
        assertEquals(Cat.WATER_SUPPLY, c("man_made" to "water_tower")?.category)
        assertEquals(Cat.LANDUSE to "landuse=farmland", c("landuse" to "farmland", type = "area")?.let { it.category to it.kind })
        assertNull(c("amenity" to "bench"))
        val q = buildOverpassQuery(lat0, lng0)
        assertTrue(q.startsWith("[out:json]") && "is_in(" in q && "\"natural\"~\"^(water|wetland)$\"" in q, q)
    }

    @Test fun factsFromOverpass() {
        val plot = square(50.0).map { ll(it.x, it.y).let { (a, b) -> GeoPt(a, b) } }
        val f = osmFacts(parseOverpass(fixture()), lat0, lng0, plot)
        near(30.0, f.waterPct ?: -1.0, 2.0, "on the kanmoi")
        near(45.0, f.bufferPct ?: -1.0, 2.0, "in the 15 m buffer")
        assertEquals("Vandiyur Kanmoi", f.nearestWaterName)
        assertEquals("reservoir", f.nearestWaterKind)
        assertEquals(1, f.waterOutlines.size)
        assertEquals(5, f.waterOutlines[0].size, "the two member ways are joined into one outline")
        assertEquals(listOf("landuse=residential"), f.landuse)
        fun d(cat: String) = f.features.filter { it.category == cat }.minOf { it.distanceM }
        near(800.0, d(Cat.MAJOR_ROAD), 5.0, "NH")
        near(1_200.0, d(Cat.PROPOSED_ROAD), 5.0, "ring road")
        near(81.4, d(Cat.ROAD), 3.0, "lane (its middle is clipped away: nearest kept node)")
        near(949.0, d(Cat.SCHOOL), 5.0, "school")
        near(790.0, d(Cat.BUS), 5.0, "bus")
        // Pin only (no outline): the pin is 20 m from the lake edge -> outside water, inside nothing at 15 m.
        val pinOnly = osmFacts(parseOverpass(fixture()), lat0, lng0, emptyList())
        assertEquals(0.0, pinOnly.bufferPct)
        near(20.0, pinOnly.nearestWaterM ?: -1.0, 1.0)
    }

    // ---------- ratings ----------

    private val plot24 = Property(id = "p", title = "2.4 acre plot", type = PropertyType.LAND, lat = lat0, lng = lng0, size = 2.4, unit = AreaUnit.ACRES)

    private fun check(
        buffer: Double? = 0.0, water: Double? = 0.0, nearestWater: Double? = 600.0, features: List<LandFeature> = emptyList(),
        osmOk: Boolean = true, ai: LandAi? = null, landuse: List<String> = emptyList(), drawn: Boolean = false, area: Double = 9_712.5,
        elev: Double? = null, around: Double? = null,
    ) = LandCheck(
        propertyId = "p", key = landCheckKey(plot24), lat = lat0, lng = lng0, fetchedAt = nowMillis(), drawn = drawn, plotAreaSqm = area,
        osmOk = osmOk, features = features, bufferPct = buffer, waterPct = water, nearestWaterM = nearestWater, nearestWaterName = "Lake",
        landuse = landuse, ai = ai, elevationM = elev, surroundingElevationM = around,
    )

    private fun f(cat: String, m: Double, name: String = "", kind: String = "") = LandFeature(cat, kind, name, m)

    @Test fun riskThresholds() {
        assertEquals(LandRating.POOR, riskTile(check(buffer = 25.0)).rating)
        assertEquals(LandReason.IN_BUFFER, riskTile(check(buffer = 25.0)).reason)
        assertEquals(LandRating.FAIR, riskTile(check(buffer = 5.0)).rating)
        assertEquals(LandRating.POOR, riskTile(check(buffer = 40.0, water = 2.0)).rating)
        assertEquals(LandReason.PLOT_ON_WATER, riskTile(check(buffer = 40.0, water = 2.0)).reason)
        assertEquals(LandRating.POOR, riskTile(check(landuse = listOf("natural=wetland"))).rating)
        assertEquals(LandRating.FAIR, riskTile(check(nearestWater = 60.0)).rating)
        assertEquals(LandRating.FAIR, riskTile(check(elev = 140.0, around = 144.0)).rating, "low-lying")
        assertEquals(LandRating.GOOD, riskTile(check(nearestWater = 600.0)).rating)
        assertEquals(LandRating.GOOD, riskTile(check(nearestWater = null)).rating)
        assertEquals(LandRating.UNKNOWN, riskTile(check(osmOk = false)).rating)
    }

    @Test fun connectivityThresholds() {
        val lane = f(Cat.ROAD, 40.0)
        assertEquals(LandRating.GOOD, connectivityTile(check(features = listOf(f(Cat.MAJOR_ROAD, 800.0, "NH 44", "trunk"), lane))).rating)
        assertEquals(LandRating.FAIR, connectivityTile(check(features = listOf(f(Cat.MAJOR_ROAD, 2_000.0), lane))).rating)
        assertEquals(LandRating.POOR, connectivityTile(check(features = listOf(f(Cat.MAJOR_ROAD, 4_000.0), lane))).rating)
        assertEquals(LandRating.POOR, connectivityTile(check(features = listOf(lane))).rating)
        // A new road 1.2 km away lifts it one step.
        val withNew = connectivityTile(check(features = listOf(f(Cat.MAJOR_ROAD, 2_000.0), lane, f(Cat.PROPOSED_ROAD, 1_200.0, "Ring road"))))
        assertEquals(LandRating.GOOD, withNew.rating)
        assertTrue(withNew.notes.any { it.code == "proposed" && it.args.contains("1.2 km") })
        // An AI-reported proposed road counts too (and marks the tile as AI).
        val ai = LandAi(roads = listOf(AiRoad("Outer ring road", "proposed", 1.5, "https://example.org/x")))
        val viaAi = connectivityTile(check(features = listOf(f(Cat.MAJOR_ROAD, 2_000.0), lane), ai = ai))
        assertEquals(LandRating.GOOD, viaAi.rating)
        assertTrue(viaAi.ai)
        // No road at the plot: a strong main road is only moderate.
        assertEquals(LandRating.FAIR, connectivityTile(check(features = listOf(f(Cat.MAJOR_ROAD, 700.0)))).rating)
        assertEquals(LandRating.UNKNOWN, connectivityTile(check(osmOk = false)).rating)
    }

    @Test fun localityUtilityBoundaryGrowth() {
        val good = check(features = listOf(f(Cat.SCHOOL, 900.0), f(Cat.HOSPITAL, 2_900.0), f(Cat.BUS, 300.0)))
        assertEquals(LandRating.GOOD, localityTile(good).rating)
        assertEquals(LandRating.FAIR, localityTile(check(features = listOf(f(Cat.SCHOOL, 900.0), f(Cat.BUS, 300.0)))).rating)
        assertEquals(LandRating.POOR, localityTile(check(features = listOf(f(Cat.BUS, 300.0)))).rating)
        assertEquals(LandReason.NO_AMENITIES, localityTile(check()).reason)

        assertEquals(LandRating.GOOD, utilityTile(check(features = listOf(f(Cat.POWER, 100.0), f(Cat.WATER_SUPPLY, 700.0))), emptySet()).rating)
        assertEquals(LandRating.FAIR, utilityTile(check(features = listOf(f(Cat.POWER, 100.0))), emptySet()).rating)
        assertEquals(LandRating.UNKNOWN, utilityTile(check(features = listOf(f(Cat.POWER, 900.0))), emptySet()).rating, "too far")
        assertEquals(LandRating.GOOD, utilityTile(check(), setOf(DocItem.ELECTRICITY, DocItem.WATER)).rating, "user's own checklist")

        assertEquals(LandReason.NOT_VERIFIED, boundaryTile(check(), plot24).reason)
        assertEquals(LandRating.GOOD, boundaryTile(check(), plot24.copy(docs = setOf(DocItem.FMB_SKETCH, DocItem.PATTA))).rating)
        assertEquals(LandRating.FAIR, boundaryTile(check(drawn = true, area = 9_900.0), plot24).rating)
        val mismatch = boundaryTile(check(drawn = true, area = 12_000.0), plot24)
        assertEquals(LandReason.AREA_MISMATCH, mismatch.reason)
        assertEquals("+24%", mismatch.args[2])

        assertEquals(LandRating.GOOD, growthTile(check(ai = LandAi(growth = "high", growthReason = "emerging corridor")), true).rating)
        assertEquals(LandRating.FAIR, growthTile(check(ai = LandAi(growth = "moderate")), true).rating)
        assertEquals(LandReason.GROWTH_NO_KEY, growthTile(check(), false).reason)
        assertEquals(LandRating.UNKNOWN, growthTile(check(ai = LandAi(growth = "unknown")), true).rating)
    }

    @Test fun bannerUsableArea() {
        // The screenshot's case: 2.4 acres, 25 % in the lake buffer -> you could build on about 1.8 acres.
        val a = assessLand(check(buffer = 25.0), plot24, hasKey = false)
        assertEquals(LandRating.POOR, a.banner.level)
        near(240.0, a.banner.paidCents ?: 0.0, 0.01)
        near(180.0, a.banner.usableCents ?: 0.0, 0.01)
        assertEquals(6, a.tiles.size)
        near(240.0, sqmToCents(9_712.5), 0.05)
    }

    // ---------- AI reply ----------

    @Test fun parsesAiReply() {
        val text = """
            Here is what I found:
            ```json
            {"zoning":{"value":"Residential","plan":"Madurai LPA Master Plan","note":"primary residential","source_url":"https://dtcp.tn.gov.in/x"},
             "roads":[{"name":"Madurai outer ring road","status":"proposed","distance_km":"1.2","date":"2025","source_url":"https://news.example/ring"},
                      {"name":"Some road","status":"announced","distance_km":3,"source_url":""},
                      {"name":"unknown","status":"","distance_km":null,"source_url":"https://x"}],
             "growth":{"rating":"high","reason":"emerging corridor","source_url":"https://news.example/g"}}
            ```
        """.trimIndent()
        val ai = parseLandAi(text)
        assertNotNull(ai)
        assertEquals("residential", ai.zoning)
        assertEquals(1, ai.roads.size, "unsourced / unknown roads are dropped")
        assertEquals(1.2, ai.roads[0].distanceKm)
        assertEquals("high", ai.growth)
        // Unsourced claims become "unknown".
        val unsourced = parseLandAi("""{"zoning":{"value":"industrial","source_url":""},"roads":[],"growth":{"rating":"high","source_url":null}}""")
        assertEquals("unknown", unsourced?.zoning)
        assertEquals("unknown", unsourced?.growth)
        assertNull(parseLandAi("Sorry, I could not find anything."))
        assertEquals(listOf(120.0, 118.5), parseElevations("""{"elevation":[120.0,118.5]}"""))
        assertNull(parseElevations("""{"error":true}"""))
    }

    // ---------- data compatibility & integration ----------

    @Test fun oldDataStillLoads() {
        // A v2.1.2 file: no boundary, no landChecks.
        val old = """{"version":2,"properties":[{"id":"a","title":"Old plot","lat":9.9,"lng":78.1,"size":5.0}],"settings":{"lang":"TA"}}"""
        val store = MemoryFileStore().apply { write(old) }
        val repo = JsonRepository(store)
        assertEquals(1, repo.data.properties.size)
        assertTrue(repo.data.properties[0].boundary.isEmpty())
        assertTrue(repo.data.landChecks.isEmpty())
        // New fields survive a save / load round trip.
        repo.setBoundary("a", listOf(GeoPt(9.9, 78.1), GeoPt(9.9001, 78.1), GeoPt(9.9001, 78.1001)))
        repo.saveLandCheck(check().copy(propertyId = "a", key = "k"))
        val again = JsonRepository(store)
        assertEquals(3, again.data.properties[0].boundary.size)
        assertEquals(1, again.data.landChecks.size)
        again.deleteProperty("a")
        assertTrue(again.data.landChecks.isEmpty(), "deleting a property removes its land check")
    }

    @Test fun worthItUnchangedWithoutLandCheck() {
        val data = AppData(properties = listOf(plot24.copy(askingPrice = 5_000_000)))
        val checks = checkAll(data)
        val p = data.properties[0]
        val before = worthIt(p, checks[p.id]!!, data, checks, nowMillis())
        val withSafe = worthIt(p, checks[p.id]!!, data, checks, nowMillis(), assessLand(check(nearestWater = 900.0), p, false))
        assertEquals(before, withSafe, "a safe land check adds nothing")
        val withRisk = worthIt(p, checks[p.id]!!, data, checks, nowMillis(), assessLand(check(buffer = 25.0), p, false))
        assertTrue(withRisk.reasons.any { it.code == ReasonCode.LAND_RISK_HIGH })
        assertFalse(before.reasons.any { it.code == ReasonCode.LAND_RISK_HIGH })
    }

    @Test fun cacheKeyAndOutline() {
        val k = landCheckKey(plot24)
        assertTrue(k != landCheckKey(plot24.copy(boundary = listOf(GeoPt(9.92, 78.15)))), "corners change the key")
        assertTrue(k != landCheckKey(plot24.copy(size = 3.0)), "size changes the key")
        val data = AppData(properties = listOf(plot24), landChecks = listOf(check()))
        assertNotNull(freshLandCheck(data, plot24))
        assertNull(freshLandCheck(data, plot24.copy(lat = 9.93)))
        assertNull(freshLandCheck(data, plot24, now = nowMillis() + 31L * 86_400_000))
        // Without corners: a circle of the stated area around the pin.
        val (circle, drawn) = plotOutline(plot24)!!
        assertFalse(drawn)
        near(9_712.5, polygonAreaSqm(circle.map { it.lat to it.lng }), 40.0)
        // Corners far from the pin (the pin was moved) are ignored.
        val far = plot24.copy(boundary = listOf(GeoPt(10.5, 78.15), GeoPt(10.5, 78.16), GeoPt(10.51, 78.16)))
        assertFalse(plotOutline(far)!!.second)
        assertNull(plotOutline(plot24.copy(size = 0.0)))
    }
}
