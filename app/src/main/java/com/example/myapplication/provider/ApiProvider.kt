package com.example.myapplication.provider

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** 流式增量事件。工具调用在流结束时以完整形式发出（各 Provider 内部累积分片）。 */
sealed interface StreamEvent {
    data class Text(val delta: String) : StreamEvent
    data class Thinking(val delta: String) : StreamEvent
    data class ToolCall(val id: String, val name: String, val argumentsJson: String) : StreamEvent
    data class Done(val stopReason: String?) : StreamEvent
    data class Error(val message: String) : StreamEvent
}

/** 工具定义（JSON Schema 参数） */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

class ApiException(val code: Int, message: String) : Exception("HTTP $code: $message")

interface ApiProvider {
    /**
     * 发起一次流式对话。事件通过 [onEvent] 逐个回调；
     * 流正常结束时回调 Done，HTTP/解析错误回调 Error 或抛异常。
     */
    suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    )
}

val ProviderJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/** SSE POST 助手：逐行读取，把每个 `data:` 载荷交给 [onData]。 */
object Sse {
    suspend fun post(client: OkHttpClient, request: Request, onData: suspend (String) -> Unit) {
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                throw ApiException(resp.code, body.take(2000))
            }
            val source = resp.body?.source() ?: return
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isNotEmpty()) onData(data)
                }
            }
        }
    }
}

/** 极简 JSON 路径提取：支持 `$.a.b[0].c` 形式。 */
object JsonPath {
    private val tokenRegex = Regex("""[^.\[\]]+|\[\d+]""")

    fun extract(element: JsonElement, path: String): String? {
        val tokens = tokenRegex.findAll(path.removePrefix("$")).map { it.value }.toList()
        var current: JsonElement? = element
        for (token in tokens) {
            current = if (token.startsWith("[")) {
                val idx = token.removeSurrounding("[", "]").toIntOrNull() ?: return null
                (current as? JsonArray)?.getOrNull(idx)
            } else {
                (current as? JsonObject)?.get(token)
            } ?: return null
        }
        return when (val c = current) {
            null -> null
            is JsonPrimitive -> if (c.isString) c.content else c.toString()
            else -> c.toString()
        }
    }
}
