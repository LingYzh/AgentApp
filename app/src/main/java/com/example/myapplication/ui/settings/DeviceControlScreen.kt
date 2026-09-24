package com.example.myapplication.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.AgentApp
import com.example.myapplication.agent.DeviceTaskService
import com.example.myapplication.ui.components.UiScaffold
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AgentApp
    val device = app.deviceController
    var enabled by remember { mutableStateOf(device.enabled) }
    var status by remember { mutableStateOf(device.status()) }
    var masterError by remember { mutableStateOf<String?>(null) }
    var accessibilityError by remember { mutableStateOf<String?>(null) }
    var shizukuError by remember { mutableStateOf<String?>(null) }
    var taskError by remember { mutableStateOf<String?>(null) }

    val active by DeviceTaskService.active.collectAsState()
    val owner = LocalLifecycleOwner.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) taskError = "通知未授权，建议开启，以便操作其他应用时随时停止任务。"
        else taskError = null
    }

    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                status = device.status()
                enabled = device.enabled
                delay(1000)
            }
        }
    }

    val fields = runCatching { Json.parseToJsonElement(status).jsonObject }.getOrNull()
    val access = fields?.get("accessibility") as? JsonObject
    val shizuku = fields?.get("shizuku") as? JsonObject
    fun JsonObject?.yes(key: String) = this?.get(key)?.jsonPrimitive?.booleanOrNull == true

    UiScaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备控制") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 总开关栏目
            SettingsSectionCard(
                title = "允许 Agent 控制此手机",
                description = "读取界面、截图与操作应用；仍遵循会话权限和工具授权。",
                trailingHeader = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            try {
                                device.enabled = value
                                enabled = value
                                masterError = null
                                status = device.status()
                                if (!value) {
                                    DeviceTaskService.stopAll()
                                } else if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } catch (e: Exception) {
                                masterError = e.message ?: "操作失败"
                            }
                        }
                    )
                }
            ) {
                masterError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // 2. 无障碍服务栏目
            SettingsSectionCard(
                title = "1 · 无障碍服务",
                description = "在系统设置中启用 UAH 设备控制，用于读取控件、点击、输入和截图。安装来源受限时，需先在系统应用信息中允许受限设置。"
            ) {
                val accessConnected = access.yes("connected")
                val screenshotSupported = fields?.yes("screenshot_supported") == true

                Text(
                    "无障碍：${if (accessConnected) "已连接" else "未连接，请在系统设置中开启"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (accessConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "截图：${if (screenshotSupported) "可用" else "需要 Android 11 以上及无障碍连接"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (screenshotSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "安全窗口可能禁止截图；此接入不提供 root，也不能直接读取其他应用的私有文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(onClick = {
                    try {
                        accessibilityError = null
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (e: Exception) {
                        accessibilityError = e.message ?: "无法打开系统无障碍设置"
                    }
                }) {
                    Text("打开无障碍设置")
                }

                accessibilityError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // 3. Shizuku 栏目
            SettingsSectionCard(
                title = "2 · Shizuku（增强能力）",
                description = "先安装并启动 Shizuku，再授权 UAH。无需 root；重启手机后通常需要重新启动 Shizuku。未连接时仍可使用无障碍功能。"
            ) {
                val shizukuConnected = shizuku.yes("user_service_connected")
                val permissionGranted = shizuku.yes("permission_granted")
                val binderAvailable = shizuku.yes("binder_available")
                val shizukuStatusText = when {
                    shizukuConnected -> "已连接"
                    permissionGranted -> "已授权，等待连接服务"
                    binderAvailable -> "已启动，等待授权"
                    else -> "未启动或连接已断开"
                }

                Text(
                    "Shizuku：$shizukuStatusText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shizukuConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                shizuku?.get("error")?.jsonPrimitive?.contentOrNull?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try {
                            shizukuError = null
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/zh-hans/download/")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            shizukuError = e.message ?: "无法打开下载链接"
                        }
                    }) {
                        Text("下载 Shizuku")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                shizukuError = null
                                device.requestShizukuPermission()
                                status = device.status()
                            } catch (e: Exception) {
                                shizukuError = e.message ?: "请求授权失败"
                            }
                        },
                        enabled = enabled
                    ) {
                        Text("请求授权")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                shizukuError = null
                                device.connectShizuku()
                                status = device.status()
                            } catch (e: Exception) {
                                shizukuError = e.message ?: "连接服务失败"
                            }
                        },
                        enabled = enabled
                    ) {
                        Text("连接服务")
                    }
                }

                shizukuError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // 4. 任务运行与停止栏目
            SettingsSectionCard(
                title = "3 · 运行与停止",
                description = "亮屏并解锁后，在聊天中描述任务。Auto 可连续操作；Accept Edit 每次操作需审批，可在目标应用上方的 UAH 面板确认。运行中可通过悬浮按钮或通知停止，多个会话不会同时占用屏幕。"
            ) {
                Text(
                    "当前运行任务：$active 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                taskError = null
                                DeviceTaskService.stopAll()
                            } catch (e: Exception) {
                                taskError = e.message ?: "停止任务失败"
                            }
                        },
                        enabled = active > 0
                    ) {
                        Text("停止全部设备任务（$active）")
                    }

                    if (Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) {
                            Text("允许任务通知")
                        }
                    }
                }

                taskError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
