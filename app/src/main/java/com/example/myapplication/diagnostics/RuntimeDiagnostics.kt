package com.example.myapplication.diagnostics

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Metadata only: never pass prompts, file bodies, commands, HTTP headers or credentials. */
object RuntimeDiagnostics {
    @Volatile private var log: DiagnosticLog? = null
    @Volatile var sharedStorageAccess: () -> Boolean? = { null }

    fun initialize(directory: File) { log = DiagnosticLog(directory) }

    fun event(name: String, vararg fields: Pair<String, Any?>) {
        // Diagnostic failures must never interrupt a tool or the model stream.
        runCatching { log?.append(name, fields.toMap()) }
    }

    fun storageAccessSummary(): String = when (runCatching { sharedStorageAccess() }.getOrNull()) {
        true -> "Android 共享存储授权：已开启；仍不能访问其他 App 私有目录及系统保护区域。"
        false -> "Android 共享存储授权：未开启。请用户在设置 → 共享存储中授予所有文件访问。能创建本 App 的文件不代表能读取其他来源的已有文件；Auto/Accept Edit 不会授予 Android 权限。"
        null -> "Android 共享存储授权：状态未知，以系统实际返回为准。"
    }

    fun permissionFailure(error: Throwable): Boolean = generateSequence(error) { it.cause }
        .take(8).any { it.message?.let { message ->
            message.contains("EACCES") || message.contains("EPERM") ||
                message.contains("Permission denied", ignoreCase = true)
        } == true }

    fun fileFailure(tool: String, path: String, error: Throwable) {
        val target = File(path)
        event("file_failure", "tool" to tool, "path" to path,
            "errorClass" to error.javaClass.simpleName,
            "osPermissionDenied" to permissionFailure(error),
            "allFilesAccess" to runCatching { sharedStorageAccess() }.getOrNull(),
            "exists" to runCatching { target.exists() }.getOrNull(),
            "readable" to runCatching { target.canRead() }.getOrNull(),
            "writable" to runCatching { target.canWrite() }.getOrNull())
    }
}

/** Bounded JSONL files, private to the app and retrievable with adb run-as on debug builds. */
class DiagnosticLog(private val directory: File, private val maxBytes: Long = 1024 * 1024L) {
    @Synchronized fun append(event: String, fields: Map<String, Any?> = emptyMap()) {
        directory.mkdirs()
        val current = File(directory, "runtime.jsonl")
        val line = buildJsonObject {
            put("time", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date()))
            put("event", event.take(80))
            fields.entries.take(24).forEach { (key, value) -> put(key.take(80), value?.toString()?.take(1000)) }
        }.toString() + "\n"
        if (current.length() + line.toByteArray(Charsets.UTF_8).size > maxBytes) {
            val oldest = File(directory, "runtime.2.jsonl")
            if (oldest.exists()) check(oldest.delete())
            val previous = File(directory, "runtime.1.jsonl")
            if (previous.exists()) check(previous.renameTo(oldest))
            if (current.exists()) check(current.renameTo(previous))
        }
        current.appendText(line, Charsets.UTF_8)
    }
}
