package com.example.myapplication.ui.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.data.model.AgentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AgentsViewModel(val app: AgentApp) : ViewModel() {
    private val _agents = MutableStateFlow<List<AgentProfile>>(emptyList())
    val agents = _agents.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _agents.value = app.store.loadAgents()
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(navController: NavHostController, openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: AgentsViewModel = viewModel(factory = viewModelFactory {
        initializer { AgentsViewModel(app) }
    })
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val agents by vm.agents.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.agentEdit("new")) }) {
                Icon(Icons.Filled.Add, "新建 Agent")
            }
        }
    ) { padding ->
        if (agents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无 Agent。创建带独立系统提示词和默认模型的 Agent，新会话时可选择。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(agents, key = { it.id }) { agent ->
                    Card(Modifier.fillMaxWidth().clickable {
                        navController.navigate(Routes.agentEdit(agent.id))
                    }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(agent.emoji, style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(agent.name, style = MaterialTheme.typography.titleMedium)
                                if (agent.description.isNotBlank()) {
                                    Text(agent.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                val modelLabel = agent.model?.let { m ->
                                    val p = agent.providerId?.let { id ->
                                        app.store.loadConfig().providers.firstOrNull { it.id == id }
                                    }
                                    "${p?.name?.ifBlank { p.model } ?: ""} / $m"
                                } ?: "跟随全局选中模型"
                                Text(modelLabel, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.delete(agent.id) }) {
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

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "🤖") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(existing?.systemPrompt ?: "") }
    var providerId by remember { mutableStateOf(existing?.providerId) }
    var model by remember { mutableStateOf(existing?.model) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val selectedProvider = providers.firstOrNull { it.id == providerId }
    val providerLabel = selectedProvider?.let { "${it.name.ifBlank { it.model }}（${it.type.label}）" }
        ?: "跟随全局选中"
    val modelOptions = selectedProvider?.models ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建 Agent" else "编辑 Agent") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (name.isNotBlank()) {
                            vm.save(
                                AgentProfile(
                                    id = existing?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    emoji = emoji.trim().ifBlank { "🤖" },
                                    description = description.trim(),
                                    systemPrompt = systemPrompt,
                                    providerId = providerId,
                                    model = model
                                )
                            )
                            navController.popBackStack()
                        }
                    }) { Icon(Icons.Filled.Check, "保存") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(emoji, { emoji = it }, Modifier.weight(1f),
                    label = { Text("图标") }, singleLine = true)
                OutlinedTextField(name, { name = it }, Modifier.weight(3f),
                    label = { Text("名称") }, singleLine = true)
            }
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(),
                label = { Text("描述") }, singleLine = true)

            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = providerLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("默认模型配置") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("跟随全局选中") },
                        onClick = { providerId = null; model = null; providerMenuExpanded = false }
                    )
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name.ifBlank { p.model }}（${p.type.label}）") },
                            onClick = {
                                providerId = p.id
                                model = p.models.firstOrNull() ?: p.model.ifBlank { null }
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
                    OutlinedTextField(
                        value = model ?: selectedProvider.model,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("默认模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        (modelOptions.ifEmpty { listOf(selectedProvider.model) }).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { model = m; modelMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                systemPrompt, { systemPrompt = it }, Modifier.fillMaxWidth(),
                label = { Text("系统提示词（定义这个 Agent 的人设与行为）") },
                minLines = 8, maxLines = 16
            )
        }
    }
}
