package com.example.myapplication.provider

import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.ModelCapabilities
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Missing metadata remains unknown; attachment delivery defaults conservatively. */
data class ModelCatalog(
    val models: List<String>,
    val discoveredCapabilities: Map<String, ModelCapabilities>
)

/** 从供应商的 /models 接口拉取可用模型列表 */
class ModelFetcher(private val client: OkHttpClient) {

    /** Legacy list-only API retained for existing callers. */
    suspend fun fetchModels(config: ProviderConfig): List<String> = fetchModelCatalog(config).models

    /** Fetches models plus only capability fields explicitly advertised by the catalogue. */
    suspend fun fetchModelCatalog(config: ProviderConfig): ModelCatalog =
        withCancellableResponse(client, buildModelsRequest(config)) { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, "获取模型列表失败，请检查模型列表地址、凭据与权限")
            }
            val body = resp.body?.string().orEmpty()
            val json = runCatching { ProviderJson.parseToJsonElement(body) }
                .getOrElse { throw ApiException(-1, "响应不是合法 JSON") }
            when (config.type) {
                ProviderType.GEMINI -> parseGeminiModelCatalog(json.toString())
                else -> parseOpenAiStyleModelCatalog(json.toString())
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
        /**
         * OpenRouter exposes architecture.input_modalities. Some compatible
         * catalogues expose the same explicit field at the model root. Values
         * outside the known modality names deliberately remain disabled.
         */
        fun parseOpenAiStyleModelCatalog(json: String): ModelCatalog = runCatching {
            val root = ProviderJson.parseToJsonElement(json)
            val entries = (root as? kotlinx.serialization.json.JsonArray
                ?: (root as? kotlinx.serialization.json.JsonObject)?.get("data") as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { element ->
                    val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val id = (obj["id"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    id to capabilitiesFromExplicitModalities(obj)
                }
                ?.sortedBy { it.first }
                ?: emptyList()
            ModelCatalog(
                models = entries.map { it.first },
                discoveredCapabilities = entries.mapNotNull { (id, capabilities) ->
                    capabilities?.let { id to it }
                }.toMap()
            )
        }.getOrDefault(ModelCatalog(emptyList(), emptyMap()))

        /** OpenAI / Anthropic 风格：{"data":[{"id":"..."}]} */
        fun parseOpenAiStyleModels(json: String): List<String> =
            parseOpenAiStyleModelCatalog(json).models

        fun parseGeminiModelCatalog(json: String): ModelCatalog = runCatching {
            val entries = ProviderJson.parseToJsonElement(json).jsonObject["models"]?.jsonArray
                ?.mapNotNull { element ->
                    val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val methods = obj["supportedGenerationMethods"]?.jsonArray
                        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        ?: emptyList()
                    if ("generateContent" !in methods) return@mapNotNull null
                    name.removePrefix("models/") to capabilitiesFromExplicitModalities(obj)
                }
                ?.sortedBy { it.first }
                ?: emptyList()
            ModelCatalog(
                models = entries.map { it.first },
                discoveredCapabilities = entries.mapNotNull { (id, capabilities) ->
                    capabilities?.let { id to it }
                }.toMap()
            )
        }.getOrDefault(ModelCatalog(emptyList(), emptyMap()))

        /** Gemini 风格：{"models":[{"name":"models/gemini-2.5-flash",...}]}，只保留 generateContent 类模型 */
        fun parseGeminiModels(json: String): List<String> =
            parseGeminiModelCatalog(json).models

        /**
         * Anthropic's Models API explicitly declares image_input/pdf_input
         * support. OpenRouter declares input modalities. No model name or
         * generation method is used as a proxy for a native input feature.
         */
        private fun capabilitiesFromExplicitModalities(
            obj: kotlinx.serialization.json.JsonObject
        ): ModelCapabilities? {
            val architecture = obj["architecture"] as? kotlinx.serialization.json.JsonObject
            val nestedModalities = obj["modalities"] as? kotlinx.serialization.json.JsonObject
            val architectureModalityFields = listOf(architecture?.get("input_modalities"))
            val rootModalityFields = listOf(
                obj["input_modalities"],
                obj["inputModalities"],
                obj["supportedInputModalities"],
                obj["inputTypes"],
                nestedModalities?.get("input")
            )
            val modalityFields = architectureModalityFields + rootModalityFields
            val hasModalityField = modalityFields.any { it is kotlinx.serialization.json.JsonArray }
            fun modalities(fields: List<kotlinx.serialization.json.JsonElement?>): Set<String> =
                fields.asSequence().filterNotNull().flatMap { value ->
                    (value as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        ?.asSequence()
                        ?: emptySequence()
                }.map { it.lowercase() }.toSet()
            val architectureModalities = modalities(architectureModalityFields)
            val allModalities = modalities(modalityFields)
            val anthropicCapabilities = obj["capabilities"] as? kotlinx.serialization.json.JsonObject
            // Local gateways commonly use an explicit per-input boolean map.
            // Output modalities and generic attachment/tool flags are not input support.
            val inputCapabilities = anthropicCapabilities?.get("input") as? kotlinx.serialization.json.JsonObject
            val anthropicImage = anthropicCapabilities.booleanSupport("image_input")
            val anthropicPdf = anthropicCapabilities.booleanSupport("pdf_input")
            val image = anthropicImage ?: inputCapabilities.booleanValue("image")
                ?: anthropicCapabilities.booleanValue("vision")
            val pdf = anthropicPdf ?: inputCapabilities.booleanValue("pdf")
            val audio = inputCapabilities.booleanValue("audio")
            val video = inputCapabilities.booleanValue("video")
            if (!hasModalityField && listOf(image, pdf, audio, video).all { it == null }) return null
            return ModelCapabilities(
                image = image ?: ("image" in allModalities),
                // OpenRouter documents generic uploaded documents as "file";
                // this app currently offers native documents only for PDFs.
                pdf = pdf ?: ("pdf" in allModalities || "file" in architectureModalities),
                audio = audio ?: ("audio" in allModalities),
                video = video ?: ("video" in allModalities)
            )
        }

        private fun kotlinx.serialization.json.JsonObject?.booleanValue(key: String): Boolean? {
            val primitive = this?.get(key) as? kotlinx.serialization.json.JsonPrimitive ?: return null
            return if (primitive.isString) null else primitive.content.toBooleanStrictOrNull()
        }

        private fun kotlinx.serialization.json.JsonObject?.booleanSupport(key: String): Boolean? {
            val support = this?.get(key) as? kotlinx.serialization.json.JsonObject ?: return null
            val primitive = support["supported"] as? kotlinx.serialization.json.JsonPrimitive ?: return null
            return if (primitive.isString) null else primitive.content.toBooleanStrictOrNull()
        }
    }
}
