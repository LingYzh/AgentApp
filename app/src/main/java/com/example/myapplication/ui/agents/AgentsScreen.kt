package com.example.myapplication.ui.agents

import com.example.myapplication.ui.chat.showHomeChat

import com.example.myapplication.ui.components.UiScaffold
import com.example.myapplication.ui.components.FormSection
import com.example.myapplication.ui.components.PrototypeTextField
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.example.myapplication.agent.Tools
import com.example.myapplication.ui.components.rememberListSelection
import com.example.myapplication.ui.components.ListSelectionBar
import com.example.myapplication.ui.components.ListPageHeader
import com.example.myapplication.ui.components.ListOperationsMenu
import com.example.myapplication.ui.components.PrototypeListAction
import com.example.myapplication.ui.components.PrototypeListOverflowMenu
import com.example.myapplication.ui.components.PrototypeListTag
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.data.backup.TransferKind
import com.example.myapplication.safeNavigateDirect
import com.example.myapplication.safePopBackStack
import com.example.myapplication.ui.theme.AgentTheme
import com.example.myapplication.ui.theme.ExpressiveTokens
import com.example.myapplication.ui.transfer.ConfigurationTransferHost
import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AgentsViewModel(val app: AgentApp) : ViewModel() {
    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError = _deleteError.asStateFlow()
    fun clearDeleteError() { _deleteError.value = null }

    fun deleteSelected(ids: Set<String>) {
        if (_deleting.value || ids.isEmpty()) return
        _deleting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.store.saveAgents(app.store.loadAgents().filterNot { it.id in ids })
                _agents.value = app.store.loadAgents()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _deleteError.value = error.message ?: "删除失败"
            } finally {
                _deleting.value = false
            }
        }
    }

    private val _agents = MutableStateFlow<List<AgentProfile>>(emptyList())
    val agents = _agents.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providers = _providers.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _providers.value = app.store.loadConfig().providers
            _agents.value = app.store.loadAgents()
            _loading.value = false
        }
    }

    fun save(agent: AgentProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.saveAgents(app.store.loadAgents().filterNot { it.id == agent.id } + agent)
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.saveAgents(app.store.loadAgents().filterNot { it.id == id })
            refresh()
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            _agents.value = app.store.resetAgentsToDefault()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(navController: NavHostController, openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: AgentsViewModel = viewModel(factory = viewModelFactory {
        initializer { AgentsViewModel(app) }
    })
    // STARTED 已处于入场动画阶段；等到 RESUMED 才刷新会推迟首屏数据加载。
    LifecycleStartEffect(Unit) {
        vm.refresh()
        onStopOrDispose { }
    }
    val agents by vm.agents.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val deleteError by vm.deleteError.collectAsStateWithLifecycle()
    deleteError?.let { message ->
        AlertDialog(onDismissRequest = vm::clearDeleteError,
            title = { Text("删除失败") }, text = { Text(message) },
            confirmButton = { UiTextButton(onClick = vm::clearDeleteError) { Text("确定") } })
    }


    ConfigurationTransferHost(
        kind = TransferKind.AGENTS,
        transfer = app.configurationTransfer,
        onImportSuccess = { vm.refresh() }
    ) { actions ->
        AgentsContent(
            agents = agents,
            loading = loading,
            modelLabel = { agent ->
                val provider = agent.providerId?.let { id ->
                    providers.firstOrNull { it.id == id }
                }
                when {
                    agent.providerId != null && provider == null -> {
                        "模型配置待导入或重新选择" + agent.model?.let { " / $it" }.orEmpty()
                    }

                    agent.model != null -> {
                        "${provider?.name?.ifBlank { provider.type.label } ?: ""} / ${agent.model}"
                    }

                    else -> "跟随新会话选择"
                }
            },
            onOpenDrawer = openDrawer,
            onNewAgent = { navController.safeNavigateDirect(Routes.agentEdit("new")) },
            onSelectAgent = { id -> navController.safeNavigateDirect(Routes.agentEdit(id)) },
            onStartAgent = { id -> navController.showHomeChat(agentId = id) },
            onDeleteAgent = { id -> vm.delete(id) },
            onResetToDefaults = { vm.resetToDefaults() },
            onImport = actions.onImport,
            onExportAll = actions.onExportAll,
            onExportSelected = actions.onExportSelected,
            onDeleteSelected = vm::deleteSelected,
            busy = actions.busy || deleting
        )
    }
}

/**
 * Agent 列表展示组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsContent(
    agents: List<AgentProfile>,
    modelLabel: (AgentProfile) -> String,
    onOpenDrawer: () -> Unit,
    onNewAgent: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onDeleteAgent: (String) -> Unit,
    onResetToDefaults: () -> Unit,
    loading: Boolean = false,
    onImport: () -> Unit = {},
    onExportAll: () -> Unit = {},
    onExportSelected: (Set<String>) -> Unit = {},
    onDeleteSelected: (Set<String>) -> Unit = {},
    busy: Boolean = false,
    onStartAgent: (String) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredAgents = remember(agents, normalizedQuery, modelLabel) {
        agents.filter { agent ->
            normalizedQuery.isBlank() || listOf(agent.name, agent.description, modelLabel(agent))
                .any { it.contains(normalizedQuery, ignoreCase = true) }
        }
    }
    val selection = rememberListSelection(agents.map { it.id },
        agents.associate { it.id to it.name }, filteredAgents.map { it.id })

    UiScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                },
                actions = {
                    ListOperationsMenu(selection, busy, onImport, onExportAll)
                    if (!selection.active) {
                        IconButton(onClick = onResetToDefaults, enabled = !busy) {
                            Icon(Icons.Filled.AutoAwesome, "载入默认预设")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(selection.active) {
                ListSelectionBar(selection, busy, onDeleteSelected, onExport = onExportSelected)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = ExpressiveTokens.ScreenHorizontalPadding,
                top = 8.dp,
                end = ExpressiveTokens.ScreenHorizontalPadding,
                bottom = if (selection.active) 16.dp else 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "page-header") {
                ListPageHeader(
                    title = "Agents",
                    description = "管理智能体，选择一个开始新的工作。",
                    query = query,
                    onQueryChange = { query = it },
                    searchPlaceholder = "搜索 Agent",
                    actionLabel = if (selection.active || busy) null else "新建 Agent",
                    onAction = if (selection.active || busy) null else onNewAgent
                )
            }
            if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            } else if (filteredAgents.isEmpty()) {
                item(key = "empty-state") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp)
                    ) {
                        Text(
                            if (normalizedQuery.isBlank()) "暂无 Agent" else "没有匹配的 Agent",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            if (normalizedQuery.isBlank()) "可以新建 Agent，或载入官方预设。" else "换个关键词试试。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (normalizedQuery.isBlank()) {
                            OutlinedButton(onClick = onResetToDefaults, enabled = !busy) {
                                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("载入默认预设")
                            }
                        }
                    }
                }
            } else {
                items(filteredAgents, key = { it.id }) { agent ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        onClick = {
                            if (selection.active) selection.onToggle(agent.id) else onSelectAgent(agent.id)
                        },
                        enabled = !busy,
                        modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                            ) {
                                AgentAvatar(
                                    emoji = agent.emoji,
                                    avatarPath = agent.avatarPath,
                                    size = 42.dp
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        agent.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.W600,
                                            lineHeight = 23.sp
                                        ),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Box(Modifier.padding(top = 8.dp)) {
                                        PrototypeListTag(modelLabel(agent))
                                    }
                                }
                                if (selection.active) {
                                    Checkbox(
                                        checked = agent.id in selection.selectedIds,
                                        onCheckedChange = { selection.onToggle(agent.id) },
                                        enabled = !busy
                                    )
                                } else {
                                    PrototypeListOverflowMenu(
                                        contentDescription = "${agent.name} 操作",
                                        enabled = !busy,
                                        actions = listOf(
                                            PrototypeListAction(
                                                "编辑 Agent",
                                                Icons.Filled.Edit,
                                                onClick = { onSelectAgent(agent.id) }
                                            ),
                                            PrototypeListAction(
                                                "删除",
                                                Icons.Filled.Delete,
                                                destructive = true,
                                                onClick = { onDeleteAgent(agent.id) }
                                            )
                                        )
                                    )
                                }
                            }
                            if (agent.description.isNotBlank()) {
                                Text(
                                    agent.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 23.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp),
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Column(Modifier.fillMaxWidth().padding(top = 13.dp)) {
                                androidx.compose.material3.HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (agent.tools.isEmpty()) "全部工具" else "${agent.tools.size} 项工具",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    UiTextButton(onClick = { onStartAgent(agent.id) }, enabled = !busy) {
                                        Text("开始对话")
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditScreen(navController: NavHostController, agentId: String) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: AgentsViewModel = viewModel(factory = viewModelFactory {
        initializer { AgentsViewModel(app) }
    })
    val existing = remember(agentId) {
        if (agentId == "new") null
        else app.store.loadAgents().firstOrNull { it.id == agentId }
    }
    val providers = remember { app.store.loadConfig().providers }

    AgentEditContent(
        isNew = agentId == "new",
        initialAgent = existing,
        providers = providers,
        onBack = { navController.safePopBackStack() },
        onSave = { agent ->
            vm.save(agent)
            navController.safePopBackStack()
        }
    )
}

/**
 * Agent 新建/编辑展示组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentEditContent(
    isNew: Boolean,
    initialAgent: AgentProfile?,
    providers: List<ProviderConfig>,
    onBack: () -> Unit,
    onSave: (AgentProfile) -> Unit
) {
    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val app = if (isPreview) null else context.applicationContext as AgentApp
    val agentId = remember { initialAgent?.id ?: java.util.UUID.randomUUID().toString() }

    var name by remember { mutableStateOf(initialAgent?.name ?: "") }
    var emoji by remember { mutableStateOf(initialAgent?.emoji ?: "🤖") }
    var avatarPath by remember { mutableStateOf(initialAgent?.avatarPath) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var allowAllTools by remember { mutableStateOf(initialAgent?.tools.isNullOrEmpty()) }
    var selectedTools by remember {
        mutableStateOf(initialAgent?.tools?.takeIf { it.isNotEmpty() }?.toSet() ?: Tools.ALL_NAMES)
    }
    var description by remember { mutableStateOf(initialAgent?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(initialAgent?.systemPrompt ?: "") }
    var providerId by remember { mutableStateOf(initialAgent?.providerId) }
    var model by remember { mutableStateOf(initialAgent?.model) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val path = app?.store?.saveAgentAvatar(agentId, stream)
                    if (path != null) {
                        avatarPath = path
                    }
                }
            }
        }
    }

    val selectedProvider = providers.firstOrNull { it.id == providerId }
    val providerLabel = selectedProvider?.let { "${it.name.ifBlank { it.type.label }}（${it.type.label}）" }
        ?: "跟随新会话选择"
    val modelOptions = selectedProvider?.models ?: emptyList()

    UiScaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建 Agent" else "编辑 Agent") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    UiTextButton(onClick = {
                        if (name.isNotBlank()) {
                            onSave(
                                AgentProfile(
                                    id = agentId,
                                    name = name.trim(),
                                    emoji = emoji.trim().ifBlank { "🤖" },
                                    avatarPath = avatarPath,
                                    tools = if (allowAllTools) emptyList() else selectedTools.toList(),
                                    description = description.trim(),
                                    systemPrompt = systemPrompt.trim(),
                                    providerId = providerId,
                                    model = model
                                )
                            )
                        }
                    }) { Text("保存") }
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                    AgentAvatar(
                        emoji = emoji,
                        avatarPath = avatarPath,
                        size = 72.dp,
                        modifier = Modifier.clickable { showEmojiPicker = true }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") }
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("选图")
                            }
                            OutlinedButton(
                                onClick = { showEmojiPicker = true }
                            ) {
                                Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("选 Emoji")
                            }
                            if (avatarPath != null) {
                                OutlinedButton(
                                    onClick = {
                                        avatarPath = null
                                        app?.store?.deleteAgentAvatar(agentId)
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("清除")
                                }
                            }
                        }
                    Text(
                        text = if (avatarPath != null) "已使用自选相册图片（优先展示）" else "可选择 Emoji 或相册图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }

            PrototypeTextField(name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text("名称") }, singleLine = true)
            PrototypeTextField(description, { description = it }, Modifier.fillMaxWidth(),
                label = { Text("一句话介绍") }, singleLine = true)

            PrototypeTextField(
                systemPrompt, { systemPrompt = it }, Modifier.fillMaxWidth(),
                label = { Text("系统提示词") },
                supportingText = { Text("仅定义助手行为，工具执行仍受应用权限边界约束。") },
                minLines = 8, maxLines = 16
            )

            FormSection(
                title = "默认模型",
                description = "会话中的模型选择可以覆盖这里。"
            ) {
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = it }
            ) {
                PrototypeTextField(
                    value = providerLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("默认模型配置") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("跟随新会话选择") },
                        onClick = { providerId = null; model = null; providerMenuExpanded = false }
                    )
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name.ifBlank { p.type.label }}（${p.type.label}）") },
                            onClick = {
                                providerId = p.id
                                model = null
                                providerMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedProvider != null) {
                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it }
                ) {
                    PrototypeTextField(
                        value = model.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("默认模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        modelOptions.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { model = m; modelMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            }

            FormSection(
                title = "工具授权",
                description = "选择此 Agent 可使用的工具。"
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        allowAllTools = !allowAllTools
                        if (!allowAllTools && selectedTools.isEmpty()) {
                            selectedTools = Tools.ALL_NAMES.toSet()
                        }
                    }
                    .padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("允许全部工具", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "包含当前及后续新增的工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = allowAllTools,
                    onCheckedChange = { enabled ->
                        allowAllTools = enabled
                        if (!enabled && selectedTools.isEmpty()) {
                            selectedTools = Tools.ALL_NAMES.toSet()
                        }
                    }
                )
            }
            if (allowAllTools) {
                Text(
                    "实际执行时仍检查会话权限、目录范围和 Android 权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
            // 可用工具集配置卡片
            Card(
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "可用工具集权限 (${selectedTools.size}/${Tools.ALL_NAMES.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "全选",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    selectedTools = Tools.ALL_NAMES.toSet()
                                }
                            )
                            Text(
                                "允许全部",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.clickable {
                                    allowAllTools = true
                                }
                            )
                        }
                    }
                    Text(
                        "工具执行还受会话权限限制。实时会话状态查询始终可用；主代理具备文件写入能力时自动提供进入/提交计划工具；空工具列表沿用默认全集。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    val toolDescriptions = mapOf(
                        Tools.WRITE_FILE to ("写入文件" to "创建或覆盖工作区文本文件"),
                        Tools.EDIT_FILE to ("编辑文件" to "精确替换文件中的指定文本"),
                        Tools.DELETE_FILE to ("删除文件" to "仅删除单个文件；Accept Edit 每次确认，Plan/Readonly 禁止"),
                        Tools.RUN_COMMAND to ("执行命令" to "在 App 权限范围内执行，按会话模式审批"),
                        Tools.ENTER_PLAN_MODE to ("进入计划模式" to "主会话计划流程；具备文件写入能力时自动提供"),
                        Tools.EXIT_PLAN_MODE to ("提交计划" to "向用户展示计划，接受后切换模式并执行"),
                        Tools.READ_FILE to ("读取文件" to "读取工作区文本文件内容"),
                        Tools.LIST_FILES to ("文件列表" to "浏览工作区中现有文件列表"),
                        Tools.SAVE_MEMORY to ("存储记忆" to "保存重要认知至长期记忆库"),
                        Tools.SEARCH_MEMORY to ("检索记忆" to "查询关联的历史记忆"),
                        Tools.DELETE_MEMORY to ("删除记忆" to "移除已过期的记忆项"),
                        Tools.USE_SKILL to ("调度技能" to "执行专业预制技能模板"),
                        Tools.SAVE_SKILL to ("沉淀技能" to "保存并积累新能力"),
                        Tools.RUN_SUBAGENT to ("委派子代理" to "派发独立子任务并行推演")
                    )
                    Tools.ALL_NAMES.filterNot { it == Tools.GET_SESSION_STATE }.forEach { toolName ->
                        val (label, desc) = toolDescriptions[toolName] ?: (toolName to "")
                        val isChecked = toolName in selectedTools
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(ExpressiveTokens.CardShape)
                                .clickable {
                                    selectedTools = when {
                                        !isChecked -> selectedTools + toolName
                                        selectedTools.size > 1 -> selectedTools - toolName
                                        else -> selectedTools
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedTools = when {
                                        checked -> selectedTools + toolName
                                        selectedTools.size > 1 -> selectedTools - toolName
                                        else -> selectedTools
                                    }
                                }
                            )
                            Column(Modifier.padding(start = 4.dp)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                if (desc.isNotBlank()) {
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEmojiPicker) {
        AgentEmojiPickerDialog(
            currentEmoji = emoji,
            onSelect = { emoji = it },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

private val AGENT_EMOJI_CATEGORIES = listOf(
    "伙伴与角色" to listOf("🐱", "🦊", "🐰", "🤖", "🧙", "🕵️", "🧑‍💻", "🧑‍🔬", "🧑‍🎨", "🦸", "🥷", "👑"),
    "科技与工程" to listOf("💻", "⚡", "🛠️", "⚙️", "🔧", "📡", "🔬", "🧪", "🛰️", "🔌", "🖥️", "💾"),
    "思考与分析" to listOf("🧠", "💡", "🎯", "📊", "📈", "🗺️", "♟️", "🎲", "🧭", "🔍", "🔮", "📐"),
    "创作与知识" to listOf("🖋️", "📚", "📜", "📝", "🎨", "🎭", "🎬", "🎙️", "📖", "🔖", "💡", "💌"),
    "助手与效率" to listOf("🚀", "🛡️", "💼", "💬", "🌐", "🔔", "🔑", "📦", "🧩", "⭐", "☕", "🦾")
)

/**
 * Agent Emoji 选择器弹窗，提供丰富分类与单 Emoji 防呆校验
 */
@Composable
private fun AgentEmojiPickerDialog(
    currentEmoji: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 Agent 图标") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 自定义单 Emoji 输入槽（带防呆截断校验）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { input ->
                            // 限制最多为 1 个完整 Unicode 码点（杜绝长句子与乱输字符）
                            if (input.codePointCount(0, input.length) <= 1) {
                                customInput = input
                            }
                        },
                        placeholder = { Text("键入/粘贴单 Emoji") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            if (customInput.isNotBlank()) {
                                onSelect(customInput.trim())
                                onDismiss()
                            }
                        },
                        enabled = customInput.isNotBlank()
                    ) {
                        Text("确定")
                    }
                }

                AGENT_EMOJI_CATEGORIES.forEach { (category, emojis) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        emojis.chunked(6).forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { item ->
                                    val isSelected = item == currentEmoji
                                    Surface(
                                        onClick = {
                                            onSelect(item)
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        border = if (isSelected) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(item, fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            UiTextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Preview(showBackground = true, name = "Agents - Light")
@Composable
private fun AgentsPreviewLight() {
    val dummyAgents = listOf(
        AgentProfile(
            id = "1",
            name = "专属猫娘女仆",
            emoji = "🐱",
            description = "贴心、顺从、随时准备为主人效劳的女仆助手",
            model = "claude-3-7-sonnet"
        ),
        AgentProfile(
            id = "2",
            name = "代码重构专家",
            emoji = "⚡",
            description = "精通 Kotlin 与 Jetpack Compose 架构规范",
            model = "gpt-4o"
        )
    )
    AgentTheme(themeMode = "light") {
        AgentsContent(
            agents = dummyAgents,
            modelLabel = { it.model ?: "默认模型" },
            onOpenDrawer = {},
            onNewAgent = {},
            onSelectAgent = {},
            onDeleteAgent = {},
            onResetToDefaults = {}
        )
    }
}

@Preview(showBackground = true, name = "Agents - Dark")
@Composable
private fun AgentsPreviewDark() {
    val dummyAgents = listOf(
        AgentProfile(
            id = "1",
            name = "专属猫娘女仆",
            emoji = "🐱",
            description = "贴心、顺从、随时准备为主人效劳的女仆助手",
            model = "claude-3-7-sonnet"
        )
    )
    AgentTheme(themeMode = "dark") {
        AgentsContent(
            agents = dummyAgents,
            modelLabel = { it.model ?: "默认模型" },
            onOpenDrawer = {},
            onNewAgent = {},
            onSelectAgent = {},
            onDeleteAgent = {},
            onResetToDefaults = {}
        )
    }
}

@Preview(showBackground = true, name = "Agents - Empty")
@Composable
private fun AgentsEmptyPreview() {
    AgentTheme(themeMode = "light") {
        AgentsContent(
            agents = emptyList(),
            modelLabel = { "" },
            onOpenDrawer = {},
            onNewAgent = {},
            onSelectAgent = {},
            onDeleteAgent = {},
            onResetToDefaults = {}
        )
    }
}

@Preview(showBackground = true, name = "Agent Edit - Light")
@Composable
private fun AgentEditPreviewLight() {
    val sample = AgentProfile(
        id = "preview_1",
        name = "猫娘管家",
        emoji = "🐾",
        description = "2380年的专属女仆管家",
        systemPrompt = "你是一个专属猫娘女仆，全心全意侍奉主人..."
    )
    AgentTheme(themeMode = "light") {
        AgentEditContent(
            isNew = false,
            initialAgent = sample,
            providers = emptyList(),
            onBack = {},
            onSave = {}
        )
    }
}

@Preview(showBackground = true, name = "Agent Edit - Dark")
@Composable
private fun AgentEditPreviewDark() {
    AgentTheme(themeMode = "dark") {
        AgentEditContent(
            isNew = true,
            initialAgent = null,
            providers = emptyList(),
            onBack = {},
            onSave = {}
        )
    }
}
