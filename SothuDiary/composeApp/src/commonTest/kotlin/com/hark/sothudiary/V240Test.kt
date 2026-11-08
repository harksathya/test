package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.GvUnit
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.LandCheck
import com.hark.sothudiary.data.LandRecord
import com.hark.sothudiary.data.MemoryFileStore
import com.hark.sothudiary.data.OfficialGuideline
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.RegistryInfo
import com.hark.sothudiary.data.ScoreWeights
import com.hark.sothudiary.data.landFromLegacy
import com.hark.sothudiary.data.migrate
import com.hark.sothudiary.data.reconcileLand
import com.hark.sothudiary.data.splitSurvey
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.ScoreFactor
import com.hark.sothudiary.domain.guidelineFactor
import com.hark.sothudiary.domain.land.Bhuvan
import com.hark.sothudiary.domain.land.Cat
import com.hark.sothudiary.domain.land.LandRating
import com.hark.sothudiary.domain.land.LandReason
import com.hark.sothudiary.domain.land.LandTile
import com.hark.sothudiary.domain.land.OsmGroup
import com.hark.sothudiary.domain.land.OsmLayer
import com.hark.sothudiary.domain.land.OverpassResult
import com.hark.sothudiary.domain.land.SourceStatusLine
import com.hark.sothudiary.domain.land.VillageDirectory
import com.hark.sothudiary.domain.land.assessLand
import com.hark.sothudiary.domain.land.buildLayerQueries
import com.hark.sothudiary.domain.land.classify
import com.hark.sothudiary.domain.land.fetchGroup
import com.hark.sothudiary.domain.land.groupQueries
import com.hark.sothudiary.domain.land.hasAnyData
import com.hark.sothudiary.domain.land.lulcKind
import com.hark.sothudiary.domain.land.LulcKind
import com.hark.sothudiary.domain.land.mergedWith
import com.hark.sothudiary.domain.land.osmFacts
import com.hark.sothudiary.domain.land.overpassReason
import com.hark.sothudiary.domain.land.parseElevations
import com.hark.sothudiary.domain.land.parseLandText
import com.hark.sothudiary.domain.land.parseOverpass
import com.hark.sothudiary.domain.land.readOverpassAnswer
import com.hark.sothudiary.domain.land.riskTile
import com.hark.sothudiary.domain.pricing.convertPastedRate
import com.hark.sothudiary.domain.pricing.parsePastedRates
import com.hark.sothudiary.domain.priceFactor
import com.hark.sothudiary.domain.propertyScore
import com.hark.sothudiary.domain.registry.districtSpellings
import com.hark.sothudiary.domain.registry.gvPlace
import com.hark.sothudiary.domain.registry.gvPrefillScript
import com.hark.sothudiary.domain.share.buildShareText
import com.hark.sothudiary.domain.share.ShareFacts
import com.hark.sothudiary.domain.share.codeFromFileText
import com.hark.sothudiary.domain.share.decodeShare
import com.hark.sothudiary.domain.share.encodeShare
import com.hark.sothudiary.domain.share.shareFileName
import com.hark.sothudiary.domain.share.shareFileText
import com.hark.sothudiary.domain.share.SharedProperty
import com.hark.sothudiary.ui.map.MapItem
import com.hark.sothudiary.ui.map.clusterItems
import com.hark.sothudiary.ui.map.shortRupees
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V240Test {

    // ------------------------------------------------------------------ parsers with real answers

    @Test fun overpassRealMaduraiWater() {
        val els = parseOverpass(Fixtures240.OVERPASS_MADURAI_WATER)
        assertEquals(2, els.size)
        val kulam = els.first { it.name == "Porthamarai Kulam" }
        assertEquals(Cat.WATER, classify(kulam)?.category)
        assertTrue(kulam.paths.single().size >= 4, "the tank outline")
        val vaigai = els.first { it.name == "Vaigai" }
        assertEquals("river", classify(vaigai)?.kind)
        // The facts for a pin in the city: the nearest water is found and has a name.
        val f = osmFacts(els, 9.9252, 78.1198, emptyList())
        assertNotNull(f.nearestWaterM)
        assertTrue(f.nearestWaterName.isNotBlank())
    }

    @Test fun overpass406IsRefusedNotBusy() {
        val (r, busy) = readOverpassAnswer(406, Fixtures240.OVERPASS_406)
        assertTrue(r.error!!.contains("406") && r.error!!.contains("User-Agent"), r.error)
        assertFalse(busy)
        assertTrue(overpassReason(406, null).contains("User-Agent"))
        assertTrue(overpassReason(429, null).contains("429"))
        assertEquals("timeout", overpassReason(-1, "timeout"))
    }

    @Test fun appUserAgentIdentifiesWithContact() {
        // overpass-api.de refuses user agents without a way to reach the author (HTTP 406 seen on 2026-11-08).
        assertTrue(APP_USER_AGENT.startsWith("SothuDiary/2.4"))
        assertTrue("https://" in APP_USER_AGENT)
    }

    @Test fun nominatimRealAnswers() {
        val panpoli = Geocoder.parseReverse(Fixtures240.NOMINATIM_PANPOLI)!!
        assertEquals("Tenkasi", panpoli.district)
        assertEquals("Shenkottai", panpoli.taluk)
        assertEquals("Vadakarai", panpoli.locality)
        val mdu = Geocoder.parseReverse(Fixtures240.NOMINATIM_MADURAI)!!
        assertEquals("Madurai", mdu.district)
        assertEquals("Madurai South", mdu.taluk)
        assertNull(Geocoder.parseReverse("<html>error</html>"))
    }

    @Test fun openMeteoRealAnswer() {
        assertEquals(listOf(184.0, 184.0, 179.0), parseElevations(Fixtures240.OPEN_METEO_ELEVATION))
        assertNull(parseElevations("""{"error":true,"reason":"Latitude must be in range"}"""))
    }

    @Test fun bhuvanRealAnswersParse() {
        // Parser only – the app does not call Bhuvan automatically in 2.4.0 (licence).
        val t = Bhuvan.parseFeatureInfo(Fixtures240.BHUVAN_TENKASI, 9.03, 77.27)!!
        assertEquals("AGPL", t.code); assertEquals(LulcKind.AGRICULTURE, lulcKind(t))
        val m = Bhuvan.parseFeatureInfo(Fixtures240.BHUVAN_MADURAI, 9.9252, 78.1198)!!
        assertEquals(LulcKind.BUILT_UP, lulcKind(m))
        assertTrue(Bhuvan.layersAt(9.03, 77.27).any { "Tirunelveli" in it }, "Tenkasi lies in the older Tirunelveli layer")
    }

    // ------------------------------------------------------------------ Overpass: two combined queries, racing, partial

    @Test fun layersAreSentAsTwoGroups() {
        val g = groupQueries(buildLayerQueries(9.03, 77.27))
        assertEquals(setOf(OsmGroup.CORE, OsmGroup.EXTRA), g.keys)
        g.values.forEach { assertTrue(it.startsWith("[out:json][timeout:25];"), it) }
        val core = g.getValue(OsmGroup.CORE)
        assertTrue("is_in(" in core && "\"natural\"~\"^(water|wetland)$\"" in core && "motorway" in core)
        assertEquals(1, Regex("""\[out:json]""").findAll(core).count(), "one header per query")
        val extra = g.getValue(OsmGroup.EXTRA)
        assertTrue("school" in extra && "level_crossing" in extra && "protected_area" in extra && "power" in extra)
        assertFalse("is_in(" in extra)
    }

    @Test fun fetchGroupFallsOverToTheNextServer() = runBlocking {
        val calls = mutableListOf<String>()
        val (res, lines) = fetchGroup("q", 0, "(test)", budgetMs = 10_000, hedgeMs = 5_000, endpoints = listOf("https://a/api", "https://b/api"),
            attempt = { url, _, _, _ ->
                calls += url
                if (url.contains("//a/")) OverpassResult(emptyList(), "[other] Overpass HTTP 406") to SourceStatusLine("Overpass · a", false, "HTTP 406")
                else OverpassResult(parseOverpass(Fixtures240.OVERPASS_MADURAI_WATER)) to SourceStatusLine("Overpass · b", true, "2 map features")
            })
        assertNull(res.error)
        assertEquals(2, res.elements.size)
        assertEquals(listOf("https://a/api", "https://b/api"), calls)
        assertEquals(listOf(false, true), lines.map { it.ok })
    }

    @Test fun fetchGroupGivesUpWithReasons() = runBlocking {
        val (res, lines) = fetchGroup("q", 0, "(test)", budgetMs = 9_000, hedgeMs = 2_000, endpoints = listOf("https://a/api"),
            attempt = { _, _, _, _ -> OverpassResult(emptyList(), "network") to SourceStatusLine("Overpass · a", false, "timeout") })
        assertNotNull(res.error)
        assertTrue(lines.isNotEmpty() && lines.all { it.ok == false })
    }

    // ------------------------------------------------------------------ partial results still show

    @Test fun landCheckWithoutMapDataStillShowsElevation() {
        val c = LandCheck("p", "k", 9.03, 77.27, 1L, osmOk = false, elevationM = 170.0, surroundingElevationM = 175.0)
        assertTrue(c.hasAnyData())
        val r = riskTile(c)
        assertEquals(LandRating.FAIR, r.rating, "5 m below its surroundings")
        assertEquals(LandReason.LOW_LYING, r.reason)
        val a = assessLand(c, Property("p", "x", lat = 9.03, lng = 77.27), hasKey = false)
        assertEquals(LandRating.UNKNOWN, a.tile(LandTile.CONNECTIVITY).rating)
        assertFalse(LandCheck("p", "k", 0.0, 0.0).hasAnyData())
    }

    // ------------------------------------------------------------------ shared land record

    @Test fun surveySplit() {
        assertEquals("123" to "4B", splitSurvey("S.F. No. 123/4B"))
        assertEquals("88" to "", splitSurvey("88"))
        assertEquals("" to "", splitSurvey(" "))
    }

    @Test fun migrationMergesTheOldFields() {
        val old = Property("a", "Plot", district = "Tenkasi District", taluk = "Shenkottai", locality = "Vadakarai", surveyNumber = "123/4B",
            registry = RegistryInfo(sro = "Panpozhi", village = "Vadakarai Melpidagai"))
        val d = migrate(AppData(properties = listOf(old)))
        val p = d.properties.single()
        assertEquals(LandRecord(district = "Tenkasi", taluk = "Shenkottai", village = "Vadakarai Melpidagai", sro = "Panpozhi", surveyNo = "123", subdivision = "4B"),
            p.land.copy(autoFilled = emptySet()))
        // The old fields follow the record (nothing lost).
        assertEquals("123/4B", p.surveyNumber)
        assertEquals("Panpozhi", p.registry.sro)
        assertEquals("Tenkasi", p.registry.district)
    }

    @Test fun editingOneSideUpdatesTheOther() {
        val base = reconcileLand(null, Property("a", "Plot", district = "Madurai", surveyNumber = "45"))
        assertEquals("45", base.land.surveyNo)
        // Land details edited -> guideline place and survey number follow.
        val edited = reconcileLand(base, base.copy(land = base.land.copy(sro = "Thirupparankundram", surveyNo = "46", subdivision = "2A", pattaNo = "789")))
        assertEquals("46/2A", edited.surveyNumber)
        assertEquals("Thirupparankundram", edited.registry.sro)
        assertEquals("789", edited.land.pattaNo)
        // An older screen (2.3.1 guideline place) changes the village -> the record follows.
        val viaRegistry = reconcileLand(edited, edited.copy(registry = edited.registry.copy(village = "Thiruparankundram")))
        assertEquals("Thiruparankundram", viaRegistry.land.village)
        // The edit form changes the district.
        val viaForm = reconcileLand(viaRegistry, viaRegistry.copy(district = "Theni"))
        assertEquals("Theni", viaForm.land.district)
        assertEquals("Theni", viaForm.registry.district)
    }

    @Test fun repositoryKeepsThemInStep() {
        val repo = JsonRepository(MemoryFileStore())
        repo.upsertProperty(Property("a", "Plot", district = "Tenkasi", lat = 9.03, lng = 77.27))
        repo.setSurveyNumber("a", "S.F.No 77/1C")
        assertEquals("77", repo.data.properties.single().land.surveyNo)
        assertEquals("1C", repo.data.properties.single().land.subdivision)
        repo.setLand("a", repo.data.properties.single().land.copy(village = "Vadakarai Keelpidagai", sro = "Shenkottai"))
        val p = repo.data.properties.single()
        assertEquals("Vadakarai Keelpidagai", gvPlace(p).village)
        assertEquals("Shenkottai", gvPlace(p).sro)
        assertEquals("77/1C", gvPlace(p).survey)
        repo.fillLandFromMap("a", "Tenkasi", "Shenkottai", "Vadakarai")
        assertEquals("Vadakarai Keelpidagai", repo.data.properties.single().land.village, "a typed village is never overwritten by the pin")
        assertEquals("Shenkottai", repo.data.properties.single().land.taluk)
    }

    @Test fun landTextIsReadFromPastedPatta() {
        val t = "District: Tenkasi  Taluk: Shenkottai  Village: Vadakarai Melpidagai\nPatta No: 1234  Survey No: 123/4B"
        val r = parseLandText(t)
        assertEquals("Tenkasi", r.district); assertEquals("Shenkottai", r.taluk); assertEquals("Vadakarai Melpidagai", r.village)
        assertEquals("1234", r.pattaNo); assertEquals("123", r.surveyNo); assertEquals("4B", r.subdivision)
        val ta = parseLandText("மாவட்டம்: மதுரை, வட்டம்: மேலூர், புல எண்: 56/2")
        assertEquals("மதுரை", ta.district); assertEquals("56", ta.surveyNo); assertEquals("2", ta.subdivision)
        val merged = LandRecord(district = "Madurai", pattaNo = "9").mergedWith(LandRecord(surveyNo = "1"))
        assertEquals("Madurai", merged.district); assertEquals("9", merged.pattaNo); assertEquals("1", merged.surveyNo)
    }

    // ------------------------------------------------------------------ bundled LGD village directory

    private val dirText = """
        # sample
        Tenkasi|Shenkottai|Elathur;Puliyarai;Vadakarai Keelpidagai;Vadakarai Melpidagai
        Tenkasi|Tenkasi|Ayikudi;Kasimajorpuram
        Madurai|Madurai South|Avaniyapuram;Thiruparankundram
        The Nilgiris|Ooty|Ithalar
    """.trimIndent()

    @Test fun villageDirectoryPickers() {
        val d = VillageDirectory(dirText)
        assertEquals(listOf("Madurai", "Tenkasi", "The Nilgiris"), d.districts)
        assertEquals("Tenkasi", d.district("Tenkasi District"))
        assertEquals("Tenkasi", d.district("தென்காசி"))
        assertEquals("The Nilgiris", d.district("Nilgiris"))
        assertEquals("Shenkottai", d.taluk("Tenkasi", "Shencottai"), "sound-alike spelling")
        assertEquals(4, d.villages("Tenkasi", "Shenkottai").size)
        assertEquals("Shenkottai", d.talukOf("Tenkasi", "Puliyarai"))
        assertEquals(listOf("Vadakarai Keelpidagai", "Vadakarai Melpidagai"), d.filter(d.villages("Tenkasi", ""), "vada"))
    }

    // ------------------------------------------------------------------ TNREGINET prefill

    @Test fun prefillTriesTamilDistrictNames() {
        assertTrue("தென்காசி" in districtSpellings("Tenkasi"))
        assertTrue("Tenkasi" in districtSpellings("தென்காசி"))
        val js = gvPrefillScript(gvPlace(Property("a", "x", land = LandRecord(district = "Madurai", sro = "Thirupparankundram", surveyNo = "45", subdivision = "2"))))
        assertTrue("\"மதுரை\"" in js, "Tamil spelling passed to the page")
        assertTrue("districtList" in js && "SROList" in js && "villageList" in js)
        assertTrue("survey:\"45\"" in js, "the form takes the survey number without the subdivision")
        assertTrue("out.sel=" in js, "the user's own choices are read back")
    }

    // ------------------------------------------------------------------ rates the user pastes

    @Test fun pastedRatesFromRealListingText() {
        // Snippets as returned by a web search on 2026-11-08 (Tenkasi / Panpoli plots).
        val a = parsePastedRates("The best plot in Kuthukkal valasi is available for sale at a price of ₹44 Lac (₹1,822/sqft).")
        assertEquals(listOf(1822.0), a.map { it.value }); assertEquals("sqft", a[0].unit)
        val b = parsePastedRates("Residential Land / Plot in Melagaram, Tenkasi ₹25.0 L Avg. price: ₹2.31k/sq.ft 1080 sq.ft Plot area")
        assertEquals(2310.0, b.single().value)
        val c = parsePastedRates("Property details: Price: Rs. 36 lakhs Land area: 22 cents Built-Up area: 400 sq.Ft")
        val perCent = c.single { it.derived }
        assertEquals("cent", perCent.unit); assertEquals(163_636.0, perCent.value, 1.0)
        val g = parsePastedRates("TNReginet guideline value for Kanakku Pillai Street, Panpoli village: Rs 201 per sq.ft effective 1 July 2024.")
        assertTrue(g.single().guideline)
        assertTrue(parsePastedRates("Call 98765 43210 today").isEmpty())
        assertEquals(1822.0 * 435.6, convertPastedRate(1822.0, "sqft", "cent"), 0.01)
    }

    // ------------------------------------------------------------------ score

    @Test fun scoreSkipsMissingFactors() {
        val none = propertyScore(null, null, null, ScoreWeights())
        assertNull(none.total)
        assertEquals(0, none.known)
        val duty = com.hark.sothudiary.domain.registry.saleDeedDuty(10_00_000, 11_00_000)
        val s = propertyScore(null, null, duty, ScoreWeights())
        assertEquals(90, s.total, "only the guideline gap is known: 10 % above guideline")
        assertEquals(90, s.factors.first { it.factor == ScoreFactor.GUIDELINE }.score)
        assertEquals(40, guidelineFactor(com.hark.sothudiary.domain.registry.saleDeedDuty(10_00_000, 8_00_000)), "below guideline")
        assertEquals(20, guidelineFactor(com.hark.sothudiary.domain.registry.saleDeedDuty(10_00_000, 60_00_000)))
        val zero = propertyScore(null, null, duty, ScoreWeights(guideline = 0))
        assertNull(zero.total, "a weight of 0 ignores the factor")
        assertNull(priceFactor(null))
    }

    // ------------------------------------------------------------------ share

    @Test fun shareTextReadsWellWithoutTheApp() {
        val t = buildShareText(ShareFacts(
            title = "10 cent plot, Panpoli", price = "₹18 L", unitPrice = "₹1.8 L/cent", area = "10 cents (4,356 sq ft)",
            place = "Vadakarai, Shenkottai, Tenkasi", lat = 9.03, lng = 77.27, land = "S.F. 123/4B · Patta 456",
            checks = listOf("Water risk: Low"), sourceUrl = "https://youtu.be/abc", broker = "", appLink = "https://harksathya.github.io/test/p/#1.x",
        ))
        assertTrue(t.startsWith("🏡 10 cent plot, Panpoli"))
        assertTrue("https://www.google.com/maps/search/?api=1&query=9.03,77.27" in t)
        assertTrue("Price / விலை: ₹18 L" in t && "Land / நிலம்: S.F. 123/4B" in t && "https://youtu.be/abc" in t)
        assertFalse("Broker" in t, "the broker only when chosen")
        assertTrue(AppName.EN in t && AppName.TA in t)
    }

    @Test fun sothuFileRoundTrip() {
        val sp = SharedProperty(Property("p1", "10 cent plot", type = PropertyType.PLOT, askingPrice = 18_00_000, sourceUrl = "https://youtu.be/abc",
            land = LandRecord(district = "Tenkasi", surveyNo = "123", subdivision = "4B", pattaNo = "456"),
            registry = RegistryInfo(guideline = OfficialGuideline(201.0, GvUnit.SQFT))))
        val code = encodeShare(sp)
        val file = shareFileText(code, sp.p.title)
        assertEquals(code, codeFromFileText(file))
        val back = decodeShare(codeFromFileText(file)!!)!!
        assertEquals("456", back.p.land.pattaNo)
        assertEquals("https://youtu.be/abc", back.p.sourceUrl)
        assertEquals(201.0, back.p.registry.guideline!!.value)
        assertNull(codeFromFileText("""{"app":"other","d":"1.abc"}"""))
        assertNull(codeFromFileText("hello"))
        assertEquals("10 cent plot.sothu", shareFileName("10 cent plot"))
        assertEquals("a b.sothu", shareFileName("a/b"))
    }

    // ------------------------------------------------------------------ map

    @Test fun clustersWhenZoomedOut() {
        val items = listOf(MapItem("a", 9.03, 77.27, "₹1.8k", 1, 50.0), MapItem("b", 9.031, 77.271, "₹2k", 2, 80.0), MapItem("c", 9.93, 78.12, "₹9.8k", 3))
        val far = clusterItems(items, 9.0)
        assertEquals(2, far.size)
        val cl = far.single { it.count == 2 }
        assertEquals(2L, cl.color, "the cluster takes its best member's colour")
        assertEquals(3, clusterItems(items, 16.0).size)
        assertEquals("₹9.8k", shortRupees(9_800.0)); assertEquals("₹1.2L", shortRupees(1_20_000.0)); assertEquals("₹850", shortRupees(850.0))
        assertEquals("₹12k", shortRupees(12_400.0))
    }
}
