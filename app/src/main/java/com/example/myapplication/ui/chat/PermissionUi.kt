package com.example.myapplication.ui.chat

import com.example.myapplication.ui.components.PrototypeTextField

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.agent.PermissionRequest
import com.example.myapplication.agent.PermissionRequestKind
import com.example.myapplication.agent.PermissionDecision
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.ui.components.MarkdownContent
import java.io.File
import com.example.myapplication.ui.components.inertWhen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionPermissionsDialog(
    current: PermissionMode,
    directories: List<String>,
    workingDirectory: String?,
    defaultWorkingDirectory: String,
    onDismiss: () -> Unit,
    onSave: suspend (PermissionMode, List<String>, String?) -> String?
) {
    var selected by rememberSaveable(current) { mutableStateOf(current) }
    var scopePaths by rememberSaveable(directories) { mutableStateOf(directories.distinct()) }
    var selectedWorkingDirectory by rememberSaveable(workingDirectory) { mutableStateOf(workingDirectory) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickerKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerStart by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val saveScope = rememberCoroutineScope()
    val shortcuts = remember(defaultWorkingDirectory) { commonDirectoryShortcuts(defaultWorkingDirectory) }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !saving })) {
        if (pickerKind != null) {
            DirectoryPicker(
                title = if (pickerKind == "working") "选择工作目录" else "添加允许目录",
                initialDirectory = pickerStart ?: selectedWorkingDirectory ?: defaultWorkingDirectory,
                shortcuts = shortcuts,
                onCancel = {
                    pickerKind = null
                    pickerStart = null
                },
                onSelect = { path ->
                    if (pickerKind == "working") {
                        selectedWorkingDirectory = path
                    } else if (path !in scopePaths) {
                        scopePaths = scopePaths + path
                    }
                    error = null
                    pickerKind = null
                    pickerStart = null
                }
            )
        } else {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    PanelHeading("权限与目录", { if (!saving) onDismiss() })
                    error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall) }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().inertWhen(saving),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("控制这段会话可以执行的操作。工具可见，不等于执行已获授权。", Modifier.padding(top = 14.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(listOf(PermissionMode.ACCEPT_EDIT, PermissionMode.PLAN, PermissionMode.READONLY, PermissionMode.AUTO), key = { it.name }) { mode ->
                        PermissionModeRow(mode, selected == mode) { selected = mode }
                        HorizontalDivider()
                    }
                    item {
                        WorkingDirectorySection(selectedWorkingDirectory, defaultWorkingDirectory,
                            onUseDefault = { selectedWorkingDirectory = null; error = null },
                            onChoose = {
                                pickerStart = selectedWorkingDirectory ?: defaultWorkingDirectory
                                pickerKind = "working"
                                error = null
                            })
                    }
                    item { HorizontalDivider(Modifier.padding(top = 4.dp)) }
                    item { Text("额外允许访问的目录（可选）", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall) }
                    if (scopePaths.isEmpty()) {
                        item {
                            Text("未添加额外目录：文件工具仅访问工作目录。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(scopePaths, key = { it }) { path ->
                            AllowedDirectoryRow(path) { scopePaths = scopePaths.filterNot { it == path } }
                        }
                    }
                    item {
                        OutlinedButton(onClick = {
                            pickerStart = scopePaths.lastOrNull() ?: selectedWorkingDirectory ?: defaultWorkingDirectory
                            pickerKind = "scope"
                            error = null
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("添加目录")
                        }
                    }
                    item {
                        Text("文件工具可访问工作目录和上方额外目录，无需重复添加工作目录。Shell 从工作目录启动，按权限模式审批；这些目录范围不会限制 Shell。记忆、Skill 和计划专用工具保留各自的应用内部目录。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Text(com.example.myapplication.diagnostics.RuntimeDiagnostics.storageAccessSummary(),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
                    Button(onClick = {
                        if (saving) return@Button
                        saving = true
                        error = null
                        saveScope.launch {
                            try {
                                val paths = withContext(Dispatchers.IO) {
                                    canonicalDirectory(selectedWorkingDirectory ?: defaultWorkingDirectory)
                                    scopePaths.map(::canonicalPath).distinct() to selectedWorkingDirectory?.let(::canonicalPath)
                                }
                                error = onSave(selected, paths.first, paths.second)
                                if (error == null) onDismiss()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "目录设置保存失败，请重试"
                            } finally { saving = false }
                        }
                    }, enabled = !saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (saving) "正在应用…" else "应用到本会话")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionModeRow(mode: PermissionMode, selected: Boolean, onSelect: () -> Unit) {
    Surface(onClick = onSelect, color = Color.Transparent, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
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
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun WorkingDirectorySection(
    selectedWorkingDirectory: String?,
    defaultWorkingDirectory: String,
    onUseDefault: () -> Unit,
    onChoose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("工作目录", style = MaterialTheme.typography.titleSmall)
        Text("相对路径和命令默认从这里开始；文件工具始终可访问此目录，无需在下方重复添加。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(onClick = onUseDefault, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, if (selectedWorkingDirectory == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            color = if (selectedWorkingDirectory == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("使用 App workspace（默认）", style = MaterialTheme.typography.titleSmall)
                    Text(defaultWorkingDirectory, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = selectedWorkingDirectory == null, onClick = onUseDefault)
            }
        }
        if (selectedWorkingDirectory != null) {
            Surface(onClick = onChoose, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("自定义工作目录", style = MaterialTheme.typography.titleSmall)
                        Text(selectedWorkingDirectory, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.Check, "已选择", tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择自定义工作目录")
            }
        }
    }
}

@Composable
private fun AllowedDirectoryRow(path: String, onRemove: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(path, Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp), maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "移除目录", Modifier.size(20.dp)) }
        }
    }
}

private fun commonDirectoryShortcuts(defaultWorkingDirectory: String): List<DirectoryShortcut> {
    // Canonicalization and access checks run when the picker loads this shortcut on IO.
    val shared = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
    val candidates = buildList {
        add(DirectoryShortcut("App workspace", defaultWorkingDirectory))
        shared?.let { root ->
            add(DirectoryShortcut("共享存储", root))
            add(DirectoryShortcut("Downloads", File(root, Environment.DIRECTORY_DOWNLOADS).path))
            add(DirectoryShortcut("Documents", File(root, Environment.DIRECTORY_DOCUMENTS).path))
        }
    }
    return candidates.distinctBy { it.path }
}

private fun canonicalPath(path: String): String = canonicalDirectory(path).canonicalPath

private fun canonicalDirectory(path: String): File {
    require(path.isNotBlank()) { "路径为空" }
    val raw = File(path)
    require(raw.isAbsolute) { "必须使用绝对路径" }
    val canonical = raw.canonicalFile
    require(canonical.exists()) { "目录不存在：${canonical.path}" }
    require(canonical.isDirectory) { "不是目录：${canonical.path}" }
    require(canonical.canRead()) { "没有读取权限：${canonical.path}" }
    return canonical
}

@Composable
internal fun PlanDocumentDialog(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("会话计划", style = MaterialTheme.typography.titleLarge)
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
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (isPlan) "审阅计划" else if (isDelete) "确认删除文件" else "授权执行命令", style = MaterialTheme.typography.titleLarge)
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
                    PrototypeTextField(feedback, { feedback = it }, label = { Text("修改意见") }, maxLines = 4,
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
