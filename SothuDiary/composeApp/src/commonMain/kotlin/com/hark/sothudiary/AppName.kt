package com.hark.sothudiary

/**
 * 2.4.0: THE app name, in both languages – the one place to change it.
 *
 * Everything reads it from here: the in-app texts (Strings.kt), the share texts and image card, the desktop window
 * title, and – through composeApp/build.gradle.kts, which reads the two lines below when it builds – the Android
 * launcher label (English, and Tamil on phones set to Tamil) and the "Add to …" share target label.
 * Keep each value on one line, in double quotes.
 */
object AppName {
    const val EN = "Sothu Diary"
    const val TA = "சொத்து டைரி"
}
