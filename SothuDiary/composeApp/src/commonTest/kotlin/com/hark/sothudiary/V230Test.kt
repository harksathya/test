package com.hark.sothudiary

import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.GeoPt
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.LandAi
import com.hark.sothudiary.data.LandCheck
import com.hark.sothudiary.data.LandFeature
import com.hark.sothudiary.data.LulcHit
import com.hark.sothudiary.data.MemoryFileStore
import com.hark.sothudiary.data.AiRoad
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.SketchOverlay
import com.hark.sothudiary.data.WebSnippet
import com.hark.sothudiary.domain.land.Bhuvan
import com.hark.sothudiary.domain.land.Cat
import com.hark.sothudiary.domain.land.LandRating
import com.hark.sothudiary.domain.land.LandReason
import com.hark.sothudiary.domain.land.LandRules
import com.hark.sothudiary.domain.land.LandTile
import com.hark.sothudiary.domain.land.LocalProjection
import com.hark.sothudiary.domain.land.LulcKind
import com.hark.sothudiary.domain.land.OsmLayer
import com.hark.sothudiary.domain.land.OverpassResult
import com.hark.sothudiary.domain.land.PlotEdit
import com.hark.sothudiary.domain.land.XY
import com.hark.sothudiary.domain.land.assessLand
import com.hark.sothudiary.domain.land.buildLayerQueries
import com.hark.sothudiary.domain.land.classify
import com.hark.sothudiary.domain.land.connectivityTile
import com.hark.sothudiary.domain.land.ecoTile
import com.hark.sothudiary.domain.land.endpointOrder
import com.hark.sothudiary.domain.land.freshLandCheck
import com.hark.sothudiary.domain.land.keepEarlierLayers
import com.hark.sothudiary.domain.land.landCheckKey
import com.hark.sothudiary.domain.land.lulcKind
import com.hark.sothudiary.domain.land.lulcLabel
import com.hark.sothudiary.domain.land.mergeLayers
import com.hark.sothudiary.domain.land.moveSketch
import com.hark.sothudiary.domain.land.onlyCitedResults
import com.hark.sothudiary.domain.land.osmFacts
import com.hark.sothudiary.domain.land.overpassBackoffMs
import com.hark.sothudiary.domain.land.overpassRetryable
import com.hark.sothudiary.domain.land.parseOverpass
import com.hark.sothudiary.domain.land.parseSurveyNumber
import com.hark.sothudiary.domain.land.normalizeSurveyNumber
import com.hark.sothudiary.domain.land.polygonAreaSqm
import com.hark.sothudiary.domain.land.railwayTile
import com.hark.sothudiary.domain.land.readOverpassAnswer
import com.hark.sothudiary.domain.land.riskTile
import com.hark.sothudiary.domain.land.sketchCorners
import com.hark.sothudiary.domain.pricing.QuotaWindow
import com.hark.sothudiary.domain.pricing.groundedModelOrder
import com.hark.sothudiary.domain.pricing.nextPacificMidnight
import com.hark.sothudiary.domain.pricing.parseGeminiError
import com.hark.sothudiary.domain.pricing.priceSearchQueries
import com.hark.sothudiary.domain.pricing.quotaSkipUntil
import com.hark.sothudiary.domain.pricing.quotaWindow
import com.hark.sothudiary.domain.pricing.isQuotaError
import com.hark.sothudiary.domain.share.ImportMode
import com.hark.sothudiary.domain.share.ShareLink
import com.hark.sothudiary.domain.share.ShareOptions
import com.hark.sothudiary.domain.share.base64UrlDecode
import com.hark.sothudiary.domain.share.base64UrlEncode
import com.hark.sothudiary.domain.share.decodeShare
import com.hark.sothudiary.domain.share.encodeShare
import com.hark.sothudiary.domain.share.extractShareCode
import com.hark.sothudiary.domain.share.mergeShared
import com.hark.sothudiary.domain.share.sharedCopy
import com.hark.sothudiary.domain.web.citesResult
import com.hark.sothudiary.domain.web.isBlockedPage
import com.hark.sothudiary.domain.web.parseBingRss
import com.hark.sothudiary.domain.web.parseDuckDuckGoHtml
import com.hark.sothudiary.domain.web.parseDuckDuckGoLite
import com.hark.sothudiary.domain.web.percentDecode
import com.hark.sothudiary.domain.web.promptWithResults
import com.hark.sothudiary.domain.web.relevantOnly
import com.hark.sothudiary.domain.web.unwrapDuckLink
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.3.0: corner editing, layered Overpass, railway / protected areas, Bhuvan, web search fallback, share links. */
class V230Test {
    private val lat0 = 9.0202
    private val lng0 = 77.2530
    private val proj = LocalProjection(lat0, lng0)
    private fun g(x: Double, y: Double) = proj.toLatLng(XY(x, y)).let { (a, b) -> GeoPt(a, b) }
    private fun near(expected: Double, actual: Double, tol: Double, msg: String = "") =
        assertTrue(abs(expected - actual) <= tol, "$msg expected $expected ± $tol but was $actual")

    // ---------------- corner editing ----------------

    private val square = listOf(g(0.0, 0.0), g(40.0, 0.0), g(40.0, 40.0), g(0.0, 40.0))

