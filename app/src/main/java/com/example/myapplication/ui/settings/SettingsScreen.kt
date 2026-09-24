package com.example.myapplication.ui.settings

import com.example.myapplication.ui.components.AppModalBottomSheet

import com.example.myapplication.ui.components.inertWhen

import com.example.myapplication.ui.components.UiScaffold
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.myapplication.ui.components.UiTextButton
import com.example.myapplication.ui.components.PrototypeTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.ui.theme.AgentTheme
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.AgentApp
import com.example.myapplication.agent.CommandPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

class SettingsViewModel(val app: AgentApp) : ViewModel() {
    private val _maxLoops = MutableStateFlow(app.store.loadConfig().maxAgentLoops)
    val maxLoops = _maxLoops.asStateFlow()

    private val _subagentProviderId = MutableStateFlow(app.store.loadConfig().subagentProviderId)
    val subagentProviderId = _subagentProviderId.asStateFlow()
    private val _subagentModel = MutableStateFlow(app.store.loadConfig().subagentModel)
    val subagentModel = _subagentModel.asStateFlow()

    private val _autoApprovedCommands = MutableStateFlow(
        app.store.loadConfig().autoApprovedCommands
    )
    val autoApprovedCommands = _autoApprovedCommands.asStateFlow()

    private val autoApprovedCommandsWriteMutex = Mutex()

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

    /** Adds one complete command after the settings UI has handled any required confirmation. */
    fun addAutoApprovedCommand(command: String): Boolean {
        val normalized = command.trim()
        if (normalized.isEmpty()) return false
        if (normalized in _autoApprovedCommands.value) return false

        _autoApprovedCommands.value = _autoApprovedCommands.value + normalized
        persistAutoApprovedCommands { commands ->
            if (normalized in commands) commands else commands + normalized
        }
        return true
    }

    fun removeAutoApprovedCommand(command: String) {
        _autoApprovedCommands.value = _autoApprovedCommands.value.filterNot { it == command }
        persistAutoApprovedCommands { commands -> commands.filterNot { it == command } }
    }

    fun refreshAutoApprovedCommands() {
        viewModelScope.launch(Dispatchers.IO) {
            autoApprovedCommandsWriteMutex.withLock {
                _autoApprovedCommands.value = app.store.loadConfig().autoApprovedCommands.distinct()
            }
        }
    }

