package com.hark.sothudiary.data

import kotlinx.serialization.Serializable

const val CURRENT_DATA_VERSION = 2

@Serializable
enum class Lang { EN, TA }

@Serializable
enum class PropertyType { LAND, HOUSE, FARMLAND, AGRI_LAND, APARTMENT, PLOT }

@Serializable
enum class AreaUnit { CENTS, ACRES, SQFT }

@Serializable
enum class DealStatus { NEW, CONTACTED, SITE_VISIT, NEGOTIATING, BOUGHT, DROPPED }

@Serializable
enum class SourcePlatform { YOUTUBE, INSTAGRAM, FACEBOOK, WHATSAPP, OTHER, NONE }

@Serializable
enum class DocItem { PATTA, CHITTA, EC, APPROVAL, ROAD_ACCESS, WATER, ELECTRICITY, FMB_SKETCH }

@Serializable
enum class InteractionType { CALL, VISIT, WHATSAPP, MEETING }

@Serializable
data class Property(
    val id: String,
    val title: String,
    val type: PropertyType = PropertyType.LAND,
    val locality: String = "",
    val taluk: String = "",
    val district: String = "",
    /** Full address picked on the map (reverse geocoded), for display only. */
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val size: Double = 0.0,
    val unit: AreaUnit = AreaUnit.CENTS,
    val askingPrice: Long = 0,
    val sourceUrl: String = "",
    val sourcePlatform: SourcePlatform = SourcePlatform.NONE,
    val sourceCaption: String = "",
    val brokerId: String? = null,
    val status: DealStatus = DealStatus.NEW,
    val docs: Set<DocItem> = emptySet(),
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** Photo / poster saved in app storage (file name in the images folder). */
    val imageFile: String = "",
    /** Remote thumbnail (og:image / YouTube thumbnail). */
    val imageUrl: String = "",
    /** User-made folders / collections this property is in. */
    val folderIds: Set<String> = emptySet(),
    val sample: Boolean = false,
)

/** A user-made collection, e.g. "Shortlist" or "Jaihindpuram plots". */
@Serializable
data class Folder(
    val id: String,
    val name: String,
    /** ARGB colour of the folder tile. */
    val color: Long = 0xFF0B5CAD,
    val createdAt: Long = 0,
)

@Serializable
data class Broker(
    val id: String,
    val name: String,
    val phone: String = "",
    val whatsapp: String = "",
    val platform: SourcePlatform = SourcePlatform.NONE,
    val area: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val sample: Boolean = false,
)

@Serializable
data class Interaction(
    val id: String,
    val propertyId: String? = null,
    val brokerId: String? = null,
    val at: Long = 0,
    val type: InteractionType = InteractionType.CALL,
    val quotedPrice: Long? = null,
    val summary: String = "",
    val promises: String = "",
    val followUpAt: Long? = null,
    val followUpDone: Boolean = false,
    val voiceNotes: List<VoiceNote> = emptyList(),
    val sample: Boolean = false,
)

/** An audio clip attached to a call / visit. The file lives in the app's private storage. */
@Serializable
data class VoiceNote(
    val id: String,
    val fileName: String,
    val durationMs: Long = 0,
    val createdAt: Long = 0,
    /** true = attached from an existing file (e.g. the phone's call recording), false = recorded in the app. */
    val imported: Boolean = false,
    val label: String = "",
)

/** Guideline value (government value) for a locality, per cent and/or per sq ft, in rupees. */
@Serializable
data class GuidelineValue(
    val id: String,
    val location: String,
    val perCent: Double? = null,
    val perSqft: Double? = null,
    val note: String = "",
    val sample: Boolean = false,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    val lang: Lang = Lang.EN,
    /** v1 only: sample data was seeded. Kept so old files still parse. */
    val seeded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Daily 9 AM reminder notification for overdue / today's follow-ups (Android). */
    val reminders: Boolean = true,
    /** Language used for speech-to-text; null = follow the app language. */
    val speechLang: Lang? = null,
    /** The welcome / onboarding card was dismissed. */
    val onboarded: Boolean = false,
)

@Serializable
data class AppData(
    val version: Int = CURRENT_DATA_VERSION,
    val properties: List<Property> = emptyList(),
    val brokers: List<Broker> = emptyList(),
    val interactions: List<Interaction> = emptyList(),
    val guidelines: List<GuidelineValue> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val settings: AppSettings = AppSettings(),
)
