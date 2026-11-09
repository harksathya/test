package com.hark.sothudiary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/** Threads for blocking HTTP calls (daemon threads, so a stuck call never keeps the app process alive). */
private val httpPool = Executors.newCachedThreadPool { r -> Thread(r, "sothu-http").apply { isDaemon = true } }

/** Short, user-readable reason for a failed call (shown in the Status / நிலை panels). */
internal fun httpErrorText(e: Throwable): String = when (e) {
    is SocketTimeoutException -> "timeout"
    is UnknownHostException -> "no internet / DNS (" + (e.message ?: "host") + ")"
    is SSLException -> "TLS: " + (e.message ?: e::class.simpleName).orEmpty().take(80)
    is java.net.ConnectException -> "connection refused"
    is java.io.InterruptedIOException -> "cancelled"
    else -> (e::class.simpleName ?: "error") + (e.message?.let { ": " + it.take(80) } ?: "")
}

/**
 * 2.4.0: every request runs on its own pooled thread; cancelling the coroutine (leaving the screen, a race won by
 * another server, a time budget) disconnects the socket at once. Before, `withContext(IO) { connection… }` could not be
 * cancelled: the coroutine waited for the full read timeout of a slow server.
 */
actual suspend fun httpCall(
    url: String,
    method: String,
    headers: Map<String, String>,
    body: String?,
    followRedirects: Boolean,
    timeoutMs: Int,
): HttpResult {
    val started = System.currentTimeMillis()
    val live = AtomicReference<HttpURLConnection?>(null)
    return suspendCancellableCoroutine { cont ->
        val future = httpPool.submit {
            val r = try {
                blockingCall(url, method, headers, body, followRedirects, timeoutMs, live)
            } catch (e: Throwable) {
                HttpResult(-1, null, null, url, httpErrorText(e))
            }
            if (cont.isActive) cont.resume(r.copy(ms = System.currentTimeMillis() - started))
        }
        cont.invokeOnCancellation {
            runCatching { live.get()?.disconnect() }
            future.cancel(true)
        }
    }
}

private fun blockingCall(
    url: String, method: String, headers: Map<String, String>, body: String?, followRedirects: Boolean, timeoutMs: Int,
    live: AtomicReference<HttpURLConnection?>,
): HttpResult {
    var current = url
    var hops = 0
    val cookies = mutableListOf<String>()
    while (true) {
        val c = URL(current).openConnection() as HttpURLConnection
        live.set(c)
        try {
            c.instanceFollowRedirects = false
            c.connectTimeout = timeoutMs.coerceAtMost(12_000)
            c.readTimeout = timeoutMs
            c.requestMethod = method
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (cookies.isNotEmpty()) c.setRequestProperty("Cookie", cookies.joinToString("; "))
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.encodeToByteArray()) }
            }
            val code = c.responseCode
            val location = c.getHeaderField("Location")
            // Header names are case-insensitive (HTTP/2 answers come in lower case).
            c.headerFields.entries.filter { it.key != null && it.key.equals("Set-Cookie", true) }
                .forEach { e -> e.value.forEach { sc -> cookies += sc.substringBefore(';') } }
            if (code in 300..399 && location != null && followRedirects && hops < 8 && method == "GET") {
                current = URL(URL(current), location).toString()
                hops++
                continue
            }
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.use { s -> s.readNBytesCompat(4_000_000).decodeToString() }
            return HttpResult(code, text, location?.let { URL(URL(current), it).toString() }, current)
        } finally {
            c.disconnect()
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (total < max) {
        val n = read(buf, 0, minOf(buf.size, max - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

actual suspend fun httpBytes(url: String, userAgent: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", userAgent)
            if (c.responseCode in 200..299) c.inputStream.use { it.readNBytesCompat(maxBytes) } else null
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

/** 2.7.0: unpacks gzip data (a gzipped map tile); null when it is not valid gzip. */
actual fun ungzipOrNull(bytes: ByteArray): ByteArray? = runCatching {
    java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readNBytesCompat(16_000_000) }
}.getOrNull()
