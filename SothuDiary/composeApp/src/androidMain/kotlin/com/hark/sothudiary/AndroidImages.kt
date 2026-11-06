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
