package com.hark.sothudiary

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import java.io.File

/**
 * Soft background music: one looping MediaPlayer at low volume, only while the app is in the foreground.
 * Fades in over 2 s. Pauses while a voice note is recorded / played or speech-to-text listens ([hold]),
 * and follows audio focus: ducks for short sounds (navigation prompts), pauses for calls and other media.
 */
class AndroidMusic(private val context: Context, private val bridge: ActivityBridge) : MusicActions {

    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val dir: File get() = File(context.filesDir, MUSIC_DIR).apply { mkdirs() }

    private var enabled = false
    private var volume = 0.12f
    private var customFile = ""
    private var configured = false
    private var foreground = false
    private val holds = mutableSetOf<String>()
    private var focusLost = false
    private var ducked = false

    private var player: MediaPlayer? = null
    private var playerSource: String? = null
    private var fade = 0f
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    override val supported = true
    override val hasDefaultTrack: Boolean = runCatching {
        context.resources.openRawResourceFd(R.raw.bgm_default)?.use { it.length > 0 } ?: false
    }.getOrDefault(false)

    override fun configure(enabled: Boolean, volume: Float, customFile: String) {
        val sourceChanged = configured && customFile != this.customFile
        configured = true
        val turnedOn = enabled && !this.enabled
        this.enabled = enabled
        this.volume = volume.coerceIn(0f, 1f)
        this.customFile = customFile
        if (sourceChanged) releasePlayer()
        // Switching music on again is a fresh request to play, even after another app took audio focus.
        if (turnedOn) focusLost = false
        update()
    }

    /** Activity started / stopped. */
    fun setForeground(on: Boolean) {
        foreground = on
        // Back in the app: play again unless another app is still playing music (then leave it alone).
        if (on && focusLost && !audioManager.isMusicActive) focusLost = false
        update()
    }

    /** Voice note recording / playback or speech-to-text in progress: keep the music paused meanwhile. */
    fun hold(reason: String, on: Boolean) {
        main.post {
            if (on) holds += reason else holds -= reason
            update()
        }
    }

    private fun shouldPlay() = configured && enabled && foreground && holds.isEmpty() && !focusLost && volume > 0.001f

    private fun update() {
        if (shouldPlay()) start() else pause()
        applyVolume()
    }

    private fun sourceFile(): File? =
        customFile.takeIf { it.isNotBlank() && !it.contains('/') }?.let { File(dir, it) }?.takeIf { it.exists() && it.length() > 0 }

    private fun ensurePlayer(): MediaPlayer? {
        val custom = sourceFile()
        val src = custom?.absolutePath ?: (if (hasDefaultTrack) "default" else null) ?: return null
        player?.let { if (playerSource == src) return it }
        releasePlayer()
        return try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            if (custom != null) p.setDataSource(custom.absolutePath)
            else context.resources.openRawResourceFd(R.raw.bgm_default).use { fd -> p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            p.isLooping = true
            p.prepare()
            p.setVolume(0f, 0f)
            player = p
            playerSource = src
            fade = 0f
            p
        } catch (e: Exception) {
            null
        }
    }

    private fun start() {
        if (!requestFocus()) return
        val p = ensurePlayer() ?: return
        if (!runCatching { p.isPlaying }.getOrDefault(false)) {
            fade = 0f
            applyVolume()
            runCatching { p.start() }
            fadeIn()
        }
    }

    private fun pause() {
        main.removeCallbacks(fadeStep)
        player?.let { p -> runCatching { if (p.isPlaying) p.pause() } }
        if (!foreground || !enabled) abandonFocus()
    }

    private val fadeStep = object : Runnable {
        override fun run() {
            fade = (fade + 0.05f).coerceAtMost(1f) // 20 steps x 100 ms = 2 s fade-in
            applyVolume()
            if (fade < 1f) main.postDelayed(this, 100)
        }
    }

    private fun fadeIn() {
        main.removeCallbacks(fadeStep)
        main.postDelayed(fadeStep, 100)
    }

    private fun applyVolume() {
        val v = volume * fade * (if (ducked) 0.3f else 1f)
        runCatching { player?.setVolume(v, v) }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> { ducked = false; focusLost = false; hasFocus = true; update() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true; applyVolume() }
                // A call or a short interruption: pause, resume on GAIN.
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { focusLost = true; pause() }
                // Other media took over: stay paused until the app comes back to the foreground.
                AudioManager.AUDIOFOCUS_LOSS -> { focusLost = true; pause(); abandonFocus() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val res = if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusListener, main)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (!hasFocus && focusRequest == null) return
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else audioManager.abandonAudioFocus(focusListener)
        focusRequest = null
        hasFocus = false
    }

    private fun releasePlayer() {
        main.removeCallbacks(fadeStep)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playerSource = null
    }

    override fun pickTrack(onResult: (String?) -> Unit) {
        bridge.openDocument(arrayOf("audio/*")) { uri ->
            if (uri == null) {
                onResult(null)
                return@openDocument
            }
            Thread {
                val name = runCatching { copyIn(uri) }.getOrNull()
                main.post { onResult(name) }
            }.start()
        }
    }

    /** Copies the chosen song into app storage (so it keeps working after the picker's grant ends). */
    private fun copyIn(uri: Uri): String? {
        var display = "song"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { display = it }
        }
        val ext = display.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "mp3" }
        val out = File(dir, "bgm_" + System.currentTimeMillis() + "." + ext)
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        if (out.length() == 0L) {
            out.delete()
            return null
        }
        // Only keep a file that actually plays.
        val ok = runCatching { MediaPlayer().apply { setDataSource(out.absolutePath); prepare(); release() } }.isSuccess
        if (!ok) {
            out.delete()
            return null
        }
        return out.name
    }

    override fun deleteTrack(fileName: String) {
        if (fileName.isBlank() || fileName.contains('/')) return
        if (fileName == customFile) releasePlayer()
        File(dir, fileName).delete()
    }

    fun release() {
        releasePlayer()
        abandonFocus()
    }

    companion object {
        const val MUSIC_DIR = "music"
    }
}
