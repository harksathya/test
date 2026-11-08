package com.hark.sothudiary.domain.ai

import com.hark.sothudiary.data.AppSettings
import com.hark.sothudiary.data.normalizeGeminiModel
import com.hark.sothudiary.domain.pricing.GeminiError
import com.hark.sothudiary.domain.pricing.parseGeminiError
import com.hark.sothudiary.domain.social.arr
import com.hark.sothudiary.domain.social.at
import com.hark.sothudiary.domain.social.get
import com.hark.sothudiary.domain.social.parseJsonOrNull
import com.hark.sothudiary.domain.social.str
import com.hark.sothudiary.httpRequest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 2.5.0 (issue #1): the app talks to any of several LLM providers with the user's own key, chosen at setup.
 *
 * How a provider is called is its [ApiStyle]: Google's Gemini API, the OpenAI-compatible Chat Completions API (OpenAI,
 * OpenRouter, xAI, Groq, Mistral, DeepSeek … – most providers speak it) or Anthropic's Messages API. Adding a provider
 * is one more [LlmProvider] in [LlmProviders.all]. Model names are never hard-coded: every provider's model list is
 * read from its own API ([listModels]) and ranked ([rankChatModels]); the user can also pick one in Settings.
 */
enum class ApiStyle { GEMINI, OPENAI, ANTHROPIC }

data class LlmProvider(
    /** Stable id saved in the settings. */
    val id: String,
    val name: String,
    val style: ApiStyle,
    /** API root without a trailing slash (".../v1"). */
    val baseUrl: String,
    /** Where the user creates a key. */
    val keyUrl: String,
    /** How the key usually starts (shown as a hint; never enforced). */
    val keyPrefix: String = "",
    /** Gemini only: Google Search grounding is built in; the others read the app's own free web search results. */
    val nativeSearch: Boolean = false,
    /** A free tier exists (shown in the picker). */
    val freeTier: Boolean = false,
)

object LlmProviders {
    val GEMINI = LlmProvider("gemini", "Google Gemini", ApiStyle.GEMINI, "https://generativelanguage.googleapis.com/v1beta",
        "https://aistudio.google.com/apikey", "AIza", nativeSearch = true, freeTier = true)
    val OPENAI = LlmProvider("openai", "OpenAI (ChatGPT)", ApiStyle.OPENAI, "https://api.openai.com/v1",
        "https://platform.openai.com/api-keys", "sk-")
    val ANTHROPIC = LlmProvider("anthropic", "Anthropic (Claude)", ApiStyle.ANTHROPIC, "https://api.anthropic.com/v1",
        "https://console.anthropic.com/settings/keys", "sk-ant-")
    val OPENROUTER = LlmProvider("openrouter", "OpenRouter", ApiStyle.OPENAI, "https://openrouter.ai/api/v1",
        "https://openrouter.ai/keys", "sk-or-", freeTier = true)
    val XAI = LlmProvider("xai", "xAI (Grok)", ApiStyle.OPENAI, "https://api.x.ai/v1", "https://console.x.ai", "xai-")
    val GROQ = LlmProvider("groq", "Groq", ApiStyle.OPENAI, "https://api.groq.com/openai/v1",
        "https://console.groq.com/keys", "gsk_", freeTier = true)
    val MISTRAL = LlmProvider("mistral", "Mistral AI", ApiStyle.OPENAI, "https://api.mistral.ai/v1",
        "https://console.mistral.ai/api-keys", freeTier = true)
    val DEEPSEEK = LlmProvider("deepseek", "DeepSeek", ApiStyle.OPENAI, "https://api.deepseek.com",
        "https://platform.deepseek.com/api_keys", "sk-")

    /** In the order shown in the picker. Add new providers here. */
    val all: List<LlmProvider> = listOf(GEMINI, OPENAI, ANTHROPIC, OPENROUTER, XAI, GROQ, MISTRAL, DEEPSEEK)

    fun byId(id: String?): LlmProvider? = all.firstOrNull { it.id == id?.trim()?.lowercase() }

    /** Guesses the provider from how a pasted key starts (null when it can't tell). */
    fun guessFromKey(key: String): LlmProvider? {
        val k = key.trim()
        if (k.isEmpty()) return null
        // Longer prefixes first: "sk-ant-" and "sk-or-" before OpenAI / DeepSeek's "sk-" (ambiguous, so not guessed).
        return all.filter { it.keyPrefix.length > 3 }.sortedByDescending { it.keyPrefix.length }.firstOrNull { k.startsWith(it.keyPrefix) }
    }
}

