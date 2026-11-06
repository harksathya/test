package com.hark.sothudiary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hark.sothudiary.data.JsonRepository

class MainActivity : ComponentActivity() {

    private var sharedText by mutableStateOf<String?>(null)
    private var pendingPick: ((String?) -> Unit)? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = pendingPick
        pendingPick = null
        if (uri == null) {
            cb?.invoke(null)
        } else {
            val text = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            cb?.invoke(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = AppGraph.repository(applicationContext)
        val platform = AndroidPlatform(this) { cb ->
            pendingPick = cb
            runCatching { openDocument.launch(arrayOf("application/json", "text/plain", "*/*")) }
                .onFailure { pendingPick = null; cb(null) }
        }
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            App(
                repo = repo,
                platform = platform,
                sharedText = sharedText,
                onSharedConsumed = { sharedText = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val combined = listOfNotNull(subject?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
                .distinct().joinToString("\n")
            if (combined.isNotBlank()) sharedText = combined
        }
    }
}
