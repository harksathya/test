package com.hark.sothudiary

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** Property photos / shared posters in filesDir/images, plus ML Kit OCR (Latin script). */
object AndroidImages {
    const val DIR = "images"

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /**
     * 2.8.0: copies a picked document (photo or PDF) into app storage. Photos larger than [DOC_MAX_SIDE] px are scaled
     * down to a JPEG (a phone photo of an EC is often 4–8 MB; the AI reads 2400 px well and the upload stays small).
     */
    fun copyDocument(context: Context, uri: Uri): com.hark.sothudiary.PickedFile? = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        var display = "document"
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { display = it }
        }
        val stamp = System.currentTimeMillis().toString() + "_" + (0..999).random()
        if (mime == "application/pdf") {
            val out = File(dir(context), "doc_$stamp.pdf")
            context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
            if (out.length() == 0L) { out.delete(); return null }
            return com.hark.sothudiary.PickedFile(out.name, "application/pdf", display)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        if (side <= 0) return null
        var sample = 1
        while (side / (sample * 2) >= DOC_MAX_SIDE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val big = maxOf(bmp.width, bmp.height)
        val scaled = if (big > DOC_MAX_SIDE) {
            val f = DOC_MAX_SIDE.toFloat() / big
            android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
        } else bmp
        val out = File(dir(context), "doc_$stamp.jpg")
        out.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        com.hark.sothudiary.PickedFile(out.name, "image/jpeg", display)
    }.getOrNull()

    private const val DOC_MAX_SIDE = 2400

    /** Copies a content:// image into app storage, downscaling very large images. Returns the file name. */
    fun copyIn(context: Context, uri: Uri): String? = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val out = File(dir(context), "img_" + System.currentTimeMillis() + "_" + (0..999).random() + "." + ext)
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        if (out.length() == 0L) {
            out.delete()
            null
        } else out.name
    }.getOrNull()

    /** Saves downloaded image bytes (e.g. a post thumbnail – CDN links expire) into app storage. */
    fun saveBytes(context: Context, bytes: ByteArray, ext: String): String? = runCatching {
        if (bytes.size < 200) return null
        // Only keep real images.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val out = File(dir(context), "post_" + System.currentTimeMillis() + "_" + (0..999).random() + "." + ext.ifBlank { "jpg" })
        out.writeBytes(bytes)
        out.name
    }.getOrNull()

    fun recognize(context: Context, fileName: String, onResult: (String?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val f = File(dir(context), fileName)
        if (!f.exists()) {
            onResult(null)
            return
        }
        try {
            // Decode with sampling so huge screenshots do not run out of memory.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp == null) {
                onResult(null)
                return
            }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { r -> main.post { onResult(r.text.takeIf { it.isNotBlank() }) } }
                .addOnFailureListener { main.post { onResult(null) } }
                .addOnCompleteListener { runCatching { recognizer.close() } }
        } catch (e: Throwable) {
            onResult(null)
        }
    }
}
