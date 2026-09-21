package com.example.myapplication.ui.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.example.myapplication.AgentApp
import com.example.myapplication.Routes
import com.example.myapplication.ui.components.ListSelectionBar
import com.example.myapplication.ui.components.rememberListSelection
import com.example.myapplication.data.model.SkillMeta
import com.example.myapplication.safeNavigateDirect
import com.example.myapplication.safePopBackStack
import com.example.myapplication.ui.theme.AgentTheme
import com.example.myapplication.ui.theme.ExpressiveTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 格式化 Skill 名称为标准的 kebab-case 规范
 */
fun formatKebabCase(input: String): String {
    return input.trim()
        .replace(Regex("[\\s_]+"), "-")
        .replace(Regex("[^a-zA-Z0-9\\-]"), "")
        .lowercase()
}

/**
 * 调用系统分享 ZIP 文件
 */
fun shareSkillZip(context: Context, zipFile: File, chooserTitle: String = "分享 Skill ZIP") {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

class SkillsViewModel(val app: AgentApp) : ViewModel() {
    private val _skills = MutableStateFlow<List<SkillMeta>>(emptyList())
    val skills = _skills.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            reload()
        }
    }

    fun delete(name: String) {
        deleteSelected(setOf(name))
    }

    /** Batch delete runs one storage pass and refreshes the list once. */
    fun deleteSelected(names: Set<String>) {
        if (_busy.value || names.isEmpty()) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                names.forEach { name ->
                    currentCoroutineContext().ensureActive()
                    app.store.deleteSkill(name)
                }
                reload()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Reflect any earlier deletions if a later item fails.
                runCatching { reload() }
                _message.value = "删除失败: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun importZip(
        inputStream: InputStream,
        onSuccess: (List<SkillMeta>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_busy.value) {
            inputStream.close()
            return
        }
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                val imported = inputStream.use { app.store.importSkillZip(it) }
                currentCoroutineContext().ensureActive()
                reload()
                onSuccess(imported)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error.localizedMessage ?: "导入技能失败")
            } finally {
                _busy.value = false
            }
        }
    }

    fun exportSkill(
        name: String,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                val file = app.store.exportSkillZip(name)
                currentCoroutineContext().ensureActive()
                onSuccess(file)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error.localizedMessage ?: "导出技能失败")
            } finally {
                _busy.value = false
            }
        }
    }

    fun exportAll(
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                val file = app.store.exportAllSkillsZip()
                currentCoroutineContext().ensureActive()
                onSuccess(file)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error.localizedMessage ?: "导出全部技能失败")
            } finally {
                _busy.value = false
            }
        }
    }

    /** 将选定技能合集保存到 SAF 位置；选定导出不经过分享面板。 */
    fun exportSelected(
        names: Set<String>,
        uri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_busy.value || names.isEmpty()) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                val zipFile = app.store.exportSkillsZip(names)
                currentCoroutineContext().ensureActive()
                val output = app.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("无法打开导出位置")
                output.use { stream ->
                    zipFile.inputStream().use { input -> input.copyTo(stream) }
                }
                currentCoroutineContext().ensureActive()
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error.localizedMessage ?: "导出所选技能失败")
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun reload() {
        currentCoroutineContext().ensureActive()
        _skills.value = app.store.listSkills()
    }

    fun clearMessage() {
        _message.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(navController: NavHostController, openDrawer: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AgentApp
    val vm: SkillsViewModel = viewModel(factory = viewModelFactory {
        initializer { SkillsViewModel(app) }
    })
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.lifecycle.compose.LifecycleStartEffect(Unit) {
        vm.refresh()
        onStopOrDispose { }
    }
    val skills by vm.skills.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    var importPickerActive by rememberSaveable { mutableStateOf(false) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        importPickerActive = false
        if (uri != null) {
            val stream = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                null
            }
            if (stream != null) {
                vm.importZip(
                    inputStream = stream,
                    onSuccess = { imported ->
                        coroutineScope.launch {
                            val names = imported.joinToString(", ") { it.name }
                            snackbarHostState.showSnackbar("成功导入 ${imported.size} 个技能: $names")
                        }
                    },
                    onError = { err ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("导入失败: $err")
                        }
                    }
                )
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("无法打开所选文件数据流")
                }
            }
        }
    }
    var exportNames by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    var exportPickerActive by rememberSaveable { mutableStateOf(false) }
    val exportPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val snapshot = exportNames.toSet()
        exportPickerActive = false
        if (uri != null && snapshot.isNotEmpty()) {
            vm.exportSelected(
                names = snapshot,
                uri = uri,
                onSuccess = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("已导出 ${snapshot.size} 个技能") }
                },
                onError = { error ->
                    coroutineScope.launch { snackbarHostState.showSnackbar("导出失败: $error") }
                }
            )
        }
    }
    var exportConfirmNames by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
    val transferBusy = busy || importPickerActive || exportPickerActive ||
        exportConfirmNames.isNotEmpty()

    SkillsContent(
        skills = skills,
        snackbarHostState = snackbarHostState,
        onOpenDrawer = openDrawer,
        onNewSkill = { navController.safeNavigateDirect(Routes.skillEdit("new")) },
        onSelectSkill = { name -> navController.safeNavigateDirect(Routes.skillEdit(name)) },
        onDeleteSkill = { name -> vm.delete(name) },
        onDeleteSelected = vm::deleteSelected,
        onImportZip = {
            if (!transferBusy) {
                importPickerActive = true
                filePickerLauncher.launch("*/*")
            }
        },
        onExportSkill = { name ->
            if (!transferBusy) {
                vm.exportSkill(
                    name = name,
                    onSuccess = { zipFile ->
                        shareSkillZip(context, zipFile, "分享技能 $name")
                    },
                    onError = { err ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("导出失败: $err")
                        }
                    }
                )
            }
        },
        onExportAll = {
            if (!transferBusy) {
                vm.exportAll(
                    onSuccess = { zipFile ->
                        shareSkillZip(context, zipFile, "分享所有技能备份")
                    },
                    onError = { err ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("导出失败: $err")
                        }
                    }
                )
            }
        },
        onExportSelected = { names ->
            if (!transferBusy && names.isNotEmpty()) {
                exportConfirmNames = names.toList()
            }
        },
        busy = transferBusy
    )

    if (exportConfirmNames.isNotEmpty()) {
        val snapshot = exportConfirmNames
        AlertDialog(
            onDismissRequest = { exportConfirmNames = arrayListOf() },
            title = { Text("确认导出所选 Skills？") },
            text = {
                Column {
                    Text("将导出 ${snapshot.size} 个 Skill：")
                    Text(
                        snapshot.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp).heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { exportConfirmNames = arrayListOf() }) { Text("取消") }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    exportConfirmNames = arrayListOf()
                    exportNames = snapshot
                    exportPickerActive = true
                    exportPickerLauncher.launch("skills-selected.zip")
                }) { Text("选择保存位置") }
            }
        )
    }
}

