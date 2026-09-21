package com.example.myapplication.provider

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.ReasoningProtocol
import com.example.myapplication.data.model.TokenUsage
import com.example.myapplication.data.model.anthropicThinkingProtocol
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString

/** Anthropic Messages API，SSE 流式 + tool_use + thinking blocks。 */
class AnthropicProvider(
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
        val url = when {
            base.endsWith("/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
        val body = buildRequestBody(config, system, messages, tools, store)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("x-api-key", config.apiKey)
        }
        config.extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val parser = AnthropicStreamParser()
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
        val effort = config.reasoningEffort
        val thinkingProtocol = effort
            ?.takeIf { it != ReasoningEffort.NONE }
            ?.let { anthropicThinkingProtocol(config.model, config.anthropicThinkingMode) }
        val manualBudget = effort?.takeIf {
            thinkingProtocol == ReasoningProtocol.ANTHROPIC_MANUAL
        }?.let(::anthropicBudgetFor)
        val maxTokens = config.maxOutputTokens ?: DEFAULT_ANTHROPIC_MAX_TOKENS
        require(maxTokens > 0) { "maxOutputTokens must be positive" }
        require(manualBudget == null || maxTokens > manualBudget) {
            "maxOutputTokens ($maxTokens) must exceed the manual thinking budget ($manualBudget)"
        }
        return buildJsonObject {
            put("model", config.model)
            // Anthropic requires max_tokens. It counts thinking and visible output together,
            // therefore the fallback must leave enough headroom for high reasoning settings.
            put("max_tokens", maxTokens)
            put("stream", true)
            // Thinking requests reject temperature. Preserve the saved setting for later modes,
            // but do not send an invalid combination when a conversation override enables effort.
            config.temperature?.takeIf { effort == null || effort == ReasoningEffort.NONE }
                ?.let { put("temperature", it) }
            when {
                effort == ReasoningEffort.NONE -> putJsonObject("thinking") {
                    put("type", "disabled")
                }
                thinkingProtocol == ReasoningProtocol.ANTHROPIC_MANUAL -> putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", requireNotNull(manualBudget))
                }
                thinkingProtocol == ReasoningProtocol.ANTHROPIC_ADAPTIVE -> {
                    putJsonObject("thinking") { put("type", "adaptive") }
                    putJsonObject("output_config") {
                        put("effort", requireNotNull(effort).wireValue)
                    }
                }
                else -> Unit
            }
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                toWireMessages(messages, store).forEach { add(it) }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.parameters)
                        }
                    }
                }
            }
        }
    }

    /**
     * 转为 Anthropic 消息格式：
     * - assistant 的工具调用转 tool_use content blocks
     * - 连续的 tool 结果合并为一条 user 消息（tool_result blocks）
     */
    internal fun toWireMessages(messages: List<ChatMessage>, store: AttachmentStore?): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            when (m.role) {
                "user" -> out += buildJsonObject {
                    put("role", "user")
                    val attachments = nativeAttachments(m)
                    if (attachments.isEmpty()) {
                        put("content", wireMessageText(store, m))
                    } else {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", wireMessageText(store, m))
                            }
                            attachments.forEach { attachment ->
                                val data = requireNotNull(store).readBytes(attachment).toByteString().base64()
                                if (attachment.mimeType.startsWith("image/")) {
                                    addJsonObject {
                                        put("type", "image")
                                        putJsonObject("source") {
                                            put("type", "base64")
                                            put("media_type", attachment.mimeType)
                                            put("data", data)
                                        }
                                    }
                                } else if (attachment.mimeType == "application/pdf") {
                                    addJsonObject {
                                        put("type", "document")
                                        putJsonObject("source") {
                                            put("type", "base64")
                                            put("media_type", attachment.mimeType)
                                            put("data", data)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "assistant" -> {
                    val providerBlocks = m.providerBlocks[ANTHROPIC_BLOCKS_KEY]
                    if (!providerBlocks.isNullOrEmpty()) {
                        // Signatures and redacted thinking are opaque: same-protocol replay only.
                        out += buildJsonObject {
                            put("role", "assistant")
                            put("content", buildJsonArray { providerBlocks.forEach { add(it) } })
                        }
                    } else out += buildJsonObject {
                        put("role", "assistant")
                        putJsonArray("content") {
                            if (m.content.isNotBlank()) {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", wireMessageText(store, m))
                                }
                            }
                            m.toolCalls.forEach { tc ->
                                addJsonObject {
                                    put("type", "tool_use")
                                    put("id", tc.id)
                                    put("name", tc.name)
                                    put("input", runCatching {
                                        ProviderJson.parseToJsonElement(tc.argumentsJson).jsonObject
                                    }.getOrDefault(buildJsonObject {}))
                                }
                            }
                        }
                    }
                }
                "tool" -> {
                    // 合并连续 tool 消息为一条 user 消息
                    val results = buildJsonArray {
                        var j = i
                        while (j < messages.size && messages[j].role == "tool") {
                            val t = messages[j]
                            addJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", t.toolCallId ?: "")
                                put("content", wireMessageText(store, t))
                                if (t.isError) put("is_error", true)
                            }
                            j++
                        }
                        i = j - 1
                    }
                    out += buildJsonObject {
                        put("role", "user")
                        put("content", results)
                    }
                }
            }
            i++
        }
        return out
    }

    companion object {
        internal const val ANTHROPIC_BLOCKS_KEY = "anthropic"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Required by the Anthropic Messages API; applies only when the user left the cap empty. */
internal const val DEFAULT_ANTHROPIC_MAX_TOKENS = 65_536

/** App-level fixed budgets; the UI shows the exact number before the request is sent. */
internal fun anthropicBudgetFor(effort: ReasoningEffort): Int = when (effort) {
    ReasoningEffort.MINIMAL -> 1_024
    ReasoningEffort.LOW -> 2_048
    ReasoningEffort.MEDIUM -> 4_096
    ReasoningEffort.HIGH -> 8_192
    ReasoningEffort.XHIGH -> 16_384
    ReasoningEffort.MAX -> 32_768
    ReasoningEffort.NONE -> error("Manual Anthropic thinking cannot use none")
}

/** 解析 Anthropic SSE 流（content_block 事件模型）。 */
class AnthropicStreamParser {

    private class BlockState(
        val initial: JsonObject,
        val type: String,
        val id: String = "",
        val name: String = "",
        val text: StringBuilder = StringBuilder(),
        val args: StringBuilder = StringBuilder(),
        val signature: StringBuilder = StringBuilder()
    ) {
        fun completed(): JsonObject = buildJsonObject {
            initial.forEach { (key, value) -> put(key, value) }
            when (type) {
                "text" -> put("text", initial["text"]?.jsonPrimitive?.content.orEmpty() + text)
                "thinking" -> {
                    put("thinking", initial["thinking"]?.jsonPrimitive?.content.orEmpty() + text)
                    (initial["signature"]?.jsonPrimitive?.content.orEmpty() + signature)
                        .takeIf { it.isNotEmpty() }
                        ?.let { put("signature", it) }
                }
                "tool_use" -> if (args.isNotEmpty()) {
                    runCatching {
                        ProviderJson.parseToJsonElement(args.toString()).jsonObject
                    }.getOrNull()?.let { put("input", it) }
                }
            }
        }
    }

    private val blocks = mutableMapOf<Int, BlockState>()
    private val completedBlocks = sortedMapOf<Int, JsonObject>()
    private var stopReason: String? = null
    private var rawInputTokens: Long? = null
    private var rawCacheReadTokens: Long? = null
    private var rawCacheWriteTokens: Long? = null
    private var rawOutputTokens: Long? = null
    private var rawReasoningTokens: Long? = null

    private fun providerBlocksEvent(): StreamEvent.ProviderBlocks? =
        completedBlocks.values.takeIf { it.isNotEmpty() }
            ?.let { StreamEvent.ProviderBlocks(AnthropicProvider.ANTHROPIC_BLOCKS_KEY, it.toList()) }

    fun parse(data: String): List<StreamEvent> {
        val root = runCatching { ProviderJson.parseToJsonElement(data).jsonObject }
            .getOrNull() ?: return emptyList()
        val type = root["type"]?.jsonPrimitive?.content ?: return emptyList()
        val events = mutableListOf<StreamEvent>()
        when (type) {
            "message_start" -> {
                root["message"]?.jsonObject?.get("usage")?.jsonObject?.let { wire ->
                    updateUsage(wire)
                    events += StreamEvent.Usage(currentUsage())
                }
            }
            "content_block_start" -> {
                val index = root["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val block = root["content_block"]?.jsonObject
                val blockType = block?.get("type")?.jsonPrimitive?.content ?: ""
                if (blockType == "tool_use") {
                    blocks[index] = BlockState(
                        initial = block ?: buildJsonObject {},
                        type = "tool_use",
                        id = block?.get("id")?.jsonPrimitive?.content ?: "",
                        name = block?.get("name")?.jsonPrimitive?.content ?: ""
                    )
                } else {
                    blocks[index] = BlockState(initial = block ?: buildJsonObject {}, type = blockType)
                    when (blockType) {
                        "text" -> block?.get("text")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                            ?.let { events += StreamEvent.Text(it) }
                        "thinking" -> block?.get("thinking")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                            ?.let { events += StreamEvent.Thinking(it) }
                    }
                }
            }
            "content_block_delta" -> {
                val index = root["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val delta = root["delta"]?.jsonObject ?: return events
                when (delta["type"]?.jsonPrimitive?.content) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotEmpty() }?.let {
                            blocks[index]?.text?.append(it)
                            events += StreamEvent.Text(it)
                        }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotEmpty() }?.let {
                            blocks[index]?.text?.append(it)
                            events += StreamEvent.Thinking(it)
                        }
                    "signature_delta" -> delta["signature"]?.jsonPrimitive?.content
                        ?.let { blocks[index]?.signature?.append(it) }
                    "input_json_delta" -> delta["partial_json"]?.jsonPrimitive?.content
                        ?.let { blocks[index]?.args?.append(it) }
                }
            }
            "content_block_stop" -> {
                val index = root["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                blocks.remove(index)?.let { b ->
                    val completed = b.completed()
                    completedBlocks[index] = completed
                    if (b.type == "tool_use" && b.name.isNotEmpty()) {
                        events += StreamEvent.ToolCall(
                            id = b.id.ifEmpty { "toolu_$index" },
                            name = b.name,
                            argumentsJson = if (b.args.isNotEmpty()) b.args.toString()
                                else completed["input"]?.toString() ?: "{}"
                        )
                    }
                    providerBlocksEvent()?.let { events += it }
                }
            }
            "message_delta" -> {
                stopReason = root["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.content
                root["usage"]?.jsonObject?.let { wire ->
                    updateUsage(wire)
                    events += StreamEvent.Usage(currentUsage())
                }
            }
            "error" -> {
                val msg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "未知错误"
                events += StreamEvent.Error(msg)
            }
        }
        return events
    }

    /** Each SSE usage object can be partial, so preserve its raw counters before summing input. */
    private fun updateUsage(wire: JsonObject) {
        wire.longValue("input_tokens")?.let { rawInputTokens = it }
        wire.longValue("cache_read_input_tokens")?.let { rawCacheReadTokens = it }
        wire.longValue("cache_creation_input_tokens")?.let { rawCacheWriteTokens = it }
        wire.longValue("output_tokens")?.let { rawOutputTokens = it }
        wire["output_tokens_details"]?.jsonObject?.longValue("thinking_tokens")
            ?.let { rawReasoningTokens = it }
    }

    private fun currentUsage(): TokenUsage = TokenUsage(
        inputTokens = totalInputTokens(rawInputTokens, rawCacheReadTokens, rawCacheWriteTokens),
        outputTokens = rawOutputTokens,
        cacheReadTokens = rawCacheReadTokens,
        cacheWriteTokens = rawCacheWriteTokens,
        reasoningTokens = rawReasoningTokens
    )

    private fun totalInputTokens(vararg values: Long?): Long? =
        values.takeIf { it.any { value -> value != null } }?.sumOf { it ?: 0L }

    fun finish(): List<StreamEvent> {
        // A thinking block is valid for replay only after its final signature delta. Do not
        // manufacture an apparently valid tool input from an interrupted JSON fragment.
        val leftover = blocks.entries.mapNotNull { (index, b) ->
            val completed = b.completed()
            when (b.type) {
                "thinking" -> {
                    // Without block_stop even a nonempty signature may only be a fragment.
                    StreamEvent.Error("模型思考流未完整结束，请重试本轮请求")
                }
                "tool_use" -> {
                    val initialInput = b.initial["input"] as? JsonObject
                    val parsedDelta = b.args.takeIf { it.isNotEmpty() }?.let {
                        runCatching { ProviderJson.parseToJsonElement(it.toString()).jsonObject }.getOrNull()
                    }
                    val input = if (b.args.isNotEmpty()) parsedDelta else initialInput?.takeIf { it.isNotEmpty() }
                    if (input != null) {
                        completedBlocks[index] = completed
                        StreamEvent.ToolCall(
                            id = b.id.ifEmpty { "toolu_${b.name}" },
                            name = b.name,
                            argumentsJson = input.toString()
                        )
                    } else if (b.args.isNotEmpty() && b.name.isNotEmpty()) {
                        StreamEvent.Error("模型工具参数流不完整，未执行该工具")
                    } else null
                }
                else -> {
                    completedBlocks[index] = completed
                    null
                }
            }
        }
        blocks.clear()
        val snapshot = providerBlocksEvent()?.let { listOf(it) }.orEmpty()
        completedBlocks.clear()
        return leftover + snapshot + StreamEvent.Done(stopReason)
    }
}
