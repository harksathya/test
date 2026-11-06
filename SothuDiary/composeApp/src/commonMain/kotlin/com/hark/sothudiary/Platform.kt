package com.hark.sothudiary

import androidx.compose.runtime.Composable

/** Platform services the shared UI needs. Android and desktop provide their own implementation. */
interface PlatformActions {
    /** Opens the dialer (Android) for this number. */
    fun call(phone: String)

    /** Opens a WhatsApp chat via https://wa.me/91XXXXXXXXXX */
    fun whatsapp(phone: String, message: String = "")

    fun openUrl(url: String)

    /** Shares plain text (used for JSON export). */
    fun shareText(subject: String, text: String)

    /** Lets the user pick a file and returns its text, or null if cancelled / unsupported. */
    fun pickTextFile(onResult: (String?) -> Unit)

    /** A short message (toast / snackbar-like). */
    fun toast(message: String)
}

/** System back button handling (Android back gesture); no-op on desktop. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
