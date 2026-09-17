package com.example.myapplication.ui.providers

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.safeNavigateDirect
import com.example.myapplication.safePopBackStack
import com.example.myapplication.ui.theme.ExpressiveTokens
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.ui.theme.AgentTheme
import com.example.myapplication.provider.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ProvidersViewModel(val app: AgentApp) : ViewModel() {
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
    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError = _fetchError.asStateFlow()

    /** 从供应商 /models 接口拉取模型列表 */
    fun fetchModels(provider: ProviderConfig) {
        if (_fetchingModels.value) return
        _fetchingModels.value = true
        _fetchError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val models = app.modelFetcher.fetchModels(provider)
                _fetchedModels.value = models
                if (models.isEmpty()) _fetchError.value = "接口返回为空"
            } catch (e: Exception) {
                _fetchError.value = e.message ?: "拉取失败"
            }
            _fetchingModels.value = false
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
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val config by vm.config.collectAsStateWithLifecycle()

    ProvidersContent(
        config = config,
        onOpenDrawer = openDrawer,
        onAddProvider = { navController.safeNavigateDirect(Routes.providerEdit("new")) },
        onSelectProvider = { id -> vm.select(id) },
        onEditProvider = { id -> navController.safeNavigateDirect(Routes.providerEdit(id)) },
        onDeleteProvider = { id -> vm.deleteProvider(id) }
    )
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
    onDeleteProvider: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProvider) {
                Icon(Icons.Filled.Add, "添加")
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
                        modifier = Modifier.fillMaxWidth().clickable {
                            onEditProvider(p.id)
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = p.id == config.selectedProviderId,
                                onClick = { onSelectProvider(p.id) }
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
                            IconButton(onClick = { onDeleteProvider(p.id) }) {
                                Icon(Icons.Filled.Delete, "删除")
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
    fetchError: String?,
    onBack: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onTest: (ProviderConfig) -> Unit,
    onFetchModels: (ProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var type by remember { mutableStateOf(initialConfig?.type ?: ProviderType.OPENAI) }
    var baseUrl by remember { mutableStateOf(initialConfig?.baseUrl ?: defaultBaseUrl(type)) }
    var apiKey by remember { mutableStateOf(initialConfig?.apiKey ?: "") }
    var model by remember { mutableStateOf(initialConfig?.model ?: "") }
    var temperature by remember { mutableStateOf(initialConfig?.temperature?.toString() ?: "") }
    var headers by remember {
        mutableStateOf(initialConfig?.extraHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: "")
    }
    var template by remember { mutableStateOf(initialConfig?.customRequestTemplate ?: "") }
    var responsePath by remember { mutableStateOf(initialConfig?.customResponsePath ?: "") }
    var streamPath by remember { mutableStateOf(initialConfig?.customStreamPath ?: "") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val modelCandidates = fetchedModels ?: initialConfig?.models ?: emptyList()

    fun buildConfig() = ProviderConfig(
        id = initialConfig?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim(),
        type = type,
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        temperature = temperature.toFloatOrNull(),
        customRequestTemplate = template,
        customResponsePath = responsePath.trim(),
        customStreamPath = streamPath.trim(),
        extraHeaders = headers.lines()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
            }
            .toMap(),
        models = modelCandidates
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
                    IconButton(onClick = {
                        onSave(buildConfig())
                    }) { Icon(Icons.Filled.Check, "保存") }
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

            OutlinedTextField(
                temperature, { temperature = it }, Modifier.fillMaxWidth(),
                label = { Text("温度（可留空）") }, singleLine = true
            )
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
                    enabled = !testing && baseUrl.isNotBlank() && model.isNotBlank()
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

private fun defaultBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.OPENAI -> "https://api.openai.com/v1"
    ProviderType.ANTHROPIC -> "https://api.anthropic.com"
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com"
    ProviderType.CUSTOM -> ""
}

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
            fetchError = null,
            onBack = {},
            onSave = {},
            onTest = {},
            onFetchModels = {}
        )
    }
}
