package com.example.myapplication

import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.provider.ModelFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ModelFetcherRequestTest {
    private val fetcher = ModelFetcher(OkHttpClient())

    @Test
    fun `deepseek gateways share root models endpoint and bearer authentication`() {
        listOf("", "/", "/v1", "/anthropic", "/anthropic/", "/anthropic/v1").forEach { path ->
            listOf(ProviderType.OPENAI, ProviderType.ANTHROPIC).forEach { type ->
                val request = fetcher.buildModelsRequest(ProviderConfig(
                    type = type, baseUrl = "https://api.deepseek.com$path", apiKey = "test-key"
                ))
                assertEquals("https://api.deepseek.com/models", request.url.toString())
                assertEquals("Bearer test-key", request.header("Authorization"))
                assertNull(request.header("x-api-key"))
            }
        }
    }

    @Test
    fun `unknown gateways retain tenant and protocol paths`() {
        val request = fetcher.buildModelsRequest(ProviderConfig(
            type = ProviderType.ANTHROPIC,
            baseUrl = "https://gateway.example/tenant/anthropic/v1/?region=test",
            apiKey = "test-key"
        ))
        assertEquals("https://gateway.example/tenant/anthropic/v1/models?region=test", request.url.toString())
        assertEquals("test-key", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        val lookalike = fetcher.buildModelsRequest(ProviderConfig(
            type = ProviderType.ANTHROPIC, baseUrl = "https://api.deepseek.com.example/anthropic"
        ))
        assertEquals("/anthropic/v1/models", lookalike.url.encodedPath)
    }

    @Test
    fun `explicit model list url wins and custom headers remain authoritative`() {
        val request = fetcher.buildModelsRequest(ProviderConfig(
            type = ProviderType.ANTHROPIC, baseUrl = "https://api.deepseek.com/anthropic",
            modelsUrl = "https://api.deepseek.com/special/catalog?region=test",
            apiKey = "test-key", extraHeaders = mapOf("Authorization" to "custom-auth")
        ))
        assertEquals("https://api.deepseek.com/special/catalog?region=test", request.url.toString())
        assertEquals("custom-auth", request.header("Authorization"))
    }

    @Test
    fun `standard protocols and already complete urls stay compatible`() {
        val anthropic = fetcher.buildModelsRequest(ProviderConfig(
            type = ProviderType.ANTHROPIC, baseUrl = "https://api.anthropic.com"
        ))
        assertEquals("/v1/models", anthropic.url.encodedPath)
        val openai = fetcher.buildModelsRequest(ProviderConfig(baseUrl = "https://api.example/v1/models/"))
        assertEquals("/v1/models", openai.url.encodedPath)
        val gemini = fetcher.buildModelsRequest(ProviderConfig(
            type = ProviderType.GEMINI, baseUrl = "https://google.example/v1beta?key=old", apiKey = "new"
        ))
        assertEquals("/v1beta/models", gemini.url.encodedPath)
        assertEquals(listOf("new"), gemini.url.queryParameterValues("key"))
    }

    @Test
    fun `fetch uses resolved request and parses deepseek list for anthropic config`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("https://api.deepseek.com/models", request.url.toString())
            assertEquals("Bearer test-key", request.header("Authorization"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":[{"id":"model-b"},{"id":"model-a"}]}""".toResponseBody()).build()
        }.build()
        assertEquals(listOf("model-a", "model-b"), ModelFetcher(client).fetchModels(ProviderConfig(
            type = ProviderType.ANTHROPIC, baseUrl = "https://api.deepseek.com/anthropic", apiKey = "test-key"
        )))
    }
}