    @Test fun moveCornerRecomputesArea() {
        near(1_600.0, PlotEdit.areaSqm(square), 5.0, "40 m square")
        // Dragging corner 2 out by 20 m east: a trapezoid of (40 + 60) / 2 * 40 = 2 000 m².
        val moved = PlotEdit.move(square, 2, g(60.0, 40.0))
        near(2_000.0, PlotEdit.areaSqm(moved), 6.0, "after the drag")
        assertEquals(square[0], moved[0])
        assertEquals(square, PlotEdit.move(square, 9, g(1.0, 1.0)), "an unknown corner changes nothing")
    }

    @Test fun deleteAndSelect() {
        val d = PlotEdit.delete(square, 1)
        assertEquals(3, d.size)
        assertEquals(listOf(square[0], square[2], square[3]), d)
        near(800.0, PlotEdit.areaSqm(d), 4.0, "triangle")
        assertEquals(0, PlotEdit.selectionAfterDelete(1, 1, 3))
        assertEquals(0, PlotEdit.selectionAfterDelete(0, 0, 3))
        assertNull(PlotEdit.selectionAfterDelete(0, 0, 0))
        assertEquals(0.0, PlotEdit.areaSqm(PlotEdit.delete(d, 0)), "two corners: no area")
    }

    @Test fun midpointsAndInsert() {
        val mids = PlotEdit.midpoints(square)
        assertEquals(4, mids.size, "the closing edge has a handle too")
        near(20.0, proj.toXY(mids[0].lat, mids[0].lng).x, 0.01)
        near(20.0, proj.toXY(mids[3].lat, mids[3].lng).y, 0.01, "closing edge 3 -> 0")
        assertEquals(1, PlotEdit.midpoints(square.take(2)).size)
        assertTrue(PlotEdit.midpoints(square.take(1)).isEmpty())
        // Insert on edge 0 (between corner 0 and 1), pulled 10 m south: area grows by 40 * 10 / 2.
        val (list, at) = PlotEdit.insertAfter(square, 0, g(20.0, -10.0))
        assertEquals(1, at)
        assertEquals(5, list.size)
        near(1_800.0, PlotEdit.areaSqm(list), 6.0)
        // A midpoint drag inserts once, then moves the inserted corner.
        val (d1, i1) = PlotEdit.dragMid(square, 3, g(-5.0, 20.0), null)
        assertEquals(4, i1); assertEquals(5, d1.size)
        val (d2, i2) = PlotEdit.dragMid(d1, 3, g(-10.0, 20.0), i1)
        assertEquals(i1, i2); assertEquals(5, d2.size)
        near(1_800.0, PlotEdit.areaSqm(d2), 6.0, "40 x 40 + 40 x 10 / 2")
        assertEquals("c3", PlotEdit.cornerId(3)); assertEquals(3, PlotEdit.cornerIndex("c3")); assertEquals(2, PlotEdit.midIndex("m2"))
        assertNull(PlotEdit.cornerIndex("m2")); assertNull(PlotEdit.midIndex("pin"))
    }

    @Test fun sketchPlacement() {
        val s = SketchOverlay("fmb.jpg", lat0, lng0, widthM = 100.0, rotationDeg = 0.0, aspect = 0.5)
        val c = sketchCorners(s).map { proj.toXY(it.lat, it.lng) }
        near(-50.0, c[0].x, 0.05); near(25.0, c[0].y, 0.05, "top-left")
        near(50.0, c[2].x, 0.05); near(-25.0, c[2].y, 0.05, "bottom-right")
        // Turned 90° clockwise: the top-left corner goes to the top-right quadrant.
        val r = sketchCorners(s.copy(rotationDeg = 90.0)).map { proj.toXY(it.lat, it.lng) }
        near(25.0, r[0].x, 0.05); near(50.0, r[0].y, 0.05)
        near(hypot(50.0, 25.0), hypot(r[1].x, r[1].y), 0.05, "same size")
        val m = moveSketch(s, 10.0, -5.0)
        val mc = proj.toXY(m.lat, m.lng)
        near(10.0, mc.x, 0.01); near(-5.0, mc.y, 0.01)
    }

    // ---------------- layered Overpass ----------------

    @Test fun layerQueries() {
        val q = buildLayerQueries(lat0, lng0)
        assertEquals(OsmLayer.ALL, q.keys.toList())
        q.values.forEach { assertTrue(it.startsWith("[out:json][timeout:25];"), it) }
        assertTrue("\"natural\"~\"^(water|wetland)$\"" in q.getValue(OsmLayer.WATER))
        assertTrue("level_crossing" in q.getValue(OsmLayer.RAIL) && "railway\"~\"^(rail|light_rail|narrow_gauge)$\"" in q.getValue(OsmLayer.RAIL))
        assertTrue("protected_area" in q.getValue(OsmLayer.PROTECTED) && "around:10000" in q.getValue(OsmLayer.PROTECTED))
        assertTrue("is_in(" in q.getValue(OsmLayer.LANDUSE))
        assertTrue(q.values.none { "is_in(" in it && it != q.getValue(OsmLayer.LANDUSE) })
        // Each layer starts on a different server.
        assertEquals(endpointOrder(0).drop(1).first(), endpointOrder(1).first())
        assertEquals(3, endpointOrder(2).distinct().size)
    }

    private fun way(id: Long, tags: String, pts: List<GeoPt>) =
        """{"type":"way","id":$id,"tags":{$tags},"geometry":[${pts.joinToString(",") { """{"lat":${it.lat},"lon":${it.lng}}""" }}]}"""

    private fun answer(vararg elements: String) = """{"elements":[${elements.joinToString(",")}]}"""

