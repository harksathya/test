package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.market.MarketData
import com.hark.sothudiary.domain.ListingParser
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkPrice
import com.hark.sothudiary.domain.youtubeThumb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingParserTest {
    @Test fun totalPriceLakhAndSizeCents() {
        val p = ListingParser.parse("5 cents DTCP approved plot near Jaihindpuram, Madurai\nPrice ₹25 lakhs only. Call 98765 43210")
        assertEquals(2_500_000L, p.totalPrice)
        assertEquals(5.0, p.size)
        assertEquals(AreaUnit.CENTS, p.unit)
        assertEquals(PropertyType.PLOT, p.type)
        assertEquals(listOf("9876543210"), p.phones)
        assertEquals("Jaihindpuram", p.locality?.name)
        assertEquals("Madurai", p.district)
    }

    @Test fun indianCommasAndCrore() {
        assertEquals(2_500_000L, ListingParser.parse("Rate ₹25,00,000").totalPrice)
        assertEquals(12_000_000L, ListingParser.parse("3 BHK flat 1.2 Cr").totalPrice)
        assertEquals(PropertyType.APARTMENT, ListingParser.parse("3 BHK flat 1.2 Cr").type)
    }

    @Test fun tamilPerCentRate() {
        val p = ListingParser.parse("மனை விற்பனைக்கு 4 சென்ட் , சென்ட் 3 லட்சம்")
        assertEquals(300_000L, p.ratePrice)
        assertEquals(AreaUnit.CENTS, p.rateUnit)
        assertEquals(4.0, p.size)
        assertEquals(1_200_000L, p.totalPrice)
        assertEquals(PropertyType.PLOT, p.type)
    }

    @Test fun englishPerCentRate() {
        val p = ListingParser.parse("Plot 10 cents, per cent ₹3.5L")
        assertEquals(350_000L, p.ratePrice)
        assertEquals(3_500_000L, p.totalPrice)
    }

    @Test fun groundsAndAcres() {
        val g = ListingParser.parse("2.5 grounds house for sale 90 lakhs")
        assertEquals(6000.0, g.size)
        assertEquals(AreaUnit.SQFT, g.unit)
        assertEquals(PropertyType.HOUSE, g.type)
        val a = ListingParser.parse("2 ஏக்கர் விவசாய நிலம் 40 லட்சம்")
        assertEquals(2.0, a.size)
        assertEquals(AreaUnit.ACRES, a.unit)
        assertEquals(PropertyType.AGRI_LAND, a.type)
        assertEquals(4_000_000L, a.totalPrice)
    }

    @Test fun phoneIsNotPrice() {
        val p = ListingParser.parse("Contact +91 9443012345")
        assertNull(p.totalPrice)
        assertEquals(listOf("9443012345"), p.phones)
    }

    @Test fun youtubeThumbnail() {
        assertEquals("https://img.youtube.com/vi/abcDEF12345/hqdefault.jpg", youtubeThumb("https://youtu.be/abcDEF12345?si=x"))
        assertNotNull(youtubeThumb("https://www.youtube.com/shorts/abcDEF12345"))
    }

    @Test fun priceCheckAlwaysHasMarketForPlots() {
        assertTrue(MarketData.dataset.localities.isNotEmpty())
        val p = Property(id = "x", title = "t", type = PropertyType.PLOT, locality = "Jaihindpuram", lat = 9.905, lng = 78.112,
            size = 5.0, unit = AreaUnit.CENTS, askingPrice = 5_000_000)
        val c = checkPrice(p, AppData(properties = listOf(p)))
        assertNotNull(c.marketMedian)
        assertTrue(c.comparableCount >= 1)
        assertTrue(c.verdict != Verdict.UNKNOWN)
        val farm = p.copy(type = PropertyType.FARMLAND, size = 1.0, unit = AreaUnit.ACRES, askingPrice = 4_000_000, locality = "Somewhere", lat = null, lng = null)
        assertNotNull(checkPrice(farm, AppData(properties = listOf(farm))).marketMedian)
    }
}
