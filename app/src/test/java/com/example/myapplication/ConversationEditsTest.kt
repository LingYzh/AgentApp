package com.example.myapplication

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.data.store.ConversationEdits
import org.junit.Assert.*
import org.junit.Test

class ConversationEditsTest {
    @Test fun `editing text preserves opaque thinking signatures and tool blocks`() {
        val json = com.example.myapplication.provider.ProviderJson
        fun block(raw: String) = json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
        val thought = block("""{"type":"thinking","thinking":"reason","signature":"opaque"}""")
        val tool = block("""{"type":"tool_use","id":"call","name":"read_file","input":{}}""")
        val original = ChatMessage(role = "assistant", content = "old", providerBlocks = mapOf(
            "anthropic" to listOf(thought, block("""{"type":"text","text":"old"}"""), tool)))
        val edited = ConversationEdits.edit(listOf(original), original.id, "new", emptySet(), true).single()
        val blocks = edited.providerBlocks.getValue("anthropic")
        assertEquals(thought, blocks[0])
        assertEquals(block("""{"type":"text","text":"new"}"""), blocks[1])
        assertEquals(tool, blocks[2])
    }

    @Test fun `delete assistant removes only its protocol dependents`() {
        val user = ChatMessage(role = "user", content = "go")
        val assistant = ChatMessage(role = "assistant", toolCalls = listOf(ToolCallInfo("call", "read_file", "{}")))
        val result = ChatMessage(role = "tool", toolCallId = "call")
        val media = ChatMessage(role = "user", originToolCallId = "call")
        val environment = ChatMessage(role = "user", contextKind = "environment", content = "mode")
        val final = ChatMessage(role = "assistant", content = "done")
        assertEquals(listOf(user, environment, final), ConversationEdits.delete(listOf(user, assistant, result, media, environment, final), assistant.id))
    }

    @Test fun `edit failed attachment can remove attachment and restore text context`() {
        val attachment = MessageAttachment(name = "bad.pdf", mimeType = "application/pdf", sizeBytes = 3, workspacePath = "attachments/bad.pdf")
        val message = ChatMessage(role = "user", content = "bad", attachments = listOf(attachment), excludedFromContext = true)
        val edited = ConversationEdits.edit(listOf(message), message.id, "fixed", emptySet(), true).single()
        assertEquals(message.id, edited.id)
        assertEquals("fixed", edited.content)
        assertTrue(edited.attachments.isEmpty())
        assertFalse(edited.excludedFromContext)
    }

    @Test fun `editing assistant text keeps executed calls paired`() {
        val assistant = ChatMessage(role = "assistant", content = "old", toolCalls = listOf(ToolCallInfo("call", "write_file", "{}")))
        val result = ChatMessage(role = "tool", toolCallId = "call", content = "done")
        val changed = ConversationEdits.edit(listOf(assistant, result), assistant.id, "new", emptySet(), true)
        assertEquals(assistant.toolCalls, changed.first().toolCalls)
        assertEquals(result, changed.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `internal environment cannot be user edited`() {
        val env = ChatMessage(role = "user", contextKind = "environment")
        ConversationEdits.edit(listOf(env), env.id, "fake mode", emptySet(), true)
    }
}
