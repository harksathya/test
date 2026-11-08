package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.OnlineRate
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.pricing.AreaPoint
import com.hark.sothudiary.domain.pricing.LookupErrorKind
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkPrice
import com.hark.sothudiary.domain.pricing.classifyLookupError
import com.hark.sothudiary.domain.pricing.estimateMarket
import com.hark.sothudiary.domain.pricing.isQuotaError
import com.hark.sothudiary.domain.pricing.parseGeminiError
import com.hark.sothudiary.domain.pricing.parseRetryDelay
import com.hark.sothudiary.domain.pricing.pickModels
import com.hark.sothudiary.domain.pricing.rawErrorText
import com.hark.sothudiary.domain.pricing.taggedError
import com.hark.sothudiary.domain.unitPrice
import com.hark.sothudiary.ui.components.lookupErrorMessage
import com.hark.sothudiary.ui.map.fitCamera
import com.hark.sothudiary.ui.map.isValidCoord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V212Test {
    private val quotaBody = """{"error":{"code":429,"message":"You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits. Please retry in 37.4s.","status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure"},{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"41s"}]}}"""

    // ---------- error classification ----------

    @Test fun quotaErrorParsedWithRetryDelay() {
        val e = parseGeminiError(429, quotaBody)
        assertTrue(isQuotaError(e.code, e.status, e.message))
        assertEquals(41, e.retryDelaySec) // RetryInfo.retryDelay wins over the text
        assertEquals(38, parseRetryDelay("Please retry in 37.4s."))
        assertEquals(5, parseRetryDelay("5s"))
        assertNull(parseRetryDelay("limit 60s per minute")) // no "retry": not a delay
    }

    @Test fun classifyErrors() {
        assertEquals(LookupErrorKind.QUOTA, classifyLookupError(taggedError(LookupErrorKind.QUOTA, "x")))
        // Plain text saved by v2.1.1 (the user's screenshot):
        assertEquals(LookupErrorKind.QUOTA, classifyLookupError("You exceeded your current quota, please check your plan and billing details. aistudio.google.com/docs/rate-limits"))
        assertEquals(LookupErrorKind.BAD_KEY, classifyLookupError("API key not valid. Please pass a valid API key."))
        assertEquals(LookupErrorKind.NETWORK, classifyLookupError("network"))
        assertEquals(LookupErrorKind.NO_KEY, classifyLookupError("no_key"))
        assertEquals(LookupErrorKind.MODEL_UNAVAILABLE, classifyLookupError("models/gemini-1.0-pro is not found for API version v1beta"))
        assertEquals(LookupErrorKind.OTHER, classifyLookupError("Internal error encountered."))
        assertEquals("raw text", rawErrorText("[quota] raw text"))
    }

    @Test fun friendlyMessages() {
        val now = 1_000_000L
        val q = taggedError(LookupErrorKind.QUOTA, "You exceeded your current quota")
        assertTrue(lookupErrorMessage(q, 0, com.hark.sothudiary.data.Lang.EN, now).startsWith("The AI service's free limit is used up for now")) // 2.6.0: any provider (issue #1)
        assertTrue("41 seconds" in lookupErrorMessage(q, now + 41_000, com.hark.sothudiary.data.Lang.EN, now))
        assertTrue("இலவச வரம்பு" in lookupErrorMessage(q, 0, com.hark.sothudiary.data.Lang.TA, now))
        assertFalse("exceeded" in lookupErrorMessage(q, 0, com.hark.sothudiary.data.Lang.EN, now)) // raw text only under Details
    }

    @Test fun modelOrderForFreeKeys() {
        val ids = listOf("gemini-3-pro-preview", "gemini-3-flash-preview", "gemini-2.5-flash", "gemini-2.5-flash-lite",
            "gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-pro", "gemini-2.0-flash-exp")
        val p = pickModels(ids)
        assertEquals("gemini-flash-latest", p[0])
        assertEquals("gemini-2.5-flash", p[1])
        assertEquals("gemini-flash-lite-latest", p[2])
        assertEquals("gemini-2.5-flash-lite", p[3])
        assertTrue(p.indexOf("gemini-3-flash-preview") > 3)
        assertTrue(p.none { "pro" in it })
    }

    // ---------- self-exclusion ----------

    private val panpoli = Property(
        id = "me", title = "Thirumalai kovil Highway property", type = PropertyType.LAND, locality = "Panpoli", district = "Tenkasi",
        lat = 9.0123, lng = 77.2468, size = 10.0, unit = AreaUnit.CENTS, askingPrice = 2_500_000, sourceUrl = "https://www.instagram.com/p/abc/",
    )

    @Test fun propertyIsNeverItsOwnComparable() {
        // The same listing saved twice (another id, same spot and price) and the live lookup finding the post itself.
        val copy = panpoli.copy(id = "copy", lat = 9.0125, lng = 77.2469)
        val selfOnline = OnlineRate(id = "o1", place = "Panpoli", lat = 9.0123, lng = 77.2468, group = 0, typical = 250_000.0,
            unit = "cent", sourceUrl = "https://www.instagram.com/p/abc", fetchedAt = nowMillis())
        val data = AppData(properties = listOf(panpoli, copy), onlineRates = listOf(selfOnline))
        val check = checkPrice(panpoli, data)
        assertTrue(check.estimate.comparables.isEmpty(), check.estimate.comparables.toString())
        assertTrue(check.estimate.insufficient)
        assertEquals(Verdict.UNKNOWN, check.verdict)
        assertNull(check.estimate.median)
    }

    @Test fun areaRatesFromPropertyExcludesIt() {
        val data = AppData(properties = listOf(panpoli))
        val point = AreaPoint(listOf("Panpoli"), panpoli.lat, panpoli.lng, "Tenkasi")
        // v2.1.1 behaviour (no exclusion) produced "Your listing · 0 km away" as the only comparable.
        assertEquals(1, estimateMarket(point, 0, data).comparables.size)
        val est = estimateMarket(point, 0, data, excludePropertyId = panpoli.id, selfRate = panpoli.unitPrice())
        assertTrue(est.comparables.isEmpty())
        assertTrue(est.insufficient)
        assertNull(est.median)
    }

    @Test fun realNeighboursStayIn() {
        val near = panpoli.copy(id = "n1", lat = 9.0200, lng = 77.2500, askingPrice = 3_000_000) // ~0.9 km, other price
        val samePriceFar = panpoli.copy(id = "n2", lat = 9.0300, lng = 77.2468) // same price but ~2 km away
        val est = checkPrice(panpoli, AppData(properties = listOf(panpoli, near, samePriceFar))).estimate
        assertEquals(setOf("n1", "n2"), est.comparables.mapNotNull { it.propertyId }.toSet())
    }

    // ---------- map camera ----------

    @Test fun invalidCoordinates() {
        assertFalse(isValidCoord(null, 77.0))
        assertFalse(isValidCoord(0.0, 0.0))
        assertFalse(isValidCoord(Double.NaN, 77.0))
        assertFalse(isValidCoord(95.0, 77.0))
        assertTrue(isValidCoord(9.0123, 77.2468))
    }

    @Test fun cameraCentresOnPinEvenForDegenerateBox() {
        // The v2.1.1 bug: pin + its own listing at the same spot -> zero-size box -> map over Guatemala.
        val cam = fitCamera(listOf(9.0123 to 77.2468, 9.0123 to 77.2468), 9.0123, 77.2468)
        assertNotNull(cam)
        assertEquals(9.0123, cam.lat)
        assertEquals(77.2468, cam.lng)
        assertEquals(16.0, cam.zoom)
    }

    @Test fun cameraFitsComparablesWithinLimits() {
        val cam = fitCamera(listOf(9.0123 to 77.2468, 9.10 to 77.30, 0.0 to 0.0), 9.0123, 77.2468)!!
        assertEquals(9.0123, cam.lat) // centred on the property, the (0,0) point ignored
        assertTrue(cam.zoom in 9.0..14.0, cam.zoom.toString())
        assertNull(fitCamera(listOf(0.0 to 0.0), null, null))
        val noAnchor = fitCamera(listOf(9.0 to 77.0, 10.0 to 78.0), null, null, minZoom = 5.0)!!
        assertEquals(9.5, noAnchor.lat, 1e-9)
        assertEquals(77.5, noAnchor.lng, 1e-9)
    }
}
