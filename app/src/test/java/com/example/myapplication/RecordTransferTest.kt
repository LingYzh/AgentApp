package com.example.myapplication

import com.example.myapplication.data.backup.ConfigurationTransfer
import com.example.myapplication.data.backup.ImportMode
import com.example.myapplication.data.backup.TransferKind
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.store.FileStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RecordTransferTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store(name: String) = FileStore(tmp.newFolder(name)).also { it.saveAgents(emptyList()) }
    private fun export(store: FileStore, kind: TransferKind, ids: Set<String>): ByteArray =
        ByteArrayOutputStream().also { ConfigurationTransfer(store).exportRecords(it, kind, ids) }.toByteArray()

    @Test
    fun `conversation subset round trips messages and references without bundled configuration`() {
        val source = store("source")
        val chat = Conversation(id = "chat-a", title = "选中会话", agentId = "missing-agent",
            providerIdOverride = "missing-provider", messages = mutableListOf(ChatMessage(role = "user", content = "你好")))
        source.saveConversation(chat)
        source.saveConversation(Conversation(id = "unselected"))
        val target = store("target")
        val transfer = ConfigurationTransfer(target)
        val bytes = export(source, TransferKind.CONVERSATIONS, setOf(chat.id))
        val prepared = transfer.prepareImport(ByteArrayInputStream(bytes), TransferKind.CONVERSATIONS)
        assertTrue(target.listConversations().isEmpty())
        assertEquals(1, prepared.preview.missingAgentCount)
        assertEquals(1, prepared.preview.missingProviderCount)
        transfer.importPrepared(prepared, ImportMode.UPDATE)
        assertEquals(listOf(chat), target.listConversations())
        assertTrue(target.loadAgents().isEmpty())
        assertTrue(target.loadConfig().providers.isEmpty())
        transfer.importPrepared(transfer.prepareImport(ByteArrayInputStream(bytes), TransferKind.CONVERSATIONS), ImportMode.UPDATE)
        assertEquals(1, target.listConversations().size)
        transfer.importPrepared(prepared, ImportMode.COPY)
        assertEquals(2, target.listConversations().size)
        assertTrue(target.listConversations().all { it.agentId == chat.agentId && it.messages == chat.messages })
    }

    @Test
    fun `memory import preserves content metadata and unrelated records with update or copy`() {
        val source = store("source")
        val entry = source.saveMemory("记忆标题", "# 原文\n\n多行内容\n", "memory-a")
        source.saveMemory("不导出", "另一个正文", "unselected")
        val bytes = export(source, TransferKind.MEMORIES, setOf(entry.id))
        val target = store("target")
        target.saveMemory("旧标题", "旧正文", entry.id)
        val unrelated = target.saveMemory("保留", "保留正文", "local")
        val transfer = ConfigurationTransfer(target)
        val prepared = transfer.prepareImport(ByteArrayInputStream(bytes), TransferKind.MEMORIES)
        assertEquals(1, prepared.preview.conflicts.size)
        assertEquals("旧正文", target.readMemory(entry.id))
        transfer.importPrepared(prepared, ImportMode.UPDATE)
        assertEquals(entry, target.listMemories().first { it.id == entry.id })
        assertEquals(unrelated, target.listMemories().first { it.id == "local" })
        assertEquals(source.readMemory(entry.id), target.readMemory(entry.id))
        transfer.importPrepared(prepared, ImportMode.COPY)
        val copy = target.listMemories().single { it.id !in setOf(entry.id, "local") }
        assertEquals(entry.createdAt, copy.createdAt)
        assertEquals(entry.updatedAt, copy.updatedAt)
        assertEquals(source.readMemory(entry.id), target.readMemory(copy.id))
    }

    @Test
    fun `wrong kinds unsupported versions and unsafe ids are rejected without writes`() {
        val source = store("source")
        source.saveMemory("标题", "正文", "memory-a")
        val bytes = export(source, TransferKind.MEMORIES, setOf("memory-a"))
        val target = store("target")
        val transfer = ConfigurationTransfer(target)
        val json = bytes.toString(Charsets.UTF_8)
        listOf(
            json to TransferKind.CONVERSATIONS,
            json.replace("memory-a", "../escape") to TransferKind.MEMORIES,
            json.replace("\"version\": 1", "\"version\": 999") to TransferKind.MEMORIES
        ).forEach { (text, kind) ->
            assertThrows(IllegalArgumentException::class.java) {
                transfer.prepareImport(ByteArrayInputStream(text.toByteArray()), kind)
            }
        }
        assertTrue(target.listMemories().isEmpty())
        assertTrue(target.listConversations().isEmpty())
        assertFalse(target.memoryDir.resolve("index.json").exists())
    }
}
