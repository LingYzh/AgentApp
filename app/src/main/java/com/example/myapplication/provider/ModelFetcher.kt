package com.example.myapplication.provider

import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** 从供应商的 /models 接口拉取可用模型列表 */
class ModelFetcher(private val client: OkHttpClient) {

    suspend fun fetchModels(config: ProviderConfig): List<String> {
        val base = config.baseUrl.trimEnd('/')
        val requestBuilder = when (config.type) {
            ProviderType.OPENAI -> {
                val url = if (base.endsWith("/models")) base else "$base/models"
                Request.Builder().url(url).apply {
                    if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
                }
            }
            ProviderType.ANTHROPIC -> {
                val url = when {
                    base.endsWith("/models") -> base
                    base.endsWith("/v1") -> "$base/models"
                    else -> "$base/v1/models"
                }
                Request.Builder().url(url)
                    .header("anthropic-version", "2023-06-01")
                    .apply {
                        if (config.apiKey.isNotBlank()) header("x-api-key", config.apiKey)
                    }
            }
            ProviderType.GEMINI -> {
                val raw = when {
                    base.endsWith("/models") -> base
                    base.endsWith("/v1beta") -> "$base/models"
                    else -> "$base/v1beta/models"
                }
                val url = raw.toHttpUrlOrNull()?.newBuilder()
                    ?.apply {
                        if (config.apiKey.isNotBlank()) addQueryParameter("key", config.apiKey)
                    }
                    ?.build() ?: throw ApiException(-1, "非法的 Gemini Base URL: $raw")
                Request.Builder().url(url)
            }
            ProviderType.CUSTOM ->
                throw ApiException(-1, "自定义模板类型不支持拉取模型列表，请手动填写模型名")
        }
        config.extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        client.newCall(requestBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                throw ApiException(resp.code, body.take(1000))
            }
            val body = resp.body?.string().orEmpty()
            val json = runCatching { ProviderJson.parseToJsonElement(body).jsonObject }
                .getOrElse { throw ApiException(-1, "响应不是合法 JSON") }
            return when (config.type) {
                ProviderType.GEMINI -> parseGeminiModels(json.toString())
                else -> parseOpenAiStyleModels(json.toString())
            }
        }
    }

    companion object {
        /** OpenAI / Anthropic 风格：{"data":[{"id":"..."}]} */
        fun parseOpenAiStyleModels(json: String): List<String> =
            runCatching {
                ProviderJson.parseToJsonElement(json).jsonObject["data"]?.jsonArray
                    ?.mapNotNull { el ->
                        runCatching { el.jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
                    }
                    ?.filter { it.isNotBlank() }
                    ?.sorted()
                    ?: emptyList()
            }.getOrDefault(emptyList())

        /** Gemini 风格：{"models":[{"name":"models/gemini-2.5-flash",...}]}，只保留 generateContent 类模型 */
        fun parseGeminiModels(json: String): List<String> =
            runCatching {
                ProviderJson.parseToJsonElement(json).jsonObject["models"]?.jsonArray
                    ?.mapNotNull { el ->
                        val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                        val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val methods = obj["supportedGenerationMethods"]?.jsonArray
                            ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                            ?: emptyList()
                        if ("generateContent" in methods) name.removePrefix("models/") else null
                    }
                    ?.sorted()
                    ?: emptyList()
            }.getOrDefault(emptyList())
    }
}
