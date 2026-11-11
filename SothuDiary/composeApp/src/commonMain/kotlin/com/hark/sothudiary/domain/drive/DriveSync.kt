package com.hark.sothudiary.domain.drive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hark.sothudiary.BytesResult
import com.hark.sothudiary.DriveAuth
import com.hark.sothudiary.PlatformActions
import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.AppSettings
import com.hark.sothudiary.data.JsonRepository
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.httpDownloadBytes
import com.hark.sothudiary.httpUploadBytes
import com.hark.sothudiary.i18n.S30
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/**
 * 3.0.0: Google Drive backup / sync to the user's own Drive, hidden app folder (`appDataFolder`, scope drive.appdata).
 * Layout on Drive: [JSON_NAME] (= Repository.exportJson) + one Drive file per stored file, named "<folder>__<file name>"
 * (images/, voice/, music/). Stored file names are unique (time stamps), so a file is uploaded once and never replaced;
 * Drive files no longer referenced by the data are deleted after a successful backup.
 */
object DriveRules {
    const val JSON_NAME = "sothu_diary.json"
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    /** Automatic backup this long after the last change (debounced). */
    const val AUTO_DELAY_MS = 30_000L
    const val API = "https://www.googleapis.com/drive/v3"
    const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
}

/** A stored app file: [folder] is "images", "voice" or "music". */
data class StoredRef(val folder: String, val name: String) {
    val driveName: String get() = folder + "__" + name
}

data class DriveFile(val id: String, val name: String, val size: Long = 0)

enum class RestoreChoice { KEEP_PHONE, USE_DRIVE, MERGE }

/** What a backup has to do: files to upload, Drive files to delete, and the id of the JSON file to replace (null = create). */
data class BackupPlan(val upload: List<StoredRef>, val delete: List<DriveFile>, val jsonFileId: String?)

private val FOLDERS = setOf("images", "voice", "music")

/** "images__img_1.jpg" → StoredRef("images", "img_1.jpg"); null for the JSON or unknown names. */
fun refFromDriveName(n: String): StoredRef? {
    val folder = n.substringBefore("__", "")
    val name = n.substringAfter("__", "")
    return if (folder in FOLDERS && name.isNotBlank() && !name.contains('/')) StoredRef(folder, name) else null
}

/** Every stored file the data refers to (photos, documents, sketch, voice notes, the user's own song). */
fun referencedFiles(d: AppData): List<StoredRef> {
    val out = LinkedHashSet<StoredRef>()
    fun img(n: String?) { if (!n.isNullOrBlank() && !n.contains('/')) out += StoredRef("images", n) }
    d.properties.forEach { p ->
        img(p.imageFile)
        p.photos.forEach(::img)
        p.documents.forEach { img(it.fileName) }
        img(p.sketch?.file)
    }
    d.interactions.forEach { i -> i.voiceNotes.forEach { v -> if (v.fileName.isNotBlank() && !v.fileName.contains('/')) out += StoredRef("voice", v.fileName) } }
    if (d.settings.bgmFile.isNotBlank() && !d.settings.bgmFile.contains('/')) out += StoredRef("music", d.settings.bgmFile)
    return out.toList()
}

/**
 * Upload what Drive does not have yet (and the phone has), delete Drive files that the data no longer refers to, and
 * extra copies of the JSON (the first one is replaced).
 */
fun planBackup(refs: List<StoredRef>, existsLocally: (StoredRef) -> Boolean, remote: List<DriveFile>): BackupPlan {
    val remoteNames = remote.map { it.name }.toSet()
    val wanted = refs.map { it.driveName }.toSet()
    val upload = refs.filter { it.driveName !in remoteNames && existsLocally(it) }
    val jsons = remote.filter { it.name == DriveRules.JSON_NAME }
    val orphans = remote.filter { f -> refFromDriveName(f.name) != null && f.name !in wanted }
    // Two Drive files with the same name: keep one.
    val dupes = remote.filter { refFromDriveName(it.name) != null }.groupBy { it.name }.values.flatMap { it.drop(1) }
    return BackupPlan(upload, (orphans + dupes + jsons.drop(1)).distinctBy { it.id }, jsons.firstOrNull()?.id)
}

/** Files to download after a restore: referenced, missing on the phone, present on Drive. */
fun planRestore(refs: List<StoredRef>, existsLocally: (StoredRef) -> Boolean, remote: List<DriveFile>): List<Pair<StoredRef, DriveFile>> {
    val byName = remote.associateBy { it.name }
    return refs.filterNot(existsLocally).mapNotNull { r -> byName[r.driveName]?.let { r to it } }
}

