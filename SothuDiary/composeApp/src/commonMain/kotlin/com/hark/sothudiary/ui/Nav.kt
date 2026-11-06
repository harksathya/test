package com.hark.sothudiary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Tab { PROPERTIES, BROKERS, FOLLOWUPS, GUIDELINES, SETTINGS }

sealed interface Route {
    data class PropertyDetail(val id: String) : Route
    data class PropertyEdit(val id: String?, val prefillUrl: String = "", val prefillCaption: String = "") : Route
    data class BrokerDetail(val id: String) : Route
    data class BrokerEdit(val id: String?) : Route
    data class InteractionEdit(val id: String?, val propertyId: String?, val brokerId: String?) : Route
}

/** Simple state-based navigation: a selected tab plus a stack of detail/edit routes. */
class Nav {
    var tab by mutableStateOf(Tab.PROPERTIES)
    var stack by mutableStateOf(listOf<Route>())
        private set

    val top: Route? get() = stack.lastOrNull()

    fun push(r: Route) { stack = stack + r }
    fun pop() { if (stack.isNotEmpty()) stack = stack.dropLast(1) }
    fun replaceTop(r: Route) { stack = stack.dropLast(1) + r }

    /** Opened from a list: start a fresh stack (used by the two-pane layout too). */
    fun select(r: Route) { stack = listOf(r) }

    fun switchTab(t: Tab) { tab = t; stack = emptyList() }
}
