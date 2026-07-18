package com.example.myapplication.provider

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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

/** OpenAI Chat Completions 兼容（含 DeepSeek、通义、Ollama、vLLM 等），SSE 流式 + tool_calls + reasoning_content。 */
class OpenAiProvider(private val client: OkHttpClient) : ApiProvider {

    override suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    ) {
        val url = config.baseUrl.trimEnd('/').let {
            if (it.endsWith("/chat/completions")) it else "$it/chat/completions"
        }
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", true)
            config.temperature?.let { put("temperature", it) }
            putJsonArray("messages") {
                if (system.isNotBlank()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                }
                messages.forEach { add(toWireMessage(it)) }
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

    private fun toWireMessage(m: ChatMessage): JsonObject = buildJsonObject {
        when (m.role) {
            "user" -> {
                put("role", "user")
                put("content", m.content)
            }
            "assistant" -> {
                put("role", "assistant")
                put("content", m.content)
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
                put("content", m.content)
            }
            else -> {
                put("role", "user")
                put("content", m.content)
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
        val root = runCatching { ProviderJson.parseToJsonElement(data).jsonObject }
            .getOrNull() ?: return emptyList()
        val events = mutableListOf<StreamEvent>()
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return emptyList()
        choice["finish_reason"]?.let {
            if (it is JsonPrimitive && it.isString) finishReason = it.content
        }
        val delta = choice["delta"]?.jsonObject ?: return events
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
        delta["tool_calls"]?.jsonArray?.forEach { tcEl ->
            val tc = tcEl.jsonObject
            val index = tc["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val acc = accs.getOrPut(index) { ToolCallAcc() }
            tc["id"]?.jsonPrimitive?.contentOrNullSafe()?.let { acc.id = it }
            tc["function"]?.jsonObject?.let { fn ->
                fn["name"]?.jsonPrimitive?.contentOrNullSafe()?.let { acc.name.append(it) }
                fn["arguments"]?.jsonPrimitive?.contentOrNullSafe()?.let { acc.args.append(it) }
            }
        }
        return events
    }

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

internal fun JsonPrimitive.contentOrNullSafe(): String? =
    if (isString) content else null
