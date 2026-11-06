package com.hark.sothudiary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.hark.sothudiary.data.*
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.FilterState
import com.hark.sothudiary.ui.GroupBy
import com.hark.sothudiary.ui.ListMode
import com.hark.sothudiary.ui.Nav
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.screens.*
import com.hark.sothudiary.ui.theme.SothuTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders key screens to PNG (build/screens) so the UI can be reviewed without a device. */
class ScreenshotTest {
    private val out = File(System.getProperty("user.dir"), "build/screens").apply { mkdirs() }
    private val day = 86_400_000L

    private fun repo(): JsonRepository {
        val r = JsonRepository(MemoryFileStore())
        val now = nowMillis()
        val b = Broker("b1", "Murugan", phone = "9876543210", platform = SourcePlatform.YOUTUBE, createdAt = now)
        val f = Folder("f1", "Shortlist", 0xFF7C4DFF)
        val props = listOf(
            Property(id = "p1", title = "5 cent DTCP plot, Jaihindpuram", type = PropertyType.PLOT, locality = "Jaihindpuram", district = "Madurai", lat = 9.905, lng = 78.112, size = 5.0, unit = AreaUnit.CENTS, askingPrice = 4_200_000, sourceUrl = "https://youtu.be/abc", sourcePlatform = SourcePlatform.YOUTUBE, brokerId = "b1", status = DealStatus.CONTACTED, createdAt = now - day, updatedAt = now, folderIds = setOf("f1")),
            Property(id = "p2", title = "2 BHK flat near KK Nagar", type = PropertyType.APARTMENT, locality = "KK Nagar", district = "Madurai", lat = 9.932, lng = 78.142, size = 1100.0, unit = AreaUnit.SQFT, askingPrice = 6_500_000, sourceUrl = "https://instagram.com/p/x", sourcePlatform = SourcePlatform.INSTAGRAM, status = DealStatus.NEW, createdAt = now - 2 * day, updatedAt = now),
            Property(id = "p3", title = "1 acre coconut farm, Melur", type = PropertyType.FARMLAND, locality = "Melur", district = "Madurai", lat = 10.03, lng = 78.34, size = 1.0, unit = AreaUnit.ACRES, askingPrice = 2_000_000, sourceUrl = "", sourcePlatform = SourcePlatform.FACEBOOK, status = DealStatus.SITE_VISIT, createdAt = now - 3 * day, updatedAt = now),
            Property(id = "p4", title = "Cheap plot – too good?", type = PropertyType.PLOT, locality = "Vilangudi", district = "Madurai", lat = 9.95, lng = 78.09, size = 3.0, unit = AreaUnit.CENTS, askingPrice = 300_000, sourceUrl = "", sourcePlatform = SourcePlatform.WHATSAPP, status = DealStatus.NEW, createdAt = now - 4 * day, updatedAt = now),
        )
        val ints = listOf(
            Interaction("i1", "p1", "b1", now - 2 * day, InteractionType.CALL, 4_000_000, "Said EC is ready", "Send EC copy",
                followUpAt = now - day, voiceNotes = listOf(VoiceNote("v1", "x.m4a", 63_000, now - 2 * day))),
            Interaction("i2", "p1", "b1", now - day, InteractionType.VISIT, 4_200_000, "Visited site, road 30 ft"),
        )
        r.update { it.copy(properties = props, brokers = listOf(b), interactions = ints, folders = listOf(f)) }
        return r
    }

    private fun shot(name: String, lang: Lang, theme: ThemeMode, w: Int = 412, h: Int = 1400, wide: Boolean = false, content: @Composable (AppContext) -> Unit) {
        val repo = repo()
        repo.update { it.copy(settings = it.settings.copy(lang = lang, theme = theme)) }
        val ctx = AppContext(repo, Nav(), TestPlatform, FilterState(), {}, wide)
        ImageComposeScene(w * 2, h * 2, density = Density(2f)) {
            CompositionLocalProvider(LocalLang provides lang) { SothuTheme(theme) { content(ctx) } }
        }.use { scene ->
            scene.render(0)
            val img = scene.render(1_000_000_000L)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    @Test fun renderScreens() {
        shot("home_en", Lang.EN, ThemeMode.LIGHT) { HomeScreen(it) }
        shot("home_ta_dark", Lang.TA, ThemeMode.DARK) { HomeScreen(it) }
        shot("list_grouped", Lang.EN, ThemeMode.LIGHT) { it.filters.group = GroupBy.SOURCE; PropertyListScreen(it, null) }
        shot("folders", Lang.EN, ThemeMode.LIGHT) { it.filters.mode = ListMode.FOLDERS; PropertyListScreen(it, null) }
        shot("detail_plot", Lang.EN, ThemeMode.LIGHT, h = 3000) { PropertyDetailScreen(it, "p1") }
        shot("detail_suspicious_ta", Lang.TA, ThemeMode.LIGHT, h = 2600) { PropertyDetailScreen(it, "p4") }
        shot("detail_farm", Lang.EN, ThemeMode.DARK, h = 2600) { PropertyDetailScreen(it, "p3") }
        shot("area_rates", Lang.EN, ThemeMode.LIGHT, h = 2400) { AreaRatesScreen(it, "Jaihindpuram", 1, null) }
        shot("edit_share", Lang.EN, ThemeMode.LIGHT, h = 2400) {
            PropertyEditScreen(it, Route.PropertyEdit(null, "", "5 cents DTCP plot near Jaihindpuram Madurai\nPrice ₹25 lakhs. Call 9443012345"))
        }
        shot("interaction", Lang.TA, ThemeMode.LIGHT, h = 1800) { InteractionEditScreen(it, Route.InteractionEdit("i1", "p1", "b1")) }
        shot("map", Lang.EN, ThemeMode.LIGHT) { MapScreen(it) }
        shot("more", Lang.EN, ThemeMode.LIGHT, h = 800) { MoreScreen(it) }
        shot("settings", Lang.TA, ThemeMode.LIGHT, h = 1800) { SettingsScreen(it) }
        shot("followups", Lang.EN, ThemeMode.LIGHT, h = 900) { FollowUpsScreen(it) }
    }
}

object TestPlatform : PlatformActions {
    override fun call(phone: String) {}
    override fun whatsapp(phone: String, message: String) {}
    override fun openUrl(url: String) {}
    override fun openMap(lat: Double?, lng: Double?, label: String) {}
    override fun shareText(subject: String, text: String) {}
    override fun pickTextFile(onResult: (String?) -> Unit) = onResult(null)
    override fun toast(message: String) {}
    override val audio: AudioActions = NoAudio
    override val location: LocationActions = object : LocationActions {
        override val supported = true
        override fun currentLocation(onResult: (LatLng?) -> Unit) = onResult(null)
    }
    override fun imagePath(fileName: String): String? = null
    override fun pickImage(onResult: (String?) -> Unit) = onResult(null)
    override fun deleteImage(fileName: String) {}
    override fun recognizeText(fileName: String, onResult: (String?) -> Unit) = onResult(null)
    override fun setReminders(enabled: Boolean) {}
}
