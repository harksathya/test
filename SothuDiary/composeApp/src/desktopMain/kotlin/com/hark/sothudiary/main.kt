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
    override fun openMap(lat: Double?, lng: Double?, label: String) {
        val q = if (lat != null && lng != null) "$lat,$lng" else URLEncoder.encode(label, "UTF-8")
        browse("https://www.google.com/maps/search/?api=1&query=$q")
    }
    override val audio: AudioActions = NoAudio
    override val location: LocationActions = object : LocationActions {
        override val supported = false
        override fun currentLocation(onResult: (LatLng?) -> Unit) = onResult(null)
    }
    override fun setReminders(enabled: Boolean) {}
    private val imagesDir = File(System.getProperty("user.home"), ".sothudiary/images")
    override fun imagePath(fileName: String): String? =
        File(imagesDir, fileName).takeIf { fileName.isNotBlank() && it.exists() }?.absolutePath
    override fun pickImage(onResult: (String?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Photo", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir == null || name == null) return onResult(null)
        val ext = name.substringAfterLast('.', "jpg").lowercase().take(5)
        imagesDir.mkdirs()
        val out = File(imagesDir, "img_" + System.currentTimeMillis() + "." + ext)
        onResult(runCatching { File(dir, name).copyTo(out); out.name }.getOrNull())
    }
    override fun pickDocument(onResult: (PickedFile?) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Document (photo or PDF)", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir == null || name == null) return onResult(null)
        val ext = name.substringAfterLast('.', "jpg").lowercase().take(5)
        val mime = when (ext) { "pdf" -> "application/pdf"; "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        imagesDir.mkdirs()
        val out = File(imagesDir, "doc_" + System.currentTimeMillis() + "." + ext)
        onResult(runCatching { File(dir, name).copyTo(out); PickedFile(out.name, mime, name) }.getOrNull())
    }
    override fun readStoredFile(fileName: String): ByteArray? =
        if (fileName.isBlank() || fileName.contains('/')) null else File(imagesDir, fileName).takeIf { it.exists() }?.readBytes()
    override fun deleteImage(fileName: String) {
        if (fileName.isNotBlank() && !fileName.contains('/')) File(imagesDir, fileName).delete()
    }
    override fun recognizeText(fileName: String, onResult: (String?) -> Unit) = onResult(null)
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

    override fun bundledText(name: String): String? =
        runCatching { DesktopPlatform::class.java.getResourceAsStream("/$name.txt")?.use { it.readBytes().decodeToString() } }.getOrNull()
    private fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}

fun main() {
    val repo = JsonRepository(DesktopFileStore())
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = AppName.EN + " – " + AppName.TA,
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App(repo = repo, platform = DesktopPlatform())
    }
}
}
