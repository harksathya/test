package com.hark.sothudiary.domain.social

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Small helpers for walking loosely-structured JSON (YouTube / Instagram page data). */
internal val looseJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJsonOrNull(text: String?): JsonElement? =
    text?.let { runCatching { looseJson.parseToJsonElement(it) }.getOrNull() }

internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
internal operator fun JsonElement?.get(i: Int): JsonElement? = (this as? JsonArray)?.getOrNull(i)

internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
internal fun JsonElement?.num(): Long? = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
internal fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()

/** Follows a path of object keys / array indexes. */
internal fun JsonElement?.at(vararg path: Any): JsonElement? {
    var cur = this
    for (p in path) {
        cur = when (p) {
            is String -> cur[p]
            is Int -> cur[p]
            else -> null
        } ?: return null
    }
    return cur
}

/** Depth-first search for every object that has [key]; returns the values. */
internal fun JsonElement?.findAll(key: String, out: MutableList<JsonElement> = mutableListOf(), limit: Int = 200): List<JsonElement> {
    if (this == null || out.size >= limit) return out
    when (this) {
        is JsonObject -> {
            this[key]?.let { out += it }
            for (v in values) v.findAll(key, out, limit)
        }
        is JsonArray -> for (v in this) v.findAll(key, out, limit)
        else -> {}
    }
    return out
}

internal fun JsonElement?.findFirst(key: String): JsonElement? = findAll(key, limit = 1).firstOrNull()

/** YouTube "runs" / "simpleText" text objects. */
internal fun JsonElement?.ytText(): String? =
    this["simpleText"].str() ?: this["content"].str()
        ?: this["runs"].arr().mapNotNull { it["text"].str() }.joinToString("").ifBlank { null }

/** Decodes a JavaScript / JSON string literal body (the part between the quotes). */
internal fun decodeJsString(body: String): String? =
    runCatching { (looseJson.parseToJsonElement("\"" + body + "\"") as JsonPrimitive).content }.getOrNull()

/** Reads the JSON string literal that starts right after [marker] (e.g. `"contextJSON":"`). */
internal fun jsStringAfter(html: String, marker: String): String? {
    val i = html.indexOf(marker)
    if (i < 0) return null
    var j = i + marker.length
    val sb = StringBuilder()
    while (j < html.length) {
        val c = html[j]
        if (c == '\\' && j + 1 < html.length) { sb.append(c).append(html[j + 1]); j += 2; continue }
        if (c == '"') break
        sb.append(c); j++
    }
    return decodeJsString(sb.toString())
}

/** Extracts a balanced {...} JSON object starting at the first '{' at or after [from]. */
internal fun balancedJson(text: String, from: Int): String? {
    val start = text.indexOf('{', from)
    if (start < 0) return null
    var depth = 0
    var inStr = false
    var i = start
    while (i < text.length) {
        val c = text[i]
        if (inStr) {
            if (c == '\\') i++ else if (c == '"') inStr = false
        } else when (c) {
            '"' -> inStr = true
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
        }
        i++
    }
    return null
}
