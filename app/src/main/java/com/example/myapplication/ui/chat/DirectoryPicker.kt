package com.example.myapplication.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class DirectoryShortcut(val label: String, val path: String)

private data class DirectoryListing(
    val directory: File?,
    val children: List<File> = emptyList(),
    val error: String? = null
)

@Composable
internal fun DirectoryPicker(
    title: String,
    initialDirectory: String,
    shortcuts: List<DirectoryShortcut>,
    onCancel: () -> Unit,
    onSelect: (String) -> Unit
) {
    var currentPath by rememberSaveable(initialDirectory) { mutableStateOf(initialDirectory) }
    var listing by remember(currentPath) { mutableStateOf(DirectoryListing(null)) }
    var loading by remember(currentPath) { mutableStateOf(true) }
    val listState = rememberLazyListState()
    BackHandler(onBack = onCancel)

    LaunchedEffect(currentPath) {
        loading = true
        listState.scrollToItem(0)
        listing = withContext(Dispatchers.IO) { loadDirectoryListing(currentPath) }
        loading = false
    }

    val currentDirectory = listing.directory
    val breadcrumbs = remember(currentDirectory?.path) { currentDirectory?.let(::directoryBreadcrumbs).orEmpty() }
    val canSelect = currentDirectory != null && listing.error == null && !loading
    val parent = (currentDirectory ?: File(currentPath).absoluteFile)
        .parentFile?.takeUnless { it.path == File(currentPath).absolutePath }

    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "返回权限与目录") }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在读取目录…", Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    breadcrumbs.forEachIndexed { index, directory ->
                        if (index > 0) Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            directoryLabel(directory),
                            Modifier.padding(vertical = 8.dp).then(
                                if (index == breadcrumbs.lastIndex) Modifier
                                else Modifier.clickable { currentPath = directory.path }
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index == breadcrumbs.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            currentDirectory?.let { directory ->
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(directory.path, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { parent?.let { currentPath = it.path } }, enabled = parent != null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("上一级")
                            }
                        }
                    }
                }
            }
            if (currentDirectory == null && parent != null) {
                item {
                    OutlinedButton(onClick = { currentPath = parent.path }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("返回上一级目录")
                    }
                }
            }
            if (shortcuts.isNotEmpty()) {
                item {
                    Text("常用位置", Modifier.padding(top = 14.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shortcuts.forEach { shortcut ->
                            Surface(onClick = { currentPath = shortcut.path }, shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (shortcut.label == "共享存储") Icons.Default.Storage else Icons.Default.Folder,
                                        contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(shortcut.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            listing.error?.let { message ->
                item {
                    Text(message, Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!loading && listing.error == null && listing.children.isEmpty()) {
                item {
                    Text("当前目录没有可浏览的子目录。你仍可以选择当前目录。",
                        Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            items(listing.children, key = { it.path }) { child ->
                Surface(onClick = { currentPath = child.path }, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(child.name.ifBlank { child.path }, Modifier.weight(1f).padding(horizontal = 10.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.ChevronRight, "打开", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text("进入文件夹后，选择此目录。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
                Button(onClick = { currentDirectory?.path?.let(onSelect) }, enabled = canSelect,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("选择此目录") }
            }
        }
    }
}

private fun loadDirectoryListing(path: String): DirectoryListing {
    val directory = runCatching { canonicalDirectory(path) }.getOrElse {
        return DirectoryListing(null, error = "无法打开目录：${it.message ?: "路径无效或没有读取权限"}")
    }
    val children = runCatching {
        directory.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?.toList()
            ?: throw IllegalStateException("系统拒绝列出此目录，或目录暂时不可用")
    }.getOrElse {
        return DirectoryListing(directory, error = "无法列出目录：${it.message ?: "请检查读取权限"}")
    }
    return DirectoryListing(directory, children)
}

private fun canonicalDirectory(path: String): File {
    require(path.isNotBlank()) { "路径为空" }
    val raw = File(path)
    require(raw.isAbsolute) { "必须使用绝对路径" }
    val canonical = raw.canonicalFile
    require(canonical.exists()) { "目录不存在：${canonical.path}" }
    require(canonical.isDirectory) { "不是目录：${canonical.path}" }
    require(canonical.canRead()) { "没有读取权限：${canonical.path}" }
    return canonical
}

private fun directoryBreadcrumbs(directory: File): List<File> {
    val result = mutableListOf<File>()
    var current: File? = directory
    while (current != null) {
        result += current
        val parent = current.parentFile
        current = parent?.takeUnless { it == current }
    }
    return result.asReversed()
}

private fun directoryLabel(directory: File): String = directory.name.ifBlank { directory.path }
