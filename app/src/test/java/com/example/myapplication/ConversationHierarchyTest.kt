package com.example.myapplication

import com.example.myapplication.agent.SubagentRunner
import com.example.myapplication.data.backup.ConfigurationTransfer
import com.example.myapplication.data.backup.ImportMode
import com.example.myapplication.data.backup.TransferKind
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ApiProvider
import com.example.myapplication.provider.ProviderFactory
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.provider.StreamEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ConversationHierarchyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `root list hides valid children but keeps missing parents and cycles reachable`() {
        val store = FileStore(tmp.newFolder("hierarchy"))
        store.saveConversation(Conversation(id = "parent", title = "父"))
        store.saveConversation(Conversation(id = "child", title = "子", parentConversationId = "parent"))
        store.saveConversation(Conversation(id = "orphan", title = "孤儿", parentConversationId = "missing"))
        store.saveConversation(Conversation(id = "cycle-a", title = "循环 A", parentConversationId = "cycle-b"))
        store.saveConversation(Conversation(id = "cycle-b", title = "循环 B", parentConversationId = "cycle-a"))

        assertEquals(setOf("parent", "orphan", "cycle-a", "cycle-b"), store.listRootConversations().map { it.id }.toSet())
        assertEquals(listOf("child"), store.listChildConversations("parent").map { it.id })

        store.deleteConversation("parent")
        assertEquals(setOf("orphan", "cycle-a", "cycle-b"), store.listConversations().map { it.id }.toSet())

        // A malformed cycle is also safe to delete as a finite set.
        store.deleteConversation("cycle-a")
        assertEquals(setOf("orphan"), store.listConversations().map { it.id }.toSet())
    }

    @Test
    fun `copy conversation export remaps parent and referenced attachment`() {
        val source = FileStore(tmp.newFolder("source"))
        val target = FileStore(tmp.newFolder("target"))
        val oldPath = "attachments/old-id/report.txt"
        val content = "child attachment".toByteArray()
        source.workspaceFile(oldPath).apply {
            parentFile?.mkdirs()
            writeBytes(content)
        }
        source.saveConversation(Conversation(id = "parent", title = "源父"))
        val childAttachment = MessageAttachment(
            id = "attachment-id",
            name = "report.txt",
            mimeType = "text/plain",
            sizeBytes = content.size.toLong(),
            workspacePath = oldPath
        )
        source.saveConversation(
            Conversation(
                id = "child",
                title = "源子",
                parentConversationId = "parent",
                messages = mutableListOf(ChatMessage(role = "user", content = "see file", attachments = listOf(childAttachment),
                    fileChange = com.example.myapplication.data.model.FileChange(path = oldPath, after = "child attachment")))
            )
        )
        // Force only the imported parent ID to collide; the child mapping must still follow it.
        target.saveConversation(Conversation(id = "parent", title = "本地父"))

        val output = ByteArrayOutputStream()
        ConfigurationTransfer(source).exportRecords(output, TransferKind.CONVERSATIONS, setOf("parent"))
        val transfer = ConfigurationTransfer(target)
        val prepared = transfer.prepareImport(ByteArrayInputStream(output.toByteArray()), TransferKind.CONVERSATIONS)
        transfer.importPrepared(prepared, ImportMode.COPY)

        val importedParent = target.listConversations().single { it.id != "parent" && it.title == "源父" }
        val importedChild = target.listConversations().single { it.title == "源子" }
        assertEquals(importedParent.id, importedChild.parentConversationId)
        assertNotEquals(oldPath, importedChild.messages.single().attachments.single().workspacePath)
        assertEquals(importedChild.messages.single().attachments.single().workspacePath,
            importedChild.messages.single().fileChange!!.path)
        assertTrue(importedChild.messages.single().attachments.single().workspacePath.startsWith("attachments/"))
        assertEquals(content.toList(), target.workspaceFile(importedChild.messages.single().attachments.single().workspacePath).readBytes().toList())
    }

    @Test
    fun `agent export includes child without agent profile and imports its parent`() {
        val source = FileStore(tmp.newFolder("agent-source"))
        val target = FileStore(tmp.newFolder("agent-target"))
        source.saveAgents(listOf(com.example.myapplication.data.model.AgentProfile(id = "agent", name = "agent")))
        source.saveConversation(Conversation(id = "parent", agentId = "agent"))
        source.saveConversation(Conversation(id = "child", parentConversationId = "parent", executionStatus = "running"))
        val output = ByteArrayOutputStream()
        ConfigurationTransfer(source).exportAgents(output, setOf("agent"), true)
        val transfer = ConfigurationTransfer(target)
        val prepared = transfer.prepareImport(ByteArrayInputStream(output.toByteArray()), TransferKind.AGENTS)
        transfer.importPrepared(prepared, ImportMode.COPY)
        assertEquals("parent", target.loadConversation("child")!!.parentConversationId)
        assertEquals("cancelled", target.loadConversation("child")!!.executionStatus)
        assertEquals(setOf("parent"), target.listRootConversations().map { it.id }.toSet())
    }

    @Test
    fun `startup recovery changes only running children`() {
        val store = FileStore(tmp.newFolder("recovery"))
        store.saveConversation(Conversation(id = "parent"))
        store.saveConversation(Conversation(id = "active", parentConversationId = "parent", executionStatus = "running"))
        store.saveConversation(Conversation(id = "done", parentConversationId = "parent", executionStatus = "completed"))
        assertEquals(1, store.recoverInterruptedSubagents())
        assertEquals("cancelled", store.loadConversation("active")!!.executionStatus)
        assertEquals("completed", store.loadConversation("done")!!.executionStatus)
        assertEquals(0, store.recoverInterruptedSubagents())
    }

    @Test
    fun `cancelled child persists parent call and actual model override`() = runBlocking {
        val store = FileStore(tmp.newFolder("cancel"))
        val started = CompletableDeferred<Unit>()
        val provider = object : ApiProvider {
            override suspend fun streamChat(
                config: ProviderConfig,
                system: String,
                messages: List<ChatMessage>,
                tools: List<com.example.myapplication.provider.ToolSpec>,
                onEvent: suspend (StreamEvent) -> Unit
            ) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val factory = object : ProviderFactory() {
            override fun create(type: ProviderType): ApiProvider = provider
        }
        val inherited = ProviderConfig(id = "provider", model = "actual-model")
        val runner = SubagentRunner(store, factory)
        val job = launch {
            runner.run(
                task = "长任务",
                providerName = null,
                model = null,
                inherited = inherited,
                parentConversationId = "parent-conversation",
                parentToolCallId = "tool-call-42"
            )
        }
        started.await()
        val running = store.listChildConversations("parent-conversation").single()
        assertEquals("running", running.executionStatus)
        assertEquals("tool-call-42", running.parentToolCallId)
        assertEquals("actual-model", running.modelOverride)
        assertEquals("provider", running.providerIdOverride)

        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        val cancelled = store.loadConversation(running.id)
        assertNotNull(cancelled)
        assertEquals("cancelled", cancelled!!.executionStatus)
        assertFalse(cancelled.messages.none { it.content.contains("已停止") })
    }
}