    @Test fun mergeKeepsPartialResults() {
        val lake = way(1, "\"natural\":\"water\",\"name\":\"Panpoli kulam\"", listOf(g(30.0, -20.0), g(80.0, -20.0), g(80.0, 30.0), g(30.0, 30.0), g(30.0, -20.0)))
        val rail = way(2, "\"railway\":\"rail\",\"name\":\"Sengottai line\"", listOf(g(-500.0, 150.0), g(500.0, 150.0)))
        val results = mapOf(
            OsmLayer.WATER to OverpassResult(parseOverpass(answer(lake))),
            OsmLayer.ROADS to OverpassResult(emptyList(), "[other] Overpass HTTP 504 (server busy)"),
            OsmLayer.RAIL to OverpassResult(parseOverpass(answer(rail, lake))),
        )
        val m = mergeLayers(results)
        assertEquals(listOf(OsmLayer.WATER, OsmLayer.RAIL), m.okLayers)
        assertEquals(setOf(OsmLayer.ROADS), m.failed.keys)
        assertEquals(2, m.elements.size, "the lake returned twice is kept once")
        assertTrue(m.anyOk); assertNull(m.error)
        val none = mergeLayers(mapOf(OsmLayer.WATER to OverpassResult(emptyList(), "network")))
        assertFalse(none.anyOk); assertEquals("network", none.error)

        val f = osmFacts(m.elements, lat0, lng0, square)
        near(150.0, f.features.first { it.category == Cat.RAIL_LINE }.distanceM, 1.0, "railway line")
        val c = LandCheck("p", "k", lat0, lng0, 0L, plot = square, osmOk = true, features = f.features, bufferPct = f.bufferPct,
            waterPct = f.waterPct, nearestWaterM = f.nearestWaterM, nearestWaterName = f.nearestWaterName, landuse = f.landuse,
            osmFailedLayers = m.failed.keys.toList(), layersVersion = LandRules.LAYERS_VERSION)
        val a = assessLand(c, Property("p", "x", lat = lat0, lng = lng0), hasKey = false)
        assertEquals(LandRating.UNKNOWN, a.tile(LandTile.CONNECTIVITY).rating, "roads failed")
        assertEquals(LandReason.LAYER_FAILED, a.tile(LandTile.CONNECTIVITY).reason)
        assertEquals(LandRating.POOR, a.tile(LandTile.RISK).rating, "the water layer loaded: the plot touches the kulam")
        assertEquals(LandRating.FAIR, a.tile(LandTile.RAILWAY).rating, "150 m from the line")
        assertEquals(6, a.tiles.size); assertEquals(2, a.extra.size); assertEquals(8, a.allTiles.size)
    }

    @Test fun retryPolicy() {
        assertTrue(overpassRetryable(429)); assertTrue(overpassRetryable(504)); assertTrue(overpassRetryable(null))
        assertFalse(overpassRetryable(400))
        assertEquals(1_000L, overpassBackoffMs(1, true)); assertEquals(3_000L, overpassBackoffMs(2, true)); assertEquals(8_000L, overpassBackoffMs(9, true))
        assertEquals(0L, overpassBackoffMs(3, false))
        val (busy, isBusy) = readOverpassAnswer(429, "rate limited")
        assertNotNull(busy.error); assertTrue(isBusy)
        val (remark, rb) = readOverpassAnswer(200, """{"elements":[],"remark":"runtime error: Query timed out in \"query\" at line 3 after 26 seconds."}""")
        assertTrue(remark.error!!.contains("timed out")); assertTrue(rb)
        val (ok, ob) = readOverpassAnswer(200, answer(way(5, "\"highway\":\"primary\"", listOf(g(0.0, 0.0), g(10.0, 0.0)))))
        assertNull(ok.error); assertEquals(1, ok.elements.size); assertFalse(ob)
    }

    @Test fun keepsEarlierLayersOnFailure() {
        val prev = LandCheck("p", "k", lat0, lng0, 1L, osmOk = true, bufferPct = 12.0, waterPct = 0.0, nearestWaterM = 5.0, nearestWaterName = "Old tank",
            features = listOf(LandFeature(Cat.WATER, "reservoir", "Old tank", 5.0), LandFeature(Cat.MAJOR_ROAD, "trunk", "NH 744", 300.0)), layersVersion = 2)
        val now = LandCheck("p", "k", lat0, lng0, 2L, osmOk = true, features = listOf(LandFeature(Cat.MAJOR_ROAD, "trunk", "NH 744", 290.0)),
            osmFailedLayers = listOf(OsmLayer.WATER, OsmLayer.RAIL), layersVersion = 2)
        val merged = keepEarlierLayers(now, prev)
        assertEquals(12.0, merged.bufferPct); assertEquals("Old tank", merged.nearestWaterName)
        assertEquals(listOf(OsmLayer.WATER, OsmLayer.RAIL), merged.osmKeptLayers)
        assertTrue(merged.osmFailedLayers.isEmpty())
        assertEquals(290.0, merged.features.first { it.category == Cat.MAJOR_ROAD }.distanceM, "loaded layers are the new ones")
        // A 2.2.x check never had railway data: nothing to keep for it.
        val fromOld = keepEarlierLayers(now, prev.copy(layersVersion = 0))
        assertEquals(listOf(OsmLayer.RAIL), fromOld.osmFailedLayers)
        assertEquals(now, keepEarlierLayers(now, null))
    }

