package com.example.myapplication.provider

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.TokenUsage
import com.example.myapplication.data.store.AttachmentStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString

/** OpenAI Chat Completions 兼容（含 DeepSeek、通义、Ollama、vLLM 等），SSE 流式 + tool_calls + reasoning_content。 */
class OpenAiProvider(
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
        val url = config.baseUrl.trimEnd('/').let {
            if (it.endsWith("/chat/completions")) it else "$it/chat/completions"
        }
        val body = buildRequestBody(config, system, messages, tools, store)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }
        config.extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val parser = OpenAiStreamParser()
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
        return buildJsonObject {
            put("model", config.model)
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            config.temperature?.let { put("temperature", it) }
            // Do not impose an app-side default: compatible services have materially different
            // completion limits. An explicit, positive user cap is forwarded unchanged.
            config.maxOutputTokens?.takeIf { it > 0 }?.let { put("max_tokens", it) }
            // Compatibility gateways often use their own model aliases. Forward explicit choices
            // verbatim and let their API report unsupported combinations.
            config.reasoningEffort?.let { put("reasoning_effort", it.wireValue) }
            putJsonArray("messages") {
                if (system.isNotBlank()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                }
                messages.forEach { add(toWireMessage(it, store)) }
            }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { t ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", t.parameters)
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun toWireMessage(m: ChatMessage, store: AttachmentStore?): JsonObject = buildJsonObject {
        when (m.role) {
            "user" -> {
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
                            val dataUrl = "data:${attachment.mimeType};base64," +
                                requireNotNull(store).readBytes(attachment).toByteString().base64()
                            if (attachment.mimeType.startsWith("image/")) {
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", dataUrl) }
                                }
                            } else if (attachment.mimeType == "application/pdf") {
                                addJsonObject {
                                    put("type", "file")
                                    putJsonObject("file") {
                                        put("filename", attachment.name)
                                        put("file_data", dataUrl)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "assistant" -> {
                put("role", "assistant")
                put("content", wireMessageText(store, m))
                if (m.toolCalls.isNotEmpty()) {
                    putJsonArray("tool_calls") {
                        m.toolCalls.forEach { tc ->
                            addJsonObject {
                                put("id", tc.id)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", tc.name)
                                    put("arguments", tc.argumentsJson)
                                }
                            }
                        }
                    }
                }
            }
            "tool" -> {
                put("role", "tool")
                put("tool_call_id", m.toolCallId ?: "")
                put("content", wireMessageText(store, m))
            }
            else -> {
                put("role", "user")
                put("content", wireMessageText(store, m))
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** 解析 OpenAI 兼容 SSE 流，内部累积 tool_calls 分片。 */
class OpenAiStreamParser {

    private class ToolCallAcc {
        var id: String = ""
        val name = StringBuilder()
        val args = StringBuilder()
    }

    private val accs = sortedMapOf<Int, ToolCallAcc>()
    private var finishReason: String? = null

    fun parse(data: String): List<StreamEvent> {
        val root = runCatching { ProviderJson.parseToJsonElement(data) as? JsonObject }
            .getOrNull() ?: return emptyList()
        val events = mutableListOf<StreamEvent>()
        (root["usage"] as? JsonObject)?.let(::usageEvent)?.let { events += it }
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return events
        (choice["finish_reason"] as? JsonPrimitive)?.takeIf { it.isString }
            ?.let { finishReason = it.content }
        val delta = choice["delta"] as? JsonObject ?: return events
        delta["content"]?.let { c ->
            if (c is JsonPrimitive && c.isString && c.content.isNotEmpty()) {
                events += StreamEvent.Text(c.content)
            }
        }
        delta["reasoning_content"]?.let { c ->
            if (c is JsonPrimitive && c.isString && c.content.isNotEmpty()) {
                events += StreamEvent.Thinking(c.content)
            }
        }
        (delta["tool_calls"] as? JsonArray)?.forEach { tcEl ->
            val tc = tcEl as? JsonObject ?: return@forEach
            val index = (tc["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            val acc = accs.getOrPut(index) { ToolCallAcc() }
            (tc["id"] as? JsonPrimitive)?.contentOrNullSafe()?.let { acc.id = it }
            (tc["function"] as? JsonObject)?.let { fn ->
                (fn["name"] as? JsonPrimitive)?.contentOrNullSafe()?.let { acc.name.append(it) }
                (fn["arguments"] as? JsonPrimitive)?.contentOrNullSafe()?.let { acc.args.append(it) }
            }
        }
        return events
    }

    private fun usageEvent(usage: JsonObject): StreamEvent.Usage = StreamEvent.Usage(
        TokenUsage(
            inputTokens = usage.longValue("prompt_tokens"),
            outputTokens = usage.longValue("completion_tokens"),
            cacheReadTokens = (usage["prompt_tokens_details"] as? JsonObject)?.longValue("cached_tokens"),
            cacheWriteTokens = (usage["prompt_tokens_details"] as? JsonObject)?.longValue("cache_creation_tokens"),
            reasoningTokens = (usage["completion_tokens_details"] as? JsonObject)?.longValue("reasoning_tokens")
        )
    )

    /** 流结束时调用：发出完整工具调用 + Done。 */
    fun finish(): List<StreamEvent> {
        val events = accs.values
            .filter { it.name.isNotEmpty() }
            .map { acc ->
                StreamEvent.ToolCall(
                    id = acc.id.ifEmpty { "call_${acc.hashCode()}" },
                    name = acc.name.toString(),
                    argumentsJson = acc.args.toString().ifEmpty { "{}" }
                )
            }
        accs.clear()
        return events + StreamEvent.Done(finishReason)
    }
}

internal fun JsonObject.longValue(name: String): Long? =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull()

internal fun JsonPrimitive.contentOrNullSafe(): String? =
    if (isString) content else null
