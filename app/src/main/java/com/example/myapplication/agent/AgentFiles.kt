package com.example.myapplication.agent

import com.example.myapplication.data.store.FileStore
import java.io.File

/** Resolves relative paths through the selected directory and applies canonical file-tool scopes. */
class AgentFiles(
    private val store: FileStore,
    private val workingDirectory: () -> String? = { null },
    private val allowedDirectories: () -> List<String>
) {
    fun resolve(path: String): File {
        val resolved = lexical(path).canonicalFile
        checkScope(resolved)
        return resolved
    }

    /** The selected directory is validated on every operation so a deleted directory never falls back. */
    fun workingDirectory(): File = resolveWorkingDirectory(workingDirectory.invoke())

    /** Returns the canonical selection (or null for the workspace default), and throws if invalid. */
    fun validateWorkingDirectory(path: String?): String? {
        val directory = resolveWorkingDirectory(path)
        return path?.let { directory.canonicalPath }
    }

    /**
     * Keeps an attachment's stored workspace reference independent from the conversation cwd.
     * [resolve] still checks the final file against the session scope.
     */
    fun lexical(path: String): File {
        require(path.isNotBlank()) { "路径不能为空" }
        val raw = File(path)
        return when {
            raw.isAbsolute -> raw.absoluteFile
            path.startsWith("attachments/") -> store.workspaceFile(path).absoluteFile
            // Legacy workspace-relative references retain FileStore containment. The final
            // file, rather than the workspace root, is checked against a narrower scope.
            workingDirectory.invoke() == null -> store.workspaceFile(path).absoluteFile
            else -> File(workingDirectory(), path).absoluteFile
        }
    }

    fun displayPath(file: File): String {
        val canonical = file.canonicalFile
        val attachments = store.workspaceFile("attachments").canonicalFile
        relativeTo(canonical, attachments)?.let { return "attachments/$it".trimEnd('/') }
        val directory = runCatching { workingDirectory() }.getOrNull()
        return directory?.let { relativeTo(canonical, it) } ?: canonical.path
    }

    private fun resolveWorkingDirectory(path: String?): File {
        val directory = if (path == null) {
            store.workspaceDir.canonicalFile
        } else {
            require(path.isNotBlank()) {
                "工作目录不能为空；请重新选择一个绝对目录，或清空以使用规范工作区"
            }
            val raw = File(path)
            require(raw.isAbsolute) { "工作目录必须是绝对路径: $path" }
            raw.canonicalFile
        }
        require(directory.exists()) { "工作目录不存在: ${directory.path}" }
        require(directory.isDirectory) { "工作目录不是目录: ${directory.path}" }
        return directory
    }

    private fun relativeTo(file: File, directory: File): String? {
        if (file == directory) return ""
        val prefix = directory.path.trimEnd(File.separatorChar) + File.separator
        return file.path.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.replace(File.separatorChar, '/')
    }

    private fun checkScope(file: File) {
        val additional = allowedDirectories().map { raw ->
            require(File(raw).isAbsolute) { "已授权目录必须是绝对路径: $raw" }
            File(raw).canonicalFile
        }
        // A stale cwd must not prevent absolute paths in an additional directory from working.
        // Relative paths already resolve through workingDirectory(), so they still fail clearly.
        val scopes = (listOfNotNull(runCatching { workingDirectory() }.getOrNull()) + additional)
            .distinctBy { it.path }
        if (scopes.isEmpty()) {
            throw SecurityException("权限限制：当前工作目录不可用，且未配置可用的额外目录")
        }
        if (scopes.none { dir ->
                val prefix = dir.path.trimEnd(File.separatorChar) + File.separator
                file == dir || file.path.startsWith(prefix)
            }) {
            throw SecurityException("权限限制：路径不在文件工具有效范围内: ${file.path}。当前范围：${scopes.joinToString { it.path }}。请在范围内操作，不要反复重试此路径。")
        }
    }
}