    @Test fun partialCheckIsRetried() {
        val p = Property("p", "x", lat = lat0, lng = lng0, size = 10.0, unit = AreaUnit.CENTS)
        val t = 1_000_000_000L
        val c = LandCheck("p", landCheckKey(p), lat0, lng0, t, osmOk = true, osmFailedLayers = listOf(OsmLayer.ROADS))
        val data = com.hark.sothudiary.data.AppData(properties = listOf(p), landChecks = listOf(c))
        assertNotNull(freshLandCheck(data, p, t + 60_000), "not again within 10 minutes")
        assertNull(freshLandCheck(data, p, t + 11 * 60_000), "tried again later")
        val full = data.copy(landChecks = listOf(c.copy(osmFailedLayers = emptyList())))
        assertNotNull(freshLandCheck(full, p, t + 11 * 60_000), "a complete check stays for 30 days")
    }

    // ---------------- railway / protected areas / Bhuvan ----------------

    private fun c(vararg kv: Pair<String, String>, type: String = "way") =
        classify(com.hark.sothudiary.domain.land.OsmElement(type, 1, kv.toMap()))

    @Test fun classifiesNewLayers() {
        assertEquals(Cat.RAIL_LINE, c("railway" to "rail")?.category)
        assertEquals(Cat.LEVEL_CROSSING, c("railway" to "level_crossing", type = "node")?.category)
        assertEquals(Cat.RAIL, c("railway" to "station", type = "node")?.category, "stations unchanged")
        assertEquals(Cat.PROTECTED to "wildlife_sanctuary", c("boundary" to "protected_area", "name" to "Srivilliputhur Grizzled Squirrel Wildlife Sanctuary")?.let { it.category to it.kind })
        assertEquals("tiger_reserve", c("boundary" to "protected_area", "name" to "Kalakad Mundanthurai Tiger Reserve")?.kind)
        assertEquals("national_park", c("boundary" to "national_park", "name" to "Guindy")?.kind)
        assertEquals("reserved_forest", c("boundary" to "protected_area", "name" to "Courtallam Reserve Forest")?.kind)
        assertEquals(Cat.FOREST, c("landuse" to "forest")?.category)
        assertEquals(Cat.LANDUSE to "boundary=protected_area", c("boundary" to "protected_area", type = "area")?.let { it.category to it.kind })
        assertEquals(Cat.LANDUSE to "landuse=forest", c("landuse" to "forest", type = "area")?.let { it.category to it.kind }, "areas keep the 2.2 landuse")
        assertEquals(Cat.WATER, c("natural" to "water")?.category, "water still first")
    }

    private fun check(vararg f: LandFeature, version: Int = 2, failed: List<String> = emptyList(), lulc: List<LulcHit> = emptyList()) =
        LandCheck("p", "k", lat0, lng0, 0L, osmOk = true, features = f.toList(), layersVersion = version, osmFailedLayers = failed, lulc = lulc, bufferPct = 0.0, waterPct = 0.0)

    @Test fun railwayTileRatings() {
        assertEquals(LandRating.POOR, railwayTile(check(LandFeature(Cat.RAIL_LINE, "rail", "Sengottai line", 30.0))).rating)
        assertEquals(LandReason.RAIL_NOC, railwayTile(check(LandFeature(Cat.RAIL_LINE, "rail", "", 30.0))).reason)
        assertEquals(LandRating.FAIR, railwayTile(check(LandFeature(Cat.RAIL_LINE, "rail", "", 150.0))).rating)
        val far = railwayTile(check(LandFeature(Cat.RAIL_LINE, "rail", "", 900.0), LandFeature(Cat.LEVEL_CROSSING, "level_crossing", "", 800.0), LandFeature(Cat.RAIL, "station", "Tenkasi Jn", 3_000.0)))
        assertEquals(LandRating.GOOD, far.rating); assertEquals(LandReason.RAIL_CLEAR, far.reason)
        assertEquals(listOf("crossing", "station"), far.notes.map { it.code })
        assertEquals(LandReason.NO_RAIL, railwayTile(check()).reason)
        assertEquals(LandReason.NOT_CHECKED, railwayTile(check(version = 0)).reason, "a 2.2 check: refresh")
        assertEquals(LandReason.LAYER_FAILED, railwayTile(check(failed = listOf(OsmLayer.RAIL))).reason)
    }

    @Test fun ecoTileRatings() {
        fun pa(kind: String, d: Double) = LandFeature(Cat.PROTECTED, kind, "KMTR", d)
        assertEquals(LandReason.INSIDE_PROTECTED, ecoTile(check(pa("tiger_reserve", 0.0))).reason)
        assertEquals(LandRating.POOR, ecoTile(check(pa("wildlife_sanctuary", 800.0))).rating)
        assertEquals(LandReason.ESZ_LIKELY, ecoTile(check(pa("wildlife_sanctuary", 800.0))).reason)
        assertEquals(LandReason.ESZ_POSSIBLE, ecoTile(check(pa("national_park", 6_000.0))).reason)
        assertEquals(LandRating.FAIR, ecoTile(check(pa("national_park", 6_000.0))).rating)
        assertEquals(LandReason.NEAR_FOREST, ecoTile(check(LandFeature(Cat.FOREST, "forest", "", 60.0))).reason)
        assertEquals(LandRating.POOR, ecoTile(check(LandFeature(Cat.FOREST, "forest", "RF", 0.0))).rating)
        assertEquals(LandRating.GOOD, ecoTile(check()).rating)
        assertEquals(LandReason.NOT_CHECKED, ecoTile(check(version = 0)).reason)
        val forestLulc = LulcHit(lat0, lng0, "FODN", "Forest", "Deciduous", "Dense")
        assertEquals(LandReason.NEAR_FOREST, ecoTile(check(lulc = listOf(forestLulc))).reason, "ISRO map shows forest at the pin")
    }

