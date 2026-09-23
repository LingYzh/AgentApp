package com.example.myapplication.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.example.myapplication.ui.components.UiTextButton
import com.example.myapplication.ui.components.FormSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.backup.ConfigurationTransfer
import com.example.myapplication.data.backup.ImportMode
import com.example.myapplication.data.backup.PreparedImport
import com.example.myapplication.data.backup.TransferKind
import com.example.myapplication.data.backup.TransferResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 列表页需要的导入导出动作。Content 组件保留默认空回调，便于预览和单独复用。
 */
data class ConfigurationTransferActions(
    val busy: Boolean,
    val onImport: () -> Unit,
    val onExportAll: () -> Unit,
    val onExportItem: (String) -> Unit,
    val onExportSelected: (Set<String>) -> Unit
)

/**
 * Provider 与 Agent 共用的 SAF、后台 I/O、摘要确认和冲突处理宿主。
 * PreparedImport 只保存在普通 Compose 状态中，避免把不可保存的文件对象放入 saved state。
 */
@Composable
fun ConfigurationTransferHost(
    kind: TransferKind,
    transfer: ConfigurationTransfer,
    onImportSuccess: (TransferResult) -> Unit,
    content: @Composable (ConfigurationTransferActions) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var busy by remember { mutableStateOf(false) }
    var exportOptionsVisible by rememberSaveable { mutableStateOf(false) }
    var includeApiKeys by rememberSaveable { mutableStateOf(false) }
    var includeConversations by rememberSaveable { mutableStateOf(false) }
    var exportAll by rememberSaveable { mutableStateOf(true) }
    var exportItemIds by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var exportPickerActive by rememberSaveable { mutableStateOf(false) }
    var importPickerActive by rememberSaveable { mutableStateOf(false) }
    var preparedImport by remember { mutableStateOf<PreparedImport?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.UPDATE) }

    val transferBusy = busy || exportOptionsVisible || exportPickerActive ||
        importPickerActive || preparedImport != null

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(exportMimeType(kind))
    ) { uri ->
        exportPickerActive = false
        if (uri == null) {
            // SAF 取消只解除忙碌状态，不产生失败提示，也不触碰应用数据。
            busy = false
        } else {
            // Activity 重建可能丢失普通状态，结果回调重新建立 I/O 忙碌锁。
            busy = true
            val selectedIds = if (exportAll) null else exportItemIds.toSet()
            val keysIncluded = includeApiKeys
            val conversationsIncluded = includeConversations
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: throw IOException("无法打开导出文件")
                        output.use { stream ->
                            when (kind) {
                                TransferKind.PROVIDERS -> transfer.exportProviders(
                                    output = stream,
                                    providerIds = selectedIds,
                                    includeApiKeys = keysIncluded
                                )

                                TransferKind.AGENTS -> transfer.exportAgents(
                                    output = stream,
                                    agentIds = selectedIds,
                                    includeConversations = conversationsIncluded
                                )
                                TransferKind.CONVERSATIONS, TransferKind.MEMORIES -> transfer.exportRecords(
                                    stream, kind, checkNotNull(selectedIds)
                                )
                            }
                        }
                    }
                    showMessage("${transferLabel(kind)}已导出")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    showMessage("导出失败：${error.message ?: error.javaClass.simpleName}")
                } finally {
                    busy = false
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        importPickerActive = false
        if (uri == null) {
            // 文件管理器取消不会创建 PreparedImport，也不会执行任何写入。
            busy = false
        } else {
            // Activity 重建可能丢失普通状态，结果回调重新建立 I/O 忙碌锁。
            busy = true
            scope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("无法读取导入文件")
                        input.use { stream -> transfer.prepareImport(stream, kind) }
                    }
                    importMode = ImportMode.UPDATE
                    preparedImport = prepared
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    showMessage("导入失败：${error.message ?: error.javaClass.simpleName}")
                } finally {
                    busy = false
                }
            }
        }
    }

    fun startImport() {
        if (transferBusy) return
        busy = true
        importPickerActive = true
        openDocumentLauncher.launch(importMimeTypes(kind))
    }

    fun startExport(all: Boolean, itemIds: Set<String> = emptySet()) {
        if (transferBusy) return
        // 每次新建导出操作都默认排除敏感内容，重建期间仍由 rememberSaveable 保留勾选值。
        includeApiKeys = false
        includeConversations = false
        exportAll = all
        exportItemIds = ArrayList(itemIds)
        exportOptionsVisible = true
    }

    androidx.compose.runtime.CompositionLocalProvider(com.example.myapplication.ui.components.LocalTransferFeedback provides snackbarHostState) {
    Box(Modifier.fillMaxSize()) {
        content(
            ConfigurationTransferActions(
                busy = transferBusy,
                onImport = ::startImport,
                onExportAll = { startExport(all = true) },
                onExportItem = { id -> startExport(all = false, itemIds = setOf(id)) },
                onExportSelected = { ids -> if (ids.isNotEmpty()) startExport(all = false, itemIds = ids) }
            )
        )


        if (busy && !exportPickerActive && !importPickerActive) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    }

    if (exportOptionsVisible) {
        ExportOptionsDialog(
            kind = kind,
            selectedCount = if (exportAll) null else exportItemIds.size,
            includeApiKeys = includeApiKeys,
            includeConversations = includeConversations,
            onIncludeApiKeysChange = { includeApiKeys = it },
            onIncludeConversationsChange = { includeConversations = it },
            onDismiss = { exportOptionsVisible = false },
            onConfirm = {
                exportOptionsVisible = false
                busy = true
                exportPickerActive = true
                createDocumentLauncher.launch(exportFileName(kind))
            }
        )
    }

    preparedImport?.let { prepared ->
        ImportSummaryDialog(
            prepared = prepared,
            mode = importMode,
            onModeChange = { importMode = it },
            onDismiss = { preparedImport = null },
            onConfirm = {
                preparedImport = null
                busy = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            transfer.importPrepared(prepared, importMode)
                        }
                        onImportSuccess(result)
                        showMessage("${transferLabel(kind)}已导入")
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        showMessage("导入失败：${error.message ?: error.javaClass.simpleName}")
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExportOptionsDialog(
    kind: TransferKind,
    selectedCount: Int?,
    includeApiKeys: Boolean,
    includeConversations: Boolean,
    onIncludeApiKeysChange: (Boolean) -> Unit,
    onIncludeConversationsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("导出${transferLabel(kind)}", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                FormSection("导出内容") {
                    selectedCount?.let { Text("将导出所选 $it 项。") }
                    if (kind == TransferKind.PROVIDERS) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onIncludeApiKeysChange(!includeApiKeys) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includeApiKeys,
                                onCheckedChange = onIncludeApiKeysChange
                            )
                            Text("包含 API Key 和附加请求头")
                        }
                        Text(
                            "未勾选时不导出 API Key 字段和附加请求头。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (kind == TransferKind.AGENTS) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onIncludeConversationsChange(!includeConversations) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includeConversations,
                                onCheckedChange = onIncludeConversationsChange
                            )
                            Text("包含所属会话")
                        }
                        Text(
                            "包含头像；会话仅含记录，不包含工作区文件、技能或 Provider 配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(if (kind == TransferKind.CONVERSATIONS)
                            "导出所选会话的完整消息和工具记录；不包含 Agent、Provider 配置或工作区文件。"
                            else "导出所选记忆的标题、正文和时间信息。")
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                UiTextButton(onClick = onDismiss) { Text("取消") }
                UiTextButton(onClick = onConfirm) { Text("选择保存位置") }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ImportSummaryDialog(
    prepared: PreparedImport,
    mode: ImportMode,
    onModeChange: (ImportMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val preview = prepared.preview
    val hasConflicts = preview.conflicts.isNotEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("确认导入${transferLabel(preview.kind)}", style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (preview.kind == TransferKind.PROVIDERS) {
                        "将导入 ${preview.itemCount} 个模型配置。"
                    } else if (preview.kind == TransferKind.AGENTS) {
                        "将导入 ${preview.itemCount} 个 Agent，${preview.conversationCount} 个所属会话。"
                    } else "将导入 ${preview.itemCount} 条${transferLabel(preview.kind)}。"
                )
                if (preview.kind == TransferKind.PROVIDERS) {
                    Text(
                        if (preview.includesApiKeys) {
                            "文件包含 API Key 和附加请求头。"
                        } else {
                            "文件不包含 API Key 和附加请求头；更新已有配置时会保留本机 Key 和附加请求头。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (preview.missingProviderCount > 0) {
                    Text(
                        "有 ${preview.missingProviderCount} 个 Provider 引用未包含配置，可能需要单独导入 Provider 或重新绑定。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (preview.missingAgentCount > 0) {
                    Text("有 ${preview.missingAgentCount} 个 Agent 尚未导入，引用会保留；请导入相应 Agent 后再继续这些会话。",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (hasConflicts) {
                    FormSection(
                        title = "冲突处理",
                        description = "发现 ${preview.conflicts.size} 个冲突，请选择处理方式。"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            preview.conflicts.forEach { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ImportModeOption(
                            selected = mode == ImportMode.UPDATE,
                            label = "更新已有",
                            onClick = { onModeChange(ImportMode.UPDATE) }
                        )
                        ImportModeOption(
                            selected = mode == ImportMode.COPY,
                            label = "作为副本导入",
                            onClick = { onModeChange(ImportMode.COPY) }
                        )
                        if (preview.kind == TransferKind.PROVIDERS && mode == ImportMode.COPY) {
                            Text(
                                "副本不会替换现有 Agent 使用的模型配置；需要时请在 Agent 中重新选择。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                UiTextButton(onClick = onDismiss) { Text("取消") }
                UiTextButton(onClick = onConfirm) { Text("导入") }
            }
        }
    }
}

@Composable
private fun ImportModeOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

private fun exportMimeType(kind: TransferKind): String = when (kind) {
    TransferKind.PROVIDERS, TransferKind.CONVERSATIONS, TransferKind.MEMORIES -> "application/json"
    TransferKind.AGENTS -> "application/zip"
}

private fun importMimeTypes(kind: TransferKind): Array<String> = when (kind) {
    TransferKind.PROVIDERS, TransferKind.CONVERSATIONS, TransferKind.MEMORIES -> arrayOf("application/json", "application/octet-stream")
    TransferKind.AGENTS -> arrayOf("application/zip", "application/octet-stream")
}

private fun exportFileName(kind: TransferKind): String {
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    return when (kind) {
        TransferKind.PROVIDERS -> "actant-providers-$timestamp.json"
        TransferKind.AGENTS -> "actant-agents-$timestamp.zip"
        TransferKind.CONVERSATIONS -> "actant-conversations-$timestamp.json"
        TransferKind.MEMORIES -> "actant-memories-$timestamp.json"
    }
}

private fun transferLabel(kind: TransferKind): String = when (kind) {
    TransferKind.PROVIDERS -> "模型供应商设置"
    TransferKind.AGENTS -> "Agent"
    TransferKind.CONVERSATIONS -> "会话"
    TransferKind.MEMORIES -> "记忆"
}