/**
 * A phone that never backed up (or was disconnected) must not overwrite a backup that is already on Drive: the user
 * chooses first (keep phone / use Drive / merge).
 */
fun needsRestoreChoice(driveHasBackup: Boolean, lastBackupAt: Long): Boolean = driveHasBackup && lastBackupAt == 0L

/**
 * Merge by id: a property on both sides keeps the newer [com.hark.sothudiary.data.Property.updatedAt] (phone on a
 * tie); everything else (brokers, calls, folders, guideline values, rates) is the union by id with the phone's copy
 * kept for ids on both sides; land checks keep the newer per property. Settings stay the phone's.
 */
fun mergeAppData(phone: AppData, drive: AppData): AppData {
    fun <T, K> union(a: List<T>, b: List<T>, key: (T) -> K): List<T> {
        val have = a.map(key).toSet()
        return a + b.filter { key(it) !in have }
    }
    val driveProps = drive.properties.associateBy { it.id }
    val phoneIds = phone.properties.map { it.id }.toSet()
    val props = phone.properties.map { p -> driveProps[p.id]?.takeIf { it.updatedAt > p.updatedAt } ?: p } +
        drive.properties.filter { it.id !in phoneIds }
    val checks = (phone.landChecks + drive.landChecks).groupBy { it.propertyId }.values.map { l -> l.maxBy { it.fetchedAt } }
    return phone.copy(
        properties = props,
        brokers = union(phone.brokers, drive.brokers) { it.id },
        interactions = union(phone.interactions, drive.interactions) { it.id },
        guidelines = union(phone.guidelines, drive.guidelines) { it.id },
        folders = union(phone.folders, drive.folders) { it.id },
        onlineRates = union(phone.onlineRates, drive.onlineRates) { it.id },
        rateLookups = union(phone.rateLookups, drive.rateLookups) { it.key },
        landChecks = checks,
        settings = mergeAiSettings(phone.settings, drive.settings),
    )
}

/**
 * 3.1.0: on MERGE the phone keeps its own settings, but an AI provider key / model the phone does not have yet comes
 * from the Drive backup (so the same Gmail on a new phone does not need the key typed again).
 */
fun mergeAiSettings(phone: AppSettings, drive: AppSettings): AppSettings {
    val keys = drive.llmKeys.filterValues { it.isNotBlank() } + phone.llmKeys.filterValues { it.isNotBlank() }
    val models = drive.llmModels + phone.llmModels
    val phoneHasGemini = phone.geminiApiKey.isNotBlank()
    val phoneHasAny = phoneHasGemini || phone.llmKeys.values.any { it.isNotBlank() }
    return phone.copy(
        geminiApiKey = if (phoneHasGemini) phone.geminiApiKey else drive.geminiApiKey,
        geminiModel = if (phoneHasGemini) phone.geminiModel else drive.geminiModel,
        llmKeys = keys,
        llmModels = models,
        llmProvider = if (phoneHasAny) phone.llmProvider else drive.llmProvider,
        youtubeApiKey = phone.youtubeApiKey.ifBlank { drive.youtubeApiKey },
    )
}

/** 3.1.0: the first-launch Google sign-in screen shows once, on Android, until it is finished or skipped. */
fun showSignInStep(driveSupported: Boolean, s: AppSettings): Boolean =
    driveSupported && !s.signInStepDone && s.driveAccount.isBlank()

fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "pdf" -> "application/pdf"
    "m4a", "aac", "mp4" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "ogg" -> "audio/ogg"
    "json" -> "application/json"
    else -> "application/octet-stream"
}

/** Multipart/related body for a Drive "create" upload (metadata + content). */
fun multipartBody(boundary: String, metadataJson: String, mime: String, bytes: ByteArray): ByteArray {
    val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadataJson\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n"
    return head.encodeToByteArray() + bytes + "\r\n--$boundary--\r\n".encodeToByteArray()
}

/** "Google refused …" texts for sign-in errors (Android passes "ApiException <code>: <message>"). */
fun authErrorText(error: String, lang: Lang): String {
    val code = Regex("""ApiException (\d+)""").find(error)?.groupValues?.get(1)?.toIntOrNull()
    return when (code) {
        10 -> S30.devError.of(lang)
        7 -> S30.netError.of(lang)
        else -> S30.authFailed.format(lang, error)
    }
}

class DriveApiException(val code: Int, message: String) : Exception(message)

