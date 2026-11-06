package com.hark.sothudiary.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hark.sothudiary.PlatformBackHandler
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.domain.social.SocialPost
import com.hark.sothudiary.domain.social.facebookVariants
import com.hark.sothudiary.domain.social.instagramShortcode
import com.hark.sothudiary.domain.social.platformOf
import com.hark.sothudiary.domain.social.postFromReaderJson
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.FullScreenOverlay
import com.hark.sothudiary.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** The URL the reader opens: Instagram post page; Facebook's lighter mobile site. */
fun readerUrl(url: String): String = when (platformOf(url)) {
    SourcePlatform.INSTAGRAM -> instagramShortcode(url)?.let { "https://www.instagram.com/p/$it/" } ?: url
    SourcePlatform.FACEBOOK -> facebookVariants(url).getOrNull(1) ?: url
    else -> url
}

/**
 * "Read post": loads the post in an in-app browser (log in once to Instagram / Facebook – cookies are kept),
 * then reads caption, picture and visible comments from the page. Has a progress bar, a timeout and a
 * "Paste caption" box as the last resort. [onResult] gets the post (null = cancelled).
 */
@Composable
fun PostReaderOverlay(url: String, onResult: (SocialPost) -> Unit, onDismiss: () -> Unit) {
    FullScreenOverlay {
        PlatformBackHandler(enabled = true) { onDismiss() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PostReaderContent(url, onResult, onDismiss)
        }
    }
}

@Composable
private fun PostReaderContent(url: String, onResult: (SocialPost) -> Unit, onDismiss: () -> Unit) {
    val target = remember(url) { readerUrl(url) }
    var progress by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var loads by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf<SocialPost?>(null) }
    var needsLogin by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(!platformHasWebView) }
    var reading by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(!platformHasWebView) }

    // Read the page a moment after every load (pages finish rendering with JavaScript after onPageFinished).
    LaunchedEffect(loads) {
        if (loads == 0) return@LaunchedEffect
        delay(2500)
        reading = true
        tick++
    }
    // Retry a few times while nothing is found (lazy-loaded comments / captions).
    LaunchedEffect(found, loads) {
        if (found?.caption != null || loads == 0) return@LaunchedEffect
        repeat(3) {
            delay(4000)
            if (found?.caption == null) { reading = true; tick++ }
        }
    }
    // Timeout: after 30 s without a caption, say so and open the paste box.
    LaunchedEffect(Unit) {
        delay(30_000)
        if (found?.caption == null) { timedOut = true; showPaste = true }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(LocalBrand.current.bar).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s(), tint = LocalBrand.current.onBar) }
            Column(Modifier.weight(1f)) {
                Text(S.readPost.s(), color = LocalBrand.current.onBar, style = MaterialTheme.typography.titleMedium)
                Text(target, color = LocalBrand.current.onBar.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val f = found
            TextButton(onClick = { if (f != null) onResult(f) }, enabled = f != null) {
                Text(S.useThis.s(), color = LocalBrand.current.onBar.copy(alpha = if (f != null) 1f else 0.5f), fontWeight = FontWeight.Bold)
            }
        }
        if (progress in 1..99 || reading) LinearProgressIndicator(Modifier.fillMaxWidth())
        // Status
        Surface(Modifier.zIndex(2f), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val f = found
                when {
                    f?.caption != null -> {
                        Text("✓ " + S.readerFound.f(f.comments.size), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text(f.caption, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    needsLogin -> Text(S.readerLogin.s(), style = MaterialTheme.typography.bodySmall)
                    timedOut -> Text(S.readerTimeout.s(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> Text(S.readerLoading.s(), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (platformHasWebView) OutlinedButton(onClick = { reading = true; tick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.readNow.s())
                    }
                    TextButton(onClick = { showPaste = !showPaste }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.pasteCaption.s())
                    }
                }
                if (showPaste) {
                    OutlinedTextField(
                        pasted, { pasted = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 180.dp),
                        placeholder = { Text(S.pasteCaptionHint.s()) }, minLines = 3,
                    )
                    Button(onClick = {
                        val base = found ?: SocialPost(url, platformOf(url))
                        onResult(base.copy(caption = pasted.trim(), methods = base.methods + "manual"))
                    }, enabled = pasted.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.usePasted.s())
                    }
                }
            }
        }
        HorizontalDivider()
        PostWebView(
            url = target,
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            extractTick = tick,
            onExtracted = { json ->
                reading = false
                val obj = parseJsonOrNull(json)
                needsLogin = (obj["login"] as? JsonPrimitive)?.booleanOrNull == true
                val post = postFromReaderJson(url, json)
                if (post != null && (post.caption != null || post.comments.isNotEmpty())) {
                    val prev = found
                    found = if (prev == null) post else post.merge(prev)
                }
            },
            onPageLoaded = { loads++ },
            onProgress = { progress = it },
        )
        Spacer(Modifier.height(0.dp))
    }
}

@Suppress("unused")
private fun Modifier.verticalScrollIfNeeded() = this
