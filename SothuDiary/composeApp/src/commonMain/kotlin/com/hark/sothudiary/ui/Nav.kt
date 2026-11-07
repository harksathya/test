package com.hark.sothudiary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Tab(val inBottomBar: Boolean) {
    HOME(true), PROPERTIES(true), MAP(true), FOLLOWUPS(true), MORE(true),
    // Reached from "More" on phones; shown directly in the navigation rail on tablets.
    BROKERS(false), AREA_RATES(false), GUIDELINES(false), SETTINGS(false),
}

sealed interface Route {
    data class PropertyDetail(val id: String) : Route
    data class PropertyEdit(
        val id: String?,
        val prefillUrl: String = "",
        val prefillCaption: String = "",
        val prefillImages: List<String> = emptyList(),
        val folderId: String? = null,
    ) : Route
    /** Properties of one source (platform name) or one user folder. */
    data class FolderView(val source: String? = null, val folderId: String? = null) : Route
    data class BrokerDetail(val id: String) : Route
    data class BrokerEdit(val id: String?) : Route
    data class InteractionEdit(val id: String?, val propertyId: String?, val brokerId: String?) : Route
    /** Area rates for a locality / type, optionally pre-selected. */
    data class AreaRates(val propertyId: String? = null, val group: Int? = null) : Route
    /** 2.2.0: land check (map data layers + AI) of one property. */
    data class LandCheck(val propertyId: String) : Route
}

/** Simple state-based navigation: a selected tab plus a stack of detail/edit routes. */
class Nav {
    var tab by mutableStateOf(Tab.HOME)
    var stack by mutableStateOf(listOf<Route>())
        private set

    val top: Route? get() = stack.lastOrNull()

    fun push(r: Route) { stack = stack + r }
    fun pop() { if (stack.isNotEmpty()) stack = stack.dropLast(1) }
    fun replaceTop(r: Route) { stack = stack.dropLast(1) + r }

    /** Opened from a list: start a fresh stack (used by the two-pane layout too). */
    fun select(r: Route) { stack = listOf(r) }

    fun switchTab(t: Tab) { tab = t; stack = emptyList() }

    /** System back: pop a screen, then go from a "More" page back to More, then to Home. */
    fun back(): Boolean = when {
        stack.isNotEmpty() -> { pop(); true }
        !tab.inBottomBar -> { switchTab(Tab.MORE); true }
        tab != Tab.HOME -> { switchTab(Tab.HOME); true }
        else -> false
    }
}
