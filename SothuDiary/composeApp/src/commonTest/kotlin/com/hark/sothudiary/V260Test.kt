package com.hark.sothudiary

import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.LandCheck
import com.hark.sothudiary.data.MemoryFileStore
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.RateLookup
import com.hark.sothudiary.data.SourceStatus
import com.hark.sothudiary.data.WebSnippet
import com.hark.sothudiary.domain.ai.LlmConfig
import com.hark.sothudiary.domain.ai.LlmProviders
import com.hark.sothudiary.domain.ai.ModelInfo
import com.hark.sothudiary.domain.ai.isPremium
import com.hark.sothudiary.domain.ai.rankChatModels
import com.hark.sothudiary.domain.land.LandReason
import com.hark.sothudiary.domain.land.LandRules
import com.hark.sothudiary.domain.land.applyLandAi
import com.hark.sothudiary.domain.land.growthTile
import com.hark.sothudiary.domain.land.landCheckKey
import com.hark.sothudiary.domain.land.runLandAi
import com.hark.sothudiary.domain.pricing.Grounded
import com.hark.sothudiary.domain.pricing.LookupErrorKind
import com.hark.sothudiary.domain.pricing.taggedError
import com.hark.sothudiary.domain.share.ImportMode
import com.hark.sothudiary.domain.share.SOTHU_MIME
import com.hark.sothudiary.domain.share.SOTHU_PICK_TYPES
import com.hark.sothudiary.domain.share.ShareOptions
import com.hark.sothudiary.domain.share.decodeShare
import com.hark.sothudiary.domain.share.encodeShare
import com.hark.sothudiary.domain.share.importCodeForFile
import com.hark.sothudiary.domain.share.shareFileText
import com.hark.sothudiary.domain.share.sharedCopy
import com.hark.sothudiary.ui.components.lookupErrorMessage
import com.hark.sothudiary.ui.screens.onlineStatusLine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.6.0: AI search on tap (issue #1 leftovers), provider-neutral messages, .sothu import. */
class V260Test {

    private val prop = Property(id = "p1", title = "10 cent plot, Thirumangalam", type = PropertyType.LAND, lat = 9.8236, lng = 77.9870,
        askingPrice = 1_500_000, size = 10.0, district = "Madurai", locality = "Thirumangalam")

    private fun base() = LandCheck("p1", landCheckKey(prop), 9.8236, 77.9870, 5L, osmOk = true,
        status = listOf(SourceStatus("Overpass", true, "120 elements"), SourceStatus("${LandRules.AI_STATUS_PREFIX} (OpenAI)", null, "not run yet")))

    private val gemini = LlmConfig(LlmProviders.GEMINI, "AIza-test")
    private val openai = LlmConfig(LlmProviders.OPENAI, "sk-test")

    // ---------------- .sothu import ----------------

    @Test fun importCodeFromAPickedFile() {
        val code = encodeShare(sharedCopy(prop, null, null, ShareOptions(sharedBy = "Sathya")))
        val file = shareFileText(code, prop.title)
        assertEquals(code, importCodeForFile(file))
        assertEquals(prop.title, decodeShare(importCodeForFile(file)!!)!!.p.title)
        // A share link pasted into a text file works too.
        assertEquals(code, importCodeForFile("Look: sothudiary://p?d=$code"))
        // Not ours: the dialog says so ("bad" never decodes).
        assertEquals("bad", importCodeForFile("{\"app\":\"other\",\"d\":\"x\"}"))
        assertEquals("bad", importCodeForFile(""))
        assertNull(decodeShare("bad"))
        // Cancelled picker: nothing to show.
        assertNull(importCodeForFile(null))
    }

    @Test fun pickerAcceptsRetypedFiles() {
        assertTrue(SOTHU_MIME in SOTHU_PICK_TYPES)
        assertTrue("application/octet-stream" in SOTHU_PICK_TYPES, "WhatsApp / Downloads re-type .sothu files")
        assertTrue("*/*" in SOTHU_PICK_TYPES)
    }

    @Test fun importedFileAddsThenUpdates() {
        val repo = JsonRepository(MemoryFileStore())
        val sp = decodeShare(importCodeForFile(shareFileText(encodeShare(sharedCopy(prop, null, null, ShareOptions())), prop.title))!!)!!
        val id = repo.importShared(sp, ImportMode.ADD_NEW)
        assertEquals(1, repo.data.properties.size)
        assertEquals(prop.title, repo.data.properties.first { it.id == id }.title)
        val changed = sp.copy(p = sp.p.copy(askingPrice = 1_400_000))
        val id2 = repo.importShared(changed, ImportMode.UPDATE_EXISTING)
        assertEquals(id, id2)
        assertEquals(1, repo.data.properties.size)
        assertEquals(1_400_000, repo.data.properties.first().askingPrice)
    }

    // ---------------- land check AI on tap ----------------

    @Test fun landAiAnswerIsApplied() {
        val text = """{"zoning":{"value":"residential","plan":"Madurai LPA","note":"","source_url":"https://example.org/plan"},
            "roads":[{"name":"Ring road","status":"proposed","distance_km":2.5,"date":"2026","source_url":"https://example.org/road"}],
            "growth":{"rating":"moderate","reason":"new layouts","source_url":"https://example.org/growth"}}"""
        val c = applyLandAi(base(), Grounded.Ok(text, listOf("https://example.org/plan"), "gemini-2.5-flash"), 1200, gemini, 99L)
        assertNotNull(c.ai)
        assertEquals("residential", c.ai!!.zoning)
        assertNull(c.aiError)
        assertEquals(99L, c.aiFetchedAt)
        // One AI line, replaced – the map lines stay.
        assertEquals(1, c.status.count { it.source.startsWith(LandRules.AI_STATUS_PREFIX) })
        assertEquals(true, c.status.last().ok)
        assertTrue(c.status.any { it.source == "Overpass" })
        assertEquals(LandReason.GROWTH_AI, growthTile(c, true).reason)
    }

    @Test fun landAiFromWebSearchKeepsOnlyCitedItems() {
        val hits = listOf(WebSnippet("Plan", "https://example.org/plan", "residential zone"))
        val text = """{"zoning":{"value":"residential","source_url":"https://example.org/plan"},
            "roads":[{"name":"Made up road","status":"proposed","source_url":"https://not-a-result.example/x"}],
            "growth":{"rating":"high","reason":"?","source_url":"https://not-a-result.example/y"}}"""
        val c = applyLandAi(base(), Grounded.Ok(text, hits.map { it.url }, "gpt-x (web search)", viaSearch = true, hits = hits), 900, openai, 7L)
        assertEquals("residential", c.ai!!.zoning)
        assertTrue(c.ai!!.roads.isEmpty(), "uncited road dropped")
        assertEquals("unknown", c.ai!!.growth, "uncited growth dropped")
        assertTrue(c.aiViaSearch)
    }

    @Test fun landAiFailureIsShownNotSilent() {
        val earlier = base().copy(ai = com.hark.sothudiary.data.LandAi(zoning = "agricultural"), aiFetchedAt = 3L)
        val c = applyLandAi(earlier, Grounded.Failed(taggedError(LookupErrorKind.QUOTA, "limit"), 0L, "gpt-x",
            snippets = listOf(WebSnippet("t", "https://a.example", "s"))), 500, openai, 9L)
        assertEquals("agricultural", c.ai!!.zoning, "an earlier answer is kept")
        assertEquals(LookupErrorKind.QUOTA, com.hark.sothudiary.domain.pricing.classifyLookupError(c.aiError))
        assertEquals(false, c.status.last().ok)
        assertEquals("free quota used up for now", c.status.last().reason)
        assertEquals(1, c.webSnippets.size, "search results still shown")
    }

    @Test fun landAiWithoutKeyNeedsNoNetwork() = runBlocking {
        val c = runLandAi(prop, LlmConfig(LlmProviders.GROQ, ""), "plot", base())
        assertEquals("no_key", c.aiError)
        assertEquals("no AI key", c.status.last().reason)
    }

    @Test fun growthTileSaysNotSearchedYet() {
        assertEquals(LandReason.GROWTH_NOT_RUN, growthTile(base(), true).reason)
        assertEquals(LandReason.GROWTH_NO_KEY, growthTile(base(), false).reason)
        assertEquals(LandReason.GROWTH_UNKNOWN, growthTile(base().copy(aiError = "[other] x"), true).reason)
        // Nothing third-party is read automatically.
        assertFalse(LandRules.AUTO_AI_SEARCH)
        assertTrue(LandRules.AI_ON_TAP)
    }

    // ---------------- price check: online line ----------------

    @Test fun priceOnlineStatusLine() {
        assertNull(onlineStatusLine(Lang.EN, false, null).ok)
        assertTrue(onlineStatusLine(Lang.EN, true, null).reason.contains("not searched"))
        val failed = RateLookup("k", 9.8, 77.9, 0, fetchedAt = 1L, error = taggedError(LookupErrorKind.BAD_KEY, "Incorrect API key"))
        assertEquals(false, onlineStatusLine(Lang.EN, true, failed).ok)
        assertTrue(onlineStatusLine(Lang.EN, true, failed).reason.contains("refused"))
        val ok = RateLookup("k", 9.8, 77.9, 0, fetchedAt = 1L, found = 4)
        assertEquals(true, onlineStatusLine(Lang.EN, true, ok).ok)
        assertTrue(onlineStatusLine(Lang.EN, true, ok).reason.startsWith("4 rates"))
    }

    // ---------------- issue #1: nothing says Gemini is required ----------------

    @Test fun errorMessagesDoNotNameGemini() {
        for (k in listOf(LookupErrorKind.QUOTA, LookupErrorKind.BAD_KEY, LookupErrorKind.MODEL_UNAVAILABLE, LookupErrorKind.NO_KEY)) {
            for (lang in Lang.entries) {
                val m = lookupErrorMessage(taggedError(k, "x"), 0L, lang)
                assertFalse("Gemini" in m || "ஜெமினி" in m, "$k/$lang: $m")
            }
        }
        val s6 = com.hark.sothudiary.i18n.S6
        for (t in listOf(s6.landAiNeedKey, s6.priceNeedKey, s6.lrGrowthNoKey, s6.srcAi, s6.srcWebSearch, s6.viaSearchNote, s6.priceRulesText))
            assertFalse("Gemini key" in t.en || "Gemini (your" in t.en, t.en)
    }

    @Test fun autoModelSkipsPremiumTiers() {
        val listed = listOf(ModelInfo("gpt-5-pro", 300), ModelInfo("gpt-5", 200), ModelInfo("gpt-5-mini", 100), ModelInfo("o3-pro", 250))
        assertEquals(listOf("gpt-5", "gpt-5-mini", "gpt-5-pro", "o3-pro"), rankChatModels(LlmProviders.OPENAI, listed))
        assertEquals("claude-sonnet-x", rankChatModels(LlmProviders.ANTHROPIC, listOf(ModelInfo("claude-opus-x", 2), ModelInfo("claude-sonnet-x", 1))).first())
        assertTrue(isPremium("gpt-5-pro")); assertTrue(isPremium("claude-opus-4-1")); assertFalse(isPremium("gpt-5-mini")); assertFalse(isPremium("deepseek-chat"))
        assertFalse(isPremium("mistral-medium-latest")); assertFalse(isPremium("approach"))
    }
}
