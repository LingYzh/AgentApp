package com.example.myapplication.ui.providers

import com.example.myapplication.ui.components.UiScaffold
import com.example.myapplication.ui.components.PrototypeTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import com.example.myapplication.ui.components.rememberListSelection
import com.example.myapplication.ui.components.ListSelectionBar
import com.example.myapplication.ui.components.ListPageHeader
import com.example.myapplication.ui.components.ListOperationsMenu
import com.example.myapplication.ui.components.inertWhen
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.myapplication.data.model.ReasoningProtocol
import com.example.myapplication.data.model.anthropicThinkingProtocol
import com.example.myapplication.ui.theme.AgentTheme
import com.example.myapplication.provider.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

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

    suspend fun saveProvider(provider: ProviderConfig) {
        withContext(Dispatchers.IO) {
            val c = app.store.loadConfig()
            val list = c.providers.filterNot { it.id == provider.id } + provider
            val selected = c.selectedProviderId
            app.store.saveConfig(c.copy(providers = list, selectedProviderId = selected))
            _config.value = c.copy(providers = list, selectedProviderId = selected)
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
            confirmButton = { UiTextButton(onClick = vm::clearDeleteError) { Text("确定") } })
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
            onEditProvider = { id -> navController.safeNavigateDirect(Routes.providerEdit(id)) },
            onDeleteProvider = { id -> vm.deleteProvider(id) },
            onImport = actions.onImport,
            onExportAll = actions.onExportAll,
            onExportSelected = actions.onExportSelected,
            onDeleteSelected = vm::deleteSelected,
            busy = actions.busy || deleting
        )
    }
}

