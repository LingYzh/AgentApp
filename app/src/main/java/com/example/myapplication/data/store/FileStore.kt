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
import java.util.UUID
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

    /**
     * Managed data roots are intentionally separate from user-chosen workspace scopes.
     * Every direct child is still canonicalized so a malformed index, name, or symlink
     * cannot turn a dedicated memory/Skill operation into an arbitrary file operation.
     */
    /** Accept a trusted root's canonical alias, while requiring the root itself to be a directory. */
    private fun managedDirectory(directory: File, label: String): File {
        val absolute = directory.absoluteFile
        val canonical = absolute.canonicalFile
        check(canonical.exists() && canonical.isDirectory) { "${label}目录无效或被重定向" }
        return canonical
    }

    /** memory/ and skills/ must be direct children of the trusted application root. */
    private fun managedStoreRoot(directory: File, label: String): File {
        val expected = File(root.canonicalFile, directory.name).absoluteFile
        val canonical = managedDirectory(directory, label)
        check(canonical == expected) { "${label}目录无效或被符号链接重定向" }
        return canonical
    }

    private fun managedChild(root: File, name: String, label: String): File {
        require(name.isNotBlank() && name !in setOf(".", "..") &&
            !name.contains('/') && !name.contains('\\')) { "${label}名称无效" }
        val canonicalRoot = managedDirectory(root, label)
        val child = File(canonicalRoot, name).absoluteFile
        val canonical = child.canonicalFile
        check(canonical.parentFile == canonicalRoot && canonical == child) { "${label}路径越界或被符号链接重定向" }
        return canonical
    }

    private fun rejectSymbolicLinksInTree(directory: File, label: String) {
        directory.walkTopDown().onEnter { candidate ->
            check(candidate.canonicalFile == candidate.absoluteFile) { "${label}包含符号链接，拒绝删除" }
            true
        }.forEach { candidate ->
            check(candidate.canonicalFile == candidate.absoluteFile) { "${label}包含符号链接，拒绝删除" }
        }
    }

    /** Replace a managed file's directory entry instead of mutating a possible hard link. */
    private fun writeManagedFile(target: File, content: String) {
        val parent = managedDirectory(target.parentFile ?: error("托管文件缺少父目录"), "托管文件")
        val temporary = File.createTempFile(".managed-", ".tmp", parent)
        val backup = File(parent, ".managed-backup-${UUID.randomUUID()}")
        try {
            temporary.writeText(content)
            if (!temporary.renameTo(target)) {
                val existed = target.exists()
                check(!existed || target.renameTo(backup)) { "无法替换托管文件" }
                if (!temporary.renameTo(target)) {
                    if (existed) check(backup.renameTo(target)) { "托管文件写入失败，原文件保留在 ${backup.path}" }
                    error("托管文件写入失败")
                }
                backup.delete()
            }
        } finally {
            temporary.delete()
        }
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

    @Synchronized
    fun listConversations(): List<Conversation> =
        conversationsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.messages.lastOrNull()?.timestamp ?: it.createdAt }
            ?: emptyList()

    /**
     * 返回可以在会话根列表中展示的会话。
     *
     * 子代理只有在父会话文件仍然存在且可以被解析时才会被隐藏；旧数据或被移动过的
     * 子代理因此会作为普通根会话保留，避免把它变成用户无法找到的记录。
     */
    fun listRootConversations(): List<Conversation> {
        val all = listConversations()
        val byId = all.associateBy { it.id }
        return all.filter { conversation ->
            val firstParent = conversation.parentConversationId ?: return@filter true
            var parentId: String? = firstParent
            val visited = mutableSetOf(conversation.id)
            var directParent = true
            while (parentId != null) {
                // A direct parent is enough to hide this record from the root list. Ancestor
                // gaps do not make the child disappear; only a graph cycle is promoted back
                // to the root list so corrupt data remains reachable.
                val parent = byId[parentId] ?: return@filter directParent
                // A corrupt/self-referential graph must remain visible rather than hide
                // every node in the cycle from the root list.
                if (!visited.add(parent.id)) return@filter true
                parentId = parent.parentConversationId
                directParent = false
            }
            false
        }
    }

    /** 返回直接归属于指定父会话的子代理记录。 */
    fun listChildConversations(parentConversationId: String): List<Conversation> =
        listConversations().filter { it.parentConversationId == parentConversationId }

    @Synchronized
    fun loadConversation(id: String): Conversation? {
        val f = conversationFile(id)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull()
    }

    /** 与详情轮询共用锁，避免读取正在写入的 JSON。 */
    @Synchronized
    fun saveConversation(c: Conversation) {
        conversationFile(c.id).writeText(json.encodeToString(c))
    }

    @Synchronized
    fun deleteConversation(id: String) {
        // Snapshot first so a malformed parent graph cannot make deletion loop forever.
        val all = listConversations()
        val deletedIds = linkedSetOf(id)
        var changed = true
        while (changed) {
            changed = false
            all.forEach { conversation ->
                if (conversation.parentConversationId in deletedIds && deletedIds.add(conversation.id)) {
                    changed = true
                }
            }
        }
        deletedIds.forEach { conversationFile(it).delete() }
    }

    /**
     * Mark child agents that were running when the process stopped as cancelled.
     * This is deliberately explicit rather than part of list operations, so a live child
     * is never changed merely because the root list is refreshed.
     */
    fun recoverInterruptedSubagents(): Int {
        val running = listConversations().filter {
            it.parentConversationId != null && it.executionStatus == "running"
        }
        running.forEach {
            it.executionStatus = "cancelled"
            saveConversation(it)
        }
        return running.size
    }

    /** Resolve a conversation ID to a direct file below conversations/. */
    private fun conversationFile(id: String): File {
        require(id.isNotBlank()) { "会话 ID 不能为空" }
        val directory = conversationsDir.canonicalFile
        val file = File(directory, "$id.json").canonicalFile
        if (file.parentFile != directory) {
            throw SecurityException("会话 ID 路径越界: $id")
        }
        return file
    }

    // ---------- 记忆 ----------

    private val memoryIndexFile get() = managedChild(memoryRoot(), "index.json", "记忆索引")

    private fun memoryRoot(): File = managedStoreRoot(memoryDir, "记忆")

    private fun skillRoot(): File = managedStoreRoot(skillsDir, "Skill")

    /** Memory IDs are storage keys, never paths supplied by a tool call. */
    private fun memoryFile(id: String): File = managedChild(
        memoryRoot(),
        "${requireMemoryId(id)}.md",
        "记忆正文"
    )

    private fun requireMemoryId(id: String): String {
        require(MANAGED_ID.matches(id)) { "记忆 ID 无效" }
        return id
    }

    private fun isMemoryId(id: String): Boolean = MANAGED_ID.matches(id)

    fun listMemories(): List<MemoryEntry> =
        runCatching {
            json.decodeFromString<List<MemoryEntry>>(memoryIndexFile.readText())
        }.getOrDefault(emptyList()).filter { isMemoryId(it.id) }

    private fun saveMemoryIndex(list: List<MemoryEntry>) {
        writeManagedFile(memoryIndexFile, json.encodeToString(list))
    }

    fun saveMemory(title: String, content: String, id: String? = null): MemoryEntry {
        id?.let(::requireMemoryId)
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
        val bodyFile = memoryFile(entry.id)
        saveMemoryIndex(list)
        writeManagedFile(bodyFile, content)
        return entry
    }

    fun readMemory(id: String): String? =
        if (!isMemoryId(id)) null else memoryFile(id).takeIf { it.exists() }?.readText()

    fun deleteMemory(id: String) {
        requireMemoryId(id)
        val body = memoryFile(id)
        saveMemoryIndex(listMemories().filterNot { it.id == id })
        body.delete()
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

    fun skillDir(name: String): File {
        require(name.trim() !in setOf(".", "..")) { "Skill 名称不能为 . 或 .." }
        return managedChild(skillRoot(), sanitizeFileName(name), "Skill")
    }

    fun skillFile(name: String): File {
        val dir = skillDir(name)
        if (!dir.exists()) return File(dir, "SKILL.md")
        val standard = managedChild(managedDirectory(dir, "Skill"), "SKILL.md", "Skill 入口文件")
        if (standard.exists()) return standard
        val lower = managedChild(managedDirectory(dir, "Skill"), "skill.md", "Skill 入口文件")
        if (lower.exists()) return lower
        return standard
    }

    /** 平滑迁移旧版单文件 skills（如 skills/xxx.md -> skills/xxx/SKILL.md） */
    private fun migrateOldSkillsIfNeed() {
        val root = skillRoot()
        root.listFiles { f ->
            f.isFile && f.extension == "md" && f.canonicalFile == f.absoluteFile
        }?.forEach { oldFile ->
            val source = managedChild(root, oldFile.name, "旧版 Skill 文件")
            val name = oldFile.nameWithoutExtension
            val dir = skillDir(name)
            if (!dir.exists()) {
                dir.mkdirs()
                val targetFile = managedChild(managedDirectory(dir, "Skill"), "SKILL.md", "Skill 入口文件")
                source.copyTo(targetFile, overwrite = true)
            }
            source.delete()
        }
    }

    /** 扫描 skills 目录下所有目录包，解析 SKILL.md 的 Frontmatter */
    fun listSkills(): List<SkillMeta> {
        migrateOldSkillsIfNeed()
        return skillRoot().listFiles { f -> f.isDirectory && f.canonicalFile == f.absoluteFile }
            ?.mapNotNull { dir ->
                val root = runCatching { managedDirectory(dir, "Skill") }.getOrNull() ?: return@mapNotNull null
                val skillMd = runCatching { managedChild(root, "SKILL.md", "Skill 入口文件") }.getOrNull()?.takeIf { it.exists() }
                    ?: runCatching { managedChild(root, "skill.md", "Skill 入口文件") }.getOrNull()?.takeIf { it.exists() }
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
        val root = managedDirectory(dir, "Skill")
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
        writeManagedFile(managedChild(root, "SKILL.md", "Skill 入口文件"), text)
    }

    fun deleteSkill(name: String) {
        val dir = skillDir(name)
        if (dir.exists()) {
            rejectSymbolicLinksInTree(dir, "Skill")
            dir.deleteRecursively()
        }
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
        val tempRoot = tempDir.canonicalFile
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
                        val outFile = File(tempRoot, entryName).canonicalFile
                        if (outFile != tempRoot && !outFile.path.startsWith(tempRoot.path + File.separator)) {
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
                    rejectSymbolicLinksInTree(targetDir, "Skill")
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
        rejectSymbolicLinksInTree(dir, "Skill")
        val exportsDir = File(backupsDir, "exports").apply { mkdirs() }
        val zipFile = File(exportsDir, "${dir.name}.zip")
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
                    rejectSymbolicLinksInTree(dir, "Skill")
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

    /**
     * 将指定的 Skill 目录打包为可再次导入的合集 ZIP。
     * ZIP 根目录使用已清洗的 Skill 目录名，避免名称中的路径字符污染 entry。
     */
    fun exportSkillsZip(names: Set<String>): File {
        require(names.isNotEmpty()) { "未选择 Skill" }

        val selected = names.toList().sorted().map { name ->
            val directory = skillDir(name)
            require(directory.exists() && directory.isDirectory) {
                "Skill '$name' 不存在"
            }
            rejectSymbolicLinksInTree(directory, "Skill")
            sanitizeFileName(name) to directory
        }
        require(selected.map { it.first }.distinct().size == selected.size) {
            "所选 Skill 名称重复"
        }

        val exportsDir = File(backupsDir, "exports").apply { mkdirs() }
        val zipFile = File(exportsDir, "skills_selected_collections.zip")
        if (zipFile.exists()) zipFile.delete()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            selected.forEach { (entryRoot, directory) ->
                val rootPath = directory.canonicalFile.path
                val skillsRoot = skillRoot().path + File.separator
                require(rootPath.startsWith(skillsRoot)) {
                    "Skill 目录越界: ${directory.name}"
                }
                val directoryPrefix = rootPath + File.separator
                directory.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val canonicalFile = file.canonicalFile
                        require(canonicalFile.path.startsWith(directoryPrefix)) {
                            "Skill 文件越界: ${file.name}"
                        }
                        val relativePath = canonicalFile.path
                            .removePrefix(directoryPrefix)
                            .replace(File.separatorChar, '/')
                        require(relativePath.isNotBlank()) { "Skill 文件路径无效: ${file.name}" }
                        zos.putNextEntry(ZipEntry("$entryRoot/$relativePath"))
                        FileInputStream(file).use { input -> input.copyTo(zos) }
                        zos.closeEntry()
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
        private val MANAGED_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "unnamed" }
    }
}
