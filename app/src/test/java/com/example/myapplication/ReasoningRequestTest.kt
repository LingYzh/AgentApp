package com.example.myapplication

import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.AnthropicThinkingMode
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.ReasoningProtocol
import com.example.myapplication.data.model.anthropicThinkingProtocol
import com.example.myapplication.data.model.reasoningSupportFor
import com.example.myapplication.provider.AnthropicProvider
import com.example.myapplication.provider.GeminiProvider
import com.example.myapplication.provider.OpenAiProvider
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningRequestTest {
    private val client = OkHttpClient()

    @Test
    fun `OpenAI Chat Completions sends configured reasoning effort without an implicit cap`() {
        val body = OpenAiProvider(client).buildRequestBody(
            ProviderConfig(model = "gpt-5", reasoningEffort = ReasoningEffort.HIGH),
            "", emptyList(), emptyList()
        )
        assertEquals("high", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals(
            "true",
            body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content
        )
        assertFalse(OpenAiProvider(client).buildRequestBody(
            ProviderConfig(model = "gpt-5"), "", emptyList(), emptyList()
        ).containsKey("reasoning_effort"))
        assertFalse(OpenAiProvider(client).buildRequestBody(
            ProviderConfig(model = "deepseek-flash"), "", emptyList(), emptyList()
        ).containsKey("max_tokens"))
    }

    @Test
    fun `OpenAI compatible forwards every explicit effort and temperature`() {
        val support = reasoningSupportFor(ProviderType.OPENAI, "gpt-4o")
        assertEquals(ReasoningProtocol.OPENAI_CHAT_COMPLETIONS, support.protocol)
        assertTrue(support.efforts.containsAll(listOf(ReasoningEffort.NONE, ReasoningEffort.XHIGH, ReasoningEffort.MAX)))
        val body = OpenAiProvider(client).buildRequestBody(
            ProviderConfig(model = "gpt-4o", temperature = 0.7f, reasoningEffort = ReasoningEffort.MAX),
            "", emptyList(), emptyList()
        )
        assertEquals("max", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals("0.7", body["temperature"]!!.jsonPrimitive.content)
    }

    @Test
    fun `OpenAI compatible forwards an explicit completion cap without model gating`() {
        val body = OpenAiProvider(client).buildRequestBody(
            ProviderConfig(model = "gateway-alias", maxOutputTokens = 131_072),
            "", emptyList(), emptyList()
        )
        assertEquals("131072", body["max_tokens"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Claude 4 5 uses manual thinking budget with a safe required default cap`() {
        val body = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.ANTHROPIC,
                model = "claude-sonnet-4-5-20250929",
                reasoningEffort = ReasoningEffort.HIGH
            ),
            "", emptyList(), emptyList()
        )
        assertEquals("enabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("8192", body["thinking"]!!.jsonObject["budget_tokens"]!!.jsonPrimitive.content)
        assertEquals("65536", body["max_tokens"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("output_config"))
    }

    @Test
    fun `Claude 4 6 uses adaptive thinking and output effort`() {
        val body = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.ANTHROPIC,
                model = "claude-sonnet-4-6",
                reasoningEffort = ReasoningEffort.MAX
            ),
            "", emptyList(), emptyList()
        )
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("max", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("65536", body["max_tokens"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Claude none disables thinking without effort`() {
        val body = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.ANTHROPIC,
                model = "gateway-claude-alias",
                temperature = 0.4f,
                reasoningEffort = ReasoningEffort.NONE
            ),
            "", emptyList(), emptyList()
        )
        assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("output_config"))
        assertEquals("0.4", body["temperature"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Claude unknown aliases default to adaptive and allow manual override`() {
        val automatic = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.ANTHROPIC,
                model = "gateway-claude-alias",
                reasoningEffort = ReasoningEffort.XHIGH
            ),
            "", emptyList(), emptyList()
        )
        assertEquals("adaptive", automatic["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("xhigh", automatic["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)

        val manual = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.ANTHROPIC,
                model = "gateway-claude-alias",
                reasoningEffort = ReasoningEffort.LOW,
                anthropicThinkingMode = AnthropicThinkingMode.MANUAL
            ),
            "", emptyList(), emptyList()
        )
        assertEquals("enabled", manual["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("2048", manual["thinking"]!!.jsonObject["budget_tokens"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Claude auto mode recognises only 3 7 and 4 5 as manual`() {
        assertEquals(
            ReasoningProtocol.ANTHROPIC_MANUAL,
            anthropicThinkingProtocol("claude-sonnet-4-5-20250929", AnthropicThinkingMode.AUTO)
        )
        assertEquals(
            ReasoningProtocol.ANTHROPIC_ADAPTIVE,
            anthropicThinkingProtocol("claude-sonnet-4-6", AnthropicThinkingMode.AUTO)
        )
        assertEquals(
            ReasoningProtocol.ANTHROPIC_ADAPTIVE,
            anthropicThinkingProtocol("gateway-claude-alias", AnthropicThinkingMode.AUTO)
        )
    }

    @Test
    fun `Claude thinking omits saved temperature instead of blocking an effort override`() {
        val body = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.ANTHROPIC,
                model = "claude-sonnet-4-6",
                temperature = 0.5f,
                reasoningEffort = ReasoningEffort.HIGH
            ),
            "", emptyList(), emptyList()
        )
        assertFalse(body.containsKey("temperature"))
        assertEquals("high", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Anthropic respects explicit cap and rejects a manual thinking conflict`() {
        val capped = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(type = ProviderType.ANTHROPIC, model = "gateway-alias", maxOutputTokens = 50_000),
            "", emptyList(), emptyList()
        )
        assertEquals("50000", capped["max_tokens"]!!.jsonPrimitive.content)

        val error = runCatching {
            AnthropicProvider(client).buildRequestBody(
                ProviderConfig(
                    type = ProviderType.ANTHROPIC,
                    model = "gateway-alias",
                    maxOutputTokens = 32_768,
                    reasoningEffort = ReasoningEffort.MAX,
                    anthropicThinkingMode = AnthropicThinkingMode.MANUAL
                ),
                "", emptyList(), emptyList()
            )
        }.exceptionOrNull()
        assertTrue(error?.message?.contains("must exceed") == true)
    }

    @Test
    fun `Gemini 3 uses thinking level while Gemini 2 5 uses budget`() {
        val gemini3 = GeminiProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.GEMINI,
                model = "gemini-3.8-flash",
                reasoningEffort = ReasoningEffort.LOW
            ),
            "", emptyList(), emptyList()
        )
        val gemini3Thinking = gemini3["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject
        assertEquals("low", gemini3Thinking["thinkingLevel"]!!.jsonPrimitive.content)
        assertFalse(gemini3Thinking.containsKey("thinkingBudget"))

        val gemini25 = GeminiProvider(client).buildRequestBody(
            ProviderConfig(
                type = ProviderType.GEMINI,
                model = "gemini-2.5-flash",
                reasoningEffort = ReasoningEffort.NONE
            ),
            "", emptyList(), emptyList()
        )
        val gemini25Thinking = gemini25["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject
        assertEquals("0", gemini25Thinking["thinkingBudget"]!!.jsonPrimitive.content)
        assertFalse(gemini25Thinking.containsKey("thinkingLevel"))
    }

    @Test
    fun `Gemini forwards an explicit output cap`() {
        val body = GeminiProvider(client).buildRequestBody(
            ProviderConfig(type = ProviderType.GEMINI, model = "gemini-3.8-flash", maxOutputTokens = 12_345),
            "", emptyList(), emptyList()
        )
        assertEquals("12345", body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.content)
    }

    @Test
    fun `custom and unknown Gemini models do not gain guessed thinking configuration`() {
        assertEquals(
            ReasoningProtocol.UNSUPPORTED,
            reasoningSupportFor(ProviderType.CUSTOM, "anything").protocol
        )
        assertEquals(
            ReasoningProtocol.UNSUPPORTED,
            reasoningSupportFor(ProviderType.GEMINI, "gateway-gemini-alias").protocol
        )
    }
}
