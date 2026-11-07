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

private class PortalHolder { var view: WebView? = null }

/** 2.3.1: TNREGINET page in a WebView (JavaScript on, cookies kept, desktop-width page so the portal's layout fits). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PortalWebView(
    url: String,
    modifier: Modifier,
    script: String,
    scriptTick: Int,
    onResult: (String?) -> Unit,
    onPageLoaded: (String) -> Unit,
    onProgress: (Int) -> Unit,
) {
    val holder = remember { PortalHolder() }
    val result by rememberUpdatedState(onResult)
    val loaded by rememberUpdatedState(onPageLoaded)
    val progress by rememberUpdatedState(onProgress)
    val currentScript by rememberUpdatedState(script)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                CookieManager.getInstance().setAcceptCookie(true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
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

    LaunchedEffect(scriptTick) {
        if (scriptTick == 0) return@LaunchedEffect
        val v = holder.view ?: return@LaunchedEffect result(null)
        v.evaluateJavascript(currentScript) { raw ->
            // evaluateJavascript hands back the JS string as a JSON string literal.
            val decoded = runCatching { (Json.parseToJsonElement(raw) as? JsonPrimitive)?.content }.getOrNull()
            result(decoded)
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
