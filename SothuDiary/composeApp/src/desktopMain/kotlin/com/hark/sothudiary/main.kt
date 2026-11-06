package com.hark.sothudiary

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.hark.sothudiary.data.FileStore
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.domain.waNumber
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.net.URLEncoder

/** Desktop data file: ~/.sothudiary/sothu_diary.json */
class DesktopFileStore : FileStore {
    private val file = File(System.getProperty("user.home"), ".sothudiary/sothu_diary.json")
    override fun read(): String? = if (file.exists()) file.readText() else null
    override fun write(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}

class DesktopPlatform : PlatformActions {
    private fun browse(url: String) {
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }
    }
    override fun call(phone: String) {
        // No dialer on desktop: copy the number to the clipboard.
        copy(phone)
    }
    override fun whatsapp(phone: String, message: String) {
        val num = waNumber(phone)
        if (num.isNotBlank()) browse("https://wa.me/$num" + if (message.isNotBlank()) "?text=" + URLEncoder.encode(message, "UTF-8") else "")
    }
    override fun openUrl(url: String) = browse(url)
    override fun shareText(subject: String, text: String) {
        val dialog = FileDialog(null as Frame?, subject, FileDialog.SAVE)
        dialog.file = "sothu_diary_backup.json"
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir != null && name != null) File(dir, name).writeText(text)
    }
    override fun pickTextFile(onResult: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Import", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        onResult(if (dir != null && name != null) runCatching { File(dir, name).readText() }.getOrNull() else null)
    }
    override fun toast(message: String) = println(message)
    private fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}

fun main() {
    val repo = JsonRepository(DesktopFileStore())
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Sothu Diary – சொத்து டைரி",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App(repo = repo, platform = DesktopPlatform())
    }
}
}
