package com.hark.sothudiary.data

import com.hark.sothudiary.domain.ai.withLlm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Platform storage for the single JSON data file (Android: filesDir, desktop: ~/.sothudiary). */
interface FileStore {
    fun read(): String?
    fun write(text: String)
}

/** In-memory store, handy for previews/tests and as a fallback. */
class MemoryFileStore : FileStore {
    private var text: String? = null
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
}

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun newId(): String =
    nowMillis().toString(36) + "-" + Random.nextLong(0, Long.MAX_VALUE).toString(36).take(6)

/** Models that older versions saved as the default; they retire, so they now mean "auto" (newest Flash). */
val LEGACY_DEFAULT_MODELS = setOf("gemini-2.0-flash", "gemini-2.5-flash", "gemini-1.5-flash")

/** "auto" for blank / "Auto (latest)" / an old default; otherwise the model id without the "models/" prefix. */
fun normalizeGeminiModel(raw: String): String {
    val m = raw.trim().removePrefix("models/").trim()
    if (m.isBlank() || m.lowercase() in LEGACY_DEFAULT_MODELS || m.lowercase().startsWith("auto") || m.startsWith("தானியங்கி")) return "auto"
    return m
}

/**
 * Upgrades data written by older versions.
 * v1 -> v2: the clearly labelled SAMPLE records that v1 seeded are removed (the app now starts empty).
 * 2.1.1: old default Gemini models become "auto"; placeholder titles ("Instagram") are rebuilt from the caption.
 */
fun migrate(d: AppData): AppData {
    val sampleProps = d.properties.filter { it.sample }.map { it.id }.toSet()
    val sampleBrokers = d.brokers.filter { it.sample }.map { it.id }.toSet()
    return d.copy(
        version = CURRENT_DATA_VERSION,
        brokers = d.brokers.filterNot { it.sample },
        interactions = d.interactions.filterNot { it.sample || (it.propertyId != null && it.propertyId in sampleProps) }
            .map { if (it.brokerId != null && it.brokerId in sampleBrokers) it.copy(brokerId = null) else it },
        guidelines = d.guidelines.filterNot { it.sample },
        settings = d.settings.copy(geminiModel = normalizeGeminiModel(d.settings.geminiModel)),
        properties = d.properties.filterNot { it.sample }
            .map { if (it.brokerId != null && it.brokerId in sampleBrokers) it.copy(brokerId = null) else it }
            .map { com.hark.sothudiary.domain.repairTitle(it, d.settings.lang) }
            // 2.4.0: one shared land record, filled from the older scattered fields (nothing is dropped).
            .map { if (it.land.isEmpty) mirrorToLegacy(it.copy(land = landFromLegacy(it))) else it },
    )
}

interface PropertyRepository {
    val data: AppData
    fun update(transform: (AppData) -> AppData)
}

/**
 * JSON-file backed repository. State is exposed as Compose state so the UI recomposes,
 * and every change is saved to disk immediately (auto-save).
 */
