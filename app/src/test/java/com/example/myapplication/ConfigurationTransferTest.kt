package com.example.myapplication

import com.example.myapplication.data.backup.ConfigurationTransfer
import com.example.myapplication.data.backup.ImportMode
import com.example.myapplication.data.backup.TransferKind
import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.store.FileStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ConfigurationTransferTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(name: String) = FileStore(tmp.newFolder(name)).also { it.saveAgents(emptyList()) }

    private val provider = ProviderConfig(
        id = "provider-1", name = "测试 Provider", baseUrl = "https://example.test/v1",
        model = "test-model", apiKey = "private-key", modelsUrl = "https://example.test/catalog",
        extraHeaders = mapOf("Authorization" to "Bearer header-secret", "X-Custom-Secret" to "custom-secret")
    )
    private val agent = AgentProfile(id = "agent-1", name = "测试 Agent", providerId = provider.id, model = provider.model)

    private fun providerBytes(source: FileStore, includeKeys: Boolean, ids: Set<String>? = null): ByteArray =
        ByteArrayOutputStream().also { ConfigurationTransfer(source).exportProviders(it, ids, includeKeys) }.toByteArray()

    private fun agentBytes(source: FileStore, includeChats: Boolean): ByteArray =
        ByteArrayOutputStream().also { ConfigurationTransfer(source).exportAgents(it, setOf(agent.id), includeChats) }.toByteArray()

    private fun importBytes(target: FileStore, bytes: ByteArray, kind: TransferKind, mode: ImportMode = ImportMode.UPDATE) {
        val transfer = ConfigurationTransfer(target)
        val prepared = transfer.prepareImport(ByteArrayInputStream(bytes), kind)
        transfer.importPrepared(prepared, mode)
    }

    @Test
    fun `provider export excludes keys and arbitrary custom headers by default choice`() {
        val source = store("source")
        source.saveConfig(AppConfig(providers = listOf(provider)))
        val bytes = providerBytes(source, false)
        val text = bytes.toString(Charsets.UTF_8)
        assertFalse(text.contains("private-key"))
        assertFalse(text.contains("header-secret"))
        assertFalse(text.contains("custom-secret"))
        val target = store("target")
        importBytes(target, bytes, TransferKind.PROVIDERS)
        val restored = target.loadConfig().providers.single()
        assertEquals(provider.copy(apiKey = "", extraHeaders = emptyMap()), restored)
    }

    @Test
    fun `provider export with keys restores all fields and selected provider`() {
        val source = store("source")
        source.saveConfig(AppConfig(providers = listOf(provider), selectedProviderId = provider.id))
        val target = store("target")
        target.saveConfig(AppConfig(themeMode = "dark", maxAgentLoops = 19))
        importBytes(target, providerBytes(source, true), TransferKind.PROVIDERS)
        assertEquals(provider, target.loadConfig().providers.single())
        assertEquals(provider.id, target.loadConfig().selectedProviderId)
        assertEquals("dark", target.loadConfig().themeMode)
        assertEquals(19, target.loadConfig().maxAgentLoops)
    }

    @Test
    fun `update without keys preserves local credentials and does not duplicate`() {
        val source = store("source")
        source.saveConfig(AppConfig(providers = listOf(provider.copy(name = "新名称"))))
        val target = store("target")
        target.saveConfig(AppConfig(providers = listOf(provider.copy(apiKey = "local-key"))))
        val bytes = providerBytes(source, false)
        repeat(2) { importBytes(target, bytes, TransferKind.PROVIDERS) }
        val restored = target.loadConfig().providers.single()
        assertEquals("local-key", restored.apiKey)
        assertEquals(provider.extraHeaders, restored.extraHeaders)
        assertEquals("新名称", restored.name)
    }

    @Test
    fun `copy conflicting provider keeps original and does not borrow its key`() {
        val source = store("source")
        source.saveConfig(AppConfig(providers = listOf(provider)))
        val target = store("target")
        target.saveConfig(AppConfig(providers = listOf(provider), selectedProviderId = provider.id))
        importBytes(target, providerBytes(source, false), TransferKind.PROVIDERS, ImportMode.COPY)
        val configs = target.loadConfig().providers
        assertEquals(2, configs.size)
        assertEquals(provider, configs.first { it.id == provider.id })
        assertEquals("", configs.first { it.id != provider.id }.apiKey)
        assertEquals(provider.id, target.loadConfig().selectedProviderId)
    }

    @Test
    fun `single provider export includes only selection`() {
        val source = store("source")
        source.saveConfig(AppConfig(providers = listOf(provider, provider.copy(id = "other"))))
        val target = store("target")
        importBytes(target, providerBytes(source, true, setOf("other")), TransferKind.PROVIDERS)
        assertEquals("other", target.loadConfig().providers.single().id)
    }

    @Test
    fun `agent avatar and owned conversations round trip without provider configs`() {
        val source = store("source")
        source.saveConfig(AppConfig(providers = listOf(provider)))
        val image = byteArrayOf(1, 2, 3, 4)
        val avatarPath = source.saveAgentAvatar(agent.id, ByteArrayInputStream(image))
        source.saveAgents(listOf(agent.copy(avatarPath = avatarPath)))
        source.saveConversation(Conversation(id = "owned", agentId = agent.id, messages = mutableListOf(ChatMessage(role = "user", content = "hello"))))
        source.saveConversation(Conversation(id = "unrelated", agentId = "other"))
        val bytes = agentBytes(source, true)
        val entries = unzip(bytes)
        val manifest = entries.getValue("agents.json").toString(Charsets.UTF_8)
        assertFalse(manifest.contains(avatarPath))
        assertFalse(manifest.contains("private-key"))
        assertFalse(manifest.contains(provider.baseUrl))
        val target = store("target")
        val manager = ConfigurationTransfer(target)
        val prepared = manager.prepareImport(ByteArrayInputStream(bytes), TransferKind.AGENTS)
        assertEquals(1, prepared.preview.missingProviderCount)
        assertEquals(1, prepared.preview.conversationCount)
        manager.importPrepared(prepared, ImportMode.UPDATE)
        val restored = target.loadAgents().single()
        assertArrayEquals(image, File(checkNotNull(restored.avatarPath)).readBytes())
        assertTrue(restored.avatarPath!!.startsWith(target.avatarsDir.absolutePath))
        assertEquals(agent.providerId, restored.providerId)
        assertEquals("hello", target.loadConversation("owned")!!.messages.single().content)
        assertNull(target.loadConversation("unrelated"))
        assertTrue(target.loadConfig().providers.isEmpty())
    }

    @Test
    fun `excluding conversations neither exports them nor deletes local conversations`() {
        val source = store("source")
        source.saveAgents(listOf(agent))
        source.saveConversation(Conversation(id = "source-chat", agentId = agent.id))
        val target = store("target")
        target.saveConversation(Conversation(id = "local-chat", agentId = agent.id))
        importBytes(target, agentBytes(source, false), TransferKind.AGENTS)
        assertNull(target.loadConversation("source-chat"))
        assertNotNull(target.loadConversation("local-chat"))
    }

    @Test
    fun `copy remaps colliding agent conversation and avatar while preserving originals`() {
        val source = store("source")
        val path = source.saveAgentAvatar(agent.id, ByteArrayInputStream(byteArrayOf(9)))
        source.saveAgents(listOf(agent.copy(avatarPath = path)))
        source.saveConversation(Conversation(id = "chat", agentId = agent.id, title = "导入会话"))
        val target = store("target")
        target.saveAgents(listOf(agent.copy(name = "本机 Agent")))
        target.saveConversation(Conversation(id = "chat", agentId = agent.id, title = "本机会话"))
        val transfer = ConfigurationTransfer(target)
        val prepared = transfer.prepareImport(ByteArrayInputStream(agentBytes(source, true)), TransferKind.AGENTS)
        assertEquals(2, prepared.preview.conflicts.size)
        transfer.importPrepared(prepared, ImportMode.COPY)
        val imported = target.loadAgents().single { it.id != agent.id }
        assertEquals(imported.id, target.listConversations().single { it.id != "chat" }.agentId)
        assertEquals("本机会话", target.loadConversation("chat")!!.title)
        assertEquals("本机 Agent", target.loadAgents().single { it.id == agent.id }.name)
        assertArrayEquals(byteArrayOf(9), File(imported.avatarPath!!).readBytes())
    }

    @Test
    fun `preview is read only even when default agents have not yet been written`() {
        val source = store("source")
        source.saveAgents(listOf(agent))
        val targetRoot = tmp.newFolder("fresh")
        val target = FileStore(targetRoot)
        ConfigurationTransfer(target).prepareImport(ByteArrayInputStream(agentBytes(source, false)), TransferKind.AGENTS)
        assertFalse(File(targetRoot, "agents.json").exists())
        assertFalse(target.configFile.exists())
    }

    @Test
    fun `invalid zip paths and missing avatars are rejected without changing data`() {
        val source = store("source")
        source.saveAgents(listOf(agent))
        val target = store("target")
        target.saveAgents(listOf(agent.copy(name = "保留")))
        val entries = unzip(agentBytes(source, false))
        entries["../escape"] = byteArrayOf(1)
        assertRejected(target, zip(entries), TransferKind.AGENTS)
        entries.remove("../escape")
        entries["agents.json"] = entries.getValue("agents.json").toString(Charsets.UTF_8)
            .replace("\"avatarPath\": null", "\"avatarPath\": \"avatars/agent-1.png\"").toByteArray()
        assertRejected(target, zip(entries), TransferKind.AGENTS)
        assertEquals("保留", target.loadAgents().single().name)
    }

    @Test
    fun `invalid IDs duplicates and unsupported versions are rejected before writes`() {
        val source = store("source")
        source.saveConfig(AppConfig(providers = listOf(provider)))
        val target = store("target")
        target.saveConfig(AppConfig(providers = listOf(provider.copy(name = "保留"))))
        val valid = providerBytes(source, true).toString(Charsets.UTF_8)
        listOf(
            valid.replace("provider-1", "../escape"),
            valid.replace("\"version\": 1", "\"version\": 99"),
            "{}"
        ).forEach { assertRejected(target, it.toByteArray(), TransferKind.PROVIDERS) }
        source.saveConfig(AppConfig(providers = listOf(provider, provider)))
        assertRejected(target, providerBytes(source, true), TransferKind.PROVIDERS)
        assertEquals("保留", target.loadConfig().providers.single().name)
    }

    private fun assertRejected(target: FileStore, bytes: ByteArray, kind: TransferKind) {
        try {
            ConfigurationTransfer(target).prepareImport(ByteArrayInputStream(bytes), kind)
            fail("Invalid import was accepted")
        } catch (_: IllegalArgumentException) {
            // 格式、结构与路径错误都必须在提交之前被拒绝。
        } catch (_: IllegalStateException) {
        }
    }

    private fun unzip(bytes: ByteArray): MutableMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
