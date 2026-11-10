package com.hark.sothudiary.domain.docs

import com.hark.sothudiary.domain.ai.AUTO_MODEL
import com.hark.sothudiary.domain.ai.ApiStyle
import com.hark.sothudiary.domain.ai.LlmConfig
import com.hark.sothudiary.domain.ai.LlmProviders
import com.hark.sothudiary.domain.ai.listModels
import com.hark.sothudiary.domain.ai.parseChatText
import com.hark.sothudiary.domain.ai.rankChatModels
import com.hark.sothudiary.domain.pricing.parseGeminiError
import com.hark.sothudiary.domain.pricing.pickModels
import com.hark.sothudiary.httpRequest
import com.hark.sothudiary.domain.urlEncode
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** One file sent to the AI. */
class DocFile(val mime: String, val bytes: ByteArray, val name: String = "document")

/** Outcome of [readDocumentsWithAi]. */
sealed class DocAiResult {
    data class Ok(val text: String, val model: String) : DocAiResult()
    /** [message] is a short reason for the Status line (bad key, quota, model cannot read images/PDF …). */
    data class Failed(val message: String, val model: String = "") : DocAiResult()
}

@OptIn(ExperimentalEncodingApi::class)
private fun b64(bytes: ByteArray): String = Base64.encode(bytes)

/**
 * Request body with the prompt and the files, in the provider's own format: Gemini inline_data (images and PDF),
 * OpenAI-compatible image_url / file parts, Anthropic image / document blocks.
 */
fun multimodalBody(style: ApiStyle, model: String, prompt: String, files: List<DocFile>): String = when (style) {
    ApiStyle.GEMINI -> buildJsonObject {
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("parts", buildJsonArray {
                    files.forEach { f -> add(buildJsonObject { put("inline_data", buildJsonObject { put("mime_type", f.mime); put("data", b64(f.bytes)) }) }) }
                    add(buildJsonObject { put("text", prompt) })
                })
            })
        })
        put("generationConfig", buildJsonObject { put("temperature", 0.0); put("responseMimeType", "application/json") })
    }
    ApiStyle.ANTHROPIC -> buildJsonObject {
        put("model", model)
        put("max_tokens", 8192)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    files.forEach { f ->
                        add(buildJsonObject {
                            put("type", if (f.mime == "application/pdf") "document" else "image")
                            put("source", buildJsonObject { put("type", "base64"); put("media_type", f.mime); put("data", b64(f.bytes)) })
                        })
                    }
                    add(buildJsonObject { put("type", "text"); put("text", prompt) })
                })
            })
        })
    }
    ApiStyle.OPENAI -> buildJsonObject {
        put("model", model)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", prompt) })
                    files.forEach { f ->
                        val url = "data:${f.mime};base64," + b64(f.bytes)
                        add(
                            if (f.mime == "application/pdf") buildJsonObject {
                                put("type", "file")
                                put("file", buildJsonObject { put("filename", f.name.ifBlank { "document.pdf" }); put("file_data", url) })
                            } else buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", url) }) },
                        )
                    }
                })
            })
        })
    }
}.toString()

/** Models to try, in order (at most 3): the user's chosen one, else the provider's list ranked like the other AI calls. */
suspend fun docModels(llm: LlmConfig): List<String> {
    if (llm.model != AUTO_MODEL && llm.model.isNotBlank()) return listOf(llm.model)
    val listed = listModels(llm.provider, llm.apiKey) ?: return emptyList()
    val ids = if (llm.provider.style == ApiStyle.GEMINI) pickModels(listed.map { it.id }) else rankChatModels(llm.provider, listed)
    return ids.take(3)
}

/** Sends the files and [prompt] to the chosen provider. Tries up to three models; stops on a bad key. */
suspend fun readDocumentsWithAi(llm: LlmConfig, prompt: String, files: List<DocFile>, timeoutMs: Int = 150_000): DocAiResult {
    if (!llm.hasKey) return DocAiResult.Failed("no AI key")
    if (files.isEmpty()) return DocAiResult.Failed("no documents")
    val total = files.sumOf { it.bytes.size }
    if (total > DocRules.MAX_TOTAL_BYTES) return DocAiResult.Failed("files too large (${total / 1_000_000} MB, max ${DocRules.MAX_TOTAL_BYTES / 1_000_000} MB)")
    val models = docModels(llm)
    if (models.isEmpty()) return DocAiResult.Failed("no model listed for this key (or no internet)")
    var last: DocAiResult.Failed = DocAiResult.Failed("failed", models.first())
    for (m in models) {
        val p = llm.provider
        val url = when (p.style) {
            ApiStyle.GEMINI -> "${LlmProviders.GEMINI.baseUrl}/models/$m:generateContent?key=" + urlEncode(llm.apiKey)
            ApiStyle.ANTHROPIC -> p.baseUrl + "/messages"
            ApiStyle.OPENAI -> p.baseUrl + "/chat/completions"
        }
        val headers = buildMap {
            put("Content-Type", "application/json")
            when (p.style) {
                ApiStyle.GEMINI -> {}
                ApiStyle.OPENAI -> {
                    put("Authorization", "Bearer " + llm.apiKey.trim())
                    if (p.id == LlmProviders.OPENROUTER.id) { put("X-Title", "Sothu Diary"); put("HTTP-Referer", "https://github.com/harksathya/test") }
                }
                ApiStyle.ANTHROPIC -> { put("x-api-key", llm.apiKey.trim()); put("anthropic-version", "2023-06-01") }
            }
        }
        val r = httpRequest(url, method = "POST", headers = headers, body = multimodalBody(p.style, m, prompt, files), timeoutMs = timeoutMs)
            ?: return DocAiResult.Failed("no internet / no answer", m)
        if (r.ok) {
            val text = parseChatText(p.style, r.body)
            if (text.isNotBlank()) return DocAiResult.Ok(text, m)
            last = DocAiResult.Failed("empty answer", m)
            continue
        }
        val e = parseGeminiError(r.code, r.body)
        last = DocAiResult.Failed("HTTP ${r.code}: " + e.message.take(160), m)
        // A refused key fails the same way on every model.
        if (r.code == 401 || r.code == 403) break
    }
    return last
}

