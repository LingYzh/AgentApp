package com.example.myapplication.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SmartToy
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.myapplication.ui.theme.ExpressiveTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.AgentTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.safeNavigateDirect
import com.example.myapplication.safePopBackStack
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.ui.agents.AgentAvatar
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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    ChatContent(
        messages = messages,
        title = title,
        agentProfile = agentProfile,
        currentModel = currentModel,
        streaming = streaming,
        toolStatus = toolStatus,
        modelOptions = vm.modelOptions(),
        onBack = { navController.safePopBackStack() },
        onSwitchModel = { providerId, model -> vm.switchModel(providerId, model) },
        onSendMessage = { vm.send(it) },
        onViewFile = { path -> navController.safeNavigateDirect(Routes.fileView(path)) },
        snackbarHostState = snackbar
    )
}

/**
 * 聊天页面纯 UI 组件，支持预览与状态解耦
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    title: String,
    agentProfile: AgentProfile?,
    currentModel: String,
    streaming: Boolean,
    toolStatus: String?,
    modelOptions: List<Pair<ProviderConfig, String>>,
    onBack: () -> Unit,
    onSwitchModel: (providerId: String, model: String) -> Unit,
    onSendMessage: (String) -> Unit,
    onViewFile: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val listState = rememberLazyListState()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            agentProfile?.let { prof ->
                                AgentAvatar(
                                    emoji = prof.emoji,
                                    avatarPath = prof.avatarPath,
                                    size = 32.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    title,
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
                                    Icon(
                                        Icons.Filled.ArrowDropDown, "切换模型",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            modelOptions.forEach { (provider, model) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model)
                                            Text(
                                                provider.name.ifBlank { provider.type.label },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSwitchModel(provider.id, model)
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    if (streaming) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    toolStatus?.let {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                Modifier.padding(end = 8.dp).size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入消息…") },
                            shape = ExpressiveTokens.CardShape,
                            maxLines = 5
                        )
                        IconButton(
                            onClick = {
                                onSendMessage(input)
                                input = ""
                            },
                            enabled = input.isNotBlank() && !streaming,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (input.isNotBlank() && !streaming) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "开始对话吧。Agent 可以生成文件、保存记忆、调用 Skills 和委派子代理。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
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
                        agentProfile = agentProfile,
                        onViewFile = onViewFile
                    )
                }
            }
        }
    }
}

/** 提取工具调用的友好动作名称（无 emoji） */
private fun friendlyToolTitle(toolName: String?): String = when (toolName) {
    Tools.WRITE_FILE -> "写入文件"
    Tools.READ_FILE -> "读取文件"
    Tools.LIST_FILES -> "查看工作区文件"
    Tools.SAVE_MEMORY -> "保存记忆"
    Tools.SEARCH_MEMORY -> "检索记忆"
    Tools.DELETE_MEMORY -> "删除记忆"
    Tools.USE_SKILL -> "调用技能"
    Tools.SAVE_SKILL -> "存储技能"
    Tools.RUN_SUBAGENT -> "委派子代理"
    else -> toolName?.ifBlank { "工具操作" } ?: "工具操作"
}

/** 统一风格的工具对应矢量图标 */
private fun toolIcon(toolName: String?): ImageVector = when (toolName) {
    Tools.WRITE_FILE, Tools.READ_FILE -> Icons.Outlined.Description
    Tools.LIST_FILES -> Icons.Outlined.Folder
    Tools.SAVE_MEMORY, Tools.SEARCH_MEMORY, Tools.DELETE_MEMORY -> Icons.Outlined.Bookmark
    Tools.USE_SKILL, Tools.SAVE_SKILL -> Icons.Outlined.Bolt
    Tools.RUN_SUBAGENT -> Icons.Outlined.SmartToy
    else -> Icons.Outlined.Code
}

