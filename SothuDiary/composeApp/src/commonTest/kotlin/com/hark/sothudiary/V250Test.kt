package com.hark.sothudiary

import com.hark.sothudiary.data.AppSettings
import com.hark.sothudiary.domain.ai.ApiStyle
import com.hark.sothudiary.domain.ai.AUTO_MODEL
import com.hark.sothudiary.domain.ai.LlmProviders
import com.hark.sothudiary.domain.ai.ModelInfo
import com.hark.sothudiary.domain.ai.chatRequestBody
import com.hark.sothudiary.domain.ai.hasAiKey
import com.hark.sothudiary.domain.ai.llm
import com.hark.sothudiary.domain.ai.normalizeModel
import com.hark.sothudiary.domain.ai.parseChatText
import com.hark.sothudiary.domain.ai.parseModelList
import com.hark.sothudiary.domain.ai.rankChatModels
import com.hark.sothudiary.domain.ai.withLlm
import com.hark.sothudiary.domain.pricing.LookupErrorKind
import com.hark.sothudiary.domain.pricing.classifyLookupError
import com.hark.sothudiary.domain.pricing.isBadKeyError
import com.hark.sothudiary.domain.pricing.isModelUnavailableError
import com.hark.sothudiary.domain.pricing.isQuotaError
import com.hark.sothudiary.domain.pricing.parseGeminiError
import com.hark.sothudiary.domain.pricing.shortAiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.5.0 (issue #1): any LLM provider with the user's own key. */
class V250Test {
    @Test
    fun providerRegistry() {
        val ids = LlmProviders.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in listOf("gemini", "openai", "anthropic", "openrouter", "xai", "groq")) assertTrue(id in ids, id)
        assertEquals(LlmProviders.ANTHROPIC, LlmProviders.byId("Anthropic"))
        assertNull(LlmProviders.byId("nope"))
        assertTrue(LlmProviders.all.all { it.baseUrl.startsWith("https://") && !it.baseUrl.endsWith("/") && it.keyUrl.startsWith("https://") })
        // Only Gemini has Google Search grounding built in.
        assertEquals(listOf("gemini"), LlmProviders.all.filter { it.nativeSearch }.map { it.id })
    }

    @Test
    fun keyGuess() {
        assertEquals(LlmProviders.ANTHROPIC, LlmProviders.guessFromKey("sk-ant-api03-abc"))
        assertEquals(LlmProviders.OPENROUTER, LlmProviders.guessFromKey("sk-or-v1-abc"))
        assertEquals(LlmProviders.GEMINI, LlmProviders.guessFromKey("AIzaSyXYZ"))
        assertEquals(LlmProviders.GROQ, LlmProviders.guessFromKey("gsk_abc"))
        assertEquals(LlmProviders.XAI, LlmProviders.guessFromKey("xai-abc"))
        // "sk-" alone is OpenAI or DeepSeek: not guessed.
        assertNull(LlmProviders.guessFromKey("sk-proj-abc"))
        assertNull(LlmProviders.guessFromKey(""))
    }

    @Test
    fun olderSettingsStayOnGemini() {
        val old = AppSettings(geminiApiKey = "AIzaOld", geminiModel = "gemini-2.0-flash")
        val c = old.llm()
        assertEquals(LlmProviders.GEMINI, c.provider)
        assertEquals("AIzaOld", c.apiKey)
        assertEquals(AUTO_MODEL, c.model)
        assertTrue(old.hasAiKey)
        assertFalse(AppSettings().hasAiKey)
    }

    @Test
    fun anyOneProviderKeyIsEnough() {
        val s = AppSettings().withLlm(LlmProviders.OPENAI, "  sk-proj-123 ", "Auto (latest)")
        assertTrue(s.hasAiKey)
        assertEquals("", s.geminiApiKey)
        val c = s.llm()
        assertEquals(LlmProviders.OPENAI, c.provider)
        assertEquals("sk-proj-123", c.apiKey)
        assertEquals(AUTO_MODEL, c.model)
        // Switching provider keeps the other provider's key, and Gemini still uses its own fields.
        val g = s.withLlm(LlmProviders.GEMINI, "AIzaX", "")
        assertEquals("AIzaX", g.geminiApiKey)
        assertEquals("sk-proj-123", g.llmKeys["openai"])
        assertEquals(LlmProviders.GEMINI, g.llm().provider)
        // A chosen model is kept as typed.
        assertEquals("my-model", AppSettings().withLlm(LlmProviders.GROQ, "gsk_1", " my-model ").llm().model)
        assertEquals(AUTO_MODEL, normalizeModel(LlmProviders.ANTHROPIC, "தானியங்கி (புதியது)"))
    }

    @Test
    fun modelListsAreReadFromTheProvider() {
        val openai = """{"object":"list","data":[{"id":"text-embedding-3-small","created":1705948997},{"id":"whisper-1","created":1677532384},
            {"id":"chat-new","created":1750000000},{"id":"chat-old","created":1700000000},{"id":"chat-next-preview","created":1760000000},
            {"id":"omni-moderation-latest","created":1731689265},{"id":"tts-1","created":1681940951}]}"""
        val listed = parseModelList(ApiStyle.OPENAI, openai)
        assertEquals(7, listed.size)
        assertEquals(listOf("chat-new", "chat-old", "chat-next-preview"), rankChatModels(LlmProviders.OPENAI, listed))

        val anthropic = """{"data":[{"type":"model","id":"claude-a","display_name":"A","created_at":"2024-10-22T00:00:00Z"},
            {"type":"model","id":"claude-b","display_name":"B","created_at":"2025-05-14T00:00:00Z"}],"has_more":false}"""
        val a = parseModelList(ApiStyle.ANTHROPIC, anthropic)
        assertEquals(20250514L, a.first { it.id == "claude-b" }.created)
        assertEquals(listOf("claude-b", "claude-a"), rankChatModels(LlmProviders.ANTHROPIC, a))

        val router = """{"data":[{"id":"x/img-maker","created":1760000000,"architecture":{"output_modalities":["image"]}},
            {"id":"y/chat","created":1750000000,"architecture":{"output_modalities":["text"]}},
            {"id":"openrouter/auto","created":1690000000,"architecture":{"output_modalities":["text"]}}]}"""
        assertEquals(listOf("openrouter/auto", "y/chat"), rankChatModels(LlmProviders.OPENROUTER, parseModelList(ApiStyle.OPENAI, router)))

        val mistral = """{"data":[{"id":"m-chat","created":1,"capabilities":{"completion_chat":true}},{"id":"m-x","created":2,"capabilities":{"completion_chat":false}}]}"""
        assertEquals(listOf("m-chat"), rankChatModels(LlmProviders.MISTRAL, parseModelList(ApiStyle.OPENAI, mistral)))

        val gemini = """{"models":[{"name":"models/gemini-9.0-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}]}"""
        assertEquals(listOf("gemini-9.0-flash"), rankChatModels(LlmProviders.GEMINI, parseModelList(ApiStyle.GEMINI, gemini)))
        assertTrue(parseModelList(ApiStyle.OPENAI, "not json").isEmpty())
        assertTrue(rankChatModels(LlmProviders.OPENAI, listOf(ModelInfo("text-embedding-x"))).isEmpty())
    }

    @Test
    fun requestBodies() {
        val ant = chatRequestBody(LlmProviders.ANTHROPIC, "claude-b", "hi \"there\"")
        assertTrue("\"max_tokens\"" in ant && "\"model\":\"claude-b\"" in ant && "\"role\":\"user\"" in ant)
        assertTrue("hi \\\"there\\\"" in ant)
        val oa = chatRequestBody(LlmProviders.OPENAI, "chat-new", "hello")
        assertTrue("\"messages\"" in oa && "\"model\":\"chat-new\"" in oa)
        assertFalse("temperature" in oa)
    }

    @Test
    fun replies() {
        assertEquals("{\"a\":1}", parseChatText(ApiStyle.OPENAI, """{"choices":[{"index":0,"message":{"role":"assistant","content":"{\"a\":1}"}}]}"""))
        assertEquals("one\ntwo", parseChatText(ApiStyle.ANTHROPIC,
            """{"content":[{"type":"text","text":"one"},{"type":"tool_use","id":"x"},{"type":"text","text":"two"}],"stop_reason":"end_turn"}"""))
        assertEquals("", parseChatText(ApiStyle.OPENAI, "{}"))
    }

    @Test
    fun otherProvidersErrors() {
        val bad = parseGeminiError(401, """{"error":{"message":"Incorrect API key provided: sk-xx.","type":"invalid_request_error","code":"invalid_api_key"}}""")
        assertTrue(isBadKeyError(bad.code, bad.status, bad.message))
        val antBad = parseGeminiError(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""")
        assertTrue(isBadKeyError(antBad.code, antBad.status, antBad.message))
        val rate = parseGeminiError(429, """{"error":{"message":"Rate limit reached for model","type":"tokens"}}""")
        assertTrue(isQuotaError(rate.code, rate.status, rate.message))
        val gone = parseGeminiError(404, """{"error":{"message":"The model `old-x` does not exist or you do not have access to it.","code":"model_not_found"}}""")
        assertTrue(isModelUnavailableError(gone.code, gone.message))
        assertEquals("no AI key", shortAiError("no_key"))
        assertEquals(LookupErrorKind.NO_KEY, classifyLookupError("no_key"))
    }
}
