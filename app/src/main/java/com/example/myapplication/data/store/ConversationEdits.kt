package com.example.myapplication.data.store

import com.example.myapplication.data.model.ChatMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** User-requested history edits preserve tool protocol pairs and do not repeat side effects. */
object ConversationEdits {
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
