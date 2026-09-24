package com.example.myapplication.agent

import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.store.FileChanges
import com.example.myapplication.data.store.FileSnapshot
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ProviderJson
import com.example.myapplication.provider.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import com.example.myapplication.diagnostics.RuntimeDiagnostics

/** Executes tool calls only after the current [PermissionSession] permits them. */
class ToolExecutor(
    private val store: FileStore,
    private val allowedTools: Set<String>? = null,
    private val onRunSubagent: (suspend (task: String, providerName: String?, model: String?) -> String)? = null,
    private val onFileChange: (FileChange) -> Unit = {},
    private val onReadMedia: ((File) -> String?)? = null,
    private val permissionSession: PermissionSession = PermissionSession(store, com.example.myapplication.data.model.Conversation(), PermissionCoordinator()),
    private val commandExecutor: suspend (String, String) -> String = ShellCommandRunner::run,
    private val deviceSession: DeviceToolSession? = null
) {
    private val webTools by lazy { WebTools() }

    /** Session lifecycle controls accompany file-writing capabilities, including old profiles. */
    fun specs(): List<ToolSpec> {
        val includePlanControls = !permissionSession.isChild &&
            (allowedTools == null || Tools.WRITE_FILE in allowedTools ||
                allowedTools.any { it in setOf(Tools.ENTER_PLAN_MODE, Tools.EXIT_PLAN_MODE) })
        return Tools.specs(permissionSession.planPath, includePlanControls)
        .filter { spec ->
            (allowedTools == null || spec.name in allowedTools || spec.name == Tools.GET_SESSION_STATE ||
                (includePlanControls && spec.name in setOf(Tools.ENTER_PLAN_MODE, Tools.EXIT_PLAN_MODE))) &&
                (spec.name != Tools.RUN_SUBAGENT || onRunSubagent != null) &&
                (spec.name != Tools.SEARCH || store.loadConfig().webSearch.isConfigured) &&
                (spec.name !in Tools.DEVICE_NAMES || (deviceSession != null &&
                    (spec.name == Tools.DEVICE_STATUS || deviceSession.enabled)))
        }
    }

    suspend fun execute(name: String, argumentsJson: String): String {
        val started = System.nanoTime()
        RuntimeDiagnostics.event("tool_start", "tool" to name,
            "conversation" to permissionSession.conversation.id, "mode" to permissionSession.conversation.permissionMode)
        return try {
            val result = executeInternal(name, argumentsJson)
            RuntimeDiagnostics.event("tool_end", "tool" to name,
                "conversation" to permissionSession.conversation.id, "mode" to permissionSession.conversation.permissionMode,
                "failed" to result.startsWith("错误"), "elapsedMs" to (System.nanoTime() - started) / 1_000_000)
            result
        } catch (cancelled: CancellationException) {
            RuntimeDiagnostics.event("tool_cancelled", "tool" to name, "conversation" to permissionSession.conversation.id)
            throw cancelled
        }
    }

    private suspend fun executeInternal(name: String, argumentsJson: String): String {
        if (specs().none { it.name == name }) return "错误: 工具 '$name' 不可用"
        permissionSession.toolBlockReason(name)?.let { return "错误: $it" }
        val args = try {
            ProviderJson.parseToJsonElement(argumentsJson).jsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "错误: 工具参数不是合法 JSON: ${e.message}"
        }
        fun arg(key: String): String = args[key]?.jsonPrimitive?.content ?: ""
        return try {
            if (name in Tools.DEVICE_NAMES) {
                if (name == Tools.DEVICE_ACTION || (name == Tools.DEVICE_SYSTEM && arg("operation") != "list_apps")) {
                    permissionSession.authorizeDeviceAction("$name\n$args")?.let { return "错误: $it" }
                }
                currentCoroutineContext().ensureActive()
                return requireNotNull(deviceSession).execute(name, args, onReadMedia)
            }
            when (name) {
                Tools.FETCH -> webTools.fetch(arg("url"))
                Tools.SEARCH -> webTools.search(store.loadConfig().webSearch, arg("query"))
                Tools.WRITE_FILE -> writeFile(arg("path"), arg("content"))
                Tools.EDIT_FILE -> editFile(arg("path"), arg("old_text"), arg("new_text"))
                Tools.DELETE_FILE -> FileDeletion(permissionSession).delete(arg("path"))
                Tools.READ_FILE -> readFile(arg("path"))
                Tools.LIST_FILES -> listFiles(arg("path"))
                Tools.RUN_COMMAND -> runCommand(arg("command"), arg("cwd"))
                Tools.ENTER_PLAN_MODE -> permissionSession.enterPlan()
                Tools.EXIT_PLAN_MODE -> permissionSession.exitPlan()
                // Read current session state at execution time, never infer it from chat history.
                Tools.GET_SESSION_STATE -> permissionSession.stateDescription(specs().map { it.name })
                Tools.SAVE_MEMORY -> mutate(Tools.SAVE_MEMORY) {
                    val id = arg("id").takeIf { it.isNotBlank() }
                    if (id != null && store.listMemories().none { it.id == id }) {
                        "错误: 记忆 $id 不存在，请先 search_memory 获取有效 ID"
                    } else {
                        val entry = store.saveMemory(arg("title").ifEmpty { "未命名记忆" }, arg("content"), id)
                        "已保存记忆 [id=${entry.id}] ${entry.title}"
                    }
                }
                Tools.SEARCH_MEMORY -> {
                    val hits = store.searchMemory(arg("query"))
                    if (hits.isEmpty()) "(未找到相关记忆)" else hits.take(5).joinToString("\n\n") { (e, body) -> "[id=${e.id}] ${e.title}\n${body.take(1000)}" }
                }
                Tools.DELETE_MEMORY -> mutate(Tools.DELETE_MEMORY) {
                    val id = arg("id")
                    if (store.listMemories().none { it.id == id }) {
                        "错误: 记忆 $id 不存在"
                    } else {
                        store.deleteMemory(id)
                        "已删除记忆 $id"
                    }
                }
                Tools.USE_SKILL -> {
                    val body = store.readSkillBody(arg("name")) ?: return "错误: Skill '${arg("name")}' 不存在"
                    "以下是 Skill '${arg("name")}' 的指令，请遵循执行：\n\n$body"
                }
                Tools.SAVE_SKILL -> mutate(Tools.SAVE_SKILL) {
                    val skillName = com.example.myapplication.ui.skills.formatKebabCase(arg("name")).ifBlank { "unnamed-skill" }
                    store.saveSkill(skillName, arg("description"), arg("content")); "已成功保存 Skill '$skillName'"
                }
                Tools.RUN_SUBAGENT -> {
                    permissionSession.canRunSubagent()?.let { return "错误: $it" }
                    val runner = onRunSubagent ?: return "错误: 当前上下文不允许调用子代理"
                    runner(arg("task"), arg("provider_name").ifBlank { null }, arg("model").ifBlank { null })
                }
                else -> "错误: 未知工具 '$name'"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            RuntimeDiagnostics.event("tool_permission_denied", "tool" to name, "mode" to permissionSession.conversation.permissionMode)
            buildString { append("错误: "); append(permissionSession.explainSecurityDenial(e.message ?: "权限边界拒绝此操作")) }
        } catch (e: Exception) {
            if (name in setOf(Tools.READ_FILE, Tools.WRITE_FILE, Tools.EDIT_FILE, Tools.DELETE_FILE, Tools.LIST_FILES)) {
                val raw = File(arg("path"))
                val path = runCatching { permissionSession.files.lexical(arg("path")).path }.getOrDefault(raw.path)
                RuntimeDiagnostics.fileFailure(name, path, e)
            }
            "错误: ${e.message ?: e.javaClass.simpleName}" + if (RuntimeDiagnostics.permissionFailure(e)) {
                "\n这是 Android 操作系统拒绝访问，不是会话模式故障。${RuntimeDiagnostics.storageAccessSummary()}" +
                    "不能仅凭此错误认定文件名被保护；请停止重复改名/换模式试探，确认系统授权和文件来源。"
            } else ""
        }
    }

    private fun mutate(tool: String, action: () -> String): String {
        permissionSession.canMutate(tool)?.let { return "错误: $it" }
        return action()
    }
    private fun writable(path: String): File {
        permissionSession.canWritePath(path)?.let { throw SecurityException(it) }
        return if (permissionSession.conversation.permissionMode == PermissionMode.PLAN) permissionSession.planFile() else permissionSession.files.resolve(path)
    }
    private fun snapshot(target: File): FileSnapshot = try {
        FileChanges.snapshot(target)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        FileSnapshot(target.exists(), null, previewOmitted = true)
    }
    private fun emitChange(target: File, before: FileSnapshot, after: String) {
        // Keep the saved file reference stable when this session later changes its directory.
        val path = if (permissionSession.conversation.workingDirectory != null) target.canonicalPath
            else permissionSession.files.displayPath(target)
        val change = FileChanges.change(path, before, after)
        try { onFileChange(change) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    private fun writeFile(path: String, content: String): String {
        val target = writable(path)
        val before = snapshot(target)
        target.parentFile?.mkdirs()
        writeTarget(target, content)
        emitChange(target, before, content)
        return "已写入 ${permissionSession.files.displayPath(target)}（${target.length()} 字节）"
    }
    private fun editFile(path: String, oldText: String, newText: String): String {
        val target = writable(path)
        require(target.exists() && target.isFile) { "文件不存在: $path" }
        val before = snapshot(target)
        val old = target.readText()
        require(oldText.isNotEmpty()) { "old_text 不能为空" }
        val first = old.indexOf(oldText)
        require(first >= 0) { "old_text 未在文件中找到" }
        require(old.indexOf(oldText, first + oldText.length) < 0) { "old_text 出现多次，拒绝不精确替换" }
        val after = old.replaceRange(first, first + oldText.length, newText)
        writeTarget(target, after)
        emitChange(target, before, after)
        return "已编辑 ${permissionSession.files.displayPath(target)}"
    }
    /** Replacing the plan inode also prevents a pre-existing hard link from modifying another file. */
    private fun writeTarget(target: File, content: String) {
        if (permissionSession.conversation.permissionMode != PermissionMode.PLAN) {
            target.writeText(content)
            return
        }
        require(!target.exists() || target.isFile) { "计划路径不是文件" }
        val temporary = File.createTempFile(".plan-", ".tmp", target.parentFile)
        val backup = File(target.parentFile, ".plan-backup-${java.util.UUID.randomUUID()}")
        try {
            temporary.writeText(content)
            if (!temporary.renameTo(target)) {
                val hadOriginal = target.exists()
                check(!hadOriginal || target.renameTo(backup)) { "无法替换计划文件" }
                if (!temporary.renameTo(target)) {
                    if (hadOriginal) check(backup.renameTo(target)) { "计划保存失败，原文件保留在 ${backup.path}" }
                    error("计划保存失败")
                }
                backup.delete()
            }
        } finally {
            temporary.delete()
        }
    }

    private fun readFile(path: String): String {
        val target = permissionSession.readableFile(path)
        require(target.exists() && target.isFile) { "文件不存在: $path" }
        onReadMedia?.invoke(target)?.let { return it }
        require(target.length() <= MAX_READ_BYTES) { "文件过大（${target.length()} 字节），超出可读取上限" }
        return target.readText()
    }
    private suspend fun listFiles(path: String): String {
        val base = if (path.isBlank()) permissionSession.workingDirectory() else permissionSession.files.resolve(path)
        if (!base.exists()) return "(目录为空)"
        val files = mutableListOf<String>()
        if (base.isFile) {
            files += permissionSession.files.displayPath(base)
        } else {
            val visited = mutableSetOf<String>()
            val iterator = base.walkTopDown()
                .maxDepth(MAX_LIST_DEPTH)
                .onEnter { dir ->
                    runCatching { visited.add(permissionSession.files.resolve(dir.path).canonicalPath) }.getOrDefault(false)
                }
                .iterator()
            var inspected = 0
            while (iterator.hasNext() && files.size < MAX_LIST_FILES && inspected < MAX_LIST_INSPECTED) {
                currentCoroutineContext().ensureActive()
                val file = iterator.next()
                inspected++
                if (file != base && file.isDirectory && runCatching { permissionSession.files.resolve(file.path) }.isSuccess) {
                    files += permissionSession.files.displayPath(file).trimEnd('/') + "/"
                } else if (file.isFile && runCatching { permissionSession.files.resolve(file.path) }.isSuccess) {
                    files += permissionSession.files.displayPath(file)
                }
            }
            val truncated = iterator.hasNext() || inspected >= MAX_LIST_INSPECTED
            return renderFileList(files, truncated) + "\n（递归深度最多 $MAX_LIST_DEPTH 层，可指定子目录继续查看）"
        }
        return renderFileList(files, false)
    }

    private fun renderFileList(files: List<String>, truncated: Boolean): String {
        if (files.isEmpty()) return "(目录为空)"
        val suffix = if (truncated || files.size >= MAX_LIST_FILES) {
            "\n... 列表已截断（最多 $MAX_LIST_FILES 项）"
        } else ""
        return files.sorted().joinToString("\n") + suffix
    }
    private suspend fun runCommand(command: String, cwd: String): String {
        require(command.isNotBlank()) { "command 不能为空" }
        val directory = permissionSession.commandDirectory(cwd)
        permissionSession.authorizeCommand(command, directory.path)?.let { return "错误: $it" }
        return commandExecutor(command, directory.path)
    }

    companion object {
        private const val MAX_READ_BYTES = 200 * 1024L
        private const val MAX_LIST_FILES = 500
        private const val MAX_LIST_INSPECTED = 2_000
        private const val MAX_LIST_DEPTH = 20
    }
}
