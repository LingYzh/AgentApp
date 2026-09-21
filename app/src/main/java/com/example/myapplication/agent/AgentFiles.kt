package com.example.myapplication.agent

import com.example.myapplication.data.store.FileStore
import java.io.File

/** Resolves relative paths through FileStore and applies optional canonical directory scopes. */
class AgentFiles(private val store: FileStore, private val allowedDirectories: () -> List<String>) {
    fun resolve(path: String): File {
        require(path.isNotBlank()) { "路径不能为空" }
        val raw = File(path)
        val resolved = if (raw.isAbsolute) raw.canonicalFile else store.workspaceFile(path).canonicalFile
        checkScope(resolved)
        return resolved
    }

    fun displayPath(file: File): String {
        val canonical = file.canonicalFile
        val workspace = store.workspaceFile(".").canonicalFile
        val prefix = workspace.path + File.separator
        return if (canonical.path.startsWith(prefix)) {
            canonical.path.removePrefix(prefix).replace(File.separatorChar, '/')
        } else canonical.path
    }

    private fun checkScope(file: File) {
        val rawScopes = allowedDirectories()
        if (rawScopes.isEmpty()) return
        val scopes = rawScopes.map { raw ->
            require(File(raw).isAbsolute) { "已授权目录必须是绝对路径: $raw" }
            File(raw).canonicalFile
        }
        if (scopes.none { dir ->
                val prefix = dir.path.trimEnd(File.separatorChar) + File.separator
                file == dir || file.path.startsWith(prefix)
            }) {
            throw SecurityException("权限限制：路径不在已授权目录内: ${file.path}。当前范围：${scopes.joinToString { it.path }}。请在范围内操作，不要反复重试此路径。")
        }
    }
}
