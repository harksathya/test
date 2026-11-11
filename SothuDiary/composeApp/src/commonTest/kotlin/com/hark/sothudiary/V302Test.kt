package com.hark.sothudiary

import com.hark.sothudiary.domain.land.Cat
import com.hark.sothudiary.domain.land.ExtraTileRules
import com.hark.sothudiary.domain.land.MvtFeature
import com.hark.sothudiary.domain.land.MvtLayer
import com.hark.sothudiary.domain.land.OsmElement
import com.hark.sothudiary.domain.land.OsmLayer
import com.hark.sothudiary.domain.land.OverpassResult
import com.hark.sothudiary.domain.land.SourceStatusLine
import com.hark.sothudiary.domain.land.buildLayerQueries
import com.hark.sothudiary.domain.land.classify
import com.hark.sothudiary.domain.land.fetchOsmLayers
import com.hark.sothudiary.domain.land.latToTileY
import com.hark.sothudiary.domain.land.lngToTileX
import com.hark.sothudiary.domain.land.omtExtraTags
import com.hark.sothudiary.domain.land.osmFacts
import com.hark.sothudiary.domain.land.protectedKind
import com.hark.sothudiary.domain.land.tilesToExtraLayers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import com.hark.sothudiary.data.nowMillis
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 3.0.2: the land check's EXTRA layers (amenities, railway, protected areas / forest) from OpenFreeMap tiles. */
class V302Test {
    private val lat = 9.8236
    private val lng = 77.9870

    /** Tile-local coordinates (extent 4096) of a point at zoom [z] in tile [xy]. */
    private fun local(la: Double, ln: Double, z: Int, xy: Pair<Int, Int>): Pair<Int, Int> =
        floor((lngToTileX(ln, z) - xy.first) * 4096).toInt() to floor((latToTileY(la, z) - xy.second) * 4096).toInt()

    private fun tileOf(z: Int) = floor(lngToTileX(lng, z)).toInt() to floor(latToTileY(lat, z)).toInt()

    private fun fixture(): List<Triple<Int, Pair<Int, Int>, List<MvtLayer>>> {
        val t14 = tileOf(14); val t11 = tileOf(11)
        fun p14(dLat: Double, dLng: Double) = local(lat + dLat, lng + dLng, 14, t14)
        fun p11(dLat: Double, dLng: Double) = local(lat + dLat, lng + dLng, 11, t11)
        val poi = MvtLayer("poi", 4096, listOf(
            MvtFeature(1, 1, mapOf("class" to "school", "subclass" to "school", "name" to "Govt School"), listOf(listOf(p14(0.0, 0.0045)))), // ~490 m east
            MvtFeature(2, 1, mapOf("class" to "hospital", "subclass" to "clinic", "name" to "PHC"), listOf(listOf(p14(0.009, 0.0)))), // ~1 km north
            MvtFeature(3, 1, mapOf("class" to "bus", "subclass" to "bus_stop"), listOf(listOf(p14(-0.0018, 0.0)))), // ~200 m south
            MvtFeature(4, 1, mapOf("class" to "railway", "subclass" to "station", "name" to "Kalligudi"), listOf(listOf(p14(0.0, -0.009)))),
            MvtFeature(5, 1, mapOf("class" to "lodging", "subclass" to "hotel"), listOf(listOf(p14(0.001, 0.001)))), // ignored
            MvtFeature(6, 1, mapOf("class" to "grocery", "subclass" to "supermarket"), listOf(listOf(p14(0.002, 0.0)))),
        ))
        val rail = MvtLayer("transportation", 4096, listOf(
            MvtFeature(7, 2, mapOf("class" to "rail", "subclass" to "rail"), listOf(listOf(p14(-0.01, -0.0055), p14(0.01, -0.0055)))), // ~600 m west
            MvtFeature(8, 2, mapOf("class" to "minor"), listOf(listOf(p14(0.0, 0.0), p14(0.001, 0.0)))), // a road: not EXTRA
        ))
        val cover = MvtLayer("landcover", 4096, listOf(
            MvtFeature(9, 3, mapOf("class" to "wood", "subclass" to "forest"),
                listOf(listOf(p14(0.009, 0.009), p14(0.009, 0.012), p14(0.012, 0.012), p14(0.012, 0.009), p14(0.009, 0.009)))),
            MvtFeature(10, 3, mapOf("class" to "farmland", "subclass" to "farmland"), listOf(listOf(p14(0.0, 0.0), p14(0.0, 0.001), p14(0.001, 0.0), p14(0.0, 0.0)))),
        ))
        // A park in the z14 tile must be ignored (parks come from the wide tiles only).
        val park14 = MvtLayer("park", 4096, listOf(MvtFeature(11, 3, mapOf("class" to "national_park", "name" to "Wrong"), listOf(listOf(p14(0.0, 0.0), p14(0.0, 0.001), p14(0.001, 0.0), p14(0.0, 0.0))))))
        val park11 = MvtLayer("park", 4096, listOf(
            MvtFeature(12, 1, mapOf("class" to "wildlife_refuge", "name" to "Label point"), listOf(listOf(p11(0.0, 0.05)))),
            // Sanctuary ~5.5 km east of the pin (its west edge at +0.05° lng).
            MvtFeature(13, 3, mapOf("class" to "wildlife_refuge", "name" to "Test Wildlife Sanctuary"),
                listOf(listOf(p11(-0.02, 0.05), p11(-0.02, 0.08), p11(0.02, 0.08), p11(0.02, 0.05), p11(-0.02, 0.05)))),
        ))
        return listOf(Triple(14, t14, listOf(poi, rail, cover, park14)), Triple(11, t11, listOf(park11)))
    }