/**
 * 模型供应商设置列表纯 UI 组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersContent(
    config: AppConfig,
    onOpenDrawer: () -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (String) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onImport: () -> Unit = {},
    onExportAll: () -> Unit = {},
    onExportSelected: (Set<String>) -> Unit = {},
    onDeleteSelected: (Set<String>) -> Unit = {},
    busy: Boolean = false
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val filteredProviders = remember(config.providers, normalizedQuery) {
        config.providers.filter { provider ->
            normalizedQuery.isBlank() || listOf(
                provider.name,
                provider.type.label,
                provider.baseUrl
            ).any { it.contains(normalizedQuery, ignoreCase = true) }
        }
    }
    val selection = rememberListSelection(config.providers.map { it.id },
        config.providers.associate { it.id to it.name.ifBlank { it.type.label } },
        filteredProviders.map { it.id })

    UiScaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型供应商设置") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                },
                actions = {
                    ListOperationsMenu(selection, busy, onImport, onExportAll)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "page-header") {
                ListPageHeader(
                    title = "模型供应商设置",
                    description = "管理连接与模型列表，点开供应商调整配置。",
                    query = query,
                    onQueryChange = { query = it },
                    searchPlaceholder = "搜索模型供应商",
                    actionLabel = if (selection.active || busy) null else "添加供应商",
                    onAction = if (selection.active || busy) null else onAddProvider
                )
            }
            if (filteredProviders.isEmpty()) {
                item(key = "empty-state") {
                    Text(
                        if (normalizedQuery.isBlank()) "还没有模型供应商" else "没有匹配的模型供应商",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                items(filteredProviders, key = { it.id }) { p ->
                    Card(
                        onClick = { if (selection.active) selection.onToggle(p.id) else onEditProvider(p.id) },
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.animateItem().fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (selection.active) Checkbox(
                                checked = p.id in selection.selectedIds,
                                onCheckedChange = null,
                                enabled = !busy
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.name.ifBlank { p.type.label }, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(p.type.label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${p.models.filter { it.isNotBlank() }.distinct().size} 个模型",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!selection.active) Icon(Icons.Filled.ExpandMore, "编辑供应商",
                                Modifier.graphicsLayer { rotationZ = -90f },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val coroutineScope = rememberCoroutineScope()
    var saving by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }

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
            if (!saving) coroutineScope.launch {
                saving = true
                saveError = null
                try {
                    vm.saveProvider(config)
                    navController.safePopBackStack()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    saveError = error.message ?: "保存失败，请重试。"
                } finally {
                    saving = false
                }
            }
        },
        onTest = { config -> vm.testConnection(config) },
        onFetchModels = { config -> vm.fetchModels(config) },
        saving = saving,
        saveError = saveError
    )
}

/**
 * 模型供应商编辑页面纯 UI 组件
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
    onFetchModels: (ProviderConfig) -> Unit,
    saving: Boolean = false,
    saveError: String? = null
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var type by remember { mutableStateOf(initialConfig?.type ?: ProviderType.OPENAI) }
    var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl ?: defaultBaseUrl(type)) }
    var modelsUrl by remember { mutableStateOf(initialConfig?.modelsUrl ?: "") }
    var apiKey by remember { mutableStateOf(initialConfig?.apiKey ?: "") }
    var model by remember { mutableStateOf(initialConfig?.model ?: "") }
    var temperature by remember { mutableStateOf(initialConfig?.temperature?.toString() ?: "") }
    var maxOutputTokens by remember { mutableStateOf(initialConfig?.maxOutputTokens?.toString() ?: "") }
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
    var contextWindowDrafts by remember {
        mutableStateOf<Map<String, String>>(
            initialConfig?.contextWindowOverrides?.mapValues { it.value.toString() } ?: emptyMap()
        )
    }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var anthropicThinkingModeMenuExpanded by remember { mutableStateOf(false) }
    var generationExpanded by rememberSaveable { mutableStateOf(false) }
    var modelListExpanded by rememberSaveable { mutableStateOf(true) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var modelSettingsModel by rememberSaveable { mutableStateOf<String?>(null) }
    val pageState = rememberSaveableStateHolder()

    BackHandler(enabled = modelSettingsModel != null || saving) {
        if (!saving) modelSettingsModel = null
    }

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
    var manualModelName by rememberSaveable { mutableStateOf("") }
    var addedModels by remember { mutableStateOf(emptyList<String>()) }
    val currentModelId = model.trim()
    val anthropicProtocol = if (type == ProviderType.ANTHROPIC) {
        anthropicThinkingProtocol(currentModelId, anthropicThinkingMode)
    } else {
        null
    }
    val parsedMaxOutputTokens = maxOutputTokens.trim().toIntOrNull()
    val maxOutputTokensError = when {
        maxOutputTokens.isBlank() -> null
        parsedMaxOutputTokens == null || parsedMaxOutputTokens <= 0 -> "请输入大于 0 的整数。"
        else -> null
    }
    val invalidContextWindowModels = contextWindowDrafts.filterValues { value ->
        value.isNotBlank() && value.trim().toIntOrNull()?.let { it > 0 } != true
    }.keys
    val activeModelContextIsInvalid = modelSettingsModel?.let { modelId ->
        contextWindowDrafts[modelId]?.let { value ->
            value.isNotBlank() && value.trim().toIntOrNull()?.let { it > 0 } != true
        } == true
    } == true
    val visibleModelIds = (modelCandidates + addedModels).filter { it.isNotBlank() }.distinct()

    fun buildConfig() = ProviderConfig(
        id = initialConfig?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim(),
        type = type,
        baseUrl = baseUrl.trim(),
        modelsUrl = modelsUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        temperature = temperature.toFloatOrNull(),
        maxOutputTokens = parsedMaxOutputTokens?.takeIf { it > 0 },
        reasoningEffort = null,
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
        models = visibleModelIds,
        discoveredCapabilities = discoveredCapabilities,
        capabilityOverrides = capabilityOverrides,
        contextWindowOverrides = contextWindowOverrides
    )

    UiScaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (modelSettingsModel == null) {
                        Text(if (isNew) "添加模型供应商" else "编辑模型供应商")
                    } else {
                        Column {
                            Text("模型设置", style = MaterialTheme.typography.titleMedium)
                            Text(
                                modelSettingsModel.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (modelSettingsModel != null) modelSettingsModel = null else onBack()
                        },
                        enabled = !saving
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            if (modelSettingsModel == null) "返回供应商设置" else "返回模型列表"
                        )
                    }
                },
                actions = {
                    UiTextButton(
                        onClick = {
                            if (modelSettingsModel != null) {
                                modelSettingsModel = null
                            } else {
                                onSave(buildConfig())
                            }
                        },
                        enabled = !saving && if (modelSettingsModel != null) {
                            !activeModelContextIsInvalid
                        } else {
                            maxOutputTokensError == null && invalidContextWindowModels.isEmpty()
                        }
                    ) {
                        Text(
                            when {
                                modelSettingsModel != null -> "完成"
                                saving -> "保存中…"
                                else -> "保存"
                            }
                        )
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
        AnimatedContent(
            targetState = modelSettingsModel,
            modifier = Modifier.fillMaxSize(),
            label = "provider model settings"
        ) { targetModel ->
            pageState.SaveableStateProvider(targetModel?.let { "model:$it" } ?: "provider-form") {
                Box(Modifier.fillMaxSize().inertWhen(targetModel != modelSettingsModel || saving)) {
                    if (targetModel == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            saveError?.let {
                Text(
                    "保存失败：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (invalidContextWindowModels.isNotEmpty()) {
                Text(
                    "请返回模型列表修正上下文 tokens 后再保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            PrototypeTextField(
                name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text("名称") }, singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it }
            ) {
                PrototypeTextField(
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

            PrototypeTextField(
                baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(),
                label = { Text("Base URL") }, singleLine = true,
                supportingText = { Text(baseUrlHint(type)) }
            )
            PrototypeTextField(
                apiKey, { apiKey = it }, Modifier.fillMaxWidth(),
                label = { Text("API Key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = { modelMenuExpanded = it }
            ) {
                PrototypeTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("测试连接模型") },
                    supportingText = { Text("仅用于测试连接，不影响聊天、Agent 或子代理的模型选择。") },
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
                            text = { Column { Text(m); com.example.myapplication.ui.chat.ModelCapabilitiesRow(buildConfig(), m) } },
                            onClick = { model = m; modelMenuExpanded = false }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onFetchModels(buildConfig()) },
                    enabled = !fetchingModels && type != ProviderType.CUSTOM && baseUrl.isNotBlank()
                ) {
                    if (fetchingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("获取模型列表")
                    }
                }
                Button(
                    onClick = { onTest(buildConfig()) },
                    enabled = !testing && maxOutputTokensError == null && baseUrl.isNotBlank() && model.isNotBlank()
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试连接")
                    }
                }
            }
            if (modelCandidates.isNotEmpty()) {
                Text(
                    "${modelCandidates.size} 个模型可选",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            fetchError?.let {
                Text(
                    "❌ $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ProviderExpandableSection(
                title = "模型列表",
                description = "点击模型可设置该模型的能力与上下文覆盖。",
                expanded = modelListExpanded,
                onExpandedChange = { modelListExpanded = it }
            ) {
                if (visibleModelIds.isEmpty()) {
                    Text(
                        "获取模型列表，或手动添加模型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleModelIds.forEach { modelId ->
                            ProviderModelRow(
                                modelId = modelId,
                                override = capabilityOverrides[modelId] != null ||
                                    contextWindowOverrides[modelId] != null,
                                discovered = discoveredCapabilities.containsKey(modelId),
                                onClick = { modelSettingsModel = modelId }
                            )
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrototypeTextField(manualModelName, { manualModelName = it }, Modifier.weight(1f),
                    label = { Text("手动添加模型 ID") }, singleLine = true)
                UiTextButton(onClick = {
                    addedModels = (addedModels + manualModelName.trim()).distinct()
                    manualModelName = ""
                }, enabled = manualModelName.isNotBlank() && !saving) { Text("添加") }
            }

            ProviderExpandableSection(
                title = "生成参数",
                description = "留空表示不发送可选参数，保持协议本身的默认行为。",
                expanded = generationExpanded,
                onExpandedChange = { generationExpanded = it }
            ) {
            PrototypeTextField(
                temperature, { temperature = it }, Modifier.fillMaxWidth(),
                label = { Text("温度（可留空）") },
                singleLine = true
            )
            if (type != ProviderType.CUSTOM) {
                PrototypeTextField(
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
            if (type == ProviderType.ANTHROPIC) {
                Text("Claude thinking 协议", style = MaterialTheme.typography.titleSmall)
                ExposedDropdownMenuBox(
                    expanded = anthropicThinkingModeMenuExpanded,
                    onExpandedChange = { anthropicThinkingModeMenuExpanded = it }
                ) {
                    PrototypeTextField(
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
                    if (anthropicProtocol == ReasoningProtocol.ANTHROPIC_MANUAL) {
                        "会话选择思考档位后，此模型使用固定 thinking token 预算。"
                    } else {
                        "会话选择思考档位后，此模型发送 adaptive thinking 与 output_config.effort。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
            ProviderExpandableSection(
                title = "高级连接设置",
                description = "附加请求头和自定义模板仅在保存后用于此配置。",
                expanded = advancedExpanded,
                onExpandedChange = { advancedExpanded = it }
            ) {
            if (type != ProviderType.CUSTOM) {
                PrototypeTextField(
                    modelsUrl, { modelsUrl = it }, Modifier.fillMaxWidth(),
                    label = { Text("模型列表 URL（可选）") }, singleLine = true,
                    supportingText = {
                        Text("留空自动识别；DeepSeek 两种协议均使用 /models。特殊网关可填完整地址。")
                    }
                )
            }
            PrototypeTextField(
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
                PrototypeTextField(
                    template, { template = it }, Modifier.fillMaxWidth(),
                    label = { Text("请求体模板（JSON）") }, minLines = 4, maxLines = 8
                )
                PrototypeTextField(
                    responsePath, { responsePath = it }, Modifier.fillMaxWidth(),
                    label = { Text("非流式响应提取路径，如 $.choices[0].message.content") }, singleLine = true
                )
                PrototypeTextField(
                    streamPath, { streamPath = it }, Modifier.fillMaxWidth(),
                    label = { Text("SSE 行提取路径，如 $.choices[0].delta.content") }, singleLine = true
                )
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
            Spacer(modifier = Modifier.height(24.dp))
        }
                    } else {
                        val selectedOverride = capabilityOverrides[targetModel]
                        ProviderModelSettingsContent(
                            modelId = targetModel,
                            providerType = type,
                            capabilities = selectedOverride
                                ?: discoveredCapabilities[targetModel]
                                ?: ModelCapabilities(),
                            hasManualCapabilityOverride = selectedOverride != null,
                            hasDiscoveredCapabilities = discoveredCapabilities.containsKey(targetModel),
                            contextWindowDraft = contextWindowDrafts[targetModel]
                                ?: contextWindowOverrides[targetModel]?.toString().orEmpty(),
                            saveError = saveError,
                            modifier = Modifier.fillMaxSize().padding(padding),
                            onCapabilitiesChange = { updated ->
                                capabilityOverrides = capabilityOverrides + (targetModel to updated)
                            },
                            onResetCapabilities = {
                                capabilityOverrides = capabilityOverrides - targetModel
                            },
                            onContextWindowChange = { value ->
                                contextWindowOverrides = if (value == null) {
                                    contextWindowOverrides - targetModel
                                } else {
                                    contextWindowOverrides + (targetModel to value)
                                }
                            },
                            onContextWindowDraftChange = { value ->
                                contextWindowDrafts = contextWindowDrafts + (targetModel to value)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderModelRow(
    modelId: String,
    override: Boolean,
    discovered: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    modelId,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        override -> "手动覆盖"
                        discovered -> "接口自动发现"
                        else -> "尚未配置覆盖"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(6.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = "设置 $modelId",
                modifier = Modifier.graphicsLayer { rotationZ = -90f },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderModelSettingsContent(
    modelId: String,
    providerType: ProviderType,
    capabilities: ModelCapabilities,
    hasManualCapabilityOverride: Boolean,
    hasDiscoveredCapabilities: Boolean,
    contextWindowDraft: String,
    saveError: String?,
    modifier: Modifier = Modifier,
    onCapabilitiesChange: (ModelCapabilities) -> Unit,
    onResetCapabilities: () -> Unit,
    onContextWindowChange: (Int?) -> Unit,
    onContextWindowDraftChange: (String) -> Unit
) {
    val parsedContextWindow = contextWindowDraft.trim().toIntOrNull()
    val contextWindowError = when {
        contextWindowDraft.isBlank() || parsedContextWindow != null && parsedContextWindow > 0 -> null
        else -> "请输入大于 0 的整数，留空表示未知。"
    }

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        saveError?.let {
            Text(
                "保存失败：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(modelId, style = MaterialTheme.typography.titleLarge)
        Text(
            "返回供应商页面后，使用顶部的“保存”统一写入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrototypeTextField(
            value = contextWindowDraft,
            onValueChange = { value ->
                onContextWindowDraftChange(value)
                val trimmed = value.trim()
                val parsed = trimmed.toIntOrNull()
                when {
                    trimmed.isBlank() -> onContextWindowChange(null)
                    parsed != null && parsed > 0 -> onContextWindowChange(parsed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("最大上下文 tokens（可选）") },
            supportingText = {
                Text(contextWindowError ?: "留空表示未知，不估算上下文窗口。")
            },
            isError = contextWindowError != null,
            singleLine = true
        )
        if (providerType != ProviderType.CUSTOM) {
            Text("模型能力", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    hasManualCapabilityOverride -> "来源：手动覆盖（仅 $modelId）"
                    hasDiscoveredCapabilities -> "来源：模型列表接口声明；未声明的能力默认关闭，可手动调整。"
                    else -> "模型列表接口未提供可识别的输入能力声明。关闭不代表模型一定不支持，可按供应商文档手动设置。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CapabilityToggleRow("图片", capabilities.image) {
                onCapabilitiesChange(capabilities.copy(image = !capabilities.image))
            }
            CapabilityToggleRow("PDF", capabilities.pdf) {
                onCapabilitiesChange(capabilities.copy(pdf = !capabilities.pdf))
            }
            CapabilityToggleRow(
                "音频（仅 Gemini 原生请求）",
                capabilities.audio,
                enabled = providerType == ProviderType.GEMINI
            ) {
                onCapabilitiesChange(capabilities.copy(audio = !capabilities.audio))
            }
            CapabilityToggleRow(
                "视频（仅 Gemini 原生请求）",
                capabilities.video,
                enabled = providerType == ProviderType.GEMINI
            ) {
                onCapabilitiesChange(capabilities.copy(video = !capabilities.video))
            }
            if (hasManualCapabilityOverride) {
                UiTextButton(onClick = onResetCapabilities) {
                    Text("恢复接口自动能力")
                }
            }
        } else {
            Text(
                "自定义模板不支持附件读取，请改用 OpenAI 兼容、Anthropic 或 Gemini。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
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

@Composable
private fun ProviderExpandableSection(
    title: String,
    description: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "provider section chevron"
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起$title" else "展开$title",
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(240)) + fadeOut(tween(180))
            ) {
                Column(
                    Modifier.fillMaxWidth().inertWhen(!expanded)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
            }
        }
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
