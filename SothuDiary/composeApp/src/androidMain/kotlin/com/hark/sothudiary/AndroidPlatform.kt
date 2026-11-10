package com.hark.sothudiary

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hark.sothudiary.data.FileStore
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.domain.waNumber
import java.io.File

/** Stores the data file in the app's private files directory. */
class AndroidFileStore(context: Context) : FileStore {
    private val file = File(context.filesDir, "sothu_diary.json")
    override fun read(): String? = if (file.exists()) file.readText() else null
    override fun write(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}

object AppGraph {
    @Volatile private var repo: JsonRepository? = null
    fun repository(context: Context): JsonRepository =
        repo ?: synchronized(this) { repo ?: JsonRepository(AndroidFileStore(context.applicationContext)).also { repo = it } }
}

class AndroidPlatform(
    private val context: Context,
    private val bridge: ActivityBridge,
) : PlatformActions {

    override val music = AndroidMusic(context, bridge)
    override val audio = AndroidAudio(context, bridge, music)
    override val location: LocationActions = AndroidLocation(context, bridge)

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No app found to open this")
        }
    }

    override fun call(phone: String) {
        if (phone.isBlank()) return
        start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })))
    }

    override fun whatsapp(phone: String, message: String) {
        val num = waNumber(phone)
        if (num.isBlank()) return
        val url = "https://wa.me/$num" + if (message.isNotBlank()) "?text=" + Uri.encode(message) else ""
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun openUrl(url: String) {
        if (url.isBlank()) return
        val fixed = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        start(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
    }

    override fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(Intent.createChooser(send, subject))
    }

    override fun pickTextFile(onResult: (String?) -> Unit) {
        bridge.openDocument(arrayOf("application/json", "text/plain", "*/*")) { uri ->
            onResult(uri?.let { u -> runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() })
        }
    }

    /** 2.6.0: "Import .sothu file" – the system file picker; the file is read off the main thread (at most 2 MB). */
    override fun pickPropertyFile(onResult: (String?) -> Unit) {
        bridge.openDocument(com.hark.sothudiary.domain.share.SOTHU_PICK_TYPES) { uri ->
            if (uri == null) { onResult(null); return@openDocument }
            Thread {
                // A file that cannot be opened reads as "" (the import dialog then says it is not a property file).
                val text = AndroidShare.readUri(context, uri) ?: ""
                android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(text) }
            }.start()
        }
    }

    override fun openMap(lat: Double?, lng: Double?, label: String) {
        if (lat != null && lng != null) {
            val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(" + Uri.encode(label.ifBlank { "Property" }) + ")")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, geo))
                return
            } catch (e: ActivityNotFoundException) {
                // fall through to the web link
            }
            openUrl("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
        } else if (label.isNotBlank()) {
            openUrl("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(label))
        }
    }

    override fun imagePath(fileName: String): String? {
        if (fileName.isBlank()) return null
        val f = java.io.File(AndroidImages.dir(context), fileName)
        return if (f.exists()) f.absolutePath else null
    }

    override fun pickImage(onResult: (String?) -> Unit) {
        bridge.openDocument(arrayOf("image/*")) { uri ->
            if (uri == null) onResult(null)
            else Thread {
                val name = AndroidImages.copyIn(context, uri)
                android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(name) }
            }.start()
        }
    }

    override fun pickDocument(onResult: (PickedFile?) -> Unit) {
        bridge.openDocument(arrayOf("image/*", "application/pdf")) { uri ->
            if (uri == null) onResult(null)
            else Thread {
                val f = AndroidImages.copyDocument(context, uri)
                android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(f) }
            }.start()
        }
    }

    // ---- 3.0.0: Google Drive backup ----
    override val driveSupported: Boolean get() = true

    private fun appDir(folder: String): java.io.File? = when (folder) {
        "images" -> AndroidImages.dir(context)
        "voice" -> java.io.File(context.filesDir, AndroidAudio.VOICE_DIR).apply { mkdirs() }
        "music" -> java.io.File(context.filesDir, AndroidMusic.MUSIC_DIR).apply { mkdirs() }
        else -> null
    }

    private fun appFile(folder: String, fileName: String): java.io.File? =
        if (fileName.isBlank() || fileName.contains('/') || fileName.startsWith(".")) null else appDir(folder)?.let { java.io.File(it, fileName) }

    override fun readAppFile(folder: String, fileName: String): ByteArray? =
        appFile(folder, fileName)?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }

    override fun appFileExists(folder: String, fileName: String): Boolean = appFile(folder, fileName)?.let { it.exists() && it.length() > 0 } == true

    override fun writeAppFile(folder: String, fileName: String, bytes: ByteArray): Boolean {
        val f = appFile(folder, fileName) ?: return false
        return runCatching {
            val tmp = java.io.File(f.parentFile, f.name + ".part")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) { f.writeBytes(bytes); tmp.delete() }
            true
        }.getOrDefault(false)
    }

    override fun driveAuthorize(interactive: Boolean, onResult: (DriveAuth) -> Unit) {
        val activity = bridge.activity
        val client = com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(activity)
        val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
            .setRequestedScopes(listOf(com.google.android.gms.common.api.Scope(com.hark.sothudiary.domain.drive.DriveRules.SCOPE)))
            .build()
        fun failure(e: Exception): DriveAuth = when (e) {
            is com.google.android.gms.common.api.ApiException ->
                DriveAuth(error = "ApiException " + e.statusCode + ": " + (e.message ?: ""), cancelled = e.statusCode == 16 || e.statusCode == 12501)
            else -> DriveAuth(error = (e::class.simpleName ?: "error") + ": " + (e.message ?: ""))
        }
        try {
            client.authorize(request)
                .addOnSuccessListener { r ->
                    val token = r.accessToken
                    val pi = r.pendingIntent
                    when {
                        r.hasResolution() && pi != null -> {
                            if (!interactive) { onResult(DriveAuth(needsScreen = true)); return@addOnSuccessListener }
                            bridge.startIntentSender(pi.intentSender) { code, data ->
                                if (code != android.app.Activity.RESULT_OK || data == null) {
                                    onResult(DriveAuth(cancelled = true, error = "cancelled ($code)"))
                                } else try {
                                    val res = client.getAuthorizationResultFromIntent(data)
                                    onResult(res.accessToken?.let { DriveAuth(token = it) } ?: DriveAuth(error = "no access token"))
                                } catch (e: Exception) {
                                    onResult(failure(e))
                                }
                            }
                        }
                        token != null -> onResult(DriveAuth(token = token))
                        else -> onResult(DriveAuth(error = "no access token"))
                    }
                }
                .addOnFailureListener { e -> onResult(failure(e)) }
        } catch (e: Exception) {
            onResult(failure(e))
        }
    }

    override fun driveClearToken(token: String, onDone: () -> Unit) {
        try {
            com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(bridge.activity)
                .clearToken(com.google.android.gms.auth.api.identity.ClearTokenRequest.builder().setToken(token).build())
                .addOnCompleteListener { onDone() }
        } catch (e: Exception) {
            onDone()
        }
    }

    override fun readStoredFile(fileName: String): ByteArray? {
        if (fileName.isBlank() || fileName.contains('/')) return null
        val f = java.io.File(AndroidImages.dir(context), fileName)
        return if (f.exists()) runCatching { f.readBytes() }.getOrNull() else null
    }

    /** 2.9.0: PDF pages as JPEG (1600 px wide, white background) for page-by-page reading of a long EC. */
    override fun pdfPageImages(fileName: String, from: Int, count: Int): Pair<Int, List<ByteArray>>? {
        if (fileName.isBlank() || fileName.contains('/')) return null
        val f = java.io.File(AndroidImages.dir(context), fileName)
        if (!f.exists()) return null
        return runCatching {
            val fd = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val r = android.graphics.pdf.PdfRenderer(fd)
            try {
                val total = r.pageCount
                val out = mutableListOf<ByteArray>()
                for (i in from until minOf(from + count, total)) {
                    val page = r.openPage(i)
                    try {
                        val w = 1400
                        val h = (w.toDouble() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, 4000)
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bos = java.io.ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
                        bmp.recycle()
                        out += bos.toByteArray()
                    } finally { page.close() }
                }
                total to out
            } finally { r.close(); fd.close() }
        }.getOrNull()
    }

    override fun imageAspect(fileName: String): Double? {
        val path = imagePath(fileName) ?: return null
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outHeight.toDouble() / o.outWidth else null
    }

    override fun deleteImage(fileName: String) {
        if (fileName.isBlank() || fileName.contains('/')) return
        java.io.File(AndroidImages.dir(context), fileName).delete()
    }

    override fun recognizeText(fileName: String, onResult: (String?) -> Unit) = AndroidImages.recognize(context, fileName, onResult)

    override fun saveImage(bytes: ByteArray, ext: String): String? = AndroidImages.saveBytes(context, bytes, ext)

    override val hasWebReader: Boolean get() = true

    override fun setReminders(enabled: Boolean) {
        Reminders.schedule(context, enabled)
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            bridge.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { }
        }
    }

    override fun bundledText(name: String): String? = runCatching {
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        if (id == 0) null else context.resources.openRawResource(id).use { it.readBytes().decodeToString() }
    }.getOrNull()

    override fun shareFiles(subject: String, text: String, files: List<Pair<String, String>>) {
        if (!AndroidShare.share(context, subject, text, files)) shareText(subject, text)
    }

    override fun renderShareCard(card: ShareCard): String? = AndroidShare.renderCard(context, card)

    override fun writeShareFile(fileName: String, text: String): String? = AndroidShare.writeFile(context, fileName, text)

    override fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