    @Test
    fun omtTagsForTheExtraLayers() {
        assertEquals(mapOf("amenity" to "college", "name" to "X"), omtExtraTags("poi", mapOf("class" to "college", "subclass" to "college", "name" to "X"), 1))
        assertEquals(mapOf("amenity" to "school"), omtExtraTags("poi", mapOf("class" to "school"), 1), "no subclass: the class")
        assertEquals(mapOf("highway" to "bus_stop"), omtExtraTags("poi", mapOf("class" to "bus", "subclass" to "bus_stop"), 1))
        assertEquals(mapOf("railway" to "halt"), omtExtraTags("poi", mapOf("class" to "railway", "subclass" to "halt"), 1))
        assertNull(omtExtraTags("poi", mapOf("class" to "place_of_worship", "subclass" to "hindu"), 1))
        assertEquals(mapOf("railway" to "rail"), omtExtraTags("transportation", mapOf("class" to "rail", "subclass" to "rail"), 2))
        assertNull(omtExtraTags("transportation", mapOf("class" to "rail", "subclass" to "subway"), 2))
        assertNull(omtExtraTags("transportation", mapOf("class" to "primary"), 2))
        assertEquals(mapOf("boundary" to "national_park"), omtExtraTags("park", mapOf("class" to "national_park"), 3))
        assertNull(omtExtraTags("park", mapOf("class" to "national_park"), 1), "label points are skipped")
        val refuge = omtExtraTags("park", mapOf("class" to "wildlife_refuge", "name" to "S"), 3)!!
        assertEquals("protected_area", refuge["boundary"])
        assertEquals("wildlife_sanctuary", protectedKind(refuge))
        assertEquals("reserved_forest", protectedKind(omtExtraTags("park", mapOf("class" to "reserved_forest"), 3)!!))
        assertEquals(mapOf("landuse" to "forest"), omtExtraTags("landcover", mapOf("class" to "wood", "subclass" to "forest"), 3))
        assertNull(omtExtraTags("landcover", mapOf("class" to "wood", "subclass" to "wood"), 3))
    }

