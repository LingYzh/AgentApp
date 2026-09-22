package com.example.myapplication.ui.chat

import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.agent.PermissionRequest
import com.example.myapplication.agent.PermissionRequestKind
import com.example.myapplication.agent.PermissionDecision
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.ui.components.MarkdownContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionPermissionsDialog(
    current: PermissionMode,
    directories: List<String>,
    onDismiss: () -> Unit,
    onSave: (PermissionMode, List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    var scope by remember { mutableStateOf(directories.joinToString("\n")) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 22.dp)) {
            PanelHeading("权限与目录", onDismiss)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("控制这段会话可以执行的操作。工具可见，不等于执行已获授权。", Modifier.padding(vertical = 14.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(PermissionMode.ACCEPT_EDIT, PermissionMode.PLAN, PermissionMode.READONLY, PermissionMode.AUTO).forEach { mode ->
                    Surface(onClick = { selected = mode }, color = androidx.compose.ui.graphics.Color.Transparent,
                        shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Icon(Icons.Outlined.VerifiedUser, null, Modifier.padding(12.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                Text(when (mode) {
                                    PermissionMode.ACCEPT_EDIT -> "允许文件修改；删除文件逐次确认；系统命令逐次授权，已放行命令除外"
                                    PermissionMode.PLAN -> "只允许编辑本会话的计划文件"
                                    PermissionMode.AUTO -> "无需 App 内审批；仍遵守下方目录范围与 Android 系统权限"
                                    PermissionMode.READONLY -> "仅读取，不执行命令；主代理可主动进入 Plan，仅编辑指定计划文件"
                                }, style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected == mode, onClick = null)
                        }
                    }
                    HorizontalDivider()
                }
                OutlinedTextField(value = scope, onValueChange = { scope = it; error = null },
                    label = { Text("允许访问的目录（可选）") }, minLines = 4, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Text("留空可访问所有系统允许的目录。限定目录后，各模式均禁用任意系统命令，因为当前没有可靠的命令目录沙箱。记忆和 Skill 专用工具使用各自的应用内部目录，不受此范围限制；Readonly 和 Plan 仍禁止修改它们。",
                    style = MaterialTheme.typography.bodySmall)
                Text(com.example.myapplication.diagnostics.RuntimeDiagnostics.storageAccessSummary(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
                Button(onClick = {
            val paths = scope.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (paths.any { !java.io.File(it).isAbsolute }) error = "请使用绝对路径，例如 /storage/emulated/0/Documents"
            else onSave(selected, paths)
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("应用到本会话") }
            }
        }
    }
}

@Composable
internal fun PlanDocumentDialog(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("会话计划", style = MaterialTheme.typography.headlineSmall)
                    UiTextButton(onClick = onDismiss) { Text("关闭") }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (text.isBlank()) Text("尚未生成计划。Agent 进入 Plan 模式后会在此更新计划。")
                    else MarkdownContent(text)
                }
            }
        }
    }
}

@Composable
internal fun PermissionRequestDialog(request: PermissionRequest, onResolve: (PermissionDecision, String) -> Unit) {
    var feedback by remember(request.id) { mutableStateOf("") }
    var editingFeedback by remember(request.id) { mutableStateOf(false) }
    val isPlan = request.kind == PermissionRequestKind.PLAN
    val isDelete = request.kind == PermissionRequestKind.FILE_DELETE
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).imePadding().heightIn(max = 780.dp)
            .then(if (isPlan) Modifier.fillMaxHeight(0.92f) else Modifier.wrapContentHeight()),
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isPlan) "审阅计划" else if (isDelete) "确认删除文件" else "授权执行命令", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f, fill = isPlan).verticalScroll(rememberScrollState())) {
                    if (isPlan) {
                        Text(request.planPath, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(12.dp))
                        MarkdownContent(request.planText)
                    } else if (isDelete) {
                        Text("此操作会永久删除下面的文件，不会移入回收站。", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        SelectionContainer { Text(request.filePath, fontFamily = FontFamily.Monospace) }
                    } else {
                        Text("工作目录：${request.workingDirectory}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            SelectionContainer { Text(request.command, modifier = Modifier.fillMaxWidth().padding(12.dp),
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                if (editingFeedback) {
                    OutlinedTextField(feedback, { feedback = it }, label = { Text("修改意见") }, maxLines = 4,
                        modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onResolve(PermissionDecision.FEEDBACK, feedback) }, enabled = feedback.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()) { Text("提交意见并修改计划") }
                } else if (isPlan) {
                    Button(onClick = { onResolve(PermissionDecision.ACCEPT_EDIT, "") }, modifier = Modifier.fillMaxWidth()) {
                        Text("接受并执行 · Accept Edit")
                    }
                    OutlinedButton(onClick = { onResolve(PermissionDecision.ACCEPT_AUTO, "") }, modifier = Modifier.fillMaxWidth()) {
                        Text("接受并执行 · Auto")
                    }
                } else {
                    Button(onClick = { onResolve(PermissionDecision.ALLOW_ONCE, "") }, modifier = Modifier.fillMaxWidth(),
                        colors = if (isDelete) ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFA33732), contentColor = androidx.compose.ui.graphics.Color.White)
                            else ButtonDefaults.buttonColors()) { Text(if (isDelete) "确认删除此文件" else "允许本次") }
                    if (request.canAlwaysAllow) OutlinedButton(onClick = { onResolve(PermissionDecision.ALLOW_ALWAYS, "") },
                        modifier = Modifier.fillMaxWidth()) { Text("允许并记住此命令") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    UiTextButton(onClick = { onResolve(PermissionDecision.DENY, feedback) }) { Text("拒绝") }
                    if (isPlan) UiTextButton(onClick = { editingFeedback = !editingFeedback }) {
                        Text(if (editingFeedback) "返回审批" else "提出修改意见")
                    }
                }
            }
        }
    }
}
