package com.example.myapplication

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.data.store.ConversationEdits
import org.junit.Assert.*
import org.junit.Test

class ConversationEditsTest {
    @Test fun `explicit retry re includes only its original excluded user`() {
        val older = ChatMessage(role = "user", content = "older", excludedFromContext = true)
        val user = ChatMessage(role = "user", content = "retry me", excludedFromContext = true)
        val failure = ChatMessage(role = "assistant", content = "network error", isError = true)
        val revised = ConversationEdits.truncateForRegeneration(listOf(older, user, failure), failure.id)
        assertTrue(revised.first().excludedFromContext)
        assertFalse(revised.last().excludedFromContext)
        assertEquals(user.id, revised.last().id)
    }

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

    @Test fun `replyGroups groups by real user and aggregates multi-loop tools and media`() {
        val user = ChatMessage(role = "user", content = "do something")
        val asst1 = ChatMessage(role = "assistant", content = "step 1", toolCalls = listOf(ToolCallInfo("call_1", "read_file", "{}")))
        val tool1 = ChatMessage(role = "tool", toolCallId = "call_1", content = "file text")
        val media1 = ChatMessage(role = "user", originToolCallId = "call_1", content = "tool image")
        val asst2 = ChatMessage(role = "assistant", content = "step 2 done")

        val groups = ConversationEdits.replyGroups(listOf(user, asst1, tool1, media1, asst2))
        assertEquals(1, groups.size)

        val group = groups.single()
        assertEquals(user, group.userMessage)
        assertEquals(listOf(asst1, asst2), group.assistantMessages)
        assertEquals(setOf("call_1"), group.toolCallIds)
        assertEquals(setOf(tool1.id, media1.id), group.toolMessageIds)
        assertEquals(asst2.id, group.lastAssistantId)
        assertEquals("step 1\n\nstep 2 done", group.aggregatedContent)
        assertTrue(group.isLatest)
    }

    @Test fun `replyGroups ignores hidden environment messages and does not split groups`() {
        val user = ChatMessage(role = "user", content = "query")
        val env1 = ChatMessage(role = "user", contextKind = "environment", content = "env snapshot 1")
        val asst1 = ChatMessage(role = "assistant", content = "checking", toolCalls = listOf(ToolCallInfo("call_x", "run_cmd", "{}")))
        val tool1 = ChatMessage(role = "tool", toolCallId = "call_x", content = "output")
        val env2 = ChatMessage(role = "user", contextKind = "environment", content = "env snapshot 2")
        val asst2 = ChatMessage(role = "assistant", content = "all clear")

        val groups = ConversationEdits.replyGroups(listOf(user, env1, asst1, tool1, env2, asst2))
        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(user, group.userMessage)
        assertEquals(listOf(asst1, asst2), group.assistantMessages)
        assertFalse(group.toolMessageIds.contains(env1.id))
        assertFalse(group.toolMessageIds.contains(env2.id))
        assertEquals(asst2.id, group.lastAssistantId)
    }

    @Test fun `replyGroups marks only true tail turn as isLatest`() {
        val u1 = ChatMessage(role = "user", content = "first")
        val a1 = ChatMessage(role = "assistant", content = "first reply")
        val u2 = ChatMessage(role = "user", content = "second")
        val a2 = ChatMessage(role = "assistant", content = "second reply")

        val groups = ConversationEdits.replyGroups(listOf(u1, a1, u2, a2))
        assertEquals(2, groups.size)
        assertFalse(groups[0].isLatest)
        assertTrue(groups[1].isLatest)

        val u3 = ChatMessage(role = "user", content = "third pending")
        val groupsWithTrailingUser = ConversationEdits.replyGroups(listOf(u1, a1, u2, a2, u3))
        assertEquals(2, groupsWithTrailingUser.size)
        assertFalse(groupsWithTrailingUser[0].isLatest)
        assertFalse(groupsWithTrailingUser[1].isLatest)
    }

    @Test fun `deleteReply deletes entire reply including tools and media but keeps starting user and environment`() {
        val u1 = ChatMessage(role = "user", content = "task")
        val env = ChatMessage(role = "user", contextKind = "environment", content = "mode")
        val a1 = ChatMessage(role = "assistant", content = "reading", toolCalls = listOf(ToolCallInfo("c1", "read_file", "{}")))
        val t1 = ChatMessage(role = "tool", toolCallId = "c1", content = "content")
        val m1 = ChatMessage(role = "user", originToolCallId = "c1", content = "media")
        val a2 = ChatMessage(role = "assistant", content = "done")
        val messages = listOf(u1, env, a1, t1, m1, a2)

        val result = ConversationEdits.deleteReply(messages, a1.id)
        assertEquals(listOf(u1, env), result)
    }

