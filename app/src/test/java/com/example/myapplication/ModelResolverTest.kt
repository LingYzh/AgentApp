package com.example.myapplication

import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ModelResolver
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.contextWindowFor
import com.example.myapplication.provider.ModelFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelResolverTest {

    private val p1 = ProviderConfig(id = "p1", name = "主", type = ProviderType.OPENAI, model = "m1")
    private val p2 = ProviderConfig(id = "p2", name = "备", type = ProviderType.OPENAI, model = "m2")
    private val appConfig = AppConfig(providers = listOf(p1, p2), selectedProviderId = p1.id)
    private val agent = AgentProfile(
        id = "a1", name = "写手", providerId = p2.id, model = "m2-pro"
    )

    @Test
    fun `no conversation override falls back to global selected`() {
        val conv = Conversation()
        val resolved = ModelResolver.resolve(conv, appConfig, emptyList())!!
        assertEquals("p1", resolved.id)
        assertEquals("m1", resolved.model)
    }

    @Test
    fun `agent profile defaults apply`() {
        val conv = Conversation(agentId = "a1")
        val resolved = ModelResolver.resolve(conv, appConfig, listOf(agent))!!
        assertEquals("p2", resolved.id)
        assertEquals("m2-pro", resolved.model)
    }

    @Test
    fun `conversation override beats agent profile`() {
        val conv = Conversation(agentId = "a1").also {
            it.providerIdOverride = "p1"
            it.modelOverride = "m1-mini"
        }
        val resolved = ModelResolver.resolve(conv, appConfig, listOf(agent))!!
        assertEquals("p1", resolved.id)
        assertEquals("m1-mini", resolved.model)
    }

    @Test
    fun `no providers returns null`() {
        val resolved = ModelResolver.resolve(Conversation(), AppConfig(), emptyList())
        assertNull(resolved)
    }

    @Test
    fun `resolved conversation ignores stored provider reasoning strength`() {
        val stored = p1.copy(reasoningEffort = ReasoningEffort.MAX)
        val resolved = ModelResolver.resolve(
            Conversation(reasoningEffortOverride = null),
            AppConfig(providers = listOf(stored), selectedProviderId = stored.id),
            emptyList()
        )!!

        assertEquals(ReasoningEffort.MEDIUM, resolved.reasoningEffort)
    }
}

class ModelFetcherParseTest {

    @Test
    fun `parse openai style models`() {
        val json = """{"object":"list","data":[{"id":"gpt-4o"},{"id":"deepseek-chat"},{"id":"gpt-4o-mini"}]}"""
        assertEquals(
            listOf("deepseek-chat", "gpt-4o", "gpt-4o-mini"),
            ModelFetcher.parseOpenAiStyleModels(json)
        )
    }

    @Test
    fun `parse anthropic style models`() {
        val json = """{"data":[{"id":"claude-sonnet-4-5","type":"model"},{"id":"claude-haiku-4-5","type":"model"}],"has_more":false}"""
        assertEquals(
            listOf("claude-haiku-4-5", "claude-sonnet-4-5"),
            ModelFetcher.parseOpenAiStyleModels(json)
        )
    }

    @Test
    fun `parse gemini models filters by generateContent`() {
        val json = """{"models":[
            {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-embedding","supportedGenerationMethods":["embedContent"]},
            {"name":"models/gemini-2.5-pro","supportedGenerationMethods":["generateContent","countTokens"]}
        ]}"""
        assertEquals(
            listOf("gemini-2.5-flash", "gemini-2.5-pro"),
            ModelFetcher.parseGeminiModels(json)
        )
    }

    @Test
    fun `garbage json yields empty list`() {
        assertEquals(emptyList<String>(), ModelFetcher.parseOpenAiStyleModels("not json"))
        assertEquals(emptyList<String>(), ModelFetcher.parseGeminiModels("{}"))
    }
}

class ProviderConfigSerializationTest {

    @Test
    fun `context window overrides persist only for their model`() {
        val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
        val original = ProviderConfig(
            model = "gateway-coder",
            contextWindowOverrides = mapOf("gateway-coder" to 131_072)
        )
        val restored = json.decodeFromString<ProviderConfig>(json.encodeToString(original))

        assertEquals(131_072, restored.contextWindowFor())
        assertNull(restored.contextWindowFor("another-model"))
    }

    @Test
    fun `nonpositive imported context window is unknown`() {
        assertNull(ProviderConfig(contextWindowOverrides = mapOf("m" to -1)).contextWindowFor("m"))
    }

    @Test
    fun `optional output cap survives provider config serialization`() {
        val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }
        val restored = json.decodeFromString<ProviderConfig>(
            json.encodeToString(ProviderConfig(model = "gateway", maxOutputTokens = 65_536))
        )

        assertEquals(65_536, restored.maxOutputTokens)
    }
}
