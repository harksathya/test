package com.hark.sothudiary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity(), ActivityBridge {

    private var shared by mutableStateOf<SharedContent?>(null)
    /** 2.3.0: a property share link the app was opened with. */
    private var incomingLink by mutableStateOf<String?>(null)
    private var openFollowUps by mutableStateOf(false)

    private var pendingPermission: ((Boolean) -> Unit)? = null
    private var pendingDocument: ((Uri?) -> Unit)? = null
    private var pendingResult: ((Int, Intent?) -> Unit)? = null
    private var platform: AndroidPlatform? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val cb = pendingPermission
        pendingPermission = null
        cb?.invoke(result.values.any { it })
    }

    private val documentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = pendingDocument
        pendingDocument = null
        cb?.invoke(uri)
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = pendingResult
        pendingResult = null
        cb?.invoke(r.resultCode, r.data)
    }

    private var pendingSender: ((Int, Intent?) -> Unit)? = null
    private val senderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val cb = pendingSender
        pendingSender = null
        cb?.invoke(r.resultCode, r.data)
    }

    override val activity: android.app.Activity get() = this

    override fun startIntentSender(sender: android.content.IntentSender, cb: (Int, Intent?) -> Unit) {
        pendingSender = cb
        runCatching { senderLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }
            .onFailure { pendingSender = null; cb(RESULT_CANCELED, null) }
    }

    override fun requestPermissions(perms: Array<String>, cb: (Boolean) -> Unit) {
        pendingPermission = cb
        runCatching { permissionLauncher.launch(perms) }.onFailure { pendingPermission = null; cb(false) }
    }

    override fun openDocument(mimeTypes: Array<String>, cb: (Uri?) -> Unit) {
        pendingDocument = cb
        runCatching { documentLauncher.launch(mimeTypes) }.onFailure { pendingDocument = null; cb(null) }
    }

    override fun startForResult(intent: Intent, cb: (Int, Intent?) -> Unit) {
        pendingResult = cb
        try {
            resultLauncher.launch(intent)
        } catch (e: Exception) {
            pendingResult = null
            throw e
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from the splash (starting) theme to the normal theme.
        setTheme(R.style.Theme_SothuDiary)
        super.onCreate(savedInstanceState)
        val repo = AppGraph.repository(applicationContext)
        val p = AndroidPlatform(this, this)
        platform = p
        Reminders.schedule(applicationContext, repo.data.settings.reminders)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            App(
                repo = repo,
                platform = p,
                shared = shared,
                onSharedConsumed = { shared = null },
                incomingLink = incomingLink,
                onIncomingLinkConsumed = { incomingLink = null },
                openFollowUps = openFollowUps,
                onOpenFollowUpsConsumed = { openFollowUps = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        platform?.music?.setForeground(true)
    }

    override fun onStop() {
        super.onStop()
        platform?.audio?.stopPlayback()
        platform?.music?.setForeground(false)
    }

    override fun onDestroy() {
        platform?.audio?.release()
        platform?.music?.release()
        super.onDestroy()
    }

    /** Reads a property file off the main thread and hands its share code to the import screen. */
    private fun readPropertyFile(uri: Uri) {
        Thread {
            val text = AndroidShare.readUri(applicationContext, uri)
            val code = com.hark.sothudiary.domain.share.codeFromFileText(text)
            runOnUiThread {
                // A file that is not ours opens the import dialog with its "could not read" message.
                incomingLink = if (code != null) "sothudiary://p?d=$code" else "sothudiary://p?d=bad"
            }
        }.start()
    }

    /** 2.6.0: the file name behind a content:// or file:// URI ("" when the provider does not say). */
    private fun displayName(uri: Uri): String = runCatching {
        if (uri.scheme == "file") return@runCatching uri.lastPathSegment.orEmpty()
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private fun isSothuName(name: String) = name.lowercase().endsWith("." + com.hark.sothudiary.domain.share.SOTHU_FILE_EXT)

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(Reminders.EXTRA_OPEN) == Reminders.OPEN_FOLLOWUPS) openFollowUps = true
        val action = intent?.action ?: return
        if (action == Intent.ACTION_VIEW) {
            // 2.3.0: sothudiary://p?d=… or https://harksathya.github.io/test/p/#…
            val data = intent.dataString
            if (data != null && (data.startsWith("sothudiary:") || data.contains("harksathya.github.io/test/p"))) { incomingLink = data; return }
            // 2.4.0: a .sothu property file (content:// or file://).
            intent.data?.let { readPropertyFile(it) }
            return
        }
        // 2.4.0: a .sothu file shared into the app (SEND with a file, not text / images).
        // 2.6.0: also when the sender typed it text/plain, application/json or */* – a stream whose name ends in .sothu.
        val type = intent.type.orEmpty()
        if (action == Intent.ACTION_SEND && !type.startsWith("image/")) {
            @Suppress("DEPRECATION")
            val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            if (stream != null && (type.startsWith("application/") || isSothuName(displayName(stream)))) {
                readPropertyFile(stream)
                return
            }
        }
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val combined = listOfNotNull(subject?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
            .distinct().joinToString("\n")
        val uris = mutableListOf<Uri>()
        if (intent.type?.startsWith("image/") == true || action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            if (action == Intent.ACTION_SEND) {
                (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uris += it }
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
            }
            intent.clipData?.let { cd -> for (i in 0 until cd.itemCount) cd.getItemAt(i).uri?.let { if (it !in uris) uris += it } }
        }
        if (uris.isEmpty()) {
            if (combined.isNotBlank()) shared = SharedContent(combined, emptyList())
            return
        }
        // Copy images off the main thread (the share grant only lasts while we hold the intent).
        Thread {
            val files = uris.take(6).mapNotNull { AndroidImages.copyIn(applicationContext, it) }
            runOnUiThread { if (files.isNotEmpty() || combined.isNotBlank()) shared = SharedContent(combined, files) }
        }.start()
    }
}
