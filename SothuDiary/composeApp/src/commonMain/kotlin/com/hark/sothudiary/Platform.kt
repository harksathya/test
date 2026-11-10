package com.hark.sothudiary

import androidx.compose.runtime.Composable

/** 2.8.0: a file copied into app storage by [PlatformActions.pickDocument]. */
data class PickedFile(val fileName: String, val mime: String, val displayName: String)

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

    /** 2.3.0: height / width of a stored image (to keep an FMB sketch's proportions on the map), null if unknown. */
    fun imageAspect(fileName: String): Double? = null

    /** 2.8.0: lets the user pick a photo or PDF (EC, patta …); it is copied into app storage. Null when cancelled / unsupported. */
    fun pickDocument(onResult: (PickedFile?) -> Unit) { onResult(null) }

    /** 2.8.0: the bytes of a file in app storage (the images folder), or null when missing. */
    fun readStoredFile(fileName: String): ByteArray? = null

    /**
     * 2.9.0: [count] pages of a stored PDF from page [from] (0-based) as JPEG images, with the PDF's page count; null
     * where pages cannot be rendered (desktop) – the whole PDF is then sent in one request.
     */
    fun pdfPageImages(fileName: String, from: Int, count: Int): Pair<Int, List<ByteArray>>? = null

    /** 2.4.0: a text file bundled with the app ("tn_villages": the LGD / GODL village directory), null if missing. */
    fun bundledText(name: String): String? = null

    /**
     * 2.4.0: opens the system share sheet with [text] and files made by the app ([files]: absolute paths in app
     * storage, with their MIME type). Falls back to [shareText] where files are not supported.
     */
    fun shareFiles(subject: String, text: String, files: List<Pair<String, String>>) = shareText(subject, text)

    /** 2.4.0: draws a share card (Instagram / WhatsApp status size) and returns its file path, null when unsupported. */
    fun renderShareCard(card: ShareCard): String? = null

    /** 2.4.0: writes [text] to a file in the app's share folder and returns its absolute path. */
    fun writeShareFile(fileName: String, text: String): String? = null

    /**
     * 2.6.0: lets the user pick a .sothu property file (Android: the system file picker, any type, since WhatsApp /
     * Downloads often hand .sothu files over as application/octet-stream) and returns its text, null when cancelled.
     */
    fun pickPropertyFile(onResult: (String?) -> Unit) = pickTextFile(onResult)

    /** 3.0.0: Google Drive backup (Android, Google Identity Services). False on desktop. */
    val driveSupported: Boolean get() = false

    /**
     * 3.0.0: asks Google for an access token for the hidden Drive app folder (scope drive.appdata). [interactive] =
     * may show the Google account / consent screen; false = only a token that needs no screen (automatic backup).
     * The token is used for one backup / restore and never saved.
     */
    fun driveAuthorize(interactive: Boolean, onResult: (DriveAuth) -> Unit) { onResult(DriveAuth(error = "unsupported")) }

    /** 3.0.0: tells Google an access token was refused (HTTP 401) so the next [driveAuthorize] gets a fresh one. */
    fun driveClearToken(token: String, onDone: () -> Unit) { onDone() }

    /** 3.0.0: bytes of a file the app stores in [folder] ("images", "voice", "music"), null when missing. */
    fun readAppFile(folder: String, fileName: String): ByteArray? = if (folder == "images") readStoredFile(fileName) else null

    /** 3.0.0: writes a file restored from Drive into [folder]; false on failure. */
    fun writeAppFile(folder: String, fileName: String, bytes: ByteArray): Boolean = false

    /** 3.0.0: true when [folder]/[fileName] exists in app storage. */
    fun appFileExists(folder: String, fileName: String): Boolean = readAppFile(folder, fileName) != null
}

/** 3.0.0: result of [PlatformActions.driveAuthorize]: a token, or why there is none ([needsScreen] = sign-in screen needed). */
data class DriveAuth(val token: String? = null, val error: String? = null, val needsScreen: Boolean = false, val cancelled: Boolean = false)

