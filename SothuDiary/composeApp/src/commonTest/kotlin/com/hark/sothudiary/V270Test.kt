package com.hark.sothudiary

import com.hark.sothudiary.domain.land.Cat
import com.hark.sothudiary.domain.land.OsmLayer
import com.hark.sothudiary.domain.land.decodeGeometry
import com.hark.sothudiary.domain.land.decodeMvt
import com.hark.sothudiary.domain.land.latToTileY
import com.hark.sothudiary.domain.land.lngToTileX
import com.hark.sothudiary.domain.land.omtTags
import com.hark.sothudiary.domain.land.osmFacts
import com.hark.sothudiary.domain.land.parseTileTemplate
import com.hark.sothudiary.domain.land.pinInRings
import com.hark.sothudiary.domain.land.tileToLatLng
import com.hark.sothudiary.domain.land.tilesAround
import com.hark.sothudiary.domain.land.tilesToLayers
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.7.0: OpenFreeMap vector tiles as the second source for the land check's water / roads / land use. */
class V270Test {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // Hand-made tiles (Python mapbox-vector-tile) around a village pin near Thirumangalam (9.8236, 77.9870):
    // z14 11741/7742: lake "Test Kanmai" ~88 m east, a stream ~176 m west, "Kovil Street" (minor) ~29 m west, a primary
    // road (must be ignored at z14), farmland under the pin, a school POI (ignored). z12 2935/1935: primary "Madurai Road"
    // ~700 m east, a trunk under construction ~940 m west, a minor road and a lake (both ignored at z12).
    private val z14 = hex("1a4e0a057761746572121c080b1204000001011803221009f813f22b1af40300009003f303000f1a05636c6173731a046e616d6522060a046c616b65220d0a0b54657374204b616e6d616928802078021a350a0877617465727761791213080c120200001802220909f40cd2250a00d00f1a05636c61737322080a0673747265616d28802078021a720a0e7472616e73706f72746174696f6e1215080d1204000001011802220909e8109a270a00c00c1213080e120200021802220909d4109a270a00c00c1a05636c6173731a046e616d6522070a056d696e6f72220e0a0c4b6f76696c2053747265657422090a077072696d61727928802078021a4b0a096c616e64636f766572121c080f1204000001001803221009bc0eaa2a1ad8040000a006d704000f1a05636c6173731a08737562636c617373220a0a086661726d6c616e6428802078021a2c0a03706f69120f0810120200001801220509cc11ba2d1a05636c61737322080a067363686f6f6c2880207802")
    private val z12 = hex("1a9d010a0e7472616e73706f72746174696f6e1215081512040000010118022209098a19de1b0a00a01f12130816120200021802220909920ede1b0a00a01f121308171202000318022209099e14de1b0a00a01f1a05636c6173731a046e616d6522090a077072696d617279220e0a0c4d61647572616920526f616422140a127472756e6b5f636f6e737472756374696f6e22070a056d696e6f7228802078021a370a057761746572121a08181202000018032210099a1cae2b1ac8010000c801c701000f1a05636c61737322060a046c616b652880207802")
    private val lat = 9.8236
    private val lng = 77.9870

    @Test
    fun geometryCommandsFromTheMvtSpec() {
        assertEquals(listOf(listOf(25 to 17)), decodeGeometry(longArrayOf(9, 50, 34)))
        assertEquals(listOf(listOf(2 to 2, 2 to 10, 10 to 10)), decodeGeometry(longArrayOf(9, 4, 4, 18, 0, 16, 16, 0)))
        assertEquals(listOf(listOf(3 to 6, 8 to 12, 20 to 34, 3 to 6)), decodeGeometry(longArrayOf(9, 6, 12, 18, 10, 12, 24, 44, 15)))
        // A cut-off command list stops cleanly.
        assertEquals(listOf(listOf(2 to 2)), decodeGeometry(longArrayOf(9, 4, 4, 18, 0)))
    }

    @Test
    fun tileMathsRoundTrip() {
        val x = lngToTileX(lng, 14); val y = latToTileY(lat, 14)
        assertEquals(11741, x.toInt()); assertEquals(7742, y.toInt())
        val (la, ln) = tileToLatLng(x, y, 14)
        assertTrue(abs(la - lat) < 1e-9 && abs(ln - lng) < 1e-9)
        val t = tilesAround(lat, lng, 1000.0, 14)
        assertTrue(t.first() == (11741 to 7742), "nearest tile first: $t")
        assertTrue(t.size in 1..4, "1 km fits in at most 2x2 z14 tiles: $t")
        assertTrue(tilesAround(lat, lng, 50_000.0, 14).size == 9, "capped")
    }

    @Test
    fun decodesLayersAndTags() {
        val layers = decodeMvt(z14)
        assertEquals(setOf("water", "waterway", "transportation", "landcover", "poi"), layers.map { it.name }.toSet())
        val water = layers.first { it.name == "water" }
        assertEquals(4096, water.extent)
        assertEquals("Test Kanmai", water.features.single().tags["name"])
        assertEquals(3, water.features.single().type)
        assertEquals(5, water.features.single().parts.single().size, "closed ring")
        assertEquals(setOf("water"), decodeMvt(z14, setOf("water")).map { it.name }.toSet())
    }

