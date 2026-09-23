package com.example.myapplication.ui.chat

import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.FileChange

internal data class SessionArtifact(val messageId: String, val change: FileChange)

/** Each saved change is a historical record, even when its current file no longer exists. */
internal fun sessionArtifacts(messages: List<ChatMessage>): List<SessionArtifact> {
    val calls = messages.flatMap { it.toolCalls }.associateBy { it.id }
    return messages.mapNotNull { message ->
        val tool = calls[message.toolCallId]?.name ?: message.toolName
        val change = message.fileChange
        if (message.role == "tool" && !message.isError && change != null &&
            tool in setOf(Tools.WRITE_FILE, Tools.EDIT_FILE)) {
            SessionArtifact(message.id, change)
        } else null
    }
}
