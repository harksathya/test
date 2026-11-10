package com.hark.sothudiary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.daysUntil
import com.hark.sothudiary.domain.extractUrl
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.Tr
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.FilterState
import com.hark.sothudiary.ui.Nav
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.Tab
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.screens.BrokerDetailScreen
import com.hark.sothudiary.ui.screens.LandCheckScreen
import com.hark.sothudiary.ui.screens.BrokerEditScreen
import com.hark.sothudiary.ui.screens.BrokerListScreen
import com.hark.sothudiary.ui.screens.FollowUpsScreen
import com.hark.sothudiary.ui.screens.GuidelinesScreen
import com.hark.sothudiary.ui.screens.InteractionEditScreen
import com.hark.sothudiary.ui.screens.PropertyDetailScreen
import com.hark.sothudiary.ui.screens.PropertyEditScreen
import com.hark.sothudiary.ui.screens.PropertyListScreen
import com.hark.sothudiary.ui.screens.SettingsScreen
import com.hark.sothudiary.ui.screens.HomeScreen
import com.hark.sothudiary.ui.screens.MapScreen
import com.hark.sothudiary.ui.screens.MoreScreen
import com.hark.sothudiary.ui.screens.AreaRatesScreen
import com.hark.sothudiary.ui.screens.FolderViewScreen
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QueryStats
import com.hark.sothudiary.ui.theme.SothuTheme

private fun Tab.icon(): ImageVector = when (this) {
    Tab.HOME -> Icons.Filled.Dashboard
    Tab.PROPERTIES -> Icons.Filled.HomeWork
    Tab.MAP -> Icons.Filled.Map
    Tab.FOLLOWUPS -> Icons.Filled.Event
    Tab.MORE -> Icons.Filled.Menu
    Tab.BROKERS -> Icons.Filled.Groups
    Tab.AREA_RATES -> Icons.Filled.QueryStats
    Tab.GUIDELINES -> Icons.Filled.Gavel
    Tab.SETTINGS -> Icons.Filled.Settings
}

private fun Tab.label(): Tr = when (this) {
    Tab.HOME -> S.tabHome
    Tab.PROPERTIES -> S.tabProperties
    Tab.MAP -> S.tabMap
    Tab.FOLLOWUPS -> S.tabFollowUps
    Tab.MORE -> S.tabMore
    Tab.BROKERS -> S.tabBrokers
    Tab.AREA_RATES -> S.tabAreaRates
    Tab.GUIDELINES -> S.tabGuidelines
    Tab.SETTINGS -> S.tabSettings
}

/**
 * Root of the shared UI.
 * @param sharedText text shared into the app (Android ACTION_SEND); opens "New property" prefilled.
 */
