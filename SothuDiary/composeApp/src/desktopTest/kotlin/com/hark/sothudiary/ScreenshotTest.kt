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
            Property(id = "p1", title = "5 cent DTCP plot, Jaihindpuram", type = PropertyType.PLOT, locality = "Jaihindpuram", district = "Madurai", lat = 9.905, lng = 78.112, size = 5.0, unit = AreaUnit.CENTS, askingPrice = 4_200_000, sourceUrl = "https://youtu.be/abc", sourcePlatform = SourcePlatform.YOUTUBE, brokerId = "b1", status = DealStatus.CONTACTED, createdAt = now - day, updatedAt = now, folderIds = setOf("f1"),
                reviews = listOf(
                    Review("Karthik R", "Visited last week, road is 30 ft and EB line is next to the plot. Price is negotiable.", 12, "1 week ago", SourcePlatform.YOUTUBE),
                    Review("செல்வி", "பட்டா சரியா இருக்கான்னு பாருங்க, இந்த ஏரியால விலை கொஞ்சம் அதிகம்.", 4, "2 weeks ago", SourcePlatform.YOUTUBE),
                ), reviewsFetchedAt = now - day),
            Property(id = "p2", title = "2 BHK flat near KK Nagar", type = PropertyType.APARTMENT, locality = "KK Nagar", district = "Madurai", lat = 9.932, lng = 78.142, size = 1100.0, unit = AreaUnit.SQFT, askingPrice = 6_500_000, sourceUrl = "https://instagram.com/p/x", sourcePlatform = SourcePlatform.INSTAGRAM, status = DealStatus.NEW, createdAt = now - 2 * day, updatedAt = now),
            Property(id = "p3", title = "1 acre coconut farm, Melur", type = PropertyType.FARMLAND, locality = "Melur", district = "Madurai", lat = 10.03, lng = 78.34, size = 1.0, unit = AreaUnit.ACRES, askingPrice = 2_000_000, sourceUrl = "", sourcePlatform = SourcePlatform.FACEBOOK, status = DealStatus.SITE_VISIT, createdAt = now - 3 * day, updatedAt = now),
            Property(id = "p4", title = "Cheap plot – too good?", type = PropertyType.PLOT, locality = "Vilangudi", district = "Madurai", lat = 9.95, lng = 78.09, size = 3.0, unit = AreaUnit.CENTS, askingPrice = 300_000, sourceUrl = "", sourcePlatform = SourcePlatform.WHATSAPP, status = DealStatus.NEW, createdAt = now - 4 * day, updatedAt = now),
        )
        val ints = listOf(
            Interaction("i1", "p1", "b1", now - 2 * day, InteractionType.CALL, 4_000_000, "Said EC is ready", "Send EC copy",
                followUpAt = now - day, voiceNotes = listOf(VoiceNote("v1", "x.m4a", 63_000, now - 2 * day))),
            Interaction("i2", "p1", "b1", now - day, InteractionType.VISIT, 4_200_000, "Visited site, road 30 ft"),
        )
        // Cached live-lookup rates (fresh, same type, within 10 km) so the price check and Area rates render offline.
        fun rate(id: String, place: String, lat: Double, lng: Double, group: Int, unit: String, mn: Double, mx: Double, ty: Double, kind: RateKind = RateKind.ASKING, km: Double) =
            OnlineRate(id, place, "Madurai", lat, lng, group, kind, unit, mn, mx, ty, "99acres", "https://www.99acres.com/x", "Oct 2026", now - 2 * day, "3 listings", "k$group", km)
        val rates = listOf(
            rate("r1", "Jaihindpuram", 9.906, 78.115, 0, "cent", 700_000.0, 950_000.0, 820_000.0, km = 0.4),
            rate("r2", "Palanganatham", 9.915, 78.095, 0, "cent", 650_000.0, 900_000.0, 760_000.0, km = 2.1),
            rate("r3", "Thiruparankundram", 9.880, 78.072, 0, "cent", 500_000.0, 750_000.0, 600_000.0, km = 5.2),
            rate("r4", "Madurai South", 9.900, 78.110, 0, "sqft", 1_200.0, 1_800.0, 1_500.0, RateKind.GUIDELINE, km = 0.6),
            rate("r5", "Melur", 10.035, 78.338, 1, "acre", 1_500_000.0, 2_500_000.0, 2_000_000.0, km = 0.6),
            rate("r6", "Kottampatti", 10.07, 78.32, 1, "acre", 1_200_000.0, 2_000_000.0, 1_600_000.0, km = 4.9),
        )
        val lookups = listOf(
            RateLookup("k0", 9.905, 78.112, 0, "Jaihindpuram, Madurai South taluk, Madurai district", now - 2 * day, 4, 1, "gemini-2.5-flash"),
            RateLookup("k1", 10.03, 78.34, 1, "Melur, Madurai district", now - 2 * day, 2, 0, "gemini-2.5-flash"),
        )
        // 2.2.0: a saved land check for p1 (fresh, same pin / size), so the land check screens render offline.
        val p1 = props[0]
        val plot = com.hark.sothudiary.domain.land.plotOutline(p1)!!.first
        val proj = com.hark.sothudiary.domain.land.LocalProjection(9.905, 78.112)
        fun g(x: Double, y: Double) = proj.toLatLng(com.hark.sothudiary.domain.land.XY(x, y)).let { (a, b2) -> GeoPt(a, b2) }
        val tank = listOf(g(10.0, -120.0), g(160.0, -140.0), g(220.0, 40.0), g(120.0, 130.0), g(15.0, 90.0), g(10.0, -120.0))
        val land = LandCheck(
            propertyId = "p1", key = com.hark.sothudiary.domain.land.landCheckKey(p1), lat = 9.905, lng = 78.112, fetchedAt = now - day,
            plot = plot, drawn = false, plotAreaSqm = com.hark.sothudiary.domain.land.polygonAreaSqm(plot.map { it.lat to it.lng }),
            osmOk = true, bufferPct = 25.0, waterPct = 0.0, nearestWaterM = 0.0, nearestWaterName = "Madakulam tank", nearestWaterKind = "reservoir",
            waterOutlines = listOf(tank), landuse = listOf("landuse=residential"),
            features = listOf(
                LandFeature("water", "reservoir", "Madakulam tank", 0.0),
                LandFeature("major_road", "primary", "Tirumangalam Road", 650.0),
                LandFeature("proposed_road", "primary", "Ring road link", 1_200.0),
                LandFeature("road", "residential", "", 30.0),
                LandFeature("school", "school", "Govt School", 900.0),
                LandFeature("hospital", "hospital", "GH", 3_000.0),
                LandFeature("bus", "bus_stop", "", 350.0),
                LandFeature("power", "minor_line", "", 80.0),
            ),
            elevationM = 132.0, surroundingElevationM = 134.0,
            ai = LandAi(zoning = "residential", zoningPlan = "Madurai LPA Master Plan", zoningUrl = "https://example.org/plan",
                roads = listOf(AiRoad("Madurai ring road widening", "under construction", 1.2, "https://example.org/road", "2026")),
                growth = "high", growthReason = "Emerging residential corridor along the ring road", growthUrl = "https://example.org/growth"),
            aiModel = "gemini-flash-latest", aiFetchedAt = now - day, sources = listOf("https://example.org/plan"),
        )
        r.update { it.copy(properties = props, brokers = listOf(b), interactions = ints, folders = listOf(f), onlineRates = rates, rateLookups = lookups, landChecks = listOf(land)) }
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
        shot("home_en", Lang.EN, ThemeMode.LIGHT, h = 2300) { HomeScreen(it) }
        shot("home_ta_dark", Lang.TA, ThemeMode.DARK) { HomeScreen(it) }
        shot("home_dark_en", Lang.EN, ThemeMode.DARK, h = 2300) { HomeScreen(it) }
        shot("list", Lang.EN, ThemeMode.LIGHT) { PropertyListScreen(it, null) }
        shot("list_dark", Lang.EN, ThemeMode.DARK) { PropertyListScreen(it, null) }
        shot("list_grouped", Lang.EN, ThemeMode.LIGHT) { it.filters.group = GroupBy.SOURCE; PropertyListScreen(it, null) }
        shot("list_grouped_open_dark", Lang.EN, ThemeMode.DARK) { it.filters.group = GroupBy.SOURCE; it.filters.toggleGroup("YOUTUBE"); PropertyListScreen(it, null) }
        shot("folders", Lang.EN, ThemeMode.LIGHT) { it.filters.mode = ListMode.FOLDERS; PropertyListScreen(it, null) }
        shot("folders_dark_ta", Lang.TA, ThemeMode.DARK) { it.filters.mode = ListMode.FOLDERS; PropertyListScreen(it, null) }
        shot("folder_view", Lang.EN, ThemeMode.LIGHT) { FolderViewScreen(it, Route.FolderView(source = "INSTAGRAM")) }
        shot("detail_plot", Lang.EN, ThemeMode.LIGHT, h = 3000) { PropertyDetailScreen(it, "p1") }
        shot("detail_suspicious_ta", Lang.TA, ThemeMode.LIGHT, h = 2600) { PropertyDetailScreen(it, "p4") }
        shot("detail_farm", Lang.EN, ThemeMode.DARK, h = 2600) { PropertyDetailScreen(it, "p3") }
        shot("area_rates", Lang.EN, ThemeMode.LIGHT, h = 2400) { AreaRatesScreen(it, "p1", 0, null) }
        shot("edit_share", Lang.EN, ThemeMode.LIGHT, h = 2400) {
            PropertyEditScreen(it, Route.PropertyEdit(null, "", "5 cents DTCP plot near Jaihindpuram Madurai\nPrice ₹25 lakhs. Call 9443012345"))
        }
        shot("interaction", Lang.TA, ThemeMode.LIGHT, h = 1800) { InteractionEditScreen(it, Route.InteractionEdit("i1", "p1", "b1")) }
        shot("map", Lang.EN, ThemeMode.LIGHT) { MapScreen(it) }
        shot("more", Lang.EN, ThemeMode.LIGHT, h = 800) { MoreScreen(it) }
        shot("settings_en", Lang.EN, ThemeMode.LIGHT, h = 1800) { SettingsScreen(it) }
        shot("settings", Lang.TA, ThemeMode.LIGHT, h = 1800) { SettingsScreen(it) }
        shot("location_picker", Lang.EN, ThemeMode.LIGHT) {
            androidx.compose.foundation.layout.Box {
                com.hark.sothudiary.ui.map.LocationPickerDialog(TestPlatform, com.hark.sothudiary.ui.map.PickedLocation(9.905, 78.112, "Jaihindpuram", "Madurai South", "Madurai"), "", {}, {})
                com.hark.sothudiary.ui.OverlayHost()
            }
        }
        shot("followups", Lang.EN, ThemeMode.LIGHT, h = 900) { FollowUpsScreen(it) }
        shot("land_check", Lang.EN, ThemeMode.LIGHT, h = 2600) { LandCheckScreen(it, "p1") }
        shot("land_check_ta_dark", Lang.TA, ThemeMode.DARK, h = 2600) { LandCheckScreen(it, "p1") }
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
