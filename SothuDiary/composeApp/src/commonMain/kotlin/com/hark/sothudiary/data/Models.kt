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
    /** v2.1: comments read from the source post ("User reviews"). */
    val reviews: List<Review> = emptyList(),
    val reviewsFetchedAt: Long = 0,
    /** v2.1: claims found in the post text (keys of [com.hark.sothudiary.domain.PostClaim]) – to be verified. */
    val postClaims: Set<String> = emptySet(),
    /** 2.1.1: extra photos saved from the post (e.g. the second carousel slide), file names in the images folder. */
    val photos: List<String> = emptyList(),
)

/** A comment on the source post (YouTube / Instagram / Facebook), shown as a "user review". */
@Serializable
data class Review(
    val author: String = "",
    val text: String = "",
    val likes: Long? = null,
    /** As the site shows it, e.g. "2 weeks ago" or "2026-10-12". */
    val date: String = "",
    val source: SourcePlatform = SourcePlatform.OTHER,
)

/** Kind of an online rate: a listing's asking price or a government guideline value. */
@Serializable
enum class RateKind { ASKING, GUIDELINE }

/**
 * A market rate found online ("Find nearby rates online", Gemini + Google Search) and saved locally,
 * so later price checks reuse it. [group]: 0 plot / land, 1 farmland / agri, 2 house, 3 apartment.
 * Rates are rupees per [unit] ("sqft", "cent" or "acre").
 */
@Serializable
data class OnlineRate(
    val id: String,
    val place: String,
    val district: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val group: Int,
    val kind: RateKind = RateKind.ASKING,
    val unit: String = "cent",
    val min: Double? = null,
    val max: Double? = null,
    val typical: Double? = null,
    val sourceName: String = "",
    val sourceUrl: String = "",
    /** Date of the listing / guideline as the source gives it. */
    val asOf: String = "",
    val fetchedAt: Long = 0,
    val note: String = "",
    /** Key of the live lookup (location + type) this rate came from; rates are replaced when it is refreshed. */
    val lookupKey: String = "",
    /** Distance from the looked-up point (km) when it was found. */
    val distanceKm: Double? = null,
)

/**
 * A live price lookup done for a location + property type (Gemini grounded search, cached locally).
 * It is a cache, not a dataset: it is reused for [com.hark.sothudiary.domain.pricing.PriceRules.CACHE_DAYS] days.
 */
@Serializable
data class RateLookup(
    val key: String,
    val lat: Double,
    val lng: Double,
    val group: Int,
    /** Reverse-geocoded place, e.g. "Panpoli, Shenkottai taluk, Tenkasi district". */
    val place: String = "",
    val fetchedAt: Long = 0,
    /** Comparables kept (within 10 km). */
    val found: Int = 0,
    /** Comparables the model returned that were dropped (beyond 10 km, unplaceable, no rate). */
    val skipped: Int = 0,
    val model: String = "",
    val error: String? = null,
    val sources: List<String> = emptyList(),
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
    /** Optional YouTube Data API v3 key – used for comments when set. */
    val youtubeApiKey: String = "",
    /** Optional Gemini API key (Google AI Studio) – "Find nearby rates online". */
    val geminiApiKey: String = "",
    val geminiModel: String = "auto",
    /** The "set up live price check" card on Home was dismissed. */
    val keyCardDismissed: Boolean = false,
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
    /** v2.1: rates found online and saved for later price checks. */
    val onlineRates: List<OnlineRate> = emptyList(),
    /** v2.1: live price lookups (cache, per location + type). */
    val rateLookups: List<RateLookup> = emptyList(),
)
