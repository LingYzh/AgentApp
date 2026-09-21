package com.example.myapplication.data.backup

import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.DefaultAgents
import com.example.myapplication.data.model.MemoryEntry
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.store.FileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class TransferKind { PROVIDERS, AGENTS, CONVERSATIONS, MEMORIES }
enum class ImportMode { UPDATE, COPY }

data class TransferPreview(
    val kind: TransferKind,
    val itemCount: Int,
    val conversationCount: Int,
    val conflicts: List<String>,
    val missingProviderCount: Int,
    val includesApiKeys: Boolean,
    val missingAgentCount: Int = 0
)

data class TransferResult(val providers: Int = 0, val agents: Int = 0, val conversations: Int = 0, val memories: Int = 0)

class PreparedImport internal constructor(
    val preview: TransferPreview,
    internal val providerArchive: ProviderArchive? = null,
    internal val agentArchive: AgentArchive? = null,
    internal val avatars: Map<String, ByteArray> = emptyMap(),
    internal val recordArchive: RecordArchive? = null
)

@Serializable
internal data class ProviderArchive(
    val format: String,
    val version: Int,
    val includesApiKeys: Boolean,
    val providers: List<ProviderConfig>,
    val selectedProviderId: String? = null
)

@Serializable
internal data class AgentArchive(
    val format: String,
    val version: Int,
    val includesConversations: Boolean,
    val agents: List<AgentProfile>,
    val conversations: List<Conversation>
)

@Serializable
internal data class MemoryRecord(val entry: MemoryEntry, val content: String)

@Serializable
internal data class RecordArchive(
    val format: String,
    val version: Int,
    val conversations: List<Conversation> = emptyList(),
    val memories: List<MemoryRecord> = emptyList()
)

/** 独立配置迁移：先完整读取和校验，再由用户选择冲突处理方式后写入。 */
class ConfigurationTransfer(private val store: FileStore) {
    private val root get() = store.configFile.parentFile!!
    private val agentsFile get() = File(root, "agents.json")

