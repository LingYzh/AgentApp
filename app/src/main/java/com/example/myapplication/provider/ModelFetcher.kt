package com.example.myapplication.provider

import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.ModelCapabilities
import com.example.myapplication.data.model.ModelMetadata
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Missing metadata remains unknown; attachment delivery defaults conservatively. */
data class ModelCatalog(
    val models: List<String>,
    val discoveredCapabilities: Map<String, ModelCapabilities>,
    val discoveredModelMetadata: Map<String, ModelMetadata> = emptyMap()
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
                    Triple(id, capabilitiesFromExplicitModalities(obj), metadataFromModelObject(obj))
                }
                ?.sortedBy { it.first }
                ?: emptyList()
            ModelCatalog(
                models = entries.map { it.first },
                discoveredCapabilities = entries.mapNotNull { (id, capabilities, _) ->
                    capabilities?.let { id to it }
                }.toMap(),
                discoveredModelMetadata = entries.associate { (id, _, metadata) -> id to metadata }
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
                    Triple(name.removePrefix("models/"), capabilitiesFromExplicitModalities(obj), metadataFromModelObject(obj))
                }
                ?.sortedBy { it.first }
                ?: emptyList()
            ModelCatalog(
                models = entries.map { it.first },
                discoveredCapabilities = entries.mapNotNull { (id, capabilities, _) ->
                    capabilities?.let { id to it }
                }.toMap(),
                discoveredModelMetadata = entries.associate { (id, _, metadata) -> id to metadata }
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

        private fun metadataFromModelObject(
            obj: kotlinx.serialization.json.JsonObject
        ): ModelMetadata {
            val capabilitiesObj = obj["capabilities"] as? kotlinx.serialization.json.JsonObject
            val limitObj = obj["limit"] as? kotlinx.serialization.json.JsonObject
            val architectureObj = obj["architecture"] as? kotlinx.serialization.json.JsonObject
            val modalitiesObj = obj["modalities"] as? kotlinx.serialization.json.JsonObject

            val reasoning = capabilitiesObj.booleanValue("reasoning")
                ?: obj.booleanValue("reasoning")
                ?: obj.booleanValue("supportsThinking")

            val temperature = capabilitiesObj.booleanValue("temperature")
                ?: obj.booleanValue("temperature")

            val toolCall = capabilitiesObj.booleanValue("toolcall")
                ?: capabilitiesObj.booleanValue("tool_call")
                ?: capabilitiesObj.booleanValue("toolCall")
                ?: obj.booleanValue("tool_call")
                ?: obj.booleanValue("toolCall")
                ?: obj.booleanValue("toolcall")

            val promptCaching = capabilitiesObj.booleanValue("promptCaching")
                ?: capabilitiesObj.booleanValue("prompt_caching")
                ?: capabilitiesObj.booleanValue("supportsPromptCaching")
                ?: obj.booleanValue("supportsPromptCaching")
                ?: obj.booleanValue("promptCaching")
                ?: obj.booleanValue("prompt_caching")

            val contextWindow = limitObj?.get("context").positiveInt()
                ?: limitObj?.get("context_window").positiveInt()
                ?: limitObj?.get("context_length").positiveInt()
                ?: obj["context_length"].positiveInt()
                ?: obj["contextLength"].positiveInt()
                ?: obj["context_window"].positiveInt()
                ?: obj["contextWindow"].positiveInt()
                ?: obj["max_context_tokens"].positiveInt()
                ?: obj["inputTokenLimit"].positiveInt()

            val maxInputTokens = limitObj?.get("input").positiveInt()
                ?: limitObj?.get("input_tokens").positiveInt()
                ?: limitObj?.get("max_input_tokens").positiveInt()
                ?: obj["max_input_tokens"].positiveInt()
                ?: obj["maxInputTokens"].positiveInt()
                ?: obj["inputTokenLimit"].positiveInt()

            val maxOutputTokens = limitObj?.get("output").positiveInt()
                ?: limitObj?.get("output_tokens").positiveInt()
                ?: limitObj?.get("max_output_tokens").positiveInt()
                ?: obj["max_output_tokens"].positiveInt()
                ?: obj["maxOutputTokens"].positiveInt()
                ?: obj["max_tokens"].positiveInt()
                ?: obj["maxTokens"].positiveInt()
                ?: obj["outputTokenLimit"].positiveInt()

            val effortsElement = capabilitiesObj?.get("thinkingEfforts").toJsonArray()
                ?: capabilitiesObj?.get("reasoningEfforts").toJsonArray()
                ?: capabilitiesObj?.get("reasoning_efforts").toJsonArray()
                ?: obj["thinkingEfforts"].toJsonArray()
                ?: obj["reasoningEfforts"].toJsonArray()
                ?: obj["reasoning_efforts"].toJsonArray()
            val reasoningEfforts = effortsElement?.toStringList()

            val inputModElement = modalitiesObj?.get("input").toJsonArray()
                ?: architectureObj?.get("input_modalities").toJsonArray()
                ?: obj["input_modalities"].toJsonArray()
                ?: obj["inputModalities"].toJsonArray()
                ?: obj["supportedInputModalities"].toJsonArray()
                ?: obj["inputTypes"].toJsonArray()
            val inputModalities = inputModElement?.toStringList()

            val outputModElement = modalitiesObj?.get("output").toJsonArray()
                ?: architectureObj?.get("output_modalities").toJsonArray()
                ?: obj["output_modalities"].toJsonArray()
                ?: obj["outputModalities"].toJsonArray()
                ?: obj["supportedOutputModalities"].toJsonArray()
                ?: obj["outputTypes"].toJsonArray()
            val outputModalities = outputModElement?.toStringList()

            return ModelMetadata(
                contextWindow = contextWindow,
                maxInputTokens = maxInputTokens,
                maxOutputTokens = maxOutputTokens,
                reasoning = reasoning,
                temperature = temperature,
                toolCall = toolCall,
                promptCaching = promptCaching,
                reasoningEfforts = reasoningEfforts,
                inputModalities = inputModalities,
                outputModalities = outputModalities,
                raw = obj
            )
        }

        private fun kotlinx.serialization.json.JsonElement?.toJsonArray(): kotlinx.serialization.json.JsonArray? =
            this as? kotlinx.serialization.json.JsonArray

        private fun kotlinx.serialization.json.JsonArray.toStringList(): List<String> =
            mapNotNull { element ->
                val primitive = element as? kotlinx.serialization.json.JsonPrimitive
                if (primitive != null && primitive.isString) primitive.content else null
            }

        private fun kotlinx.serialization.json.JsonElement?.positiveInt(): Int? {
            val primitive = this as? kotlinx.serialization.json.JsonPrimitive ?: return null
            val num = primitive.content.toLongOrNull() ?: return null
            return if (num > 0 && num <= Int.MAX_VALUE) num.toInt() else null
        }
    }
}
