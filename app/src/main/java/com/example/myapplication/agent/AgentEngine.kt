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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        val effectiveAllowedTools = allowedTools
            ?: agentProfile?.tools?.takeIf { it.isNotEmpty() }?.toSet()
        val executor = ToolExecutor(
            store = store,
            allowedTools = effectiveAllowedTools,
            onRunSubagent = if (depth == 0 && subagentRunner != null) {
                { task, providerName, model -> subagentRunner.run(task, providerName, model, config) }
            } else null
        )
        val tools = executor.specs()
        val system = systemOverride
            ?: buildSystemPrompt(tools.map { it.name }.toSet(), agentProfile, store.loadConfig())

        var loop = 0
        var cancellationRecorded = false
        try {
            while (true) {
                // 每次请求前检查，避免取消后创建一个没有请求意义的 assistant 占位。
                currentCoroutineContext().ensureActive()

                var assistant = ChatMessage(role = "assistant")
                conversation.messages += assistant
                callbacks.onMessageAdded(assistant)

                val text = StringBuilder()
                val thinking = StringBuilder()
                val calls = mutableListOf<ToolCallInfo>()
                var error: String? = null
                var stopReason: String? = null
                var streamCancellation: CancellationException? = null

                try {
                    // 历史消息不含刚追加的空 assistant 占位
                    currentCoroutineContext().ensureActive()
                    val history = conversation.messages.dropLast(1).toList()
                    provider.streamChat(config, system, history, tools) { ev ->
                        // 某些 Provider 可能在底层已读入数据后才回调，不能在取消后继续写入会话。
                        currentCoroutineContext().ensureActive()
                        when (ev) {
                            is StreamEvent.Text -> {
                                text.append(ev.delta)
                                assistant = assistant.copy(
                                    content = text.toString(),
                                    thinking = thinking.toString(),
                                    toolCalls = calls.toList()
                                )
                                updateAssistant(conversation, assistant, callbacks)
                            }
                            is StreamEvent.Thinking -> {
                                thinking.append(ev.delta)
                                assistant = assistant.copy(
                                    content = text.toString(),
                                    thinking = thinking.toString(),
                                    toolCalls = calls.toList()
                                )
                                updateAssistant(conversation, assistant, callbacks)
                            }
                            is StreamEvent.ToolCall -> {
                                calls += ToolCallInfo(ev.id, ev.name, ev.argumentsJson)
                                // 工具调用可能在文本后到达，实时写入以便取消时保留完整请求。
                                assistant = assistant.copy(toolCalls = calls.toList())
                                updateAssistant(conversation, assistant, callbacks)
                            }
                            is StreamEvent.Done -> stopReason = ev.stopReason
                            is StreamEvent.Error -> {
                                error = ev.message.ifBlank { "模型流返回错误" }
                            }
                        }
                    }
                    // Provider 正常返回但 Job 已被取消时，也要走可见的中断收尾。
                    currentCoroutineContext().ensureActive()
                } catch (e: CancellationException) {
                    streamCancellation = e
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                }

                val cancellation = streamCancellation
                if (cancellation != null) {
                    cancellationRecorded = true
                    assistant = assistant.copy(
                        content = stoppedContent(text.toString()),
                        thinking = thinking.toString(),
                        toolCalls = calls.toList(),
                        isError = true
                    )
                    updateAssistant(conversation, assistant, callbacks)
                    store.saveConversation(conversation)
                    // 流阶段尚未开始执行工具，使用保守提示，避免声称动作已完成或未发生。
                    calls.forEach { call ->
                        appendToolResponse(
                            conversation,
                            call,
                            "未执行：任务已中断",
                            callbacks,
                            isError = true
                        )
                    }
                    throw cancellation
                }

                // 空流、显式 Error 以及 stop_reason=error 都必须在消息中可见。
                if (error == null && stopReason.equals("error", ignoreCase = true)) {
                    error = "模型流返回错误"
                }
                if (error == null && text.isEmpty() && thinking.isEmpty() && calls.isEmpty()) {
                    error = "模型未返回内容"
                }

                val err = error
                assistant = assistant.copy(
                    content = visibleContent(text.toString(), err),
                    thinking = thinking.toString(),
                    toolCalls = calls.toList(),
                    isError = err != null
                )
                updateAssistant(conversation, assistant, callbacks)
                // 定稿 assistant 后立即持久化，随后每条 tool response 也单独持久化。
                store.saveConversation(conversation)

                if (calls.isEmpty()) return

                loop++
                if (err != null) {
                    calls.forEach { call ->
                        appendToolResponse(
                            conversation,
                            call,
                            "未执行：模型流发生错误：$err",
                            callbacks,
                            isError = true
                        )
                    }
                    return
                }

                if (loop > maxLoops) {
                    calls.forEach { call ->
                        appendToolResponse(
                            conversation,
                            call,
                            "未执行：已达到最大工具循环次数（$maxLoops）",
                            callbacks,
                            isError = true
                        )
                    }
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

                var toolCancellation: CancellationException? = null
                var currentToolStarted = false
                for ((index, call) in calls.withIndex()) {
                    currentToolStarted = false
                    try {
                        // 放在每个调用自己的 try 中，取消发生在这里也要为当前及后续调用补齐响应。
                        currentCoroutineContext().ensureActive()
                        callbacks.onToolStatus("执行工具：${call.name}")
                        currentCoroutineContext().ensureActive()
                        // 标记在真正进入 executor 前，取消发生在此之后就不能声称工具未启动。
                        currentToolStarted = true
                        val result = executor.execute(call.name, call.argumentsJson)
                        appendToolResponse(
                            conversation,
                            call,
                            result,
                            callbacks,
                            isError = result.startsWith("错误")
                        )
                    } catch (e: CancellationException) {
                        toolCancellation = e
                        cancellationRecorded = true
                        // 当前工具可能已经开始，必须提示调用方确认真实外部副作用。
                        appendToolResponse(
                            conversation,
                            call,
                            if (currentToolStarted) "执行已中断，结果需确认" else "未执行：任务已中断",
                            callbacks,
                            isError = true
                        )
                        // 尚未开始的后续调用可以明确标记为未执行。
                        calls.drop(index + 1).forEach { pending ->
                            appendToolResponse(
                                conversation,
                                pending,
                                "未执行：任务已中断",
                                callbacks,
                                isError = true
                            )
                        }
                        break
                    }
                }

                val interrupted = toolCancellation
                if (interrupted != null) {
                    markAssistantStopped(conversation, assistant, callbacks)
                    throw interrupted
                }
                // 工具批次完成后立刻回到空闲状态，下一轮模型请求期间不显示过期工具状态。
                callbacks.onToolStatus(null)
            }
        } catch (e: CancellationException) {
            if (!cancellationRecorded) {
                // 取消发生在下一轮请求前或其它尚无当前流的边界，单独留下可见停止消息。
                val note = ChatMessage(
                    role = "assistant",
                    content = "⚠️ 已停止：本次执行已取消。",
                    isError = true
                )
                conversation.messages += note
                callbacks.onMessageAdded(note)
                store.saveConversation(conversation)
            }
            throw e
        } finally {
            callbacks.onToolStatus(null)
            // 终止路径再次保存，确保取消或回调异常时已写入的部分不会丢失。
            store.saveConversation(conversation)
        }
    }

    private fun updateAssistant(
        conversation: Conversation,
        message: ChatMessage,
        callbacks: Callbacks
    ) {
        replaceMessage(conversation, message)
        callbacks.onMessageUpdated(message)
    }

    private fun appendToolResponse(
        conversation: Conversation,
        call: ToolCallInfo,
        content: String,
        callbacks: Callbacks,
        isError: Boolean
    ) {
        val toolMsg = ChatMessage(
            role = "tool",
            toolCallId = call.id,
            toolName = call.name,
            content = content,
            isError = isError
        )
        conversation.messages += toolMsg
        callbacks.onMessageAdded(toolMsg)
        store.saveConversation(conversation)
    }

    private fun markAssistantStopped(
        conversation: Conversation,
        assistant: ChatMessage,
        callbacks: Callbacks
    ) {
        val stopped = assistant.copy(
            content = stoppedContent(assistant.content),
            isError = true
        )
        updateAssistant(conversation, stopped, callbacks)
        store.saveConversation(conversation)
    }

    private fun visibleContent(text: String, error: String?): String {
        if (error != null) {
            val marker = "⚠️ $error"
            return if (text.isBlank()) marker else "$text\n\n$marker"
        }
        return text
    }

    private fun stoppedContent(partial: String): String {
        val marker = "⚠️ 已停止：本次执行已取消。"
        return if (partial.isBlank()) marker else "$partial\n\n$marker"
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
        if (Tools.SAVE_SKILL in availableToolNames) {
            appendLine("- 技能沉淀：当你发现某种复杂任务流程、标准化 SOP 或多次复用的提示词范式可被固化时，或者用户要求你学习/掌握某项新技能时，主动调用 save_skill 创建新技能（会自动生成标准目录包），供后续或其它对话复用。")
        }
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