    @Test
    fun tilesGiveTheExtraLayersAndTheLandCheckFacts() {
        val layers = tilesToExtraLayers(fixture())
        val amen = layers.getValue(OsmLayer.AMENITIES)
        assertEquals(4, amen.size, "school, clinic, bus stop, supermarket: ${amen.map { it.tags }}")
        assertEquals(2, layers.getValue(OsmLayer.RAIL).size, "station + line")
        val prot = layers.getValue(OsmLayer.PROTECTED)
        assertEquals(listOf("forest", "Test Wildlife Sanctuary"), prot.map { it.tags["name"] ?: it.tags["landuse"] })
        assertTrue(layers.values.flatten().all { it.id <= ExtraTileRules.ID_BASE }, "ids never clash with the CORE tile ids")
        assertTrue(layers.values.flatten().all { classify(it) != null }, "every element is understood by classify")

        val f = osmFacts(layers.values.flatten(), lat, lng, emptyList())
        val school = f.features.first { it.category == Cat.SCHOOL }
        assertTrue(school.distanceM in 400.0..600.0, "school ~490 m: ${school.distanceM}")
        assertTrue(f.features.first { it.category == Cat.BUS }.distanceM in 150.0..250.0)
        assertTrue(f.features.any { it.category == Cat.HOSPITAL })
        assertTrue(f.features.any { it.category == Cat.MARKET })
        val line = f.features.first { it.category == Cat.RAIL_LINE }
        assertTrue(line.distanceM in 500.0..700.0, "rail line ~600 m: ${line.distanceM}")
        assertTrue(f.features.any { it.category == Cat.RAIL && it.name == "Kalligudi" })
        val pa = f.features.first { it.category == Cat.PROTECTED }
        assertEquals("wildlife_sanctuary", pa.kind)
        assertTrue(pa.distanceM in 5000.0..6000.0, "sanctuary ~5.5 km: ${pa.distanceM}")
        assertTrue(f.features.any { it.category == Cat.FOREST && it.distanceM > 0 })
    }

    private fun ok(n: Int) = OverpassResult(List(n) { OsmElement("node", it + 1L, mapOf("amenity" to "school"), lat, lng) })

    @Test
    fun extraFallbackFillsTheLayersWhenOverpassFails() = runBlocking {
        val q = buildLayerQueries(lat, lng)
        val tiles = mapOf(
            OsmLayer.AMENITIES to OverpassResult(listOf(OsmElement("node", ExtraTileRules.ID_BASE, mapOf("amenity" to "school"), lat, lng))),
            OsmLayer.RAIL to OverpassResult(emptyList()),
            OsmLayer.PROTECTED to OverpassResult(emptyList()),
        ) to listOf(SourceStatusLine("tiles", true, "3 features"))
        val r = fetchOsmLayers(q, extraFallback = { tiles },
            groupFetch = { _, i, _, _ -> if (i == 0) ok(2) to emptyList() else OverpassResult(emptyList(), "[other] Overpass HTTP 504 (server busy)") to emptyList() })
        assertTrue(r.okLayers.containsAll(listOf(OsmLayer.WATER, OsmLayer.ROADS, OsmLayer.LANDUSE, OsmLayer.AMENITIES, OsmLayer.RAIL, OsmLayer.PROTECTED)), "${r.okLayers}")
        assertEquals(setOf(OsmLayer.UTILITIES), r.failed.keys, "power lines only come from Overpass")
        assertEquals(3, r.elements.size)
    }

    @Test
    fun slowOverpassExtraIsStoppedAfterTheGrace() = runBlocking {
        val q = buildLayerQueries(lat, lng)
        val tiles = listOf(OsmLayer.AMENITIES, OsmLayer.RAIL, OsmLayer.PROTECTED).associateWith { OverpassResult(emptyList()) } to emptyList<SourceStatusLine>()
        val t0 = nowMillis()
        val r = fetchOsmLayers(q, extraFallback = { tiles }, hedgeMs = 100, extraGraceMs = 300,
            groupFetch = { _, i, _, _ -> if (i == 0) ok(1) to emptyList() else { delay(10_000); ok(5) to emptyList() } })
        val took = nowMillis() - t0
        assertEquals(setOf(OsmLayer.UTILITIES), r.failed.keys)
        assertTrue(r.attempts.any { "power lines need Overpass" in it.reason })
        assertTrue(took < 5_000, "stopped after hedge + grace (~0.4 s), not 10 s: $took ms")
    }

    @Test
    fun fastOverpassExtraWinsAndTilesAreNotAsked() = runBlocking {
        val q = buildLayerQueries(lat, lng)
        var asked = false
        val r = fetchOsmLayers(q, extraFallback = { asked = true; emptyMap<String, OverpassResult>() to emptyList() },
            hedgeMs = 2_000, groupFetch = { _, i, _, _ -> delay(50); ok(i + 1) to emptyList() })
        assertTrue(!asked)
        assertTrue(r.failed.isEmpty(), "${r.failed}")
    }
}