@Composable
fun App(
    repo: JsonRepository,
    platform: PlatformActions,
    shared: SharedContent? = null,
    onSharedConsumed: () -> Unit = {},
    openFollowUps: Boolean = false,
    onOpenFollowUpsConsumed: () -> Unit = {},
    /** 2.3.0: a share link the app was opened with (sothudiary:// or the https page link). */
    incomingLink: String? = null,
    onIncomingLinkConsumed: () -> Unit = {},
) {
    val nav = remember { Nav() }
    val filters = remember { FilterState() }
    val lang = repo.data.settings.lang
    /** 2.3.0: a received share code waiting for "Add / Update" (bad link: the code is kept and the dialog says so). */
    var importCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingLink) {
        val l = incomingLink
        if (l != null) {
            importCode = com.hark.sothudiary.domain.share.extractShareCode(l) ?: "bad"
            onIncomingLinkConsumed()
        }
    }

    // 2.6.0: a .sothu file picked with "Import .sothu file".
    val picked = com.hark.sothudiary.ui.screens.PropertyImport.pending
    LaunchedEffect(picked) {
        if (picked != null) {
            importCode = picked
            com.hark.sothudiary.ui.screens.PropertyImport.pending = null
        }
    }

    LaunchedEffect(shared) {
        val sc = shared
        // 2.3.0: a Sothu Diary share link shared into the app (e.g. from WhatsApp) imports that property.
        val code = sc?.takeIf { it.imageFiles.isEmpty() }?.let { com.hark.sothudiary.domain.share.extractShareCode(it.text) }
        if (code != null) {
            importCode = code
            onSharedConsumed()
        } else if (sc != null && (sc.text.isNotBlank() || sc.imageFiles.isNotEmpty())) {
            val text = sc.text
            val url = extractUrl(text) ?: ""
            val caption = (if (url.isNotEmpty()) text.replace(url, "") else text).trim()
            nav.switchTab(Tab.PROPERTIES)
            nav.select(Route.PropertyEdit(id = null, prefillUrl = url, prefillCaption = caption, prefillImages = sc.imageFiles))
            onSharedConsumed()
        }
    }

    // 2.4.0: the bundled LGD village list (district → taluk → village pickers), read once off the main thread.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { com.hark.sothudiary.domain.land.VillageDirectory.install(platform.bundledText("tn_villages")) }
        }
    }

    // Real cover photos (and titles) for properties saved without one – once per session, in the background.
    LaunchedEffect(Unit) { runCatching { com.hark.sothudiary.domain.social.backfillCovers(repo, platform) } }

    LaunchedEffect(openFollowUps) {
        if (openFollowUps) {
            nav.switchTab(Tab.FOLLOWUPS)
            onOpenFollowUpsConsumed()
        }
    }

    // 3.0.0: automatic Drive backup 30 s after the last change (a new change restarts the wait). The key leaves out the
    // last-backup time, so finishing a backup does not start another one.
    val driveKey = repo.data.let { it.copy(settings = it.settings.copy(driveLastBackupAt = 0)) }
    LaunchedEffect(driveKey) {
        val s = repo.data.settings
        if (!platform.driveSupported || s.driveAccount.isBlank() || !s.driveAuto || s.driveLastBackupAt == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(com.hark.sothudiary.domain.drive.DriveRules.AUTO_DELAY_MS)
        runCatching { com.hark.sothudiary.domain.drive.DriveSync.backup(repo, platform, interactive = false) }
    }

    // Soft background music (Android): follows the saved settings; the top-bar note icon toggles it.
    val st = repo.data.settings
    LaunchedEffect(st.bgmEnabled, st.bgmVolume, st.bgmFile) { platform.music.configure(st.bgmEnabled, st.bgmVolume, st.bgmFile) }
    val music = com.hark.sothudiary.ui.components.MusicToggleState(
        available = platform.music.supported && (platform.music.hasDefaultTrack || st.bgmFile.isNotBlank()),
        playing = st.bgmEnabled,
        toggle = { repo.setMusic(enabled = !repo.data.settings.bgmEnabled) },
    )

    CompositionLocalProvider(LocalLang provides lang, com.hark.sothudiary.ui.components.LocalMusicToggle provides music) {
        SothuTheme(repo.data.settings.theme) {
            PlatformBackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Tab.HOME) { nav.back() }
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val wide = maxWidth >= 600.dp
                val ctx = AppContext(
                    repo = repo,
                    nav = nav,
                    platform = platform,
                    filters = filters,
                    toggleLang = { repo.setLang(if (repo.data.settings.lang == Lang.EN) Lang.TA else Lang.EN) },
                    wide = wide,
                )
                if (wide) WideLayout(ctx) else PhoneLayout(ctx)
                importCode?.let { code ->
                    val sp = remember(code) { com.hark.sothudiary.domain.share.decodeShare(code) }
                    com.hark.sothudiary.ui.screens.ImportSharedDialog(ctx, sp) { importCode = null }
                }
                com.hark.sothudiary.ui.OverlayHost()
            }
        }
    }
}

private fun overdueCount(ctx: AppContext): Int {
    val now = nowMillis()
    return ctx.repo.data.interactions.count { i ->
        val fu = i.followUpAt
        !i.followUpDone && fu != null && daysUntil(fu, now) <= 0
    }
}

@Composable
private fun TabIcon(ctx: AppContext, t: Tab) {
    val n = if (t == Tab.FOLLOWUPS) overdueCount(ctx) else 0

    if (n > 0) {
        BadgedBox(badge = { Badge { Text(n.toString()) } }) { Icon(t.icon(), contentDescription = null) }
    } else {
        Icon(t.icon(), contentDescription = null)
    }
}

