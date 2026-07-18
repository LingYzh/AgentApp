package com.example.myapplication.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.AgentApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(val app: AgentApp) : ViewModel() {
    private val _maxLoops = MutableStateFlow(app.store.loadConfig().maxAgentLoops)
    val maxLoops = _maxLoops.asStateFlow()

    private val _subagentProviderId = MutableStateFlow(app.store.loadConfig().subagentProviderId)
    val subagentProviderId = _subagentProviderId.asStateFlow()
    private val _subagentModel = MutableStateFlow(app.store.loadConfig().subagentModel)
    val subagentModel = _subagentModel.asStateFlow()

    val themeMode = app.themeMode

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    fun setMaxLoops(value: Int) {
        val v = value.coerceIn(1, 50)
        _maxLoops.value = v
        viewModelScope.launch(Dispatchers.IO) {
            app.store.saveConfig(app.store.loadConfig().copy(maxAgentLoops = v))
        }
    }

    fun setSubagentModel(providerId: String?, model: String?) {
        _subagentProviderId.value = providerId
        _subagentModel.value = model
        viewModelScope.launch(Dispatchers.IO) {
            app.store.saveConfig(
                app.store.loadConfig().copy(subagentProviderId = providerId, subagentModel = model)
            )
        }
    }

    fun export(uri: android.net.Uri) {
        runIo("导出") {
            val count = app.backupManager.exportTo(uri)
            "✅ 已导出 $count 个文件"
        }
    }

    fun import(uri: android.net.Uri) {
        runIo("导入") {
            val count = app.backupManager.importFrom(uri)
            "✅ 已导入 $count 个文件（原数据已自动备份到 backups/）"
        }
    }

    private fun runIo(label: String, block: () -> String) {
        if (_busy.value) return
        _busy.value = true
        _status.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { block() }
                .getOrElse { "❌ ${label}失败: ${it.message}" }
            _status.value = result
            _busy.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app) }
    })
    val maxLoops by vm.maxLoops.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val subagentProviderId by vm.subagentProviderId.collectAsStateWithLifecycle()
    val subagentModel by vm.subagentModel.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    var loopsText by remember(maxLoops) { mutableStateOf(maxLoops.toString()) }
    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }
    var subProviderMenuExpanded by remember { mutableStateOf(false) }
    var subModelMenuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { vm.export(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportConfirm = it } }

    val providers = remember { app.store.loadConfig().providers }
    val subProvider = providers.firstOrNull { it.id == subagentProviderId }
    val subProviderModels = subProvider?.models?.ifEmpty {
        listOf(subProvider.model).filter { it.isNotBlank() }
    } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置 / 备份") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 外观
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("外观", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
                            .forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = { app.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index, count = 3
                                    )
                                ) { Text(label) }
                            }
                    }
                }
            }

            // Agent
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agent", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = loopsText,
                        onValueChange = {
                            loopsText = it.filter(Char::isDigit).take(3)
                            loopsText.toIntOrNull()?.let { v -> vm.setMaxLoops(v) }
                        },
                        label = { Text("最大工具循环次数（1-50）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 子代理模型
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("子代理模型", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "指定后所有子代理强制使用该模型（最高优先级）；不指定则由主代理选择或继承当前模型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExposedDropdownMenuBox(
                        expanded = subProviderMenuExpanded,
                        onExpandedChange = { subProviderMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = subProvider?.name?.ifBlank { subProvider.model } ?: "不指定",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("模型配置") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(subProviderMenuExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = subProviderMenuExpanded,
                            onDismissRequest = { subProviderMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("不指定") },
                                onClick = {
                                    vm.setSubagentModel(null, null)
                                    subProviderMenuExpanded = false
                                }
                            )
                            providers.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name.ifBlank { p.model }) },
                                    onClick = {
                                        val firstModel = p.models.firstOrNull()
                                            ?: p.model.ifBlank { null }
                                        vm.setSubagentModel(p.id, firstModel)
                                        subProviderMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (subProvider != null && subProviderModels.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = subModelMenuExpanded,
                            onExpandedChange = { subModelMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = subagentModel ?: subProvider.model,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("模型") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(subModelMenuExpanded)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = subModelMenuExpanded,
                                onDismissRequest = { subModelMenuExpanded = false }
                            ) {
                                subProviderModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            vm.setSubagentModel(subProvider.id, m)
                                            subModelMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 备份
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("数据备份与迁移", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "导出包含：模型配置（含 API Key）、Agents、对话、记忆、Skills、工作区文件。" +
                            "在新设备上导入同一个 zip 即可完成迁移。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { exportLauncher.launch(app.backupManager.suggestedFileName()) },
                            enabled = !busy
                        ) {
                            Icon(Icons.Filled.FileUpload, null)
                            Text(" 导出备份")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            enabled = !busy
                        ) {
                            Icon(Icons.Filled.FileDownload, null)
                            Text(" 导入备份")
                        }
                    }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text(" 处理中…", Modifier.padding(start = 8.dp))
                        }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    showImportConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text("确认导入？") },
            text = { Text("导入会覆盖当前全部数据（模型配置、Agents、对话、记忆、Skills、工作区文件）。当前数据会先自动备份到应用内 backups/ 目录。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.import(uri)
                    showImportConfirm = null
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = null }) { Text("取消") }
            }
        )
    }
}