/** The provider, key and model the app uses for every AI call. */
data class LlmConfig(val provider: LlmProvider, val apiKey: String, val model: String = AUTO_MODEL) {
    val hasKey: Boolean get() = apiKey.isNotBlank()
}

const val AUTO_MODEL = "auto"

/** "auto" for blank / "Auto …" / Tamil auto label; otherwise the trimmed id (Gemini's own rules for Gemini). */
fun normalizeModel(provider: LlmProvider, raw: String): String {
    if (provider.style == ApiStyle.GEMINI) return normalizeGeminiModel(raw)
    val m = raw.trim()
    if (m.isBlank() || m.lowercase().startsWith("auto") || m.startsWith("தானியங்கி")) return AUTO_MODEL
    return m
}

/** The provider chosen at setup. Older data (Gemini only) has no provider saved: Gemini. */
fun AppSettings.llmProvider(): LlmProvider = LlmProviders.byId(llmProvider) ?: LlmProviders.GEMINI

fun AppSettings.keyFor(p: LlmProvider): String = if (p.style == ApiStyle.GEMINI) geminiApiKey else llmKeys[p.id].orEmpty()

fun AppSettings.modelFor(p: LlmProvider): String =
    if (p.style == ApiStyle.GEMINI) normalizeGeminiModel(geminiModel) else normalizeModel(p, llmModels[p.id].orEmpty())

/** What every AI call in the app uses. */
fun AppSettings.llm(): LlmConfig = llmProvider().let { LlmConfig(it, keyFor(it).trim(), modelFor(it)) }

/** Any one provider's key is enough (a Gemini key is not required). */
val AppSettings.hasAiKey: Boolean get() = llm().hasKey

/** Settings after saving [key] / [model] for [p] and making it the active provider. */
fun AppSettings.withLlm(p: LlmProvider, key: String, model: String): AppSettings {
    val k = key.trim()
    val m = normalizeModel(p, model)
    return if (p.style == ApiStyle.GEMINI) copy(llmProvider = p.id, geminiApiKey = k, geminiModel = m)
    else copy(llmProvider = p.id, llmKeys = llmKeys + (p.id to k), llmModels = llmModels + (p.id to m))
}

// ---------------- models ----------------

/** A model the provider lists; [created] orders newest first (epoch seconds, or yyyymmdd for ISO dates). */
data class ModelInfo(val id: String, val created: Long = 0, val textOut: Boolean = true)