@Composable
private fun PhoneLayout(ctx: AppContext) {
    AnimatedContent(
        targetState = ctx.nav.top,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }) togetherWith fadeOut(tween(150))
        },
        label = "route",
    ) { r ->
        if (r != null) RouteScreen(ctx, r) else PhoneTabs(ctx)
    }
}

@Composable
private fun PhoneTabs(ctx: AppContext) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Tab.entries.filter { it.inBottomBar }.forEach { t ->
                    NavigationBarItem(
                        selected = ctx.nav.tab == t || (t == Tab.MORE && !ctx.nav.tab.inBottomBar),
                        onClick = { ctx.nav.switchTab(t) },
                        icon = { TabIcon(ctx, t) },
                        label = { Text(t.label().s(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            Crossfade(targetState = ctx.nav.tab, animationSpec = tween(180), label = "tab") { t -> TabScreen(ctx, selectedId = null, tab = t) }
        }
    }
}

@Composable
private fun WideLayout(ctx: AppContext) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            Spacer(Modifier.height(12.dp))
            Tab.entries.filter { it != Tab.MORE }.forEach { t ->
                NavigationRailItem(
                    selected = ctx.nav.tab == t,
                    onClick = { ctx.nav.switchTab(t) },
                    icon = { TabIcon(ctx, t) },
                    label = { Text(t.label().s(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        VerticalDivider()
        val top = ctx.nav.top
        val selectedId = when (top) {
            is Route.PropertyDetail -> top.id
            is Route.BrokerDetail -> top.id
            else -> null
        }
        val listTab = ctx.nav.tab == Tab.PROPERTIES || ctx.nav.tab == Tab.BROKERS || ctx.nav.tab == Tab.FOLLOWUPS
        if (!listTab && top == null) {
            Box(Modifier.weight(1f).fillMaxHeight()) { TabScreen(ctx, selectedId) }
        } else {
            Box(Modifier.weight(0.42f).fillMaxHeight()) { TabScreen(ctx, selectedId) }
            VerticalDivider()
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                if (top != null) {
                    RouteScreen(ctx, top)
                } else {
                    EmptyState(Icons.Filled.TouchApp, S.selectSomething.s(), S.selectSomethingBody.s())
                }
            }
        }
    }
}

@Composable
private fun TabScreen(ctx: AppContext, selectedId: String?, tab: Tab = ctx.nav.tab) {
    when (tab) {
        Tab.HOME -> HomeScreen(ctx)
        Tab.PROPERTIES -> PropertyListScreen(ctx, selectedId)
        Tab.MAP -> MapScreen(ctx)
        Tab.FOLLOWUPS -> FollowUpsScreen(ctx)
        Tab.MORE -> MoreScreen(ctx)
        Tab.BROKERS -> BrokerListScreen(ctx, selectedId)
        Tab.AREA_RATES -> AreaRatesScreen(ctx, null, null, onBack = null)
        Tab.GUIDELINES -> GuidelinesScreen(ctx)
        Tab.SETTINGS -> SettingsScreen(ctx)
    }
}

@Composable
private fun RouteScreen(ctx: AppContext, r: Route) {
    when (r) {
        is Route.PropertyDetail -> PropertyDetailScreen(ctx, r.id)
        is Route.PropertyEdit -> PropertyEditScreen(ctx, r)
        is Route.BrokerDetail -> BrokerDetailScreen(ctx, r.id)
        is Route.BrokerEdit -> BrokerEditScreen(ctx, r)
        is Route.InteractionEdit -> InteractionEditScreen(ctx, r)
        is Route.FolderView -> FolderViewScreen(ctx, r)
        is Route.AreaRates -> AreaRatesScreen(ctx, r.propertyId, r.group, onBack = { ctx.nav.pop() })
        is Route.LandCheck -> LandCheckScreen(ctx, r.propertyId)
        is Route.PriceCheck -> com.hark.sothudiary.ui.screens.PriceCheckScreen(ctx, r.propertyId)
        is Route.Guideline -> com.hark.sothudiary.ui.screens.GuidelineScreen(ctx, r.propertyId)
        is Route.Documents -> com.hark.sothudiary.ui.screens.DocumentsScreen(ctx, r.propertyId)
    }
}
