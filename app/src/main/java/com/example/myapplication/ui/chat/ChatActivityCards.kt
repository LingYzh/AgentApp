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
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "tool chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(toolIcon(call.name), null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (isWrite && completed) {
                Text((change?.path ?: presentation.path).orEmpty().substringAfterLast('/').substringAfterLast('\\'),
                    Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge)
                diff?.takeIf { !it.usedFallback && !it.previewOmitted }?.let {
                    FileDiffStats(it)
                }
            }
            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp).rotate(rotation))
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(240)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(240)) + fadeOut(tween(180))) {
            Column(Modifier.inertWhen(!expanded), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    call.name == Tools.RUN_COMMAND -> {
                        val command = remember(call.argumentsJson) { runCatching {
                            Json.parseToJsonElement(call.argumentsJson).jsonObject["command"]?.jsonPrimitive?.content
                        }.getOrNull() }
                        if (call.argumentsJson.isEmpty()) Text("原始命令未保存在此记录中。", style = MaterialTheme.typography.bodySmall)
                        else InlineCodePanel(if (command == null) "参数（无法解析命令）" else "命令", command ?: call.argumentsJson)
                        if (result == null) Text("等待此调用返回输出", style = MaterialTheme.typography.bodySmall)
                        else {
                            val record = remember(result.content) { commandRecord(result.content) }
                            record.exitCode?.let { Text("退出码 $it", style = MaterialTheme.typography.labelSmall) }
                            InlineCodePanel(if (result.isError) "错误 / 输出" else "输出", record.output)
                        }
                    }
                    isWrite && completed -> {
                        if (change != null) InlineFileDiff(change, diff)
                        else Text("无法显示此次 Diff：没有保存修改快照。", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        if (call.argumentsJson.isEmpty()) Text("原始参数未保存在此记录中。", style = MaterialTheme.typography.bodySmall)
                        else InlineCodePanel("参数 · ${call.name}", call.argumentsJson)
                        if (result == null) Text("等待此调用返回结果")
                        else InlineCodePanel("原始结果", result.content, "本次工具返回空内容")
                    }
                }
                if (child != null) UiTextButton(onClick = { onOpenChild(child.id) }) {
                    Text("查看子代理 · ${childStatus(child.executionStatus)}")
                }
                if (allowFileNavigation && (isWrite || call.name == Tools.READ_FILE)) {
                    extractFilePath(call.argumentsJson)?.let { path ->
                        UiTextButton(onClick = { onViewFile(path) }) { Text("查看当前文件") }
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
