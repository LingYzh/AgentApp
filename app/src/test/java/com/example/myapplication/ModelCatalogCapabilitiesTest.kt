package com.example.myapplication

import com.example.myapplication.data.model.ModelCapabilities
import com.example.myapplication.data.model.ModelMetadata
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.contextWindowFor
import com.example.myapplication.provider.ModelFetcher
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogCapabilitiesTest {
    @Test
    fun `local gateway input declarations are discovered without output leakage`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {"id":"local","modalities":{"input":["text","image"],"output":["audio"]},
             "capabilities":{"input":{"image":true,"pdf":false,"audio":false,"video":false}}},
            {"id":"uppercase","inputTypes":["TEXT","IMAGE","PDF"]},
            {"id":"output-only","modalities":{"output":["image","audio"]},"attachment":true}
        ]}""")
        assertEquals(ModelCapabilities(image = true), catalog.discoveredCapabilities["local"])
        assertEquals(ModelCapabilities(image = true, pdf = true), catalog.discoveredCapabilities["uppercase"])
        assertFalse(catalog.discoveredCapabilities.containsKey("output-only"))
    }

    @Test
    fun `explicit false wins over positive aliases and malformed booleans stay unknown`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {"id":"false","modalities":{"input":["image","audio","pdf"]},
             "capabilities":{"input":{"image":false,"audio":false,"pdf":false}}},
            {"id":"bad","capabilities":{"input":{"image":"true","pdf":1}}},
            {"id":{},"capabilities":{"vision":true}},
            {"id":"good","capabilities":{"vision":true}}
        ]}""")
        assertEquals(3, catalog.models.size)
        assertEquals(ModelCapabilities(), catalog.discoveredCapabilities["false"])
        assertFalse(catalog.discoveredCapabilities.containsKey("bad"))
        assertTrue(catalog.discoveredCapabilities.getValue("good").image)
    }

    @Test
    fun `Mistral bare catalog array recognizes vision without inferring PDF`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""[
            {"id":"vision-model","capabilities":{"vision":true,"function_calling":true}},
            {"id":"text-model","capabilities":{"vision":false}}
        ]""")
        assertEquals(ModelCapabilities(image = true), catalog.discoveredCapabilities["vision-model"])
        assertEquals(ModelCapabilities(), catalog.discoveredCapabilities["text-model"])
    }

    @Test
    fun `Gemini custom input metadata is recognized but generation method is not vision`() {
        val catalog = ModelFetcher.parseGeminiModelCatalog("""{"models":[
            {"name":"models/explicit","supportedGenerationMethods":["generateContent"],
             "inputModalities":["TEXT","AUDIO","VIDEO"]},
            {"name":"models/unknown","supportedGenerationMethods":["generateContent"]}
        ]}""")
        assertEquals(ModelCapabilities(audio = true, video = true), catalog.discoveredCapabilities["explicit"])
        assertFalse(catalog.discoveredCapabilities.containsKey("unknown"))
    }

    @Test
    fun `real shape models-5580 declaration parses limits, thinking, booleans and raw`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {
                "id":"claude-opus-5",
                "reasoning":true,
                "temperature":true,
                "tool_call":true,
                "limit":{"context":1000000,"input":1000000,"output":128000},
                "modalities":{"input":["text","image"],"output":["text"]},
                "capabilities":{"temperature":true,"reasoning":true,"attachment":true,"toolcall":true},
                "context_length":1000000,
                "max_tokens":128000,
                "max_input_tokens":1000000,
                "max_output_tokens":128000,
                "supportsThinking":true,
                "thinkingEfforts":["low","medium","high","xhigh","max"],
                "supportsPromptCaching":true
            },
            {
                "id":"auto",
                "reasoning":false,
                "temperature":true,
                "tool_call":true,
                "limit":{"context":1000000,"input":1000000,"output":64000},
                "capabilities":{"temperature":true,"reasoning":false,"toolcall":true},
                "supportsThinking":false,
                "supportsPromptCaching":true
            }
        ]}""")

        val opus = catalog.discoveredModelMetadata["claude-opus-5"]
        assertNotNull(opus)
        assertEquals(1000000, opus!!.contextWindow)
        assertEquals(1000000, opus.maxInputTokens)
        assertEquals(128000, opus.maxOutputTokens)
        assertEquals(true, opus.reasoning)
        assertEquals(true, opus.temperature)
        assertEquals(true, opus.toolCall)
        assertEquals(true, opus.promptCaching)
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), opus.reasoningEfforts)
        assertEquals(listOf("text", "image"), opus.inputModalities)
        assertEquals(listOf("text"), opus.outputModalities)
        assertEquals("claude-opus-5", opus.raw["id"]?.jsonPrimitive?.content)

        val auto = catalog.discoveredModelMetadata["auto"]
        assertNotNull(auto)
        assertEquals(false, auto!!.reasoning)
        assertEquals(true, auto.temperature)
        assertEquals(true, auto.toolCall)
        assertEquals(true, auto.promptCaching)
        assertEquals(1000000, auto.contextWindow)
        assertEquals(64000, auto.maxOutputTokens)
        assertNull(auto.reasoningEfforts)
    }

    @Test
    fun `empty fields and unknown models remain null with raw preserved`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {"id":"bare-model"}
        ]}""")
        val meta = catalog.discoveredModelMetadata["bare-model"]
        assertNotNull(meta)
        assertNull(meta!!.contextWindow)
        assertNull(meta.maxInputTokens)
        assertNull(meta.maxOutputTokens)
        assertNull(meta.reasoning)
        assertNull(meta.temperature)
        assertNull(meta.toolCall)
        assertNull(meta.promptCaching)
        assertNull(meta.reasoningEfforts)
        assertNull(meta.inputModalities)
        assertNull(meta.outputModalities)
        assertEquals("bare-model", meta.raw["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `explicit false wins over nested or root positive aliases`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {
                "id":"override-model",
                "capabilities":{"reasoning":false,"temperature":false,"toolcall":false},
                "supportsThinking":true,
                "temperature":true,
                "tool_call":true
            },
            {
                "id":"root-false-model",
                "reasoning":false,
                "temperature":false,
                "tool_call":false
            }
        ]}""")

        val over = catalog.discoveredModelMetadata["override-model"]!!
        assertEquals(false, over.reasoning)
        assertEquals(false, over.temperature)
        assertEquals(false, over.toolCall)

        val rootFalse = catalog.discoveredModelMetadata["root-false-model"]!!
        assertEquals(false, rootFalse.reasoning)
        assertEquals(false, rootFalse.temperature)
        assertEquals(false, rootFalse.toolCall)
    }

    @Test
    fun `contextWindowFor prioritizes manual override then discovered metadata`() {
        val configWithBoth = ProviderConfig(
            contextWindowOverrides = mapOf("m" to 50000),
            discoveredModelMetadata = mapOf("m" to ModelMetadata(contextWindow = 1000000))
        )
        assertEquals(50000, configWithBoth.contextWindowFor("m"))

        val configDiscoveredOnly = ProviderConfig(
            discoveredModelMetadata = mapOf("m" to ModelMetadata(contextWindow = 1000000))
        )
        assertEquals(1000000, configDiscoveredOnly.contextWindowFor("m"))

        val configInvalidOverride = ProviderConfig(
            contextWindowOverrides = mapOf("m" to -1),
            discoveredModelMetadata = mapOf("m" to ModelMetadata(contextWindow = 1000000))
        )
        assertEquals(1000000, configInvalidOverride.contextWindowFor("m"))

        val configInvalidDiscovered = ProviderConfig(
            discoveredModelMetadata = mapOf("m" to ModelMetadata(contextWindow = 0))
        )
        assertNull(configInvalidDiscovered.contextWindowFor("m"))
    }

    @Test
    fun `Gemini models parse inputTokenLimit and outputTokenLimit into metadata`() {
        val catalog = ModelFetcher.parseGeminiModelCatalog("""{"models":[
            {
                "name":"models/gemini-1.5-pro",
                "supportedGenerationMethods":["generateContent"],
                "inputTokenLimit":2000000,
                "outputTokenLimit":8192
            }
        ]}""")
        val meta = catalog.discoveredModelMetadata["gemini-1.5-pro"]
        assertNotNull(meta)
        assertEquals(2000000, meta!!.contextWindow)
        assertEquals(2000000, meta.maxInputTokens)
        assertEquals(8192, meta.maxOutputTokens)
        assertNull(meta.reasoning)
    }

    @Test
    fun `empty arrays for reasoningEfforts and modalities are preserved as emptyList rather than null`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {
                "id":"empty-arrays-model",
                "thinkingEfforts":[],
                "modalities":{"input":[],"output":[]}
            }
        ]}""")
        val meta = catalog.discoveredModelMetadata["empty-arrays-model"]
        assertNotNull(meta)
        assertEquals(emptyList<String>(), meta!!.reasoningEfforts)
        assertEquals(emptyList<String>(), meta.inputModalities)
        assertEquals(emptyList<String>(), meta.outputModalities)
    }

    @Test
    fun `array fields accept only isString primitives and ignore numbers and booleans`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {
                "id":"mixed-types-model",
                "thinkingEfforts":["low", 123, true, "high", {"nested":"val"}],
                "modalities":{"input":["text", false, 999, "image"],"output":[true, "text", 42]}
            }
        ]}""")
        val meta = catalog.discoveredModelMetadata["mixed-types-model"]
        assertNotNull(meta)
        assertEquals(listOf("low", "high"), meta!!.reasoningEfforts)
        assertEquals(listOf("text", "image"), meta.inputModalities)
        assertEquals(listOf("text"), meta.outputModalities)
    }

    @Test
    fun `generic tools field does not infer toolCall`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {
                "id":"generic-tools-model",
                "tools":true,
                "capabilities":{"tools":true}
            },
            {
                "id":"generic-tools-array-model",
                "tools":[{"type":"function"}]
            },
            {
                "id":"actual-toolcall-model",
                "capabilities":{"toolcall":true},
                "tools":false
            }
        ]}""")
        val m1 = catalog.discoveredModelMetadata["generic-tools-model"]
        assertNotNull(m1)
        assertNull(m1!!.toolCall)

        val m2 = catalog.discoveredModelMetadata["generic-tools-array-model"]
        assertNotNull(m2)
        assertNull(m2!!.toolCall)

        val m3 = catalog.discoveredModelMetadata["actual-toolcall-model"]
        assertNotNull(m3)
        assertEquals(true, m3!!.toolCall)
    }
}
