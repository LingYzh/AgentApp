package com.example.myapplication.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.myapplication.ui.theme.ExpressiveTokens
import com.example.myapplication.ui.agents.AgentAvatar
import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.ui.components.ListSelectionBar
import com.example.myapplication.ui.components.rememberListSelection
import com.example.myapplication.ui.theme.AgentTheme
import com.example.myapplication.ui.transfer.ConfigurationTransferHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationsViewModel(private val app: AgentApp) : ViewModel() {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val _agents = MutableStateFlow<List<AgentProfile>>(emptyList())
    val agents = _agents.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private suspend fun refreshData() {
        _conversations.value = app.store.listConversations()
        _agents.value = app.store.loadAgents()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshData()
        }
    }

    fun create(agentId: String?, onCreated: (Conversation) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val c = Conversation(agentId = agentId)
            app.store.saveConversation(c)
            refreshData()
            withContext(Dispatchers.Main) { onCreated(c) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.deleteConversation(id)
            refreshData()
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.loadConversation(id)?.let {
                it.title = title.ifBlank { it.title }
                app.store.saveConversation(it)
            }
            refreshData()
        }
    }

    /** Deletes the selected conversations in one IO task and refreshes the list once. */
    fun deleteSelected(ids: Set<String>) {
        val selectedIds = ids.toSet()
        if (selectedIds.isEmpty() || _busy.value) return

        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var failure: Exception? = null
                try {
                    selectedIds.forEach { app.store.deleteConversation(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = error
                }
                try {
                    // Refresh once even after a partial failure so the UI reflects removals.
                    refreshData()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failure = failure ?: error
                }
                failure?.let { error ->
                    _message.value = "批量删除对话失败：${error.message ?: error.javaClass.simpleName}"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun agentLabel(agentId: String?): String? =
        agentId?.let { id -> _agents.value.firstOrNull { it.id == id }?.let { "${it.emoji} ${it.name}" } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(navController: NavHostController, openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: ConversationsViewModel = viewModel(factory = viewModelFactory {
        initializer { ConversationsViewModel(app) }
    })
    LifecycleStartEffect(Unit) {
        vm.refresh()
        onStopOrDispose { }
    }
    val list by vm.conversations.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    ConfigurationTransferHost(
        kind = TransferKind.CONVERSATIONS,
        transfer = app.configurationTransfer,
        onImportSuccess = { vm.refresh() }
    ) { actions ->
        ConversationsContent(
            conversations = list,
            agents = agents,
            agentLabel = { vm.agentLabel(it) },
            onOpenDrawer = openDrawer,
            onSelectConversation = { navController.safeNavigateDirect(Routes.chat(it)) },
            onCreateConversation = { agentId ->
                vm.create(agentId) { navController.safeNavigateDirect(Routes.chat(it.id)) }
            },
            onRenameConversation = { id, title -> vm.rename(id, title) },
            onDeleteConversation = { vm.delete(it) },
            onDeleteSelected = { vm.deleteSelected(it) },
            onImport = actions.onImport,
            onExportSelected = actions.onExportSelected,
            busy = actions.busy || busy,
            snackbarHostState = snackbar
        )
    }
}

/**
 * 会话列表纯 UI 组件，便于状态解耦与预览
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsContent(
    conversations: List<Conversation>,
    agents: List<AgentProfile>,
    agentLabel: (String?) -> String?,
    onOpenDrawer: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onCreateConversation: (String?) -> Unit,
    onRenameConversation: (id: String, title: String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit = { ids -> ids.forEach(onDeleteConversation) },
    onImport: () -> Unit = {},
    onExportSelected: (Set<String>) -> Unit = {},
    busy: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var showAgentPicker by remember { mutableStateOf(false) }
    val selection = rememberListSelection(conversations.map { it.id }, conversations.associate { it.id to it.title })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("对话") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    TextButton(
                        onClick = {
                            if (selection.active) selection.onExit() else selection.onEnter()
                        },
                        enabled = !busy
                    ) {
                        Text(if (selection.active) "完成" else "管理")
                    }
                }
            )
        },
        bottomBar = {
            if (selection.active) {
                ListSelectionBar(
                    selection = selection,
                    busy = busy,
                    onDelete = onDeleteSelected,
                    onImport = onImport,
                    onExport = onExportSelected
                )
            }
        },
        floatingActionButton = {
            if (!selection.active) {
                FloatingActionButton(onClick = { showAgentPicker = true }) {
                    Icon(Icons.Filled.Add, "新对话")
                }
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "还没有对话记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "点击右下角按钮开启与智能体的第一次对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = ExpressiveTokens.ScreenHorizontalPadding,
                    top = 8.dp,
                    end = ExpressiveTokens.ScreenHorizontalPadding,
                    bottom = if (selection.active) 16.dp else ExpressiveTokens.FabSafeBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val boundAgent = remember(conv.agentId, agents) {
                        conv.agentId?.let { id -> agents.firstOrNull { it.id == id } }
                    }
                    ConversationItem(
                        conv = conv,
                        agent = boundAgent,
                        agentLabel = agentLabel(conv.agentId),
                        selectionMode = selection.active,
                        selected = conv.id in selection.selectedIds,
                        onClick = {
                            if (!busy) {
                                if (selection.active) selection.onToggle(conv.id)
                                else onSelectConversation(conv.id)
                            }
                        },
                        onToggle = { if (!busy) selection.onToggle(conv.id) },
                        onRename = { renameTarget = conv },
                        onDelete = { onDeleteConversation(conv.id) }
                    )
                }
            }
        }
    }

    // 新会话：选择用哪个 Agent 开始
    if (showAgentPicker) {
        AlertDialog(
            onDismissRequest = { showAgentPicker = false },
            title = { Text("选择 Agent 开始对话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AgentPickRow(
                        emoji = "🤖",
                        avatarPath = null,
                        name = "默认 Agent",
                        desc = "通用助手，跟随全局选中模型"
                    ) {
                        showAgentPicker = false
                        onCreateConversation(null)
                    }
                    agents.forEach { agent ->
                        AgentPickRow(
                            emoji = agent.emoji,
                            avatarPath = agent.avatarPath,
                            name = agent.name,
                            desc = agent.description
                        ) {
                            showAgentPicker = false
                            onCreateConversation(agent.id)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAgentPicker = false }) { Text("取消") }
            }
        )
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameConversation(target.id, text)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AgentPickRow(
    emoji: String,
    avatarPath: String? = null,
    name: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AgentAvatar(
                emoji = emoji,
                avatarPath = avatarPath,
                size = 38.dp
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                if (desc.isNotBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conv: Conversation,
    agent: AgentProfile?,
    agentLabel: String?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit = {}
) {
    Card(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() }
                )
                Spacer(Modifier.width(8.dp))
            }
            AgentAvatar(
                emoji = agent?.emoji ?: "🤖",
                avatarPath = agent?.avatarPath,
                size = 42.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conv.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    agentLabel?.let {
                        Text(
                            "  $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                val preview = conv.messages.lastOrNull()?.content?.take(60) ?: "(空对话)"
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(Date(conv.messages.lastOrNull()?.timestamp ?: conv.createdAt))
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "重命名") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
            }
        }
    }
}

@Preview(showBackground = true, name = "Conversations - Light")
@Composable
private fun ConversationsPreviewLight() {
    val dummyAgents = listOf(
        AgentProfile(id = "a1", name = "代码助手", emoji = "💻", description = "专注写代码"),
        AgentProfile(id = "a2", name = "翻译官", emoji = "🌐", description = "精通多国语言")
    )
    val dummyConversations = listOf(
        Conversation(
            id = "1",
            title = "关于 Compose Preview 设计讨论",
            agentId = "a1",
            createdAt = System.currentTimeMillis() - 3600_000,
            messages = mutableListOf(
                ChatMessage(role = "user", content = "如何优雅地为 Android Compose 界面添加预览？"),
                ChatMessage(role = "assistant", content = "推荐将 ViewModel 依赖与纯 UI 展示解耦为 Stateless Composable。")
            )
        ),
        Conversation(
            id = "2",
            title = "英语技术文档翻译",
            agentId = "a2",
            createdAt = System.currentTimeMillis() - 7200_000,
            messages = mutableListOf(
                ChatMessage(role = "user", content = "Translate this paragraph into Chinese please.")
            )
        )
    )
    AgentTheme(themeMode = "light") {
        ConversationsContent(
            conversations = dummyConversations,
            agents = dummyAgents,
            agentLabel = { id -> dummyAgents.firstOrNull { it.id == id }?.let { "${it.emoji} ${it.name}" } },
            onOpenDrawer = {},
            onSelectConversation = {},
            onCreateConversation = {},
            onRenameConversation = { _, _ -> },
            onDeleteConversation = {}
        )
    }
}

@Preview(showBackground = true, name = "Conversations - Dark")
@Composable
private fun ConversationsPreviewDark() {
    val dummyAgents = listOf(
        AgentProfile(id = "a1", name = "代码助手", emoji = "💻", description = "专注写代码")
    )
    val dummyConversations = listOf(
        Conversation(
            id = "1",
            title = "关于 Compose Preview 设计讨论",
            agentId = "a1",
            createdAt = System.currentTimeMillis() - 3600_000,
            messages = mutableListOf(
                ChatMessage(role = "assistant", content = "推荐将 ViewModel 依赖与纯 UI 展示解耦为 Stateless Composable。")
            )
        )
    )
    AgentTheme(themeMode = "dark") {
        ConversationsContent(
            conversations = dummyConversations,
            agents = dummyAgents,
            agentLabel = { "${dummyAgents[0].emoji} ${dummyAgents[0].name}" },
            onOpenDrawer = {},
            onSelectConversation = {},
            onCreateConversation = {},
            onRenameConversation = { _, _ -> },
            onDeleteConversation = {}
        )
    }
}

@Preview(showBackground = true, name = "Conversations - Empty")
@Composable
private fun ConversationsEmptyPreview() {
    AgentTheme(themeMode = "light") {
        ConversationsContent(
            conversations = emptyList(),
            agents = emptyList(),
            agentLabel = { null },
            onOpenDrawer = {},
            onSelectConversation = {},
            onCreateConversation = {},
            onRenameConversation = { _, _ -> },
            onDeleteConversation = {}
        )
    }
}

@Preview(showBackground = true, name = "Conversation Item")
@Composable
private fun ConversationItemPreview() {
    val conv = Conversation(
        id = "preview",
        title = "测试会话项目",
        agentId = "a1",
        createdAt = System.currentTimeMillis(),
        messages = mutableListOf(
            ChatMessage(role = "user", content = "主人，这是一条会话预览消息 nya~")
        )
    )
    AgentTheme(themeMode = "light") {
        ConversationItem(
            conv = conv,
            agent = AgentProfile(id = "a1", name = "专属女仆", emoji = "🐱"),
            agentLabel = "🐱 专属女仆",
            onClick = {},
            onRename = {},
            onDelete = {}
        )
    }
}
