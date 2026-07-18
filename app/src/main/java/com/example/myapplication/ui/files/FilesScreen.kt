package com.example.myapplication.ui.files

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilesViewModel(val app: AgentApp) : ViewModel() {
    private val _files = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val files = _files.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _files.value = app.store.listWorkspace().map { it to app.store.workspaceSize(it) }
        }
    }

    fun delete(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.store.deleteWorkspace(path)
            refresh()
        }
    }

    /** 把 SAF 选中的文件复制进工作区根目录 */
    fun import(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = app.contentResolver
                val name = resolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                } ?: "uploaded-${System.currentTimeMillis()}"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取所选文件")
                app.store.writeWorkspace(name, String(bytes, Charsets.UTF_8))
                _message.value = "已导入 $name"
            } catch (e: Exception) {
                _message.value = "导入失败: ${e.message}"
            }
            refresh()
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavHostController, openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val vm: FilesViewModel = viewModel(factory = viewModelFactory {
        initializer { FilesViewModel(app) }
    })
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val files by vm.files.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.import(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("文件工作区") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "菜单") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.UploadFile, "上传文件")
            }
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("工作区为空。Agent 生成的文件会出现在这里，也可以点右下角上传。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.first }) { (path, size) ->
                    Card(Modifier.fillMaxWidth().clickable {
                        navController.navigate(Routes.fileView(path))
                    }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.FileOpen, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(path, style = MaterialTheme.typography.titleSmall)
                                Text(formatSize(size), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.delete(path) }) {
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
fun FileViewScreen(navController: NavHostController, path: String) {
    val app = LocalContext.current.applicationContext as AgentApp
    val context = LocalContext.current
    var content by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var editBuffer by remember { mutableStateOf("") }

    val isImage = remember(path) {
        path.substringAfterLast('.', "").lowercase() in
            listOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    }

    LaunchedEffect(path) {
        if (isImage) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                content = app.store.readWorkspace(path, maxBytes = 512 * 1024)
            } catch (e: Exception) {
                loadError = e.message
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (content != null && !isImage) {
                        IconButton(onClick = {
                            if (editing) {
                                app.store.writeWorkspace(path, editBuffer)
                                content = editBuffer
                                editing = false
                            } else {
                                editBuffer = content ?: ""
                                editing = true
                            }
                        }) {
                            Icon(if (editing) Icons.Filled.Save else Icons.Filled.Edit, "编辑/保存")
                        }
                    }
                    IconButton(onClick = {
                        runCatching {
                            val file = app.store.workspaceFile(path)
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = if (isImage) "image/*" else "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享文件"))
                        }
                    }) { Icon(Icons.Filled.Share, "分享") }
                }
            )
        }
    ) { padding ->
        when {
            isImage -> {
                val file = remember(path) { app.store.workspaceFile(path) }
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    coil.compose.AsyncImage(
                        model = file,
                        contentDescription = path,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
            loadError != null -> Box(Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                Text("无法预览：$loadError\n（可尝试分享后用其他应用打开）",
                    color = MaterialTheme.colorScheme.error)
            }
            editing -> OutlinedTextField(
                value = editBuffer,
                onValueChange = { editBuffer = it },
                modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            content != null -> SelectionContainer {
                Text(
                    content!!,
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("加载中…")
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / 1024 / 1024} MB"
}