    @Test fun protectedAreaFromIsIn() {
        val els = parseOverpass(answer("""{"type":"area","id":3600001,"tags":{"boundary":"protected_area","name":"Kalakad Mundanthurai Tiger Reserve"}}"""))
        val f = osmFacts(els, lat0, lng0, emptyList())
        val pa = f.features.first { it.category == Cat.PROTECTED }
        assertEquals(0.0, pa.distanceM); assertEquals("tiger_reserve", pa.kind)
        assertEquals(LandReason.INSIDE_PROTECTED, ecoTile(check(*f.features.toTypedArray())).reason)
    }

    @Test fun bhuvanLandUse() {
        assertEquals(listOf("sisdpv2:TN_Tirunelveli_lulc_v2"), Bhuvan.layersAt(9.0202, 77.2530), "Panpoli (Tenkasi) is in the old Tirunelveli district")
        assertTrue("sisdpv2:TN_Madurai_lulc_v2" in Bhuvan.layersAt(9.925, 78.12))
        assertTrue(Bhuvan.layersAt(13.08, 77.59).isEmpty(), "Bengaluru: outside Tamil Nadu")
        val url = Bhuvan.featureInfoUrl(listOf("sisdpv2:TN_Madurai_lulc_v2"), 9.925, 78.12)
        assertTrue("REQUEST=GetFeatureInfo" in url && "BBOX=9.9245,78.1195,9.9255,78.1205" in url && "INFO_FORMAT=application/json" in url, url)
        // A real answer (2026-11-07, TN_Tirunelveli_lulc_v2 near Panpoli).
        val body = """{"type":"FeatureCollection","features":[{"type":"Feature","id":"TN_Tirunelveli_lulc_v2.17268","geometry":null,"properties":{"lc_code":"AGPL","dscr1":"Agricultural land","dscr2":"Agriculture plantation","dscr3":"Agriculture plantation"}}],"totalFeatures":"unknown","numberReturned":1}"""
        val h = Bhuvan.parseFeatureInfo(body, 9.0, 77.2)!!
        assertEquals("AGPL", h.code); assertEquals(LulcKind.AGRICULTURE, lulcKind(h)); assertEquals("Agricultural land · Agriculture plantation", lulcLabel(h))
        assertNull(Bhuvan.parseFeatureInfo("""{"type":"FeatureCollection","features":[]}""", 9.0, 77.2))
        assertNull(Bhuvan.parseFeatureInfo("<html>error</html>", 9.0, 77.2))
        assertEquals(LulcKind.WATER, lulcKind(LulcHit(0.0, 0.0, "WBTK", "Waterbodies", "Lake/Pond", "Tank")))
        assertEquals(LulcKind.WASTELAND, lulcKind(LulcHit(0.0, 0.0, "WLSP", "Wastelands", "Scrub land", "Scrub land Open")), "WL = wasteland, not wetland")
        assertEquals(LulcKind.BUILT_UP, lulcKind(LulcHit(0.0, 0.0, "BURV", "Built up", "Built up (Rural)", "Village")))
    }

    @Test fun bhuvanWaterWhenOsmWaterFailed() {
        val w = LulcHit(lat0, lng0, "WBTK", "Waterbodies", "Tank", "Tank")
        val r = riskTile(check(failed = listOf(OsmLayer.WATER), lulc = listOf(w)))
        assertEquals(LandRating.POOR, r.rating); assertEquals(LandReason.LULC_WATER, r.reason)
        assertEquals(LandReason.LAYER_FAILED, riskTile(check(failed = listOf(OsmLayer.WATER))).reason)
        // Water layer fine and clear, but the ISRO map shows water: at least a caution with a note.
        val both = riskTile(check(lulc = listOf(w)).copy(nearestWaterM = 600.0))
        assertEquals(LandRating.FAIR, both.rating); assertTrue(both.notes.any { it.code == "lulc_water" })
        // Old checks (no Bhuvan data) are judged as before.
        assertEquals(LandRating.GOOD, riskTile(check().copy(nearestWaterM = 600.0)).rating)
        assertEquals(LandRating.GOOD, connectivityTile(check(LandFeature(Cat.MAJOR_ROAD, "trunk", "NH", 500.0), LandFeature(Cat.ROAD, "residential", "", 20.0))).rating)
    }

    // ---------------- survey numbers ----------------

    @Test fun surveyNumbers() {
        assertEquals("123/4B", parseSurveyNumber("S.F. No. 123/4B").toString())
        assertEquals("45/2A1", parseSurveyNumber("Sy.No 45/2a1").toString())
        assertEquals("67/3", parseSurveyNumber("புல எண் 67/3").toString())
        assertEquals("123/4B", parseSurveyNumber("123 - 4 b").toString())
        assertEquals("88", parseSurveyNumber("88").toString())
        assertEquals("12", parseSurveyNumber("survey number 012").toString())
        assertNull(parseSurveyNumber("no number here"))
        assertNull(parseSurveyNumber(""))
        assertEquals("123/4B", normalizeSurveyNumber(" 123 / 4B "))
        assertEquals("not readable", normalizeSurveyNumber("not readable "))
    }

    // ---------------- web search fallback ----------------

