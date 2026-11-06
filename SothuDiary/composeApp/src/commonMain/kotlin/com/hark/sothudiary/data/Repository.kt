package com.hark.sothudiary.data

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

/**
 * Upgrades data written by older versions.
 * v1 -> v2: the clearly labelled SAMPLE records that v1 seeded are removed (the app now starts empty).
 */
fun migrate(d: AppData): AppData {
    val sampleProps = d.properties.filter { it.sample }.map { it.id }.toSet()
    val sampleBrokers = d.brokers.filter { it.sample }.map { it.id }.toSet()
    return d.copy(
        version = CURRENT_DATA_VERSION,
        properties = d.properties.filterNot { it.sample }
            .map { if (it.brokerId != null && it.brokerId in sampleBrokers) it.copy(brokerId = null) else it },
        brokers = d.brokers.filterNot { it.sample },
        interactions = d.interactions.filterNot { it.sample || (it.propertyId != null && it.propertyId in sampleProps) }
            .map { if (it.brokerId != null && it.brokerId in sampleBrokers) it.copy(brokerId = null) else it },
        guidelines = d.guidelines.filterNot { it.sample },
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
        val next = transform(data)
        data = next
        persist(next)
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
    fun setLang(lang: Lang) = update { d -> d.copy(settings = d.settings.copy(lang = lang)) }

    fun setTheme(mode: ThemeMode) = update { d -> d.copy(settings = d.settings.copy(theme = mode)) }

    fun setReminders(on: Boolean) = update { d -> d.copy(settings = d.settings.copy(reminders = on)) }

    fun setSpeechLang(lang: Lang?) = update { d -> d.copy(settings = d.settings.copy(speechLang = lang)) }

    fun setOnboarded() = update { d -> d.copy(settings = d.settings.copy(onboarded = true)) }

    fun exportJson(): String = json.encodeToString(AppData.serializer(), data)

    /** Replaces all data with the imported file. Returns false if the text is not a valid backup. */
    fun importJson(text: String): Boolean {
        val parsed = runCatching { json.decodeFromString(AppData.serializer(), text) }.getOrNull() ?: return false
        update { migrate(parsed).copy(settings = parsed.settings.copy(lang = data.settings.lang)) }
        return true
    }
}
