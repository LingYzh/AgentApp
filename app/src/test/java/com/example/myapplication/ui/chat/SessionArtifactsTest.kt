package com.example.myapplication.ui.chat

import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.model.ToolCallInfo
import org.junit.Assert.*
import org.junit.Test

class SessionArtifactsTest {
    @Test fun usesSavedSuccessfulWritesOnlyWithoutLookingUpCurrentFiles() {
        val snapshot = FileChange("/missing/output.md", after = "saved")
        val messages = listOf(
            ChatMessage(role = "assistant", toolCalls = listOf(ToolCallInfo("write", Tools.WRITE_FILE, "{}"))),
            ChatMessage(id = "saved", role = "tool", toolCallId = "write", fileChange = snapshot),
            ChatMessage(role = "tool", toolName = Tools.EDIT_FILE, isError = true, fileChange = snapshot),
            ChatMessage(role = "tool", toolName = Tools.READ_FILE, fileChange = snapshot),
            ChatMessage(role = "tool", toolName = Tools.WRITE_FILE),
            ChatMessage(role = "assistant", toolName = Tools.WRITE_FILE, fileChange = snapshot)
        )
        assertEquals(listOf(SessionArtifact("saved", snapshot)), sessionArtifacts(messages))
    }

    @Test fun repeatedEditsKeepTheirOwnChronologicalSnapshotsAndOmissionFlags() {
        val before = FileChange("same.txt", after = "one")
        val after = FileChange("same.txt", before = "one", after = "two", beforeExists = true, previewOmitted = true)
        val artifacts = sessionArtifacts(listOf(
            ChatMessage(id = "first", role = "tool", toolName = Tools.WRITE_FILE, fileChange = before),
            ChatMessage(id = "second", role = "tool", toolName = Tools.EDIT_FILE, fileChange = after)
        ))
        assertEquals(listOf("first", "second"), artifacts.map { it.messageId })
        assertEquals("one", artifacts.first().change.after)
        assertTrue(artifacts.last().change.previewOmitted)
    }

    @Test fun associatedCallIsAuthoritativeOverLegacyToolName() {
        val messages = listOf(
            ChatMessage(role = "assistant", toolCalls = listOf(ToolCallInfo("read", Tools.READ_FILE, "{}"))),
            ChatMessage(role = "tool", toolCallId = "read", toolName = Tools.WRITE_FILE,
                fileChange = FileChange("not-a-write.txt"))
        )
        assertTrue(sessionArtifacts(messages).isEmpty())
    }
}