    @Test
    fun omtSchemaToOsmTags() {
        assertEquals(mapOf("natural" to "water", "water" to "lake"), omtTags("water", mapOf("class" to "lake"), true))
        assertNull(omtTags("water", mapOf("class" to "swimming_pool"), true))
        assertEquals(mapOf("waterway" to "drain"), omtTags("waterway", mapOf("class" to "ditch"), true))
        assertEquals(mapOf("highway" to "residential", "name" to "X"), omtTags("transportation", mapOf("class" to "minor", "name" to "X"), true))
        assertNull(omtTags("transportation", mapOf("class" to "minor"), false))
        assertNull(omtTags("transportation", mapOf("class" to "primary"), true))
        assertEquals(mapOf("highway" to "primary"), omtTags("transportation", mapOf("class" to "primary"), false))
        assertEquals(mapOf("highway" to "construction", "construction" to "trunk"), omtTags("transportation", mapOf("class" to "trunk_construction"), false))
        assertNull(omtTags("transportation", mapOf("class" to "path"), true))
        assertEquals(mapOf("landuse" to "forest"), omtTags("landcover", mapOf("class" to "wood", "subclass" to "forest"), true))
        assertEquals(mapOf("natural" to "wetland"), omtTags("landcover", mapOf("class" to "wetland", "subclass" to "marsh"), true))
        assertNull(omtTags("landuse", mapOf("class" to "school"), true))
    }

    @Test
    fun tilesGiveTheCoreLayersAndTheLandCheckFacts() {
        val tiles = listOf(Triple(14, 11741 to 7742, decodeMvt(z14)), Triple(12, 2935 to 1935, decodeMvt(z12)))
        val layers = tilesToLayers(tiles, lat, lng)
        assertEquals(2, layers.getValue(OsmLayer.WATER).size, "lake + stream: ${layers[OsmLayer.WATER]}")
        val roads = layers.getValue(OsmLayer.ROADS).map { it.tags }
        assertEquals(3, roads.size, "minor (z14) + primary + construction (z12): $roads")
        assertTrue(roads.any { it["highway"] == "residential" && it["name"] == "Kovil Street" })
        assertTrue(roads.any { it["highway"] == "primary" && it["name"] == "Madurai Road" })
        assertTrue(roads.any { it["highway"] == "construction" })
        val landuse = layers.getValue(OsmLayer.LANDUSE)
        assertEquals(listOf("farmland"), landuse.map { it.tags["landuse"] })
        assertTrue(landuse.all { it.type == "area" })

        val f = osmFacts(layers.values.flatten(), lat, lng, emptyList())
        assertEquals(listOf("landuse=farmland"), f.landuse)
        val water = assertNotNull(f.nearestWaterM)
        assertTrue(water in 70.0..110.0, "lake ~88 m east: $water")
        assertEquals("Test Kanmai", f.nearestWaterName)
        val road = f.features.first { it.category == Cat.ROAD }
        assertTrue(road.distanceM in 15.0..45.0, "Kovil Street ~29 m: ${road.distanceM}")
        val major = f.features.first { it.category == Cat.MAJOR_ROAD }
        assertTrue(major.distanceM in 600.0..800.0, "primary ~700 m: ${major.distanceM}")
        assertTrue(f.features.any { it.category == Cat.PROPOSED_ROAD && it.distanceM in 850.0..1050.0 })
    }

    @Test
    fun pinInsideWaterIsReportedAsWaterLandUse() {
        val lakeLat = lat; val lakeLng = lng + 0.0012 // inside the lake
        val layers = tilesToLayers(listOf(Triple(14, 11741 to 7742, decodeMvt(z14))), lakeLat, lakeLng)
        assertTrue(layers.getValue(OsmLayer.LANDUSE).any { it.tags["natural"] == "water" })
        val f = osmFacts(layers.values.flatten(), lakeLat, lakeLng, emptyList())
        assertEquals(0.0, f.nearestWaterM)
        assertTrue(pinInRings(0.5, 0.5, listOf(listOf(0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0, 1.0 to 0.0, 0.0 to 0.0))))
        assertTrue(!pinInRings(1.5, 0.5, listOf(listOf(0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0, 1.0 to 0.0, 0.0 to 0.0))))
    }

    @Test
    fun tileTemplateFromTileJson() {
        assertEquals("https://tiles.openfreemap.org/planet/20261101_080013_pt/{z}/{x}/{y}.pbf",
            parseTileTemplate("""{"tilejson":"3.0.0","tiles":["https://tiles.openfreemap.org/planet/20261101_080013_pt/{z}/{x}/{y}.pbf"]}"""))
        assertNull(parseTileTemplate("{}"))
        assertNull(parseTileTemplate("not json"))
    }
}