    private val ddgHtml = """
        <div class="serp__results"><div id="links" class="results">
        <div class="result results_links results_links_deep result--ad ">
          <div class="links_main links_deep result__body"><h2 class="result__title">
          <a rel="nofollow" class="result__a" href="https://duckduckgo.com/y.js?ad_domain=example.com&amp;u3=x">Buy plots – Ad</a></h2>
          <a class="result__snippet" href="#">Sponsored</a></div></div>
        <div class="result results_links results_links_deep web-result ">
          <div class="links_main links_deep result__body"><h2 class="result__title">
          <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.99acres.com%2Fplots-in-tenkasi-ffid&amp;rut=abc123">Residential <b>Plots</b> in Tenkasi &amp; nearby - 99acres</a></h2>
          <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.99acres.com%2Fplots-in-tenkasi-ffid">Plots in <b>Panpoli</b> from ₹1.2 Lac per cent. 45 listings.</a>
          </div></div>
        <div class="result results_links results_links_deep web-result ">
          <div class="links_main links_deep result__body"><h2 class="result__title">
          <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Ftnreginet.gov.in%2Fportal%2F%3Flang%3Den&amp;rut=def">Guideline value – TNREGINET</a></h2>
          <a class="result__snippet" href="#">Guideline value search by village.</a></div></div>
        </div></div>
    """.trimIndent()

    @Test fun parsesDuckDuckGo() {
        val hits = parseDuckDuckGoHtml(ddgHtml)
        assertEquals(2, hits.size, "the ad is skipped")
        assertEquals("https://www.99acres.com/plots-in-tenkasi-ffid", hits[0].url)
        assertEquals("Residential Plots in Tenkasi & nearby - 99acres", hits[0].title)
        assertEquals("Plots in Panpoli from ₹1.2 Lac per cent. 45 listings.", hits[0].snippet)
        assertEquals("https://tnreginet.gov.in/portal/?lang=en", hits[1].url)
        assertTrue(parseDuckDuckGoHtml("").isEmpty())
        assertTrue(isBlockedPage("<html><div class=\"anomaly-modal__title\">Unfortunately, bots use DuckDuckGo too.</div>"))
        assertFalse(isBlockedPage(ddgHtml))
        assertEquals("https://a.b/c d", unwrapDuckLink("//duckduckgo.com/l/?uddg=https%3A%2F%2Fa.b%2Fc%20d&rut=1"))
        assertEquals("https://x.org/p", unwrapDuckLink("https://x.org/p"))
        assertEquals("தமிழ் x", percentDecode("%E0%AE%A4%E0%AE%AE%E0%AE%BF%E0%AE%B4%E0%AF%8D+x"))
    }

    @Test fun parsesLiteAndBing() {
        val lite = """
            <table><tr><td>1.&nbsp;</td><td><a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.magicbricks.com%2Fplots-in-tenkasi&amp;rut=1" class='result-link'>Plots for sale in Tenkasi | Magicbricks</a></td></tr>
            <tr><td></td><td class='result-snippet'>Find <b>plots</b> in Tenkasi from ₹2 L per cent.</td></tr>
            <tr><td>2.&nbsp;</td><td><a rel="nofollow" href="https://www.commonfloor.com/tenkasi" class='result-link'>Commonfloor Tenkasi</a></td></tr>
            <tr><td></td><td class='result-snippet'>Land rates.</td></tr></table>
        """.trimIndent()
        val l = parseDuckDuckGoLite(lite)
        assertEquals(2, l.size)
        assertEquals("https://www.magicbricks.com/plots-in-tenkasi", l[0].url)
        assertEquals("Find plots in Tenkasi from ₹2 L per cent.", l[0].snippet)
        assertEquals("Land rates.", l[1].snippet)
        val rss = """<?xml version="1.0" encoding="utf-8" ?><rss version="2.0"><channel><title>Bing: x</title>
            <item><title>Panpoli land rate per cent</title><link>https://www.example.in/panpoli-rates</link><description>Rates around ₹80,000 per cent near Panpoli.</description></item>
            <item><title>Microsoft – Official Home Page</title><link>https://www.microsoft.com/en-in</link><description>Explore Microsoft products.</description></item>
            </channel></rss>"""
        val b = parseBingRss(rss)
        assertEquals(2, b.size)
        val rel = relevantOnly(b, "Panpoli Tenkasi plot price per cent")
        assertEquals(listOf("https://www.example.in/panpoli-rates"), rel.map { it.url }, "unrelated pages are dropped")
    }

    @Test fun searchPromptAndCitations() {
        val hits = parseDuckDuckGoHtml(ddgHtml)
        val p = promptWithResults("Find rates. Use Google Search. Reply with JSON.", hits)
        assertFalse("Use Google Search." in p)
        assertTrue("URL: https://www.99acres.com/plots-in-tenkasi-ffid" in p && "WEB SEARCH RESULTS" in p)
        assertTrue(citesResult("https://www.99acres.com/plots-in-tenkasi-ffid/", hits))
        assertTrue(citesResult("HTTPS://www.99acres.com/plots-in-tenkasi-ffid#x", hits))
        assertFalse(citesResult("https://www.99acres.com/other", hits))
        assertFalse(citesResult("", hits))
        val ai = LandAi(zoning = "residential", zoningUrl = "https://tnreginet.gov.in/portal/?lang=en", growth = "high", growthUrl = "https://made-up.example/x",
            roads = listOf(AiRoad("Ring road", "proposed", 1.0, "https://www.99acres.com/plots-in-tenkasi-ffid"), AiRoad("Bypass", "proposed", 2.0, "https://made-up.example/y")))
        val only = onlyCitedResults(ai, hits)
        assertEquals("residential", only.zoning)
        assertEquals("unknown", only.growth, "an uncited claim becomes unknown")
        assertEquals(listOf("Ring road"), only.roads.map { it.name })
        val q = priceSearchQueries(com.hark.sothudiary.domain.GeoPlace("Panpoli", 9.02, 77.25, "Panpoli", "Shenkottai", "Tenkasi", "Tamil Nadu"), "", 0)
        assertTrue(q.first().startsWith("Panpoli Tenkasi plot"), q.toString())
        assertTrue(priceSearchQueries(null, "", 0).isEmpty())
    }

