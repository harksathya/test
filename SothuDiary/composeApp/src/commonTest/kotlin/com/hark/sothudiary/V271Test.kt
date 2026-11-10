package com.hark.sothudiary

import com.hark.sothudiary.domain.land.OVERPASS_SLOTS_PER_SERVER
import com.hark.sothudiary.domain.land.OverpassResult
import com.hark.sothudiary.domain.land.SourceStatusLine
import com.hark.sothudiary.domain.land.VectorTileRules
import com.hark.sothudiary.domain.land.fetchGroup
import com.hark.sothudiary.domain.land.httpCodeOf
import com.hark.sothudiary.domain.land.overpassRetryable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V271Test {
    @Test fun httpCodeIsReadFromTheStatusLine() {
        assertEquals(400, httpCodeOf(SourceStatusLine("x", false, "HTTP 400 – query rejected", detail = "POST https://a/api · HTTP 400 · bad")))
        assertEquals(504, httpCodeOf(SourceStatusLine("x", false, "busy", detail = "POST https://a/api · HTTP 504")))
        assertNull(httpCodeOf(SourceStatusLine("x", false, "timeout", detail = "POST https://a/api · HTTP -1 · timeout")))
        assertNull(httpCodeOf(SourceStatusLine("x", false, "timeout")))
    }

    @Test fun rejectedQueryIsNotSentToOtherServers() = runBlocking {
        val calls = mutableListOf<String>()
        val (res, _) = fetchGroup("q", 0, "(test)", budgetMs = 20_000, hedgeMs = 2_000, endpoints = listOf("https://a/api", "https://b/api"),
            attempt = { url, _, _, _ ->
                calls += url
                OverpassResult(emptyList(), "[other] Overpass HTTP 400") to SourceStatusLine("Overpass · a", false, "HTTP 400", detail = "POST $url · HTTP 400")
            })
        assertNotNull(res.error)
        assertEquals(listOf("https://a/api"), calls)
        assertTrue(!overpassRetryable(400) && overpassRetryable(504) && overpassRetryable(null))
    }

    @Test fun busyServerIsStillRetriedElsewhere() = runBlocking {
        val calls = mutableListOf<String>()
        fetchGroup("q", 0, "(test)", budgetMs = 10_000, hedgeMs = 2_000, endpoints = listOf("https://a/api", "https://b/api"),
            attempt = { url, _, _, _ ->
                calls += url
                OverpassResult(emptyList(), "[other] Overpass HTTP 504") to SourceStatusLine("Overpass", false, "busy", detail = "POST $url · HTTP 504")
            })
        assertTrue("https://b/api" in calls)
    }

    @Test fun tilesAreAskedSoonAndBothGroupsGetASlot() {
        assertTrue(VectorTileRules.HEDGE_MS <= 5_000L)
        assertTrue(VectorTileRules.HEDGE_MS + VectorTileRules.OVERPASS_GRACE_MS <= 10_000L)
        assertEquals(2, OVERPASS_SLOTS_PER_SERVER)
    }
}
