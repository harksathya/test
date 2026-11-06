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

    override fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
