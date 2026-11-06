package com.hark.sothudiary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun httpGet(url: String, userAgent: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode in 200..299) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
