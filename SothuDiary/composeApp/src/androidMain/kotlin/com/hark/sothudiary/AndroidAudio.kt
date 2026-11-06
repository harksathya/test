package com.hark.sothudiary

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.io.File

/** Voice notes (MediaRecorder AAC / .m4a in filesDir/voice), playback and live speech-to-text. */
class AndroidAudio(private val context: Context, private val bridge: ActivityBridge) : AudioActions {

    private val main = Handler(Looper.getMainLooper())
    private val dir: File get() = File(context.filesDir, VOICE_DIR).apply { mkdirs() }

    private var recorder: MediaRecorder? = null
    private var recFile: File? = null
    private var recStartedAt = 0L
    private var player: MediaPlayer? = null
    private var recognizer: SpeechRecognizer? = null

    override val recordingSupported: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    override val speechSupported: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) ||
            recognizeIntent("en-IN").resolveActivity(context.packageManager) != null

    private fun withMic(cb: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) cb(true)
        else bridge.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), cb)
    }

    override fun startRecording(onStarted: (Boolean) -> Unit) {
        withMic { ok -> onStarted(ok && begin()) }
    }

    @Suppress("DEPRECATION")
    private fun begin(): Boolean {
        stopPlayback()
        stopSpeech()
        cancelRecording()
        val f = File(dir, "vn_" + System.currentTimeMillis() + ".m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setAudioChannels(1)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recFile = f
            recStartedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            f.delete()
            false
        }
    }

    override fun stopRecording(): AudioClip? {
        val r = recorder ?: return null
        val f = recFile
        val elapsed = System.currentTimeMillis() - recStartedAt
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        recorder = null
        recFile = null
        if (f == null) return null
        if (!ok || f.length() == 0L) {
            f.delete()
            return null
        }
        return AudioClip(f.name, durationOf(f) ?: elapsed)
    }

    override fun cancelRecording() {
        val r = recorder ?: return
        runCatching { r.stop() }
        runCatching { r.release() }
        recorder = null
        recFile?.delete()
        recFile = null
    }

    private fun durationOf(f: File): Long? = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(f.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrNull()

    override fun importAudio(onResult: (AudioClip?) -> Unit) {
        bridge.openDocument(arrayOf("audio/*")) { uri ->
            if (uri == null) {
                onResult(null)
                return@openDocument
            }
            Thread {
                val clip = runCatching { copyIn(uri) }.getOrNull()
                main.post { onResult(clip) }
            }.start()
        }
    }

    private fun copyIn(uri: Uri): AudioClip? {
        var display = "audio"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { display = it }
        }
        val ext = display.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "m4a" }
        val out = File(dir, "imp_" + System.currentTimeMillis() + "." + ext)
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        if (out.length() == 0L) {
            out.delete()
            return null
        }
        return AudioClip(out.name, durationOf(out) ?: 0L)
    }

    override fun play(fileName: String, onComplete: () -> Unit): Boolean {
        stopPlayback()
        val f = File(dir, fileName)
        if (!f.exists()) return false
        return try {
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { onComplete() }
            p.prepare()
            p.start()
            player = p
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun pause() { runCatching { player?.pause() } }
    override fun resume() { runCatching { player?.start() } }
    override fun stopPlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
    override fun seekTo(ms: Long) { runCatching { player?.seekTo(ms.toInt()) } }
    override fun positionMs(): Long = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L
    override fun durationMs(): Long = runCatching { player?.duration?.toLong() }.getOrNull() ?: 0L

    override fun deleteAudio(fileName: String) {
        if (fileName.isBlank() || fileName.contains('/')) return
        File(dir, fileName).delete()
    }

    private fun recognizeIntent(langTag: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    override fun startSpeech(langTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        withMic { ok ->
            if (!ok) {
                onError(SPEECH_PERMISSION)
                return@withMic
            }
            stopPlayback()
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                listen(langTag, onPartial, onFinal, onError)
            } else {
                // No recognition service bound: fall back to the system speech dialog, if any.
                try {
                    bridge.startForResult(recognizeIntent(langTag)) { _, data ->
                        val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                        if (text.isNullOrBlank()) onError(SPEECH_NO_MATCH) else onFinal(text)
                    }
                } catch (e: ActivityNotFoundException) {
                    onError(SPEECH_UNAVAILABLE)
                }
            }
        }
    }

    private fun listen(langTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        stopSpeech()
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            onError(SPEECH_UNAVAILABLE)
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onError(SPEECH_NO_MATCH) else onFinal(text)
                stopSpeech()
            }
            override fun onError(error: Int) {
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SPEECH_NO_MATCH
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> SPEECH_NETWORK
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SPEECH_PERMISSION
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SPEECH_BUSY
                        12, 13 -> SPEECH_LANGUAGE // ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE (API 31+)
                        else -> SPEECH_OTHER
                    },
                )
                stopSpeech()
            }
        })
        try {
            r.startListening(recognizeIntent(langTag))
        } catch (e: Exception) {
            onError(SPEECH_UNAVAILABLE)
            stopSpeech()
        }
    }

    override fun stopSpeech() {
        val r = recognizer ?: return
        recognizer = null
        runCatching { r.stopListening() }
        runCatching { r.destroy() }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
        stopSpeech()
    }

    companion object {
        const val VOICE_DIR = "voice"
    }
}
