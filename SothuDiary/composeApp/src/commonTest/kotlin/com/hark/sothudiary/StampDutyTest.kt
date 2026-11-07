package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.GvUnit
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.MemoryFileStore
import com.hark.sothudiary.data.OfficialGuideline
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.RegistryInfo
import com.hark.sothudiary.domain.PriceBasis
import com.hark.sothudiary.domain.pricing.checkPrice
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.registry.DutyRates
import com.hark.sothudiary.domain.registry.DutyRules
import com.hark.sothudiary.domain.registry.GV_CAPTURE_JS
import com.hark.sothudiary.domain.registry.SQFT_PER_SQM
import com.hark.sothudiary.domain.registry.captureCandidates
import com.hark.sothudiary.domain.registry.convertArea
import com.hark.sothudiary.domain.registry.convertGvRate
import com.hark.sothudiary.domain.registry.guidelineTotal
import com.hark.sothudiary.domain.registry.gvPlace
import com.hark.sothudiary.domain.registry.gvPrefillScript
import com.hark.sothudiary.domain.registry.officialGuideline
import com.hark.sothudiary.domain.registry.ownGuidelineInfo
import com.hark.sothudiary.domain.registry.parseGuidelineText
import com.hark.sothudiary.domain.registry.pctOf
import com.hark.sothudiary.domain.registry.propertyDuty
import com.hark.sothudiary.domain.registry.saleDeedDuty
import com.hark.sothudiary.domain.registry.sqft
import com.hark.sothudiary.domain.registry.toGvUnit
import com.hark.sothudiary.domain.registry.unitIn
import com.hark.sothudiary.ui.screens.moneyText
import com.hark.sothudiary.data.Lang
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StampDutyTest {
    private fun near(expected: Double, actual: Double, tol: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= tol * maxOf(1.0, abs(expected)), "expected $expected but was $actual")

    // ---------------------------------------------------------------- units

    @Test
    fun unitSizesInSqft() {
        near(1.0, GvUnit.SQFT.sqft())
        near(435.6, GvUnit.CENT.sqft())
        near(43_560.0, GvUnit.ACRE.sqft())
        near(10.7639104, GvUnit.SQM.sqft(), 1e-8)
        near(107_639.104, GvUnit.HECTARE.sqft(), 1e-8)
        near(SQFT_PER_SQM, 10.76391041671, 1e-10)
    }

    @Test
    fun areaConversions() {
        near(100.0, convertArea(1.0, GvUnit.ACRE, GvUnit.CENT))
        near(2.4710538, convertArea(1.0, GvUnit.HECTARE, GvUnit.ACRE), 1e-7)
        near(247.10538, convertArea(1.0, GvUnit.HECTARE, GvUnit.CENT), 1e-7)
        near(4356.0, convertArea(10.0, GvUnit.CENT, GvUnit.SQFT))
        near(40.468564, convertArea(1.0, GvUnit.CENT, GvUnit.SQM), 1e-7)
        // round trip
        near(3.75, convertArea(convertArea(3.75, GvUnit.ACRE, GvUnit.SQM), GvUnit.SQM, GvUnit.ACRE))
    }

    @Test
    fun rateConversions() {
        // ₹1,000 / sq ft = ₹4,35,600 / cent = ₹4.356 Cr / acre
        near(435_600.0, convertGvRate(1000.0, GvUnit.SQFT, GvUnit.CENT))
        near(43_560_000.0, convertGvRate(1000.0, GvUnit.SQFT, GvUnit.ACRE))
        // ₹10,00,000 / hectare ≈ ₹4,04,686 / acre ≈ ₹4,047 / cent
        near(404_685.64, convertGvRate(1_000_000.0, GvUnit.HECTARE, GvUnit.ACRE), 1e-6)
        near(4_046.8564, convertGvRate(1_000_000.0, GvUnit.HECTARE, GvUnit.CENT), 1e-6)
        // ₹5,000 / sq m ≈ ₹464.5 / sq ft
        near(464.5152, convertGvRate(5000.0, GvUnit.SQM, GvUnit.SQFT), 1e-6)
        assertEquals(GvUnit.CENT, AreaUnit.CENTS.toGvUnit())
        assertEquals(GvUnit.ACRE, AreaUnit.ACRES.toGvUnit())
        assertEquals(GvUnit.SQFT, AreaUnit.SQFT.toGvUnit())
        assertEquals(GvUnit.ACRE, PriceBasis.PER_ACRE.toGvUnit())
    }

    @Test
    fun guidelineTotalUsesTheAreaUnit() {
        // 5 cents at ₹1,200 / sq ft = 2,178 sq ft × 1,200 = ₹26,13,600
        assertEquals(2_613_600L, guidelineTotal(1200.0, GvUnit.SQFT, 5.0, GvUnit.CENT))
        // 2 acres at ₹12,00,000 / hectare = 0.809371 ha × 12 L = ₹9,71,246
        assertEquals(971_246L, guidelineTotal(1_200_000.0, GvUnit.HECTARE, 2.0, GvUnit.ACRE))
        assertNull(guidelineTotal(0.0, GvUnit.SQFT, 5.0, GvUnit.CENT))
        assertNull(guidelineTotal(1000.0, GvUnit.SQFT, 0.0, GvUnit.CENT))
    }

    // ---------------------------------------------------------------- calculator

    @Test
    fun defaultsAreSevenAndTwo() {
        assertEquals(7.0, DutyRules.STAMP_DUTY_PCT)
        assertEquals(2.0, DutyRules.REGISTRATION_FEE_PCT)
        assertEquals(DutyRates(7.0, 2.0), DutyRates())
        val s = com.hark.sothudiary.data.AppSettings()
        assertEquals(7.0, s.stampDutyPct)
        assertEquals(2.0, s.registrationFeePct)
    }

    @Test
    fun dutyOnThePriceWhenItIsHigher() {
        val d = assertNotNull(saleDeedDuty(guidelineValue = 40_00_000, agreedPrice = 50_00_000))
        assertEquals(50_00_000, d.dutyBase)
        assertFalse(d.baseIsGuideline)
        assertEquals(3_50_000, d.stampDuty)
        assertEquals(1_00_000, d.registrationFee)
        assertEquals(4_50_000, d.total)
        near(25.0, d.gapPct!!)
    }

    @Test
    fun dutyOnTheGuidelineWhenItIsHigher() {
        val d = assertNotNull(saleDeedDuty(guidelineValue = 30_00_000, agreedPrice = 24_00_000))
        assertEquals(30_00_000, d.dutyBase)
        assertTrue(d.baseIsGuideline)
        assertEquals(2_10_000, d.stampDuty)
        assertEquals(60_000, d.registrationFee)
        assertEquals(2_70_000, d.total)
        near(-20.0, d.gapPct!!)
    }

    @Test
    fun oneSideOnlyAndNothing() {
        val priceOnly = assertNotNull(saleDeedDuty(null, 12_34_567))
        assertNull(priceOnly.gapPct)
        assertNull(priceOnly.guidelineValue)
        assertEquals(86_420, priceOnly.stampDuty) // 7 % of 12,34,567 = 86,419.69
        assertEquals(24_691, priceOnly.registrationFee) // 2 % = 24,691.34
        val gvOnly = assertNotNull(saleDeedDuty(8_00_000, 0))
        assertTrue(gvOnly.baseIsGuideline)
        assertEquals(56_000, gvOnly.stampDuty)
        assertNull(saleDeedDuty(null, null))
        assertNull(saleDeedDuty(0, 0))
    }

    @Test
    fun editableRates() {
        val d = assertNotNull(saleDeedDuty(10_00_000, 10_00_000, DutyRates(stampPct = 5.0, regPct = 4.0)))
        assertEquals(50_000, d.stampDuty)
        assertEquals(40_000, d.registrationFee)
        assertEquals(1_000, pctOf(1_00_000, 1.0))
        assertEquals(0, pctOf(1_00_000, 0.0))
    }

    @Test
    fun womenBuyerConcessionUpToTenLakh() {
        val small = assertNotNull(saleDeedDuty(9_00_000, 10_00_000, womenBuyers = true))
        assertTrue(small.womenConcession)
        assertEquals(1.0, small.regPct)
        assertEquals(10_000, small.registrationFee)
        assertEquals(70_000, small.stampDuty) // stamp duty unchanged
        val big = assertNotNull(saleDeedDuty(9_00_000, 10_00_001, womenBuyers = true))
        assertFalse(big.womenConcession)
        assertEquals(2.0, big.regPct)
        val notWomen = assertNotNull(saleDeedDuty(9_00_000, 9_00_000))
        assertFalse(notWomen.womenConcession)
    }

    @Test
    fun propertyDutyUsesGuidelineTimesArea() {
        val p = Property(
            id = "p1", title = "Plot", type = PropertyType.PLOT, size = 5.0, unit = AreaUnit.CENTS, askingPrice = 20_00_000,
            registry = RegistryInfo(guideline = OfficialGuideline(value = 1200.0, unit = GvUnit.SQFT, method = OfficialGuideline.METHOD_WEBVIEW)),
        )
        val d = assertNotNull(propertyDuty(p, DutyRates()))
        assertEquals(26_13_600, d.guidelineValue)
        assertEquals(20_00_000, d.marketValue)
        assertTrue(d.baseIsGuideline)
        assertEquals(1_82_952, d.stampDuty)
        assertEquals(52_272, d.registrationFee)
        near(-23.477, d.gapPct!!, 1e-4)
        // A land area override (house plot / UDS) replaces the stated size.
        val house = p.copy(type = PropertyType.HOUSE, size = 1800.0, unit = AreaUnit.SQFT, registry = p.registry.copy(landAreaSqft = 1200.0))
        assertEquals(14_40_000, propertyDuty(house, DutyRates())!!.guidelineValue)
        // Without a guideline value: price only.
        val noGv = p.copy(registry = RegistryInfo())
        assertNull(propertyDuty(noGv, DutyRates())!!.guidelineValue)
        assertNull(propertyDuty(noGv.copy(askingPrice = 0), DutyRates()))
    }

    @Test
    fun indianMoneyFormatting() {
        assertEquals("₹45,000", moneyText(45_000, Lang.EN))
        assertEquals("₹26,13,600  (₹26.14 L)", moneyText(26_13_600, Lang.EN))
        assertEquals("₹1,82,95,200  (₹1.83 Cr)", moneyText(1_82_95_200, Lang.EN))
        assertEquals("₹2,00,00,000  (₹2 கோடி)", moneyText(2_00_00_000, Lang.TA))
    }

    // ---------------------------------------------------------------- reading captured values

    @Test
    fun parsesPortalAndPastedValues() {
        assertEquals(1200.0, parseGuidelineText("Rs. 1,200 per Sq.Ft")!!.value)
        assertEquals(GvUnit.SQFT, parseGuidelineText("Rs. 1,200 per Sq.Ft")!!.unit)
        val ha = parseGuidelineText("123/4B  Dry  ₹ 3,50,000 / Hectare")!!
        assertEquals(350_000.0, ha.value)
        assertEquals(GvUnit.HECTARE, ha.unit)
        assertEquals(GvUnit.SQM, parseGuidelineText("2400 per Sq.Mt")!!.unit)
        assertEquals(GvUnit.SQFT, parseGuidelineText("ரூ. 950 / சதுர அடி")!!.unit)
        assertEquals(950.0, parseGuidelineText("ரூ. 950 / சதுர அடி")!!.value)
        assertEquals(GvUnit.CENT, parseGuidelineText("1,20,000 per cent")!!.unit)
        assertEquals(GvUnit.ACRE, parseGuidelineText("ஏக்கர் ஒன்றுக்கு 8,00,000")!!.unit)
        // A street row: serial number and street name digits must not win over the rupee amount.
        assertEquals(1450.0, parseGuidelineText("12  2nd Cross Street  Residential  Rs.1,450")!!.value)
        assertEquals(1500.0, parseGuidelineText("1500")!!.value)
        assertNull(parseGuidelineText("Madurai South"))
        assertNull(parseGuidelineText(""))
        assertNull(unitIn("no unit 1200"))
    }

    @Test
    fun captureCandidatesPreferRowsWithAUnit() {
        val c = captureCandidates("", listOf("S.No 1 Value 2024", "Anna Nagar Main Road Rs. 3,200 per Sq.Ft", "Total 12"))
        assertEquals(3200.0, c.first().second.value)
        assertEquals(GvUnit.SQFT, c.first().second.unit)
        assertTrue(captureCandidates("", emptyList()).isEmpty())
    }

    @Test
    fun savedGuidelineKeepsSourceAndDate() {
        val g = officialGuideline(1200.0, GvUnit.SQFT, OfficialGuideline.METHOD_WEBVIEW, "https://tnreginet.gov.in/portal/x", "Rs 1,200 / Sq.Ft", " 01-07-2024 ", 1_700_000_000_000)
        assertEquals("tnreginet.gov.in", g.source)
        assertEquals("01-07-2024", g.effectiveFrom)
        assertEquals(1_700_000_000_000, g.capturedAt)
        val m = officialGuideline(900.0, GvUnit.SQFT, OfficialGuideline.METHOD_MANUAL, "", "900", "", 1)
        assertEquals("", m.source)
    }

    // ---------------------------------------------------------------- place / prefill

    @Test
    fun placeIsFilledFromTheAddress() {
        val p = Property(id = "a", title = "t", locality = "Thiruparankundram", district = "Madurai District", surveyNumber = "123/4B")
        val g = gvPlace(p)
        assertEquals("Madurai", g.district)
        assertEquals("Thiruparankundram", g.village)
        assertEquals("123/4B", g.survey)
        val saved = gvPlace(p.copy(registry = RegistryInfo(district = "Madurai", sro = "Thirupparankundram", village = "Harveypatti")))
        assertEquals("Harveypatti", saved.village)
        assertEquals("Thirupparankundram", saved.sro)
    }

    @Test
    fun prefillScriptIsSafeJavaScript() {
        val js = gvPrefillScript(gvPlace(Property(id = "a", title = "t", locality = "O\"Brien</script>", district = "Madurai", surveyNumber = "45/2A")))
        assertTrue("districtList" in js && "SROList" in js && "villageList" in js)
        assertTrue("\\\"Brien" in js) // quote escaped
        assertFalse("</script>" in js)
        assertTrue("survey:\"45\"" in js) // the survey field takes the number; the sub-division is chosen on the page
        assertFalse("click()" in js.substringAfter("var sv=").substringBefore("return")) // never presses Submit
        assertTrue("rows" in GV_CAPTURE_JS)
    }

    // ---------------------------------------------------------------- data + price check

    @Test
    fun olderFilesLoadAndNewFieldsRoundTrip() {
        val repo = JsonRepository(MemoryFileStore())
        assertTrue(repo.importJson("""{"version":2,"properties":[{"id":"x","title":"Old plot","size":5.0}],"settings":{"lang":"EN"}}"""))
        val p = repo.data.properties.single()
        assertEquals(RegistryInfo(), p.registry)
        assertEquals(7.0, repo.data.settings.stampDutyPct)
        repo.setRegistry("x", RegistryInfo(sro = "Madurai South", guideline = OfficialGuideline(1000.0, GvUnit.SQFT, "tnreginet.gov.in", "u", 5, "01-07-2024", OfficialGuideline.METHOD_WEBVIEW, "Rs 1000")))
        repo.setDutyRates(7.0, 1.0)
        val text = repo.exportJson()
        val back = Json { ignoreUnknownKeys = true }.decodeFromString(AppData.serializer(), text)
        assertEquals("Madurai South", back.properties.single().registry.sro)
        assertEquals(1000.0, back.properties.single().registry.guideline!!.value)
        assertEquals(1.0, back.settings.registrationFeePct)
    }

    @Test
    fun ownGuidelineFeedsThePriceCheckForLandOnly() {
        val plot = Property(id = "p", title = "Plot", type = PropertyType.PLOT, size = 10.0, unit = AreaUnit.CENTS, askingPrice = 30_00_000,
            registry = RegistryInfo(guideline = OfficialGuideline(1000.0, GvUnit.SQFT, "tnreginet.gov.in", "", 0, "", OfficialGuideline.METHOD_WEBVIEW)))
        val info = assertNotNull(ownGuidelineInfo(plot, PriceBasis.PER_CENT))
        near(435_600.0, info.value)
        // ₹3 L / cent is below the ₹4.36 L / cent guideline value -> suspicious, as with any guideline value.
        val check = checkPrice(plot, AppData(properties = listOf(plot)))
        near(435_600.0, check.guideline!!)
        assertTrue(check.belowGuideline)
        assertEquals(Verdict.SUSPICIOUS_LOW, check.verdict)
        // Houses: the land guideline value is not compared with a built-up price.
        assertNull(ownGuidelineInfo(plot.copy(type = PropertyType.HOUSE), PriceBasis.PER_SQFT))
        assertNull(ownGuidelineInfo(plot.copy(registry = RegistryInfo()), PriceBasis.PER_CENT))
    }
}
