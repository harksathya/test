package com.hark.kuralkathaigal.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/** Tiny persistence for bookmarks, last read position and reader font size. */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("kural_library", Context.MODE_PRIVATE)

    val bookmarks = mutableStateListOf<Int>().apply {
        addAll(prefs.getStringSet("bookmarks", emptySet())!!.mapNotNull { it.toIntOrNull() }.sorted())
    }
    val lastRead = mutableStateOf(prefs.getInt("last_read", 0))
    val fontScale = mutableStateOf(prefs.getFloat("font_scale", 1f))

    fun isBookmarked(n: Int) = n in bookmarks

    fun toggleBookmark(n: Int) {
        if (n in bookmarks) {
            bookmarks.remove(n)
        } else {
            val idx = bookmarks.indexOfFirst { it > n }.let { if (it < 0) bookmarks.size else it }
            bookmarks.add(idx, n)
        }
        prefs.edit().putStringSet("bookmarks", bookmarks.map { it.toString() }.toSet()).apply()
    }

    fun markRead(n: Int) {
        lastRead.value = n
        prefs.edit().putInt("last_read", n).apply()
    }

    fun cycleFont() {
        val next = when {
            fontScale.value < 1f -> 1f
            fontScale.value < 1.2f -> 1.25f
            else -> 0.9f
        }
        fontScale.value = next
        prefs.edit().putFloat("font_scale", next).apply()
    }
}

fun storyByNumber(n: Int): KuralStory = kuralStories.first { it.number == n }