    // ---------------- Gemini quota root cause ----------------

    @Test fun quotaWindows() {
        val groundingNotFree = """{"error":{"code":429,"message":"You exceeded your current quota. Quota exceeded for metric: generativelanguage.googleapis.com/search_grounding_request_free_tier, limit: 0, model: gemini-3-flash","status":"RESOURCE_EXHAUSTED",
            "details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaMetric":"generativelanguage.googleapis.com/search_grounding_request_free_tier","quotaId":"SearchGroundingRequestsPerDayPerProjectPerModel-FreeTier","quotaValue":"0"}]},
            {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"20s"}]}}"""
        val e = parseGeminiError(429, groundingNotFree)
        assertTrue(isQuotaError(e.code, e.status, e.message))
        assertEquals("0", e.quotaValue)
        assertEquals(QuotaWindow.NOT_IN_TIER, quotaWindow(e))
        val now = 1_700_000_000_000L
        assertEquals(now + 24 * 3_600_000L, quotaSkipUntil(e, now), "not available: skip for a day, not 20 s")
        val daily = parseGeminiError(429, """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier","quotaValue":"20"}]}]}}""")
        assertEquals(QuotaWindow.DAY, quotaWindow(daily))
        assertEquals(nextPacificMidnight(now), quotaSkipUntil(daily, now))
        val minute = parseGeminiError(429, """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier","quotaValue":"5"}]},{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"41s"}]}}""")
        assertEquals(QuotaWindow.MINUTE, quotaWindow(minute))
        assertEquals(now + 60_000L, quotaSkipUntil(minute, now))
        // Midnight Pacific (08:00 UTC): always in the future, within a day, on 08:00 UTC.
        val m = nextPacificMidnight(now)
        assertTrue(m > now && m - now <= 24 * 3_600_000L)
        assertEquals(8L * 3_600_000L, m % (24 * 3_600_000L))
    }

    @Test fun groundedOrderPrefersFreeGrounding() {
        val ids = listOf("gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-lite-latest", "gemini-3-pro-preview")
        val o = groundedModelOrder(ids)
        assertEquals("gemini-2.5-flash", o[0])
        assertEquals("gemini-2.5-flash-lite", o[1])
        assertTrue("gemini-flash-latest" in o)
        assertTrue(o.none { "pro" in it })
        // Without 2.5 models the 2.1.2 order stays.
        assertEquals("gemini-flash-latest", groundedModelOrder(listOf("gemini-3.5-flash", "gemini-flash-latest"))[0])
    }

    // ---------------- share links ----------------

    private val prop = Property(
        id = "abc123", title = "10 cent plot near Thirumalai kovil, Panpoli", type = PropertyType.PLOT, locality = "Panpoli", taluk = "Shenkottai",
        district = "Tenkasi", lat = 9.0123456789, lng = 77.2468123456, size = 10.0, unit = AreaUnit.CENTS, askingPrice = 2_500_000,
        sourceUrl = "https://www.instagram.com/p/abc/", notes = "Broker said lowest 22 L", status = DealStatus.NEGOTIATING,
        docs = setOf(DocItem.PATTA), folderIds = setOf("f1"), imageFile = "img_1.jpg", surveyNumber = "123/4B",
        boundary = listOf(GeoPt(9.0123, 77.2468), GeoPt(9.0125, 77.2468), GeoPt(9.0125, 77.2470)),
    )

    @Test fun base64RoundTrip() {
        for (n in 0..40) {
            val bytes = ByteArray(n) { (it * 37 + n).toByte() }
            assertTrue(bytes.contentEquals(base64UrlDecode(base64UrlEncode(bytes))), "n=$n")
        }
        assertEquals("_-8", base64UrlEncode(byteArrayOf(0xFF.toByte(), 0xEF.toByte())))
        assertNull(base64UrlDecode("ab*c"))
    }

