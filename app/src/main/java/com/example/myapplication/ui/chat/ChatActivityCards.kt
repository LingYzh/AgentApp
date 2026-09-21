package com.example.myapplication.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.data.store.FileChanges
import com.example.myapplication.ui.files.FileDiffDialog
import com.example.myapplication.ui.files.FileDiffPreview
import com.example.myapplication.ui.files.FileDiffStats
import java.io.File

@Composable
internal fun AttachmentChip(
    attachment: MessageAttachment,
    file: File?,
    onToggle: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (attachment.mimeType.startsWith("image/") && file != null) {
                AsyncImage(model = file, contentDescription = attachment.name, modifier = Modifier.size(44.dp))
            } else Icon(Icons.Default.Description, null, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                val size = if (attachment.sizeBytes >= 1024 * 1024) "%.1f MB".format(attachment.sizeBytes / 1048576.0)
                    else "${(attachment.sizeBytes + 1023) / 1024} KB"
                Text("$size · ${if (attachment.delivery == "native") "模型直接读取" else "工作区文件"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onToggle != null) TextButton(onClick = onToggle, enabled = enabled) { Text("切换") }
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
    allowFileNavigation: Boolean = true
) {
    if (call.name == Tools.RUN_SUBAGENT) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (child?.executionStatus == "running" || (running && result == null))
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.SmartToy, null, Modifier.size(18.dp))
            Text(child?.title ?: "子代理", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge)
            Text(child?.let { childStatus(it.executionStatus) } ?: when {
                result?.isError == true -> "失败"
                result != null -> "已完成"
                running -> "运行中"
                queued -> "等待执行"
                else -> "未完成"
            }, style = MaterialTheme.typography.labelSmall)
        }
        return
    }
    var showDiff by rememberSaveable(call.id) { mutableStateOf(false) }
    val isFileWrite = call.name == Tools.WRITE_FILE || call.name == Tools.EDIT_FILE
    val fileChange = result?.takeIf { !it.isError && isFileWrite }?.fileChange
    if (showDiff) fileChange?.let { FileDiffDialog(it) { showDiff = false } }
    var showDetails by rememberSaveable(call.id) { mutableStateOf(false) }
    var showRaw by rememberSaveable(call.id) { mutableStateOf(false) }
    var showFull by rememberSaveable(call.id) { mutableStateOf(false) }
    val presentation = remember(call, result) { presentTool(call, result) }
    val displayPath = fileChange?.path ?: presentation.path
    val fileDiff = remember(fileChange) { fileChange?.let { change -> FileChanges.diff(change) } }
    val status = when {
        result?.isError == true && result.content.startsWith("未执行") -> "未执行"
        result?.isError == true && (result.content.contains("中断") || result.content.contains("取消")) -> "已停止"
        result?.isError == true -> "失败"
        result != null -> "完成"
        running -> "进行中"
        queued -> "等待执行"
        else -> "未完成"
    }
    val tint = if (result?.isError == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Surface(shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running && result == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(if (result?.isError == true) Icons.Default.ErrorOutline else if (result != null) Icons.Default.Check else toolIcon(call.name),
                    null, Modifier.size(18.dp), tint = tint)
                Column(Modifier.weight(1f)) {
                    Text(presentation.title, style = MaterialTheme.typography.labelLarge)
                    displayPath?.let {
                        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = tint)
                    }
                    presentation.summary?.takeIf { fileDiff == null }?.let {
                        Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    fileDiff?.let { diff ->
                        FileDiffStats(
                            result = diff,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    if (child != null) Text(child.modelOverride.orEmpty(), maxLines = 1,
                        style = MaterialTheme.typography.bodySmall, color = tint)
                }
                Text(status, style = MaterialTheme.typography.labelSmall, color = tint)
                IconButton(onClick = { showDetails = !showDetails }, modifier = Modifier.size(40.dp)) {
                    Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (showDetails) "收起工具详情" else "查看工具详情", tint = tint)
                }
            }
        }
        if (child != null) TextButton(onClick = { onOpenChild(child.id) }) { Text("查看子代理 · ${childStatus(child.executionStatus)}") }
        if (showDetails) {
            Column(Modifier.padding(start = 18.dp, top = 6.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fileChange?.let { change ->
                    FileDiffPreview(
                        change = change,
                        onOpenFull = { showDiff = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (fileChange == null) {
                    Text("结果", style = MaterialTheme.typography.labelSmall, color = tint)
                    Text(
                        text = presentation.summary ?: "暂无可显示结果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (allowFileNavigation && (isFileWrite || call.name == Tools.READ_FILE)) {
                    extractFilePath(call.argumentsJson)?.let { path -> TextButton(onClick = { onViewFile(path) }) { Text("查看文件") } }
                }
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "收起原始数据" else "原始数据")
                }
                if (showRaw) {
                    Text("参数 · ${call.name}", style = MaterialTheme.typography.labelSmall, color = tint)
                    SelectionContainer {
                        Text(
                            if (showFull) call.argumentsJson else call.argumentsJson.take(2000),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    result?.let {
                        Text("原始结果", style = MaterialTheme.typography.labelSmall, color = tint)
                        SelectionContainer {
                            Text(
                                if (showFull) it.content else it.content.take(3000),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = tint
                            )
                        }
                    }
                    if (call.argumentsJson.length > 2000 || (result?.content?.length ?: 0) > 3000) {
                        TextButton(onClick = { showFull = !showFull }) {
                            Text(if (showFull) "收起长内容" else "显示完整内容")
                        }
                    }
                }
            }
        }
    }
}

internal fun childStatus(status: String?): String = when (status) {
    "running" -> "运行中"
    "completed" -> "已完成"
    "failed" -> "失败"
    "cancelled" -> "已停止"
    else -> "记录"
}

/** The plan entry stays available even before a conversation has child agents. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDrawer(
    children: List<Conversation>,
    planContent: String? = null,
    onOpenPlan: () -> Unit = {},
    onOpen: (String) -> Unit
) {
    ModalDrawerSheet(Modifier.fillMaxWidth(0.9f).fillMaxHeight()) {
        Text("会话面板", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        HorizontalDivider()
        Card(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.AccountTree, contentDescription = null, Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("计划", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = planContent?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.trimStart('#')?.trim() ?: "尚未创建计划",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "打开计划", Modifier.size(18.dp))
            }
        }
        Text("子代理 · ${children.size}", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        if (children.isEmpty()) Text("主代理委派任务后，子代理将在此显示。", Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, group) in listOf("运行中" to children.filter { it.executionStatus == "running" },
                "已结束" to children.filter { it.executionStatus != "running" })) {
                if (group.isNotEmpty()) {
                    item { Text(label, Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium) }
                    items(group, key = { it.id }) { child ->
                        Card(onClick = { onOpen(child.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(child.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${childStatus(child.executionStatus)} · ${child.modelOverride.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
