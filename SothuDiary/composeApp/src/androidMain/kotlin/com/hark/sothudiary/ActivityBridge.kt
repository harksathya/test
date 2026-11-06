package com.hark.sothudiary

import android.content.Intent
import android.net.Uri

/** What platform services need from the running Activity (permission prompts, pickers, result screens). */
interface ActivityBridge {
    /** Requests the permissions; the callback gets true when at least one of them is granted. */
    fun requestPermissions(perms: Array<String>, cb: (Boolean) -> Unit)
    fun openDocument(mimeTypes: Array<String>, cb: (Uri?) -> Unit)
    fun startForResult(intent: Intent, cb: (Int, Intent?) -> Unit)
}
