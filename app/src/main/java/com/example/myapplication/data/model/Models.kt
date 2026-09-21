package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
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
    /** Optional completion cap. Null leaves optional protocol fields unset. */
    val maxOutputTokens: Int? = null,
    /** Optional reasoning control. Null preserves each model's own default and sends no field. */
    val reasoningEffort: ReasoningEffort? = null,
    /** Anthropic only. AUTO picks the known manual 3.7/4.5 protocol, otherwise adaptive. */
    val anthropicThinkingMode: AnthropicThinkingMode = AnthropicThinkingMode.AUTO,
    /** CUSTOM 专用：请求体模板，占位符 ${messages} ${tools} ${model} ${system} */
    val customRequestTemplate: String = "",
    /** CUSTOM 专用：非流式响应文本提取路径，如 $.choices[0].message.content */
    val customResponsePath: String = "",
    /** CUSTOM 专用：SSE 每行 JSON 的增量文本提取路径，如 $.choices[0].delta.content */
    val customStreamPath: String = "",
    /** 附加请求头 */
    val extraHeaders: Map<String, String> = emptyMap(),
    /** 缓存的可用模型列表（通过 /models 接口拉取） */
    val models: List<String> = emptyList(),
    /** 可选的完整模型列表地址；留空按供应商和协议推导。 */
    val modelsUrl: String = "",
    /** 接口发现的能力与用户覆盖分开保存，刷新列表不覆盖用户选择。 */
    val discoveredCapabilities: Map<String, ModelCapabilities> = emptyMap(),
    val capabilityOverrides: Map<String, ModelCapabilities> = emptyMap(),
    /** Per-model context window supplied by the user when a provider does not expose it. */
    val contextWindowOverrides: Map<String, Int> = emptyMap()
)

/**
 * A deliberately small cross-provider vocabulary. Gemini translates it only for documented
 * request schemas; OpenAI-compatible and Anthropic providers retain explicit user choices.
 * CUSTOM never receives it automatically.
 */
@Serializable
enum class ReasoningEffort(val wireValue: String, val label: String) {
    @SerialName("none") NONE("none", "关闭"),
    @SerialName("minimal") MINIMAL("minimal", "极低"),
    @SerialName("low") LOW("low", "低"),
    @SerialName("medium") MEDIUM("medium", "中"),
    @SerialName("high") HIGH("high", "高"),
    @SerialName("xhigh") XHIGH("xhigh", "很高"),
    @SerialName("max") MAX("max", "最大")
}

/** Anthropic thinking request mode. AUTO is safe for new or gateway model aliases. */
@Serializable
enum class AnthropicThinkingMode(val label: String) {
    AUTO("自动"),
    ADAPTIVE("自适应"),
    MANUAL("手动预算")
}

enum class ReasoningProtocol {
    OPENAI_CHAT_COMPLETIONS,
    ANTHROPIC_MANUAL,
    ANTHROPIC_ADAPTIVE,
    GEMINI_THINKING_LEVEL,
    GEMINI_THINKING_BUDGET,
    UNSUPPORTED
}

/** The recognised protocol and selectable strengths for one configured model. */
data class ReasoningSupport(
    val protocol: ReasoningProtocol,
    val efforts: List<ReasoningEffort>,
    val description: String
)

private val openCompatibleEfforts = listOf(
    ReasoningEffort.NONE,
    ReasoningEffort.LOW,
    ReasoningEffort.MEDIUM,
    ReasoningEffort.HIGH,
    ReasoningEffort.XHIGH,
    ReasoningEffort.MAX
)

private val anthropicEfforts = listOf(
    ReasoningEffort.NONE,
    ReasoningEffort.LOW,
    ReasoningEffort.MEDIUM,
    ReasoningEffort.HIGH,
    ReasoningEffort.XHIGH,
    ReasoningEffort.MAX
)

private val geminiLevels = listOf(
    ReasoningEffort.MINIMAL,
    ReasoningEffort.LOW,
    ReasoningEffort.MEDIUM,
    ReasoningEffort.HIGH
)

private val geminiNoMinimalLevels = listOf(
    ReasoningEffort.LOW,
    ReasoningEffort.MEDIUM,
    ReasoningEffort.HIGH
)

/**
 * Provider configuration vocabulary used by the editor. OpenAI-compatible and Anthropic
 * providers deliberately do not restrict a selected effort by model ID: compatible gateways
 * often publish aliases, and the upstream is the source of truth for request validation.
 */
