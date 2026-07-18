package com.example.myapplication.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.agent.AgentEngine
import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.AgentProfile
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ModelResolver
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.provider.ProviderJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChatViewModel(
    private val app: AgentApp,
    private val conversationId: String
) : ViewModel() {

    private var conversation: Conversation? = null
    private var agents: List<AgentProfile> = emptyList()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _title = MutableStateFlow("对话")
    val title = _title.asStateFlow()

    private val _agentProfile = MutableStateFlow<AgentProfile?>(null)
    val agentProfile = _agentProfile.asStateFlow()

    private val _currentModel = MutableStateFlow("")
    val currentModel = _currentModel.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming = _streaming.asStateFlow()

    private val _toolStatus = MutableStateFlow<String?>(null)
    val toolStatus = _toolStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            agents = app.store.loadAgents()
            val conv = app.store.loadConversation(conversationId)
            if (conv != null) {
                conversation = conv
                _messages.value = conv.messages.toList()
                _title.value = conv.title
                _agentProfile.value = conv.agentId?.let { id -> agents.firstOrNull { it.id == id } }
                refreshModelLabel()
            } else {
                _error.value = "对话不存在"
            }
        }
    }

    private fun refreshModelLabel() {
        val conv = conversation ?: return
        val resolved = ModelResolver.resolve(conv, app.store.loadConfig(), agents)
        _currentModel.value = resolved?.model ?: ""
    }

    /** 模型可选项：(provider, model) 对 */
    fun modelOptions(): List<Pair<ProviderConfig, String>> =
        app.store.loadConfig().providers.flatMap { p ->
            (p.models.ifEmpty { listOf(p.model) }).filter { it.isNotBlank() }.map { p to it }
        }

    fun switchModel(providerId: String, model: String) {
        val conv = conversation ?: return
        conv.providerIdOverride = providerId
        conv.modelOverride = model
        viewModelScope.launch(Dispatchers.IO) {
            app.store.saveConversation(conv)
        }
        _currentModel.value = model
    }

    fun send(text: String) {
        if (text.isBlank() || _streaming.value) return
        val conv = conversation ?: run {
            _error.value = "对话尚未加载完成"
            return
        }
        val appConfig = app.store.loadConfig()
        val resolved = ModelResolver.resolve(conv, appConfig, agents)
        if (resolved == null) {
            _error.value = "请先在「模型配置」中添加并选择一个模型"
            return
        }
        val userMsg = ChatMessage(role = "user", content = text.trim())
        conv.messages += userMsg
        if (conv.title == "新对话") {
            conv.title = text.trim().take(24)
            _title.value = conv.title
        }
        app.store.saveConversation(conv)
        _messages.value = conv.messages.toList()
        _streaming.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val engine = app.newAgentEngine(onSubagentStatus = { _toolStatus.value = it })
                engine.run(
                    conversation = conv,
                    config = resolved,
                    maxLoops = appConfig.maxAgentLoops,
                    agentProfile = _agentProfile.value,
                    callbacks = AgentEngine.Callbacks(
                        onMessageAdded = { _messages.value = _messages.value + it },
                        onMessageUpdated = { updated ->
                            _messages.value = _messages.value.map {
                                if (it.id == updated.id) updated else it
                            }
                        },
                        onToolStatus = { _toolStatus.value = it }
                    )
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "发送失败"
            } finally {
                _streaming.value = false
                _toolStatus.value = null
                _messages.value = conv.messages.toList()
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavHostController, conversationId: String) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: ChatViewModel = viewModel(
        key = "chat-$conversationId",
        factory = viewModelFactory { initializer { ChatViewModel(app, conversationId) } }
    )
    val messages by vm.messages.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val agentProfile by vm.agentProfile.collectAsStateWithLifecycle()
    val currentModel by vm.currentModel.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val toolStatus by vm.toolStatus.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    // 仅当用户已在底部附近时才跟随流式滚动，避免打断翻看历史
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisible >= messages.size - 2) {
            listState.scrollToItem(messages.size - 1)
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            (agentProfile?.let { "${it.emoji} " } ?: "") + title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                        // 会话内模型切换（只影响本会话）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !streaming) {
                                modelMenuExpanded = true
                            }
                        ) {
                            Text(
                                currentModel.ifBlank { "未配置模型" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(Icons.Filled.ArrowDropDown, "切换模型",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            vm.modelOptions().forEach { (provider, model) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model)
                                            Text(provider.name.ifBlank { provider.type.label },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        vm.switchModel(provider.id, model)
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                if (streaming) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                toolStatus?.let {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入消息…") },
                        maxLines = 5
                    )
                    IconButton(
                        onClick = {
                            vm.send(input)
                            input = ""
                        },
                        enabled = input.isNotBlank() && !streaming
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "发送")
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("开始对话吧。Agent 可以生成文件、保存记忆、调用 Skills 和委派子代理。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        onViewFile = { path -> navController.navigate(Routes.fileView(path)) }
                    )
                }
            }
        }
    }
}

/** 从工具调用参数中提取文件路径（write_file / read_file 可预览） */
private fun extractFilePath(argumentsJson: String): String? =
    runCatching {
        ProviderJson.parseToJsonElement(argumentsJson).jsonObject["path"]
            ?.jsonPrimitive?.content
    }.getOrNull()

@Composable
private fun MessageBubble(msg: ChatMessage, onViewFile: (String) -> Unit) {
    when (msg.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                SelectionContainer {
                    Text(msg.content, Modifier.padding(12.dp))
                }
            }
        }
        "tool" -> ToolMessageCard(msg)
        else -> Column(Modifier.fillMaxWidth()) {
            if (msg.thinking.isNotBlank()) {
                ThinkingCard(msg.thinking)
            }
            if (msg.content.isNotBlank() || msg.toolCalls.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (msg.content.isNotBlank()) {
                            SelectionContainer { Text(msg.content) }
                        }
                        msg.toolCalls.forEach { tc ->
                            Spacer(Modifier.padding(top = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Terminal, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    " 调用 ${tc.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (tc.name == Tools.WRITE_FILE || tc.name == Tools.READ_FILE) {
                                    extractFilePath(tc.argumentsJson)?.let { path ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 8.dp)
                                                .clickable { onViewFile(path) }
                                        ) {
                                            Icon(Icons.Filled.FileOpen, "查看文件",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(14.dp))
                                            Text(
                                                " 查看",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.tertiary
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
}

@Composable
private fun ThinkingCard(thinking: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.widthIn(max = 340.dp).padding(bottom = 4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Text(
                    "💭 思考过程",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(expanded) {
                SelectionContainer {
                    Text(
                        thinking,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolMessageCard(msg: ChatMessage) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (msg.isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(Icons.Filled.Terminal, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    " ${msg.toolName ?: "tool"} 结果",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(expanded) {
                SelectionContainer {
                    Text(
                        msg.content.take(4000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
