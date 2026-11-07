package com.hark.sothudiary.domain.share

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

actual fun deflateRaw(data: ByteArray): ByteArray {
    val d = Deflater(Deflater.BEST_COMPRESSION, true)
    try {
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream(data.size / 2 + 64)
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        return out.toByteArray()
    } finally {
        d.end()
    }
}

actual fun inflateRaw(data: ByteArray): ByteArray? {
    val inf = Inflater(true)
    return try {
        inf.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4 + 64)
        val buf = ByteArray(8192)
        var guard = 0
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0) {
                if (inf.needsInput() || inf.needsDictionary()) break
                if (++guard > 1000) break
            }
            out.write(buf, 0, n)
            // A share link is small; refuse anything absurd.
            if (out.size() > 2_000_000) return null
        }
        if (!inf.finished()) null else out.toByteArray()
    } catch (e: Exception) {
        null
    } finally {
        inf.end()
    }
}
