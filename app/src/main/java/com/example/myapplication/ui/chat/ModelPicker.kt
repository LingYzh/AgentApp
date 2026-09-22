package com.example.myapplication.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.capabilitiesFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPicker(options: List<Pair<ProviderConfig, String>>, onSelect: (String, String) -> Unit,
    selectedProviderId: String? = null, selectedModel: String? = null,
    onManage: () -> Unit = {}, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(options, search) {
        options.filter { (provider, model) -> model.contains(search, true) || provider.name.contains(search, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 22.dp)) {
            PanelHeading("切换模型", onDismiss)
            Text("文件图标表示原生输入能力，不等于工作区工具能读取的文件类型。",
                Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextField(search, { search = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索模型") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (filtered.isEmpty()) item { Text("没有匹配的模型", Modifier.padding(vertical = 20.dp)) }
                filtered.groupBy { it.first.id }.forEach { (id, group) ->
                    item(key = "provider:$id") { Text(group.first().first.name.ifBlank { group.first().first.type.label },
                        Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(group, key = { "${it.first.id}:${it.second}" }) { (provider, model) ->
                        val selected = selectedProviderId == provider.id && selectedModel == model
                        Surface(onClick = { onSelect(provider.id, model); onDismiss() }, modifier = Modifier.animateItem().fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Memory, null, Modifier.padding(top = 5.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(model, style = MaterialTheme.typography.titleSmall)
                                    Text(provider.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ModelCapabilitiesRow(provider, model)
                                }
                                RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
            com.example.myapplication.ui.components.UiTextButton(onClick = { onDismiss(); onManage() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("管理模型配置")
            }
        }
    }
}

@Composable
internal fun PanelHeading(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "关闭", Modifier.size(22.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelCapabilitiesRow(provider: ProviderConfig, model: String) {
    val capabilities = provider.capabilitiesFor(model)
    val source = when (model) {
        in provider.capabilityOverrides -> "手动覆盖"
        in provider.discoveredCapabilities -> "接口发现"
        else -> "未声明"
    }
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("图片", Icons.Default.Image, capabilities.image),
                Triple("PDF", Icons.Default.PictureAsPdf, capabilities.pdf),
                Triple("音频", Icons.Default.AudioFile, capabilities.audio),
                Triple("视频", Icons.Default.VideoFile, capabilities.video)).forEach { (name, icon, enabled) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, "$name：${if (enabled) "支持原生输入" else "未声明支持"}", Modifier.size(18.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline)
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("原生输入能力 · $source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