    @Test fun shareLinkRoundTrip() {
        val land = LandCheck("abc123", landCheckKey(prop), 9.0123, 77.2468, 5L, osmOk = true, bufferPct = 3.0,
            features = listOf(LandFeature(Cat.MAJOR_ROAD, "trunk", "NH 744", 300.0)), layersVersion = 2,
            waterOutlines = listOf((0..200).map { GeoPt(9.01 + it * 1e-5, 77.24) }))
        val sp = sharedCopy(prop, land, com.hark.sothudiary.data.Broker("b1", "Murugan", phone = "9876543210"), ShareOptions(notes = false, landCheck = true, broker = true, sharedBy = "Sathya"))
        assertEquals("", sp.p.notes, "notes only when chosen")
        assertEquals(DealStatus.NEW, sp.p.status); assertTrue(sp.p.docs.isEmpty()); assertTrue(sp.p.folderIds.isEmpty()); assertEquals("", sp.p.imageFile)
        assertTrue(sp.lc!!.waterOutlines[0].size <= 41, "outlines thinned")
        val code = encodeShare(sp)
        assertTrue(code.startsWith("1.") && code.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }, code)
        val back = decodeShare(code)!!
        assertEquals(sp, back)
        assertEquals("123/4B", back.p.surveyNumber); assertEquals(9.012346, back.p.lat); assertEquals("Murugan", back.bn); assertEquals("Sathya", back.by)
        val link = ShareLink.httpsLink(code)
        assertTrue(link.startsWith("https://harksathya.github.io/test/p/#1."))
        assertTrue(link.length < 3_000, "WhatsApp-friendly length: ${link.length}")
        // Found again in the link, the app link and a WhatsApp message.
        assertEquals(code, extractShareCode(link))
        assertEquals(code, extractShareCode(ShareLink.appLink(code)))
        assertEquals(code, extractShareCode("🏡 10 cent plot\n₹25 L\nOpen it in the Sothu Diary app:\n$link\n(No app yet?)"))
        assertEquals(code, extractShareCode("https://harksathya.github.io/test/p/?d=$code"))
        assertNull(extractShareCode("https://example.com/#$code"), "only our page or scheme")
        assertNull(decodeShare("1.AAAA_broken"))
        assertNull(decodeShare("9." + code.drop(2)), "unknown future format")
        assertNull(extractShareCode(null))
        val withNotes = decodeShare(encodeShare(sharedCopy(prop, null, null, ShareOptions(notes = true, landCheck = false))))!!
        assertEquals("Broker said lowest 22 L", withNotes.p.notes); assertNull(withNotes.lc)
    }

    @Test fun importAddOrUpdate() {
        var n = 0
        val incoming = sharedCopy(prop, null, null, ShareOptions(notes = true)).p
        val added = mergeShared(null, incoming, ImportMode.ADD_NEW, 100L, { "new${n++}" }, "Sathya")
        assertEquals("abc123", added.id); assertEquals(DealStatus.NEW, added.status); assertEquals("[Sathya] Broker said lowest 22 L", added.notes)
        assertEquals(100L, added.importedAt)
        val mine = prop.copy(title = "My title", askingPrice = 2_400_000, notes = "my own notes", status = DealStatus.SITE_VISIT, docs = setOf(DocItem.EC, DocItem.PATTA))
        val copy = mergeShared(mine, incoming, ImportMode.ADD_NEW, 100L, { "new${n++}" })
        assertEquals("new0", copy.id, "a copy never overwrites the existing record")
        val upd = mergeShared(mine, incoming.copy(askingPrice = 2_300_000), ImportMode.UPDATE_EXISTING, 200L, { "x" })
        assertEquals("abc123", upd.id); assertEquals(2_300_000, upd.askingPrice); assertEquals(prop.title, upd.title)
        assertEquals("my own notes", upd.notes); assertEquals(DealStatus.SITE_VISIT, upd.status); assertEquals(setOf(DocItem.EC, DocItem.PATTA), upd.docs)
        assertEquals(setOf("f1"), upd.folderIds); assertEquals("img_1.jpg", upd.imageFile)
    }

    @Test fun repositoryImport() {
        val r = JsonRepository(MemoryFileStore())
        val land = LandCheck("abc123", landCheckKey(prop), 9.0123, 77.2468, 5L, osmOk = true, layersVersion = 2)
        val sp = sharedCopy(prop, land, com.hark.sothudiary.data.Broker("b1", "Murugan", phone = "98765 43210"), ShareOptions(broker = true))
        val code = encodeShare(sp)
        val id = r.importShared(decodeShare(code)!!, ImportMode.ADD_NEW)
        assertEquals("abc123", id)
        val saved = r.data.properties.first { it.id == id }
        assertEquals(1, r.data.brokers.size); assertEquals(r.data.brokers[0].id, saved.brokerId)
        assertEquals(landCheckKey(saved), r.data.landChecks.first { it.propertyId == id }.key, "the land check stays valid")
        // The same link again: as a copy (new id, broker reused) or as an update (same record).
        val id2 = r.importShared(decodeShare(code)!!, ImportMode.ADD_NEW)
        assertTrue(id2 != id); assertEquals(2, r.data.properties.size); assertEquals(1, r.data.brokers.size)
        r.update { d -> d.copy(properties = d.properties.map { if (it.id == id) it.copy(notes = "keep me", status = DealStatus.NEGOTIATING) else it }) }
        r.importShared(decodeShare(code)!!, ImportMode.UPDATE_EXISTING)
        val after = r.data.properties.first { it.id == id }
        assertEquals("keep me", after.notes); assertEquals(DealStatus.NEGOTIATING, after.status); assertEquals(2, r.data.properties.size)
        // Old saved data (no 2.3.0 fields) still loads.
        val old = """{"properties":[{"id":"o1","title":"Old plot","lat":9.9,"lng":78.1}],"landChecks":[{"propertyId":"o1","key":"k","lat":9.9,"lng":78.1,"fetchedAt":1,"osmOk":true}]}"""
        val store = MemoryFileStore().also { it.write(old) }
        val r2 = JsonRepository(store)
        assertEquals("", r2.data.properties[0].surveyNumber); assertNull(r2.data.properties[0].sketch)
        assertEquals(0, r2.data.landChecks[0].layersVersion); assertTrue(r2.data.landChecks[0].osmFailedLayers.isEmpty())
        assertEquals(LandReason.NOT_CHECKED, railwayTile(r2.data.landChecks[0]).reason)
    }
}
