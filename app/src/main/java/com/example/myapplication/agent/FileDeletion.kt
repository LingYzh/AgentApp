package com.example.myapplication.agent

import com.example.myapplication.data.model.PermissionMode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Single-file deletion only; no recursive walk or automatic retry after an approval. */
internal class FileDeletion(private val session: PermissionSession) {
    private data class Target(val file: File, val bytes: Long, val modified: Long)

    private fun target(path: String): Target {
        session.toolBlockReason(Tools.DELETE_FILE)?.let { throw SecurityException(it) }
        session.canWritePath(path)?.let { throw SecurityException(it) }
        val raw = session.files.lexical(path)
        val file = session.files.resolve(path)
        val unlinked = File(requireNotNull(raw.parentFile).canonicalFile, raw.name)
        require(unlinked == file) { "删除工具不接受符号链接或特殊路径" }
        require(file.isFile) { "只能删除已存在的普通文件，不能删除目录: $path" }
        return Target(file, file.length(), file.lastModified())
    }

    suspend fun delete(path: String): String {
        val before = target(path)
        if (session.conversation.permissionMode != PermissionMode.AUTO) {
            val answer = session.coordinator.request(PermissionRequest(
                conversationId = session.conversation.id,
                kind = PermissionRequestKind.FILE_DELETE,
                filePath = before.file.path
            ))
            if (answer.decision != PermissionDecision.ALLOW_ONCE) return "错误: 用户拒绝删除文件；不要重试，等待用户新指示。"
        }
        currentCoroutineContext().ensureActive()
        val after = target(path)
        require(before == after) { "审批期间文件已发生变化，未删除；请重新确认目标" }
        check(after.file.delete()) { "删除失败，可能是 Android 存储权限不足；没有重试" }
        return "已删除 ${session.files.displayPath(after.file)}"
    }
}
