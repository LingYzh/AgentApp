package com.example.myapplication.ui.chat

import com.example.myapplication.ui.components.UiTextButton
import com.example.myapplication.ui.components.inertWhen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.myapplication.ui.components.InlineCodePanel
import com.example.myapplication.ui.components.ToolRecordPanel
import com.example.myapplication.ui.files.InlineFileDiff
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.data.store.FileChanges
import com.example.myapplication.ui.files.FileDiffStats
import java.io.File

@Composable
internal fun AttachmentChip(
    attachment: MessageAttachment,
    file: File?,
    onToggle: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
        modifier = (if (compact) Modifier.width(280.dp) else Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            .padding(vertical = 3.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (attachment.mimeType.startsWith("image/") && file != null) {
                AsyncImage(model = file, contentDescription = attachment.name, modifier = Modifier.size(if (compact) 32.dp else 44.dp))
            } else Icon(Icons.Default.Description, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                val size = if (attachment.sizeBytes >= 1024 * 1024) "%.1f MB".format(attachment.sizeBytes / 1048576.0)
                    else "${(attachment.sizeBytes + 1023) / 1024} KB"
                Text("$size · ${if (attachment.delivery == "native") "模型直接读取" else "工作区文件"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onToggle != null) UiTextButton(onClick = onToggle, enabled = enabled) { Text("切换") }
            if (onRemove != null) IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Default.Close, "移除附件") }
        }
    }
}

@Composable
internal fun ToolActivityRow(
    call: ToolCallInfo,
    result: ChatMessage?,
    running: Boolean,
    child: Conversation?,
    onOpenChild: (String) -> Unit,
    onViewFile: (String) -> Unit,
    queued: Boolean = false,
    allowFileNavigation: Boolean = true,
    awaitingApproval: Boolean = false
) {
    var expanded by rememberSaveable(call.id) { mutableStateOf(false) }
    val detailState = rememberSaveableStateHolder()
    val presentation = remember(call, result) { presentTool(call, result) }
    val isWrite = call.name == Tools.WRITE_FILE || call.name == Tools.EDIT_FILE
    val change = result?.takeIf { !it.isError && isWrite }?.fileChange
    val diff by produceState<com.example.myapplication.data.store.FileDiffResult?>(null, change) {
        value = change?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { FileChanges.diff(it) } }
    }
    val completed = result != null && !result.isError
    val label = when {
        call.name == Tools.RUN_COMMAND -> commandCaption(result, running, queued, awaitingApproval)
        isWrite && completed -> if (change?.beforeExists == false) "已新建" else "已编辑"
        call.name == Tools.DELETE_FILE && completed -> "已删除"
        result?.isError == true -> "${presentation.title} · 失败或中止"
        completed -> presentation.title
        awaitingApproval -> "${presentation.title} · 等待批准"
        running -> "${presentation.title} · 进行中"
        queued -> "${presentation.title} · 等待执行"
        else -> "${presentation.title} · 未返回"
    }
    val rowColor = when {
        result?.isError == true -> MaterialTheme.colorScheme.error
        awaitingApproval -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, tween(200), label = "tool chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(toolIcon(call.name), null, Modifier.size(17.dp), tint = rowColor)
            Text(if (isWrite && completed && expanded) "已编辑的文件" else label,
                fontSize = 14.sp, lineHeight = 21.sp, color = rowColor)
            if (isWrite && completed && !expanded) {
                Text((change?.path ?: presentation.path).orEmpty().substringAfterLast('/').substringAfterLast('\\'),
                    Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp, lineHeight = 21.sp, color = rowColor,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                diff?.takeIf { !it.usedFallback && !it.previewOmitted }?.let {
                    FileDiffStats(it)
                }
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(14.dp).rotate(rotation), tint = rowColor)
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(240)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(240)) + fadeOut(tween(180))) {
            detailState.SaveableStateProvider(call.id) {
            Column(Modifier.inertWhen(!expanded), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    call.name == Tools.RUN_COMMAND -> {
                        val command = remember(call.argumentsJson) { runCatching {
                            Json.parseToJsonElement(call.argumentsJson).jsonObject["command"]?.jsonPrimitive?.content
                        }.getOrNull() }
                        val record = if (result == null) null else remember(result.content) { commandRecord(result.content) }
                        ToolRecordPanel(
                            command = command,
                            rawCommandFallback = call.argumentsJson.takeIf { it.isNotBlank() },
                            output = record?.output,
                            exitCode = record?.exitCode,
                            running = running,
                            awaitingApproval = awaitingApproval,
                            queued = queued,
                            cancelled = result?.isError == true && commandCaption(result, running, queued, awaitingApproval) == "命令已中止",
                            failed = result?.isError == true
                        )
                    }
                    isWrite && completed -> {
                        if (change != null) InlineFileDiff(change, diff)
                        else Text("无法显示此次 Diff：没有保存修改快照。", style = MaterialTheme.typography.bodySmall)
                        var showRaw by rememberSaveable(call.id) { mutableStateOf(false) }
                        UiTextButton(onClick = { showRaw = !showRaw }) {
                            Text(if (showRaw) "收起入参与回参" else "查看入参与回参")
                        }
                        AnimatedVisibility(showRaw) {
                            Box(Modifier.inertWhen(!showRaw)) { GenericToolRecord(call, result, running, queued, awaitingApproval) }
                        }
                    }
                    else -> {
                        GenericToolRecord(call, result, running, queued, awaitingApproval)
                    }
                }
                if (child != null) UiTextButton(onClick = { onOpenChild(child.id) }) {
                    Text("查看子代理 · ${childStatus(child.executionStatus)}")
                }
                if (allowFileNavigation && (isWrite || call.name == Tools.READ_FILE)) {
                    (result?.fileChange?.path ?: extractFilePath(call.argumentsJson))?.let { path ->
                        UiTextButton(onClick = { onViewFile(path) }) { Text("查看当前文件") }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun GenericToolRecord(call: ToolCallInfo, result: ChatMessage?, running: Boolean, queued: Boolean, awaitingApproval: Boolean) {
    ToolRecordPanel(command = null, rawCommandFallback = call.argumentsJson, output = result?.content,
        exitCode = null, running = running, awaitingApproval = awaitingApproval, queued = queued,
        cancelled = result?.isError == true && result.content in setOf("未执行：任务已中断", "执行已中断，结果需确认"),
        failed = result?.isError == true, isShell = false, toolName = call.name)
}

internal fun childStatus(status: String?): String = when (status) {
    "running" -> "运行中"
    "completed" -> "已完成"
    "failed" -> "失败"
    "cancelled" -> "已停止"
    else -> "记录"
}
