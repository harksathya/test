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
    /** 2.2.0: plot corners the user tapped on the map (land check). Empty = only the pin + stated size are known. */
    val boundary: List<GeoPt> = emptyList(),
    /** 2.3.0: survey number / sub-division as the user found it (TNGIS / FMB / patta), e.g. "123/4B". */
    val surveyNumber: String = "",
    /** 2.3.0: an FMB sketch image laid over the map to trace the corners (Android). */
    val sketch: SketchOverlay? = null,
    /** 2.3.0: when this property was imported from a shared link (epoch ms), else 0. */
    val importedAt: Long = 0,
    /** 2.3.1: registration details – TNREGINET place (district / SRO / village / street) and the official guideline value. */
    val registry: RegistryInfo = RegistryInfo(),
)

/** 2.3.1: unit a guideline value is quoted in (TNREGINET uses ₹ per sq ft / sq m for streets, per hectare for survey numbers). */
@Serializable
enum class GvUnit { SQFT, SQM, CENT, ACRE, HECTARE }

/**
 * 2.3.1: a guideline value for one property, always with where it came from and when. Never invented: it is what the user
 * read on tnreginet.gov.in (in-app page, [METHOD_WEBVIEW]) or typed themselves ([METHOD_MANUAL]).
 */
@Serializable
data class OfficialGuideline(
    /** Rupees per [unit]. */
    val value: Double,
    val unit: GvUnit = GvUnit.SQFT,
    /** "tnreginet.gov.in" for a value read on the portal; blank when typed by the user. */
    val source: String = "",
    /** The page the value was read on. */
    val sourceUrl: String = "",
    /** When it was saved (epoch ms). */
    val capturedAt: Long = 0,
    /** "In force from" date as the portal shows it, e.g. "01-07-2024" (optional). */
    val effectiveFrom: String = "",
    val method: String = METHOD_MANUAL,
    /** The row / text the value was read from. */
    val rawText: String = "",
) {
    companion object {
        const val METHOD_WEBVIEW = "webview"
        const val METHOD_MANUAL = "manual"
    }
}

/** 2.3.1: what the registration office needs for this property, entered by the user or filled from the map address. */
@Serializable
data class RegistryInfo(
    val district: String = "",
    /** Sub-Registrar Office (சார்பதிவாளர் அலுவலகம்). */
    val sro: String = "",
    val village: String = "",
    val street: String = "",
    val guideline: OfficialGuideline? = null,
    /** Land area the guideline applies to when it differs from the stated size (house plot, UDS of a flat), sq ft. */
    val landAreaSqft: Double? = null,
    /** Every buyer is a woman (registration fee concession up to ₹10 lakh, G.O. Ms. No.77 / 2025). */
    val womenBuyers: Boolean = false,
)

/**
 * 2.3.0: an FMB sketch (image in app storage) placed on the map: centred at [lat]/[lng], [widthM] metres wide,
 * turned [rotationDeg] clockwise, drawn with [alpha] opacity.
 */
@Serializable
data class SketchOverlay(
    val file: String,
    val lat: Double,
    val lng: Double,
    val widthM: Double = 100.0,
    val rotationDeg: Double = 0.0,
    val alpha: Float = 0.6f,
    /** Height / width of the image (to keep its proportions on the map). */
    val aspect: Double = 1.0,
)

/** 2.3.0: one web search result (free keyless search) – title, page and the text shown under it. */
@Serializable
data class WebSnippet(val title: String = "", val url: String = "", val snippet: String = "")

/** 2.3.0: the ISRO Bhuvan land-use / land-cover class at a point (SIS-DP 1:10,000 map). */
@Serializable
data class LulcHit(
    val lat: Double,
    val lng: Double,
    val code: String = "",
    val level1: String = "",
    val level2: String = "",
    val level3: String = "",
)

/** A latitude / longitude pair that can be saved (plot corners, water outlines). */
@Serializable
data class GeoPt(val lat: Double, val lng: Double)

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
    /** v2.1.2: when the API asked us to retry after a quota error (epoch ms), else 0. */
    val retryAt: Long = 0,
    /** 2.3.0: the rates came from our own free web search read by Gemini (not Google Search grounding). */
    val viaSearch: Boolean = false,
    /** 2.3.0: web search results kept when Gemini could not read them (shown as they are, with their pages). */
    val snippets: List<WebSnippet> = emptyList(),
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
    /** v2.1.2: soft background music while the app is open (Android). On by default, at low volume. */
    val bgmEnabled: Boolean = true,
    /** Background music volume 0..1 (default about 12%). */
    val bgmVolume: Float = 0.12f,
    /** The user's own song, copied into app storage; blank = the bundled default track. */
    val bgmFile: String = "",
    /** 2.3.1: Tamil Nadu sale deed rates (editable; see domain/registry/StampDuty.kt for the source). */
    val stampDutyPct: Double = 7.0,
    val registrationFeePct: Double = 2.0,
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
    /** 2.2.0: land checks (map data layers + AI), one per property – a cache like [rateLookups]. */
    val landChecks: List<LandCheck> = emptyList(),
)

