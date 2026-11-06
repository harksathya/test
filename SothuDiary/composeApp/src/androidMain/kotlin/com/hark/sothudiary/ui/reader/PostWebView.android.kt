package com.hark.sothudiary.ui.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/**
 * Draws the WebView into a bitmap and crops it to the media element's on-screen rectangle (from the reader script's
 * getBoundingClientRect, CSS px scaled to view px). Returns a JPEG, or null when nothing usable is visible.
 */
private fun captureMedia(v: WebView, json: String?): ByteArray? = runCatching {
    if (json == null || v.width <= 0 || v.height <= 0) return null
    val r = Json.parseToJsonElement(json).jsonObject["rect"]?.jsonObject ?: return null
    fun d(k: String) = r[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
    val vw = d("vw").takeIf { it > 0 } ?: return null
    val scale = v.width / vw
    val left = (d("x") * scale).toInt().coerceIn(0, v.width)
    val top = (d("y") * scale).toInt().coerceIn(0, v.height)
    val right = ((d("x") + d("w")) * scale).toInt().coerceIn(0, v.width)
    val bottom = ((d("y") + d("h")) * scale).toInt().coerceIn(0, v.height)
    if (right - left < 160 || bottom - top < 160) return null
    val full = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(full)
    canvas.translate(-v.scrollX.toFloat(), -v.scrollY.toFloat())
    v.draw(canvas)
    val crop = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
    full.recycle()
    // Reject a blank capture (video surfaces and unloaded images draw as one flat colour).
    val samples = (1..5).flatMap { i -> (1..5).map { j -> crop.getPixel(crop.width * i / 6, crop.height * j / 6) } }
    if (samples.distinct().size < 4) { crop.recycle(); return null }
    val out = ByteArrayOutputStream()
    crop.compress(Bitmap.CompressFormat.JPEG, 88, out)
    crop.recycle()
    out.toByteArray()
}.getOrNull()

private class WebHolder { var view: WebView? = null }

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PostWebView(
    url: String,
    modifier: Modifier,
    extractTick: Int,
    onExtracted: (json: String?, mediaSnapshot: ByteArray?) -> Unit,
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
        val v = holder.view ?: return@LaunchedEffect extracted(null, null)
        v.evaluateJavascript(READER_JS) { raw ->
            // evaluateJavascript returns the JS string as a JSON string literal.
            val decoded = runCatching { (Json.parseToJsonElement(raw) as? JsonPrimitive)?.content }.getOrNull()
            extracted(decoded, captureMedia(v, decoded))
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
