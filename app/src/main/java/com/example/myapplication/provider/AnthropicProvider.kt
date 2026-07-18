package com.example.myapplication.provider

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
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

/** Anthropic Messages API，SSE 流式 + tool_use + thinking blocks。 */
class AnthropicProvider(private val client: OkHttpClient) : ApiProvider {

    override suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    ) {
        val base = config.baseUrl.trimEnd('/')
        val url = when {
            base.endsWith("/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 8192)
            put("stream", true)
            config.temperature?.let { put("temperature", it) }
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                toWireMessages(messages).forEach { add(it) }
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

    /**
     * 转为 Anthropic 消息格式：
     * - assistant 的工具调用转 tool_use content blocks
     * - 连续的 tool 结果合并为一条 user 消息（tool_result blocks）
     */
    private fun toWireMessages(messages: List<ChatMessage>): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            when (m.role) {
                "user" -> out += buildJsonObject {
                    put("role", "user")
                    put("content", m.content)
                }
                "assistant" -> out += buildJsonObject {
                    put("role", "assistant")
                    putJsonArray("content") {
                        if (m.content.isNotBlank()) {
                            addJsonObject {
                                put("type", "text")
                                put("text", m.content)
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
                "tool" -> {
                    // 合并连续 tool 消息为一条 user 消息
                    val results = buildJsonArray {
                        var j = i
                        while (j < messages.size && messages[j].role == "tool") {
                            val t = messages[j]
                            addJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", t.toolCallId ?: "")
                                put("content", t.content)
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
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** 解析 Anthropic SSE 流（content_block 事件模型）。 */
class AnthropicStreamParser {

    private class BlockState(
        val type: String,
        val id: String = "",
        val name: String = "",
        val args: StringBuilder = StringBuilder()
    )

    private val blocks = mutableMapOf<Int, BlockState>()
    private var stopReason: String? = null
    private var done = false

    fun parse(data: String): List<StreamEvent> {
        val root = runCatching { ProviderJson.parseToJsonElement(data).jsonObject }
            .getOrNull() ?: return emptyList()
        val type = root["type"]?.jsonPrimitive?.content ?: return emptyList()
        val events = mutableListOf<StreamEvent>()
        when (type) {
            "content_block_start" -> {
                val index = root["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val block = root["content_block"]?.jsonObject
                val blockType = block?.get("type")?.jsonPrimitive?.content ?: ""
                if (blockType == "tool_use") {
                    blocks[index] = BlockState(
                        type = "tool_use",
                        id = block?.get("id")?.jsonPrimitive?.content ?: "",
                        name = block?.get("name")?.jsonPrimitive?.content ?: ""
                    )
                } else {
                    blocks[index] = BlockState(type = blockType)
                }
            }
            "content_block_delta" -> {
                val index = root["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val delta = root["delta"]?.jsonObject ?: return events
                when (delta["type"]?.jsonPrimitive?.content) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotEmpty() }?.let { events += StreamEvent.Text(it) }
                    "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotEmpty() }?.let { events += StreamEvent.Thinking(it) }
                    "input_json_delta" -> delta["partial_json"]?.jsonPrimitive?.content
                        ?.let { blocks[index]?.args?.append(it) }
                }
            }
            "content_block_stop" -> {
                val index = root["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                blocks.remove(index)?.let { b ->
                    if (b.type == "tool_use" && b.name.isNotEmpty()) {
                        events += StreamEvent.ToolCall(
                            id = b.id.ifEmpty { "toolu_$index" },
                            name = b.name,
                            argumentsJson = b.args.toString().ifEmpty { "{}" }
                        )
                    }
                }
            }
            "message_delta" -> {
                stopReason = root["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.content
            }
            "message_stop" -> done = true
            "error" -> {
                val msg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: "未知错误"
                events += StreamEvent.Error(msg)
            }
        }
        return events
    }

    fun finish(): List<StreamEvent> {
        // 防御：未正常 stop 的 tool_use 块也补发
        val leftover = blocks.values
            .filter { it.type == "tool_use" && it.name.isNotEmpty() }
            .map { b ->
                StreamEvent.ToolCall(
                    id = b.id.ifEmpty { "toolu_${b.name}" },
                    name = b.name,
                    argumentsJson = b.args.toString().ifEmpty { "{}" }
                )
            }
        blocks.clear()
        return leftover + StreamEvent.Done(stopReason)
    }
}
