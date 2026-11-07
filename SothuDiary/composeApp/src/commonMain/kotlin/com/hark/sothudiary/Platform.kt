package com.hark.sothudiary

import androidx.compose.runtime.Composable

/** Platform services the shared UI needs. Android and desktop provide their own implementation. */
interface PlatformActions {
    /** Opens the dialer (Android) for this number. */
    fun call(phone: String)

    /** Opens a WhatsApp chat via https://wa.me/91XXXXXXXXXX */
    fun whatsapp(phone: String, message: String = "")

    fun openUrl(url: String)

    /** Opens a point in a maps app (geo: intent on Android, Google Maps in the browser otherwise). */
    fun openMap(lat: Double?, lng: Double?, label: String)

    /** Shares plain text (used for JSON export). */
    fun shareText(subject: String, text: String)

    /** Lets the user pick a file and returns its text, or null if cancelled / unsupported. */
    fun pickTextFile(onResult: (String?) -> Unit)

    /** A short message (toast / snackbar-like). */
    fun toast(message: String)

    /** Voice notes and speech-to-text. */
    val audio: AudioActions

    /** Device location. */
    val location: LocationActions

    /** Absolute path of an image saved in app storage (for display), or null if missing. */
    fun imagePath(fileName: String): String?

    /** Lets the user pick a photo; it is copied into app storage. Returns the stored file name. */
    fun pickImage(onResult: (String?) -> Unit)

    fun deleteImage(fileName: String)

    /** On-device text recognition (OCR) of a stored image. Null when unsupported or nothing found. */
    fun recognizeText(fileName: String, onResult: (String?) -> Unit)

    /** (Re)schedules or cancels the daily 9 AM follow-up reminder. Asks for notification permission when needed. */
    fun setReminders(enabled: Boolean)

    /** Saves downloaded image bytes (e.g. a post thumbnail) into app storage. Returns the stored file name. */
    fun saveImage(bytes: ByteArray, ext: String = "jpg"): String? = null

    /** True where the in-app browser ("Read post" with login) is available (Android WebView). */
    val hasWebReader: Boolean get() = false

    /** Soft background music (Android). */
    val music: MusicActions get() = NoMusic
}

/** Background music: a quiet looping track while the app is in the foreground. */
interface MusicActions {
    val supported: Boolean
    /** A default (royalty-free) track is bundled with the app. */
    val hasDefaultTrack: Boolean
    /** Applies the user's settings; [customFile] blank = the bundled default track. */
    fun configure(enabled: Boolean, volume: Float, customFile: String)
    /** Lets the user pick an audio file; it is copied into app storage. Returns the stored file name. */
    fun pickTrack(onResult: (String?) -> Unit)
    fun deleteTrack(fileName: String)
}

object NoMusic : MusicActions {
    override val supported = false
    override val hasDefaultTrack = false
    override fun configure(enabled: Boolean, volume: Float, customFile: String) {}
    override fun pickTrack(onResult: (String?) -> Unit) = onResult(null)
    override fun deleteTrack(fileName: String) {}
}

/** Content shared into the app from another app (text and / or images already copied into app storage). */
data class SharedContent(val text: String, val imageFiles: List<String>)

data class LatLng(val lat: Double, val lng: Double)

interface LocationActions {
    val supported: Boolean
    /** Asks for permission if needed and returns the current position, or null (denied / unavailable / timeout). */
    fun currentLocation(onResult: (LatLng?) -> Unit)
}

/** A saved audio clip in app storage. */
data class AudioClip(val fileName: String, val durationMs: Long)

interface AudioActions {
    val recordingSupported: Boolean
    val speechSupported: Boolean

    /** Asks for the microphone permission if needed, then starts recording. onStarted(false) on failure. */
    fun startRecording(onStarted: (Boolean) -> Unit)
    /** Stops and saves the recording. */
    fun stopRecording(): AudioClip?
    fun cancelRecording()
    /** Lets the user pick an existing audio file; it is copied into app storage. */
    fun importAudio(onResult: (AudioClip?) -> Unit)

    fun play(fileName: String, onComplete: () -> Unit): Boolean
    fun pause()
    fun resume()
    fun stopPlayback()
    fun seekTo(ms: Long)
    fun positionMs(): Long
    fun durationMs(): Long
    fun deleteAudio(fileName: String)

    /**
     * Live speech-to-text from the microphone. [langTag] is "ta-IN" or "en-IN".
     * onPartial gives interim text, onFinal the final text, onError a reason (shown to the user).
     */
    fun startSpeech(langTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit)
    fun stopSpeech()
}

/** Used where a platform has no audio support (desktop). */
object NoAudio : AudioActions {
    override val recordingSupported = false
    override val speechSupported = false
    override fun startRecording(onStarted: (Boolean) -> Unit) = onStarted(false)
    override fun stopRecording(): AudioClip? = null
    override fun cancelRecording() {}
    override fun importAudio(onResult: (AudioClip?) -> Unit) = onResult(null)
    override fun play(fileName: String, onComplete: () -> Unit) = false
    override fun pause() {}
    override fun resume() {}
    override fun stopPlayback() {}
    override fun seekTo(ms: Long) {}
    override fun positionMs() = 0L
    override fun durationMs() = 0L
    override fun deleteAudio(fileName: String) {}
    override fun startSpeech(langTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) = onError("unsupported")
    override fun stopSpeech() {}
}

/** System back button handling (Android back gesture); no-op on desktop. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Simple HTTP GET returning the body, or null on any error. Runs off the main thread. */
expect suspend fun httpGet(url: String, userAgent: String): String?

/** Result of [httpRequest]: status code, body (also for errors), the redirect target and the final URL after redirects. */
data class HttpResult(val code: Int, val body: String?, val location: String?, val finalUrl: String) {
    val ok: Boolean get() = code in 200..299
}

/**
 * General HTTP request (GET / POST with headers). Follows redirects across hosts when [followRedirects]
 * (GET only), otherwise returns the 3xx with its [HttpResult.location]. Null on network errors.
 */
expect suspend fun httpRequest(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    followRedirects: Boolean = true,
    timeoutMs: Int = 20_000,
): HttpResult?

/** Downloads a file (e.g. a post thumbnail). Null on errors. */
expect suspend fun httpBytes(url: String, userAgent: String, maxBytes: Int = 8_000_000): ByteArray?

const val APP_USER_AGENT = "SothuDiary/2.2 (com.hark.sothudiary; personal property diary app)"

// Speech-to-text error codes passed to AudioActions.startSpeech(onError).
const val SPEECH_PERMISSION = "permission"
const val SPEECH_NO_MATCH = "no_match"
const val SPEECH_NETWORK = "network"
const val SPEECH_BUSY = "busy"
const val SPEECH_LANGUAGE = "language"
const val SPEECH_UNAVAILABLE = "unavailable"
const val SPEECH_OTHER = "other"
