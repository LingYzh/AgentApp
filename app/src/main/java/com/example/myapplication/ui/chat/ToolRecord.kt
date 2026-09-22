package com.example.myapplication.ui.chat

import com.example.myapplication.data.model.ChatMessage

/** Only decode the exact prefix written by ShellCommandRunner; output text is not metadata. */
internal data class CommandRecord(val output: String, val exitCode: Int?)

internal fun commandRecord(raw: String): CommandRecord {
    val first = raw.substringBefore('\n')
    val match = Regex("^(?:退出码 |错误: 命令退出码 )(-?\\d+)$").matchEntire(first)
    return if (match == null) CommandRecord(raw, null)
    else CommandRecord(raw.substringAfter('\n', ""), match.groupValues[1].toIntOrNull())
}

internal fun commandCaption(result: ChatMessage?, running: Boolean, queued: Boolean, approval: Boolean): String = when {
    result?.isError == true && result.content in setOf("未执行：任务已中断", "执行已中断，结果需确认") -> "命令已中止"
    result?.isError == true -> "命令执行失败"
    result != null -> "运行了命令"
    approval -> "等待命令批准"
    running -> "正在运行命令"
    queued -> "等待执行命令"
    else -> "命令尚未返回"
}
