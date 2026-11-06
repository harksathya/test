package com.hark.sothudiary

import android.content.ActivityNotFoundException
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
        repo ?: synchronized(this) { repo ?: JsonRepository(AndroidFileStore(context)).also { repo = it } }
}

class AndroidPlatform(
    private val context: Context,
    private val launchPicker: ((String?) -> Unit) -> Unit,
) : PlatformActions {

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

    override fun pickTextFile(onResult: (String?) -> Unit) = launchPicker(onResult)

    override fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
