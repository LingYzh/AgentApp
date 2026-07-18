package com.example.myapplication.agent

import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ProviderFactory
import com.example.myapplication.provider.StreamEvent

/**
 * Agent 主循环：发送消息 → 流式解析 → 执行工具调用 → 回填结果 → 继续，
 * 直到模型给出最终回复或达到循环上限。
 */
class AgentEngine(
    private val store: FileStore,
    private val providerFactory: ProviderFactory,
    private val subagentRunner: SubagentRunner? = null
) {
    class Callbacks(
        val onMessageAdded: (ChatMessage) -> Unit = {},
        val onMessageUpdated: (ChatMessage) -> Unit = {},
        /** 工具执行状态文本，null 表示空闲 */
        val onToolStatus: (String?) -> Unit = {}
    )

    /**
     * @param depth 0 = 主 Agent（可委派子代理），>=1 = 子代理（禁止嵌套）
     * @param systemOverride 覆盖系统提示（子代理用）
     * @param agentProfile 本会话绑定的 Agent 配置（其 systemPrompt 作为系统提示主体）
     * @param allowedTools 允许的工具名，null = 默认全集（受 depth 限制）
     */
    suspend fun run(
        conversation: Conversation,
        config: ProviderConfig,
        maxLoops: Int = 10,
        depth: Int = 0,
        systemOverride: String? = null,
        agentProfile: AgentProfile? = null,
        allowedTools: Set<String>? = null,
        callbacks: Callbacks = Callbacks()
    ) {
        val provider = providerFactory.create(config.type)
        val executor = ToolExecutor(
            store = store,
            allowedTools = allowedTools,
            onRunSubagent = if (depth == 0 && subagentRunner != null) {
                { task, providerName, model -> subagentRunner.run(task, providerName, model, config) }
            } else null
        )
        val tools = executor.specs()
        val system = systemOverride
            ?: buildSystemPrompt(tools.map { it.name }.toSet(), agentProfile, store.loadConfig())

        var loop = 0
        while (true) {
            var assistant = ChatMessage(role = "assistant")
            conversation.messages += assistant
            callbacks.onMessageAdded(assistant)

            val text = StringBuilder()
            val thinking = StringBuilder()
            val calls = mutableListOf<ToolCallInfo>()
            var error: String? = null

            try {
                // 历史消息不含刚追加的空 assistant 占位
                val history = conversation.messages.dropLast(1).toList()
                provider.streamChat(config, system, history, tools) { ev ->
                    when (ev) {
                        is StreamEvent.Text -> {
                            text.append(ev.delta)
                            callbacks.onMessageUpdated(
                                assistant.copy(content = text.toString(), thinking = thinking.toString())
                            )
                        }
                        is StreamEvent.Thinking -> {
                            thinking.append(ev.delta)
                            callbacks.onMessageUpdated(
                                assistant.copy(content = text.toString(), thinking = thinking.toString())
                            )
                        }
                        is StreamEvent.ToolCall ->
                            calls += ToolCallInfo(ev.id, ev.name, ev.argumentsJson)
                        is StreamEvent.Done -> Unit
                        is StreamEvent.Error -> error = ev.message
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }

            // 定稿 assistant 消息
            val err = error
            assistant = assistant.copy(
                content = if (text.isEmpty() && err != null && calls.isEmpty()) "⚠️ $err" else text.toString(),
                thinking = thinking.toString(),
                toolCalls = calls,
                isError = err != null && calls.isEmpty()
            )
            replaceMessage(conversation, assistant)
            callbacks.onMessageUpdated(assistant)

            if (calls.isEmpty()) {
                store.saveConversation(conversation)
                return
            }

            loop++
            if (loop > maxLoops) {
                val note = ChatMessage(
                    role = "assistant",
                    content = "⚠️ 已达到最大工具循环次数（$maxLoops），为避免失控已停止。",
                    isError = true
                )
                conversation.messages += note
                callbacks.onMessageAdded(note)
                store.saveConversation(conversation)
                return
            }

            for (call in calls) {
                callbacks.onToolStatus("执行工具：${call.name}")
                val result = executor.execute(call.name, call.argumentsJson)
                val toolMsg = ChatMessage(
                    role = "tool",
                    toolCallId = call.id,
                    toolName = call.name,
                    content = result,
                    isError = result.startsWith("错误")
                )
                conversation.messages += toolMsg
                callbacks.onMessageAdded(toolMsg)
            }
            callbacks.onToolStatus(null)
        }
    }

    private fun replaceMessage(conversation: Conversation, message: ChatMessage) {
        val idx = conversation.messages.indexOfFirst { it.id == message.id }
        if (idx >= 0) conversation.messages[idx] = message
    }

    /** 组装系统提示：Agent 人设（或默认）+ 工具说明 + Skill 索引 + 可委派模型清单 */
    private fun buildSystemPrompt(
        availableToolNames: Set<String>,
        agentProfile: AgentProfile?,
        appConfig: AppConfig
    ): String = buildString {
        if (agentProfile != null && agentProfile.systemPrompt.isNotBlank()) {
            appendLine(agentProfile.systemPrompt)
            appendLine()
        } else {
            appendLine("你是一个运行在用户 Android 手机上的 AI Agent。你可以调用工具来完成任务：")
        }
        appendLine("- 工作区文件：生成的文件保存在工作区，用户可在应用的文件页查看。")
        appendLine("- 长期记忆：重要信息（用户偏好、项目状态、关键结论）主动用 save_memory 保存；不确定时用 search_memory 检索。")
        val skills = store.listSkills()
        if (skills.isNotEmpty() && Tools.USE_SKILL in availableToolNames) {
            appendLine()
            appendLine("可用 Skills（需要时用 use_skill 加载指令后执行）：")
            skills.forEach { appendLine("- ${it.name}: ${it.description}") }
        }
        if (Tools.RUN_SUBAGENT in availableToolNames) {
            appendLine()
            appendLine("可用模型配置（run_subagent 的 provider_name/model 可从中选择，不传则继承你当前使用的模型）：")
            appConfig.providers.forEach { p ->
                val label = p.name.ifBlank { p.model }
                val models = if (p.models.isNotEmpty()) p.models.joinToString(", ") else p.model
                appendLine("- $label: $models")
            }
        }
        appendLine()
        append("回复使用与用户相同的语言。工具执行结果不理想时换策略重试，不要盲目重复同一调用。")
    }
}
