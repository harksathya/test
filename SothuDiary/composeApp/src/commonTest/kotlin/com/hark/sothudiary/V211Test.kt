package com.hark.sothudiary

import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AppSettings
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.data.migrate
import com.hark.sothudiary.data.normalizeGeminiModel
import com.hark.sothudiary.domain.isPlaceholderTitle
import com.hark.sothudiary.domain.repairTitle
import com.hark.sothudiary.domain.smartTitle
import com.hark.sothudiary.domain.social.SocialPost
import com.hark.sothudiary.domain.social.coverCandidates
import com.hark.sothudiary.domain.social.parseInstagramEmbed
import com.hark.sothudiary.domain.social.postFromReaderJson
import com.hark.sothudiary.domain.social.secondSlide
import com.hark.sothudiary.ui.FilterState
import com.hark.sothudiary.ui.GroupBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V211Test {
    @Test fun placeholderTitles() {
        listOf("Instagram", "instagram", " Facebook ", "YouTube", "Instagram reel", "Facebook video", "", "https://instagram.com/p/x", "Reels")
            .forEach { assertTrue(isPlaceholderTitle(it), it) }
        listOf("1.37 acre farmland, Panpoli", "5 cent plot near Melur", "2 BHK flat").forEach { assertFalse(isPlaceholderTitle(it), it) }
    }

    @Test fun titleFromFacts() {
        val t = smartTitle("🔥🔥 Super opportunity 🔥🔥\n1.37 ஏக்கர் விவசாய நிலம் விற்பனைக்கு\nPanpoli, Tenkasi district\nPrice 45 lakhs")
        assertNotNull(t)
        assertTrue(t.startsWith("1.37 acre"), t)
        assertFalse(isPlaceholderTitle(t))
    }

    @Test fun titleFromFirstMeaningfulLine() {
        val t = smartTitle("Instagram\nBeautiful house for sale near Palayamkottai bus stand\nCall 9443012345")
        assertNotNull(t)
        assertFalse(t.equals("Instagram", true), t)
    }

    @Test fun neverBareSourceName() {
        assertNull(smartTitle("Instagram"))
        val p = Property(id = "x", title = "Instagram", type = PropertyType.FARMLAND, locality = "Panpoli", sourcePlatform = SourcePlatform.INSTAGRAM)
        val fixed = repairTitle(p)
        assertFalse(isPlaceholderTitle(fixed.title), fixed.title)
        assertTrue("Panpoli" in fixed.title, fixed.title)
        // A real title is kept.
        assertEquals("My plot", repairTitle(p.copy(title = "My plot")).title)
    }

    @Test fun tamilFactsTitle() {
        val t = smartTitle("5 cent DTCP plot\nJaihindpuram, Madurai", Lang.TA)
        assertNotNull(t)
        assertTrue("சென்ட்" in t, t)
    }

    @Test fun geminiModelMigration() {
        assertEquals("auto", normalizeGeminiModel("gemini-2.0-flash"))
        assertEquals("auto", normalizeGeminiModel("models/gemini-2.5-flash"))
        assertEquals("auto", normalizeGeminiModel(""))
        assertEquals("auto", normalizeGeminiModel("Auto (latest)"))
        assertEquals("auto", normalizeGeminiModel("தானியங்கி (புதியது)"))
        assertEquals("gemini-3.8-flash", normalizeGeminiModel("models/gemini-3.8-flash"))
        val migrated = migrate(AppData(settings = AppSettings(geminiModel = "gemini-2.0-flash")))
        assertEquals("auto", migrated.settings.geminiModel)
        assertEquals("auto", AppSettings().geminiModel)
    }

    @Test fun migrationRepairsPlaceholderTitles() {
        val d = AppData(properties = listOf(Property(id = "a", title = "Instagram", sourceCaption = "2 acre coconut farm at Melur\nPrice 40 lakhs")))
        val t = migrate(d).properties.first().title
        assertFalse(isPlaceholderTitle(t), t)
    }

    @Test fun groupsStartCollapsed() {
        val f = FilterState()
        f.group = GroupBy.SOURCE
        assertFalse(f.isExpanded("INSTAGRAM"))
        f.toggleGroup("INSTAGRAM")
        assertTrue(f.isExpanded("INSTAGRAM"))
        // Another grouping keeps its own (collapsed) state; switching back remembers the open group.
        f.group = GroupBy.TYPE
        assertFalse(f.isExpanded("INSTAGRAM"))
        f.group = GroupBy.SOURCE
        assertTrue(f.isExpanded("INSTAGRAM"))
        f.toggleGroup("INSTAGRAM")
        assertFalse(f.isExpanded("INSTAGRAM"))
    }

    @Test fun youtubeCoverPrefersMaxRes() {
        val c = coverCandidates("https://youtu.be/dQw4w9WgXcQ", null)
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", c[0])
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg", c[1])
    }

    @Test fun instagramCarouselFirstAndSecondSlide() {
        val ctx = """{"context":{"media":{"shortcode_media":{"display_url":"https://cdn/a.jpg","owner":{"username":"land_tn"},""" +
            """"edge_media_to_caption":{"edges":[{"node":{"text":"1.37 acre farmland, Panpoli"}}]},""" +
            """"edge_sidecar_to_children":{"edges":[{"node":{"display_url":"https://cdn/a.jpg"}},{"node":{"display_url":"https://cdn/b.jpg"}}]}}}}}"""
        val html = "<script>window.x={\"contextJSON\":\"" + ctx.replace("\"", "\\\"") + "\"}</script>"
        val post = parseInstagramEmbed("https://www.instagram.com/p/ABCDE/", html)
        assertNotNull(post)
        assertEquals("https://cdn/a.jpg", post.image)
        assertEquals("https://cdn/b.jpg", secondSlide(post))
        assertEquals("https://cdn/a.jpg", coverCandidates(post.url, post).first())
    }

    @Test fun readerJsonGivesImagesAndNoPlaceholderTitle() {
        val json = """{"title":"Instagram","caption":"3 cent plot at Vilangudi","image":"https://og/x.jpg","images":["https://cdn/big.jpg","https://og/x.jpg"],"slides":["https://cdn/big.jpg","https://cdn/two.jpg"],"comments":[]}"""
        val post = postFromReaderJson("https://www.instagram.com/p/ABCDE/", json)
        assertNotNull(post)
        assertNull(post.title)
        assertEquals("https://cdn/big.jpg", post.image)
        assertEquals("https://cdn/two.jpg", secondSlide(post))
        val merged = SocialPost(post.url, SourcePlatform.INSTAGRAM).merge(post)
        assertTrue("https://og/x.jpg" in merged.allImages)
    }
}