fun reasoningSupportFor(type: ProviderType, modelId: String): ReasoningSupport {
    val model = modelId.trim().lowercase()
    return when (type) {
        ProviderType.CUSTOM -> ReasoningSupport(
            ReasoningProtocol.UNSUPPORTED,
            emptyList(),
            "自定义模板保持原样，不会自动注入思考参数。"
        )
        ProviderType.OPENAI -> ReasoningSupport(
            ReasoningProtocol.OPENAI_CHAT_COMPLETIONS,
            openCompatibleEfforts,
            "会按所选值原样发送 reasoning_effort；兼容服务或模型的限制由上游返回。"
        )
        ProviderType.ANTHROPIC -> ReasoningSupport(
            ReasoningProtocol.ANTHROPIC_ADAPTIVE,
            anthropicEfforts,
            "可选择关闭或任一强度。自动模式对已识别的 Claude 3.7/4.5 使用手动预算，其他别名使用 adaptive。"
        )
        ProviderType.GEMINI -> when {
            model.startsWith("gemini-2.5-pro") -> ReasoningSupport(
                ReasoningProtocol.GEMINI_THINKING_BUDGET,
                listOf(
                    ReasoningEffort.MINIMAL,
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH
                ),
                "Gemini 2.5 Pro 使用 thinkingBudget；该模型不能关闭思考。"
            )
            model.startsWith("gemini-2.5-flash-lite") -> ReasoningSupport(
                ReasoningProtocol.GEMINI_THINKING_BUDGET,
                listOf(
                    ReasoningEffort.NONE,
                    ReasoningEffort.MINIMAL,
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH
                ),
                "Gemini 2.5 Flash-Lite 使用 thinkingBudget。"
            )
            model.startsWith("gemini-2.5-flash") || model.startsWith("gemini-robotics-er") ->
                ReasoningSupport(
                    ReasoningProtocol.GEMINI_THINKING_BUDGET,
                    listOf(
                        ReasoningEffort.NONE,
                        ReasoningEffort.MINIMAL,
                        ReasoningEffort.LOW,
                        ReasoningEffort.MEDIUM,
                        ReasoningEffort.HIGH
                    ),
                    "Gemini 2.5 使用 thinkingBudget。"
                )
            model.startsWith("gemini-3.1-flash-lite-image") -> ReasoningSupport(
                ReasoningProtocol.GEMINI_THINKING_LEVEL,
                listOf(ReasoningEffort.MINIMAL, ReasoningEffort.HIGH),
                "此 Gemini 3.1 Flash-Lite Image 模型仅支持 minimal 和 high thinkingLevel。"
            )
            model.startsWith("gemini-3.8-") || model.startsWith("gemini-3.7-") ||
                model.startsWith("gemini-3.1-pro") -> ReasoningSupport(
                    ReasoningProtocol.GEMINI_THINKING_LEVEL,
                    geminiNoMinimalLevels,
                    "此 Gemini 3 模型使用 thinkingLevel。"
                )
            model.startsWith("gemini-3.6-") || model.startsWith("gemini-3.5-") ||
                model.startsWith("gemini-3.1-flash-lite") || model.startsWith("gemini-3-flash") ->
                ReasoningSupport(
                    ReasoningProtocol.GEMINI_THINKING_LEVEL,
                    geminiLevels,
                    "此 Gemini 3 模型使用 thinkingLevel。"
                )
            else -> ReasoningSupport(
                ReasoningProtocol.UNSUPPORTED,
                emptyList(),
                "此 Gemini 模型世代无法可靠判断，因而不会发送 thinkingConfig。"
            )
        }
    }
}

/** AUTO keeps the manual protocol for the older, identified Claude generations only. */
fun anthropicThinkingProtocol(
    modelId: String,
    mode: AnthropicThinkingMode
): ReasoningProtocol = when (mode) {
    AnthropicThinkingMode.ADAPTIVE -> ReasoningProtocol.ANTHROPIC_ADAPTIVE
    AnthropicThinkingMode.MANUAL -> ReasoningProtocol.ANTHROPIC_MANUAL
    AnthropicThinkingMode.AUTO -> {
        val model = modelId.trim().lowercase()
        if (
            model.startsWith("claude-3-7-") ||
            Regex("^claude-(opus|sonnet|haiku)-4-5(?:-\\d{8})?$").matches(model)
        ) ReasoningProtocol.ANTHROPIC_MANUAL else ReasoningProtocol.ANTHROPIC_ADAPTIVE
    }
}

