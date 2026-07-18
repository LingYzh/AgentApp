package com.example.myapplication.provider

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 自定义 Provider：用户配置请求体模板 + 响应 JSON 提取路径，兼容其余服务。
 *
 * 模板占位符：${model} ${system} ${messages} ${tools}
 * 响应提取：customStreamPath（SSE 每行 JSON 的增量路径）/ customResponsePath（整包 JSON 路径）
 * 注意：自定义模式不支持工具调用解析（无统一格式），Agent 自动降级为纯对话。
 */
class CustomProvider(private val client: OkHttpClient) : ApiProvider {

    override suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    ) {
        val messagesJson = buildJsonArray {
            if (system.isNotBlank()) {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
            }
            messages.forEach { m ->
                addJsonObject {
                    put("role", if (m.role == "tool") "user" else m.role)
                    put("content", m.content)
                }
            }
        }
        val toolsJson = buildJsonArray {
            tools.forEach { t ->
                addJsonObject {
                    put("name", t.name)
                    put("description", t.description)
                    put("parameters", t.parameters)
                }
            }
        }

        val bodyText = if (config.customRequestTemplate.isNotBlank()) {
            config.customRequestTemplate
                .replace("\${model}", config.model)
                .replace("\${system}", system.jsonEscape())
                .replace("\${messages}", messagesJson.toString())
                .replace("\${tools}", toolsJson.toString())
        } else {
            // 模板为空时退化为 OpenAI 风格请求体
            buildJsonObject {
                put("model", config.model)
                put("messages", messagesJson)
            }.toString()
        }

        val requestBuilder = Request.Builder()
            .url(config.baseUrl)
            .post(bodyText.toRequestBody(JSON))
            .header("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }
        config.extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        val response = client.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errBody = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                throw ApiException(resp.code, errBody.take(2000))
            }
            val body = resp.body?.string().orEmpty()
            // 含 data: 行 → 按 SSE 解析；否则按整包 JSON 解析
            if (body.lineSequence().any { it.startsWith("data:") }) {
                body.lineSequence()
                    .filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .filter { it.isNotEmpty() && it != "[DONE]" }
                    .forEach { data ->
                        val el = runCatching { ProviderJson.parseToJsonElement(data) }.getOrNull()
                            ?: return@forEach
                        JsonPath.extract(el, config.customStreamPath)
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { onEvent(StreamEvent.Text(it)) }
                    }
            } else {
                val el = runCatching { ProviderJson.parseToJsonElement(body) }.getOrNull()
                if (el == null) {
                    onEvent(StreamEvent.Text(body))
                } else {
                    val text = JsonPath.extract(el, config.customResponsePath)
                    onEvent(StreamEvent.Text(text ?: body))
                }
            }
            onEvent(StreamEvent.Done("custom"))
        }
    }

    private fun String.jsonEscape(): String =
        ProviderJson.encodeToString(this).removeSurrounding("\"")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
