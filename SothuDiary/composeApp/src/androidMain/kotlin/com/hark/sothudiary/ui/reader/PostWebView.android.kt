package com.hark.sothudiary.ui.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

actual val platformHasWebView: Boolean = true

private class WebHolder { var view: WebView? = null }

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PostWebView(
    url: String,
    modifier: Modifier,
    extractTick: Int,
    onExtracted: (String?) -> Unit,
    onPageLoaded: (String) -> Unit,
    onProgress: (Int) -> Unit,
) {
    val holder = remember { WebHolder() }
    val extracted by rememberUpdatedState(onExtracted)
    val loaded by rememberUpdatedState(onPageLoaded)
    val progress by rememberUpdatedState(onProgress)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                cm.setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Stay inside the WebView for http(s); ignore intent:// / app links (instagram://, fb://).
                        val scheme = request.url.scheme ?: return true
                        return scheme != "http" && scheme != "https"
                    }
                    override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) { progress(5) }
                    override fun onPageFinished(view: WebView, u: String) {
                        CookieManager.getInstance().flush()
                        progress(100)
                        loaded(u)
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) { progress(newProgress) }
                }
                loadUrl(url)
                holder.view = this
            }
        },
    )

    LaunchedEffect(extractTick) {
        if (extractTick == 0) return@LaunchedEffect
        val v = holder.view ?: return@LaunchedEffect extracted(null)
        v.evaluateJavascript(READER_JS) { raw ->
            // evaluateJavascript returns the JS string as a JSON string literal.
            val decoded = runCatching { (Json.parseToJsonElement(raw) as? JsonPrimitive)?.content }.getOrNull()
            extracted(decoded)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            holder.view?.apply { stopLoading(); destroy() }
            holder.view = null
        }
    }
}