/** Model-list parsing for OpenAI-compatible and Anthropic answers ({"data":[…]}) and Gemini ({"models":[…]}). */
fun parseModelList(style: ApiStyle, body: String?): List<ModelInfo> {
    val root = parseJsonOrNull(body ?: "")
    return when (style) {
        ApiStyle.GEMINI -> root.at("models").arr()
            .filter { m -> m.at("supportedGenerationMethods").arr().any { it.str() == "generateContent" } }
            .mapNotNull { m -> m.at("name").str()?.removePrefix("models/")?.let { ModelInfo(it) } }
        ApiStyle.OPENAI, ApiStyle.ANTHROPIC -> root.at("data").arr().mapNotNull { m ->
            val id = m["id"].str() ?: return@mapNotNull null
            val createdNum = (m["created"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            val createdIso = m["created_at"].str()?.let { iso -> iso.take(10).replace("-", "").toLongOrNull() }
            // OpenRouter: architecture.output_modalities; Mistral: capabilities.completion_chat.
            val outs = m.at("architecture", "output_modalities").arr().mapNotNull { it.str() }
            val chat = (m.at("capabilities", "completion_chat") as? JsonPrimitive)?.contentOrNull
            val textOut = (outs.isEmpty() || "text" in outs) && chat != "false"
            ModelInfo(id, createdNum ?: createdIso ?: 0L, textOut)
        }
    }
}

private val NON_CHAT = listOf(
    "embed", "tts", "whisper", "transcribe", "audio", "realtime", "image", "dall-e", "moderation", "guard", "search",
    "computer", "babbage", "davinci", "codex", "ocr", "rerank", "speech", "sora", "veo", "imagen", "aqa", "learnlm",
    "vision", "deep-research", "robotics", "native", "live",
)

/** 2.6.0: "gpt-5-pro", "o3-pro", "claude-opus-4-1" … */
internal fun isPremium(id: String): Boolean {
    val l = id.lowercase()
    return Regex("""(^|[-_/.])pro($|[-_.])""").containsMatchIn(l) || "opus" in l
}

private fun isPreview(id: String) = listOf("preview", "exp", "beta", "alpha").any { it in id.lowercase() }

/**
 * Order to try a provider's models in, for "Auto": chat models only (no embedding / speech / image / moderation),
 * a router the provider offers itself (an id ending in "/auto", e.g. on OpenRouter) first, then stable before preview,
 * newest first. Gemini keeps its own free-tier order (stable Flash first, see pricing.pickModels).
 */
fun rankChatModels(provider: LlmProvider, models: List<ModelInfo>): List<String> {
    if (provider.style == ApiStyle.GEMINI) return com.hark.sothudiary.domain.pricing.pickModels(models.map { it.id })
    val usable = models.filter { m -> m.textOut && NON_CHAT.none { it in m.id.lowercase() } }
    return usable.sortedWith(compareBy<ModelInfo>(
        { if (it.id.lowercase().endsWith("/auto")) 0 else 1 },
        { if (isPreview(it.id)) 1 else 0 },
        // 2.6.0: premium tiers (OpenAI "…-pro" – answered only by the Responses API –, Claude Opus) after the normal
        // models, so "Auto" does not start with the slowest / most expensive model of a key.
        { if (isPremium(it.id)) 1 else 0 },
        { -it.created },
        { it.id.length },
    )).map { it.id }.distinct()
}

private fun authHeaders(p: LlmProvider, key: String): Map<String, String> = when (p.style) {
    ApiStyle.GEMINI -> emptyMap()
    ApiStyle.OPENAI -> buildMap {
        put("Authorization", "Bearer " + key.trim())
        if (p.id == LlmProviders.OPENROUTER.id) { put("X-Title", "Sothu Diary"); put("HTTP-Referer", "https://github.com/harksathya/test") }
    }
    ApiStyle.ANTHROPIC -> mapOf("x-api-key" to key.trim(), "anthropic-version" to "2023-06-01")
}

/** The models this key can use, or null on a network failure (empty when the provider refused the key). */
suspend fun listModels(p: LlmProvider, key: String): List<ModelInfo>? {
    val url = when (p.style) {
        ApiStyle.GEMINI -> p.baseUrl + "/models?pageSize=200&key=" + com.hark.sothudiary.domain.urlEncode(key.trim())
        ApiStyle.OPENAI -> p.baseUrl + "/models"
        ApiStyle.ANTHROPIC -> p.baseUrl + "/models?limit=100"
    }
    val r = httpRequest(url, method = "GET", headers = authHeaders(p, key), body = null, timeoutMs = 30_000) ?: return null
    if (!r.ok) return emptyList()
    return parseModelList(p.style, r.body)
}

/** Request body of one plain (no tools) call to an OpenAI-compatible or Anthropic API. */
fun chatRequestBody(p: LlmProvider, model: String, prompt: String): String = when (p.style) {
    ApiStyle.ANTHROPIC -> buildJsonObject {
        put("model", model)
        put("max_tokens", 4096)
        put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
    }
    // No temperature: some newer models only accept their default.
    else -> buildJsonObject {
        put("model", model)
        put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
    }
}.toString()

/** The answer text of an OpenAI-compatible or Anthropic reply ("" when there is none). */
fun parseChatText(style: ApiStyle, body: String?): String {
    val root = parseJsonOrNull(body ?: "")
    return when (style) {
        ApiStyle.ANTHROPIC -> root.at("content").arr().filter { it["type"].str() == "text" }.mapNotNull { it["text"].str() }.joinToString("\n")
        ApiStyle.OPENAI -> {
            val content = root.at("choices", 0).at("message", "content")
            content.str() ?: content.arr().mapNotNull { it["text"].str() }.joinToString("\n")
        }
        ApiStyle.GEMINI -> root.at("candidates", 0).at("content", "parts").arr().mapNotNull { it["text"].str() }.joinToString("\n")
    }
}

sealed class ChatResult {
    data class Ok(val text: String) : ChatResult()
    data class Fail(val e: GeminiError) : ChatResult()
    object Offline : ChatResult()
}

/** One plain call to an OpenAI-compatible or Anthropic provider (Gemini goes through pricing.OnlineRates). */
suspend fun chat(p: LlmProvider, key: String, model: String, prompt: String, timeoutMs: Int = 40_000): ChatResult {
    val url = if (p.style == ApiStyle.ANTHROPIC) p.baseUrl + "/messages" else p.baseUrl + "/chat/completions"
    val r = httpRequest(url, method = "POST", headers = authHeaders(p, key) + ("Content-Type" to "application/json"),
        body = chatRequestBody(p, model, prompt), timeoutMs = timeoutMs) ?: return ChatResult.Offline
    if (!r.ok) return ChatResult.Fail(parseGeminiError(r.code, r.body))
    return ChatResult.Ok(parseChatText(p.style, r.body))
}
