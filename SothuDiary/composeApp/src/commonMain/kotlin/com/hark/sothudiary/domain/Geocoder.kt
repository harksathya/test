package com.hark.sothudiary.domain

import com.hark.sothudiary.APP_USER_AGENT
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.httpGet
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A place found by search or reverse geocoding. */
data class GeoPlace(
    val displayName: String,
    val lat: Double,
    val lng: Double,
    val locality: String = "",
    val taluk: String = "",
    val district: String = "",
    val state: String = "",
)

/**
 * OpenStreetMap Nominatim client (https://nominatim.org/release-docs/latest/api/).
 * Usage policy: an identifying User-Agent and at most one request per second – enforced here.
 */
object Geocoder {
    private const val BASE = "https://nominatim.openstreetmap.org"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()
    private var lastRequest = 0L

    private suspend fun get(url: String): String? = mutex.withLock {
        val wait = 1100 - (nowMillis() - lastRequest)
        if (wait > 0) delay(wait)
        lastRequest = nowMillis()
        httpGet(url, APP_USER_AGENT)
    }

    /** Searches places in India, e.g. "Jaihindpuram Madurai". Returns an empty list on errors. */
    suspend fun search(query: String, limit: Int = 8): List<GeoPlace> {
        val q = query.trim()
        if (q.length < 3) return emptyList()
        val url = "$BASE/search?format=json&addressdetails=1&countrycodes=in&accept-language=en&limit=$limit&q=" + urlEncode(q)
        val body = get(url) ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.let(::toPlace) }
    }

    /** Area / taluk / district for a point, or null when offline. */
    suspend fun reverse(lat: Double, lng: Double): GeoPlace? {
        val url = "$BASE/reverse?format=json&addressdetails=1&zoom=16&accept-language=en&lat=$lat&lon=$lng"
        val body = get(url) ?: return null
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return toPlace(obj)
    }

    private fun JsonElement?.str(): String? = runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun toPlace(o: JsonObject): GeoPlace? {
        val lat = o["lat"].str()?.toDoubleOrNull() ?: return null
        val lng = o["lon"].str()?.toDoubleOrNull() ?: return null
        val a = o["address"] as? JsonObject
        fun f(vararg keys: String): String = keys.firstNotNullOfOrNull { a?.get(it).str() } ?: ""
        val locality = f("suburb", "neighbourhood", "quarter", "residential", "village", "hamlet", "town", "city_district", "city")
        val taluk = f("county", "municipality", "subdistrict", "town")
        val district = f("state_district", "district", "city").removeSuffix(" District").removeSuffix(" district")
        return GeoPlace(
            displayName = o["display_name"].str() ?: listOf(locality, district).filter { it.isNotBlank() }.joinToString(", "),
            lat = lat, lng = lng,
            locality = locality,
            taluk = taluk.takeIf { it != locality } ?: "",
            district = district,
            state = f("state"),
        )
    }
}

/** Percent-encodes a query parameter (UTF-8). */
fun urlEncode(s: String): String {
    val sb = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') sb.append(ch)
        else if (ch == ' ') sb.append('+')
        else {
            sb.append('%')
            sb.append("0123456789ABCDEF"[c shr 4])
            sb.append("0123456789ABCDEF"[c and 0xF])
        }
    }
    return sb.toString()
}

/** Madurai city centre: only used to centre an empty map, never saved as a property location. */
val DEFAULT_MAP_CENTER = com.hark.sothudiary.LatLng(9.9252, 78.1198)