/** 2.4.0: what goes on a "Share as image" card. Lines are already in the user's language. */
data class ShareCard(
    val title: String,
    val price: String,
    val facts: List<String>,
    val place: String,
    val coverPath: String?,
    val appName: String,
    val badge: String = "",
    val badgeColor: Long = 0xFF2E7D32,
)

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
suspend fun httpGet(url: String, userAgent: String): String? =
    httpRequest(url, headers = mapOf("User-Agent" to userAgent, "Accept" to "application/json"))?.takeIf { it.ok }?.body

/**
 * Result of [httpRequest]: status code, body (also for errors), the redirect target and the final URL after redirects.
 * 2.4.0: [code] is -1 when no answer came at all; [error] then says why ("timeout", "no internet", "TLS" …) and [ms]
 * how long the call took – shown in the checks' Status / நிலை panels.
 */
data class HttpResult(val code: Int, val body: String?, val location: String?, val finalUrl: String, val error: String? = null, val ms: Long = 0) {
    val ok: Boolean get() = code in 200..299
    val failed: Boolean get() = code < 0
}

/**
 * General HTTP request (GET / POST with headers). Follows redirects across hosts when [followRedirects]
 * (GET only), otherwise returns the 3xx with its [HttpResult.location]. Null on network errors.
 */
suspend fun httpRequest(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    followRedirects: Boolean = true,
    timeoutMs: Int = 20_000,
): HttpResult? = httpCall(url, method, headers, body, followRedirects, timeoutMs).takeUnless { it.failed }

/**
 * 2.4.0: like [httpRequest] but never null – a call with no answer comes back with code -1 and the reason in
 * [HttpResult.error]. Cancellable: leaving the coroutine closes the connection at once (no thread is left hanging).
 * [timeoutMs] is the whole call (connect + read) for one hop.
 */
expect suspend fun httpCall(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    followRedirects: Boolean = true,
    timeoutMs: Int = 20_000,
): HttpResult

/** Downloads a file (e.g. a post thumbnail). Null on errors. */
expect suspend fun httpBytes(url: String, userAgent: String, maxBytes: Int = 8_000_000): ByteArray?

/** 3.0.0: result of [httpUploadBytes] / [httpDownloadBytes]: code (-1 = no answer, see [error]) and the body. */
class BytesResult(val code: Int, val body: ByteArray?, val error: String? = null) {
    val ok: Boolean get() = code in 200..299
    val text: String get() = body?.decodeToString().orEmpty()
}

/** 3.0.0: sends [body] bytes (Drive file upload) and reads the answer as bytes. Never throws. */
expect suspend fun httpUploadBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray?, timeoutMs: Int = 120_000): BytesResult

/** 3.0.0: downloads a file (Drive alt=media) with headers; up to [maxBytes]. Never throws. */
expect suspend fun httpDownloadBytes(url: String, headers: Map<String, String>, maxBytes: Int = 64_000_000, timeoutMs: Int = 120_000): BytesResult

/** 2.7.0: unpacks gzip data (a gzipped map tile); null when it is not valid gzip. */
expect fun ungzipOrNull(bytes: ByteArray): ByteArray?

/**
 * 2.4.0: an identifying User-Agent WITH a contact URL. Since April 2026 overpass-api.de answers HTTP 406 to clients whose
 * User-Agent does not identify them with a way to reach the author – the 2.3 value ("SothuDiary/2.3 (com.hark.sothudiary;
 * personal property diary app)") got 406 on 2026-11-08, which is why every land check on that server failed. Nominatim's
 * policy asks for the same.
 */
const val APP_USER_AGENT = "SothuDiary/2.4 (+https://github.com/harksathya/test; Android property diary)"

// Speech-to-text error codes passed to AudioActions.startSpeech(onError).
const val SPEECH_PERMISSION = "permission"
const val SPEECH_NO_MATCH = "no_match"
const val SPEECH_NETWORK = "network"
const val SPEECH_BUSY = "busy"
const val SPEECH_LANGUAGE = "language"
const val SPEECH_UNAVAILABLE = "unavailable"
const val SPEECH_OTHER = "other"