/** One mapped thing near the plot found by the land check (OpenStreetMap), with its distance from the plot. */
@Serializable
data class LandFeature(
    /** water, major_road, road, proposed_road, rail, school, hospital, bus, market, power, water_supply */
    val category: String,
    /** The OSM tag value, e.g. "reservoir", "primary", "substation". */
    val kind: String = "",
    val name: String = "",
    val distanceM: Double,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** A road the AI search found announced / proposed / under construction near the plot. */
@Serializable
data class AiRoad(
    val name: String = "",
    val status: String = "",
    val distanceKm: Double? = null,
    val url: String = "",
    val date: String = "",
)

/** What the grounded AI search (Gemini + Google Search) found for the plot – an estimate, always with sources. */
@Serializable
data class LandAi(
    /** residential / commercial / industrial / agricultural / institutional / mixed / unknown */
    val zoning: String = "",
    val zoningPlan: String = "",
    val zoningNote: String = "",
    val zoningUrl: String = "",
    val roads: List<AiRoad> = emptyList(),
    /** high / moderate / low / unknown */
    val growth: String = "",
    val growthReason: String = "",
    val growthUrl: String = "",
)

/**
 * 2.2.0 "Land check / நில ஆய்வு" result for a property: free map data layers (OpenStreetMap via Overpass, elevation from
 * Open-Meteo) plus an optional grounded AI search. Saved so it is not fetched again for
 * [com.hark.sothudiary.domain.land.LandRules.CACHE_DAYS] days. Ratings are recomputed from these facts when shown.
 */
@Serializable
data class LandCheck(
    val propertyId: String,
    /** Pin + boundary + size the check was made for; a change makes it stale. */
    val key: String,
    val lat: Double,
    val lng: Double,
    val fetchedAt: Long = 0,
    /** Plot outline used (drawn corners, or a circle of the stated area around the pin). */
    val plot: List<GeoPt> = emptyList(),
    /** True when [plot] is the user's drawn corners. */
    val drawn: Boolean = false,
    val plotAreaSqm: Double = 0.0,
    // ---- OpenStreetMap ----
    val osmOk: Boolean = false,
    val osmError: String? = null,
    val features: List<LandFeature> = emptyList(),
    val bufferM: Double = 15.0,
    /** % of the plot on a water body or within [bufferM] of one; null without map data. */
    val bufferPct: Double? = null,
    /** % of the plot on the water body itself. */
    val waterPct: Double? = null,
    /** Closest the plot comes to mapped water (m), null when none within the search radius. */
    val nearestWaterM: Double? = null,
    val nearestWaterName: String = "",
    val nearestWaterKind: String = "",
    /** Outlines of the nearest water bodies, thinned, for the map. */
    val waterOutlines: List<List<GeoPt>> = emptyList(),
    /** OSM landuse / natural tags of the areas the pin is in, e.g. "landuse=farmland". */
    val landuse: List<String> = emptyList(),
    // ---- Elevation (Open-Meteo, ~90 m terrain model) ----
    val elevationM: Double? = null,
    /** Median elevation of 8 points about 500 m around the pin. */
    val surroundingElevationM: Double? = null,
    // ---- AI (Gemini + Google Search) ----
    val ai: LandAi? = null,
    val aiError: String? = null,
    val aiRetryAt: Long = 0,
    val aiModel: String = "",
    val aiFetchedAt: Long = 0,
    val sources: List<String> = emptyList(),
    // ---- 2.3.0 ----
    /** Map data layers that could not be loaded this time ([com.hark.sothudiary.domain.land.OsmLayer]). */
    val osmFailedLayers: List<String> = emptyList(),
    /** Layers that failed now but were kept from the earlier check of the same spot. */
    val osmKeptLayers: List<String> = emptyList(),
    /** 0 = made by 2.2.x (no railway / protected-area layers), 2 = 2.3.0 layers. */
    val layersVersion: Int = 0,
    /** ISRO Bhuvan land use at the pin (first) and the plot corners. */
    val lulc: List<LulcHit> = emptyList(),
    val lulcError: String? = null,
    /** The AI part came from our own web search read by Gemini (Google Search grounding was not available). */
    val aiViaSearch: Boolean = false,
    /** Web search results kept when Gemini could not read them. */
    val webSnippets: List<WebSnippet> = emptyList(),
)
