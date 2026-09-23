package com.example.myapplication.ui.chat

import androidx.compose.material.icons.outlined.Edit

import com.example.myapplication.ui.components.UiScaffold
import com.example.myapplication.ui.components.MarkdownContent
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.AccountTree
import com.example.myapplication.agent.PermissionSession
import com.example.myapplication.agent.ConversationContext
import com.example.myapplication.agent.ContextCompactor
import com.example.myapplication.agent.ContextWindows
import com.example.myapplication.data.model.ContextOverview
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.model.NewChatDefaults
import com.example.myapplication.data.model.defaultEffort
import com.example.myapplication.data.model.sessionEffort
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.store.ConversationEdits
import kotlinx.coroutines.delay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VerifiedUser
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
import com.example.myapplication.ui.components.TopFeedbackHost
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.AgentTheme
import androidx.lifecycle.createSavedStateHandle
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
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ModelResolver
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.ReasoningSupport
import com.example.myapplication.data.model.reasoningSupportFor
import com.example.myapplication.provider.ProviderJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChatViewModel(
    private val app: AgentApp,
    initialConversationId: String?,
    private val sessionKey: String = initialConversationId.orEmpty(),
    draftAgentId: String? = null,
    restored: ChatUiState? = null
) : ViewModel() {

    private var conversationId: String = restored?.conversationId ?: initialConversationId.orEmpty()
    private var isDraft = conversationId.isEmpty()
    private val _committedId = MutableStateFlow<String?>(restored?.conversationId ?: initialConversationId)
    val committedId = _committedId.asStateFlow()
    var input by mutableStateOf(restored?.input.orEmpty())
    private var conversation: Conversation? = restored?.draft

    fun uiSnapshot() = ChatUiState(
        draft = conversation?.takeIf { isDraft }?.copy(messages = mutableListOf()),
        conversationId = _committedId.value,
        input = input,
        attachments = _attachments.value
    )
    private var permissionSession: PermissionSession? = null
    private val _permissionMode = MutableStateFlow(PermissionMode.ACCEPT_EDIT)
    val permissionMode = _permissionMode.asStateFlow()
    private val _plan = MutableStateFlow<String?>(null)
    val plan = _plan.asStateFlow()
    private val _scope = MutableStateFlow<List<String>>(emptyList())
    val fileScope = _scope.asStateFlow()
    private val _workingDirectory = MutableStateFlow<String?>(null)
    val workingDirectory = _workingDirectory.asStateFlow()
    private var agents: List<AgentProfile> = emptyList()
    private var generationJob: Job? = null
    private var modelSaveJob: Job? = null
    private var compactJob: Job? = null
    private var contextRefreshJob: Job? = null
    val selectedProviderId = MutableStateFlow<String?>(null)
    private var currentResolvedModel: ProviderConfig? = null
    private var observationJob: Job? = null
    private val _attachments = MutableStateFlow<List<MessageAttachment>>(restored?.attachments.orEmpty())
    val attachments = _attachments.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _children = MutableStateFlow<List<Conversation>>(emptyList())
    val children = _children.asStateFlow()
    private val _childSnapshot = MutableStateFlow<Conversation?>(null)
    val childSnapshot = _childSnapshot.asStateFlow()
    private val _stopRequested = MutableStateFlow(false)
    val stopRequested = _stopRequested.asStateFlow()
    private val _isChild = MutableStateFlow(false)
    val isChild = _isChild.asStateFlow()
    private val _sendRevision = MutableStateFlow(0)
    val sendRevision = _sendRevision.asStateFlow()
    private val _modelOptions = MutableStateFlow<List<Pair<ProviderConfig, String>>>(emptyList())
    val modelOptions = _modelOptions.asStateFlow()

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
    private val _historyBusy = MutableStateFlow(false)
    val historyBusy = _historyBusy.asStateFlow()
    private val _contextOverview = MutableStateFlow<ContextOverview?>(null)
    val contextOverview = _contextOverview.asStateFlow()
    private val _reasoningSupport = MutableStateFlow<ReasoningSupport?>(null)
    val reasoningSupport = _reasoningSupport.asStateFlow()
    private val _reasoningEffortOverride = MutableStateFlow<ReasoningEffort?>(null)
    val reasoningEffortOverride = _reasoningEffortOverride.asStateFlow()
    private val _compacting = MutableStateFlow(false)
    val compacting = _compacting.asStateFlow()
    private val _compactionProgress = MutableStateFlow<String?>(null)
    val compactionProgress = _compactionProgress.asStateFlow()

    private val _toolStatus = MutableStateFlow<String?>(null)
    val toolStatus = _toolStatus.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch {
            val (loadedAgents, conv, config) = withContext(Dispatchers.IO) {
                val savedAgents = app.store.loadAgents()
                val savedConfig = app.store.loadConfig()
                val loaded = if (isDraft) app.store.committedDraft(sessionKey)
                    ?: restored?.draft ?: app.store.loadNewChatDefaults().draft(savedConfig, savedAgents, draftAgentId)
                else app.store.loadConversation(conversationId)
                if (loaded != null && loaded.id.isNotEmpty() && loaded.parentConversationId == null && ConversationContext.recoverRejectedAttachments(loaded)) {
                    app.store.saveConversation(loaded)
                }
                // Load preferences on IO even for restored drafts before synchronous updates.
                app.store.loadNewChatDefaults()
                Triple(savedAgents, loaded, savedConfig)
            }
            agents = loadedAgents
            _modelOptions.value = config.providers.flatMap { p ->
                p.models.filter { it.isNotBlank() }.distinct().map { p to it }
            }
            if (conv != null) {
                conversation = conv
                if (conv.id.isNotEmpty()) {
                    if (isDraft) {
                        input = ""
                        _attachments.value = emptyList()
                    }
                    conversationId = conv.id
                    isDraft = false
                    _committedId.value = conv.id
                    permissionSession = PermissionSession(app.store, conv, app.permissionCoordinator)
                }
                _permissionMode.value = conv.permissionMode
                _scope.value = conv.allowedDirectories
                _workingDirectory.value = conv.workingDirectory
                _isChild.value = conv.parentConversationId != null
                if (_isChild.value) _childSnapshot.value = conv
                _messages.value = conv.messages.toList()
                _title.value = conv.title
                _agentProfile.value = conv.agentId?.let { id -> agents.firstOrNull { it.id == id } }
                val resolved = ModelResolver.resolve(conv, config, agents)
                updateResolvedModel(resolved)
                if (isDraft && restored?.draft == null && draftAgentId != null) rememberDraftDefaults()
                refreshContextOverview(resolved = resolved)
            } else {
                _error.value = "对话不存在"
            }
        }
    }

    /** Only a draft can change its agent; input and attachment ownership remain unchanged. */
    fun selectDraftAgent(profile: AgentProfile?) {
        val draft = conversation ?: return
        if (draft.id.isNotEmpty() || _streaming.value || _historyBusy.value) return
        conversation = draft.copy(agentId = profile?.id)
        _agentProfile.value = profile
        rememberDraftDefaults()
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { app.store.loadConfig() }
            conversation?.let { updateResolvedModel(ModelResolver.resolve(it, config, agents)) }
            refreshContextOverview()
        }
    }

    fun switchModel(providerId: String, model: String) {
        if (_streaming.value || _historyBusy.value || _compacting.value || _isChild.value) return
        val conv = conversation ?: return
        conv.providerIdOverride = providerId
        conv.modelOverride = model
        val selected = _modelOptions.value.firstOrNull { it.first.id == providerId && it.second == model }
        val resolved = selected?.first?.copy(model = model)
        conv.reasoningEffortOverride = resolved?.sessionEffort(
            conv.reasoningEffortOverride ?: app.store.loadNewChatDefaults().reasoningEffort
        )
        rememberDraftDefaults()
        val snapshot = conv.copy(messages = conv.messages.toMutableList())
        val previousSave = modelSaveJob
        modelSaveJob = viewModelScope.launch {
            previousSave?.join()
            try {
                withContext(Dispatchers.IO) { if (snapshot.id.isNotEmpty()) app.store.saveConversation(snapshot) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _error.value = error.message ?: "模型选择保存失败"
            }
        }
        _currentModel.value = model
        updateResolvedModel(resolved)
        refreshContextOverview(resolved = resolved)
    }

    /** An in-flight request keeps its current value; this applies to its next model request. */
    fun updateReasoningEffort(effort: ReasoningEffort?) {
        if (_isChild.value || _compacting.value || _historyBusy.value) return
        if (effort != null && effort !in _reasoningSupport.value?.efforts.orEmpty()) return
        val conv = conversation ?: return
        val selected = effort ?: _reasoningSupport.value?.defaultEffort()
        conv.reasoningEffortOverride = selected
        _reasoningEffortOverride.value = selected
        rememberDraftDefaults()
        // AgentEngine saves this volatile field with its final conversation save. Encoding the
        // mutable live conversation during a stream can otherwise race the provider callbacks.
        if (_streaming.value) return
        val snapshot = conv.copy(messages = conv.messages.toMutableList())
        val previousSave = modelSaveJob
        modelSaveJob = viewModelScope.launch {
            previousSave?.join()
            try {
                withContext(Dispatchers.IO) { if (snapshot.id.isNotEmpty()) app.store.saveConversation(snapshot) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _error.value = error.message ?: "思考强度保存失败"
            }
        }
    }

    fun compactContext() {
        if (isDraft || _isChild.value || _streaming.value || _historyBusy.value || _compacting.value) return
        val conv = conversation ?: return
        _compacting.value = true
        _historyBusy.value = true
        _compactionProgress.value = "准备上下文…"
        compactJob = viewModelScope.launch {
            try {
                modelSaveJob?.join()
                val appConfig = withContext(Dispatchers.IO) { app.store.loadConfig() }
                val resolved = ModelResolver.resolve(conv, appConfig, agents)
                    ?: error("请先配置模型后再压缩上下文")
                val snapshot = conv.copy(messages = conv.messages.toMutableList())
                val result = withContext(Dispatchers.IO) {
                    ContextCompactor(app.store, app.providerFactory).compact(snapshot, resolved) { progress ->
                        _compactionProgress.value = progress
                    }
                }
                currentCoroutineContext().ensureActive()
                val persisted = conv.copy(
                    messages = conv.messages.toMutableList(),
                    contextCompaction = result
                )
                val committedOverview = withContext(Dispatchers.IO) {
                    ContextWindows.overview(persisted, resolved, _agentProfile.value)
                }
                currentCoroutineContext().ensureActive()
                // Once disk commit begins it must also update the live object, even if the
                // user tapped cancel in the tiny interval after the provider finished.
                withContext(NonCancellable + Dispatchers.IO) {
                    app.store.saveConversation(persisted)
                    conv.contextCompaction = result
                    _contextOverview.value = committedOverview
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _error.value = error.message ?: "压缩上下文失败"
            } finally {
                _compactionProgress.value = null
                _compacting.value = false
                _historyBusy.value = false
            }
        }
    }

    fun cancelCompaction() {
        compactJob?.cancel()
    }

    fun currentFilePath(path: String): String? = try {
        val current = conversation ?: error("会话尚未加载")
        PermissionSession(app.store, current, app.permissionCoordinator).readableFile(path).canonicalPath
    } catch (error: Exception) {
        _error.value = error.message ?: "无法打开文件"
        null
    }

    private fun rememberDraftDefaults() {
        val draft = conversation?.takeIf { isDraft && it.id.isEmpty() } ?: return
        val defaults = NewChatDefaults.fromDraft(draft, app.store.loadNewChatDefaults())
        val write = app.rememberNewChatDefaults(defaults)
        viewModelScope.launch {
            try { write.await() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = error.message ?: "新会话默认设置保存失败" }
        }
    }

    private fun updateResolvedModel(resolved: ProviderConfig?) {
        conversation?.let { conv ->
            if (resolved != null) conv.reasoningEffortOverride = resolved.sessionEffort(conv.reasoningEffortOverride)
        }
        currentResolvedModel = resolved
        selectedProviderId.value = resolved?.id
        _currentModel.value = resolved?.model.orEmpty()
        _reasoningSupport.value = resolved?.let {
            val support = reasoningSupportFor(it.type, it.model)
            if (it.type == com.example.myapplication.data.model.ProviderType.ANTHROPIC) {
                support.copy(protocol = com.example.myapplication.data.model.anthropicThinkingProtocol(it.model, it.anthropicThinkingMode))
            } else support
        }
        _reasoningEffortOverride.value = conversation?.reasoningEffortOverride
    }

    private fun refreshContextOverview(
        conversationSnapshot: Conversation? = conversation,
        resolved: ProviderConfig? = currentResolvedModel
    ) {
        val source = conversationSnapshot ?: return
        val snapshot = source.copy(messages = source.messages.toMutableList())
        val config = resolved ?: return
        contextRefreshJob?.cancel()
        contextRefreshJob = viewModelScope.launch {
            val overview = withContext(Dispatchers.IO) {
                ContextWindows.overview(snapshot, config, _agentProfile.value)
            }
            _contextOverview.value = overview
        }
    }

    /** At most one full estimate per 750 ms while stream callbacks are changing messages. */
    private fun scheduleContextOverviewRefresh() {
        if (contextRefreshJob?.isActive == true) return
        contextRefreshJob = viewModelScope.launch {
            delay(750)
            val source = conversation ?: return@launch
            // Provider callbacks mutate Conversation on IO. The UI flow is an immutable list
            // snapshot, so use it for the throttled estimate instead of iterating that live list.
            val snapshot = source.copy(messages = _messages.value.toMutableList())
            val config = currentResolvedModel ?: return@launch
            val overview = withContext(Dispatchers.IO) {
                ContextWindows.overview(snapshot, config, _agentProfile.value)
            }
            _contextOverview.value = overview
        }
    }

    fun observeChildren(): Job {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { app.store.loadConfig() }
            agents = withContext(Dispatchers.IO) { app.store.loadAgents() }
            _modelOptions.value = config.providers.flatMap { provider ->
                provider.models.filter { it.isNotBlank() }.distinct().map { provider to it }
            }
            conversation?.let { updateResolvedModel(ModelResolver.resolve(it, config, agents)); refreshContextOverview() }
            while (true) {
                if (isDraft) { delay(1200); continue }
                if (_isChild.value) {
                    val snapshot = withContext(Dispatchers.IO) { app.store.loadConversation(conversationId) }
                    if (snapshot != null) {
                        _childSnapshot.value = snapshot
                        _messages.value = snapshot.messages.toList()
                        refreshContextOverview(conversationSnapshot = snapshot)
                    }
                }
                _children.value = withContext(Dispatchers.IO) {
                    app.store.listConversations().filter { it.parentConversationId == conversationId }
                }
                _permissionMode.value = conversation?.permissionMode ?: PermissionMode.ACCEPT_EDIT
                _plan.value = withContext(Dispatchers.IO) { runCatching { permissionSession?.readPlan() }.getOrNull() }
                delay(1200)
            }
        }
        return requireNotNull(observationJob)
    }

    suspend fun updatePermissions(mode: PermissionMode, directories: List<String>, workingDirectory: String?): String? {
        if (_isChild.value || _streaming.value || _historyBusy.value) return "当前会话正在处理任务，请稍后再调整"
        val conv = conversation ?: return "会话尚未加载"
        _historyBusy.value = true
        return try {
            // Publish only after the complete settings snapshot is saved; cancellation cannot
            // leave the model, visible controls and disk with different directory policies.
            withContext(NonCancellable) {
                val proposed = withContext(Dispatchers.IO) {
                    val canonical = directories.map { directory ->
                        val file = java.io.File(directory)
                        require(file.isAbsolute && file.isDirectory && file.canRead()) { "额外目录不可访问：$directory" }
                        file.canonicalPath
                    }.distinct()
                    val next = conv.copy(allowedDirectories = canonical, workingDirectory = workingDirectory,
                        permissionMode = mode, messages = conv.messages.toMutableList())
                    next.workingDirectory = PermissionSession(app.store, next, app.permissionCoordinator)
                        .files.validateWorkingDirectory(workingDirectory)
                    if (next.id.isNotEmpty()) app.store.saveConversation(next)
                    next
                }
                conv.allowedDirectories = proposed.allowedDirectories
                conv.workingDirectory = proposed.workingDirectory
                conv.permissionMode = mode
                _permissionMode.value = mode
                _scope.value = conv.allowedDirectories
                _workingDirectory.value = conv.workingDirectory
                rememberDraftDefaults()
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error.message ?: "权限保存失败"
        } finally {
            _historyBusy.value = false
        }
    }
    suspend fun renameConversation(title: String): String? {
        val name = title.trim()
        if (name.isEmpty()) return "请输入会话标题"
        if (_isChild.value || _streaming.value || _historyBusy.value || isDraft) return "请在会话空闲时修改标题"
        val conv = conversation ?: return "会话尚未加载"
        _historyBusy.value = true
        return try {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    app.store.saveConversation(conv.copy(title = name, messages = conv.messages.toMutableList()))
                }
                conv.title = name
                _title.value = name
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            error.message ?: "标题保存失败"
        } finally { _historyBusy.value = false }
    }

    fun stopChild(reason: String) {
        if (!_isChild.value || _stopRequested.value) return
        if (app.subagentRegistry.stop(conversationId, reason)) _stopRequested.value = true
        else _error.value = "子代理已结束，无法中止"
    }

    suspend fun stopForDeletion() {
        generationJob?.cancel()
        compactJob?.cancel()
        modelSaveJob?.cancel()
        observationJob?.cancel()
        generationJob?.join()
        compactJob?.join()
        modelSaveJob?.join()
    }

    fun addAttachments(uris: List<Uri>) {
        if (_isChild.value || _importing.value || _streaming.value || uris.isEmpty()) return
        if (_attachments.value.size + uris.size > 8) {
            _error.value = "每条消息最多添加 8 个附件"
            return
        }
        _importing.value = true
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { app.store.loadConfig() }
                val resolved = conversation?.let { ModelResolver.resolve(it, config, agents) }
                for (uri in uris) {
                    val imported = withContext(Dispatchers.IO) {
                        val resolver = app.contentResolver
                        var name = uri.lastPathSegment ?: "attachment"
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (index >= 0) name = cursor.getString(index) ?: name
                            }
                        }
                        val declared = resolver.getType(uri)
                        val inferred = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                        val mime = declared?.takeUnless { it == "application/octet-stream" } ?: inferred ?: "application/octet-stream"
                        resolver.openInputStream(uri)?.use { app.attachmentStore.importFile(name, mime, it) }
                            ?: error("无法读取 $name")
                    }
                    val native = resolved != null && app.attachmentStore.nativeRejection(resolved, imported) == null
                    _attachments.value += imported.copy(delivery = if (native) "native" else "workspace")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _error.value = error.message ?: "添加附件失败"
            } finally {
                _importing.value = false
            }
        }
    }

    fun removeAttachment(id: String) {
        if (!_streaming.value && !_isChild.value) _attachments.value = _attachments.value.filterNot { it.id == id }
    }

    fun toggleAttachment(id: String) {
        if (_streaming.value || _isChild.value) return
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { app.store.loadConfig() }
            val resolved = conversation?.let { ModelResolver.resolve(it, config, agents) }
            val attachment = _attachments.value.firstOrNull { it.id == id } ?: return@launch
            val delivery = if (attachment.delivery == "native") "workspace" else "native"
            if (delivery == "native") {
                val reason = if (resolved == null) "请先配置模型" else app.attachmentStore.nativeRejection(resolved, attachment)
                if (reason != null) {
                    _error.value = "$reason；可在模型供应商设置中开启对应能力，或使用工作区文件"
                    return@launch
                }
            }
            _attachments.value = _attachments.value.map { if (it.id == id) it.copy(delivery = delivery) else it }
        }
    }

    fun send(text: String) {
        if ((text.isBlank() && _attachments.value.isEmpty()) || _streaming.value || _historyBusy.value || _importing.value || _isChild.value) return
        var conv = conversation ?: run {
            _error.value = "对话尚未加载完成"
            return
        }
        _streaming.value = true
        _error.value = null
        generationJob = viewModelScope.launch {
            var committed = false
            try {
                modelSaveJob?.join()
                val appConfig = withContext(Dispatchers.IO) { app.store.loadConfig() }
                val resolved = ModelResolver.resolve(conv, appConfig, agents)
                if (resolved == null) {
                    _error.value = "请先在聊天输入框中选择模型；没有可选模型时，可到「模型供应商设置」获取或添加模型"
                    return@launch
                }
                val attachments = _attachments.value.toList()
                val message = ChatMessage(role = "user", content = text.trim(), attachments = attachments)
                withContext(Dispatchers.IO) {
                    ConversationContext.recoverRejectedAttachments(conv)
                    val replayMessages = ContextWindows.replay(conv)
                    require(resolved.type != com.example.myapplication.data.model.ProviderType.CUSTOM ||
                        (attachments.isEmpty() && replayMessages.none { it.attachments.isNotEmpty() })) {
                        "自定义模板不支持附件读取，请改用 OpenAI 兼容、Anthropic 或 Gemini 协议"
                    }
                    app.attachmentStore.validateNative(resolved, replayMessages.flatMap { it.attachments } + attachments)
                    attachments.filter { it.delivery == "native" }.forEach { app.attachmentStore.fileFor(it) }
                }
                val candidate = conv.copy(messages = (conv.messages + message).toMutableList())
                if (candidate.title == "新对话") {
                    candidate.title = text.trim().ifBlank { attachments.firstOrNull()?.name ?: "文件对话" }.take(24)
                }
                // A completed disk commit and its UI receipt are indivisible with respect to cancellation.
                withContext(NonCancellable) {
                    conv = withContext(Dispatchers.IO) {
                        if (isDraft) app.store.commitDraft(sessionKey, candidate)
                        else candidate.also { app.store.saveConversation(it) }
                    }
                    committed = true
                    conversation = conv
                    conversationId = conv.id
                    isDraft = false
                    permissionSession = PermissionSession(app.store, conv, app.permissionCoordinator)
                    _attachments.value = emptyList()
                    if (input == text) input = ""
                    _sendRevision.value++
                    _committedId.value = conv.id
                    _title.value = conv.title
                    _messages.value = conv.messages.toList()
                }
                currentCoroutineContext().ensureActive()
                refreshContextOverview(resolved = resolved)
                val engine = app.newAgentEngine(onSubagentStatus = { _toolStatus.value = it })
                withContext(Dispatchers.IO) {
                    engine.run(
                        conversation = conv,
                        config = resolved,
                        maxLoops = appConfig.maxAgentLoops,
                        agentProfile = _agentProfile.value,
                        permissionSession = permissionSession,
                        callbacks = AgentEngine.Callbacks(
                            onMessageAdded = {
                                _messages.value = _messages.value + it
                                scheduleContextOverviewRefresh()
                            },
                            onMessageUpdated = { updated ->
                                _messages.value = _messages.value.map {
                                    if (it.id == updated.id) updated else it
                                }
                                scheduleContextOverviewRefresh()
                            },
                            onToolStatus = { _toolStatus.value = it }
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _error.value = e.message ?: "发送失败"
            } finally {
                // 退出页面或主动停止时也先完成保存，再允许下一次发送。
                try {
                    withContext(NonCancellable + Dispatchers.IO) { if (committed) app.store.saveConversation(conv) }
                } catch (error: Exception) {
                    _error.value = error.message ?: "会话保存失败"
                }
                _streaming.value = false
                _toolStatus.value = null
                _messages.value = conv.messages.toList()
                val appConfig = withContext(Dispatchers.IO) { app.store.loadConfig() }
                refreshContextOverview(resolved = ModelResolver.resolve(conv, appConfig, agents))
            }
        }
    }

    fun stop() {
        generationJob?.cancel()
    }

    fun editMessage(id: String, text: String, attachmentIds: Set<String>, includeInContext: Boolean) {
        changeHistory { ConversationEdits.edit(it, id, text, attachmentIds, includeInContext) }
    }

    fun deleteMessage(id: String) {
        changeHistory { ConversationEdits.delete(it, id) }
    }

    private fun changeHistory(change: (List<ChatMessage>) -> List<ChatMessage>) {
        if (_streaming.value || _historyBusy.value || _isChild.value) return
        val conv = conversation ?: return
        _historyBusy.value = true
        viewModelScope.launch {
            try {
                modelSaveJob?.join()
                val revised = change(conv.messages.toList())
                val persisted = conv.copy(
                    messages = revised.toMutableList(),
                    contextCompaction = null,
                    lastContextUsage = null
                )
                withContext(Dispatchers.IO) { app.store.saveConversation(persisted) }
                conv.messages.clear()
                conv.messages.addAll(revised)
                conv.contextCompaction = null
                conv.lastContextUsage = null
                _messages.value = revised
                val appConfig = withContext(Dispatchers.IO) { app.store.loadConfig() }
                refreshContextOverview(resolved = ModelResolver.resolve(conv, appConfig, agents))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _error.value = error.message ?: "修改消息失败"
            } finally {
                _historyBusy.value = false
            }
        }
    }

    fun clearError(expected: String? = null) {
        if (expected == null || _error.value == expected) _error.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavHostController,
    conversationId: String?,
    sessionKey: String = conversationId.orEmpty(),
    draftAgentId: String? = null,
    openDrawer: (() -> Unit)? = null,
    onCommitted: ((String) -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as AgentApp
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val sessions: ChatSessions = viewModel(viewModelStoreOwner = activity, factory = viewModelFactory {
        initializer { ChatSessions(createSavedStateHandle()) }
    })
    val vm = sessions.session(app, sessionKey, conversationId, draftAgentId)
    val committed by vm.committedId.collectAsStateWithLifecycle()
    // Draft commit changes the content in place. Approval matching must use the newly saved ID.
    val activeConversationId = committed ?: conversationId
    LaunchedEffect(vm, committed) { committed?.let { onCommitted?.invoke(it) } }
    val messages by vm.messages.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val agentProfile by vm.agentProfile.collectAsStateWithLifecycle()
    val selectedProviderId by vm.selectedProviderId.collectAsStateWithLifecycle()
    val currentModel by vm.currentModel.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val historyBusy by vm.historyBusy.collectAsStateWithLifecycle()
    val contextOverview by vm.contextOverview.collectAsStateWithLifecycle()
    val reasoningSupport by vm.reasoningSupport.collectAsStateWithLifecycle()
    val reasoningEffortOverride by vm.reasoningEffortOverride.collectAsStateWithLifecycle()
    val compacting by vm.compacting.collectAsStateWithLifecycle()
    val compactionProgress by vm.compactionProgress.collectAsStateWithLifecycle()
    val toolStatus by vm.toolStatus.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val modelOptions by vm.modelOptions.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val children by vm.children.collectAsStateWithLifecycle()
    val isChild by vm.isChild.collectAsStateWithLifecycle()
    val childSnapshot by vm.childSnapshot.collectAsStateWithLifecycle()
    val stopRequested by vm.stopRequested.collectAsStateWithLifecycle()
    val sendRevision by vm.sendRevision.collectAsStateWithLifecycle()
    val permissionMode by vm.permissionMode.collectAsStateWithLifecycle()
    val fileScope by vm.fileScope.collectAsStateWithLifecycle()
    val workingDirectory by vm.workingDirectory.collectAsStateWithLifecycle()
    val pending by app.permissionCoordinator.pending.collectAsStateWithLifecycle()
    ConversationApprovalHost(activeConversationId)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addAttachments(it) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleStartEffect(vm) {
        val observation = vm.observeChildren()
        onStopOrDispose { observation.cancel() }
    }

    ConversationFeedbackEffect(vm, error, snackbar)

    val agentScope = rememberCoroutineScope()
    var availableAgents by remember { mutableStateOf(emptyList<AgentProfile>()) }
    LifecycleStartEffect(vm) {
        val refresh = agentScope.launch { availableAgents = withContext(Dispatchers.IO) { app.store.loadAgents() } }
        onStopOrDispose { refresh.cancel() }
    }
    ChatContent(
        conversationKey = sessionKey,
        onRenameConversation = vm::renameConversation,
        isDraft = activeConversationId == null,
        hasGlobalDrawer = openDrawer != null,
        availableAgents = availableAgents,
        onSelectAgent = vm::selectDraftAgent,
        pendingCommandApproval = pending.any { it.conversationId == activeConversationId && it.kind == com.example.myapplication.agent.PermissionRequestKind.COMMAND },
        messages = messages,
        title = title,
        agentProfile = agentProfile,
        currentModel = currentModel,
        selectedProviderId = selectedProviderId,
        streaming = streaming,
        toolStatus = toolStatus,
        modelOptions = modelOptions,
        onBack = { openDrawer?.invoke() ?: navController.safePopBackStack() },
        draftInput = vm.input,
        onDraftInput = { vm.input = it },
        onConfigureModel = { navController.safeNavigateDirect(Routes.PROVIDERS) },
        onSwitchModel = { providerId, model -> vm.switchModel(providerId, model) },
        contextOverview = contextOverview,
        reasoningSupport = reasoningSupport,
        reasoningEffortOverride = reasoningEffortOverride,
        compacting = compacting,
        compactionProgress = compactionProgress,
        onUpdateReasoningEffort = vm::updateReasoningEffort,
        onCompactContext = vm::compactContext,
        onCancelCompaction = vm::cancelCompaction,
        onSendMessage = { vm.send(it) },
        onStop = { vm.stop() },
        onViewFile = { path -> vm.currentFilePath(path)?.let { navController.safeNavigateDirect(Routes.fileView(it)) } },
        snackbarHostState = snackbar,
        attachments = attachments,
        importing = importing,
        children = children,
        readOnly = isChild,
        waitingForParentApproval = isChild && pending.any { it.conversationId == childSnapshot?.parentConversationId },
        permissionMode = permissionMode,
        fileScope = fileScope,
        workingDirectory = workingDirectory,
        defaultWorkingDirectory = app.store.workspaceDir.absolutePath,
        onUpdatePermissions = vm::updatePermissions,
        historyBusy = historyBusy,
        onEditMessage = vm::editMessage,
        onDeleteMessage = vm::deleteMessage,
        childExecutionStatus = childSnapshot?.executionStatus,
        childStopReason = childSnapshot?.stopReason,
        canStopChild = childSnapshot?.executionStatus == "running" && !stopRequested,
        onStopChild = vm::stopChild,
        onOpenChild = { navController.safeNavigateDirect(Routes.chat(it)) },
        onOpenSession = { activeConversationId?.let { navController.safeNavigateDirect(Routes.session(it)) } },
        sendRevision = sendRevision,
        onAddAttachments = { picker.launch(arrayOf("*/*")) },
        onRemoveAttachment = vm::removeAttachment,
        onToggleAttachment = vm::toggleAttachment,
        attachmentFile = { runCatching { app.store.workspaceFile(it.workspacePath) }.getOrNull() }
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
    contextOverview: ContextOverview? = null,
    reasoningSupport: ReasoningSupport? = null,
    reasoningEffortOverride: ReasoningEffort? = null,
    compacting: Boolean = false,
    compactionProgress: String? = null,
    onUpdateReasoningEffort: (ReasoningEffort?) -> Unit = {},
    onCompactContext: () -> Unit = {},
    onCancelCompaction: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onViewFile: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onStop: () -> Unit = {},
    attachments: List<MessageAttachment> = emptyList(),
    importing: Boolean = false,
    children: List<Conversation> = emptyList(),
    readOnly: Boolean = false,
    waitingForParentApproval: Boolean = false,
    permissionMode: PermissionMode = PermissionMode.ACCEPT_EDIT,
    fileScope: List<String> = emptyList(),
    workingDirectory: String? = null,
    defaultWorkingDirectory: String = "",
    onUpdatePermissions: suspend (PermissionMode, List<String>, String?) -> String? = { _, _, _ -> null },
    childExecutionStatus: String? = null,
    childStopReason: String? = null,
    canStopChild: Boolean = false,
    onStopChild: (String) -> Unit = {},
    onOpenChild: (String) -> Unit = {},
    sendRevision: Int = 0,
    onAddAttachments: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onToggleAttachment: (String) -> Unit = {},
    attachmentFile: (MessageAttachment) -> java.io.File? = { null },
    historyBusy: Boolean = false,
    onEditMessage: (String, String, Set<String>, Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteMessage: (String) -> Unit = {},
    pendingCommandApproval: Boolean = false,
    draftInput: String? = null,
    onDraftInput: (String) -> Unit = {},
    onConfigureModel: () -> Unit = {},
    selectedProviderId: String? = null,
    isDraft: Boolean = false,
    hasGlobalDrawer: Boolean = false,
    availableAgents: List<AgentProfile> = emptyList(),
    onSelectAgent: (AgentProfile?) -> Unit = {},
    conversationKey: String = "",
    onRenameConversation: suspend (String) -> String? = { null },
    onOpenSession: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var renameDialog by rememberSaveable(conversationKey) { mutableStateOf(false) }
    if (renameDialog) RenameConversationDialog(title, onDismiss = { renameDialog = false }, onSave = onRenameConversation)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var localInput by rememberSaveable { mutableStateOf("") }
    val input = draftInput ?: localInput
    fun setInput(value: String) { localInput = value; onDraftInput(value) }
    var submittedInput by rememberSaveable { mutableStateOf<String?>(null) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var showContextUsage by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(attachment, attachmentFile(attachment)) { previewAttachment = null }
    }
    editingMessage?.let { message ->
        EditMessageDialog(message, onDismiss = { editingMessage = null }) { text, ids, included ->
            onEditMessage(message.id, text, ids, included)
            editingMessage = null
        }
    }
    deletingMessage?.let { message ->
        AlertDialog(onDismissRequest = { deletingMessage = null }, title = { Text("删除这条消息？") },
            text = { Text(if (message.toolCalls.isEmpty()) "这会改变后续请求的历史内容及缓存。其他消息会保留。"
                else "这条消息及关联的工具记录将从对话中删除。已执行的文件修改不会撤销，子代理会话仍保留在会话面板中。") },
            confirmButton = { UiTextButton(onClick = { onDeleteMessage(message.id); deletingMessage = null }) { Text("删除") } },
            dismissButton = { UiTextButton(onClick = { deletingMessage = null }) { Text("取消") } })
    }
    if (showPermissions) SessionPermissionsDialog(permissionMode, fileScope, workingDirectory, defaultWorkingDirectory,
        onDismiss = { showPermissions = false }, onSave = { mode, directories, directory ->
            onUpdatePermissions(mode, directories, directory)
        })
    if (showContextUsage) {
        ContextUsageSheet(
            overview = contextOverview,
            isCompacting = compacting,
            compactionProgress = compactionProgress,
            canCompact = !readOnly && !streaming && !historyBusy && !compacting,
            onCompact = onCompactContext,
            onCancelCompaction = onCancelCompaction,
            feedback = snackbarHostState,
            onDismiss = { showContextUsage = false }
        )
    }
    val scope = rememberCoroutineScope()

    var stopDialog by remember { mutableStateOf(false) }
    var stopReason by rememberSaveable { mutableStateOf("") }
    val toolResults = remember(messages) { messages.filter { it.role == "tool" }.associateBy { it.toolCallId } }
    val callIds = remember(messages) { messages.flatMap { it.toolCalls }.map { it.id }.toSet() }
    val displayMessages = remember(messages) { messages.filterNot { it.contextKind != null || it.originToolCallId != null || (it.role == "tool" && it.toolCallId in callIds) } }
    var consumedSendRevision by rememberSaveable { mutableStateOf(sendRevision) }
    LaunchedEffect(sendRevision) {
        if (sendRevision != consumedSendRevision) {
            if (input == submittedInput) setInput("")
            submittedInput = null
            consumedSendRevision = sendRevision
        }
    }
    if (stopDialog) AlertDialog(
        onDismissRequest = { stopDialog = false },
        title = { Text("中止子代理") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("确认后将停止此子代理，并将理由通知主代理。")
            OutlinedTextField(value = stopReason, onValueChange = { stopReason = it },
                label = { Text("中止理由（可选）") }, maxLines = 5)
        } },
        confirmButton = { UiTextButton(onClick = { onStopChild(stopReason); stopDialog = false }) { Text("确认中止") } },
        dismissButton = { UiTextButton(onClick = { stopDialog = false }) { Text("取消") } }
    )

    var followLatest by rememberSaveable { mutableStateOf(true) }
    var scrollRequest by remember { mutableStateOf(0) }
    val streamingMessageId = displayMessages.lastOrNull()?.id
    val extraRows = if (readOnly) 1 else 0
    var userScrollPending by remember { mutableStateOf(false) }
    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    userScrollPending = true
                }
                return Offset.Zero
            }
        }
    }

    // Layout changes include AndroidView's delayed Markdown measurements. They drive following so
    // a throttled render or a table's final height still reaches the actual bottom.
    LaunchedEffect(listState, displayMessages.size, extraRows, scrollRequest) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            ChatListViewport(
                lastVisible?.index,
                lastVisible?.let { it.offset + it.size },
                listState.layoutInfo.viewportEndOffset,
                displayMessages.size + extraRows - 1,
                listState.isScrollInProgress,
                isUserDragging,
                userScrollPending
            )
        }.collect { viewport ->
            if (viewport.lastItemIndex < 0) return@collect
            val atBottom = listIsAtBottom(
                viewport.lastVisibleIndex,
                viewport.lastVisibleEnd,
                viewport.viewportEnd,
                viewport.lastItemIndex
            )
            val exactlyAtBottom = listIsAtBottom(
                viewport.lastVisibleIndex,
                viewport.lastVisibleEnd,
                viewport.viewportEnd,
                viewport.lastItemIndex,
                tolerancePx = 0
            )
            val userScrolling = viewport.isUserDragging || viewport.userScrollPending
            followLatest = updateFollowLatest(followLatest, userScrolling, atBottom)
            if (userScrollPending && !viewport.isUserDragging && !viewport.isScrollInProgress) {
                userScrollPending = false
            }
            if (followLatest && !userScrolling && !exactlyAtBottom) {
                try {
                    listState.scrollToChatBottom(viewport.lastItemIndex)
                } catch (cancelled: CancellationException) {
                    // A user gesture cancels the scroll mutation, not the follow observer.
                    // Lifecycle cancellation must still propagate.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        UiScaffold(
            snackbarHost = { if (!showContextUsage) TopFeedbackHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    expandedHeight = 64.dp,
                    title = {
                        if (isDraft) Text("AgentApp", fontSize = 20.sp)
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(agentProfile?.name ?: "通用助手", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!readOnly) IconButton(onClick = { renameDialog = true }, enabled = !streaming && !historyBusy) {
                                Icon(Icons.Outlined.Edit, "修改会话标题", Modifier.size(17.dp))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(if (hasGlobalDrawer || isDraft) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                                if (hasGlobalDrawer || isDraft) "打开导航" else "返回", Modifier.size(22.dp))
                        }
                    },
                    actions = {
                        if (isDraft) DraftAgentPicker(agentProfile, availableAgents, onSelectAgent)
                        else if (readOnly) UiTextButton(onClick = { stopDialog = true }, enabled = canStopChild) { Text("中止") }
                        else IconButton(onClick = onOpenSession) {
                            Icon(Icons.Default.AccountTree, "会话面板 · 子代理 ${children.size}", Modifier.size(22.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                if (!readOnly) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .imePadding().navigationBarsPadding().padding(horizontal = 14.dp)) {
                    if (isDraft && messages.isEmpty()) Text("你的模型、文件与工具，在同一个对话里。",
                        Modifier.padding(start = 14.dp, bottom = 9.dp), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedVisibility(streaming || importing) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(if (importing) "正在复制附件…" else toolStatus ?: "正在处理…",
                                Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Surface(shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 0.dp) {
                        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 13.dp, bottom = 6.dp)) {
                            AnimatedVisibility(attachments.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(attachments, key = { it.id }) { attachment ->
                                        Box(Modifier.animateItem()) {
                                            AttachmentChip(attachment, attachmentFile(attachment),
                                                onToggle = { onToggleAttachment(attachment.id) },
                                                onRemove = { onRemoveAttachment(attachment.id) },
                                                enabled = !streaming && !importing && !historyBusy,
                                                compact = true)
                                        }
                                    }
                                }
                            }
                            BasicTextField(value = input, onValueChange = ::setInput,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 7.dp),
                                maxLines = 5, enabled = !historyBusy,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { field ->
                                    Box {
                                        if (input.isEmpty()) Text(if (isDraft) "有什么想一起完成的？" else "继续这个想法…",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .82f), fontSize = 16.sp)
                                        field()
                                    }
                                })
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onAddAttachments, enabled = !streaming && !importing && !historyBusy) {
                                    Icon(Icons.Default.Add, "添加附件", Modifier.size(22.dp))
                                }
                                // The flexible slot owns all remaining width; the label itself stays left aligned.
                                // weight(fill=false) on a sibling of another weight leaves unassigned trailing space.
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    UiTextButton(onClick = { if (modelOptions.isEmpty()) onConfigureModel() else modelMenuExpanded = true },
                                        enabled = !streaming && !historyBusy) {
                                        Text(currentModel.ifBlank { "未选择模型" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, false), fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = {
                                    if (streaming) onStop() else {
                                        submittedInput = input
                                        followLatest = true
                                        scrollRequest++
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onSendMessage(input)
                                    }
                                }, enabled = streaming || (currentModel.isNotBlank() && !historyBusy && !importing && (input.isNotBlank() || attachments.isNotEmpty())),
                                    modifier = Modifier.size(48.dp)) {
                                    val canSend = streaming || (currentModel.isNotBlank() && !historyBusy && !importing && (input.isNotBlank() || attachments.isNotEmpty()))
                                    val sendColor by androidx.compose.animation.animateColorAsState(
                                        MaterialTheme.colorScheme.primary.copy(alpha = if (canSend) 1f else .35f), label = "send availability")
                                    Box(Modifier.size(40.dp).background(sendColor, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center) {
                                    androidx.compose.animation.Crossfade(streaming, label = "send state") { running ->
                                        Icon(if (running) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                            if (running) "停止生成" else "发送", Modifier.size(22.dp), tint = androidx.compose.ui.graphics.Color.White)
                                    }
                                }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        UiTextButton(onClick = { showPermissions = true }, enabled = !streaming && !historyBusy,
                            contentPadding = PaddingValues(horizontal = 2.dp)) {
                            Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(16.dp))
                            Text(permissionMode.label, fontSize = 12.sp)
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(14.dp))
                        }
                        ReasoningEffortMenu(reasoningSupport, reasoningEffortOverride,
                            !compacting, onUpdateReasoningEffort, Modifier.weight(1f))
                        CompactContextUsage(contextOverview) { showContextUsage = true }
                    }
                }
                if (modelMenuExpanded) ModelPicker(modelOptions, onSwitchModel, selectedProviderId, currentModel, onConfigureModel) { modelMenuExpanded = false }
            }
        ) { padding ->
            if (messages.isEmpty()) {
                ChatWelcome(Modifier.fillMaxSize().padding(padding), onSuggestion = ::setInput)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .nestedScroll(userScrollConnection),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (readOnly) item(key = "child-status") {
                        Column(Modifier.padding(8.dp)) {
                            Text("只读 · ${childStatus(childExecutionStatus)}", style = MaterialTheme.typography.labelLarge)
                            if (waitingForParentApproval) Text("主会话有待处理的授权，请返回主会话查看。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            childStopReason?.takeIf { it.isNotBlank() }?.let { Text("中止理由：$it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    items(displayMessages, key = { it.id }) { msg ->
                        MessageBubble(
                            conversationKey = conversationKey,
                            msg = msg,
                            agentProfile = agentProfile,
                            streaming = streaming && msg.id == streamingMessageId,
                            onViewFile = onViewFile,
                            toolResults = toolResults,
                            running = streaming || childExecutionStatus == "running",
                            children = children,
                            onOpenChild = onOpenChild,
                            allowFileNavigation = !readOnly,
                            attachmentFile = attachmentFile,
                            onOpenAttachment = { previewAttachment = it },
                            canEdit = !readOnly && !streaming && !historyBusy,
                            onEdit = { editingMessage = msg },
                            onDelete = { deletingMessage = msg },
                            pendingCommandApproval = pendingCommandApproval,
                            activeToolCallId = if (toolStatus == null && childExecutionStatus != "running") null else messages.flatMap { it.toolCalls }.firstOrNull { it.id !in toolResults }?.id
                        )
                    }
                }
            }
        }
    }
}

/** Returns whether the last row is visible through the bottom edge of the viewport. */
private data class ChatListViewport(
    val lastVisibleIndex: Int?,
    val lastVisibleEnd: Int?,
    val viewportEnd: Int,
    val lastItemIndex: Int,
    val isScrollInProgress: Boolean,
    val isUserDragging: Boolean,
    val userScrollPending: Boolean
)

internal fun listIsAtBottom(
    lastVisibleIndex: Int?,
    lastVisibleEnd: Int?,
    viewportEnd: Int,
    lastItemIndex: Int,
    tolerancePx: Int = 80
): Boolean =
    lastItemIndex >= 0 &&
        lastVisibleIndex != null &&
        lastVisibleEnd != null &&
        lastVisibleIndex >= lastItemIndex &&
        lastVisibleEnd <= viewportEnd + tolerancePx

/** Only user-initiated movement changes whether streamed output may take over the list. */
internal fun updateFollowLatest(
    current: Boolean,
    userScrolling: Boolean,
    atBottom: Boolean
): Boolean = if (userScrolling) atBottom else current

private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToChatBottom(lastItemIndex: Int) {
    var lastItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastItemIndex }
    if (lastItem == null) {
        scrollToItem(lastItemIndex)
        lastItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastItemIndex }
    }
    lastItem?.let { item ->
        val distance = (item.offset + item.size - layoutInfo.viewportEndOffset).coerceAtLeast(0)
        if (distance > 0) animateScrollBy(
            distance.toFloat(),
            androidx.compose.animation.core.tween(100, easing = androidx.compose.animation.core.LinearEasing)
        )
    }
}

/** 提取工具调用的友好动作名称（无 emoji） */
internal fun friendlyToolTitle(toolName: String?): String = when (toolName) {
    Tools.WRITE_FILE -> "写入文件"
    Tools.DELETE_FILE -> "删除文件"
    Tools.EDIT_FILE -> "修改文件"
    Tools.RUN_COMMAND -> "执行命令"
    Tools.ENTER_PLAN_MODE -> "进入计划模式"
    Tools.EXIT_PLAN_MODE -> "提交计划"
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
internal fun toolIcon(toolName: String?): ImageVector = when (toolName) {
    Tools.WRITE_FILE, Tools.EDIT_FILE, Tools.READ_FILE, Tools.ENTER_PLAN_MODE, Tools.EXIT_PLAN_MODE -> Icons.Outlined.Description
    Tools.DELETE_FILE -> Icons.Filled.Delete
    Tools.LIST_FILES -> Icons.Outlined.Folder
    Tools.SAVE_MEMORY, Tools.SEARCH_MEMORY, Tools.DELETE_MEMORY -> Icons.Outlined.Bookmark
    Tools.USE_SKILL, Tools.SAVE_SKILL -> Icons.Outlined.Bolt
    Tools.RUN_SUBAGENT -> Icons.Outlined.SmartToy
    else -> Icons.Outlined.Code
}

/** 从工具调用参数中提取文件路径（write_file / read_file 可预览） */
internal fun extractFilePath(argumentsJson: String): String? =
    runCatching {
        ProviderJson.parseToJsonElement(argumentsJson).jsonObject["path"]
            ?.jsonPrimitive?.content
    }.getOrNull()

@Composable
internal fun MessageBubble(
    msg: ChatMessage,
    agentProfile: AgentProfile?,
    onViewFile: (String) -> Unit,
    streaming: Boolean = false,
    toolResults: Map<String?, ChatMessage> = emptyMap(),
    running: Boolean = false,
    children: List<Conversation> = emptyList(),
    onOpenChild: (String) -> Unit = {},
    attachmentFile: (MessageAttachment) -> java.io.File? = { null },
    activeToolCallId: String? = null,
    pendingCommandApproval: Boolean = false,
    allowFileNavigation: Boolean = true,
    onOpenAttachment: (MessageAttachment) -> Unit = {},
    canEdit: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    conversationKey: String = ""
) {
    when (msg.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(Modifier.fillMaxWidth(.9f), horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(16.dp)) {
                        if (msg.content.isNotBlank()) SelectionContainer { Text(msg.content, style = MaterialTheme.typography.bodyLarge) }
                        msg.attachments.forEach { attachment ->
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenAttachment(attachment) },
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Description, null, Modifier.size(20.dp))
                                Text(attachment.name, Modifier.weight(1f).padding(horizontal = 8.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text("工作区", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                MessageActions(msg, canEdit, onEdit, onDelete)
            }
        }
        "tool" -> if (msg.toolName == Tools.RUN_SUBAGENT) Text("子代理 · ${if (msg.isError) "已停止或失败" else "已完成"}", style = MaterialTheme.typography.labelMedium) else ToolMessageBlock(msg)
        else -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (agentProfile?.avatarPath != null) AgentAvatar(agentProfile.emoji, agentProfile.avatarPath, size = 20.dp)
                else AgentMark(Modifier.size(20.dp))
                Text(agentProfile?.name ?: "通用助手", Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (msg.thinking.isNotBlank()) ThinkingBlock(msg.thinking, streaming)
            if (msg.content.isNotBlank()) {
                MarkdownContent(msg.content, Modifier.fillMaxWidth().padding(vertical = 6.dp), streaming)
            }
            msg.toolCalls.forEach { call ->
                androidx.compose.runtime.key(conversationKey, msg.id, call.id) {
                ToolActivityRow(call, toolResults[call.id], running && activeToolCallId == call.id,
                    child = children.firstOrNull { it.parentToolCallId == call.id },
                    onOpenChild = onOpenChild, onViewFile = onViewFile, queued = running && activeToolCallId != call.id, allowFileNavigation = allowFileNavigation,
                    awaitingApproval = pendingCommandApproval && activeToolCallId == call.id)
                }
            }
            MessageActions(msg, canEdit, onEdit, onDelete)
        }
    }
}

/**
 * 极简思考过程呈现组件（主流移动端 AI 风格，无 Card 容器）
 */
@Composable
private fun ThinkingBlock(thinking: String, streaming: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(14.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 5.dp)
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
                MarkdownContent(thinking, Modifier.weight(1f), streaming)
            }
        }
    }
}

/**
 * 极简工具调用结果呈现组件（主流移动端 Action Step 风格，无 Card 容器）
 */
@Composable
private fun ToolMessageBlock(msg: ChatMessage) {
    ToolActivityRow(
        call = ToolCallInfo(id = msg.toolCallId ?: msg.id, name = msg.toolName.orEmpty(), argumentsJson = ""),
        result = msg, running = false, child = null, onOpenChild = {}, onViewFile = {}, allowFileNavigation = false
    )
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

@Preview(showBackground = true, name = "Chat - Empty", widthDp = 360, heightDp = 800)
@Composable
private fun ChatScreenEmptyPreview() {
    AgentTheme(themeMode = "light") {
        ChatContent(
            messages = emptyList(),
            title = "新对话",
            isDraft = true,
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
