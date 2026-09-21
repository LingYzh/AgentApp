package com.example.myapplication.provider

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.ReasoningProtocol
import com.example.myapplication.data.model.TokenUsage
import com.example.myapplication.data.model.reasoningSupportFor
import com.example.myapplication.data.store.AttachmentStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString

/** Google Gemini generateContent API，SSE 流式（alt=sse）+ functionCall + thought（思考）parts。 */
class GeminiProvider(
    private val client: OkHttpClient,
    private val attachmentStore: AttachmentStore? = null
) : ApiProvider {

    override suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    ) {
        val store = attachmentStoreFor(attachmentStore, config, messages)
        val base = config.baseUrl.trimEnd('/')
        val rawUrl = when {
            base.contains(":streamGenerateContent") -> base
            base.endsWith("/v1beta") -> "$base/models/${config.model}:streamGenerateContent?alt=sse"
            else -> "$base/v1beta/models/${config.model}:streamGenerateContent?alt=sse"
        }
        val urlBuilder = rawUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw ApiException(-1, "非法的 Gemini Base URL: $rawUrl")
        if (!rawUrl.contains("alt=sse")) urlBuilder.addQueryParameter("alt", "sse")
        if (config.apiKey.isNotBlank()) urlBuilder.addQueryParameter("key", config.apiKey)

        val body = buildRequestBody(config, system, messages, tools, store)

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
        config.extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val parser = GeminiStreamParser()
        Sse.post(client, requestBuilder.build()) { data ->
            parser.parse(data).forEach { onEvent(it) }
        }
        parser.finish().forEach { onEvent(it) }
    }

    internal fun buildRequestBody(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        store: AttachmentStore? = null
    ): JsonObject {
        require(config.maxOutputTokens == null || config.maxOutputTokens > 0) {
            "maxOutputTokens must be positive"
        }
        val support = config.reasoningEffort?.let { reasoningSupportFor(config.type, config.model) }
        config.reasoningEffort?.let { effort ->
            require(support?.protocol in setOf(
                ReasoningProtocol.GEMINI_THINKING_LEVEL,
                ReasoningProtocol.GEMINI_THINKING_BUDGET
            ) && effort in requireNotNull(support).efforts) { support?.description ?: "不支持思考参数" }
        }
        return buildJsonObject {
            if (system.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", system) }
                    }
                }
            }
            putJsonArray("contents") {
                toWireContents(messages, store).forEach { add(it) }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { t ->
                                addJsonObject {
                                    put("name", t.name)
                                    put("description", t.description)
                                    put("parameters", t.parameters)
                                }
                            }
                        }
                    }
                }
            }
            if (config.temperature != null || config.reasoningEffort != null || config.maxOutputTokens != null) {
                putJsonObject("generationConfig") {
                    config.temperature?.let { put("temperature", it) }
                    config.maxOutputTokens?.takeIf { it > 0 }?.let { put("maxOutputTokens", it) }
                    when (support?.protocol) {
                        ReasoningProtocol.GEMINI_THINKING_LEVEL -> putJsonObject("thinkingConfig") {
                            put("thinkingLevel", requireNotNull(config.reasoningEffort).wireValue)
                        }
                        ReasoningProtocol.GEMINI_THINKING_BUDGET -> putJsonObject("thinkingConfig") {
                            put("thinkingBudget", geminiBudgetFor(config.model, requireNotNull(config.reasoningEffort)))
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * 转为 Gemini contents：
     * - assistant → role "model"，text parts + functionCall parts
     * - 连续 tool 结果合并为一条 user content 的 functionResponse parts
     */
    internal fun toWireContents(messages: List<ChatMessage>, store: AttachmentStore?): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            when (m.role) {
                "user" -> out += buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", wireMessageText(store, m)) }
                        nativeAttachments(m).forEach { attachment ->
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", attachment.mimeType)
                                    put("data", requireNotNull(store).readBytes(attachment).toByteString().base64())
                                }
                            }
                        }
                    }
                }
                "assistant" -> {
                    val providerBlocks = m.providerBlocks[GEMINI_BLOCKS_KEY]
                    if (!providerBlocks.isNullOrEmpty()) {
                        // Thought signatures remain opaque and are replayed only to Gemini.
                        out += buildJsonObject {
                            put("role", "model")
                            put("parts", buildJsonArray { providerBlocks.forEach { add(it) } })
                        }
                    } else out += buildJsonObject {
                        put("role", "model")
                        putJsonArray("parts") {
                            if (m.content.isNotBlank()) {
                                addJsonObject { put("text", wireMessageText(store, m)) }
                            }
                            m.toolCalls.forEach { tc ->
                                addJsonObject {
                                    putJsonObject("functionCall") {
                                        put("name", tc.name)
                                        put("args", runCatching {
                                            ProviderJson.parseToJsonElement(tc.argumentsJson).jsonObject
                                        }.getOrDefault(buildJsonObject {}))
                                    }
                                }
                            }
                        }
                    }
                }
                "tool" -> {
                    val parts = buildJsonArray {
                        var j = i
                        while (j < messages.size && messages[j].role == "tool") {
                            val t = messages[j]
                            addJsonObject {
                                putJsonObject("functionResponse") {
                                    put("name", t.toolName ?: "")
                                    putJsonObject("response") {
                                        put("result", wireMessageText(store, t))
                                    }
                                }
                            }
                            j++
                        }
                        i = j - 1
                    }
                    out += buildJsonObject {
                        put("role", "user")
                        put("parts", parts)
                    }
                }
            }
            i++
        }
        return out
    }

    companion object {
        internal const val GEMINI_BLOCKS_KEY = "gemini"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** App-level budget mapping for Gemini 2.5; Gemini 3 uses named thinkingLevel instead. */
internal fun geminiBudgetFor(modelId: String, effort: ReasoningEffort): Int = when (effort) {
    ReasoningEffort.NONE -> 0
    ReasoningEffort.MINIMAL -> if (modelId.lowercase().startsWith("gemini-2.5-flash-lite")) 512 else 1_024
    ReasoningEffort.LOW -> 4_096
    ReasoningEffort.MEDIUM -> 8_192
    ReasoningEffort.HIGH -> if (modelId.lowercase().startsWith("gemini-2.5-pro")) 32_768 else 24_576
    ReasoningEffort.XHIGH, ReasoningEffort.MAX -> error("Gemini 2.5 does not expose this effort level")
}

/** 解析 Gemini SSE 流（candidates/parts 模型）。functionCall 在 part 中一次性给出，无需累积。 */
class GeminiStreamParser {

    private var counter = 0
    private var finishReason: String? = null
    private val providerParts = mutableListOf<JsonObject>()

    private fun snapshotEvent(): StreamEvent.ProviderBlocks? = providerParts
        .takeIf { it.isNotEmpty() }
        ?.let { StreamEvent.ProviderBlocks(GeminiProvider.GEMINI_BLOCKS_KEY, it.toList()) }

    fun parse(data: String): List<StreamEvent> {
        val root = runCatching { ProviderJson.parseToJsonElement(data).jsonObject }
            .getOrNull() ?: return emptyList()
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.content ?: "未知错误"
            return listOf(StreamEvent.Error(msg))
        }
        val events = mutableListOf<StreamEvent>()
        root["usageMetadata"]?.jsonObject?.let { metadata ->
            val thoughtTokens = metadata.longValue("thoughtsTokenCount")
            events += StreamEvent.Usage(
                TokenUsage(
                    inputTokens = metadata.longValue("promptTokenCount"),
                    // Gemini reports candidate text and thoughts separately; normalize output
                    // to include reasoning, like OpenAI's completion_tokens.
                    outputTokens = metadata.longValue("candidatesTokenCount")?.let { it + (thoughtTokens ?: 0L) },
                    cacheReadTokens = metadata.longValue("cachedContentTokenCount"),
                    reasoningTokens = thoughtTokens
                )
            )
        }
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return events
        candidate["finishReason"]?.jsonPrimitive?.content?.let { finishReason = it }
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return events
        parts.forEach { partEl ->
            val part = partEl.jsonObject
            // REST history must preserve every part and its position, including an empty
            // text part that only carries a thoughtSignature after a tool call.
            providerParts += part
            part["functionCall"]?.jsonObject?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.content ?: return@let
                val args = fc["args"]?.toString() ?: "{}"
                events += StreamEvent.ToolCall(
                    id = "gemini_${counter++}_$name",
                    name = name,
                    argumentsJson = args
                )
                return@forEach
            }
            val text = (part["text"] as? JsonPrimitive)?.content ?: return@forEach
            if (text.isEmpty()) return@forEach
            val isThought = (part["thought"] as? JsonPrimitive)?.content == "true"
            events += if (isThought) StreamEvent.Thinking(text) else StreamEvent.Text(text)
        }
        snapshotEvent()?.let { events += it }
        return events
    }

    fun finish(): List<StreamEvent> = listOfNotNull(snapshotEvent(), StreamEvent.Done(finishReason))
}