class JsonRepository(private val store: FileStore) : PropertyRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = true
    }

    override var data: AppData by mutableStateOf(AppData())
        private set

    init {
        val text = runCatching { store.read() }.getOrNull()
        val parsed = text?.let { runCatching { json.decodeFromString(AppData.serializer(), it) }.getOrNull() }
        data = migrate(parsed ?: AppData())
        if (parsed == null || parsed != data) persist(data)
    }

    override fun update(transform: (AppData) -> AppData) {
        val prev = data
        val next = syncLand(prev, transform(prev))
        data = next
        persist(next)
    }

    /** 2.4.0: every changed property keeps its land record and the older fields in step ([reconcileLand]). */
    private fun syncLand(prev: AppData, next: AppData): AppData {
        if (prev.properties === next.properties) return next
        val before = prev.properties.associateBy { it.id }
        var changed = false
        val props = next.properties.map { p ->
            val old = before[p.id]
            if (old === p) p else reconcileLand(old, p).also { if (it !== p) changed = true }
        }
        return if (changed) next.copy(properties = props) else next
    }

    private fun persist(d: AppData) {
        runCatching { store.write(json.encodeToString(AppData.serializer(), d)) }
    }

    // ---- Properties ----
    fun upsertProperty(p: Property) = update { d ->
        val stamped = p.copy(updatedAt = nowMillis(), createdAt = if (p.createdAt == 0L) nowMillis() else p.createdAt)
        val exists = d.properties.any { it.id == p.id }
        d.copy(properties = if (exists) d.properties.map { if (it.id == p.id) stamped else it } else listOf(stamped) + d.properties)
    }

    fun deleteProperty(id: String) = update { d ->
        d.copy(
            properties = d.properties.filterNot { it.id == id },
            interactions = d.interactions.filterNot { it.propertyId == id },
            landChecks = d.landChecks.filterNot { it.propertyId == id },
        )
    }

    fun setStatus(id: String, status: DealStatus) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == id) it.copy(status = status, updatedAt = nowMillis()) else it })
    }

    fun toggleDoc(id: String, doc: DocItem) = update { d ->
        d.copy(properties = d.properties.map {
            if (it.id == id) it.copy(docs = if (doc in it.docs) it.docs - doc else it.docs + doc, updatedAt = nowMillis()) else it
        })
    }

    // ---- Brokers ----
    fun upsertBroker(b: Broker) = update { d ->
        val stamped = if (b.createdAt == 0L) b.copy(createdAt = nowMillis()) else b
        val exists = d.brokers.any { it.id == b.id }
        d.copy(brokers = if (exists) d.brokers.map { if (it.id == b.id) stamped else it } else d.brokers + stamped)
    }

    fun deleteBroker(id: String) = update { d ->
        d.copy(
            brokers = d.brokers.filterNot { it.id == id },
            properties = d.properties.map { if (it.brokerId == id) it.copy(brokerId = null) else it },
            interactions = d.interactions.map { if (it.brokerId == id) it.copy(brokerId = null) else it },
        )
    }

    // ---- Interactions ----
    fun upsertInteraction(i: Interaction, updateAskingPrice: Boolean) = update { d ->
        val exists = d.interactions.any { it.id == i.id }
        var props = d.properties
        if (updateAskingPrice && i.propertyId != null && i.quotedPrice != null && i.quotedPrice > 0) {
            props = props.map { if (it.id == i.propertyId) it.copy(askingPrice = i.quotedPrice, updatedAt = nowMillis()) else it }
        }
        // Logging a call on a "New" lead moves it to "Contacted".
        if (i.propertyId != null) {
            props = props.map { if (it.id == i.propertyId && it.status == DealStatus.NEW) it.copy(status = DealStatus.CONTACTED) else it }
        }
        d.copy(
            properties = props,
            interactions = if (exists) d.interactions.map { if (it.id == i.id) i else it } else d.interactions + i,
        )
    }

    fun deleteInteraction(id: String) = update { d -> d.copy(interactions = d.interactions.filterNot { it.id == id }) }

    fun setFollowUpDone(id: String, done: Boolean) = update { d ->
        d.copy(interactions = d.interactions.map { if (it.id == id) it.copy(followUpDone = done) else it })
    }

    // ---- Guideline values ----
    fun upsertGuideline(g: GuidelineValue) = update { d ->
        val exists = d.guidelines.any { it.id == g.id }
        d.copy(guidelines = if (exists) d.guidelines.map { if (it.id == g.id) g else it } else d.guidelines + g)
    }

    fun deleteGuideline(id: String) = update { d -> d.copy(guidelines = d.guidelines.filterNot { it.id == id }) }

    // ---- Folders ----
    fun upsertFolder(f: Folder) = update { d ->
        val exists = d.folders.any { it.id == f.id }
        d.copy(folders = if (exists) d.folders.map { if (it.id == f.id) f else it } else d.folders + f.copy(createdAt = nowMillis()))
    }

    fun deleteFolder(id: String) = update { d ->
        d.copy(
            folders = d.folders.filterNot { it.id == id },
            properties = d.properties.map { if (id in it.folderIds) it.copy(folderIds = it.folderIds - id) else it },
        )
    }

    fun setInFolder(propertyId: String, folderId: String, inFolder: Boolean) = update { d ->
        d.copy(properties = d.properties.map {
            if (it.id == propertyId) it.copy(folderIds = if (inFolder) it.folderIds + folderId else it.folderIds - folderId) else it
        })
    }

    /** Moves a property out of all folders into [folderId] (null = no folder). */
    fun moveToFolder(propertyId: String, folderId: String?) = update { d ->
        d.copy(properties = d.properties.map {
            if (it.id == propertyId) it.copy(folderIds = if (folderId == null) emptySet() else setOf(folderId)) else it
        })
    }

    fun setPropertyImage(propertyId: String, imageFile: String?, imageUrl: String?) = update { d ->
        d.copy(properties = d.properties.map {
            if (it.id == propertyId) it.copy(imageFile = imageFile ?: it.imageFile, imageUrl = imageUrl ?: it.imageUrl) else it
        })
    }

    // ---- Settings / data management ----
    /** Saves a cover found later (backfill / reader) without touching a photo the user picked; optionally fixes a placeholder title. */
    fun setCover(propertyId: String, imageFile: String?, imageUrl: String?, extraPhoto: String? = null, title: String? = null, caption: String? = null) = update { d ->
        d.copy(properties = d.properties.map { p ->
            if (p.id != propertyId) p else p.copy(
                imageFile = if (p.imageFile.isBlank() && imageFile != null) imageFile else p.imageFile,
                imageUrl = imageUrl?.takeIf { it.isNotBlank() } ?: p.imageUrl,
                photos = if (extraPhoto != null && extraPhoto !in p.photos) p.photos + extraPhoto else p.photos,
                title = if (title != null && com.hark.sothudiary.domain.isPlaceholderTitle(p.title)) title else p.title,
                sourceCaption = if (p.sourceCaption.isBlank() && caption != null) caption else p.sourceCaption,
            )
        })
    }

    /** Makes one of the saved photos the cover (the old cover joins the extra photos). */
    fun makeCover(propertyId: String, file: String) = update { d ->
        d.copy(properties = d.properties.map { p ->
            if (p.id != propertyId || p.imageFile == file) p
            else p.copy(imageFile = file, photos = (listOfNotNull(p.imageFile.ifBlank { null }) + p.photos).filter { it != file }.distinct())
        })
    }

    fun setLang(lang: Lang) = update { d -> d.copy(settings = d.settings.copy(lang = lang)) }

    fun setTheme(mode: ThemeMode) = update { d -> d.copy(settings = d.settings.copy(theme = mode)) }

    fun setReminders(on: Boolean) = update { d -> d.copy(settings = d.settings.copy(reminders = on)) }

    fun setSpeechLang(lang: Lang?) = update { d -> d.copy(settings = d.settings.copy(speechLang = lang)) }

    fun setOnboarded() = update { d -> d.copy(settings = d.settings.copy(onboarded = true)) }

    /** 2.5.0: saves [key] / [model] for the LLM [provider] and makes it the one every AI call uses. */
    fun setLlm(provider: com.hark.sothudiary.domain.ai.LlmProvider, key: String, model: String) = update { d ->
        d.copy(settings = d.settings.withLlm(provider, key, model))
    }

    fun setYoutubeKey(youtube: String) = update { d -> d.copy(settings = d.settings.copy(youtubeApiKey = youtube.trim())) }

    fun setApiKeys(youtube: String, gemini: String, model: String) = update { d ->
        d.copy(settings = d.settings.copy(youtubeApiKey = youtube.trim(), geminiApiKey = gemini.trim(), geminiModel = normalizeGeminiModel(model)))
    }

    fun setReviews(propertyId: String, reviews: List<Review>) = update { d ->
        d.copy(properties = d.properties.map {
            if (it.id == propertyId) it.copy(reviews = reviews, reviewsFetchedAt = nowMillis()) else it
        })
    }

    /** Saves rates found online; a rate for the same place / type / kind / source replaces the older one. */
    fun addOnlineRates(rates: List<OnlineRate>) = update { d ->
        fun key(r: OnlineRate) = listOf(r.place.lowercase().trim(), r.group, r.kind, r.sourceUrl.ifBlank { r.sourceName }.lowercase()).joinToString("|")
        val newKeys = rates.map(::key).toSet()
        d.copy(onlineRates = d.onlineRates.filterNot { key(it) in newKeys } + rates)
    }

    /** Stores a live lookup and its rates, replacing an earlier lookup with the same key. */
    fun saveLookup(lookup: RateLookup, rates: List<OnlineRate>) = update { d ->
        // A failed refresh keeps the rates found earlier (they stay usable until they age out).
        if (lookup.error != null) d.copy(rateLookups = d.rateLookups.filterNot { it.key == lookup.key } + lookup)
        else d.copy(
            rateLookups = d.rateLookups.filterNot { it.key == lookup.key } + lookup,
            onlineRates = d.onlineRates.filterNot { it.lookupKey == lookup.key } + rates,
        )
    }

    /** 2.2.0: saves the plot corners drawn on the map (empty = remove them). */
    fun setBoundary(propertyId: String, corners: List<GeoPt>) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(boundary = corners, updatedAt = nowMillis()) else it })
    }

    /** 2.2.0: stores the land check of a property (one per property, the newest replaces the older). */
    fun saveLandCheck(check: LandCheck) = update { d ->
        d.copy(landChecks = d.landChecks.filterNot { it.propertyId == check.propertyId } + check)
    }

    fun clearLandChecks() = update { d -> d.copy(landChecks = emptyList()) }

    /** 2.3.0: saves the survey number the user found (TNGIS / FMB / patta). */
    fun setSurveyNumber(propertyId: String, survey: String) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(surveyNumber = com.hark.sothudiary.domain.land.normalizeSurveyNumber(survey).take(40), updatedAt = nowMillis()) else it })
    }

    /** 2.4.0: saves the shared land details (the older fields follow). */
    fun setLand(propertyId: String, land: LandRecord) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(land = land, updatedAt = nowMillis()) else it })
    }

    /** 2.4.0: blank district / taluk / village filled from the map address of the pin (never overwrites typed values). */
    fun fillLandFromMap(propertyId: String, district: String, taluk: String, village: String) = update { d ->
        d.copy(properties = d.properties.map { p ->
            if (p.id != propertyId) p else {
                val l = p.land.fillFromMap(district, taluk, village)
                if (l == p.land) p else p.copy(land = l)
            }
        })
    }

    /** 2.3.1: TNREGINET place / guideline value / land area of a property. */
    fun setRegistry(propertyId: String, registry: RegistryInfo) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(registry = registry, updatedAt = nowMillis()) else it })
    }

    /** 2.8.0: adds an uploaded document to a property. */
    fun addDocument(propertyId: String, doc: PropertyDocument) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(documents = it.documents + doc, updatedAt = nowMillis()) else it })
    }

    /** 2.9.0: saves the text read from one document (replaces an older reading of it). */
    fun saveDocText(propertyId: String, t: DocText) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(docTexts = it.docTexts.filterNot { x -> x.docId == t.docId } + t) else it })
    }

    /** 2.9.0: adds a message to the EC chat. */
    fun addDocChat(propertyId: String, m: DocChatMsg) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(docChat = it.docChat + m) else it })
    }

    /** 2.9.0: forgets the read text so the documents are read again on the next question. */
    fun clearDocTexts(propertyId: String) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(docTexts = emptyList()) else it })
    }

    /** 2.9.1: drops the last chat message (an unanswered question that is asked again). */
    fun removeLastDocChat(propertyId: String) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(docChat = it.docChat.dropLast(1)) else it })
    }

    /** 2.9.0: clears the EC chat (the read text stays). */
    fun clearDocChat(propertyId: String) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(docChat = emptyList()) else it })
    }

    /** 2.8.0: removes an uploaded document (the file itself is deleted by the caller). */
    fun removeDocument(propertyId: String, docId: String) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(documents = it.documents.filterNot { x -> x.id == docId }, docTexts = it.docTexts.filterNot { x -> x.docId == docId }, updatedAt = nowMillis()) else it })
    }

    /** 2.8.0: saves the last document check. */
    fun saveDocAnalysis(propertyId: String, a: DocAnalysis) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(docAnalysis = a, updatedAt = nowMillis()) else it })
    }

    /** 2.4.0: weights of the property score. */
    fun setScoreWeights(w: ScoreWeights) = update { d -> d.copy(settings = d.settings.copy(scoreWeights = w)) }

    /** 2.3.1: stamp duty and registration fee rates (percent). */
    fun setDutyRates(stampPct: Double, regPct: Double) = update { d ->
        d.copy(settings = d.settings.copy(stampDutyPct = stampPct, registrationFeePct = regPct))
    }

    /** 2.3.0: saves (or removes, null) the FMB sketch laid over the map. */
    fun setSketch(propertyId: String, sketch: SketchOverlay?) = update { d ->
        d.copy(properties = d.properties.map { if (it.id == propertyId) it.copy(sketch = sketch) else it })
    }

    /**
     * 2.3.0: adds a property received through a share link. [mode] ADD_NEW never touches an existing record (a new id
     * when the id is taken); UPDATE_EXISTING refreshes only the listing details of the record with the same id.
     * Returns the id of the saved property.
     */
    fun importShared(sp: com.hark.sothudiary.domain.share.SharedProperty, mode: com.hark.sothudiary.domain.share.ImportMode): String {
        var savedId = sp.p.id
        update { d ->
            val now = nowMillis()
            val existing = d.properties.firstOrNull { it.id == sp.p.id }
            val saved = com.hark.sothudiary.domain.share.mergeShared(existing, sp.p, mode, now, ::newId, sp.by)
            savedId = saved.id
            var brokers = d.brokers
            var withBroker = saved
            // The sender's broker (when included) is added once, matched by phone, and linked to a new property.
            if (sp.bn.isNotBlank() && (mode == com.hark.sothudiary.domain.share.ImportMode.ADD_NEW || saved.brokerId == null)) {
                val digits = sp.bp.filter { it.isDigit() }.takeLast(10)
                val match = brokers.firstOrNull { digits.isNotEmpty() && it.phone.filter { c -> c.isDigit() }.takeLast(10) == digits }
                val b = match ?: Broker(id = newId(), name = sp.bn.take(60), phone = sp.bp.take(20), createdAt = now)
                if (match == null) brokers = brokers + b
                withBroker = saved.copy(brokerId = b.id)
            }
            val props = if (d.properties.any { it.id == withBroker.id }) d.properties.map { if (it.id == withBroker.id) withBroker else it }
            else listOf(withBroker) + d.properties
            val lc = com.hark.sothudiary.domain.share.importedLandCheck(sp.lc, withBroker)
            val mine = d.landChecks.firstOrNull { it.propertyId == withBroker.id }
            val checks = if (lc != null && (mine == null || mine.fetchedAt < lc.fetchedAt)) d.landChecks.filterNot { it.propertyId == withBroker.id } + lc else d.landChecks
            d.copy(properties = props, brokers = brokers, landChecks = checks)
        }
        return savedId
    }

    fun clearLookups() = update { d -> d.copy(rateLookups = emptyList(), onlineRates = emptyList()) }

    fun setMusic(enabled: Boolean = data.settings.bgmEnabled, volume: Float = data.settings.bgmVolume, file: String = data.settings.bgmFile) = update { d ->
        d.copy(settings = d.settings.copy(bgmEnabled = enabled, bgmVolume = volume.coerceIn(0f, 1f), bgmFile = file))
    }

    fun dismissKeyCard() = update { d -> d.copy(settings = d.settings.copy(keyCardDismissed = true)) }

    fun deleteOnlineRate(id: String) = update { d -> d.copy(onlineRates = d.onlineRates.filterNot { it.id == id }) }

    /** 3.0.0: Drive backup account / last backup time / automatic backup switch. */
    fun setDrive(account: String = data.settings.driveAccount, lastBackupAt: Long = data.settings.driveLastBackupAt, auto: Boolean = data.settings.driveAuto) = update { d ->
        d.copy(settings = d.settings.copy(driveAccount = account, driveLastBackupAt = lastBackupAt, driveAuto = auto))
    }

    /** 3.0.0: parses a backup file (Drive or export) without applying it; null when it is not valid. */
    fun parseBackup(text: String): AppData? = runCatching { migrate(json.decodeFromString(AppData.serializer(), text)) }.getOrNull()

    /**
     * 3.0.0: applies a Drive backup. USE_DRIVE replaces the phone data, MERGE joins both by id (newest property wins,
     * see [com.hark.sothudiary.domain.drive.mergeAppData]). This phone's language and Drive connection are kept.
     */
    fun applyDriveBackup(drive: AppData, choice: com.hark.sothudiary.domain.drive.RestoreChoice) = update { d ->
        val base = when (choice) {
            com.hark.sothudiary.domain.drive.RestoreChoice.KEEP_PHONE -> d
            com.hark.sothudiary.domain.drive.RestoreChoice.USE_DRIVE -> drive
            com.hark.sothudiary.domain.drive.RestoreChoice.MERGE -> com.hark.sothudiary.domain.drive.mergeAppData(d, drive)
        }
        base.copy(settings = base.settings.copy(
            lang = d.settings.lang,
            driveAccount = d.settings.driveAccount, driveLastBackupAt = d.settings.driveLastBackupAt, driveAuto = d.settings.driveAuto,
        ))
    }

    fun exportJson(): String = json.encodeToString(AppData.serializer(), data)

    /** Replaces all data with the imported file. Returns false if the text is not a valid backup. */
    fun importJson(text: String): Boolean {
        val parsed = runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull() ?: return false
        update { migrate(parsed).copy(settings = parsed.settings.copy(lang = data.settings.lang)) }
        return true
    }
}
