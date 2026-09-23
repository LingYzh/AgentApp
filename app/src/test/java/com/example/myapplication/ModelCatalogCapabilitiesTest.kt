package com.example.myapplication

import com.example.myapplication.data.model.ModelCapabilities
import com.example.myapplication.provider.ModelFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
