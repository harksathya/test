package com.hark.sothudiary

import android.content.Intent
import android.net.Uri

/** What platform services need from the running Activity (permission prompts, pickers, result screens). */
interface ActivityBridge {
    /** Requests the permissions; the callback gets true when at least one of them is granted. */
    fun requestPermissions(perms: Array<String>, cb: (Boolean) -> Unit)
    fun openDocument(mimeTypes: Array<String>, cb: (Uri?) -> Unit)
    fun startForResult(intent: Intent, cb: (Int, Intent?) -> Unit)
    /** 3.0.0: Google sign-in / consent screen (a PendingIntent from the AuthorizationClient). */
    fun startIntentSender(sender: android.content.IntentSender, cb: (Int, Intent?) -> Unit)
    /** 3.0.0: the running Activity (Google Identity Services needs it for its screens). */
    val activity: android.app.Activity
}
