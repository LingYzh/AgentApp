package com.example.myapplication.data.store

import com.example.myapplication.data.model.FileChange
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * 文件修改快照和文本 diff 的小型工具集。
 *
 * 快照只用于会话里的修改预览，不能限制实际写入的文件大小。超过上限时保留
 * 前缀并设置 previewOmitted，让界面明确告诉用户这不是完整快照。
 */
data class FileSnapshot(
    val exists: Boolean,
    val content: String?,
    val previewOmitted: Boolean = false
)

enum class DiffLineType {
    CONTEXT,
    ADDED,
    REMOVED
}

data class DiffLine(
    val type: DiffLineType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String
)

data class FileDiffResult(
    val lines: List<DiffLine>,
    val addedCount: Int,
    val removedCount: Int,
    val usedFallback: Boolean = false,
    val fallbackReason: String? = null,
    val previewOmitted: Boolean = false,
    val beforeText: String? = null,
    val afterText: String = ""
)

object FileChanges {
    /** 修改快照的总字符预算；实际文件写入不受此限制。 */
    const val MAX_SNAPSHOT_CHARS = 512 * 1024

    // 这些上限防止极端的逐行文件让渲染和 Myers trace 占用不可控的内存。
    private const val MAX_DIFF_LINES = 12_000
    private const val MAX_TRACE_ENTRIES = 400_000L
    private const val MAX_MYERS_WORK = 2_000_000L