/**
 * 技能列表纯 UI 组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsContent(
    skills: List<SkillMeta>,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onOpenDrawer: () -> Unit,
    onNewSkill: () -> Unit,
    onSelectSkill: (String) -> Unit,
    onDeleteSkill: (String) -> Unit,
    onImportZip: () -> Unit = {},
    onExportSkill: (String) -> Unit = {},
    onExportAll: () -> Unit = {},
    onDeleteSelected: (Set<String>) -> Unit = { names -> names.forEach(onDeleteSkill) },
    onExportSelected: (Set<String>) -> Unit = {},
    busy: Boolean = false
) {
    val selection = rememberListSelection(skills.map { it.name })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills 扩展") },
                navigationIcon = {
                    if (selection.active) {
                        IconButton(onClick = selection.onExit, enabled = !busy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "退出管理")
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer, enabled = !busy) {
                            Icon(Icons.Filled.Menu, "菜单")
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = if (selection.active) selection.onExit else selection.onEnter,
                        enabled = !busy
                    ) {
                        Text(if (selection.active) "完成" else "管理")
                    }
                    if (!selection.active) {
                        IconButton(onClick = onImportZip, enabled = !busy) {
                            Icon(Icons.Filled.FileUpload, "导入 Skill (ZIP)")
                        }
                        IconButton(onClick = onExportAll, enabled = !busy) {
                            Icon(Icons.Filled.FileDownload, "导出全部 (ZIP)")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selection.active) {
                ListSelectionBar(
                    selection = selection,
                    busy = busy,
                    onDelete = onDeleteSelected,
                    onImport = onImportZip,
                    onExport = onExportSelected
                )
            }
        },
        floatingActionButton = {
            if (!selection.active) {
                FloatingActionButton(onClick = { if (!busy) onNewSkill() }) {
                    Icon(Icons.Filled.Add, "新建 Skill")
                }
            }
        }
    ) { padding ->
        if (skills.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "暂无 Skill。可以手动创建、导入标准 ZIP，或由 Agent 运行时自行沉淀。",
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
                items(skills, key = { it.name }) { skill ->
                    Card(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        onClick = {
                            if (!busy) {
                                if (selection.active) selection.onToggle(skill.name)
                                else onSelectSkill(skill.name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. 标题行：独占顶部空间，工具小图标 + 等宽代码质感的技能标识
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (selection.active) {
                                    Checkbox(
                                        checked = skill.name in selection.selectedIds,
                                        onCheckedChange = { selection.onToggle(skill.name) },
                                        enabled = !busy
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Build,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = skill.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 2. 描述文案（自然展开 1-2 行）
                            Text(
                                text = skill.description.ifBlank { "暂无描述" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // 3. 底部元数据与操作栏：版本号 + 许可证居左，操作按钮紧凑居右
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 元数据标签组
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    if (skill.version.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                "v${skill.version}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (skill.license.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                skill.license,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // 操作按钮组
                                if (!selection.active) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onExportSkill(skill.name) },
                                            enabled = !busy,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Share,
                                                contentDescription = "导出并分享 ZIP",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteSkill(skill.name) },
                                            enabled = !busy,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "删除",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditScreen(navController: NavHostController, skillName: String) {
    val context = LocalContext.current
    val app = context.applicationContext as AgentApp
    val decoded = remember(skillName) {
        URLDecoder.decode(skillName, StandardCharsets.UTF_8.toString())
    }
    val existing = remember(decoded) {
        if (decoded == "new") null else app.store.readSkill(decoded)?.let { raw ->
            app.store.parseSkill(raw).let { (meta, body) -> Triple(meta, body, raw) }
        }
    }

    SkillEditContent(
        isNew = decoded == "new",
        initialName = existing?.first?.get("name") ?: (if (decoded == "new") "" else decoded),
        initialDescription = existing?.first?.get("description") ?: "",
        initialVersion = existing?.first?.get("version") ?: "1.0.0",
        initialLicense = existing?.first?.get("license") ?: "MIT",
        initialContent = existing?.second ?: "",
        onBack = { navController.safePopBackStack() },
        onExport = if (decoded != "new") {
            {
                try {
                    val zipFile = app.store.exportSkillZip(decoded)
                    shareSkillZip(context, zipFile, "分享技能 $decoded")
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else null,
        onSave = { name, description, version, license, content ->
            if (existing != null && decoded != name.trim()) {
                app.store.deleteSkill(decoded)
            }
            app.store.saveSkill(
                name = name.trim(),
                description = description.trim(),
                body = content,
                version = version.trim(),
                license = license.trim()
            )
            navController.safePopBackStack()
        }
    )
}

/**
 * 技能编辑页面纯 UI 组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditContent(
    isNew: Boolean,
    initialName: String,
    initialDescription: String,
    initialVersion: String = "1.0.0",
    initialLicense: String = "MIT",
    initialContent: String,
    onBack: () -> Unit,
    onExport: (() -> Unit)? = null,
    onSave: (name: String, description: String, version: String, license: String, content: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var version by remember { mutableStateOf(initialVersion) }
    var license by remember { mutableStateOf(initialLicense) }
    var content by remember { mutableStateOf(initialContent) }

    val kebabName = remember(name) { formatKebabCase(name) }
    val isNameCompliant = name.isNotBlank() && name == kebabName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建 Skill" else "编辑 Skill") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (onExport != null) {
                        IconButton(onClick = onExport) {
                            Icon(Icons.Filled.Share, "导出为 ZIP")
                        }
                    }
                    IconButton(onClick = {
                        val cleanName = formatKebabCase(name)
                        if (cleanName.isNotBlank()) {
                            onSave(
                                cleanName,
                                description.trim(),
                                version.trim().ifBlank { "1.0.0" },
                                license.trim().ifBlank { "MIT" },
                                content
                            )
                        }
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
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("技能标识 (kebab-case)") },
                placeholder = { Text("例如: markdown-table-formatter") },
                supportingText = {
                    if (name.isNotBlank() && !isNameCompliant) {
                        Text(
                            "推荐保存为: $kebabName",
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text("Agent 通过 use_skill 按此标识调用，仅限英文小写、数字与连字符")
                    }
                },
                trailingIcon = {
                    if (name.isNotBlank() && !isNameCompliant) {
                        IconButton(onClick = { name = kebabName }) {
                            Icon(Icons.Filled.AutoFixHigh, "自动转为 kebab-case")
                        }
                    }
                },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("版本 (version)") },
                    placeholder = { Text("1.0.0") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = license,
                    onValueChange = { license = it },
                    label = { Text("许可证 (license)") },
                    placeholder = { Text("MIT") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("描述（告诉 Agent 何时该使用这个 Skill）") },
                placeholder = { Text("清晰说明该技能的职能与激活场景") },
                minLines = 2
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("指令正文（Markdown 格式）") },
                placeholder = { Text("# Skill Instructions\n\n执行步骤与规范说明...") },
                minLines = 14,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Spacer(modifier = Modifier.height(ExpressiveTokens.FabSafeBottomPadding))
        }
    }
}

@Preview(showBackground = true, name = "Skills - Light")
@Composable
private fun SkillsPreviewLight() {
    val sampleSkills = listOf(
        SkillMeta(
            name = "markdown-table-formatter",
            description = "自动解析和格式化复杂的 Markdown 数据表格，保证移动端自适应阅读体验",
            version = "1.2.0",
            license = "MIT"
        ),
        SkillMeta(
            name = "web-search",
            description = "在搜索引擎中检索实时互联网资讯与相关官方技术文档",
            version = "1.0.0",
            license = "Apache-2.0"
        ),
        SkillMeta(
            name = "deep-research-assistant",
            description = "多轮深度推演与主题研究综合报告提取",
            version = "2.0.1",
            license = "MIT"
        )
    )
    AgentTheme(themeMode = "light") {
        SkillsContent(
            skills = sampleSkills,
            onOpenDrawer = {},
            onNewSkill = {},
            onSelectSkill = {},
            onDeleteSkill = {},
            onImportZip = {},
            onExportSkill = {},
            onExportAll = {}
        )
    }
}

@Preview(showBackground = true, name = "Skills - Dark")
@Composable
private fun SkillsPreviewDark() {
    val sampleSkills = listOf(
        SkillMeta(
            name = "web-search",
            description = "在搜索引擎中检索实时互联网资讯与相关文档",
            version = "1.0.0",
            license = "MIT"
        )
    )
    AgentTheme(themeMode = "dark") {
        SkillsContent(
            skills = sampleSkills,
            onOpenDrawer = {},
            onNewSkill = {},
            onSelectSkill = {},
            onDeleteSkill = {},
            onImportZip = {},
            onExportSkill = {},
            onExportAll = {}
        )
    }
}

@Preview(showBackground = true, name = "Skills - Empty")
@Composable
private fun SkillsEmptyPreview() {
    AgentTheme(themeMode = "light") {
        SkillsContent(
            skills = emptyList(),
            onOpenDrawer = {},
            onNewSkill = {},
            onSelectSkill = {},
            onDeleteSkill = {},
            onImportZip = {},
            onExportSkill = {},
            onExportAll = {}
        )
    }
}

@Preview(showBackground = true, name = "Skill Edit - Light")
@Composable
private fun SkillEditPreviewLight() {
    AgentTheme(themeMode = "light") {
        SkillEditContent(
            isNew = false,
            initialName = "summarize-text",
            initialDescription = "提取长文本核心要点并格式化输出摘要",
            initialVersion = "1.0.0",
            initialLicense = "MIT",
            initialContent = """
                # Summarize Skill
                请提取文本中的主要论点并分条归纳。
            """.trimIndent(),
            onBack = {},
            onExport = {},
            onSave = { _, _, _, _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Skill Edit - Dark")
@Composable
private fun SkillEditPreviewDark() {
    AgentTheme(themeMode = "dark") {
        SkillEditContent(
            isNew = true,
            initialName = "",
            initialDescription = "",
            initialVersion = "1.0.0",
            initialLicense = "MIT",
            initialContent = "",
            onBack = {},
            onExport = null,
            onSave = { _, _, _, _, _ -> }
        )
    }
}
