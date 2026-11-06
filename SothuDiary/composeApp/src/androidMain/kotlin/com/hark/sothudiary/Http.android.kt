package com.hark.sothudiary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun httpGet(url: String, userAgent: String): String? =
    httpRequest(url, headers = mapOf("User-Agent" to userAgent, "Accept" to "application/json"))?.takeIf { it.ok }?.body

actual suspend fun httpRequest(
    url: String,
    method: String,
    headers: Map<String, String>,
    body: String?,
    followRedirects: Boolean,
    timeoutMs: Int,
): HttpResult? = withContext(Dispatchers.IO) {
    runCatching {
        var current = url
        var hops = 0
        val cookies = mutableListOf<String>()
        while (true) {
            val c = URL(current).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = timeoutMs.coerceAtMost(15_000)
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
                c.headerFields["Set-Cookie"]?.forEach { sc -> cookies += sc.substringBefore(';') }
                if (code in 300..399 && location != null && followRedirects && hops < 8 && method == "GET") {
                    current = URL(URL(current), location).toString()
                    hops++
                    continue
                }
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.use { s ->
                    val bytes = s.readNBytesCompat(4_000_000)
                    bytes.decodeToString()
                }
                return@runCatching HttpResult(code, text, location?.let { URL(URL(current), it).toString() }, current)
            } finally {
                c.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }.getOrNull()
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
