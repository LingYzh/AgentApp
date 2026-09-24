package com.example.myapplication.data.store

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 聚合单次交互中助手的所有回复、工具调用与结果，供操作栏、编辑、重新生成与分支使用 */
data class ReplyGroup(
    val userMessage: ChatMessage?,
    val assistantMessages: List<ChatMessage>,
    val toolCallIds: Set<String> = emptySet(),
    val toolMessageIds: Set<String> = emptySet(),
    val isLatest: Boolean = false
) {
    val lastAssistantId: String? get() = assistantMessages.lastOrNull()?.id
    val aggregatedContent: String get() = assistantMessages
        .map { it.content }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

/** User-requested history edits preserve tool protocol pairs and do not repeat side effects. */
object ConversationEdits {
    fun isRealUserMessage(msg: ChatMessage): Boolean =
        msg.role == "user" && msg.contextKind == null && msg.originToolCallId == null

    /** 按真实 user (非 contextKind/originToolCallId) 分组 */
    fun replyGroups(messages: List<ChatMessage>): List<ReplyGroup> {
        val groups = mutableListOf<ReplyGroup>()
        var currentUserMessage: ChatMessage? = null
        val currentAssistantMessages = mutableListOf<ChatMessage>()
        val currentToolCallIds = mutableSetOf<String>()
        val currentToolMessageIds = mutableSetOf<String>()

        fun flushGroup() {
            if (currentAssistantMessages.isNotEmpty()) {
                groups.add(
                    ReplyGroup(
                        userMessage = currentUserMessage,
                        assistantMessages = currentAssistantMessages.toList(),
                        toolCallIds = currentToolCallIds.toSet(),
                        toolMessageIds = currentToolMessageIds.toSet(),
                        isLatest = false
                    )
                )
                currentAssistantMessages.clear()
                currentToolCallIds.clear()
                currentToolMessageIds.clear()
            }
        }

        for (msg in messages) {
            if (msg.contextKind != null) continue
            if (isRealUserMessage(msg)) {
                flushGroup()
                currentUserMessage = msg
            } else if (msg.role == "assistant") {
                currentAssistantMessages.add(msg)
                currentToolCallIds.addAll(msg.toolCalls.map { it.id })
            } else if (msg.role == "tool") {
                currentToolMessageIds.add(msg.id)
                msg.toolCallId?.let { currentToolCallIds.add(it) }
            } else if (msg.originToolCallId != null) {
                currentToolMessageIds.add(msg.id)
                currentToolCallIds.add(msg.originToolCallId)
            }
            // contextKind != null (environment/compaction) is internal context and ignored for grouping
        }
        flushGroup()

        if (groups.isEmpty()) return emptyList()

        val lastGroup = groups.last()
        val allGroupMsgIds = (lastGroup.assistantMessages.map { it.id } + lastGroup.toolMessageIds).toSet()
        val lastGroupMsgIndex = messages.indexOfLast { it.id in allGroupMsgIds }
        val hasSubsequentRealUser = if (lastGroupMsgIndex >= 0) {
            messages.subList(lastGroupMsgIndex + 1, messages.size).any { isRealUserMessage(it) }
        } else false

        val isLastGroupLatest = !hasSubsequentRealUser

        return groups.mapIndexed { index, group ->
            if (index == groups.lastIndex && isLastGroupLatest) {
                group.copy(isLatest = true)
            } else {
                group
            }
        }
    }

    /** 整组删除回复，保留起始 user 消息及其他会话内容 */
    fun deleteReply(messages: List<ChatMessage>, targetGroup: ReplyGroup): List<ChatMessage> {
        val assistantIds = targetGroup.assistantMessages.map { it.id }.toSet()
        val toolCallIds = targetGroup.toolCallIds
        val toolMessageIds = targetGroup.toolMessageIds
        return messages.filterNot { msg ->
            msg.id in assistantIds ||
                msg.id in toolMessageIds ||
                (msg.role == "tool" && msg.toolCallId in toolCallIds) ||
                (msg.originToolCallId != null && msg.originToolCallId in toolCallIds)
        }
    }

    fun deleteReply(messages: List<ChatMessage>, targetAssistantId: String): List<ChatMessage> {
        val groups = replyGroups(messages)
        val targetGroup = groups.firstOrNull { group ->
            group.assistantMessages.any { it.id == targetAssistantId }
        } ?: return messages
        return deleteReply(messages, targetGroup)
    }

    /** 按 assistant id map 编辑，保留 tool pair 及 providerBlocks 中 opaque signatures */
    fun editReply(messages: List<ChatMessage>, edits: Map<String, String>): List<ChatMessage> {
        if (edits.isEmpty()) return messages
        return messages.map { msg ->
            val newText = edits[msg.id] ?: return@map msg
            require(msg.role == "assistant" && msg.contextKind == null && msg.originToolCallId == null) {
                "仅能编辑助手回复"
            }
            require(newText.isNotBlank() || msg.toolCalls.isNotEmpty()) {
                "消息内容不能为空"
            }
            if (newText == msg.content) msg
            else msg.copy(
                content = newText,
                providerBlocks = editVisibleBlocks(msg.providerBlocks, newText)
            )
        }
    }

    /** 只允许末尾用户对应回复，保留该 user 及之前历史，将历史环境快照标记为 excluded */
    fun truncateForRegeneration(messages: List<ChatMessage>, targetReplyId: String? = null): List<ChatMessage> {
        val groups = replyGroups(messages)
        val latestGroup = groups.lastOrNull { it.isLatest }
        val targetGroup = if (targetReplyId != null) {
            val found = groups.firstOrNull { g ->
                g.assistantMessages.any { it.id == targetReplyId } ||
                    targetReplyId in g.toolMessageIds ||
                    g.userMessage?.id == targetReplyId
            }
            if (found != null) {
                require(found.isLatest) { "仅允许重新生成最新回复" }
                found
            } else {
                val lastMsg = messages.lastOrNull()
                if (lastMsg?.id == targetReplyId && isRealUserMessage(lastMsg)) null
                else error("未找到可重新生成的回复: $targetReplyId")
            }
        } else {
            latestGroup
        }

        val base = if (targetGroup != null) {
            requireNotNull(targetGroup.userMessage) { "未找到对应的用户输入" }
            deleteReply(messages, targetGroup)
        } else {
            val lastMsg = messages.lastOrNull()
            require(lastMsg != null && isRealUserMessage(lastMsg)) { "没有可重新生成的回复或输入" }
            messages
        }
        val retryUserId = base.lastOrNull(::isRealUserMessage)?.id
        return base.map { msg ->
            when {
                // A failed request can exclude its unsent user input. Explicit retry
                // must re-include that input, without reviving other excluded history.
                msg.id == retryUserId -> msg.copy(excludedFromContext = false, isError = false)
                msg.contextKind == "environment" -> msg.copy(excludedFromContext = true)
                else -> msg
            }
        }
    }

    /**
     * 从指定消息处分支：保留到 user 或整个 reply 结束（含工具结果），
     * 返回新 Conversation（新 id 与时间），保留当前权限、目录、模型与 Agent，
     * 清除 parent、执行状态与压缩用量，历史环境快照标记为 excluded。
     */
    fun branchAt(
        source: Conversation,
        targetMessageId: String,
        newId: String = UUID.randomUUID().toString(),
        nowMs: Long = System.currentTimeMillis()
    ): Conversation {
        val targetIndex = source.messages.indexOfFirst { it.id == targetMessageId }
        require(targetIndex >= 0) { "目标消息不存在: $targetMessageId" }
        val targetMsg = source.messages[targetIndex]

        val cutoffIndex = if (isRealUserMessage(targetMsg)) {
            targetIndex - 1
        } else {
            val groups = replyGroups(source.messages)
            val targetGroup = groups.firstOrNull { g ->
                g.assistantMessages.any { it.id == targetMessageId } || targetMessageId in g.toolMessageIds
            } ?: error("未找到所属回复组")
            val groupMsgIds = (targetGroup.assistantMessages.map { it.id } + targetGroup.toolMessageIds).toSet()
            source.messages.indexOfLast { it.id in groupMsgIds }
        }

        val retained = if (cutoffIndex >= 0) source.messages.subList(0, cutoffIndex + 1) else emptyList()
        val revisedMessages = retained.map { msg ->
            if (msg.contextKind == "environment" && !msg.excludedFromContext) {
                msg.copy(excludedFromContext = true)
            } else {
                msg
            }
        }

        return Conversation(
            id = newId,
            title = "${source.title} (分支)",
            createdAt = nowMs,
            agentId = source.agentId,
            providerIdOverride = source.providerIdOverride,
            modelOverride = source.modelOverride,
            reasoningEffortOverride = source.reasoningEffortOverride,
            contextCompaction = null,
            lastContextUsage = null,
            messages = revisedMessages.toMutableList(),
            parentConversationId = null,
            parentToolCallId = null,
            executionStatus = null,
            stopReason = null,
            permissionMode = source.permissionMode,
            allowedDirectories = source.allowedDirectories.toList(),
            workingDirectory = source.workingDirectory,
            systemPromptSnapshot = source.systemPromptSnapshot
        )
    }

    fun branchAt(
        source: Conversation,
        targetGroup: ReplyGroup,
        newId: String = UUID.randomUUID().toString(),
        nowMs: Long = System.currentTimeMillis()
    ): Conversation = branchAt(
        source = source,
        targetMessageId = targetGroup.lastAssistantId ?: targetGroup.assistantMessages.first().id,
        newId = newId,
        nowMs = nowMs
    )

    fun edit(
        messages: List<ChatMessage>, id: String, text: String,
        attachmentIds: Set<String>, includeInContext: Boolean
    ): List<ChatMessage> = messages.map { message ->
        if (message.id != id) message else {
            require(message.contextKind == null && message.originToolCallId == null &&
                message.role in setOf("user", "assistant")) { "此消息不能编辑" }
            val attachments = message.attachments.filter { it.id in attachmentIds }
            require(text.isNotBlank() || attachments.isNotEmpty() || message.toolCalls.isNotEmpty()) { "消息不能为空" }
            message.copy(content = text, attachments = attachments,
                providerBlocks = if (text == message.content) message.providerBlocks else editVisibleBlocks(message.providerBlocks, text),
                excludedFromContext = !includeInContext, isError = if (includeInContext) false else message.isError)
        }
    }

    fun delete(messages: List<ChatMessage>, id: String): List<ChatMessage> {
        val target = messages.firstOrNull { it.id == id } ?: return messages
        require(target.contextKind == null && target.originToolCallId == null &&
            target.role in setOf("user", "assistant")) { "此消息不能删除" }
        val calls = target.toolCalls.map { it.id }.toSet()
        return messages.filterNot { it.id == id ||
            (it.role == "tool" && it.toolCallId in calls) ||
            (it.originToolCallId != null && it.originToolCallId in calls) }
    }

    /** Keep opaque signatures/tool blocks intact while applying the user's visible text edit. */
    internal fun editVisibleBlocks(protocols: Map<String, List<JsonObject>>, text: String): Map<String, List<JsonObject>> =
        protocols.mapValues { (protocol, blocks) ->
            if (protocol !in setOf("anthropic", "gemini")) return@mapValues blocks
            var inserted = false
            val revised = blocks.mapNotNull { block ->
                val isText = if (protocol == "anthropic") block["type"]?.jsonPrimitive?.content == "text"
                    else block["text"] != null && block["thought"]?.jsonPrimitive?.booleanOrNull != true &&
                        !(block["text"]?.jsonPrimitive?.content.orEmpty().isEmpty() && block.containsKey("thoughtSignature"))
                if (!isText) block else {
                    val replacement = if (!inserted) text else ""
                    inserted = true
                    if (replacement.isNotBlank()) JsonObject(block + ("text" to JsonPrimitive(replacement)))
                    else if (protocol == "gemini" && block.containsKey("thoughtSignature")) JsonObject(block + ("text" to JsonPrimitive("")))
                    else null
                }
            }
            if (inserted || text.isBlank()) revised else revised + JsonObject(buildMap {
                if (protocol == "anthropic") put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(text))
            })
        }
}
