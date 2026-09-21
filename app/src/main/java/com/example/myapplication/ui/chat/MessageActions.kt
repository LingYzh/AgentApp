package com.example.myapplication.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.ChatMessage

@Composable
internal fun MessageActions(message: ChatMessage, canEdit: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (message.excludedFromContext) Text("未纳入上下文", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
        IconButton(onClick = {
            val copyText = message.content.ifBlank {
                message.attachments.joinToString("\n") { it.name }.ifBlank {
                    message.toolCalls.joinToString("\n\n") { "${friendlyToolTitle(it.name)}\n${it.argumentsJson}" }
                }
            }
            clipboard.setText(AnnotatedString(copyText))
        }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.ContentCopy, "复制消息", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canEdit) Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.MoreHoriz, "消息操作", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("编辑消息") }, onClick = { expanded = false; onEdit() })
                DropdownMenuItem(text = { Text("删除消息", color = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; onDelete() })
            }
        }
    }
}

@Composable
internal fun EditMessageDialog(message: ChatMessage, onDismiss: () -> Unit,
    onSave: (String, Set<String>, Boolean) -> Unit) {
    var text by remember(message.id) { mutableStateOf(message.content) }
    var attachmentIds by remember(message.id) { mutableStateOf(message.attachments.map { it.id }.toSet()) }
    var included by remember(message.id) { mutableStateOf(!message.excludedFromContext) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑消息") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("保存会改变后续请求的历史内容及缓存。不会重新执行工具，也不会自动发送。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10,
                label = { Text("消息正文") })
            message.attachments.forEach { attachment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(attachment.id in attachmentIds, onCheckedChange = { checked ->
                        attachmentIds = if (checked) attachmentIds + attachment.id else attachmentIds - attachment.id
                    })
                    Text(attachment.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
            if (message.attachments.isNotEmpty()) Text("取消勾选可从此消息移除附件。", style = MaterialTheme.typography.bodySmall)
            if (message.excludedFromContext && message.toolCalls.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(included, onCheckedChange = { included = it })
                Text("用于后续上下文", style = MaterialTheme.typography.bodySmall)
            }
            if (message.toolCalls.isNotEmpty()) Text("仅编辑正文；工具调用及执行记录会保留。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {
        TextButton(enabled = text.isNotBlank() || attachmentIds.isNotEmpty() || message.toolCalls.isNotEmpty(),
            onClick = { onSave(text, attachmentIds, included) }) { Text("保存") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