    fun snapshot(file: File, maxChars: Int = MAX_SNAPSHOT_CHARS): FileSnapshot {
        require(maxChars >= 0) { "maxChars must be non-negative" }
        if (!file.exists()) return FileSnapshot(exists = false, content = null)
        if (!file.isFile) return FileSnapshot(exists = true, content = null, previewOmitted = true)
        if (maxChars == 0) {
            return FileSnapshot(exists = true, content = "", previewOmitted = file.length() > 0)
        }

        val builder = StringBuilder(min(maxChars.toLong(), file.length()).toInt())
        val buffer = CharArray(min(8192, maxChars + 1))
        FileInputStream(file).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                while (builder.length <= maxChars) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    val remaining = maxChars + 1 - builder.length
                    builder.append(buffer, 0, min(read, remaining))
                    if (builder.length > maxChars) break
                }
            }
        }
        val omitted = builder.length > maxChars
        return FileSnapshot(
            exists = true,
            content = builder.substring(0, min(builder.length, maxChars)),
            previewOmitted = omitted
        )
    }

    /**
     * 组合写入前快照和写入内容。before/after 共用一个预算，避免把大文本持久化到会话。
     */
    fun change(
        path: String,
        before: FileSnapshot,
        after: String,
        maxChars: Int = MAX_SNAPSHOT_CHARS
    ): FileChange {
        require(maxChars >= 0) { "maxChars must be non-negative" }
        var beforeText = if (before.exists) before.content else null
        var afterText = after
        var omitted = before.previewOmitted

        if (beforeText != null && beforeText.length.toLong() + afterText.length > maxChars) {
            omitted = true
            val beforeLength = beforeText.length
            val afterLength = afterText.length
            when {
                beforeLength <= maxChars -> {
                    afterText = afterText.take(maxChars - beforeLength)
                }
                afterLength <= maxChars -> {
                    beforeText = beforeText.take(maxChars - afterLength)
                }
                else -> {
                    val beforeBudget = maxChars / 2
                    beforeText = beforeText.take(beforeBudget)
                    afterText = afterText.take(maxChars - beforeBudget)
                }
            }
        } else if (beforeText == null && afterText.length > maxChars) {
            omitted = true
            afterText = afterText.take(maxChars)
        }

        return FileChange(
            path = path,
            before = beforeText,
            after = afterText,
            beforeExists = before.exists,
            previewOmitted = omitted
        )
    }

    /** Convert a validated workspace file back to the path shown by listWorkspace(). */
    fun relativePath(store: FileStore, file: File): String {
        val root = store.workspaceDir.canonicalFile
        val target = file.canonicalFile
        require(target == root || target.path.startsWith(root.path + File.separator)) {
            "文件不在工作区内"
        }
        return target.path
            .removePrefix(root.path)
            .trimStart(File.separatorChar, '/', '\\')
            .replace(File.separatorChar, '/')
    }

    fun diff(change: FileChange): FileDiffResult {
        val beforeText = if (change.beforeExists) change.before else ""
        val afterText = change.after
        if (change.beforeExists && change.before == null) {
            return fallback(
                before = null,
                after = afterText,
                reason = "原文件快照不可用，已降级为当前快照对照",
                previewOmitted = change.previewOmitted
            )
        }

        val beforeLines = splitLines(beforeText ?: "")
        val afterLines = splitLines(afterText)
        if (beforeLines == null || afterLines == null) {
            return fallback(
                before = beforeText,
                after = afterText,
                reason = "行数过多，已降级为整块对照",
                previewOmitted = change.previewOmitted
            )
        }

        val operations = myers(beforeLines, afterLines)
        if (operations == null) {
            return fallback(
                before = beforeText,
                after = afterText,
                reason = "变更过于复杂，已降级为整块对照",
                previewOmitted = change.previewOmitted
            )
        }

        var oldNumber = 1
        var newNumber = 1
        var added = 0
        var removed = 0
        val lines = ArrayList<DiffLine>(operations.size)
        operations.forEach { operation ->
            when (operation.type) {
                OperationType.CONTEXT -> {
                    lines += DiffLine(
                        DiffLineType.CONTEXT,
                        oldNumber++,
                        newNumber++,
                        beforeLines[operation.oldIndex]
                    )
                }
                OperationType.REMOVED -> {
                    lines += DiffLine(DiffLineType.REMOVED, oldNumber++, null, beforeLines[operation.oldIndex])
                    removed++
                }
                OperationType.ADDED -> {
                    lines += DiffLine(DiffLineType.ADDED, null, newNumber++, afterLines[operation.newIndex])
                    added++
                }
            }
        }
        return FileDiffResult(
            lines = lines,
            addedCount = added,
            removedCount = removed,
            previewOmitted = change.previewOmitted,
            beforeText = beforeText,
            afterText = afterText
        )
    }

    fun diff(before: String?, after: String, beforeExists: Boolean = before != null): FileDiffResult =
        diff(FileChange(path = "", before = before, after = after, beforeExists = beforeExists))

    private fun splitLines(text: String): List<String>? {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<String>()
        var start = 0
        while (true) {
            val end = text.indexOf('\n', start)
            val lineEnd = if (end >= 0) end else text.length
            result += text.substring(start, lineEnd).removeSuffix("\r")
            if (result.size > MAX_DIFF_LINES) return null
            if (end < 0) return result
            start = end + 1
            if (start == text.length) {
                result += ""
                return if (result.size <= MAX_DIFF_LINES) result else null
            }
        }
    }

    private fun fallback(
        before: String?,
        after: String,
        reason: String,
        previewOmitted: Boolean
    ): FileDiffResult {
        val oldLines = before?.let(::splitLines)
        val newLines = splitLines(after)
        val oldCount = oldLines?.size ?: countLines(before)
        val newCount = newLines?.size ?: countLines(after)
        val lines = if (oldLines != null && newLines != null && oldLines.size + newLines.size <= MAX_DIFF_LINES) {
            buildList(oldLines.size + newLines.size) {
                oldLines.forEachIndexed { index, line ->
                    add(DiffLine(DiffLineType.REMOVED, index + 1, null, line))
                }
                newLines.forEachIndexed { index, line ->
                    add(DiffLine(DiffLineType.ADDED, null, index + 1, line))
                }
            }
        } else {
            emptyList()
        }
        return FileDiffResult(
            lines = lines,
            addedCount = newCount,
            removedCount = oldCount,
            usedFallback = true,
            fallbackReason = reason,
            previewOmitted = previewOmitted,
            beforeText = before,
            afterText = after
        )
    }

    private fun countLines(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        return 1 + text.count { it == '\n' }
    }

    private enum class OperationType { CONTEXT, ADDED, REMOVED }

    private data class Operation(
        val type: OperationType,
        val oldIndex: Int = -1,
        val newIndex: Int = -1
    )

    /** Myers frontier search. Trace storage is bounded; callers get an explicit fallback on overflow. */
    private fun myers(before: List<String>, after: List<String>): List<Operation>? {
        val oldSize = before.size
        val newSize = after.size
        val max = oldSize + newSize
        if (max == 0) return emptyList()
        val offset = max
        val vector = IntArray(max * 2 + 1)
        val trace = ArrayList<IntArray>()
        var work = 0L

        for (distance in 0..max) {
            for (k in -distance..distance step 2) {
                val index = offset + k
                var x = when {
                    k == -distance -> vector[index + 1]
                    k == distance -> vector[index - 1] + 1
                    vector[index - 1] < vector[index + 1] -> vector[index + 1]
                    else -> vector[index - 1] + 1
                }
                var y = x - k
                while (x < oldSize && y < newSize && before[x] == after[y]) {
                    x++
                    y++
                    work++
                    if (work > MAX_MYERS_WORK) return null
                }
                vector[index] = x
                work++
                if (work > MAX_MYERS_WORK) return null
                if (x >= oldSize && y >= newSize) {
                    if (trace.sumOf { it.size.toLong() } + vector.size > MAX_TRACE_ENTRIES) return null
                    trace += vector.copyOf()
                    return backtrack(trace, distance, oldSize, newSize, offset)
                }
            }
            if (trace.sumOf { it.size.toLong() } + vector.size > MAX_TRACE_ENTRIES) return null
            trace += vector.copyOf()
        }
        return null
    }

    private fun backtrack(
        trace: List<IntArray>,
        distance: Int,
        oldSize: Int,
        newSize: Int,
        offset: Int
    ): List<Operation> {
        var x = oldSize
        var y = newSize
        val reversed = ArrayList<Operation>()
        for (d in distance downTo 1) {
            val vector = trace[d - 1]
            val k = x - y
            val previousK = if (k == -d || (k != d && vector[offset + k - 1] < vector[offset + k + 1])) {
                k + 1
            } else {
                k - 1
            }
            val previousX = vector[offset + previousK]
            val previousY = previousX - previousK
            while (x > previousX && y > previousY) {
                reversed += Operation(OperationType.CONTEXT, x - 1, y - 1)
                x--
                y--
            }
            if (x == previousX) {
                reversed += Operation(OperationType.ADDED, newIndex = y - 1)
                y--
            } else {
                reversed += Operation(OperationType.REMOVED, oldIndex = x - 1)
                x--
            }
        }
        while (x > 0 && y > 0) {
            reversed += Operation(OperationType.CONTEXT, x - 1, y - 1)
            x--
            y--
        }
        while (x > 0) {
            reversed += Operation(OperationType.REMOVED, oldIndex = x - 1)
            x--
        }
        while (y > 0) {
            reversed += Operation(OperationType.ADDED, newIndex = y - 1)
            y--
        }
        return reversed.asReversed()
    }
}
