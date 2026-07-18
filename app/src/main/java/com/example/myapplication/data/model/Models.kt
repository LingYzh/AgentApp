package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** AI 服务类型预设 */
@Serializable
enum class ProviderType(val label: String) {
    OPENAI("OpenAI 兼容"),
    ANTHROPIC("Anthropic"),
    GEMINI("Google Gemini"),
    CUSTOM("自定义模板")
}

/** 一个模型接入配置 */
@Serializable
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: ProviderType = ProviderType.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Float? = null,
    /** CUSTOM 专用：请求体模板，占位符 ${messages} ${tools} ${model} ${system} */
    val customRequestTemplate: String = "",
    /** CUSTOM 专用：非流式响应文本提取路径，如 $.choices[0].message.content */
    val customResponsePath: String = "",
    /** CUSTOM 专用：SSE 每行 JSON 的增量文本提取路径，如 $.choices[0].delta.content */
    val customStreamPath: String = "",
    /** 附加请求头 */
    val extraHeaders: Map<String, String> = emptyMap(),
    /** 缓存的可用模型列表（通过 /models 接口拉取） */
    val models: List<String> = emptyList()
)

/** 全局配置，持久化到 config.json */
@Serializable
data class AppConfig(
    val providers: List<ProviderConfig> = emptyList(),
    val selectedProviderId: String? = null,
    val maxAgentLoops: Int = 10,
    /** 用户指定的子代理模型（最高优先级，所有子代理强制使用） */
    val subagentProviderId: String? = null,
    val subagentModel: String? = null,
    /** 外观：system / light / dark */
    val themeMode: String = "system"
) {
    val selectedProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
}

/** 一次完整的工具调用（模型产出） */
@Serializable
data class ToolCallInfo(
    val id: String,
    val name: String,
    val argumentsJson: String
)

/** 对话消息。role: user / assistant / tool */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String = "",
    val thinking: String = "",
    /** assistant 消息携带的工具调用 */
    val toolCalls: List<ToolCallInfo> = emptyList(),
    /** tool 消息：对应哪次调用 */
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** tool 消息：执行是否出错 */
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    /** 创建时选择的 Agent 配置，null = 默认 Agent */
    val agentId: String? = null,
    /** 会话内切换模型的 override（只影响本会话） */
    var providerIdOverride: String? = null,
    var modelOverride: String? = null,
    val messages: MutableList<ChatMessage> = mutableListOf()
)

/** Agent 配置（类似 LobeHub 的助手）：独立系统提示词 + 默认模型 */
@Serializable
data class AgentProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "🤖",
    val description: String = "",
    val systemPrompt: String = "",
    /** 默认使用的模型配置，null = 跟随全局选中 */
    val providerId: String? = null,
    val model: String? = null
)

/** 记忆索引条目，正文存 memory/<id>.md */
@Serializable
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

/** Skill 元信息（从 SKILL.md frontmatter 解析） */
data class SkillMeta(
    val name: String,
    val description: String
)

/**
 * 解析一次对话实际使用的 ProviderConfig（含模型 override）。
 * 优先级：会话 override > 会话绑定 AgentProfile 默认 > 全局选中。
 */
object ModelResolver {
    fun resolve(
        conversation: Conversation,
        appConfig: AppConfig,
        agents: List<AgentProfile>
    ): ProviderConfig? {
        val profile = conversation.agentId?.let { id -> agents.firstOrNull { it.id == id } }
        val provider = conversation.providerIdOverride?.let { id ->
            appConfig.providers.firstOrNull { it.id == id }
        } ?: profile?.providerId?.let { id ->
            appConfig.providers.firstOrNull { it.id == id }
        } ?: appConfig.selectedProvider
        val model = conversation.modelOverride
            ?: profile?.model?.takeIf { it.isNotBlank() }
            ?: provider?.model
        return provider?.copy(model = model ?: provider.model)
    }
}
