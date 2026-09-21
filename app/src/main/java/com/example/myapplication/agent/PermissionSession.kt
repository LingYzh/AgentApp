package com.example.myapplication.agent

import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.store.FileStore
import java.io.File

class PermissionSession(
    private val store: FileStore,
    val conversation: Conversation,
    val coordinator: PermissionCoordinator,
    val isChild: Boolean = false
) {
    private val safeId = conversation.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val planPath: String = ".plans/$safeId.md"
    val files = AgentFiles(store) { conversation.allowedDirectories }

    fun childSession(): PermissionSession = PermissionSession(store, conversation, coordinator, isChild = true)

    fun readPlan(): String? = runCatching {
        val workspace = store.workspaceFile(".").canonicalFile
        val lexical = File(workspace, planPath).absoluteFile
        if (lexical.canonicalFile != lexical) null else if (lexical.exists() && lexical.isFile) lexical.readText() else null
    }.getOrNull()

    fun setMode(mode: PermissionMode) {
        if (!isChild) conversation.permissionMode = mode
    }

    /** Current scope is rendered into the request context and denial messages. */
    fun canonicalAllowedDirectories(): List<String> = conversation.allowedDirectories
        .map { File(it).canonicalPath }
        .distinct()
        .sorted()

    fun scopeDescription(): String = canonicalAllowedDirectories().takeIf { it.isNotEmpty() }
        ?.joinToString("、")
        ?: "未限制（应用 UID 当前可访问的路径）"

    /**
     * Gates that do not depend on a tool argument. Argument-specific file scope and the
     * designated Plan file are still checked by [canWritePath] and [readableFile].
     */
    fun toolBlockReason(tool: String): String? {
        if (tool == Tools.DELETE_FILE && conversation.permissionMode in setOf(PermissionMode.READONLY, PermissionMode.PLAN)) {
            return modeDenied("删除文件（包括计划文件）")
        }
        if (isChild && tool in setOf(Tools.ENTER_PLAN_MODE, Tools.EXIT_PLAN_MODE)) {
            return "子代理不能切换或提交权限模式；这不是系统故障，请勿重复调用"
        }
        if (isChild && conversation.permissionMode == PermissionMode.PLAN && tool in setOf(Tools.WRITE_FILE, Tools.EDIT_FILE)) {
            return "当前权限模式 Plan 的子代理只能研究和读取，不能修改计划文件；这不是系统故障，请勿重复调用"
        }
        if (tool == Tools.ENTER_PLAN_MODE && conversation.permissionMode == PermissionMode.PLAN) {
            return "当前权限模式 Plan 已经处于计划模式；这不是系统故障，请勿重复调用"
        }
        if (tool == Tools.EXIT_PLAN_MODE && conversation.permissionMode != PermissionMode.PLAN) {
            return "当前权限模式 ${modeLabel()} 不能提交计划；这不是系统故障，请勿重复调用"
        }
        if (tool == Tools.RUN_COMMAND) {
            if (conversation.permissionMode in setOf(PermissionMode.READONLY, PermissionMode.PLAN)) return modeDenied("执行命令")
            if (conversation.allowedDirectories.isNotEmpty()) {
                return shellScopeDenied()
            }
        }
        if (tool in setOf(Tools.SAVE_MEMORY, Tools.DELETE_MEMORY, Tools.SAVE_SKILL)) {
            canMutate(tool)?.let { return it }
        }
        if (conversation.permissionMode == PermissionMode.READONLY && tool in setOf(Tools.WRITE_FILE, Tools.EDIT_FILE)) {
            return modeDenied("修改文件")
        }
        return null
    }

    fun canWritePath(path: String): String? {
        if (conversation.permissionMode == PermissionMode.READONLY) return modeDenied("修改文件")
        if (conversation.permissionMode == PermissionMode.PLAN) {
            val workspace = store.workspaceFile(".").canonicalFile
            val plan = File(workspace, planPath).absoluteFile
            val raw = File(path)
            val lexical = if (raw.isAbsolute) raw.absoluteFile else File(workspace, path).absoluteFile
            // A symlinked plan (or ancestor) could redirect the only permitted write.
            if (isChild || lexical != plan || plan.canonicalFile != plan) {
                return "当前权限模式 Plan 只允许写入未重定向的计划文件：$planPath；这不是系统故障，请勿重复调用"
            }
        } else {
            val target = files.resolve(path)
            if (conversation.permissionMode == PermissionMode.ACCEPT_EDIT && isPermissionMetadata(target)) {
                return "当前权限模式 Accept Edit 不能修改应用权限配置或会话元数据；请通过设置界面管理授权。这不是系统故障，请勿重复调用"
            }
        }
        return null
    }

    fun readableFile(path: String): File {
        if (conversation.permissionMode == PermissionMode.PLAN) {
            val workspace = store.workspaceFile(".").canonicalFile
            val plan = File(workspace, planPath).absoluteFile
            val raw = File(path)
            val lexical = if (raw.isAbsolute) raw.absoluteFile else File(workspace, path).absoluteFile
            if (lexical == plan && plan.canonicalFile == plan) return plan
        }
        return files.resolve(path)
    }

    fun planFile(): File = File(store.workspaceFile(".").canonicalFile, planPath).absoluteFile

    fun canMutate(tool: String): String? {
        when (conversation.permissionMode) {
            PermissionMode.READONLY -> return modeDenied("执行 $tool")
            PermissionMode.PLAN -> return "当前权限模式 Plan 不允许 $tool；只可更新计划文件 $planPath。这不是系统故障，请勿重复调用"
            else -> Unit
        }
        if (tool !in setOf(Tools.SAVE_MEMORY, Tools.DELETE_MEMORY, Tools.SAVE_SKILL)) return null
        // Memory and Skills are application-managed roots, not user-selected file paths.
        // FileStore enforces their canonical containment and rejects links at operation time.
        return null
    }

    // Child sessions inherit this session's gates; delegation never widens its permissions.
    fun canRunSubagent(): String? = null

    suspend fun authorizeCommand(command: String, cwd: String): String? {
        if (conversation.permissionMode == PermissionMode.READONLY || conversation.permissionMode == PermissionMode.PLAN) {
            return modeDenied("执行命令")
        }
        if (conversation.allowedDirectories.isNotEmpty()) return shellScopeDenied()
        if (conversation.permissionMode == PermissionMode.AUTO) return null
        val config = store.loadConfig()
        if (command.isNotBlank() && command in config.autoApprovedCommands) return null
        val lowRisk = CommandPolicy.isLowRisk(command)
        val answer = coordinator.request(PermissionRequest(
            conversationId = conversation.id,
            kind = PermissionRequestKind.COMMAND,
            command = command,
            workingDirectory = cwd,
            canAlwaysAllow = lowRisk
        ))
        if (conversation.permissionMode == PermissionMode.READONLY || conversation.permissionMode == PermissionMode.PLAN ||
            conversation.allowedDirectories.isNotEmpty()) {
            return if (conversation.allowedDirectories.isNotEmpty()) {
                "命令审批期间目录范围已设置，未执行。${shellScopeDenied()}"
            } else {
                "命令审批期间权限模式已收紧为 ${modeLabel()}，未执行；这不是系统故障，请勿重复调用"
            }
        }
        return when (answer.decision) {
            PermissionDecision.ALLOW_ONCE -> null
            PermissionDecision.ALLOW_ALWAYS -> if (lowRisk) {
                val updated = store.loadConfig()
                if (command !in updated.autoApprovedCommands) {
                    store.saveConfig(updated.copy(autoApprovedCommands = updated.autoApprovedCommands + command))
                }
                null
            } else "该命令不符合长期授权条件"
            PermissionDecision.FEEDBACK -> "用户反馈：${answer.feedback.ifBlank { "请修改方案后重试" }}"
            else -> "用户拒绝执行命令"
        }
    }

    fun enterPlan(): String {
        if (isChild) return "错误: 子代理不能切换权限模式"
        conversation.permissionMode = PermissionMode.PLAN
        return "已进入计划模式。请将可执行计划写入 $planPath；除该文件外不能修改任何内容。"
    }

    suspend fun exitPlan(): String {
        if (isChild) return "错误: 子代理不能切换权限模式"
        if (conversation.permissionMode != PermissionMode.PLAN) return "错误: 当前不在计划模式"
        val snapshot = readPlan()?.takeIf { it.isNotBlank() } ?: return "错误: 请先写入非空计划文件 $planPath"
        val answer = coordinator.request(PermissionRequest(
            conversationId = conversation.id,
            kind = PermissionRequestKind.PLAN,
            planText = snapshot,
            planPath = planPath
        ))
        if (conversation.permissionMode != PermissionMode.PLAN) return "计划审批期间模式已改变，请重新检查计划"
        if (readPlan() != snapshot) return "计划在审批期间已变化，请重新提交审批"
        return when (answer.decision) {
            PermissionDecision.ACCEPT_AUTO -> { conversation.permissionMode = PermissionMode.AUTO; "计划已接受，已切换到 Auto。现在执行计划。" }
            PermissionDecision.ACCEPT_EDIT -> { conversation.permissionMode = PermissionMode.ACCEPT_EDIT; "计划已接受，已切换到 Accept Edit。现在执行计划。" }
            PermissionDecision.FEEDBACK -> "计划未接受。用户反馈：${answer.feedback.ifBlank { "请修订计划" }}"
            else -> "计划未接受，请修订后再次提交。"
        }
    }

    fun modePrompt(): String = when (conversation.permissionMode) {
        PermissionMode.ACCEPT_EDIT -> "权限模式：Accept Edit。可修改文件；删除文件逐次审批；每个命令都需用户审批，用户已配置自动放行的精确命令除外。"
        PermissionMode.PLAN -> if (isChild) {
            "权限模式：Plan（子代理）。只能研究、读取和列出内容，不能写计划、切换模式、执行命令。"
        } else {
            "权限模式：Plan。只可写入计划文件 $planPath；不得执行命令。"
        }
        PermissionMode.AUTO -> if (conversation.allowedDirectories.isEmpty()) {
            "权限模式：Auto。不会弹出应用内确认；可在应用 UID 权限范围内执行文件操作和命令。"
        } else {
            "权限模式：Auto。不会弹出应用内确认，但显式目录范围仍生效；" +
                "范围外的普通文件和 shell 命令会被拒绝。应用托管的记忆和 Skill 不受外部目录范围影响，" +
                "但仍受工具授权与当前模式约束。请在权限面板调整或清空目录范围。"
        }
        PermissionMode.READONLY -> "权限模式：Readonly。只能读取、检索和列出内容，不能修改或执行命令；主代理仍可进入 Plan 以仅写入计划文件。"
    } + "\n目录范围：${scopeDescription()}。"

    fun scopeDenied(message: String): String = "$message；当前目录范围：${scopeDescription()}。" +
        "请在会话的权限面板调整或清空目录范围后重试；Auto 只取消应用内确认，不会绕过该范围。" +
        "这不是 Android 系统权限或工具回收故障，请勿反复切换模式"

    fun explainSecurityDenial(message: String): String = if (message.contains("路径不在已授权目录")) {
        scopeDenied(message)
    } else {
        message
    }

    private fun modeLabel(): String = when (conversation.permissionMode) {
        PermissionMode.READONLY -> "Readonly"
        PermissionMode.PLAN -> "Plan"
        PermissionMode.ACCEPT_EDIT -> "Accept Edit"
        PermissionMode.AUTO -> "Auto"
    }

    private fun modeDenied(action: String): String =
        "当前权限模式 ${modeLabel()} 不允许$action；这不是系统故障，请勿重复调用"

    private fun shellScopeDenied(): String =
        "当前目录范围为 ${scopeDescription()}。shell 命令无法可靠限制在这些目录内，因此未执行；" +
            "请在会话的权限面板清空目录范围后重试。Auto 只取消应用内确认，不会绕过显式目录范围；" +
            "这不是 Android 系统权限或工具回收故障，请勿反复切换模式"

    private fun isPermissionMetadata(file: File): Boolean {
        val canonical = file.canonicalFile
        val conversations = store.conversationsDir.canonicalFile
        return canonical == store.configFile.canonicalFile ||
            canonical.path.startsWith(conversations.path.trimEnd(File.separatorChar) + File.separator)
    }
}
