package com.example.myapplication.ui.memory

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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myapplication.AgentApp
import com.example.myapplication.data.model.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryViewModel(val app: AgentApp) : ViewModel() {
    private val _memories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memories = _memories.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _memories.value = app.store.listMemories().sortedByDescending { it.updatedAt }
        }
    }

    fun save(id: String?, title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.saveMemory(title, content, id)
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.deleteMemory(id)
            refresh()
        }
    }

    fun read(id: String): String = app.store.readMemory(id).orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: MemoryViewModel = viewModel(factory = viewModelFactory {
        initializer { MemoryViewModel(app) }
    })
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val memories by vm.memories.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<Pair<String?, Boolean>?>(null) } // (id?, open)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null to true }) {
                Icon(Icons.Filled.Add, "添加记忆")
            }
        }
    ) { padding ->
        if (memories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无记忆。Agent 会用 save_memory 工具自动保存重要信息。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(memories, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth().clickable { editTarget = entry.id to true }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    vm.read(entry.id).take(80),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                        .format(Date(entry.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.delete(entry.id) }) {
                                Icon(Icons.Filled.Delete, "删除")
                            }
                        }
                    }
                }
            }
        }
    }

    editTarget?.takeIf { it.second }?.let { (id, _) ->
        var title by remember(id) { mutableStateOf(id?.let { eid -> memories.firstOrNull { it.id == eid }?.title } ?: "") }
        var content by remember(id) { mutableStateOf(id?.let { vm.read(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(if (id == null) "添加记忆" else "编辑记忆") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(),
                        label = { Text("标题") }, singleLine = true)
                    OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(),
                        label = { Text("内容") }, minLines = 4, maxLines = 8)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) vm.save(id, title.trim(), content)
                    editTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("取消") }
            }
        )
    }
}
