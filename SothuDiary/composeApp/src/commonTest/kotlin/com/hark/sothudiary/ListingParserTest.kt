package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.OnlineRate
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.RateKind
import com.hark.sothudiary.data.RateLookup
import com.hark.sothudiary.domain.ListingParser
import com.hark.sothudiary.domain.PostClaim
import com.hark.sothudiary.domain.pricing.AreaPoint
import com.hark.sothudiary.domain.pricing.CompSource
import com.hark.sothudiary.domain.pricing.PriceRules
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkPrice
import com.hark.sothudiary.domain.pricing.estimateMarket
import com.hark.sothudiary.domain.pricing.freshLookup
import com.hark.sothudiary.domain.pricing.isFresh
import com.hark.sothudiary.domain.pricing.parseComparablesJson
import com.hark.sothudiary.domain.youtubeThumb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingParserTest {

    // ---------------- Captions: price, size, type, phone ----------------

    @Test fun englishTotalPriceLakhAndSizeCents() {
        val p = ListingParser.parse("5 cents DTCP approved plot near Jaihindpuram, Madurai\nPrice ₹25 lakhs only. Call 98765 43210")
        assertEquals(2_500_000L, p.totalPrice)
        assertFalse(p.totalFromRate)
        assertNull(p.ratePrice)
        assertEquals(5.0, p.size)
        assertEquals(AreaUnit.CENTS, p.unit)
        assertEquals(PropertyType.PLOT, p.type)
        assertEquals(listOf("9876543210"), p.phones)
        assertEquals("Madurai", p.district)
        assertTrue(PostClaim.DTCP in p.claims)
    }

    @Test fun indianCommasAndCrore() {
        assertEquals(2_500_000L, ListingParser.parse("Rate ₹25,00,000").totalPrice)
        val flat = ListingParser.parse("3 BHK flat 1.2 Cr")
        assertEquals(12_000_000L, flat.totalPrice)
        assertEquals(PropertyType.APARTMENT, flat.type)
    }

    @Test fun tamilPerCentRateGivesTotal() {
        val p = ListingParser.parse("மனை விற்பனைக்கு 4 சென்ட் , சென்ட் 3 லட்சம்")
        assertEquals(300_000L, p.ratePrice)
        assertEquals(AreaUnit.CENTS, p.rateUnit)
        assertEquals(4.0, p.size)
        assertEquals(AreaUnit.CENTS, p.unit)
        assertEquals(1_200_000L, p.totalPrice)
        assertTrue(p.totalFromRate)
        assertEquals(PropertyType.PLOT, p.type)
    }

    @Test fun englishPerCentRate() {
        val p = ListingParser.parse("Plot 10 cents, per cent ₹3.5L")
        assertEquals(350_000L, p.ratePrice)
        assertEquals(AreaUnit.CENTS, p.rateUnit)
        assertEquals(3_500_000L, p.totalPrice)
        assertTrue(p.totalFromRate)
    }

    @Test fun perSqftRateIsNotATotal() {
        val p = ListingParser.parse("Residential plot 2400 sq ft for sale. Rate Rs. 1,500 per sqft. Contact 9443012345")
        assertEquals(1_500L, p.ratePrice)
        assertEquals(AreaUnit.SQFT, p.rateUnit)
        assertEquals(2400.0, p.size)
        assertEquals(AreaUnit.SQFT, p.unit)
        assertEquals(3_600_000L, p.totalPrice)
        assertTrue(p.totalFromRate)
    }

    @Test fun perAcreRateAndTamilAcres() {
        val a = ListingParser.parse("2 ஏக்கர் விவசாய நிலம் 40 லட்சம்")
        assertEquals(2.0, a.size)
        assertEquals(AreaUnit.ACRES, a.unit)
        assertEquals(PropertyType.AGRI_LAND, a.type)
        assertEquals(4_000_000L, a.totalPrice)
        assertFalse(a.totalFromRate)

        val r = ListingParser.parse("Coconut farm land 3 acres, ₹18 lakhs per acre, well and EB available")
        assertEquals(1_800_000L, r.ratePrice)
        assertEquals(AreaUnit.ACRES, r.rateUnit)
        assertEquals(5_400_000L, r.totalPrice)
        assertEquals(PropertyType.FARMLAND, r.type)
        assertTrue(PostClaim.WATER in r.claims)
    }

    @Test fun groundsAreSqft() {
        val g = ListingParser.parse("2.5 grounds house for sale 90 lakhs")
        assertEquals(6000.0, g.size)
        assertEquals(AreaUnit.SQFT, g.unit)
        assertEquals(PropertyType.HOUSE, g.type)
        assertEquals(9_000_000L, g.totalPrice)
    }

    @Test fun phoneIsNotPriceOrSize() {
        val p = ListingParser.parse("Contact +91 9443012345 / 0 98765-43210")
        assertNull(p.totalPrice)
        assertNull(p.size)
        assertEquals(listOf("9443012345", "9876543210"), p.phones)
    }

    @Test fun builtUpAreaSeparateFromLand() {
        val p = ListingParser.parse("Independent house 3 cents, built up area 1450 sqft, price 65 lakhs")
        assertEquals(3.0, p.size)
        assertEquals(AreaUnit.CENTS, p.unit)
        assertEquals(1450.0, p.builtUpSqft)
        assertEquals(6_500_000L, p.totalPrice)
        assertEquals(PropertyType.HOUSE, p.type)
    }

    // ---------------- Place extraction ----------------

    @Test fun tamilLocativePlace() {
        val p = ListingParser.parse("பண்பொழியில் 50 சென்ட் தென்னந்தோப்பு விற்பனைக்கு, தென்காசி மாவட்டம். விலை 45 லட்சம்")
        assertEquals("பண்பொழி", p.placeText)
        assertEquals("Tenkasi", p.district)
        assertEquals(50.0, p.size)
        assertEquals(4_500_000L, p.totalPrice)
        assertEquals(PropertyType.FARMLAND, p.type)
        assertEquals("பண்பொழி, Tenkasi", p.geocodeQuery())
    }

    @Test fun tamilStems() {
        assertEquals("பண்பொழி", ListingParser.tamilPlaceStem("பண்பொழியில்"))
        assertEquals("கடையம்", ListingParser.tamilPlaceStem("கடையத்தில்"))
        assertNull(ListingParser.tamilPlaceStem("Panpoli"))
    }

    @Test fun tamilNearPlace() {
        val p = ListingParser.parse("ஆலங்குளம் அருகே 10 சென்ட் வீட்டு மனை")
        assertEquals("ஆலங்குளம்", p.placeText)
        assertEquals(PropertyType.PLOT, p.type)
    }

    @Test fun englishNearPlace() {
        val p = ListingParser.parse("5 cent plot for sale near Panpoli, Tenkasi district. 12 lakhs")
        assertEquals("Panpoli", p.placeText)
        assertEquals("Tenkasi", p.district)
        assertEquals(1_200_000L, p.totalPrice)
    }

    @Test fun locationLabelWins() {
        val p = ListingParser.parse("Farm land for sale 1.5 acres\nLocation: Kadayam, Tenkasi\nPrice 30 lakhs per acre")
        assertEquals("Kadayam", p.placeCandidates.first())
        assertEquals("Tenkasi", p.district)
        assertEquals(1.5, p.size)
        assertEquals(AreaUnit.ACRES, p.unit)
        assertEquals(3_000_000L, p.ratePrice)
        assertEquals(4_500_000L, p.totalPrice)
    }

    @Test fun districtAloneIsNotAPlaceCandidate() {
        val p = ListingParser.parse("Plot for sale in Madurai 20 lakhs")
        assertTrue(p.placeCandidates.none { it.equals("Madurai", true) })
        assertEquals("Madurai", p.district)
    }

    @Test fun youtubeThumbnail() {
        assertEquals("https://img.youtube.com/vi/abcDEF12345/hqdefault.jpg", youtubeThumb("https://youtu.be/abcDEF12345?si=x"))
        assertNotNull(youtubeThumb("https://www.youtube.com/shorts/abcDEF12345"))
    }

    // ---------------- Live price check rules ----------------

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val lat = 8.95
    private val lng = 77.30
    /** ~1 km of latitude. */
    private val km = 1 / 111.2

    private fun rate(id: String, dKm: Double, perCent: Double, group: Int = 0, ageDays: Int = 1, kind: RateKind = RateKind.ASKING) =
        OnlineRate(id, "Place $id", "Tenkasi", lat + dKm * km, lng, group, kind, "cent", null, null, perCent,
            fetchedAt = now - ageDays * day)

    private fun plot(lat: Double? = this.lat, lng: Double? = this.lng, perCent: Long = 300_000) =
        Property(id = "me", title = "10 cent plot", type = PropertyType.PLOT, locality = "Panpoli", district = "Tenkasi",
            lat = lat, lng = lng, size = 10.0, unit = AreaUnit.CENTS, askingPrice = perCent * 10)

    @Test fun usesFiveKmFirst() {
        val data = AppData(onlineRates = listOf(rate("a", 1.0, 300_000.0), rate("b", 2.0, 320_000.0), rate("c", 4.0, 280_000.0), rate("d", 8.0, 900_000.0)))
        val e = estimateMarket(AreaPoint(listOf("Panpoli"), lat, lng, "Tenkasi"), 0, data, now = now)
        assertEquals(PriceRules.BASE_KM, e.radiusKm)
        assertEquals(3, e.comparables.size)
        assertFalse(e.widened)
        assertEquals(300_000.0, e.median)
        assertTrue(e.comparables.all { it.source == CompSource.ONLINE })
    }

    @Test fun widensToTenKmButNeverBeyond() {
        val data = AppData(onlineRates = listOf(rate("a", 2.0, 300_000.0), rate("b", 7.0, 340_000.0), rate("far", 12.0, 100_000.0), rate("far2", 25.0, 50_000.0)))
        val e = estimateMarket(AreaPoint(listOf("Panpoli"), lat, lng), 0, data, now = now)
        assertEquals(PriceRules.MAX_KM, e.radiusKm)
        assertTrue(e.widened)
        assertEquals(2, e.comparables.size)
        assertTrue(e.comparables.all { (it.distanceKm ?: 99.0) <= 10.0 })
    }

    @Test fun onlyFarAwayDataIsNotEnough() {
        val data = AppData(onlineRates = listOf(rate("far", 11.0, 300_000.0), rate("far2", 30.0, 310_000.0)))
        val c = checkPrice(plot(), data.copy(properties = listOf(plot())))
        assertTrue(c.estimate.insufficient)
        assertEquals(0, c.comparableCount)
        assertEquals(Verdict.UNKNOWN, c.verdict)
    }

    @Test fun sameTypeOnly() {
        // Farmland (group 1) and house (group 2) rates right next to the plot must not be used for it.
        val data = AppData(onlineRates = listOf(rate("farm", 1.0, 50_000.0, group = 1), rate("house", 1.0, 3_000.0, group = 2)))
        val e = estimateMarket(AreaPoint(emptyList(), lat, lng), 0, data, now = now)
        assertTrue(e.insufficient)
        val own = plot().copy(id = "other", type = PropertyType.FARMLAND, size = 1.0, unit = AreaUnit.ACRES, askingPrice = 2_000_000)
        val e2 = estimateMarket(AreaPoint(emptyList(), lat, lng), 0, AppData(properties = listOf(own)), now = now)
        assertTrue(e2.insufficient)
    }

    @Test fun ownSavedPropertiesOfSameTypeCount() {
        val other = plot(lat = lat + 1.5 * km, perCent = 280_000).copy(id = "other")
        val me = plot()
        val c = checkPrice(me, AppData(properties = listOf(me, other)))
        assertEquals(1, c.comparableCount)
        assertEquals(CompSource.YOUR_LISTING, c.estimate.comparables.single().source)
        assertNotNull(c.marketMedian)
    }

    @Test fun noPinMeansNotEnoughData() {
        val data = AppData(onlineRates = listOf(rate("a", 0.5, 300_000.0), rate("b", 1.0, 310_000.0), rate("c", 1.5, 290_000.0)))
        val me = plot(lat = null, lng = null)
        val c = checkPrice(me, data.copy(properties = listOf(me)))
        assertFalse(c.estimate.hasPin)
        assertTrue(c.estimate.insufficient)
        assertEquals(Verdict.UNKNOWN, c.verdict)
    }

    @Test fun cacheFreshnessThirtyDays() {
        assertTrue(isFresh(now - 29 * day, now))
        assertFalse(isFresh(now - 31 * day, now))
        val data = AppData(onlineRates = listOf(rate("old", 1.0, 300_000.0, ageDays = 40), rate("new", 2.0, 310_000.0, ageDays = 3)))
        val e = estimateMarket(AreaPoint(emptyList(), lat, lng), 0, data, now = now)
        assertEquals(listOf("Place new"), e.comparables.map { it.label })

        val lookups = listOf(
            RateLookup("k", lat, lng, 0, "Panpoli", fetchedAt = now - 10 * day),
            RateLookup("k2", lat, lng, 1, "Panpoli", fetchedAt = now - 45 * day),
        )
        assertNotNull(freshLookup(AppData(rateLookups = lookups), lat + 0.5 * km, lng, 0, now))
        assertNull(freshLookup(AppData(rateLookups = lookups), lat, lng, 1, now)) // too old
        assertNull(freshLookup(AppData(rateLookups = lookups), lat + 5 * km, lng, 0, now)) // another place
    }

    @Test fun verdicts() {
        val data = AppData(onlineRates = listOf(rate("a", 1.0, 300_000.0), rate("b", 2.0, 300_000.0), rate("c", 3.0, 300_000.0)))
        fun v(perCent: Long) = plot(perCent = perCent).let { checkPrice(it, data.copy(properties = listOf(it))).verdict }
        assertEquals(Verdict.FAIR, v(300_000))
        assertEquals(Verdict.GOOD, v(240_000))
        assertEquals(Verdict.SUSPICIOUS_LOW, v(150_000))
        assertEquals(Verdict.TOO_HIGH, v(600_000))
    }

    @Test fun guidelineFromLookupFlagsBelowGuideline() {
        val data = AppData(onlineRates = listOf(
            rate("a", 1.0, 300_000.0), rate("b", 2.0, 300_000.0), rate("c", 3.0, 300_000.0),
            rate("g", 0.5, 200_000.0, kind = RateKind.GUIDELINE),
        ))
        val me = plot(perCent = 180_000)
        val c = checkPrice(me, data.copy(properties = listOf(me)))
        assertEquals(200_000.0, c.guideline)
        assertTrue(c.belowGuideline)
        assertEquals(Verdict.SUSPICIOUS_LOW, c.verdict)
        // Guideline rows are never used as asking-price comparables.
        assertEquals(3, c.comparableCount)
    }

    // ---------------- Gemini response ----------------

    @Test fun parsesGeminiJson() {
        val text = """
            Here is what I found:
            ```json
            {"comparables":[
              {"locality":"Panpoli","district":"Tenkasi","lat":8.96,"lng":77.31,"kind":"asking","unit":"cent","min":250000,"max":350000,"typical":300000,"source_name":"99acres","source_url":"https://www.99acres.com/x","date":"Oct 2026","note":"3 listings"},
              {"locality":"Ilanji","district":"Tenkasi","lat":null,"lng":null,"kind":"guideline","unit":"sqft","min":450,"max":null,"typical":null,"source_name":"TNREGINET","source_url":"https://tnreginet.gov.in","date":"2024","note":"guideline"}
            ]}
            ```
        """.trimIndent()
        val list = parseComparablesJson(text)
        assertEquals(2, list.size)
        assertEquals("\"Panpoli\"", list[0]["locality"].toString())
        assertEquals("300000", list[0]["typical"].toString())
        assertEquals("\"guideline\"", list[1]["kind"].toString())

        // Unfenced, with text around it, and the older {"rates":[…]} shape.
        assertEquals(1, parseComparablesJson("""Sure. {"comparables":[{"locality":"Kadayam","typical":120000}]} Done.""").size)
        assertEquals(1, parseComparablesJson("""{"rates":[{"place":"Ilanji","min":1}]}""").size)
        assertTrue(parseComparablesJson("Sorry, I could not find anything.").isEmpty())
        assertTrue(parseComparablesJson("""{"comparables":[]}""").isEmpty())
    }
}
