package com.example.myapplication.ui.chat

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.myapplication.data.model.Conversation
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

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _conversations.value = app.store.listConversations()
            _agents.value = app.store.loadAgents()
        }
    }

    fun create(agentId: String?, onCreated: (Conversation) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val c = Conversation(agentId = agentId)
            app.store.saveConversation(c)
            refresh()
            withContext(Dispatchers.Main) { onCreated(c) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.deleteConversation(id)
            refresh()
        }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.loadConversation(id)?.let {
                it.title = title.ifBlank { it.title }
                app.store.saveConversation(it)
            }
            refresh()
        }
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
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val list by vm.conversations.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var showAgentPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAgentPicker = true }) {
                Icon(Icons.Filled.Add, "新对话")
            }
        }
    ) { padding ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有对话，点右下角开始", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { conv ->
                    ConversationItem(
                        conv = conv,
                        agentLabel = vm.agentLabel(conv.agentId),
                        onClick = { navController.navigate(Routes.chat(conv.id)) },
                        onRename = { renameTarget = conv },
                        onDelete = { vm.delete(conv.id) }
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
                        emoji = "🤖", name = "默认 Agent",
                        desc = "通用助手，跟随全局选中模型"
                    ) {
                        showAgentPicker = false
                        vm.create(null) { navController.navigate(Routes.chat(it.id)) }
                    }
                    agents.forEach { agent ->
                        AgentPickRow(agent.emoji, agent.name, agent.description) {
                            showAgentPicker = false
                            vm.create(agent.id) { navController.navigate(Routes.chat(it.id)) }
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
                    vm.rename(target.id, text)
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
private fun AgentPickRow(emoji: String, name: String, desc: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.padding(start = 12.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                if (desc.isNotBlank()) {
                    Text(desc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conv: Conversation,
    agentLabel: String?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conv.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    agentLabel?.let {
                        Text(
                            "  $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                val preview = conv.messages.lastOrNull()?.content?.take(60) ?: "(空对话)"
                Text(preview, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(Date(conv.messages.lastOrNull()?.timestamp ?: conv.createdAt))
                Text(time, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "重命名") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
        }
    }
}
