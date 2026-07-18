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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Google Gemini generateContent API，SSE 流式（alt=sse）+ functionCall + thought（思考）parts。 */
class GeminiProvider(private val client: OkHttpClient) : ApiProvider {

    override suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    ) {
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

        val body = buildJsonObject {
            if (system.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", system) }
                    }
                }
            }
            putJsonArray("contents") {
                toWireContents(messages).forEach { add(it) }
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
            config.temperature?.let { temp ->
                putJsonObject("generationConfig") { put("temperature", temp) }
            }
        }

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

    /**
     * 转为 Gemini contents：
     * - assistant → role "model"，text parts + functionCall parts
     * - 连续 tool 结果合并为一条 user content 的 functionResponse parts
     */
    private fun toWireContents(messages: List<ChatMessage>): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var i = 0
        while (i < messages.size) {
            val m = messages[i]
            when (m.role) {
                "user" -> out += buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", m.content) }
                    }
                }
                "assistant" -> out += buildJsonObject {
                    put("role", "model")
                    putJsonArray("parts") {
                        if (m.content.isNotBlank()) {
                            addJsonObject { put("text", m.content) }
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
                "tool" -> {
                    val parts = buildJsonArray {
                        var j = i
                        while (j < messages.size && messages[j].role == "tool") {
                            val t = messages[j]
                            addJsonObject {
                                putJsonObject("functionResponse") {
                                    put("name", t.toolName ?: "")
                                    putJsonObject("response") {
                                        put("result", t.content)
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
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** 解析 Gemini SSE 流（candidates/parts 模型）。functionCall 在 part 中一次性给出，无需累积。 */
class GeminiStreamParser {

    private var counter = 0
    private var finishReason: String? = null

    fun parse(data: String): List<StreamEvent> {
        val root = runCatching { ProviderJson.parseToJsonElement(data).jsonObject }
            .getOrNull() ?: return emptyList()
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.content ?: "未知错误"
            return listOf(StreamEvent.Error(msg))
        }
        val events = mutableListOf<StreamEvent>()
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return events
        candidate["finishReason"]?.jsonPrimitive?.content?.let { finishReason = it }
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return events
        parts.forEach { partEl ->
            val part = partEl.jsonObject
            part["functionCall"]?.jsonObject?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.content ?: return@let
                val args = fc["args"]?.toString() ?: "{}"
                events += StreamEvent.ToolCall(id = "gemini_${counter++}_$name", name = name, argumentsJson = args)
                return@forEach
            }
            val text = (part["text"] as? JsonPrimitive)?.content ?: return@forEach
            if (text.isEmpty()) return@forEach
            val isThought = (part["thought"] as? JsonPrimitive)?.content == "true"
            events += if (isThought) StreamEvent.Thinking(text) else StreamEvent.Text(text)
        }
        return events
    }

    fun finish(): List<StreamEvent> = listOf(StreamEvent.Done(finishReason))
}