    private fun persistAutoApprovedCommands(update: (List<String>) -> List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            autoApprovedCommandsWriteMutex.withLock {
                val current = app.store.loadConfig()
                val updated = update(current.autoApprovedCommands).distinct()
                app.store.saveConfig(current.copy(autoApprovedCommands = updated))
                _autoApprovedCommands.value = updated
            }
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
            val result = app.backupManager.importFrom(uri)
            "已导入 ${result.fileCount} 个文件（原数据含头像已自动备份到 backups/）" +
                if (result.missingAvatars > 0) "\n${result.missingAvatars} 个 Agent 的图片未包含在备份中，已使用默认头像。" else ""
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

private enum class SettingsPage {
    MAIN,
    BACKUP,
    COMMANDS
}

private val SettingsPageSaver = Saver<SettingsPage, String>(
    save = { it.name },
    restore = { value -> runCatching { SettingsPage.valueOf(value) }.getOrDefault(SettingsPage.MAIN) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    openDrawer: () -> Unit,
    onOpenSection: (String) -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as AgentApp
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app) }
    })
    val maxLoops by vm.maxLoops.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val subagentProviderId by vm.subagentProviderId.collectAsStateWithLifecycle()
    val subagentModel by vm.subagentModel.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val autoApprovedCommands by vm.autoApprovedCommands.collectAsStateWithLifecycle()

    var storageAccessGranted by remember(context) {
        mutableStateOf(hasSharedStorageAccess(context))
    }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storageAccessGranted = hasSharedStorageAccess(context)
                vm.refreshAutoApprovedCommands()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        storageAccessGranted = hasSharedStorageAccess(context)
    }

    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { vm.export(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportConfirm = it } }

    val providers = remember { app.store.loadConfig().providers }

    SettingsContent(
        themeMode = themeMode,
        onThemeModeChange = { app.setThemeMode(it) },
        maxLoops = maxLoops,
        onMaxLoopsChange = { vm.setMaxLoops(it) },
        subagentProviderId = subagentProviderId,
        subagentModel = subagentModel,
        providers = providers,
        onSubagentModelChange = { providerId, model -> vm.setSubagentModel(providerId, model) },
        busy = busy,
        status = status,
        storageAccessGranted = storageAccessGranted,
        onRequestStorageAccess = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                openSharedStorageSettings(context)
            } else {
                legacyStoragePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        },
        autoApprovedCommands = autoApprovedCommands,
        onRemoveAutoApprovedCommand = vm::removeAutoApprovedCommand,
        isLowRiskCommand = CommandPolicy::isLowRisk,
        onAddAutoApprovedCommand = vm::addAutoApprovedCommand,
        onOpenDrawer = openDrawer,
        onOpenSection = onOpenSection,
        onExport = { exportLauncher.launch(app.backupManager.suggestedFileName()) },
        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
    )

    showImportConfirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text("确认导入？") },
            text = { Text("导入会覆盖当前全部数据（模型配置、Agents、对话、记忆、Skills、工作区文件）。当前数据会先自动备份到应用内 backups/ 目录。") },
            confirmButton = {
                UiTextButton(onClick = {
                    vm.import(uri)
                    showImportConfirm = null
                }) { Text("导入") }
            },
            dismissButton = {
                UiTextButton(onClick = { showImportConfirm = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 设置中心纯 UI 组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    maxLoops: Int,
    onMaxLoopsChange: (Int) -> Unit,
    subagentProviderId: String?,
    subagentModel: String?,
    providers: List<ProviderConfig>,
    onSubagentModelChange: (providerId: String?, model: String?) -> Unit,
    busy: Boolean,
    status: String?,
    onOpenDrawer: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    storageAccessGranted: Boolean = false,
    onRequestStorageAccess: () -> Unit = {},
    autoApprovedCommands: List<String> = emptyList(),
    onRemoveAutoApprovedCommand: (String) -> Unit = {},
    isLowRiskCommand: (String) -> Boolean = { false },
    onAddAutoApprovedCommand: (String) -> Boolean = { false },
    onOpenSection: (String) -> Unit = {}
) {
    var loopsSliderValue by remember(maxLoops) {
        androidx.compose.runtime.mutableFloatStateOf(maxLoops.coerceIn(1, 50).toFloat())
    }
    var showSubagentPicker by rememberSaveable { mutableStateOf(false) }
    var showAddCommand by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable(stateSaver = SettingsPageSaver) {
        mutableStateOf(SettingsPage.MAIN)
    }

    BackHandler(enabled = page != SettingsPage.MAIN) {
        page = SettingsPage.MAIN
        showAddCommand = false
    }

    val subProvider = providers.firstOrNull { it.id == subagentProviderId }
    val selectedSubagentLabel = subProvider?.let { provider ->
        subagentModel ?: provider.name.ifBlank { provider.type.label }
    } ?: "继承主代理"

    val pageState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    UiScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            SettingsPage.MAIN -> "设置"
                            SettingsPage.BACKUP -> "备份与迁移"
                            SettingsPage.COMMANDS -> "自动放行命令"
                        }
                    )
                },
                navigationIcon = {
                    if (page == SettingsPage.MAIN) {
                        IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                    } else {
                        IconButton(onClick = {
                            page = SettingsPage.MAIN
                            showAddCommand = false
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置")
                        }
                    }
                },
                actions = {
                    if (page == SettingsPage.COMMANDS) {
                        IconButton(onClick = { showAddCommand = true }) {
                            Icon(Icons.Filled.Add, "新增命令")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        androidx.compose.animation.AnimatedContent(page, label = "settings page", modifier = Modifier.fillMaxSize()) { targetPage ->
            pageState.SaveableStateProvider(targetPage.name) {
                Box(Modifier.fillMaxSize().inertWhen(targetPage != page)) {
                    when (targetPage) {
                        SettingsPage.MAIN -> SettingsMainPage(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            maxLoops = maxLoops,
                            loopsSliderValue = loopsSliderValue,
                            onLoopsSliderChange = { loopsSliderValue = it },
                            onLoopsSliderFinished = { onMaxLoopsChange(loopsSliderValue.roundToInt()) },
                            selectedSubagentLabel = selectedSubagentLabel,
                            onOpenSubagentPicker = { showSubagentPicker = true },
                            onOpenCommands = { page = SettingsPage.COMMANDS },
                            autoApprovedCommandCount = autoApprovedCommands.size,
                            storageAccessGranted = storageAccessGranted,
                            onRequestStorageAccess = onRequestStorageAccess,
                            onOpenBackup = { page = SettingsPage.BACKUP },
                            onOpenSection = onOpenSection
                        )
                        SettingsPage.BACKUP -> BackupPage(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            busy = busy,
                            status = status,
                            onExport = onExport,
                            onImport = onImport,
                            onOpenSection = onOpenSection
                        )
                        SettingsPage.COMMANDS -> AutoApprovedCommandsPage(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            commands = autoApprovedCommands,
                            isLowRiskCommand = isLowRiskCommand,
                            onAddCommand = onAddAutoApprovedCommand,
                            onRemoveCommand = onRemoveAutoApprovedCommand,
                            showAddCommand = showAddCommand,
                            onShowAddCommandChange = { showAddCommand = it }
                        )
                    }
                }
            }
        }
    }

    if (showSubagentPicker) {
        AppModalBottomSheet(onDismissRequest = { showSubagentPicker = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                    start = 22.dp,
                    end = 22.dp,
                    bottom = 32.dp
                )
            ) {
                Text("子代理模型", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "指定后覆盖工具参数和主代理继承，具有最高优先级。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )
                SubagentModelOption(
                    title = "继承主代理",
                    subtitle = "不设置强制覆盖",
                    selected = subagentProviderId == null,
                    onClick = {
                        onSubagentModelChange(null, null)
                        showSubagentPicker = false
                    }
                )
                providers.forEach { provider ->
                    val models = provider.models.filter { it.isNotBlank() }.distinct()
                    Text(
                        provider.name.ifBlank { provider.type.label },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
                    )
                    if (models.isEmpty()) Text("尚未添加模型，请先到供应商设置中添加。",
                        style = MaterialTheme.typography.bodySmall)
                    models.forEach { model ->
                        SubagentModelOption(
                            title = model,
                            subtitle = provider.name.ifBlank { provider.type.label },
                            selected = subagentProviderId == provider.id && subagentModel == model,
                            onClick = {
                                onSubagentModelChange(provider.id, model)
                                showSubagentPicker = false
                            }
                        )
                    }
                }
                if (providers.isEmpty()) {
                    Text(
                        "还没有模型配置，请先在模型供应商设置页添加 Provider。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMainPage(
    modifier: Modifier,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    maxLoops: Int,
    loopsSliderValue: Float,
    onLoopsSliderChange: (Float) -> Unit,
    onLoopsSliderFinished: () -> Unit,
    selectedSubagentLabel: String,
    onOpenSubagentPicker: () -> Unit,
    onOpenCommands: () -> Unit,
    autoApprovedCommandCount: Int,
    storageAccessGranted: Boolean,
    onRequestStorageAccess: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSection: (String) -> Unit
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        SettingsSectionTitle("外观")
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                    Surface(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = if (themeMode == mode) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (themeMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ) {
                        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        SettingsSectionTitle("执行")
        SettingsOptionRow(
            icon = Icons.Filled.Refresh,
            title = "最大工具循环",
            subtitle = "达到上限后结束本轮执行",
            trailing = { Text(loopsSliderValue.roundToInt().toString(), style = MaterialTheme.typography.titleLarge) },
            showDivider = true
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MaxLoopsSlider(
                value = loopsSliderValue,
                onValueChange = onLoopsSliderChange,
                onValueChangeFinished = onLoopsSliderFinished,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsOptionRow(
            icon = Icons.Filled.AccountTree,
            title = "子代理模型",
            subtitle = selectedSubagentLabel,
            onClick = onOpenSubagentPicker
        )
        Text(
            "指定后覆盖工具参数和主代理继承，具有最高优先级。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsOptionRow(
            icon = Icons.Filled.Terminal,
            title = "自动放行命令",
            subtitle = "$autoApprovedCommandCount 条精确命令",
            onClick = onOpenCommands
        )

        SettingsSectionTitle("搜索与设备")
        SettingsOptionRow(
            icon = Icons.Filled.Search,
            title = "网络搜索服务",
            subtitle = "选择搜索服务并配置连接",
            onClick = { onOpenSection("webSearch") },
            showDivider = true
        )
        SettingsOptionRow(
            icon = Icons.Filled.PhoneAndroid,
            title = "设备控制",
            subtitle = "无障碍、Shizuku 与运行状态",
            onClick = { onOpenSection("deviceControl") }
        )
        SettingsSectionTitle("存储")
        StorageAccessCard(
            accessGranted = storageAccessGranted,
            onRequestAccess = onRequestStorageAccess
        )

        SettingsSectionTitle("数据")
        SettingsOptionRow(
            icon = Icons.Filled.Download,
            title = "备份与迁移",
            subtitle = "独立迁移配置，或恢复完整备份",
            onClick = onOpenBackup
        )

        SettingsSectionTitle("诊断日志")
        Text(
            "已启用本地运行日志，最多保留 3 个约 1 MB 的文件。记录工具状态、权限、失败路径及网络状态，不记录消息正文、文件内容、命令内容或 API 密钥。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "调试版可通过 ADB run-as 导出 files/logs/。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            "AgentApp",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    },
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            trailing()
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaxLoopsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inactiveTrack = MaterialTheme.colorScheme.outlineVariant
    val activeTrack = MaterialTheme.colorScheme.primary
    Box(modifier.height(48.dp)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            val height = 4.dp.toPx()
            val top = (size.height - height) / 2f
            val radius = height / 2f
            drawRoundRect(
                color = inactiveTrack,
                topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )
            val fraction = ((value - 1f) / 49f).coerceIn(0f, 1f)
            if (fraction > 0f) {
                drawRoundRect(
                    color = activeTrack,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 1f..50f,
            steps = 48,
            modifier = Modifier.fillMaxSize(),
            thumb = {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            },
            track = { Spacer(Modifier.fillMaxWidth().height(4.dp)) }
        )
    }
}

@Composable
private fun SubagentModelOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
private fun BackupPage(
    modifier: Modifier,
    busy: Boolean,
    status: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenSection: (String) -> Unit
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 32.dp)
    ) {
        Text(
            "把配置带走",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
            )
        )
        Text(
            "单独迁移一部分，或备份整个工作环境。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        SettingsSectionTitle("独立迁移")
        BackupMigrationRow(
            icon = Icons.Filled.AccountTree,
            title = "模型供应商设置",
            description = "可选包含 API Key 与附加请求头",
            onClick = { onOpenSection("providers") }
        )
        BackupMigrationRow(
            icon = Icons.Filled.Folder,
            title = "Agent 配置",
            description = "头像与可选所属会话；不含 Provider",
            onClick = { onOpenSection("agents") }
        )
        BackupMigrationRow(
            icon = Icons.Filled.Menu,
            title = "会话",
            description = "消息与工具记录；不含工作区文件",
            onClick = { onOpenSection("conversations") }
        )
        BackupMigrationRow(
            icon = Icons.Filled.Refresh,
            title = "长期记忆",
            description = "独立记忆数据",
            onClick = { onOpenSection("memory") }
        )
        BackupMigrationRow(
            icon = Icons.Filled.Terminal,
            title = "Skills ZIP",
            description = "指令与资源；支持单项、全部或所选集合",
            onClick = { onOpenSection("skills") }
        )
        Text(
            "点击条目可前往对应列表页，使用现有管理菜单完成导入/导出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )

        SettingsSectionTitle("完整备份")
        SettingsNotice(
            "包括配置、Agents、会话、记忆、Skills 与工作区文件。备份可能含凭据，请自行妥善保管。",
            warning = true
        )
        Button(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Icon(Icons.Filled.FileUpload, null)
            Spacer(Modifier.size(8.dp))
            Text("导出完整备份")
        }
        OutlinedButton(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Icon(Icons.Filled.FileDownload, null)
            Spacer(Modifier.size(8.dp))
            Text("恢复完整备份")
        }
        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text("处理中…", Modifier.padding(start = 8.dp))
            }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("❌")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Text(
            "完整恢复会覆盖现有数据；应用会先保存旧数据到内部 backups/。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun BackupMigrationRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClick != null) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SettingsNotice(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = if (warning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warning) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun AutoApprovedCommandsPage(
    modifier: Modifier,
    commands: List<String>,
    isLowRiskCommand: (String) -> Boolean,
    onAddCommand: (String) -> Boolean,
    onRemoveCommand: (String) -> Unit,
    showAddCommand: Boolean,
    onShowAddCommandChange: (Boolean) -> Unit
) {
    var commandText by rememberSaveable { mutableStateOf("") }
    var commandError by remember { mutableStateOf<String?>(null) }
    var pendingConfirmationCommand by rememberSaveable { mutableStateOf<String?>(null) }

    val submitCommand = {
        val command = commandText.trim()
        when {
            command.isEmpty() -> commandError = "请输入完整命令"
            command in commands -> commandError = "该命令已经在列表中"
            isLowRiskCommand(command) -> {
                if (onAddCommand(command)) {
                    commandText = ""
                    commandError = null
                    onShowAddCommandChange(false)
                } else {
                    commandError = "该命令已经在列表中"
                }
            }
            else -> {
                pendingConfirmationCommand = command
                onShowAddCommandChange(false)
            }
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 32.dp)
    ) {
        SettingsNotice(
            "这里只匹配完整命令。Readonly / Plan 仍会限制执行；Shell 从工作目录启动，继续按权限模式审批。",
            warning = true
        )
        SettingsSectionTitle("已允许的精确命令")
        if (commands.isEmpty()) {
            Text(
                "还没有自动放行的命令。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 18.dp)
            )
        } else {
            commands.forEach { command ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        command,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                    IconButton(onClick = { onRemoveCommand(command) }) {
                        Icon(Icons.Filled.Delete, "移除命令")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        SettingsNotice(
            "命令执行仍使用应用 UID。目录范围仅约束文件工具；Shell 从工作目录启动，并继续按权限模式审批。",
            modifier = Modifier.padding(top = 24.dp)
        )
    }

    if (showAddCommand) {
        AlertDialog(
            onDismissRequest = {
                onShowAddCommandChange(false)
                commandError = null
            },
            title = { Text("新增精确命令") },
            text = {
                PrototypeTextField(
                    value = commandText,
                    onValueChange = {
                        commandText = it
                        commandError = null
                    },
                    label = { Text("输入完整命令") },
                    singleLine = true,
                    isError = commandError != null,
                    supportingText = commandError?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                UiTextButton(onClick = submitCommand, enabled = commandText.isNotBlank()) {
                    Text("添加")
                }
            },
            dismissButton = {
                UiTextButton(onClick = {
                    onShowAddCommandChange(false)
                    commandError = null
                }) { Text("取消") }
            }
        )
    }

    pendingConfirmationCommand?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingConfirmationCommand = null },
            title = { Text("确认自动允许命令？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("这条命令未通过低风险检查。确认后只会自动允许下面这条完整命令：")
                    Text(command, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    Text(
                        "请确认你了解它的作用；相同前缀或其他命令不会因此获得自动允许。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                UiTextButton(onClick = {
                    if (onAddCommand(command)) {
                        commandText = ""
                        commandError = null
                    } else {
                        commandError = "该命令已经在列表中"
                    }
                    pendingConfirmationCommand = null
                }) { Text("确认添加") }
            },
            dismissButton = {
                UiTextButton(onClick = { pendingConfirmationCommand = null }) { Text("取消") }
            }
        )
    }
}

@Preview(showBackground = true, name = "Settings - Light")
@Composable
private fun SettingsPreviewLight() {
    val sampleProviders = listOf(
        ProviderConfig(id = "p1", name = "Claude", model = "claude-3-7-sonnet"),
        ProviderConfig(id = "p2", name = "DeepSeek", model = "deepseek-chat")
    )
    AgentTheme(themeMode = "light") {
        SettingsContent(
            themeMode = "light",
            onThemeModeChange = {},
            maxLoops = 15,
            onMaxLoopsChange = {},
            subagentProviderId = "p1",
            subagentModel = "claude-3-7-sonnet",
            providers = sampleProviders,
            onSubagentModelChange = { _, _ -> },
            busy = false,
            status = null,
            onOpenDrawer = {},
            onExport = {},
            onImport = {}
        )
    }
}

@Preview(showBackground = true, name = "Settings - Dark")
@Composable
private fun SettingsPreviewDark() {
    AgentTheme(themeMode = "dark") {
        SettingsContent(
            themeMode = "dark",
            onThemeModeChange = {},
            maxLoops = 20,
            onMaxLoopsChange = {},
            subagentProviderId = null,
            subagentModel = null,
            providers = emptyList(),
            onSubagentModelChange = { _, _ -> },
            busy = false,
            status = null,
            onOpenDrawer = {},
            onExport = {},
            onImport = {}
        )
    }
}

@Preview(showBackground = true, name = "Settings - Busy")
@Composable
private fun SettingsPreviewBusy() {
    AgentTheme(themeMode = "light") {
        SettingsContent(
            themeMode = "system",
            onThemeModeChange = {},
            maxLoops = 10,
            onMaxLoopsChange = {},
            subagentProviderId = null,
            subagentModel = null,
            providers = emptyList(),
            onSubagentModelChange = { _, _ -> },
            busy = true,
            status = "✅ 已导出 18 个备份文件",
            onOpenDrawer = {},
            onExport = {},
            onImport = {}
        )
    }
}
