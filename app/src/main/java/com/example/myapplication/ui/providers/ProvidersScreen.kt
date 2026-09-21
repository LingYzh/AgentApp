package com.example.myapplication.ui.providers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.myapplication.ui.components.rememberListSelection
import com.example.myapplication.ui.components.ListSelectionBar
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.example.myapplication.ui.theme.ExpressiveTokens
import com.example.myapplication.ui.transfer.ConfigurationTransferHost
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.AnthropicThinkingMode
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.ModelCapabilities
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.ReasoningProtocol
import com.example.myapplication.data.model.anthropicThinkingProtocol
import com.example.myapplication.data.model.reasoningSupportFor
import com.example.myapplication.data.model.temperatureConflictFor
import com.example.myapplication.provider.anthropicBudgetFor
import com.example.myapplication.provider.geminiBudgetFor
import com.example.myapplication.ui.theme.AgentTheme
import com.example.myapplication.provider.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ProvidersViewModel(val app: AgentApp) : ViewModel() {
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
                val current = app.store.loadConfig()
                val remaining = current.providers.filterNot { it.id in ids }
                app.store.saveConfig(current.copy(
                    providers = remaining,
                    selectedProviderId = current.selectedProviderId?.takeUnless { it in ids }
                        ?: remaining.firstOrNull()?.id
                ))
                _config.value = app.store.loadConfig()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _deleteError.value = error.message ?: "删除失败"
            } finally {
                _deleting.value = false
            }
        }
    }

    private val _config = MutableStateFlow(app.store.loadConfig())
    val config = _config.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _config.value = app.store.loadConfig()
        }
    }

    fun saveProvider(provider: ProviderConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            val c = app.store.loadConfig()
            val list = c.providers.filterNot { it.id == provider.id } + provider
            val selected = c.selectedProviderId ?: provider.id
            app.store.saveConfig(c.copy(providers = list, selectedProviderId = selected))
            refresh()
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val c = app.store.loadConfig()
            val list = c.providers.filterNot { it.id == id }
            val selected = if (c.selectedProviderId == id) list.firstOrNull()?.id else c.selectedProviderId
            app.store.saveConfig(c.copy(providers = list, selectedProviderId = selected))
            refresh()
        }
    }

    fun select(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val c = app.store.loadConfig()
            app.store.saveConfig(c.copy(selectedProviderId = id))
            refresh()
        }
    }

    private val _testing = MutableStateFlow(false)
    val testing = _testing.asStateFlow()
    private val _testResult = MutableStateFlow<String?>(null)
    val testResult = _testResult.asStateFlow()

    /** 发一条最小请求验证连通性（复用流式通道） */
    fun testConnection(provider: ProviderConfig) {
        if (_testing.value) return
        _testing.value = true
        _testResult.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = withTimeoutOrNull(60_000) {
                val sb = StringBuilder()
                var failure: String? = null
                try {
                    app.providerFactory.create(provider.type).streamChat(
                        config = provider,
                        system = "",
                        messages = listOf(ChatMessage(role = "user", content = "Hi")),
                        tools = emptyList()
                    ) { ev ->
                        when (ev) {
                            is StreamEvent.Text -> sb.append(ev.delta)
                            is StreamEvent.Error -> failure = ev.message
                            else -> Unit
                        }
                    }
                } catch (e: Exception) {
                    failure = e.message ?: e.javaClass.simpleName
                }
                failure?.let { "❌ $it" }
                    ?: if (sb.isNotEmpty()) "✅ 连接成功，模型回复：${sb.toString().take(100)}"
                    else "⚠️ 请求成功但无文本输出（若是自定义模板请检查提取路径）"
            } ?: "❌ 超时（60 秒）"
            _testResult.value = result
            _testing.value = false
        }
    }

    private val _fetchingModels = MutableStateFlow(false)
    val fetchingModels = _fetchingModels.asStateFlow()
    private val _fetchedModels = MutableStateFlow<List<String>?>(null)
    val fetchedModels = _fetchedModels.asStateFlow()
    private val _fetchedCapabilities = MutableStateFlow<Map<String, ModelCapabilities>?>(null)
    val fetchedCapabilities = _fetchedCapabilities.asStateFlow()
    private val _fetchedScope = MutableStateFlow<String?>(null)
    val fetchedScope = _fetchedScope.asStateFlow()
    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError = _fetchError.asStateFlow()

    /** 从供应商 /models 接口拉取模型列表 */
    fun fetchModels(provider: ProviderConfig) {
        if (_fetchingModels.value) return
        _fetchingModels.value = true
        _fetchError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scope = capabilityScope(provider.type, provider.baseUrl, provider.modelsUrl)
                val catalog = app.modelFetcher.fetchModelCatalog(provider)
                _fetchedModels.value = catalog.models
                _fetchedCapabilities.value = catalog.discoveredCapabilities
                _fetchedScope.value = scope
                if (catalog.models.isEmpty()) _fetchError.value = "接口返回为空"
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _fetchError.value = e.message ?: "拉取失败"
            } finally {
                _fetchingModels.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(navController: NavHostController, openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: ProvidersViewModel = viewModel(factory = viewModelFactory {
        initializer { ProvidersViewModel(app) }
    })
    LifecycleStartEffect(Unit) {
        vm.refresh()
        onStopOrDispose { }
    }
    val config by vm.config.collectAsStateWithLifecycle()
    val deleting by vm.deleting.collectAsStateWithLifecycle()
    val deleteError by vm.deleteError.collectAsStateWithLifecycle()
    deleteError?.let { message ->
        AlertDialog(onDismissRequest = vm::clearDeleteError,
            title = { Text("删除失败") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::clearDeleteError) { Text("确定") } })
    }


    ConfigurationTransferHost(
        kind = TransferKind.PROVIDERS,
        transfer = app.configurationTransfer,
        onImportSuccess = { vm.refresh() }
    ) { actions ->
        ProvidersContent(
            config = config,
            onOpenDrawer = openDrawer,
            onAddProvider = { navController.safeNavigateDirect(Routes.providerEdit("new")) },
            onSelectProvider = { id -> vm.select(id) },
            onEditProvider = { id -> navController.safeNavigateDirect(Routes.providerEdit(id)) },
            onDeleteProvider = { id -> vm.deleteProvider(id) },
            onImport = actions.onImport,
            onExportSelected = actions.onExportSelected,
            onDeleteSelected = vm::deleteSelected,
            busy = actions.busy || deleting
        )
    }
}

