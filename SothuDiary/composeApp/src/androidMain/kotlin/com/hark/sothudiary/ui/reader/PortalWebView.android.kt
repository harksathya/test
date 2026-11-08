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

private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

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
    onPageError: (String) -> Unit,
) {
    val holder = remember { PortalHolder() }
    val result by rememberUpdatedState(onResult)
    val loaded by rememberUpdatedState(onPageLoaded)
    val progress by rememberUpdatedState(onProgress)
    val currentScript by rememberUpdatedState(script)
    val failed: (String) -> Unit = { msg -> onPageError(msg) }

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
                // 2.4.0: with multiple windows off, a page's window.open() (TNREGINET opens some results that way) loads
                // in this same view; before, javaScriptCanOpenWindowsAutomatically = false silently blocked it.
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.databaseEnabled = true
                // TNREGINET still pulls some scripts / images over http: without this they are blocked on https pages.
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                // The guideline form ids were read from the desktop page; ask for the desktop layout.
                settings.userAgentString = DESKTOP_UA
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
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
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) failed("page error ${error.errorCode}: ${error.description}")
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                        if (request.isForMainFrame) failed("HTTP ${response.statusCode} ${response.reasonPhrase.orEmpty()}")
                    }
                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                        // Never accept a bad certificate; say so in the status line.
                        handler.cancel()
                        failed("certificate error (${error.primaryError})")
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
