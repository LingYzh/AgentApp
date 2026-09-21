package com.example.myapplication

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ModelCapabilities
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.capabilitiesFor
import com.example.myapplication.data.store.AttachmentStore
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.AnthropicProvider
import com.example.myapplication.provider.CustomProvider
import com.example.myapplication.provider.GeminiProvider
import com.example.myapplication.provider.ModelFetcher
import com.example.myapplication.provider.OpenAiProvider
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class ProviderMultimodalTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = AttachmentStore(FileStore(temp.root))

    @Test
    fun `null or malformed optional capability does not erase catalog or become known`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{"data":[
            {"id":"null","input_modalities":null},
            {"id":"malformed","capabilities":{"image_input":{"supported":{}}}},
            {"id":"valid","capabilities":{"image_input":{"supported":true}}}
        ]}""")
        assertEquals(3, catalog.models.size)
        assertEquals(setOf("valid"), catalog.discoveredCapabilities.keys)
    }

    @Test
    fun `OpenAI wire message includes data URL image and PDF file`() {
        val files = store()
        val image = files.importFile("photo.png", "image/png", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
            .copy(delivery = "native")
        val pdf = files.importFile("note.pdf", "application/pdf", ByteArrayInputStream(byteArrayOf(4, 5)))
            .copy(delivery = "native")
        val wire = OpenAiProvider(OkHttpClient(), files).toWireMessage(
            ChatMessage(role = "user", content = "inspect", attachments = listOf(image, pdf)), files
        )
        val content = wire["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("data:image/png;base64,AQID", content[1].jsonObject["image_url"]!!
            .jsonObject["url"]!!.jsonPrimitive.content)
        assertEquals("note.pdf", content[2].jsonObject["file"]!!.jsonObject["filename"]!!.jsonPrimitive.content)
        assertEquals("data:application/pdf;base64,BAU=", content[2].jsonObject["file"]!!
            .jsonObject["file_data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Anthropic and Gemini send bytes in their native base64 blocks`() {
        val files = store()
        val image = files.importFile("photo.png", "image/png", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
            .copy(delivery = "native")
        val message = ChatMessage(role = "user", content = "inspect", attachments = listOf(image))

        val anthropic = AnthropicProvider(OkHttpClient(), files).toWireMessages(listOf(message), files).single()
        val source = anthropic["content"]!!.jsonArray[1].jsonObject["source"]!!.jsonObject
        assertEquals("base64", source["type"]!!.jsonPrimitive.content)
        assertEquals("AQID", source["data"]!!.jsonPrimitive.content)

        val gemini = GeminiProvider(OkHttpClient(), files).toWireContents(listOf(message), files).single()
        val inlineData = gemini["parts"]!!.jsonArray[1].jsonObject["inlineData"]!!.jsonObject
        assertEquals("image/png", inlineData["mimeType"]!!.jsonPrimitive.content)
        assertEquals("AQID", inlineData["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `catalogue uses explicit modalities and manual override wins`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{
            "data":[
                {"id":"known","architecture":{"input_modalities":["text","image","file"]}},
                {"id":"unknown"}
            ]
        }""")
        assertEquals(listOf("known", "unknown"), catalog.models)
        assertTrue(catalog.discoveredCapabilities.getValue("known").image)
        assertTrue(catalog.discoveredCapabilities.getValue("known").pdf)
        assertFalse(catalog.discoveredCapabilities.containsKey("unknown"))

        val config = ProviderConfig(
            model = "known",
            discoveredCapabilities = catalog.discoveredCapabilities,
            capabilityOverrides = mapOf("known" to ModelCapabilities(audio = true))
        )
        assertEquals(ModelCapabilities(audio = true), config.capabilitiesFor())
    }

    @Test
    fun `Anthropic catalogue keeps explicit unsupported capabilities`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{
            "data":[
                {"id":"claude-known","capabilities":{
                    "image_input":{"supported":false},
                    "pdf_input":{"supported":true}
                }},
                {"id":"claude-unknown"}
            ]
        }""")
        assertEquals(ModelCapabilities(pdf = true), catalog.discoveredCapabilities["claude-known"])
        assertFalse(catalog.discoveredCapabilities.containsKey("claude-unknown"))
    }

    @Test
    fun `Anthropic explicitly false capabilities remain known`() {
        val catalog = ModelFetcher.parseOpenAiStyleModelCatalog("""{
            "data":[{"id":"claude-no-files","capabilities":{
                "image_input":{"supported":false},
                "pdf_input":{"supported":false}
            }}]
        }""")
        assertEquals(ModelCapabilities(), catalog.discoveredCapabilities["claude-no-files"])
    }

    @Test
    fun `Gemini generation method alone does not imply a modality`() {
        val catalog = ModelFetcher.parseGeminiModelCatalog("""{
            "models":[{"name":"models/gemini","supportedGenerationMethods":["generateContent"]}]
        }""")
        assertEquals(listOf("gemini"), catalog.models)
        assertFalse(catalog.discoveredCapabilities.containsKey("gemini"))
    }

    @Test
    fun `custom and missing attachment store reject before a network request`() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            chain.proceed(chain.request())
        }.build()
        val files = store()
        val workspaceAttachment = files.importFile(
            "notes.txt", "text/plain", ByteArrayInputStream(byteArrayOf(1))
        )
        val nativeImage = files.importFile(
            "photo.png", "image/png", ByteArrayInputStream(byteArrayOf(1))
        ).copy(delivery = "native")
        val config = ProviderConfig(baseUrl = "https://example.invalid", model = "test")

        try {
            CustomProvider(client, files).streamChat(
                config, "", listOf(ChatMessage(role = "user", attachments = listOf(workspaceAttachment))), emptyList()
            ) {}
            fail("custom provider should reject attachments")
        } catch (_: IllegalArgumentException) {
        }
        try {
            OpenAiProvider(client).streamChat(
                config, "", listOf(ChatMessage(role = "user", attachments = listOf(nativeImage))), emptyList()
            ) {}
            fail("provider without attachment store should reject attachments")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, requests)
    }
}