/**
 * 模型配置列表纯 UI 组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersContent(
    config: AppConfig,
    onOpenDrawer: () -> Unit,
    onAddProvider: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onEditProvider: (String) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onImport: () -> Unit = {},
    onExportSelected: (Set<String>) -> Unit = {},
    onDeleteSelected: (Set<String>) -> Unit = {},
    busy: Boolean = false
) {
    val selection = rememberListSelection(config.providers.map { it.id }, config.providers.associate { it.id to it.name.ifBlank { it.model } })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                },
                actions = {
                    TextButton(onClick = if (selection.active) selection.onExit else selection.onEnter,
                        enabled = !busy) { Text(if (selection.active) "完成" else "管理") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (selection.active) ListSelectionBar(selection, busy, onDeleteSelected,
                onImport = onImport, onExport = onExportSelected)
        },
        floatingActionButton = {
            if (!selection.active) {
                FloatingActionButton(onClick = { if (!busy) onAddProvider() }) {
                    Icon(Icons.Filled.Add, "添加")
                }
            }
        }
    ) { padding ->
        if (config.providers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "还没有模型配置，点右下角添加\n支持 OpenAI 兼容 / Anthropic / Gemini / 自定义模板",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = ExpressiveTokens.ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ExpressiveTokens.ScreenHorizontalPadding,
                    bottom = ExpressiveTokens.FabSafeBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(config.providers, key = { it.id }) { p ->
                    Card(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        onClick = { if (!busy) {
                            if (selection.active) selection.onToggle(p.id) else onEditProvider(p.id)
                        } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selection.active) Checkbox(
                                checked = p.id in selection.selectedIds,
                                onCheckedChange = { selection.onToggle(p.id) },
                                enabled = !busy
                            ) else RadioButton(
                                selected = p.id == config.selectedProviderId,
                                onClick = { onSelectProvider(p.id) },
                                enabled = !busy
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.name.ifBlank { p.model },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${p.type.label} · ${p.model}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    p.baseUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (!selection.active) {
                                IconButton(onClick = { onDeleteProvider(p.id) }, enabled = !busy) {
                                    Icon(Icons.Filled.Delete, "删除")
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
fun ProviderEditScreen(navController: NavHostController, providerId: String) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: ProvidersViewModel = viewModel(factory = viewModelFactory {
        initializer { ProvidersViewModel(app) }
    })
    val testing by vm.testing.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    val fetchingModels by vm.fetchingModels.collectAsStateWithLifecycle()
    val fetchedModels by vm.fetchedModels.collectAsStateWithLifecycle()
    val fetchedCapabilities by vm.fetchedCapabilities.collectAsStateWithLifecycle()
    val fetchedScope by vm.fetchedScope.collectAsStateWithLifecycle()
    val fetchError by vm.fetchError.collectAsStateWithLifecycle()

    val existing = remember(providerId) {
        if (providerId == "new") null
        else app.store.loadConfig().providers.firstOrNull { it.id == providerId }
    }

    ProviderEditContent(
        isNew = providerId == "new",
        initialConfig = existing,
        testing = testing,
        testResult = testResult,
        fetchingModels = fetchingModels,
        fetchedModels = fetchedModels,
        fetchedCapabilities = fetchedCapabilities,
        fetchedScope = fetchedScope,
        fetchError = fetchError,
        onBack = { navController.safePopBackStack() },
        onSave = { config ->
            vm.saveProvider(config)
            navController.safePopBackStack()
        },
        onTest = { config -> vm.testConnection(config) },
        onFetchModels = { config -> vm.fetchModels(config) }
    )
}

/**
 * 模型配置编辑页面纯 UI 组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditContent(
    isNew: Boolean,
    initialConfig: ProviderConfig?,
    testing: Boolean,
    testResult: String?,
    fetchingModels: Boolean,
    fetchedModels: List<String>?,
    fetchedCapabilities: Map<String, ModelCapabilities>?,
    fetchedScope: String?,
    fetchError: String?,
    onBack: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onTest: (ProviderConfig) -> Unit,
    onFetchModels: (ProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var type by remember { mutableStateOf(initialConfig?.type ?: ProviderType.OPENAI) }
    var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl ?: defaultBaseUrl(type)) }
    var modelsUrl by remember { mutableStateOf(initialConfig?.modelsUrl ?: "") }
    var apiKey by remember { mutableStateOf(initialConfig?.apiKey ?: "") }
    var model by remember { mutableStateOf(initialConfig?.model ?: "") }
    var temperature by remember { mutableStateOf(initialConfig?.temperature?.toString() ?: "") }
    var maxOutputTokens by remember { mutableStateOf(initialConfig?.maxOutputTokens?.toString() ?: "") }
    var reasoningEffort by remember { mutableStateOf(initialConfig?.reasoningEffort) }
    var anthropicThinkingMode by remember {
        mutableStateOf(initialConfig?.anthropicThinkingMode ?: AnthropicThinkingMode.AUTO)
    }
    var headers by remember {
        mutableStateOf(initialConfig?.extraHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: "")
    }
    var template by remember { mutableStateOf(initialConfig?.customRequestTemplate ?: "") }
    var responsePath by remember { mutableStateOf(initialConfig?.customResponsePath ?: "") }
    var streamPath by remember { mutableStateOf(initialConfig?.customStreamPath ?: "") }
    var capabilityOverrides by remember {
        mutableStateOf(initialConfig?.capabilityOverrides ?: emptyMap())
    }
    var contextWindowOverrides by remember {
        mutableStateOf(initialConfig?.contextWindowOverrides ?: emptyMap())
    }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var reasoningMenuExpanded by remember { mutableStateOf(false) }
    var anthropicThinkingModeMenuExpanded by remember { mutableStateOf(false) }

    val currentScope = capabilityScope(type, baseUrl, modelsUrl)
    val initialScope = initialConfig?.let { capabilityScope(it.type, it.baseUrl, it.modelsUrl) }
    val fetchedMatchesScope = fetchedScope == currentScope
    val modelCandidates = if (fetchedModels != null && fetchedMatchesScope) {
        fetchedModels
    } else {
        initialConfig?.models ?: emptyList()
    }
    // Discovery data belongs to one catalogue endpoint; never carry it to a changed endpoint.
    val discoveredCapabilities = when {
        fetchedModels != null && fetchedMatchesScope -> fetchedCapabilities.orEmpty()
        currentScope == initialScope -> initialConfig?.discoveredCapabilities.orEmpty()
        else -> emptyMap()
    }
    val currentModelId = model.trim()
    val reasoningSupport = reasoningSupportFor(type, currentModelId)
    val selectedReasoning = when (type) {
        ProviderType.OPENAI, ProviderType.ANTHROPIC -> reasoningEffort
        else -> reasoningEffort?.takeIf { it in reasoningSupport.efforts }
    }
    val anthropicProtocol = selectedReasoning
        ?.takeIf { type == ProviderType.ANTHROPIC && it != ReasoningEffort.NONE }
        ?.let { anthropicThinkingProtocol(currentModelId, anthropicThinkingMode) }
    val temperatureConflict = temperatureConflictFor(type, currentModelId, selectedReasoning)
    val parsedMaxOutputTokens = maxOutputTokens.trim().toIntOrNull()
    val manualThinkingBudget = selectedReasoning
        ?.takeIf { anthropicProtocol == ReasoningProtocol.ANTHROPIC_MANUAL }
        ?.let(::anthropicBudgetFor)
    val maxOutputTokensError = when {
        maxOutputTokens.isBlank() -> null
        parsedMaxOutputTokens == null || parsedMaxOutputTokens <= 0 -> "请输入大于 0 的整数。"
        manualThinkingBudget != null && parsedMaxOutputTokens <= manualThinkingBudget ->
            "手动 thinking 预算为 $manualThinkingBudget，最大输出必须更大。"
        else -> null
    }
    val currentOverride = capabilityOverrides[currentModelId]
    val currentAutoCapabilities = discoveredCapabilities[currentModelId] ?: ModelCapabilities()
    val currentCapabilities = currentOverride ?: currentAutoCapabilities

    fun updateCurrentCapabilities(transform: (ModelCapabilities) -> ModelCapabilities) {
        if (currentModelId.isBlank()) return
        capabilityOverrides = capabilityOverrides + (currentModelId to transform(currentCapabilities))
    }

    fun buildConfig() = ProviderConfig(
        id = initialConfig?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim(),
        type = type,
        baseUrl = baseUrl.trim(),
        modelsUrl = modelsUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        temperature = temperature.toFloatOrNull().takeUnless { temperatureConflict != null },
        maxOutputTokens = parsedMaxOutputTokens?.takeIf { it > 0 },
        reasoningEffort = selectedReasoning,
        anthropicThinkingMode = anthropicThinkingMode,
        customRequestTemplate = template,
        customResponsePath = responsePath.trim(),
        customStreamPath = streamPath.trim(),
        extraHeaders = headers.lines()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
            }
            .toMap(),
        models = modelCandidates,
        discoveredCapabilities = discoveredCapabilities,
        capabilityOverrides = capabilityOverrides,
        contextWindowOverrides = contextWindowOverrides
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "添加模型" else "编辑模型") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                        onSave(buildConfig())
                        },
                        enabled = maxOutputTokensError == null
                    ) { Icon(Icons.Filled.Check, "保存") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text("名称") }, singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = type.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false }
                ) {
                    ProviderType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label) },
                            onClick = {
                                type = t
                                if (initialConfig == null || baseUrl == defaultBaseUrl(type)) {
                                    baseUrl = defaultBaseUrl(t)
                                }
                                typeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(),
                label = { Text("Base URL") }, singleLine = true,
                supportingText = { Text(baseUrlHint(type)) }
            )
            if (type != ProviderType.CUSTOM) {
                OutlinedTextField(
                    modelsUrl, { modelsUrl = it }, Modifier.fillMaxWidth(),
                    label = { Text("模型列表 URL（可选）") }, singleLine = true,
                    supportingText = { Text("留空自动识别；DeepSeek 两种协议均使用 /models。特殊网关可填完整地址。") }
                )
            }
            OutlinedTextField(
                apiKey, { apiKey = it }, Modifier.fillMaxWidth(),
                label = { Text("API Key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = { modelMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名（可输入或从下拉选择）") },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable)
                )
                ExposedDropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    modelCandidates.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = { model = m; modelMenuExpanded = false }
                        )
                    }
                }
            }
            if (currentModelId.isNotBlank()) {
                OutlinedTextField(
                    value = contextWindowOverrides[currentModelId]?.toString().orEmpty(),
                    onValueChange = { value ->
                        val trimmed = value.trim()
                        val parsed = trimmed.toIntOrNull()
                        contextWindowOverrides = when {
                            trimmed.isBlank() -> contextWindowOverrides - currentModelId
                            parsed != null && parsed > 0 -> contextWindowOverrides + (currentModelId to parsed)
                            else -> contextWindowOverrides
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最大上下文 tokens（可选）") },
                    supportingText = { Text("只保存到当前模型；留空表示未知，不估算上下文窗口。") },
                    singleLine = true
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onFetchModels(buildConfig()) },
                    enabled = !fetchingModels && type != ProviderType.CUSTOM && baseUrl.isNotBlank()
                ) {
                    if (fetchingModels) CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                    ) else Text("获取模型列表")
                }
                if (modelCandidates.isNotEmpty()) {
                    Text(
                        "${modelCandidates.size} 个模型可选",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            fetchError?.let {
                Text(
                    "❌ $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (currentModelId.isNotBlank() && type != ProviderType.CUSTOM) {
                Text("当前模型能力", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        currentOverride != null -> "来源：手动覆盖（仅 $currentModelId）"
                        discoveredCapabilities.containsKey(currentModelId) -> "来源：接口自动发现"
                        else -> "接口未提供能力信息，请手动设置"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CapabilityToggleRow("图片", currentCapabilities.image) {
                    updateCurrentCapabilities { it.copy(image = !it.image) }
                }
                CapabilityToggleRow("PDF", currentCapabilities.pdf) {
                    updateCurrentCapabilities { it.copy(pdf = !it.pdf) }
                }
                CapabilityToggleRow(
                    "音频（仅 Gemini 原生请求）", currentCapabilities.audio,
                    enabled = type == ProviderType.GEMINI
                ) {
                    updateCurrentCapabilities { it.copy(audio = !it.audio) }
                }
                CapabilityToggleRow(
                    "视频（仅 Gemini 原生请求）", currentCapabilities.video,
                    enabled = type == ProviderType.GEMINI
                ) {
                    updateCurrentCapabilities { it.copy(video = !it.video) }
                }
                if (currentOverride != null) {
                    TextButton(onClick = { capabilityOverrides = capabilityOverrides - currentModelId }) {
                        Text("恢复接口自动能力")
                    }
                }
            } else if (currentModelId.isNotBlank()) {
                Text(
                    "自定义模板不支持附件读取，请改用 OpenAI 兼容、Anthropic 或 Gemini。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                temperature, { temperature = it }, Modifier.fillMaxWidth(),
                label = { Text(temperatureConflict?.let { "温度（当前组合不可用）" } ?: "温度（可留空）") },
                singleLine = true,
                enabled = temperatureConflict == null
            )
            if (type != ProviderType.CUSTOM) {
                OutlinedTextField(
                    maxOutputTokens,
                    { maxOutputTokens = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("最大输出 tokens（可选）") },
                    supportingText = {
                        Text(
                            when (type) {
                                ProviderType.OPENAI -> "留空不发送 max_tokens，由上游决定。"
                                ProviderType.ANTHROPIC -> "留空发送 65536；该协议的 max_tokens 包含 thinking 和正文。"
                                ProviderType.GEMINI -> "留空不发送 maxOutputTokens，由上游决定。"
                                ProviderType.CUSTOM -> ""
                            }
                        )
                    },
                    isError = maxOutputTokensError != null,
                    singleLine = true
                )
                maxOutputTokensError?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (type != ProviderType.CUSTOM) {
                Text("思考强度", style = MaterialTheme.typography.titleSmall)
                ExposedDropdownMenuBox(
                    expanded = reasoningMenuExpanded,
                    onExpandedChange = {
                        if (reasoningSupport.efforts.isNotEmpty()) reasoningMenuExpanded = it
                    }
                ) {
                    OutlinedTextField(
                        value = selectedReasoning?.let { "${it.wireValue}（${it.label}）" } ?: "默认（不发送参数）",
                        onValueChange = {},
                        readOnly = true,
                        enabled = reasoningSupport.efforts.isNotEmpty(),
                        label = { Text("思考强度（可选）") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(reasoningMenuExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = reasoningMenuExpanded,
                        onDismissRequest = { reasoningMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("默认（不发送参数）") },
                            onClick = { reasoningEffort = null; reasoningMenuExpanded = false }
                        )
                        reasoningSupport.efforts.forEach { effort ->
                            DropdownMenuItem(
                                text = { Text("${effort.wireValue}（${effort.label}）") },
                                onClick = { reasoningEffort = effort; reasoningMenuExpanded = false }
                            )
                        }
                    }
                }
                Text(
                    reasoningSupport.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (type == ProviderType.ANTHROPIC) {
                    ExposedDropdownMenuBox(
                        expanded = anthropicThinkingModeMenuExpanded,
                        onExpandedChange = { anthropicThinkingModeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = anthropicThinkingMode.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Claude thinking 模式") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(anthropicThinkingModeMenuExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = anthropicThinkingModeMenuExpanded,
                            onDismissRequest = { anthropicThinkingModeMenuExpanded = false }
                        ) {
                            AnthropicThinkingMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        anthropicThinkingMode = mode
                                        anthropicThinkingModeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        when {
                            selectedReasoning == null -> "默认不发送 thinking 参数。"
                            selectedReasoning == ReasoningEffort.NONE -> "关闭时发送 thinking.disabled，不发送 effort。"
                            anthropicProtocol == ReasoningProtocol.ANTHROPIC_MANUAL -> "此模型将使用固定 thinking token 预算。"
                            else -> "此模型将发送 adaptive thinking 与 output_config.effort。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (reasoningEffort != null && selectedReasoning == null) {
                    Text(
                        "已保存的思考强度不适用于当前模型；保存时会移除，且本次不会发送。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (selectedReasoning != null && anthropicProtocol == ReasoningProtocol.ANTHROPIC_MANUAL) {
                    Text(
                        "此档位会请求 ${anthropicBudgetFor(selectedReasoning)} 个 thinking tokens；max_tokens 必须比该预算大。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedReasoning != null && reasoningSupport.protocol == ReasoningProtocol.GEMINI_THINKING_BUDGET) {
                    Text(
                        "此档位会发送 thinkingBudget=${geminiBudgetFor(currentModelId, selectedReasoning)}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                temperatureConflict?.let { conflict ->
                    Text(
                        "$conflict 保存时将不发送温度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                headers, { headers = it }, Modifier.fillMaxWidth(),
                label = { Text("附加请求头（每行一个 Key: Value，可留空）") }, minLines = 1, maxLines = 4
            )

            if (type == ProviderType.CUSTOM) {
                Text("自定义模板", style = MaterialTheme.typography.titleSmall)
                Text(
                    "占位符：\${model} \${system} \${messages} \${tools}；留空则按 OpenAI 风格发送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    template, { template = it }, Modifier.fillMaxWidth(),
                    label = { Text("请求体模板（JSON）") }, minLines = 4, maxLines = 8
                )
                OutlinedTextField(
                    responsePath, { responsePath = it }, Modifier.fillMaxWidth(),
                    label = { Text("非流式响应提取路径，如 $.choices[0].message.content") }, singleLine = true
                )
                OutlinedTextField(
                    streamPath, { streamPath = it }, Modifier.fillMaxWidth(),
                    label = { Text("SSE 行提取路径，如 $.choices[0].delta.content") }, singleLine = true
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onTest(buildConfig()) },
                    enabled = !testing && maxOutputTokensError == null && baseUrl.isNotBlank() && model.isNotBlank()
                ) {
                    if (testing) CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    else Text("测试连接")
                }
            }
            testResult?.let {
                Card(
                    shape = ExpressiveTokens.CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(ExpressiveTokens.FabSafeBottomPadding))
        }
    }
}

@Composable
private fun CapabilityToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onClick() }, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun defaultBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.OPENAI -> "https://api.openai.com/v1"
    ProviderType.ANTHROPIC -> "https://api.anthropic.com"
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com"
    ProviderType.CUSTOM -> ""
}

private fun capabilityScope(type: ProviderType, baseUrl: String, modelsUrl: String): String =
    "${type.name}|${baseUrl.trim().trimEnd('/')}|${modelsUrl.trim()}"

private fun baseUrlHint(type: ProviderType): String = when (type) {
    ProviderType.OPENAI -> "填到 /v1 即可，自动追加 /chat/completions；兼容 DeepSeek、通义、Ollama 等"
    ProviderType.ANTHROPIC -> "填域名即可，自动追加 /v1/messages"
    ProviderType.GEMINI -> "填域名即可，自动追加 /v1beta/models/<model>:streamGenerateContent"
    ProviderType.CUSTOM -> "完整请求 URL"
}

@Preview(showBackground = true, name = "Providers - Light")
@Composable
private fun ProvidersPreviewLight() {
    val sampleConfig = AppConfig(
        selectedProviderId = "p1",
        providers = listOf(
            ProviderConfig(
                id = "p1",
                name = "Claude 官方",
                type = ProviderType.ANTHROPIC,
                baseUrl = "https://api.anthropic.com",
                model = "claude-3-7-sonnet"
            ),
            ProviderConfig(
                id = "p2",
                name = "DeepSeek",
                type = ProviderType.OPENAI,
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat"
            )
        )
    )
    AgentTheme(themeMode = "light") {
        ProvidersContent(
            config = sampleConfig,
            onOpenDrawer = {},
            onAddProvider = {},
            onSelectProvider = {},
            onEditProvider = {},
            onDeleteProvider = {}
        )
    }
}

@Preview(showBackground = true, name = "Providers - Dark")
@Composable
private fun ProvidersPreviewDark() {
    val sampleConfig = AppConfig(
        selectedProviderId = "p1",
        providers = listOf(
            ProviderConfig(
                id = "p1",
                name = "Claude 官方",
                type = ProviderType.ANTHROPIC,
                baseUrl = "https://api.anthropic.com",
                model = "claude-3-7-sonnet"
            )
        )
    )
    AgentTheme(themeMode = "dark") {
        ProvidersContent(
            config = sampleConfig,
            onOpenDrawer = {},
            onAddProvider = {},
            onSelectProvider = {},
            onEditProvider = {},
            onDeleteProvider = {}
        )
    }
}

@Preview(showBackground = true, name = "Providers - Empty")
@Composable
private fun ProvidersEmptyPreview() {
    AgentTheme(themeMode = "light") {
        ProvidersContent(
            config = AppConfig(providers = emptyList()),
            onOpenDrawer = {},
            onAddProvider = {},
            onSelectProvider = {},
            onEditProvider = {},
            onDeleteProvider = {}
        )
    }
}

@Preview(showBackground = true, name = "Provider Edit - Light")
@Composable
private fun ProviderEditPreviewLight() {
    val sample = ProviderConfig(
        id = "p1",
        name = "Anthropic Claude",
        type = ProviderType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        model = "claude-3-7-sonnet",
        models = listOf("claude-3-7-sonnet", "claude-3-5-haiku")
    )
    AgentTheme(themeMode = "light") {
        ProviderEditContent(
            isNew = false,
            initialConfig = sample,
            testing = false,
            testResult = "✅ 连接成功，模型响应正常",
            fetchingModels = false,
            fetchedModels = listOf("claude-3-7-sonnet", "claude-3-5-haiku"),
            fetchedCapabilities = emptyMap(),
            fetchedScope = capabilityScope(ProviderType.ANTHROPIC, "https://api.anthropic.com", ""),
            fetchError = null,
            onBack = {},
            onSave = {},
            onTest = {},
            onFetchModels = {}
        )
    }
}

@Preview(showBackground = true, name = "Provider Edit - Dark")
@Composable
private fun ProviderEditPreviewDark() {
    AgentTheme(themeMode = "dark") {
        ProviderEditContent(
            isNew = true,
            initialConfig = null,
            testing = false,
            testResult = null,
            fetchingModels = false,
            fetchedModels = null,
            fetchedCapabilities = null,
            fetchedScope = null,
            fetchError = null,
            onBack = {},
            onSave = {},
            onTest = {},
            onFetchModels = {}
        )
    }
}
