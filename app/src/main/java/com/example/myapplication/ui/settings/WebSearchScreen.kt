package com.example.myapplication.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.AgentApp
import com.example.myapplication.agent.WebTools
import com.example.myapplication.data.model.WebSearchConfig
import com.example.myapplication.data.model.WebSearchProvider
import com.example.myapplication.data.model.WebSearchService
import com.example.myapplication.ui.components.PrototypeTextField
import com.example.myapplication.ui.components.UiScaffold
import com.example.myapplication.ui.theme.ExpressiveTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(onBack: () -> Unit) {
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
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置")
                }
            },
            actions = {
                TextButton(
                    enabled = !busy && !invalid,
                    onClick = {
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
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("保存")
                }
            }
        )
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 服务选择及启用
            SettingsSectionCard(
                title = "服务选择与启用",
                description = "所有 Agent 共用此服务，各自通过 search 工具开关控制是否允许搜索。",
                trailingHeader = {
                    Switch(
                        checked = enabled,
                        enabled = !busy,
                        onCheckedChange = {
                            enabled = it
                            feedback = null
                        }
                    )
                }
            ) {
                Box {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { menu = true }
                    ) {
                        Text(provider.label)
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false }
                    ) {
                        WebSearchProvider.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = {
                                    draft = draft.copy(provider = item)
                                    menu = false
                                    feedback = null
                                    result = null
                                }
                            )
                        }
                    }
                }
                Text(
                    text = provider.help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. 连接参数
            SettingsSectionCard(
                title = "连接参数",
                description = "各服务配置独立保留，仅使用当前选择的服务。API Key 请从所选服务获取。"
            ) {
                if (provider == WebSearchProvider.SERPAPI) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("google" to "Google", "bing" to "Bing", "baidu" to "百度").forEach { (id, label) ->
                            FilterChip(
                                selected = service.engine == id,
                                enabled = !busy,
                                onClick = {
                                    updateService(service.copy(engine = id))
                                    result = null
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                if (provider != WebSearchProvider.SEARXNG) {
                    PrototypeTextField(
                        value = service.apiKey,
                        onValueChange = {
                            updateService(service.copy(apiKey = it))
                            result = null
                            feedback = null
                        },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy,
                        singleLine = true
                    )
                }
                if (provider == WebSearchProvider.GOOGLE) {
                    PrototypeTextField(
                        value = service.searchEngineId,
                        onValueChange = {
                            updateService(service.copy(searchEngineId = it))
                            result = null
                        },
                        label = { Text("搜索引擎 ID（cx）") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        singleLine = true
                    )
                }
                PrototypeTextField(
                    value = address,
                    onValueChange = {
                        updateService(service.copy(baseUrl = it))
                        feedback = null
                        result = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (provider == WebSearchProvider.SEARXNG) "SearXNG 实例地址" else "API 完整地址（可选）") },
                    placeholder = { Text(provider.defaultUrl.ifBlank { "https://search.example.com" }) },
                    supportingText = {
                        Text(
                            if (invalid) "请输入 HTTP/HTTPS 地址，不含账号、查询参数或片段。"
                            else if (provider == WebSearchProvider.SEARXNG) "支持实例根地址、子路径或 /search 地址。"
                            else "留空使用官方地址；可填写兼容同一协议的完整接口地址。"
                        )
                    },
                    isError = invalid,
                    enabled = !busy,
                    singleLine = true
                )

                feedback?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("保存失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. 测试搜索
            SettingsSectionCard(
                title = "测试搜索",
                description = "测试使用当前填写的配置；修改后仍需点击顶部“保存”。"
            ) {
                PrototypeTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        result = null
                    },
                    label = { Text("测试关键词") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true
                )
                OutlinedButton(
                    enabled = !busy && config.isConfigured && query.isNotBlank(),
                    onClick = {
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
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(if (busy) "处理中…" else "测试搜索")
                }

                result?.let { resText ->
                    Card(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (resText.startsWith("测试失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 4. 网页读取说明
            SettingsSectionCard(
                title = "网页读取 fetch",
                description = "无需搜索服务即可读取网页和文本。它不执行网页 JavaScript、不共享浏览器登录状态；可在 Agent 工具中单独关闭。"
            ) {}
        }
    }
}
