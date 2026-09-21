package com.example.myapplication.ui.chat

import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.provider.ProviderJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Human-readable tool output kept separate from the raw protocol payload. */
internal data class ToolPresentation(
    val title: String,
    val summary: String? = null,
    val path: String? = null
)

internal fun presentTool(call: ToolCallInfo, result: ChatMessage?): ToolPresentation {
    val path = toolArgument(call.argumentsJson, if (call.name == Tools.RUN_COMMAND) "command" else "path")
    val content = result?.content.orEmpty()
    val title = when (call.name) {
        Tools.EDIT_FILE -> "修改文件"
        Tools.DELETE_FILE -> "删除文件"
        Tools.RUN_COMMAND -> "运行命令"
        Tools.ENTER_PLAN_MODE -> "进入计划模式"
        Tools.EXIT_PLAN_MODE -> "提交计划"
        else -> friendlyToolTitle(call.name)
    }
    if (result == null) return ToolPresentation(title = title, path = path)

    val summary = if (result.isError && isStructuredResult(content)) {
        "工具执行失败"
    } else when (call.name) {
        Tools.WRITE_FILE -> if (result.isError) resultExcerpt(content) else "已写入"
        Tools.EDIT_FILE -> if (result.isError) resultExcerpt(content) else "已修改"
        Tools.DELETE_FILE -> if (result.isError) resultExcerpt(content) else "已删除"
        Tools.READ_FILE -> readSummary(path, content, result.isError)
        Tools.LIST_FILES -> fileListSummary(content, result.isError)
        Tools.SAVE_MEMORY, Tools.DELETE_MEMORY -> statusSummary(content, result.isError)
        Tools.SEARCH_MEMORY -> memorySearchSummary(content, result.isError)
        Tools.USE_SKILL, Tools.SAVE_SKILL -> statusSummary(content, result.isError)
        Tools.RUN_COMMAND -> commandSummary(content, result.isError)
        Tools.ENTER_PLAN_MODE -> if (result.isError) resultExcerpt(content) else "已进入计划模式"
        Tools.EXIT_PLAN_MODE -> planSubmissionSummary(content, result.isError)
        else -> statusSummary(content, result.isError)
    }
    return ToolPresentation(title = title, summary = summary, path = path)
}

private fun toolArgument(argumentsJson: String, name: String): String? = runCatching {
    ProviderJson.parseToJsonElement(argumentsJson).jsonObject[name]
        ?.jsonPrimitive?.content
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun readSummary(path: String?, content: String, isError: Boolean): String {
    if (isError) return resultExcerpt(content)
    val excerpt = resultExcerpt(content, maxLength = 120)
    return excerpt.ifBlank { path?.let { "已读取 $it" } ?: "已读取文件" }
}

private fun fileListSummary(content: String, isError: Boolean): String {
    if (isError) return resultExcerpt(content)
    val paths = content.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("(") && !it.startsWith("（") && !it.startsWith("...") }
        .toList()
    if (paths.isEmpty()) return if (content.contains("为空")) "目录为空" else "未找到文件"
    val names = paths.take(3).map { path ->
        path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\') +
            if (path.endsWith('/') || path.endsWith('\\')) "/" else ""
    }
    return "已列出 ${paths.size} 项：" + names.joinToString("、") + if (paths.size > 3) "…" else ""
}

private fun memorySearchSummary(content: String, isError: Boolean): String {
    if (isError) return resultExcerpt(content)
    if (content.contains("未找到")) return "未找到相关记忆"
    val count = content.lineSequence().count { it.startsWith("[id=") }
    return if (count > 0) "找到 $count 条记忆" else statusSummary(content, false)
}

private fun commandSummary(content: String, isError: Boolean): String {
    val exitPattern = Regex("(?:exit(?: code)?|退出码)\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    val excerpt = resultExcerpt(content, maxLength = 160)
    if (isError) return excerpt
    val exit = exitPattern
        .find(content)?.groupValues?.getOrNull(1)
    val output = content.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotBlank() && !exitPattern.containsMatchIn(it) }
        ?.let { resultExcerpt(it, maxLength = 160) }
    return listOfNotNull(exit?.let { "退出码 $it" }, output ?: excerpt.takeIf { it.isNotBlank() }).joinToString(" · ")
        .ifBlank { "命令已执行" }
}

private fun planSubmissionSummary(content: String, isError: Boolean): String {
    if (isError) return resultExcerpt(content)
    return when {
        content.contains("已接受") -> "计划已提交并接受"
        content.isBlank() -> "已提交计划"
        else -> resultExcerpt(content)
    }
}

private fun statusSummary(content: String, isError: Boolean): String {
    val excerpt = resultExcerpt(content)
    return if (isError) excerpt else excerpt.ifBlank { "已完成" }
}

internal fun resultExcerpt(content: String, maxLength: Int = 160): String = content
    .lineSequence()
    .map(String::trim)
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()
    .replace(Regex("\\s+"), " ")
    .let { if (isStructuredResult(it)) "已返回结构化结果" else it.take(maxLength) }

private fun isStructuredResult(content: String): Boolean {
    val trimmed = content.trimStart()
    return trimmed.startsWith("{") || trimmed.startsWith("[{") || trimmed.startsWith("[\"")
}
