package com.example.myapplication.data.store

import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.MemoryEntry
import com.example.myapplication.data.model.SkillMeta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 纯文件存储层。所有数据保存在应用私有目录下：
 *   config.json / subagents.json / conversations/ / memory/ / skills/ / workspace/
 * 天然适配 zip 导出导入，无数据库迁移问题。
 */
class FileStore(private val root: File) {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val configFile = File(root, "config.json")
    val conversationsDir = File(root, "conversations")
    val memoryDir = File(root, "memory")
    val skillsDir = File(root, "skills")
    val workspaceDir = File(root, "workspace")
    val backupsDir = File(root, "backups")

    init {
        init()
    }

    /** 确保目录结构存在（导入备份后也可调用重建） */
    fun init() {
        conversationsDir.mkdirs()
        memoryDir.mkdirs()
        skillsDir.mkdirs()
        workspaceDir.mkdirs()
        backupsDir.mkdirs()
    }

    // ---------- 全局配置 ----------

    fun loadConfig(): AppConfig =
        runCatching { json.decodeFromString<AppConfig>(configFile.readText()) }
            .getOrDefault(AppConfig())

    fun saveConfig(config: AppConfig) {
        configFile.writeText(json.encodeToString(config))
    }

    // ---------- Agent 配置 ----------

    private val agentsFile = File(root, "agents.json")

    fun loadAgents(): List<com.example.myapplication.data.model.AgentProfile> =
        runCatching {
            json.decodeFromString<List<com.example.myapplication.data.model.AgentProfile>>(agentsFile.readText())
        }.getOrDefault(emptyList())

    fun saveAgents(list: List<com.example.myapplication.data.model.AgentProfile>) {
        agentsFile.writeText(json.encodeToString(list))
    }

    // ---------- 对话 ----------

    fun listConversations(): List<Conversation> =
        conversationsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.messages.lastOrNull()?.timestamp ?: it.createdAt }
            ?: emptyList()

    fun loadConversation(id: String): Conversation? {
        val f = File(conversationsDir, "$id.json")
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull()
    }

    fun saveConversation(c: Conversation) {
        File(conversationsDir, "${c.id}.json").writeText(json.encodeToString(c))
    }

    fun deleteConversation(id: String) {
        File(conversationsDir, "$id.json").delete()
    }

    // ---------- 记忆 ----------

    private val memoryIndexFile get() = File(memoryDir, "index.json")

    fun listMemories(): List<MemoryEntry> =
        runCatching {
            json.decodeFromString<List<MemoryEntry>>(memoryIndexFile.readText())
        }.getOrDefault(emptyList())

    private fun saveMemoryIndex(list: List<MemoryEntry>) {
        memoryIndexFile.writeText(json.encodeToString(list))
    }

    fun saveMemory(title: String, content: String, id: String? = null): MemoryEntry {
        val list = listMemories().toMutableList()
        val entry = if (id != null) {
            list.firstOrNull { it.id == id }?.also {
                it.title = title
                it.updatedAt = System.currentTimeMillis()
            } ?: MemoryEntry(id = id, title = title)
        } else {
            MemoryEntry(title = title)
        }
        list.removeAll { it.id == entry.id }
        list.add(entry)
        saveMemoryIndex(list)
        File(memoryDir, "${entry.id}.md").writeText(content)
        return entry
    }

    fun readMemory(id: String): String? =
        File(memoryDir, "$id.md").takeIf { it.exists() }?.readText()

    fun deleteMemory(id: String) {
        saveMemoryIndex(listMemories().filterNot { it.id == id })
        File(memoryDir, "$id.md").delete()
    }

    /** 简单子串检索（大小写不敏感），返回条目与正文 */
    fun searchMemory(query: String): List<Pair<MemoryEntry, String>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return listMemories().mapNotNull { entry ->
            val body = readMemory(entry.id) ?: return@mapNotNull null
            if (entry.title.lowercase().contains(q) || body.lowercase().contains(q)) {
                entry to body
            } else null
        }
    }

    // ---------- Skills ----------

    private fun skillFile(name: String) = File(skillsDir, "${sanitizeFileName(name)}.md")

    /** 解析 frontmatter（--- name/description ---）+ 正文 */
    fun listSkills(): List<SkillMeta> =
        skillsDir.listFiles { f -> f.extension == "md" }
            ?.mapNotNull { f ->
                val text = runCatching { f.readText() }.getOrNull() ?: return@mapNotNull null
                val (meta, _) = parseSkill(text)
                val name = meta["name"] ?: f.nameWithoutExtension
                SkillMeta(name = name, description = meta["description"] ?: "")
            }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun readSkill(name: String): String? =
        skillFile(name).takeIf { it.exists() }?.readText()

    fun readSkillBody(name: String): String? =
        readSkill(name)?.let { parseSkill(it).second }

    fun saveSkill(name: String, description: String, body: String) {
        val text = buildString {
            appendLine("---")
            appendLine("name: $name")
            appendLine("description: $description")
            appendLine("---")
            appendLine()
            append(body)
        }
        skillFile(name).writeText(text)
    }

    fun deleteSkill(name: String) {
        skillFile(name).delete()
    }

    /** 返回 (frontmatter map, body) */
    fun parseSkill(text: String): Pair<Map<String, String>, String> {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap<String, String>() to text
        val meta = mutableMapOf<String, String>()
        var i = 1
        while (i < lines.size && lines[i].trim() != "---") {
            val line = lines[i]
            val idx = line.indexOf(':')
            if (idx > 0) meta[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            i++
        }
        val body = lines.drop(i + 1).joinToString("\n").trim()
        return meta to body
    }

    // ---------- 工作区文件 ----------

    /**
     * 将用户/模型给的相对路径解析到 workspace 内，拒绝越界（.. 等）。
     * @throws SecurityException 路径越界
     */
    fun workspaceFile(relativePath: String): File {
        val cleaned = relativePath.trim().trimStart('/', '\\')
        require(cleaned.isNotEmpty()) { "路径不能为空" }
        val f = File(workspaceDir, cleaned).canonicalFile
        val rootCanonical = workspaceDir.canonicalFile
        if (f != rootCanonical && !f.path.startsWith(rootCanonical.path + File.separator)) {
            throw SecurityException("路径越界: $relativePath")
        }
        return f
    }

    fun writeWorkspace(relativePath: String, content: String): File {
        val f = workspaceFile(relativePath)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
    }

    fun readWorkspace(relativePath: String, maxBytes: Int = 200 * 1024): String {
        val f = workspaceFile(relativePath)
        require(f.exists() && f.isFile) { "文件不存在: $relativePath" }
        require(f.length() <= maxBytes) { "文件过大（${f.length()} 字节），超出可读取上限" }
        return f.readText()
    }

    /** 递归列出 workspace 内所有文件（相对路径），按路径排序 */
    fun listWorkspace(subDir: String = ""): List<String> {
        val base = if (subDir.isEmpty()) workspaceDir else workspaceFile(subDir)
        if (!base.exists()) return emptyList()
        val rootPath = workspaceDir.canonicalFile.path
        return base.walkTopDown()
            .filter { it.isFile }
            .map { it.canonicalFile.path.removePrefix(rootPath).trimStart(File.separatorChar).replace(File.separatorChar, '/') }
            .sorted()
            .toList()
    }

    fun deleteWorkspace(relativePath: String): Boolean = workspaceFile(relativePath).deleteRecursively()

    fun workspaceSize(relativePath: String): Long = workspaceFile(relativePath).length()

    companion object {
        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "unnamed" }
    }
}
