package com.hark.sothudiary.data.market

import com.hark.sothudiary.domain.SQFT_PER_CENT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bundled market-rate dataset (see composeApp/data/madurai_rates.json, compiled into MarketDataJson.kt).
 * Every field is optional: the researcher's file may leave any of them out.
 */
@Serializable
data class MarketDataset(
    val city: String = "",
    val generated: String = "",
    val notes: String = "",
    val localities: List<MarketLocality> = emptyList(),
)

@Serializable
data class MarketLocality(
    val name: String = "",
    @SerialName("name_ta") val nameTa: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val rates: MarketRates? = null,
)

@Serializable
data class MarketRates(
    val plot: TypeRate? = null,
    val apartment: TypeRate? = null,
    val house: TypeRate? = null,
    val farmland: TypeRate? = null,
    val guideline: TypeRate? = null,
    /** Extension: portal locality average mixing types – used only as a last fallback. */
    val overall: TypeRate? = null,
)

@Serializable
data class RateRange(
    val min: Double? = null,
    val max: Double? = null,
    val typical: Double? = null,
    val median: Double? = null,
    val note: String? = null,
    val derived: Boolean? = null,
) {
    /** Best single figure: typical, else median, else the middle of min–max. */
    val mid: Double?
        get() = typical ?: median ?: when {
            min != null && max != null -> (min + max) / 2.0
            else -> min ?: max
        }

    fun scaled(f: Double) = RateRange(min?.times(f), max?.times(f), typical?.times(f), median?.times(f), note, derived)
}

@Serializable
data class TypeRate(
    @SerialName("per_sqft") val perSqft: RateRange? = null,
    @SerialName("per_cent") val perCent: RateRange? = null,
    @SerialName("per_acre") val perAcre: RateRange? = null,
    @SerialName("source_name") val sourceName: String = "",
    @SerialName("source_url") val sourceUrl: String = "",
    @SerialName("as_of") val asOf: String = "",
    val basis: String? = null,
    val confidence: String? = null,
    val note: String? = null,
)

/** The three units a rate can be expressed in. */
enum class RateUnit { PER_SQFT, PER_CENT, PER_ACRE }

/** Converts whatever the dataset gave into the requested unit (1 acre = 100 cents, 1 cent = 435.6 sq ft). */
fun TypeRate.rangeIn(unit: RateUnit): RateRange? {
    val sqft = perSqft?.takeIf { it.mid != null }
    val cent = perCent?.takeIf { it.mid != null }
    val acre = perAcre?.takeIf { it.mid != null }
    return when (unit) {
        RateUnit.PER_SQFT -> sqft ?: cent?.scaled(1 / SQFT_PER_CENT) ?: acre?.scaled(1 / (SQFT_PER_CENT * 100))
        // Prefer a per-sqft range (it usually carries min / max) over a single per-cent figure.
        RateUnit.PER_CENT -> when {
            cent != null && (cent.min != null || sqft == null) -> cent
            sqft != null -> sqft.scaled(SQFT_PER_CENT)
            acre != null -> acre.scaled(1 / 100.0)
            else -> null
        }
        RateUnit.PER_ACRE -> acre ?: cent?.scaled(100.0) ?: sqft?.scaled(SQFT_PER_CENT * 100)
    }
}

object MarketData {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** Parsed once, lazily. An unreadable file gives an empty dataset rather than a crash. */
    val dataset: MarketDataset by lazy { parse(MarketDataJson.text()) }

    fun parse(text: String): MarketDataset =
        if (text.isBlank()) MarketDataset()
        else runCatching { json.decodeFromString(MarketDataset.serializer(), text) }.getOrElse { MarketDataset() }
}