/** Returns the documented temperature conflict for the selected model, if any. */
fun temperatureConflictFor(
    type: ProviderType,
    @Suppress("UNUSED_PARAMETER") modelId: String,
    effort: ReasoningEffort?
): String? {
    if (type == ProviderType.ANTHROPIC && effort != null && effort != ReasoningEffort.NONE) {
        return "Anthropic 开启思考时不能同时设置温度。"
    }
    return null
}

@Serializable
data class ModelCapabilities(
    val image: Boolean = false,
    val pdf: Boolean = false,
    val audio: Boolean = false,
    val video: Boolean = false
)

fun ProviderConfig.capabilitiesFor(modelId: String = model): ModelCapabilities =
    capabilityOverrides[modelId] ?: discoveredCapabilities[modelId] ?: ModelCapabilities()

/** Returns a user supplied context window for this model, when one was configured. */
fun ProviderConfig.contextWindowFor(modelId: String = model): Int? =
    contextWindowOverrides[modelId]?.takeIf { it > 0 }

/** Normalized provider usage. Null means the protocol did not report that value; zero is real. */
@Serializable
data class TokenUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val reasoningTokens: Long? = null
)

/** 文件保存在工作区，历史只保存相对路径；请求时按需读取，不把 base64 写入会话。 */
@Serializable
data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val workspacePath: String,
    /** native = 模型原生读取；workspace = 仅通过工作区工具访问。 */
    val delivery: String = "workspace"
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
    val themeMode: String = "system",
    val autoApprovedCommands: List<String> = emptyList()
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

@Serializable
data class FileChange(
    val path: String,
    val before: String? = null,
    val after: String = "",
    val beforeExists: Boolean = false,
    val previewOmitted: Boolean = false
)

/** 对话消息。role: user / assistant / tool */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String = "",
    val thinking: String = "",
    /** Opaque provider-native assistant blocks required for same-protocol continuation. */
    val providerBlocks: Map<String, List<JsonObject>> = emptyMap(),
    /** assistant 消息携带的工具调用 */
    val toolCalls: List<ToolCallInfo> = emptyList(),
    /** tool 消息：对应哪次调用 */
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** tool 消息：执行是否出错 */
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<MessageAttachment> = emptyList(),
    val fileChange: FileChange? = null,
    val originToolCallId: String? = null,
    /** App-authored append-only context, hidden from the visible conversation. */
    val contextKind: String? = null,
    /** Kept for display/recovery, but never replayed to a provider. */
    val excludedFromContext: Boolean = false
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
    /** Null follows the model configuration selected for this conversation. */
    @Volatile
    var reasoningEffortOverride: ReasoningEffort? = null,
    var contextCompaction: ContextCompaction? = null,
    var lastContextUsage: ContextUsageRecord? = null,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val parentConversationId: String? = null,
    val parentToolCallId: String? = null,
    /** 子代理状态：running / completed / failed / cancelled。 */
    var executionStatus: String? = null,
    var stopReason: String? = null,
    var permissionMode: PermissionMode = PermissionMode.ACCEPT_EDIT,
    /** Empty means all OS-accessible paths; otherwise canonical directory boundaries. */
    var allowedDirectories: List<String> = emptyList(),
    /** Frozen first-use system prefix; changing runtime facts are appended as messages. */
    var systemPromptSnapshot: String? = null
)

/** Agent 配置（类似 LobeHub 的助手）：独立系统提示词 + 默认模型 */
@Serializable
data class AgentProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val emoji: String = "🤖",
    /** 自选头像本地文件路径（为空则使用 emoji） */
    val avatarPath: String? = null,
    val description: String = "",
    val systemPrompt: String = "",
    /** 默认使用的模型配置，null = 跟随全局选中 */
    val providerId: String? = null,
    val model: String? = null,
    /** 授权该 Agent 可用的工具名列表，空表示全量工具均可用 */
    val tools: List<String> = emptyList()
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
    val description: String,
    val version: String = "1.0.0",
    val license: String = "MIT"
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


@Serializable
enum class PermissionMode(val label: String) {
    ACCEPT_EDIT("Accept Edit"), PLAN("Plan"), AUTO("Auto"), READONLY("Readonly")
}
