package com.example.myapplication.agent

import com.example.myapplication.data.model.*

/** Local estimates deliberately remain separate from upstream billing/usage counts. */
object ContextWindows {
    fun replay(conversation: Conversation): List<ChatMessage> {
        val compact = conversation.contextCompaction
        val covered = compact?.coveredMessageIds?.toHashSet().orEmpty()
        val active = conversation.messages.filter { !it.excludedFromContext && it.id !in covered }
        return if (compact == null) active else listOf(ChatMessage(
            id = "summary-${compact.id}", role = "user", contextKind = "compaction",
            timestamp = compact.createdAt,
            content = "以下是较早对话的摘要，仅用于延续任务；权限以当前运行环境为准。原始消息保留在会话记录中。\n${compact.summary}"
        )) + active
    }

    fun estimate(text: String): Long {
        var wide = 0L
        var narrow = 0L
        text.forEach { if (it.code > 0x2ff) wide++ else narrow++ }
        return wide + (narrow + 3) / 4
    }

    fun overview(conversation: Conversation, config: ProviderConfig, agentProfile: AgentProfile?): ContextOverview {
        val counts = linkedMapOf("system" to 0L, "tools" to 0L, "environment" to 0L,
            "user" to 0L, "assistant" to 0L, "results" to 0L, "attachments" to 0L, "summary" to 0L)
        counts["system"] = estimate(conversation.systemPromptSnapshot ?: agentProfile?.systemPrompt.orEmpty())
        val selected = agentProfile?.tools.orEmpty()
        counts["tools"] = Tools.ALL.filter { (selected.isEmpty() || it.name in selected || it.name == Tools.GET_SESSION_STATE) &&
            (conversation.parentConversationId == null || it.name !in setOf(Tools.RUN_SUBAGENT, Tools.ENTER_PLAN_MODE, Tools.EXIT_PLAN_MODE))
        }.sumOf { estimate(it.name + it.description + it.parameters.toString()) + 8 }
        replay(conversation).forEach { message ->
            val key = when {
                message.contextKind == "compaction" -> "summary"
                message.contextKind != null -> "environment"
                message.role == "tool" -> "results"
                message.role == "assistant" -> "assistant"
                else -> "user"
            }
            val thinking = if (config.type in setOf(ProviderType.ANTHROPIC, ProviderType.GEMINI)) message.thinking else ""
            counts[key] = counts.getValue(key) + estimate(message.content + thinking) + 6 +
                message.toolCalls.sumOf { estimate(it.name + it.argumentsJson) + 8 }
            counts["attachments"] = counts.getValue("attachments") + message.attachments.sumOf {
                estimate(it.name + it.workspacePath) + if (it.delivery == "native") {
                    if (it.mimeType.startsWith("image/")) 1024L else 2048L
                } else 0L
            }
        }
        val labels = listOf("系统提示", "工具声明", "运行环境", "用户消息", "模型回复", "工具结果", "附件估算", "历史摘要")
        return ContextOverview(config.contextWindowFor(), counts.entries.mapIndexed { i, e ->
            ContextSegment(e.key, labels[i], e.value)
        }, conversation.lastContextUsage, conversation.contextCompaction?.coveredMessageIds?.size ?: 0)
    }

    /** Do not split parallel tool batches or detach media produced by a tool. */
    internal fun compressionBoundary(messages: List<ChatMessage>): Int {
        val desired = (messages.size - 6).coerceAtLeast(1)
        return (desired downTo 1).firstOrNull { boundary ->
            val prefix = messages.take(boundary)
            val suffix = messages.drop(boundary)
            val calls = prefix.flatMap { it.toolCalls }.map { it.id }.toSet()
            boundary < messages.size && prefix.any { it.contextKind == null } &&
                calls.all { call -> prefix.any { it.toolCallId == call } } &&
                suffix.none { it.toolCallId in calls || it.originToolCallId in calls }
        } ?: 0
    }
}
