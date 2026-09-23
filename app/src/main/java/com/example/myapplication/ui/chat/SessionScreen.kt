package com.example.myapplication.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.data.store.FileChanges
import com.example.myapplication.data.store.FileDiffResult
import com.example.myapplication.safeNavigateDirect
import com.example.myapplication.safePopBackStack
import com.example.myapplication.ui.components.*
import com.example.myapplication.ui.files.InlineFileDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only the foreground route owns approval UI; navigation never resolves or duplicates a request. */
@Composable
internal fun ConversationApprovalHost(conversationId: String?) {
    val app = LocalContext.current.applicationContext as AgentApp
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val pending by app.permissionCoordinator.pending.collectAsStateWithLifecycle()
    if (lifecycle == Lifecycle.State.RESUMED) {
        pending.firstOrNull { it.conversationId == conversationId }?.let { request ->
            PermissionRequestDialog(request) { decision, feedback ->
                app.permissionCoordinator.resolve(request.id, decision, feedback)
            }
        }
    }
}

/** Keep an undismissed error in the ViewModel when its route leaves the foreground. */
@Composable
internal fun ConversationFeedbackEffect(vm: ChatViewModel, error: String?, state: SnackbarHostState) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycle, error) {
        if (lifecycle == Lifecycle.State.RESUMED && error != null) {
            state.showSnackbar(ErrorFeedback(error))
            vm.clearError(error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(navController: NavHostController, conversationId: String) {
    val app = LocalContext.current.applicationContext as AgentApp
    val activity = LocalContext.current as ComponentActivity
    val sessions: ChatSessions = viewModel(viewModelStoreOwner = activity, factory = viewModelFactory {
        initializer { ChatSessions(createSavedStateHandle()) }
    })
    // session() finds a draft-key writer by committed ID before constructing a new ViewModel.
    val vm = sessions.session(app, conversationId, conversationId, null)
    val title by vm.title.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val children by vm.children.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val readOnly by vm.isChild.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val pending by app.permissionCoordinator.pending.collectAsStateWithLifecycle()
    val artifacts = remember(messages) { sessionArtifacts(messages) }
    var showPlan by rememberSaveable(conversationId) { mutableStateOf(false) }
    LaunchedEffect(pending) {
        // A read-only plan must not obscure an actionable approval raised while it is open.
        if (pending.any { it.conversationId == conversationId }) showPlan = false
    }
    val feedback = remember { SnackbarHostState() }
    LifecycleStartEffect(vm) {
        val observation = vm.observeChildren()
        onStopOrDispose { observation.cancel() }
    }
    ConversationFeedbackEffect(vm, error, feedback)
    ConversationApprovalHost(conversationId)
    if (showPlan) PlanDocumentDialog(plan.orEmpty()) { showPlan = false }
    UiScaffold(
        snackbarHost = { TopFeedbackHost(feedback) },
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("会话面板")
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium)
                }
            }, navigationIcon = {
                IconButton(onClick = { navController.safePopBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回会话")
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("plan-heading") { Text("当前计划", style = MaterialTheme.typography.titleSmall) }
            item("plan") {
                if (plan.isNullOrBlank()) Text("尚无计划", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Card(onClick = { showPlan = true }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text(plan?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.trimStart('#')?.trim().orEmpty(),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (pending.any { it.conversationId == conversationId }) "有操作等待审批" else "阅读计划不会批准执行",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            item("children-heading") { Text("子代理 · 单层委派", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall) }
            if (children.isEmpty()) item("no-children") { Text("尚未委派子代理", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            for ((label, group) in listOf("运行中" to children.filter { it.executionStatus == "running" },
                "已结束" to children.filter { it.executionStatus != "running" })) {
                if (group.isNotEmpty()) item("group-$label") { Text(label, style = MaterialTheme.typography.labelMedium) }
                items(group, key = { "child-${it.id}" }) { child ->
                    Card(onClick = { navController.safeNavigateDirect(Routes.chat(child.id)) },
                        modifier = Modifier.animateItem().fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(6.dp),
                                    color = if (child.executionStatus == "failed") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                                    Text(childStatus(child.executionStatus), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.width(12.dp))
                                child.modelOverride?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(child.title, style = MaterialTheme.typography.titleMedium)
                            child.messages.firstOrNull { it.role == "user" }?.content?.takeIf { it.isNotBlank() && it != child.title }?.let {
                                Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            child.stopReason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            OutlinedButton(onClick = { navController.safeNavigateDirect(Routes.chat(child.id)) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("查看子代理会话") }
                        }
                    }
                }
            }
            item("artifacts-heading") { Text("本次产物", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall) }
            if (artifacts.isEmpty()) item("no-artifacts") { Text("暂无已保存的文件变更记录", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(artifacts, key = { "artifact-${it.messageId}" }) { artifact ->
                SessionArtifactRow(artifact, allowCurrentFile = !readOnly) { path ->
                    vm.currentFilePath(path)?.let { navController.safeNavigateDirect(Routes.fileView(it)) }
                }
            }
            item("lifetime") {
                Text("任务由当前应用会话管理；没有后台常驻服务。关闭应用可能中断执行。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessionArtifactRow(artifact: SessionArtifact, allowCurrentFile: Boolean, onOpen: (String) -> Unit) {
    var expanded by rememberSaveable(artifact.messageId) { mutableStateOf(false) }
    val detailState = rememberSaveableStateHolder()
    val diff by produceState<FileDiffResult?>(null, expanded, artifact.change) {
        if (expanded) value = withContext(Dispatchers.Default) { FileChanges.diff(artifact.change) }
    }
    Column {
        UiTextButton(onClick = { expanded = !expanded }) {
            Text(artifact.change.path, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (expanded) "收起历史 Diff" else "历史 Diff")
        }
        AnimatedVisibility(expanded) {
            detailState.SaveableStateProvider(artifact.messageId) {
            Column(Modifier.inertWhen(!expanded)) {
                InlineFileDiff(artifact.change, diff)
                if (allowCurrentFile) UiTextButton(onClick = { onOpen(artifact.change.path) }) { Text("查看当前文件") }
            }
            }
        }
    }
}