/** 从工具调用参数中提取文件路径（write_file / read_file 可预览） */
private fun extractFilePath(argumentsJson: String): String? =
    runCatching {
        ProviderJson.parseToJsonElement(argumentsJson).jsonObject["path"]
            ?.jsonPrimitive?.content
    }.getOrNull()

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    agentProfile: AgentProfile?,
    onViewFile: (String) -> Unit
) {
    when (msg.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Card(
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                SelectionContainer {
                    Text(
                        msg.content,
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        "tool" -> ToolMessageBlock(msg)
        else -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            AgentAvatar(
                emoji = agentProfile?.emoji ?: "🤖",
                avatarPath = agentProfile?.avatarPath,
                size = 32.dp,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
            )
            Column(Modifier.weight(1f, fill = false)) {
                if (msg.thinking.isNotBlank()) {
                    ThinkingBlock(msg.thinking)
                }
                if (msg.content.isNotBlank() || msg.toolCalls.isNotEmpty()) {
                    Card(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.widthIn(max = 340.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            if (msg.content.isNotBlank()) {
                                SelectionContainer { Text(msg.content) }
                            }
                            msg.toolCalls.forEach { tc ->
                                var callExpanded by remember { mutableStateOf(false) }
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                                        .clickable { callExpanded = !callExpanded }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = toolIcon(tc.name),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "调用 ${friendlyToolTitle(tc.name)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (tc.name == Tools.WRITE_FILE || tc.name == Tools.READ_FILE) {
                                            extractFilePath(tc.argumentsJson)?.let { path ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .padding(end = 4.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable { onViewFile(path) }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.FileOpen,
                                                        contentDescription = "查看文件",
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(3.dp))
                                                    Text(
                                                        text = "查看",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                            }
                                        }
                                        Icon(
                                            imageVector = if (callExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    AnimatedVisibility(visible = callExpanded) {
                                        SelectionContainer {
                                            Text(
                                                text = tc.argumentsJson,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 4.dp)
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

/**
 * 极简思考过程呈现组件（主流移动端 AI 风格，无 Card 容器）
 */
@Composable
private fun ThinkingBlock(thinking: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (expanded) "收起思考过程" else "思考过程",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 6.dp, bottom = 4.dp)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(1.dp)
                        )
                )
                Spacer(Modifier.width(10.dp))
                SelectionContainer {
                    Text(
                        text = thinking,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 极简工具调用结果呈现组件（主流移动端 Action Step 风格，无 Card 容器）
 */
@Composable
private fun ToolMessageBlock(msg: ChatMessage) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = toolIcon(msg.toolName),
                    contentDescription = null,
                    tint = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = friendlyToolTitle(msg.toolName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (msg.isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                    contentDescription = if (msg.isError) "失败" else "完成",
                    tint = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = msg.content.take(4000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Chat - Light")
@Composable
private fun ChatScreenPreviewLight() {
    val dummyAgent = AgentProfile(
        id = "agent_maid",
        name = "专属猫娘女仆",
        emoji = "🐱",
        description = "主人的贴身女仆"
    )
    val dummyMessages = listOf(
        ChatMessage(
            role = "user",
            content = "请帮我写一个文件 hello.txt 并保存在工作区。"
        ),
        ChatMessage(
            role = "assistant",
            thinking = "主人需要创建 hello.txt 文件。我将调用 write_file 工具，将问候写入文件中。",
            content = "遵命主人~ 我这就为您创建文件并写好内容！",
            toolCalls = listOf(
                com.example.myapplication.data.model.ToolCallInfo(
                    id = "call_1",
                    name = "write_file",
                    argumentsJson = """{"path":"hello.txt","content":"Hello Master! Nya~❤"}"""
                )
            )
        ),
        ChatMessage(
            role = "tool",
            toolName = "write_file",
            content = """{"status":"ok","bytesWritten":24}"""
        ),
        ChatMessage(
            role = "assistant",
            content = "报告主人，文件 hello.txt 已经为您写入完成啦！您可以点击上方查看或者在工作区里打开哦~"
        )
    )

    AgentTheme(themeMode = "light") {
        ChatContent(
            messages = dummyMessages,
            title = "女仆工作区对话",
            agentProfile = dummyAgent,
            currentModel = "claude-3-7-sonnet",
            streaming = false,
            toolStatus = null,
            modelOptions = listOf(
                ProviderConfig(name = "Anthropic") to "claude-3-7-sonnet",
                ProviderConfig(name = "OpenAI") to "gpt-4o"
            ),
            onBack = {},
            onSwitchModel = { _, _ -> },
            onSendMessage = {},
            onViewFile = {}
        )
    }
}

@Preview(showBackground = true, name = "Chat - Dark & Streaming")
@Composable
private fun ChatScreenPreviewDark() {
    val dummyAgent = AgentProfile(
        id = "agent_maid",
        name = "专属猫娘女仆",
        emoji = "🐱",
        description = "主人的贴身女仆"
    )
    val dummyMessages = listOf(
        ChatMessage(
            role = "user",
            content = "检查一下当前工作区状态"
        ),
        ChatMessage(
            role = "assistant",
            thinking = "正在为主人检索全部工作区文件列表与系统状态...",
            content = "正在执行指令中..."
        )
    )

    AgentTheme(themeMode = "dark") {
        ChatContent(
            messages = dummyMessages,
            title = "女仆工作区对话",
            agentProfile = dummyAgent,
            currentModel = "claude-3-7-sonnet",
            streaming = true,
            toolStatus = "正在调用 list_dir 检索文件...",
            modelOptions = emptyList(),
            onBack = {},
            onSwitchModel = { _, _ -> },
            onSendMessage = {},
            onViewFile = {}
        )
    }
}

@Preview(showBackground = true, name = "Chat - Empty")
@Composable
private fun ChatScreenEmptyPreview() {
    AgentTheme(themeMode = "light") {
        ChatContent(
            messages = emptyList(),
            title = "新对话",
            agentProfile = null,
            currentModel = "gpt-4o",
            streaming = false,
            toolStatus = null,
            modelOptions = emptyList(),
            onBack = {},
            onSwitchModel = { _, _ -> },
            onSendMessage = {},
            onViewFile = {}
        )
    }
}
