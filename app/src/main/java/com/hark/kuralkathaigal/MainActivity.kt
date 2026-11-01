package com.hark.kuralkathaigal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hark.kuralkathaigal.data.Library
import com.hark.kuralkathaigal.ui.screens.AboutScreen
import com.hark.kuralkathaigal.ui.screens.DetailScreen
import com.hark.kuralkathaigal.ui.screens.HomeScreen
import com.hark.kuralkathaigal.ui.screens.ReaderScreen
import com.hark.kuralkathaigal.ui.screens.SavedScreen
import com.hark.kuralkathaigal.ui.theme.Accent
import com.hark.kuralkathaigal.ui.theme.AccentSoft
import com.hark.kuralkathaigal.ui.theme.KuralTheme
import com.hark.kuralkathaigal.ui.theme.Muted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KuralTheme { KuralApp() } }
    }
}

private data class Tab(val route: String, val selected: ImageVector, val unselected: ImageVector, val label: String)

private val tabs = listOf(
    Tab("home", Icons.Filled.Home, Icons.Outlined.Home, "முகப்பு"),
    Tab("saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "சேமித்தவை"),
    Tab("about", Icons.Filled.Info, Icons.Outlined.Info, "பற்றி"),
)

@Composable
fun KuralApp() {
    val context = LocalContext.current
    val library = remember { Library(context.applicationContext) }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = Color(0xFFF6F6F8),
        bottomBar = { if (showBar) BottomBar(nav, route) }
    ) { padding ->
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    library = library,
                    onOpenStory = { nav.navigate("detail/$it") },
                    onAbout = { nav.switchTab("about") },
                    contentPadding = padding
                )
            }
            composable("saved") {
                SavedScreen(library, onOpenStory = { nav.navigate("detail/$it") }, contentPadding = padding)
            }
            composable("about") { AboutScreen(padding) }
            composable("detail/{n}", arguments = listOf(navArgument("n") { type = NavType.IntType })) { entry ->
                val n = entry.arguments?.getInt("n") ?: 1
                DetailScreen(n, library, onBack = { nav.popBackStack() }, onRead = { nav.navigate("read/$n") })
            }
            composable("read/{n}", arguments = listOf(navArgument("n") { type = NavType.IntType })) { entry ->
                val n = entry.arguments?.getInt("n") ?: 1
                ReaderScreen(
                    number = n,
                    library = library,
                    onBack = { nav.popBackStack() },
                    onGoTo = { next ->
                        nav.navigate("read/$next") {
                            popUpTo("read/{n}") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = current == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { nav.switchTab(tab.route) },
                icon = {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AccentSoft else Color.Transparent)
                    ) {
                        Icon(
                            if (selected) tab.selected else tab.unselected,
                            contentDescription = tab.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    unselectedIconColor = Muted,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