/** Drive REST v3 calls with one access token (never saved). */
class DriveClient(private val token: String) {
    private val auth get() = mapOf("Authorization" to "Bearer $token")
    private val json = Json { ignoreUnknownKeys = true }

    private fun check(r: BytesResult, what: String): BytesResult {
        if (r.ok) return r
        if (r.code < 0) throw DriveApiException(-1, what + ": " + (r.error ?: "no answer"))
        val msg = runCatching {
            json.parseToJsonElement(r.text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: r.text.take(160)
        throw DriveApiException(r.code, "$what: HTTP ${r.code} $msg")
    }

    private fun obj(r: BytesResult): JsonObject = json.parseToJsonElement(r.text).jsonObject

    suspend fun email(): String {
        val r = check(httpDownloadBytes(DriveRules.API + "/about?fields=user(emailAddress)", auth, 1_000_000, 30_000), "about")
        return obj(r)["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    suspend fun list(): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            val url = DriveRules.API + "/files?spaces=appDataFolder&fields=nextPageToken,files(id,name,size)&pageSize=1000" +
                (page?.let { "&pageToken=$it" } ?: "")
            val o = obj(check(httpDownloadBytes(url, auth, 8_000_000, 60_000), "list"))
            o["files"]?.jsonArray?.forEach { e ->
                val f = e.jsonObject
                val id = f["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                out += DriveFile(id, f["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), f["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0)
            }
            page = o["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (page != null)
        return out
    }

    suspend fun create(name: String, bytes: ByteArray, mime: String): String {
        val b = "sothu" + nowMillis()
        val meta = """{"name":"${name.replace("\"", "")}","parents":["appDataFolder"]}"""
        val r = check(httpUploadBytes(DriveRules.UPLOAD + "/files?uploadType=multipart&fields=id", "POST",
            auth + ("Content-Type" to "multipart/related; boundary=$b"), multipartBody(b, meta, mime, bytes)), "upload $name")
        return obj(r)["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    suspend fun update(id: String, bytes: ByteArray, mime: String) {
        check(httpUploadBytes(DriveRules.UPLOAD + "/files/$id?uploadType=media&fields=id", "PATCH", auth + ("Content-Type" to mime), bytes), "update")
    }

    suspend fun download(id: String): ByteArray =
        check(httpDownloadBytes(DriveRules.API + "/files/$id?alt=media", auth), "download").body ?: ByteArray(0)

    suspend fun delete(id: String) {
        val r = httpUploadBytes(DriveRules.API + "/files/$id", "DELETE", auth, null, 30_000)
        if (r.code != 404) check(r, "delete")
    }
}

/** What Drive holds for the signed-in account (after "Connect" / "Restore"). */
class DriveRemote(val email: String, val backup: AppData?, val files: List<DriveFile>)

/** 3.0.0: backup / restore runner; its state is shown on the Settings card. */
object DriveSync {
    private val mutex = Mutex()
    var busy by mutableStateOf(false)
        private set
    /** Progress / result line in the user's language ("" = nothing to show). */
    var status by mutableStateOf("")
        private set
    var lastError by mutableStateOf(false)
        private set

    private fun say(text: String, error: Boolean = false) { status = text; lastError = error }

    private suspend fun authorize(platform: PlatformActions, interactive: Boolean): DriveAuth =
        suspendCancellableCoroutine { cont -> platform.driveAuthorize(interactive) { r -> if (cont.isActive) cont.resume(r) } }

    private suspend fun clear(platform: PlatformActions, token: String) =
        suspendCancellableCoroutine<Unit> { cont -> platform.driveClearToken(token) { if (cont.isActive) cont.resume(Unit) } }

    private class AuthFailed(val auth: DriveAuth) : Exception(auth.error ?: "no token")

    private suspend fun token(platform: PlatformActions, interactive: Boolean): String {
        val a = authorize(platform, interactive)
        return a.token ?: throw AuthFailed(a)
    }

    /** Runs [block] with a fresh token; a 401 clears the token and asks Google once more. */
    private suspend fun <T> withDrive(platform: PlatformActions, interactive: Boolean, block: suspend (DriveClient) -> T): T {
        val t = token(platform, interactive)
        return try {
            block(DriveClient(t))
        } catch (e: DriveApiException) {
            if (e.code != 401) throw e
            clear(platform, t)
            block(DriveClient(token(platform, interactive)))
        }
    }

    private fun errorText(e: Throwable, lang: Lang): String = when (e) {
        is AuthFailed -> when {
            e.auth.cancelled -> S30.cancelled.of(lang)
            e.auth.needsScreen -> S30.needsSignIn.of(lang)
            else -> authErrorText(e.auth.error.orEmpty(), lang)
        }
        is DriveApiException -> when (e.code) {
            403 -> S30.forbidden.format(lang, e.message.orEmpty())
            else -> S30.failed.format(lang, e.message.orEmpty())
        }
        else -> S30.failed.format(lang, e.message ?: e::class.simpleName.orEmpty())
    }

    /** Sign in (interactive) and read what Drive holds. Null on failure ([status] says why). */
    suspend fun connect(repo: JsonRepository, platform: PlatformActions): DriveRemote? = mutex.withLock {
        val lang = repo.data.settings.lang
        busy = true
        say(S30.signingIn.of(lang))
        try {
            withDrive(platform, interactive = true) { c ->
                val email = c.email()
                val files = c.list()
                val jsonFile = files.firstOrNull { it.name == DriveRules.JSON_NAME }
                val backup = jsonFile?.let { repo.parseBackup(c.download(it.id).decodeToString()) }
                repo.setDrive(account = email)
                say(if (backup != null) S30.foundBackup.format(lang, backup.properties.size) else S30.connected.format(lang, email))
                DriveRemote(email, backup, files)
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            say(errorText(e, lang), error = true); null
        } finally { busy = false }
    }

    /** Applies the user's choice for a Drive backup, downloads the missing files, then backs up the result. */
    suspend fun restore(repo: JsonRepository, platform: PlatformActions, remote: DriveRemote, choice: RestoreChoice): Boolean {
        val lang = repo.data.settings.lang
        val ok = mutex.withLock {
            busy = true
            try {
                remote.backup?.let { repo.applyDriveBackup(it, choice) }
                if (choice != RestoreChoice.KEEP_PHONE) {
                    val todo = withContext(Dispatchers.Default) {
                        planRestore(referencedFiles(repo.data), { platform.appFileExists(it.folder, it.name) }, remote.files)
                    }
                    withDrive(platform, interactive = false) { c ->
                        todo.forEachIndexed { i, (ref, f) ->
                            say(S30.downloading.format(lang, i + 1, todo.size))
                            val bytes = c.download(f.id)
                            withContext(Dispatchers.Default) { platform.writeAppFile(ref.folder, ref.name, bytes) }
                        }
                    }
                }
                true
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                say(errorText(e, lang), error = true); false
            } finally { busy = false }
        }
        return ok && backup(repo, platform, interactive = false, force = true)
    }

    /**
     * Uploads new files and the data file, then deletes Drive files no longer used. [force] = after the user chose
     * how to restore (otherwise a phone that never backed up stops when Drive already has a backup).
     */
    suspend fun backup(repo: JsonRepository, platform: PlatformActions, interactive: Boolean, force: Boolean = false): Boolean = mutex.withLock {
        val lang = repo.data.settings.lang
        if (repo.data.settings.driveAccount.isBlank()) return@withLock false
        busy = true
        say(S30.backingUp.of(lang))
        try {
            withDrive(platform, interactive) { c ->
                val remote = c.list()
                val hasJson = remote.any { it.name == DriveRules.JSON_NAME }
                if (!force && needsRestoreChoice(hasJson, repo.data.settings.driveLastBackupAt)) {
                    say(S30.chooseFirst.of(lang), error = true)
                    return@withDrive false
                }
                val data = repo.data
                val plan = withContext(Dispatchers.Default) {
                    planBackup(referencedFiles(data), { platform.appFileExists(it.folder, it.name) }, remote)
                }
                plan.upload.forEachIndexed { i, ref ->
                    say(S30.uploading.format(lang, i + 1, plan.upload.size))
                    val bytes = withContext(Dispatchers.Default) { platform.readAppFile(ref.folder, ref.name) } ?: return@forEachIndexed
                    c.create(ref.driveName, bytes, mimeOf(ref.name))
                }
                val text = repo.exportJson().encodeToByteArray()
                if (plan.jsonFileId != null) c.update(plan.jsonFileId, text, "application/json")
                else c.create(DriveRules.JSON_NAME, text, "application/json")
                plan.delete.forEach { runCatching { c.delete(it.id) } }
                val now = nowMillis()
                repo.setDrive(lastBackupAt = now)
                say(S30.backedUp.format(lang, plan.upload.size))
                true
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            say(errorText(e, lang), error = true); false
        } finally { busy = false }
    }

    fun disconnect(repo: JsonRepository) {
        repo.setDrive(account = "", lastBackupAt = 0)
        say("")
    }
}
