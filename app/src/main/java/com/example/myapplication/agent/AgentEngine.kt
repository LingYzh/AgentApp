package com.example.myapplication.agent

import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.model.ContextUsageRecord
import com.example.myapplication.data.store.AttachmentStore
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ProviderFactory
import com.example.myapplication.provider.StreamEvent
import com.example.myapplication.provider.anthropicMaxOutputTokens
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
    private val subagentRunner: SubagentRunner? = null,
    private val deviceTools: AndroidDeviceTools? = null
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
        callbacks: Callbacks = Callbacks(),
        permissionSession: PermissionSession? = null
    ) {
        val suppliedSession = permissionSession ?: PermissionSession(store, conversation, PermissionCoordinator())
        val session = if (depth >= 1) suppliedSession.childSession() else suppliedSession
        com.example.myapplication.diagnostics.RuntimeDiagnostics.event("run_start",
            "conversation" to conversation.id, "protocol" to config.type,
            "model" to config.model, "reasoningEffort" to config.reasoningEffort,
            "mode" to session.conversation.permissionMode, "depth" to depth)
        val provider = providerFactory.create(config.type)
        val effectiveAllowedTools = allowedTools
            ?: agentProfile?.tools?.takeIf { it.isNotEmpty() }?.toSet()
        // The system prefix is deliberately immutable for the life of a conversation. Runtime
        // facts are represented by append-only user context below, which keeps provider prompt
        // caches valid when permissions, skills, or the provider catalog change.
        val system = conversation.systemPromptSnapshot ?: (systemOverride ?: buildBaseSystemPrompt()).also {
            conversation.systemPromptSnapshot = it
            store.saveConversation(conversation)
        }
        // ToolExecutor keeps a deliberately small callback signature for compatibility.
        // This mutable value is set immediately before each tool starts, allowing the
        // run_subagent callback to retain the exact parent tool call ID.
        var currentSubagentToolCallId: String? = null
        var currentFileChange: FileChange? = null
        val mediaStore = AttachmentStore(store)
        val pendingMedia = mutableListOf<ChatMessage>()
        val deviceSession = deviceTools?.session(session.conversation.id)
        val executor = ToolExecutor(
            store = store,
            allowedTools = effectiveAllowedTools,
            onRunSubagent = if (depth == 0 && subagentRunner != null) {
                { task, providerName, model ->
                    subagentRunner.run(
                        task = task,
                        providerName = providerName,
                        model = model,
                        inherited = config.copy(reasoningEffort = conversation.reasoningEffortOverride ?: config.reasoningEffort),
                        parentConversationId = conversation.id,
                        parentToolCallId = currentSubagentToolCallId,
                        permissionSession = session
                    )
                }
            } else null,
            onFileChange = { currentFileChange = it },
            onReadMedia = { file ->
                mediaStore.readMediaFile(config, file)?.let { attachment ->
                    pendingMedia += ChatMessage(role = "user", content = "工具读取的文件：${file.path}",
                        attachments = listOf(attachment), originToolCallId = currentSubagentToolCallId)
                    "已读取文件 ${file.path}；原生内容已附在本轮工具结果后，请直接查看。"
                }
            },
            permissionSession = session,
            deviceSession = deviceSession
        )

        var loop = 0
        var cancellationRecorded = false
        var lastProgressSaveAt = 0L

        fun updateAssistantWithProgress(message: ChatMessage) {
            updateAssistant(conversation, message, callbacks)
            // A child conversation is independently visible from the root list. Persist
            // streamed content at a modest cadence so its detail view can follow progress
            // without turning every provider token into a file write.
            if (conversation.parentConversationId != null) {
                val now = System.currentTimeMillis()
                if (lastProgressSaveAt == 0L || now - lastProgressSaveAt >= PROGRESS_SAVE_INTERVAL_MS) {
                    store.saveConversation(conversation)
                    lastProgressSaveAt = now
                }
            }
        }
        try {
            while (true) {
                // 每次请求前检查，避免取消后创建一个没有请求意义的 assistant 占位。
                currentCoroutineContext().ensureActive()
                // Tool schemas intentionally stay mode-independent. The current gates and all
                // dynamic app state are appended at a request boundary instead of rewriting the
                // cached system prefix or historical messages.
                val tools = executor.specs()
                appendEnvironmentIfChanged(
                    conversation = conversation,
                    environment = renderEnvironment(tools.map { it.name }, agentProfile, store.loadConfig(), session),
                    callbacks = callbacks
                )

                val requestConfig = config.copy(reasoningEffort = conversation.reasoningEffortOverride ?: config.reasoningEffort)
                var assistant = ChatMessage(role = "assistant")
                conversation.messages += assistant
                callbacks.onMessageAdded(assistant)

                val text = StringBuilder()
                val thinking = StringBuilder()
                val calls = mutableListOf<ToolCallInfo>()
                var error: String? = null
                var stopReason: String? = null
                var reportedUsage: com.example.myapplication.data.model.TokenUsage? = null
                var streamCancellation: CancellationException? = null
                val requestStarted = System.nanoTime()
                com.example.myapplication.diagnostics.RuntimeDiagnostics.event("model_request",
                    "conversation" to conversation.id, "mode" to session.conversation.permissionMode,
                    "messageCount" to ContextWindows.replay(conversation).size - 1, "toolCount" to tools.size,
                    "reasoningEffort" to requestConfig.reasoningEffort)

                try {
                    // 历史消息不含刚追加的空 assistant 占位
                    currentCoroutineContext().ensureActive()
                    val history = ContextWindows.replay(conversation).filterNot { it.id == assistant.id }
                    provider.streamChat(requestConfig, system, history, tools) { ev ->
                        // 某些 Provider 可能在底层已读入数据后才回调，不能在取消后继续写入会话。
                        currentCoroutineContext().ensureActive()
                        when (ev) {
                            is StreamEvent.Usage -> {
                                reportedUsage = ev.usage
                                conversation.lastContextUsage = ContextUsageRecord(ev.usage, requestConfig.model)
                                callbacks.onMessageUpdated(assistant)
                            }
                            is StreamEvent.ProviderBlocks -> {
                                assistant = assistant.copy(providerBlocks = assistant.providerBlocks + (ev.protocol to ev.blocks))
                                updateAssistantWithProgress(assistant)
                            }
                            is StreamEvent.Text -> {
                                text.append(ev.delta)
                                assistant = assistant.copy(
                                    content = text.toString(),
                                    thinking = thinking.toString(),
                                    toolCalls = calls.toList()
                                )
                                updateAssistantWithProgress(assistant)
                            }
                            is StreamEvent.Thinking -> {
                                thinking.append(ev.delta)
                                assistant = assistant.copy(
                                    content = text.toString(),
                                    thinking = thinking.toString(),
                                    toolCalls = calls.toList()
                                )
                                updateAssistantWithProgress(assistant)
                            }
                            is StreamEvent.ToolCall -> {
                                calls += ToolCallInfo(ev.id, ev.name, ev.argumentsJson)
                                // 工具调用可能在文本后到达，实时写入以便取消时保留完整请求。
                                assistant = assistant.copy(toolCalls = calls.toList())
                                updateAssistantWithProgress(assistant)
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
                generationStopError(stopReason)?.let { termination ->
                    error = if (error == null) termination else "$termination\n$error"
                }
                if (error == null && cancellation == null && text.isEmpty() && calls.isEmpty()) {
                    error = if (thinking.isNotEmpty()) "模型只返回了思考，未生成正文或工具调用。已保留收到的思考内容，请继续或重试。"
                        else "模型未返回内容"
                }
                com.example.myapplication.diagnostics.RuntimeDiagnostics.event("model_result",
                    "conversation" to conversation.id, "textChars" to text.length, "thinkingChars" to thinking.length,
                    "toolCalls" to calls.size,
                    "stopReason" to stopReason,
                    "configuredMaxOutputTokens" to requestConfig.maxOutputTokens,
                    "effectiveMaxOutputTokens" to (requestConfig.maxOutputTokens ?: if (requestConfig.type == com.example.myapplication.data.model.ProviderType.ANTHROPIC)
                        requestConfig.anthropicMaxOutputTokens() else null),
                    "reportedOutputTokens" to reportedUsage?.outputTokens,
                    "failed" to (error != null || stopReason.equals("error", true) ||
                        (text.isEmpty() && thinking.isEmpty() && calls.isEmpty() && cancellation == null)),
                    "cancelled" to (cancellation != null),
                    "elapsedMs" to (System.nanoTime() - requestStarted) / 1_000_000)
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

                if (err != null && text.isEmpty() && thinking.isEmpty() && calls.isEmpty()) {
                    excludeRejectedRequestContext(conversation, assistant, callbacks)
                    store.saveConversation(conversation)
                }

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
                        currentSubagentToolCallId = call.id
                        currentFileChange = null
                        val result = executor.execute(call.name, call.argumentsJson)
                        appendToolResponse(
                            conversation,
                            call,
                            result,
                            callbacks,
                            isError = result.startsWith("错误"),
                            fileChange = currentFileChange
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
                // 原生文件内容作为 user parts 跟在整批 tool responses 后，兼容三种协议。
                pendingMedia.forEach { message ->
                    conversation.messages += message
                    callbacks.onMessageAdded(message)
                }
                if (pendingMedia.isNotEmpty()) store.saveConversation(conversation)
                pendingMedia.clear()
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
            deviceSession?.close()
            com.example.myapplication.diagnostics.RuntimeDiagnostics.event("run_end", "conversation" to conversation.id,
                "mode" to session.conversation.permissionMode, "cancelled" to cancellationRecorded)
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
        // Local error/stop notes and partial text must not disappear behind a raw signed replay.
        val updated = if (message.isError && message.providerBlocks.isNotEmpty()) message.copy(
            providerBlocks = com.example.myapplication.data.store.ConversationEdits.editVisibleBlocks(message.providerBlocks, message.content)
        ) else message
        replaceMessage(conversation, updated)
        callbacks.onMessageUpdated(updated)
    }

    private fun appendToolResponse(
        conversation: Conversation,
        call: ToolCallInfo,
        content: String,
        callbacks: Callbacks,
        isError: Boolean,
        fileChange: FileChange? = null
    ) {
        val toolMsg = ChatMessage(
            role = "tool",
            toolCallId = call.id,
            toolName = call.name,
            content = content,
            isError = isError,
            fileChange = fileChange
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

    /** Stable prefix only. Dynamic profiles and catalogs are append-only environment context. */
    private fun buildBaseSystemPrompt(): String = buildString {
        appendLine("你是一个运行在用户 Android 手机上的 AI Agent。你可以调用工具来完成任务：")
        appendLine("- 文件工具：相对路径以每次请求末尾运行环境中的当前工作目录为准；已保存附件仍使用其工作区引用。")
        appendLine("- 长期记忆：重要信息（用户偏好、项目状态、关键结论）主动用 save_memory 保存；不确定时用 search_memory 检索。")
        appendLine("- 权限、目录范围和工具可用性以每次请求末尾的运行环境为准。工具的实际执行会再次进行硬性校验；越权拒绝不是系统故障，不要重复调用。")
        appendLine()
        append("回复使用与用户相同的语言。工具执行结果不理想时换策略重试，不要盲目重复同一调用。")
    }

    private fun renderEnvironment(
        availableToolNames: List<String>,
        agentProfile: AgentProfile?,
        appConfig: AppConfig,
        session: PermissionSession
    ): String = buildString {
        val orderedTools = Tools.ALL_NAMES.filter { it in availableToolNames }
        val allowed = orderedTools.filter { session.toolBlockReason(it) == null }
        val blocked = orderedTools.filter { it !in allowed }
        appendLine("[应用运行环境：本消息为本次请求的权威状态，若与较早环境消息冲突，以本消息为准]")
        appendLine("当前权限模式：${session.conversation.permissionMode}")
        appendLine("权限说明：${session.modePrompt().replace('\n', ' ')}")
        appendLine("运行时允许的工具：${allowed.joinToString(", ").ifBlank { "无" }}")
        appendLine("运行时拒绝的工具：${blocked.joinToString(", ").ifBlank { "无" }}")
        blocked.forEach { name -> appendLine("- $name：${session.toolBlockReason(name)}") }
        appendLine("记忆与 Skill 专用工具使用独立的应用托管目录，不受文件工具目录范围限制；仍遵守当前模式的写入限制和 Agent 工具配置。普通文件工具只可使用当前工作目录与额外目录的并集；shell 命令按其独立的模式、审批和 Android 权限规则执行。")
        appendLine("规范工具清单保持稳定；运行时限制由执行层硬性执行。")
        appendLine("规范工作区：${store.workspaceFile(".").canonicalPath}")
        appendLine("当前工作目录：${session.workingDirectoryDescription()}（相对文件路径和未指定 cwd 的 shell 命令均从此处开始）")
        appendLine("共享存储基准路径：/storage/emulated/0（通常也可写作 /sdcard；实际 Android 访问仍受系统与用户授权限制）")
        appendLine(com.example.myapplication.diagnostics.RuntimeDiagnostics.storageAccessSummary())
        appendLine("EACCES/EPERM 是操作系统访问拒绝，不代表存在文件名保护规则；请确认系统授权和文件来源，不要反复改模式或改名试探。")
        appendLine("文件工具有效范围（工作目录与额外目录并集）：${session.scopeDescription()}")
        appendLine("shell 命令不受文件工具目录范围约束；仍受权限模式、用户审批和 Android 系统权限约束，且不提供目录沙箱。")
        appendLine("计划文件：${session.planPath}（${session.planFile().canonicalPath}）")
        appendLine("当前 Agent 系统配置：${agentProfile?.systemPrompt?.takeIf { it.isNotBlank() } ?: "默认 Agent"}")
        if (Tools.USE_SKILL in orderedTools) {
            appendLine("可用 Skills：")
            val skills = store.listSkills().sortedWith(compareBy({ it.name }, { it.description }))
            if (skills.isEmpty()) appendLine("- 无") else skills.forEach { appendLine("- ${it.name}: ${it.description}") }
        }
        if (Tools.RUN_SUBAGENT in orderedTools) {
            appendLine("可用模型配置：")
            val providers = appConfig.providers.sortedBy { it.id }
            if (providers.isEmpty()) appendLine("- 无") else providers.forEach { provider ->
                val label = provider.name.ifBlank { provider.type.label }
                val models = provider.models.sorted().joinToString(", ")
                appendLine("- $label: $models")
            }
        }
    }.trimEnd()

    private fun appendEnvironmentIfChanged(
        conversation: Conversation,
        environment: String,
        callbacks: Callbacks
    ) {
        if (hasIncompleteToolBatch(conversation)) return
        // Compacted environments are absent from the actual provider request.
        val previous = ContextWindows.replay(conversation).lastOrNull {
            it.contextKind == ENVIRONMENT_CONTEXT_KIND && !it.excludedFromContext
        }
        if (previous?.content == environment) return
        val message = ChatMessage(
            role = "user",
            content = environment,
            contextKind = ENVIRONMENT_CONTEXT_KIND
        )
        conversation.messages += message
        callbacks.onMessageAdded(message)
        store.saveConversation(conversation)
    }

    private fun hasIncompleteToolBatch(conversation: Conversation): Boolean {
        val assistantIndex = conversation.messages.indexOfLast { it.role == "assistant" && it.toolCalls.isNotEmpty() }
        if (assistantIndex < 0) return false
        val returned = conversation.messages.drop(assistantIndex + 1)
            .filter { it.role == "tool" }
            .mapNotNull { it.toolCallId }
            .toSet()
        return conversation.messages[assistantIndex].toolCalls.any { it.id !in returned }
    }

    /** Removes only the user input belonging to an empty failed request; display history remains. */
    private fun excludeRejectedRequestContext(
        conversation: Conversation,
        assistant: ChatMessage,
        callbacks: Callbacks
    ) {
        val assistantIndex = conversation.messages.indexOfFirst { it.id == assistant.id }
        if (assistantIndex < 0) return
        val previousAssistant = (assistantIndex - 1 downTo 0)
            .firstOrNull { conversation.messages[it].role == "assistant" }
            ?: -1
        val rejectedUserIndices = (assistantIndex - 1 downTo previousAssistant + 1)
            .filter { index ->
                val message = conversation.messages[index]
                message.role == "user" && message.contextKind == null && !message.excludedFromContext
            }
        rejectedUserIndices.forEach { rejectedUserIndex ->
            val rejectedUser = conversation.messages[rejectedUserIndex].copy(excludedFromContext = true)
            conversation.messages[rejectedUserIndex] = rejectedUser
            callbacks.onMessageUpdated(rejectedUser)
        }
        val rejectedAssistant = assistant.copy(excludedFromContext = true)
        replaceMessage(conversation, rejectedAssistant)
        callbacks.onMessageUpdated(rejectedAssistant)
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 750L
        const val ENVIRONMENT_CONTEXT_KIND = "environment"
    }
}

internal fun generationStopError(reason: String?): String? = when (reason?.lowercase()) {
    "length", "max_tokens", "max_output_tokens" ->
        "输出被上游截断：达到单次输出 token 上限或剩余上下文不足。思考也可能占用输出额度；已保留收到的内容，可在模型配置调高最大输出 tokens 后继续。"
    "insufficient_system_resource", "aborted" -> "上游因资源不足或请求中止而停止生成，已保留收到的内容。"
    "error" -> "模型流返回错误"
    else -> null
}

/** Narrow migration recovery for messages persisted before request-context exclusion existed. */
object ConversationContext {
    fun recoverRejectedAttachments(conversation: Conversation): Boolean {
        var changed = false
        for (index in 0 until conversation.messages.lastIndex) {
            val user = conversation.messages[index]
            val assistant = conversation.messages[index + 1]
            if (user.role != "user" || user.attachments.none { it.delivery == "native" } || user.excludedFromContext ||
                assistant.role != "assistant" || assistant.excludedFromContext || !isEmptyFailure(assistant)) {
                continue
            }
            conversation.messages[index] = user.copy(excludedFromContext = true)
            conversation.messages[index + 1] = assistant.copy(excludedFromContext = true)
            changed = true
        }
        return changed
    }

    private fun isEmptyFailure(message: ChatMessage): Boolean {
        val content = message.content.trim()
        return message.isError && content.startsWith("⚠") && !content.contains("已停止") &&
            !content.contains("\n\n⚠") && message.thinking.isBlank() && message.toolCalls.isEmpty()
    }
}
