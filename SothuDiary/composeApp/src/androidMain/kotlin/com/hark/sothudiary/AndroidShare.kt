package com.hark.sothudiary

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/** 2.4.0: share sheet with files (card image, .sothu file), the share card drawing and the share folder. */
object AndroidShare {
    private fun dir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

    fun writeFile(context: Context, name: String, text: String): String? = runCatching {
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        File(dir(context), safe).apply { writeText(text) }.absolutePath
    }.getOrNull()

    fun share(context: Context, subject: String, text: String, files: List<Pair<String, String>>): Boolean = runCatching {
        val authority = context.packageName + ".share"
        val uris = files.map { (path, _) -> FileProvider.getUriForFile(context, authority, File(path)) }
        if (uris.isEmpty()) return false
        val mime = files.map { it.second }.distinct().singleOrNull() ?: "*/*"
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uris[0])
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.putExtra(Intent.EXTRA_SUBJECT, subject)
        send.putExtra(Intent.EXTRA_TEXT, text)
        send.clipData = ClipData.newRawUri(subject, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
        true
    }.getOrDefault(false)

    /**
     * A 1080 × 1350 card (Instagram portrait post; also fine as a WhatsApp status): cover photo on top, title, price,
     * up to four facts, the place and the app name. Returns the PNG path.
     */
    fun renderCard(context: Context, card: ShareCard): String? = runCatching {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF6F3EE.toInt())
        val photoH = 760
        val cover = card.coverPath?.let { path ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var sample = 1
            while (o.outWidth / sample > 2200 || o.outHeight / sample > 2200) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (cover != null) {
            // Centre-crop into the photo area.
            val scale = maxOf(w.toFloat() / cover.width, photoH.toFloat() / cover.height)
            val sw = w / scale; val sh = photoH / scale
            val sx = (cover.width - sw) / 2f; val sy = (cover.height - sh) / 2f
            c.drawBitmap(cover, android.graphics.Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt()), android.graphics.Rect(0, 0, w, photoH), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val g = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), photoH.toFloat(), 0xFF2F6F62.toInt(), 0xFF9CC3B4.toInt(), Shader.TileMode.CLAMP) }
            c.drawRect(0f, 0f, w.toFloat(), photoH.toFloat(), g)
            val home = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 220f; color = 0x55FFFFFF }
            c.drawText("🏡", w / 2f - 120f, photoH / 2f + 80f, home)
        }
        // Darken the bottom of the photo for the price.
        c.drawRect(0f, photoH - 260f, w.toFloat(), photoH.toFloat(), Paint().apply {
            shader = LinearGradient(0f, photoH - 260f, 0f, photoH.toFloat(), 0x00000000, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        })
        val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 92f; typeface = Typeface.DEFAULT_BOLD }
        if (card.price.isNotBlank()) c.drawText(card.price, 56f, photoH - 56f, pricePaint)
        if (card.badge.isNotBlank()) {
            val bp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 44f; typeface = Typeface.DEFAULT_BOLD }
            val tw = bp.measureText(card.badge)
            val r = RectF(w - tw - 110f, 48f, w - 48f, 128f)
            c.drawRoundRect(r, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card.badgeColor.toInt() })
            c.drawText(card.badge, r.left + 31f, r.bottom - 26f, bp)
        }
        var y = photoH + 52f
        fun block(text: String, size: Float, color: Int, bold: Boolean, maxLines: Int): Float {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
            @Suppress("DEPRECATION")
            val layout = StaticLayout(text, tp, w - 112, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
            val lines = minOf(layout.lineCount, maxLines)
            c.save(); c.translate(56f, y)
            c.clipRect(0f, 0f, (w - 112).toFloat(), layout.getLineBottom(lines - 1).toFloat())
            layout.draw(c); c.restore()
            return layout.getLineBottom(lines - 1).toFloat()
        }
        y += block(card.title, 58f, 0xFF1D1C1A.toInt(), true, 2) + 18f
        if (card.place.isNotBlank()) y += block("📍 " + card.place, 40f, 0xFF5C5A55.toInt(), false, 1) + 22f
        card.facts.take(4).forEach { f -> y += block("•  $f", 38f, 0xFF2B2A27.toInt(), false, 1) + 10f }
        // Footer: app name.
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; color = 0xFF2F6F62.toInt(); typeface = Typeface.DEFAULT_BOLD }
        c.drawRect(0f, h - 96f, w.toFloat(), h.toFloat(), Paint().apply { color = 0xFFE7E1D7.toInt() })
        c.drawText(card.appName, 56f, h - 36f, foot)
        val f = File(dir(context), "card-" + System.currentTimeMillis() + ".png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        // Keep the share folder small.
        dir(context).listFiles()?.filter { it.name.startsWith("card-") && it != f }?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        f.absolutePath
    }.getOrNull()

    /** Reads a shared / opened file (a .sothu property file) – at most 2 MB. */
    fun readUri(context: Context, uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (total < 2_000_000) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n); total += n }
            out.toByteArray().decodeToString()
        }
    }.getOrNull()
}
