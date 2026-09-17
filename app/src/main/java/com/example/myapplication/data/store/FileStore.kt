package com.example.myapplication.data.store

import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.DefaultAgents
import com.example.myapplication.data.model.MemoryEntry
import com.example.myapplication.data.model.SkillMeta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
    val avatarsDir = File(root, "avatars")

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
        avatarsDir.mkdirs()
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

    fun loadAgents(): List<AgentProfile> {
        if (!agentsFile.exists()) {
            return DefaultAgents.DEFAULT_PROFILES.also { saveAgents(it) }
        }
        return runCatching {
            json.decodeFromString<List<AgentProfile>>(agentsFile.readText())
        }.getOrDefault(emptyList())
    }

    fun saveAgents(list: List<AgentProfile>) {
        agentsFile.writeText(json.encodeToString(list))
    }

    fun resetAgentsToDefault(): List<AgentProfile> {
        return DefaultAgents.DEFAULT_PROFILES.also { saveAgents(it) }
    }

    fun saveAgentAvatar(agentId: String, inputStream: java.io.InputStream): String {
        avatarsDir.mkdirs()
        val file = File(avatarsDir, "$agentId.png")
        file.outputStream().use { out -> inputStream.copyTo(out) }
        return file.absolutePath
    }

    fun deleteAgentAvatar(agentId: String) {
        File(avatarsDir, "$agentId.png").delete()
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

    // ---------- Skills（统一纯目录包格式：skills/{name}/SKILL.md） ----------

    fun skillDir(name: String): File = File(skillsDir, sanitizeFileName(name))

    fun skillFile(name: String): File {
        val dir = skillDir(name)
        val standard = File(dir, "SKILL.md")
        if (standard.exists()) return standard
        val lower = File(dir, "skill.md")
        if (lower.exists()) return lower
        return standard
    }

    /** 平滑迁移旧版单文件 skills（如 skills/xxx.md -> skills/xxx/SKILL.md） */
    private fun migrateOldSkillsIfNeed() {
        skillsDir.listFiles { f -> f.isFile && f.extension == "md" }?.forEach { oldFile ->
            val name = oldFile.nameWithoutExtension
            val dir = skillDir(name)
            if (!dir.exists()) {
                dir.mkdirs()
                val targetFile = File(dir, "SKILL.md")
                oldFile.copyTo(targetFile, overwrite = true)
            }
            oldFile.delete()
        }
    }

    /** 扫描 skills 目录下所有目录包，解析 SKILL.md 的 Frontmatter */
    fun listSkills(): List<SkillMeta> {
        migrateOldSkillsIfNeed()
        return skillsDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                val skillMd = File(dir, "SKILL.md").takeIf { it.exists() }
                    ?: File(dir, "skill.md").takeIf { it.exists() }
                    ?: return@mapNotNull null
                val text = runCatching { skillMd.readText() }.getOrNull() ?: return@mapNotNull null
                val (meta, _) = parseSkill(text)
                val name = meta["name"]?.ifBlank { null } ?: dir.name
                val version = meta["version"]?.ifBlank { null } ?: "1.0.0"
                val license = meta["license"]?.ifBlank { null } ?: "MIT"
                SkillMeta(
                    name = name,
                    description = meta["description"] ?: "",
                    version = version,
                    license = license
                )
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun readSkill(name: String): String? =
        skillFile(name).takeIf { it.exists() }?.readText()

    fun readSkillBody(name: String): String? =
        readSkill(name)?.let { parseSkill(it).second }

    fun saveSkill(
        name: String,
        description: String,
        body: String,
        version: String = "1.0.0",
        license: String = "MIT"
    ) {
        val dir = skillDir(name)
        dir.mkdirs()
        val text = buildString {
            appendLine("---")
            appendLine("name: $name")
            appendLine("description: $description")
            if (version.isNotBlank()) appendLine("version: $version")
            if (license.isNotBlank()) appendLine("license: $license")
            appendLine("---")
            appendLine()
            append(body.trim())
            appendLine()
        }
        File(dir, "SKILL.md").writeText(text)
    }

    fun deleteSkill(name: String) {
        skillDir(name).deleteRecursively()
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

    /**
     * 导入基于 my-skills-collections release 规范的通用 ZIP 包。
     * 支持导入单个 Skill 的 Release 包（根目录或子目录下包含 SKILL.md），
     * 或包含多个 Skill 目录的合集备份包。
     * 解包并规范化至 skills/{name}/ 纯目录包结构。
     */
    fun importSkillZip(inputStream: InputStream): List<SkillMeta> {
        val tempDir = File(root, "temp_skill_import_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.replace('\\', '/')
                    // 忽略 macOS 与插件特定元数据
                    if (!entryName.startsWith("__MACOSX") &&
                        !entryName.contains(".codex-plugin/") &&
                        !entryName.contains(".claude-plugin/")
                    ) {
                        val outFile = File(tempDir, entryName).canonicalFile
                        if (!outFile.path.startsWith(tempDir.canonicalPath)) {
                            throw SecurityException("ZIP 包含越界路径: $entryName")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 在解压产物中查找所有 SKILL.md
            val skillMdFiles = tempDir.walkTopDown()
                .filter { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) }
                .toList()

            if (skillMdFiles.isEmpty()) {
                throw IllegalArgumentException("ZIP 中未找到 SKILL.md，不是合法的通用 Skill 包")
            }

            val imported = mutableListOf<SkillMeta>()

            for (skillMdFile in skillMdFiles) {
                val text = skillMdFile.readText()
                val (meta, _) = parseSkill(text)
                val skillSourceDir = skillMdFile.parentFile ?: tempDir
                val name = meta["name"]?.ifBlank { null }
                    ?: skillSourceDir.name.takeIf { it != tempDir.name }
                    ?: "unnamed_skill"
                val cleanName = sanitizeFileName(name)

                val targetDir = skillDir(cleanName)
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                targetDir.mkdirs()

                // 如果 skillMd 直接在 tempDir 根下（单包），复制 tempDir 下除其他子 skill 目录外的文件
                if (skillSourceDir == tempDir) {
                    tempDir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            f.copyTo(File(targetDir, f.name), overwrite = true)
                        } else if (f.isDirectory && skillMdFiles.none { it.canonicalPath.startsWith(f.canonicalPath) }) {
                            f.copyRecursively(File(targetDir, f.name), overwrite = true)
                        }
                    }
                } else {
                    skillSourceDir.copyRecursively(targetDir, overwrite = true)
                }

                // 规范化主入口文件名固定为 SKILL.md
                val currentMd = File(targetDir, skillMdFile.name)
                if (currentMd.name != "SKILL.md" && currentMd.exists()) {
                    currentMd.renameTo(File(targetDir, "SKILL.md"))
                }

                val version = meta["version"]?.ifBlank { null } ?: "1.0.0"
                val license = meta["license"]?.ifBlank { null } ?: "MIT"
                imported.add(
                    SkillMeta(
                        name = cleanName,
                        description = meta["description"] ?: "",
                        version = version,
                        license = license
                    )
                )
            }

            return imported
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * 将指定的 Skill 目录打包为标准通用 ZIP（根目录直接包含 SKILL.md 及附属资源）。
     */
    fun exportSkillZip(name: String): File {
        val dir = skillDir(name)
        require(dir.exists() && dir.isDirectory) { "Skill '$name' 不存在" }
        val exportsDir = File(backupsDir, "exports").apply { mkdirs() }
        val zipFile = File(exportsDir, "${name}.zip")
        if (zipFile.exists()) zipFile.delete()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val rootPath = dir.canonicalFile.path
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.canonicalFile.path.removePrefix(rootPath)
                        .trimStart(File.separatorChar)
                        .replace(File.separatorChar, '/')
                    val entry = ZipEntry(relPath)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return zipFile
    }

    /**
     * 导出本地所有 Skills 为一个全量集合备份 ZIP。
     */
    fun exportAllSkillsZip(): File {
        val exportsDir = File(backupsDir, "exports").apply { mkdirs() }
        val zipFile = File(exportsDir, "skills_all_collections.zip")
        if (zipFile.exists()) zipFile.delete()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val skills = listSkills()
            skills.forEach { skill ->
                val dir = skillDir(skill.name)
                if (dir.exists() && dir.isDirectory) {
                    val rootPath = dir.canonicalFile.path
                    dir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val relPath = file.canonicalFile.path.removePrefix(rootPath)
                                .trimStart(File.separatorChar)
                                .replace(File.separatorChar, '/')
                            val entry = ZipEntry("${skill.name}/$relPath")
                            zos.putNextEntry(entry)
                            FileInputStream(file).use { fis -> fis.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
        }
        return zipFile
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
