package com.hark.sothudiary.data

import kotlinx.serialization.Serializable

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
    val district: String = "Madurai",
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
    val sample: Boolean = false,
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
    val sample: Boolean = false,
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
data class AppSettings(
    val lang: Lang = Lang.EN,
    val seeded: Boolean = false,
)

@Serializable
data class AppData(
    val version: Int = 1,
    val properties: List<Property> = emptyList(),
    val brokers: List<Broker> = emptyList(),
    val interactions: List<Interaction> = emptyList(),
    val guidelines: List<GuidelineValue> = emptyList(),
    val settings: AppSettings = AppSettings(),
)
