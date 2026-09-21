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

    suspend fun fetchModels(config: ProviderConfig): List<String> =
        withCancellableResponse(client, buildModelsRequest(config)) { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, "获取模型列表失败，请检查模型列表地址、凭据与权限")
            }
            val body = resp.body?.string().orEmpty()
            val json = runCatching { ProviderJson.parseToJsonElement(body).jsonObject }
                .getOrElse { throw ApiException(-1, "响应不是合法 JSON") }
            when (config.type) {
                ProviderType.GEMINI -> parseGeminiModels(json.toString())
                else -> parseOpenAiStyleModels(json.toString())
            }
        }

    /** 已知网关按供应商规则解析；未知网关保留路径前缀，允许显式指定列表地址。 */
    internal fun buildModelsRequest(config: ProviderConfig): Request {
        if (config.type == ProviderType.CUSTOM) {
            throw ApiException(-1, "自定义模板类型不支持拉取模型列表，请手动填写模型名")
        }
        val base = config.baseUrl.trim().toHttpUrlOrNull()
            ?: throw ApiException(-1, "非法的 Base URL")
        val override = config.modelsUrl.trim().takeIf { it.isNotEmpty() }
        val isDeepSeek = base.host == "api.deepseek.com" &&
            config.type in setOf(ProviderType.OPENAI, ProviderType.ANTHROPIC)
        val target = if (override != null) {
            override.toHttpUrlOrNull() ?: throw ApiException(-1, "非法的模型列表 URL")
        } else {
            val path = base.encodedPath.trimEnd('/')
            val modelPath = when {
                isDeepSeek -> "/models"
                path.endsWith("/models") -> path
                config.type == ProviderType.ANTHROPIC ->
                    if (path.endsWith("/v1")) "$path/models" else "$path/v1/models"
                config.type == ProviderType.GEMINI ->
                    if (path.endsWith("/v1beta")) "$path/models" else "$path/v1beta/models"
                else -> "$path/models"
            }
            base.newBuilder().encodedPath(modelPath).fragment(null).build()
        }
        // DeepSeek 的列表接口使用 OpenAI 鉴权，即使聊天走 Anthropic 网关。
        val usesBearer = config.type == ProviderType.OPENAI ||
            (isDeepSeek && target.host == "api.deepseek.com" && target.encodedPath.trimEnd('/') == "/models")
        val url = target.newBuilder().fragment(null).apply {
            if (config.type == ProviderType.GEMINI && config.apiKey.isNotBlank()) {
                setQueryParameter("key", config.apiKey)
            }
        }.build()
        val builder = Request.Builder().url(url)
        when {
            usesBearer -> if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey}")
            config.type == ProviderType.ANTHROPIC -> {
                builder.header("anthropic-version", "2023-06-01")
                if (config.apiKey.isNotBlank()) builder.header("x-api-key", config.apiKey)
            }
        }
        config.extraHeaders.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
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