    @Test fun `deleteReply only deletes target turn leaving other turn tool pairs intact`() {
        val u1 = ChatMessage(role = "user", content = "turn 1")
        val a1 = ChatMessage(role = "assistant", content = "call 1", toolCalls = listOf(ToolCallInfo("c1", "write_file", "{}")))
        val t1 = ChatMessage(role = "tool", toolCallId = "c1", content = "ok")
        val u2 = ChatMessage(role = "user", content = "turn 2")
        val a2 = ChatMessage(role = "assistant", content = "call 2", toolCalls = listOf(ToolCallInfo("c2", "read_file", "{}")))
        val t2 = ChatMessage(role = "tool", toolCallId = "c2", content = "ok")
        val messages = listOf(u1, a1, t1, u2, a2, t2)

        val afterDeletingTurn1 = ConversationEdits.deleteReply(messages, a1.id)
        assertEquals(listOf(u1, u2, a2, t2), afterDeletingTurn1)

        val afterDeletingTurn2 = ConversationEdits.deleteReply(messages, a2.id)
        assertEquals(listOf(u1, a1, t1, u2), afterDeletingTurn2)
    }

    @Test fun `editReply applies multi segment assistant edits and preserves tool calls and thinking signatures`() {
        val json = com.example.myapplication.provider.ProviderJson
        fun block(raw: String) = json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
        val thought = block("""{"type":"thinking","thinking":"think","signature":"sig123"}""")
        val toolBlock = block("""{"type":"tool_use","id":"c1","name":"read_file","input":{}}""")

        val u1 = ChatMessage(role = "user", content = "query")
        val a1 = ChatMessage(
            role = "assistant",
            content = "old part 1",
            toolCalls = listOf(ToolCallInfo("c1", "read_file", "{}")),
            providerBlocks = mapOf("anthropic" to listOf(thought, block("""{"type":"text","text":"old part 1"}"""), toolBlock))
        )
        val t1 = ChatMessage(role = "tool", toolCallId = "c1", content = "ok")
        val a2 = ChatMessage(role = "assistant", content = "old part 2")

        val edits = mapOf(a1.id to "new part 1", a2.id to "new part 2")
        val updated = ConversationEdits.editReply(listOf(u1, a1, t1, a2), edits)

        val updatedA1 = updated.first { it.id == a1.id }
        assertEquals("new part 1", updatedA1.content)
        assertEquals(listOf(ToolCallInfo("c1", "read_file", "{}")), updatedA1.toolCalls)
        val blocks = updatedA1.providerBlocks.getValue("anthropic")
        assertEquals(thought, blocks[0])
        assertEquals(block("""{"type":"text","text":"new part 1"}"""), blocks[1])
        assertEquals(toolBlock, blocks[2])

        val updatedA2 = updated.first { it.id == a2.id }
        assertEquals("new part 2", updatedA2.content)
        assertEquals(t1, updated.first { it.id == t1.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `editReply rejects editing non-assistant message`() {
        val user = ChatMessage(role = "user", content = "hello")
        ConversationEdits.editReply(listOf(user), mapOf(user.id to "fake edit"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `editReply rejects blank text when assistant has no tool calls`() {
        val asst = ChatMessage(role = "assistant", content = "hello")
        ConversationEdits.editReply(listOf(asst), mapOf(asst.id to "   "))
    }

    @Test fun `truncateForRegeneration keeps latest user and marks environment messages excluded`() {
        val u1 = ChatMessage(role = "user", content = "u1")
        val a1 = ChatMessage(role = "assistant", content = "a1")
        val env = ChatMessage(role = "user", contextKind = "environment", content = "env snapshot", excludedFromContext = false)
        val u2 = ChatMessage(role = "user", content = "u2")
        val a2 = ChatMessage(role = "assistant", content = "a2", toolCalls = listOf(ToolCallInfo("c2", "read_file", "{}")))
        val t2 = ChatMessage(role = "tool", toolCallId = "c2", content = "res")
        val messages = listOf(u1, a1, env, u2, a2, t2)

        val truncated = ConversationEdits.truncateForRegeneration(messages)
        assertEquals(4, truncated.size)
        assertEquals(u1, truncated[0])
        assertEquals(a1, truncated[1])
        val envInTruncated = truncated.find { it.id == env.id }
        assertNotNull(envInTruncated)
        assertTrue(envInTruncated!!.excludedFromContext)
        assertEquals(u2, truncated.last())
    }

    @Test fun `truncateForRegeneration handles turn that failed before assistant reply`() {
        val u1 = ChatMessage(role = "user", content = "u1")
        val a1 = ChatMessage(role = "assistant", content = "a1")
        val env = ChatMessage(role = "user", contextKind = "environment", content = "env snapshot", excludedFromContext = false)
        val u2 = ChatMessage(role = "user", content = "u2 without reply")
        val messages = listOf(u1, a1, env, u2)

        val truncated = ConversationEdits.truncateForRegeneration(messages)
        assertEquals(4, truncated.size)
        assertEquals(u2, truncated.last())
        assertTrue(truncated.find { it.id == env.id }!!.excludedFromContext)
    }

    @Test fun `branchAt user cuts off at user message and returns fresh conversation with current config`() {
        val u1 = ChatMessage(role = "user", content = "u1")
        val a1 = ChatMessage(role = "assistant", content = "a1")
        val env = ChatMessage(role = "user", contextKind = "environment", content = "env", excludedFromContext = false)
        val u2 = ChatMessage(role = "user", content = "u2")
        val a2 = ChatMessage(role = "assistant", content = "a2")

        val source = Conversation(
            id = "conv_source",
            title = "源会话",
            createdAt = 1000L,
            agentId = "agent_coder",
            providerIdOverride = "prov_openai",
            modelOverride = "gpt-4o",
            parentConversationId = "parent_123",
            parentToolCallId = "call_parent",
            executionStatus = "running",
            stopReason = "some reason",
            permissionMode = PermissionMode.AUTO,
            allowedDirectories = listOf("/workspace/extra"),
            workingDirectory = "/workspace",
            messages = mutableListOf(u1, a1, env, u2, a2)
        )

        val branched = ConversationEdits.branchAt(source, targetMessageId = u2.id, newId = "conv_branch", nowMs = 5000L)
        assertNotEquals(source.id, branched.id)
        assertEquals("conv_branch", branched.id)
        assertEquals(5000L, branched.createdAt)
        assertEquals("源会话 (分支)", branched.title)
        assertEquals("agent_coder", branched.agentId)
        assertEquals("prov_openai", branched.providerIdOverride)
        assertEquals("gpt-4o", branched.modelOverride)
        assertEquals(PermissionMode.AUTO, branched.permissionMode)
        assertEquals(listOf("/workspace/extra"), branched.allowedDirectories)
        assertEquals("/workspace", branched.workingDirectory)
        assertNull(branched.parentConversationId)
        assertNull(branched.parentToolCallId)
        assertNull(branched.executionStatus)
        assertNull(branched.stopReason)
        assertNull(branched.contextCompaction)
        assertNull(branched.lastContextUsage)

        assertEquals(3, branched.messages.size)
        assertEquals(u1, branched.messages[0])
        assertEquals(a1, branched.messages[1])
        assertTrue(branched.messages[2].excludedFromContext)
        assertFalse(branched.messages.any { it.id == u2.id })

        // Branching at the very first user message leaves an empty history
        val branchedAtFirst = ConversationEdits.branchAt(source, targetMessageId = u1.id)
        assertTrue(branchedAtFirst.messages.isEmpty())

        // Verify source is unchanged
        assertEquals("conv_source", source.id)
        assertEquals(5, source.messages.size)
        assertFalse(source.messages[2].excludedFromContext)
    }

    @Test fun `branchAt assistant reply cuts off at end of full reply including tools`() {
        val u1 = ChatMessage(role = "user", content = "u1")
        val a1_1 = ChatMessage(role = "assistant", content = "a1_1", toolCalls = listOf(ToolCallInfo("c1", "write_file", "{}")))
        val t1 = ChatMessage(role = "tool", toolCallId = "c1", content = "ok")
        val a1_2 = ChatMessage(role = "assistant", content = "a1_2 done")
        val u2 = ChatMessage(role = "user", content = "u2")
        val a2 = ChatMessage(role = "assistant", content = "a2")

        val source = Conversation(
            id = "conv_source",
            messages = mutableListOf(u1, a1_1, t1, a1_2, u2, a2)
        )

        // Branching at a1_1 (the first assistant segment) cuts off at the END of the whole reply (a1_2)
        val branched = ConversationEdits.branchAt(source, targetMessageId = a1_1.id)
        assertEquals(4, branched.messages.size)
        assertEquals(listOf(u1.id, a1_1.id, t1.id, a1_2.id), branched.messages.map { it.id })
    }

    @Test fun `replyGroup aggregatedContent preserves indentation without trimming`() {
        val u = ChatMessage(role = "user", content = "test")
        val a1 = ChatMessage(role = "assistant", content = "    fun foo() {\n        return 1\n    }")
        val a2 = ChatMessage(role = "assistant", content = "   ")
        val a3 = ChatMessage(role = "assistant", content = "  done  ")
        val groups = ConversationEdits.replyGroups(listOf(u, a1, a2, a3))
        assertEquals(1, groups.size)
        assertEquals("    fun foo() {\n        return 1\n    }\n\n  done  ", groups.single().aggregatedContent)
    }
}