    fun exportProviders(output: OutputStream, providerIds: Set<String>?, includeApiKeys: Boolean) {
        val config = store.loadConfig()
        val selected = select(config.providers, providerIds) { it.id }
        val archive = ProviderArchive(
            PROVIDER_FORMAT, VERSION, includeApiKeys,
            selected.map { if (includeApiKeys) it else withoutCredentials(it) },
            config.selectedProvider?.id?.takeIf { id -> selected.any { it.id == id } }
        )
        val bytes = store.json.encodeToString(archive).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TOTAL_BYTES) { "导出配置过大，请分批导出" }
        output.write(bytes)
    }

    fun exportAgents(output: OutputStream, agentIds: Set<String>?, includeConversations: Boolean) {
        val selected = select(readAgents(), agentIds) { it.id }
        validateIds(selected.map { it.id })
        val selectedIds = selected.map { it.id }.toSet()
        val conversations = if (includeConversations) {
            store.listConversations().filter { it.agentId in selectedIds }
        } else emptyList()
        val avatarFiles = selected.mapNotNull { agent ->
            agent.avatarPath?.let { File(it) }?.takeIf { it.isFile }?.let { file ->
                require(file.canonicalPath.startsWith(store.avatarsDir.canonicalPath + File.separator)) {
                    "Agent 头像不在应用头像目录内"
                }
                require(file.length() <= MAX_AVATAR_BYTES) { "头像过大，无法导出" }
                agent.id to file
            }
        }.toMap()
        val archive = AgentArchive(
            AGENT_FORMAT, VERSION, includeConversations,
            selected.map { it.copy(avatarPath = if (it.id in avatarFiles) avatarEntry(it.id) else null) },
            conversations
        )
        val manifest = store.json.encodeToString(archive).toByteArray(Charsets.UTF_8)
        require(manifest.size.toLong() + avatarFiles.values.sumOf { it.length() } <= MAX_TOTAL_BYTES) {
            "导出配置包过大，请减少 Agent 数量或不包含会话"
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            avatarFiles.forEach { (id, file) ->
                zip.putNextEntry(ZipEntry(avatarEntry(id)))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun prepareImport(input: InputStream, kind: TransferKind): PreparedImport = try {
        when (kind) {
            TransferKind.PROVIDERS -> prepareProviders(input)
            TransferKind.AGENTS -> prepareAgents(input)
            TransferKind.CONVERSATIONS, TransferKind.MEMORIES -> prepareRecords(input, kind)
        }
    } catch (error: SerializationException) {
        // 序列化异常可能携带原始 JSON 片段，避免把凭据显示在错误提示中。
        throw IllegalArgumentException("配置文件内容无效，请选择本应用导出的配置文件", error)
    }

    fun importPrepared(prepared: PreparedImport, mode: ImportMode): TransferResult = when (prepared.preview.kind) {
        TransferKind.PROVIDERS -> importProviders(checkNotNull(prepared.providerArchive), mode)
        TransferKind.AGENTS -> importAgents(checkNotNull(prepared.agentArchive), prepared.avatars, mode)
        TransferKind.CONVERSATIONS, TransferKind.MEMORIES -> importRecords(checkNotNull(prepared.recordArchive), prepared.preview.kind, mode)
    }

    fun exportRecords(output: OutputStream, kind: TransferKind, ids: Set<String>) {
        val archive = when (kind) {
            TransferKind.CONVERSATIONS -> RecordArchive(CONVERSATION_FORMAT, VERSION,
                conversations = select(store.listConversations(), ids) { it.id })
            TransferKind.MEMORIES -> RecordArchive(MEMORY_FORMAT, VERSION,
                memories = select(store.listMemories(), ids) { it.id }.map { entry ->
                    validateIds(listOf(entry.id))
                    MemoryRecord(entry, store.readMemory(entry.id) ?: error("记忆正文缺失，无法导出"))
                })
            else -> error("不支持的记录类型")
        }
        val bytes = store.json.encodeToString(archive).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TOTAL_BYTES) { "导出记录过大，请分批导出" }
        output.write(bytes)
    }

    private fun prepareRecords(input: InputStream, kind: TransferKind): PreparedImport {
        val archive = store.json.decodeFromString<RecordArchive>(readLimited(input, MAX_TOTAL_BYTES).toString(Charsets.UTF_8))
        val isChat = kind == TransferKind.CONVERSATIONS
        require(archive.version == VERSION && archive.format == if (isChat) CONVERSATION_FORMAT else MEMORY_FORMAT) {
            "不支持的记录文件或版本"
        }
        require(if (isChat) archive.memories.isEmpty() else archive.conversations.isEmpty()) { "记录类型与文件内容不一致" }
        val ids = if (isChat) archive.conversations.map { it.id } else archive.memories.map { it.entry.id }
        require(ids.isNotEmpty()) { "文件中没有可导入的记录" }
        validateIds(ids)
        val local = if (isChat) store.listConversations().associate { it.id to it.title }
            else store.listMemories().associate { it.id to it.title }
        val incoming = if (isChat) archive.conversations.map { it.id to it.title }
            else archive.memories.map { it.entry.id to it.entry.title }
        val conflicts = incoming.filter { it.first in local }.map { (id, title) ->
            conflictLabel(if (isChat) "会话" else "记忆", title, local.getValue(id))
        }
        val providerIds = store.loadConfig().providers.map { it.id }.toSet()
        val agentIds = if (isChat) readAgents().map { it.id }.toSet() else emptySet()
        return PreparedImport(
            preview = TransferPreview(kind, ids.size, if (isChat) ids.size else 0, conflicts,
                archive.conversations.mapNotNull { it.providerIdOverride }.distinct().count { it !in providerIds }, false,
                archive.conversations.mapNotNull { it.agentId }.distinct().count { it !in agentIds }),
            recordArchive = archive
        )
    }

    private fun importRecords(archive: RecordArchive, kind: TransferKind, mode: ImportMode): TransferResult {
        val writes = linkedMapOf<File, ByteArray>()
        if (kind == TransferKind.CONVERSATIONS) {
            val existing = store.listConversations().map { it.id }.toSet()
            archive.conversations.forEach { chat ->
                val id = if (mode == ImportMode.COPY && chat.id in existing) UUID.randomUUID().toString() else chat.id
                writes[File(store.conversationsDir, "$id.json")] = store.json.encodeToString(chat.copy(id = id)).toByteArray(Charsets.UTF_8)
            }
        } else {
            val merged = store.listMemories().associateByTo(linkedMapOf()) { it.id }
            archive.memories.forEach { record ->
                val id = if (mode == ImportMode.COPY && record.entry.id in merged) UUID.randomUUID().toString() else record.entry.id
                merged[id] = record.entry.copy(id = id)
                writes[File(store.memoryDir, "$id.md")] = record.content.toByteArray(Charsets.UTF_8)
            }
            writes[File(store.memoryDir, "index.json")] = store.json.encodeToString(merged.values.toList()).toByteArray(Charsets.UTF_8)
        }
        writeTransaction(writes)
        return TransferResult(conversations = archive.conversations.size, memories = archive.memories.size)
    }

    private fun prepareProviders(input: InputStream): PreparedImport {
        val archive = store.json.decodeFromString<ProviderArchive>(readLimited(input, MAX_TOTAL_BYTES).toString(Charsets.UTF_8))
        require(archive.format == PROVIDER_FORMAT && archive.version == VERSION) { "不支持的 Provider 配置文件或版本" }
        require(archive.providers.isNotEmpty()) { "文件中没有 Provider 配置" }
        validateIds(archive.providers.map { it.id })
        val local = store.loadConfig().providers.associateBy { it.id }
        val conflicts = archive.providers.filter { it.id in local }.map {
            val existing = local.getValue(it.id)
            conflictLabel("Provider", it.name.ifBlank { it.model }, existing.name.ifBlank { existing.model })
        }
        return PreparedImport(
            TransferPreview(TransferKind.PROVIDERS, archive.providers.size, 0, conflicts, 0, archive.includesApiKeys),
            providerArchive = archive
        )
    }

    private fun prepareAgents(input: InputStream): PreparedImport {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(entries.size < 10000) { "导入包条目过多" }
                val name = entry.name
                require(!entry.isDirectory && (name == MANIFEST || AVATAR_ENTRY.matches(name))) { "不支持的导入包条目：$name" }
                require(name !in entries) { "导入包中有重复条目：$name" }
                val limit = if (name == MANIFEST) MAX_TOTAL_BYTES - total else minOf(MAX_AVATAR_BYTES, MAX_TOTAL_BYTES - total)
                val bytes = readLimited(zip, limit)
                total += bytes.size
                entries[name] = bytes
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val manifest = entries.remove(MANIFEST) ?: error("导入包缺少 Agent 配置")
        val archive = store.json.decodeFromString<AgentArchive>(manifest.toString(Charsets.UTF_8))
        require(archive.format == AGENT_FORMAT && archive.version == VERSION) { "不支持的 Agent 配置包或版本" }
        require(archive.agents.isNotEmpty()) { "文件中没有 Agent 配置" }
        validateIds(archive.agents.map { it.id })
        validateIds(archive.conversations.map { it.id })
        val agentIds = archive.agents.map { it.id }.toSet()
        require(archive.includesConversations || archive.conversations.isEmpty()) { "会话选项与包内容不一致" }
        require(archive.conversations.all { it.agentId in agentIds }) { "导入包包含不属于这些 Agent 的会话" }
        val avatarNames = archive.agents.mapNotNull { agent ->
            agent.avatarPath?.also { require(it == avatarEntry(agent.id)) { "Agent 头像路径无效" } }
        }.toSet()
        require(entries.keys == avatarNames) { "头像文件与 Agent 配置不匹配" }
        val localAgents = readAgents().associateBy { it.id }
        val localConversations = store.listConversations().associateBy { it.id }
        val conflicts = archive.agents.filter { it.id in localAgents }.map {
            conflictLabel("Agent", it.name, localAgents.getValue(it.id).name)
        } + archive.conversations.filter { it.id in localConversations }.map {
            conflictLabel("会话", it.title, localConversations.getValue(it.id).title)
        }
        val localProviders = store.loadConfig().providers.map { it.id }.toSet()
        val references = (archive.agents.mapNotNull { it.providerId } + archive.conversations.mapNotNull { it.providerIdOverride }).toSet()
        return PreparedImport(
            TransferPreview(TransferKind.AGENTS, archive.agents.size, archive.conversations.size, conflicts,
                references.count { it !in localProviders }, false),
            agentArchive = archive,
            avatars = entries
        )
    }

    private fun importProviders(archive: ProviderArchive, mode: ImportMode): TransferResult {
        val current = store.loadConfig()
        val merged = current.providers.associateByTo(linkedMapOf()) { it.id }
        val idMap = archive.providers.associate { p ->
            p.id to if (mode == ImportMode.COPY && p.id in merged) UUID.randomUUID().toString() else p.id
        }
        archive.providers.forEach { provider ->
            val id = idMap.getValue(provider.id)
            // 不含凭据的导入不会清空本机 Key；导入副本则不挪用被冲突项的凭据。
            val imported = if (archive.includesApiKeys) provider else provider.copy(
                apiKey = merged[id]?.apiKey.orEmpty(),
                extraHeaders = merged[id]?.extraHeaders.orEmpty()
            )
            merged[id] = imported.copy(id = id)
        }
        val selected = current.selectedProviderId?.takeIf { it in merged }
            ?: archive.selectedProviderId?.let { idMap[it] } ?: merged.keys.firstOrNull()
        val config = current.copy(providers = merged.values.toList(), selectedProviderId = selected)
        writeTransaction(mapOf(store.configFile to store.json.encodeToString(config).toByteArray(Charsets.UTF_8)))
        return TransferResult(providers = archive.providers.size)
    }

    private fun importAgents(archive: AgentArchive, avatars: Map<String, ByteArray>, mode: ImportMode): TransferResult {
        val merged = readAgents().associateByTo(linkedMapOf()) { it.id }
        val localConversations = store.listConversations().map { it.id }.toSet()
        val idMap = archive.agents.associate { agent ->
            agent.id to if (mode == ImportMode.COPY && agent.id in merged) UUID.randomUUID().toString() else agent.id
        }
        val writes = linkedMapOf<File, ByteArray>()
        archive.agents.forEach { agent ->
            val id = idMap.getValue(agent.id)
            val avatar = agent.avatarPath?.let { path ->
                val target = File(store.avatarsDir, "$id.png")
                writes[target] = avatars.getValue(path)
                target.absolutePath
            }
            merged[id] = agent.copy(id = id, avatarPath = avatar)
        }
        writes[agentsFile] = store.json.encodeToString(merged.values.toList()).toByteArray(Charsets.UTF_8)
        archive.conversations.forEach { conversation ->
            val id = if (mode == ImportMode.COPY && conversation.id in localConversations) UUID.randomUUID().toString() else conversation.id
            val imported = conversation.copy(id = id, agentId = idMap.getValue(checkNotNull(conversation.agentId)))
            writes[File(store.conversationsDir, "$id.json")] = store.json.encodeToString(imported).toByteArray(Charsets.UTF_8)
        }
        writeTransaction(writes)
        return TransferResult(agents = archive.agents.size, conversations = archive.conversations.size)
    }

    /** 确认前只读，避免 FileStore.loadAgents() 的首次预设写入副作用。 */
    private fun readAgents(): List<AgentProfile> = if (agentsFile.exists()) store.loadAgents() else DefaultAgents.DEFAULT_PROFILES

    private fun withoutCredentials(provider: ProviderConfig) = provider.copy(apiKey = "", extraHeaders = emptyMap())

    private fun conflictLabel(kind: String, incoming: String, existing: String): String =
        if (incoming == existing) "$kind：$incoming" else "$kind：文件「$incoming」 / 本机「$existing」"

    private fun <T> select(items: List<T>, ids: Set<String>?, id: (T) -> String): List<T> {
        val selected = if (ids == null) items else items.filter { id(it) in ids }
        require(selected.isNotEmpty()) { "没有可导出的配置" }
        require(ids == null || selected.size == ids.size) { "所选配置已变化，请刷新后重试" }
        return selected
    }

    private fun validateIds(ids: List<String>) {
        require(ids.all { SAFE_ID.matches(it) }) { "配置或会话 ID 无效" }
        require(ids.distinct().size == ids.size) { "导入文件中存在重复 ID" }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = input.read(buffer)
        while (count != -1) {
            require(output.size().toLong() + count <= limit) { "导入文件过大（配置包最多 64 MiB，单个头像最多 8 MiB）" }
            output.write(buffer, 0, count)
            count = input.read(buffer)
        }
        return output.toByteArray()
    }

    /** 校验全部完成后才写入；普通写入失败时恢复本次涉及的文件，不触碰其它配置。 */
    private fun writeTransaction(writes: Map<File, ByteArray>) {
        val previous = writes.keys.associateWith { if (it.exists()) it.readBytes() else null }
        try {
            writes.forEach { (file, bytes) ->
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
        } catch (error: Exception) {
            previous.forEach { (file, bytes) ->
                try {
                    if (bytes == null) check(!file.exists() || file.delete()) else file.writeBytes(bytes)
                } catch (restoreError: Exception) {
                    error.addSuppressed(restoreError)
                }
            }
            throw IllegalStateException(if (error.suppressed.isEmpty()) "导入写入失败，原数据已恢复" else "导入写入失败，部分原数据恢复失败", error)
        }
    }

    private fun avatarEntry(id: String) = "avatars/$id.png"

    companion object {
        private const val CONVERSATION_FORMAT = "actant.conversations"
        private const val MEMORY_FORMAT = "actant.memories"
        private const val VERSION = 1
        private const val PROVIDER_FORMAT = "actant.providers"
        private const val AGENT_FORMAT = "actant.agents"
        private const val MANIFEST = "agents.json"
        private const val MAX_TOTAL_BYTES = 64 * 1024 * 1024
        private const val MAX_AVATAR_BYTES = 8 * 1024 * 1024
        private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
        private val AVATAR_ENTRY = Regex("avatars/[A-Za-z0-9][A-Za-z0-9_-]{0,127}\\.png")
    }
}
