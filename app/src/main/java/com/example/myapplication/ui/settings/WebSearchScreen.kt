package com.example.myapplication.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.AgentApp
import com.example.myapplication.agent.WebTools
import com.example.myapplication.data.model.WebSearchConfig
import com.example.myapplication.data.model.WebSearchProvider
import com.example.myapplication.data.model.WebSearchService
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.myapplication.ui.components.PrototypeTextField
import com.example.myapplication.ui.components.UiScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(openDrawer: () -> Unit) {
    val app = LocalContext.current.applicationContext as AgentApp
    val initial = remember { app.store.loadConfig().webSearch }
    var draft by rememberSaveable(stateSaver = Saver<WebSearchConfig, String>(
        save = { Json.encodeToString(it) }, restore = { Json.decodeFromString(it) }
    )) { mutableStateOf(initial) }
    var menu by remember { mutableStateOf(false) }
    val provider = draft.provider
    val service = draft.service()
    val address = service.baseUrl
    fun updateService(value: WebSearchService) {
        draft = draft.copy(services = draft.services + (provider to value))
    }
    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var query by rememberSaveable { mutableStateOf("Android") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val config = draft.copy(enabled = enabled)
    val invalid = address.isNotBlank() && config.endpoint() == null

    UiScaffold(topBar = {
        TopAppBar(
            title = { Text("网络搜索服务") },
            navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Default.Menu, "菜单") } },
            actions = {
                TextButton(enabled = !busy && !invalid, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            withContext(Dispatchers.IO) {
                                app.store.saveConfig(app.store.loadConfig().copy(webSearch = config))
                            }
                            feedback = if (config.isConfigured) "已保存，Agent 可启用 search" else "已保存，search 当前不可用"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            feedback = "保存失败：${e.message}"
                        } finally { busy = false }
                    }
                }) { Text("保存") }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box {
                OutlinedButton(enabled = !busy, onClick = { menu = true }) { Text(provider.label) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    WebSearchProvider.entries.forEach { item ->
                        DropdownMenuItem(text = { Text(item.label) }, onClick = {
                            draft = draft.copy(provider = item)
                            menu = false; feedback = null; result = null
                        })
                    }
                }
            }
            Text(provider.help, style = MaterialTheme.typography.bodySmall)
            if (provider == WebSearchProvider.SERPAPI) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("google" to "Google", "bing" to "Bing", "baidu" to "百度").forEach { (id, label) ->
                        FilterChip(selected = service.engine == id, enabled = !busy,
                            onClick = { updateService(service.copy(engine = id)); result = null }, label = { Text(label) })
                    }
                }
            }
            if (provider != WebSearchProvider.SEARXNG) {
                PrototypeTextField(value = service.apiKey, onValueChange = {
                    updateService(service.copy(apiKey = it)); result = null; feedback = null
                }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
            }
            if (provider == WebSearchProvider.GOOGLE) {
                PrototypeTextField(value = service.searchEngineId, onValueChange = {
                    updateService(service.copy(searchEngineId = it)); result = null
                }, label = { Text("搜索引擎 ID（cx）") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            }
            Text("所有 Agent 共用此服务，各自通过 search 工具开关控制是否允许搜索。", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("启用搜索服务", modifier = Modifier.weight(1f).padding(top = 12.dp))
                Switch(checked = enabled, enabled = !busy, onCheckedChange = { enabled = it; feedback = null })
            }
            PrototypeTextField(
                value = address,
                onValueChange = { updateService(service.copy(baseUrl = it)); feedback = null; result = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (provider == WebSearchProvider.SEARXNG) "SearXNG 实例地址" else "API 完整地址（可选）") },
                placeholder = { Text(provider.defaultUrl.ifBlank { "https://search.example.com" }) },
                supportingText = { Text(if (invalid) "请输入 HTTP/HTTPS 地址，不含账号、查询参数或片段。"
                    else if (provider == WebSearchProvider.SEARXNG) "支持实例根地址、子路径或 /search 地址。"
                    else "留空使用官方地址；可填写兼容同一协议的完整接口地址。") },
                isError = invalid,
                enabled = !busy,
                singleLine = true
            )
            Text("各服务配置独立保留，仅使用当前选择的服务。API Key 请从所选服务获取。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("测试搜索", style = MaterialTheme.typography.titleMedium)
            PrototypeTextField(value = query, onValueChange = { query = it; result = null },
                label = { Text("测试关键词") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedButton(enabled = !busy && config.isConfigured && query.isNotBlank(), onClick = {
                scope.launch {
                    busy = true
                    result = null
                    try {
                        val response = WebTools().search(config, query)
                        val rows = Json.parseToJsonElement(response).jsonObject["results"]!!.jsonArray
                        result = "连接成功，返回 ${rows.size} 条结果" + rows.joinToString("", prefix = "\n") {
                            val row = it.jsonObject
                            "\n${row["title"]?.jsonPrimitive?.content.orEmpty()}\n${row["url"]?.jsonPrimitive?.content.orEmpty()}\n"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        result = "测试失败：${e.message}"
                    } finally { busy = false }
                }
            }) { Text(if (busy) "处理中…" else "测试搜索") }
            Text("测试使用当前填写的配置；修改后仍需点击顶部“保存”。", style = MaterialTheme.typography.bodySmall)
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            Text("网页读取 fetch", style = MaterialTheme.typography.titleMedium)
            Text("无需搜索服务即可读取网页和文本。它不执行网页 JavaScript、不共享浏览器登录状态；可在 Agent 工具中单独关闭。")
        }
    }
}
