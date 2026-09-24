package com.example.myapplication.agent

import com.example.myapplication.device.DeviceController
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

class AndroidDeviceTools(private val controller: DeviceController) {
    private val lease = DeviceLease()

    fun session(conversationId: String): DeviceToolSession = object : DeviceToolSession {
        private val holder = UUID.randomUUID().toString()
        override val enabled: Boolean get() = controller.enabled

        override suspend fun execute(name: String, args: JsonObject, attach: ((File) -> String?)?): String {
            currentCoroutineContext().ensureActive()
            if (name == Tools.DEVICE_STATUS) return controller.status()
            check(enabled) { "设备控制已停用，请在设备控制页面开启" }
            val listOnly = name == Tools.DEVICE_SYSTEM && args["operation"]?.jsonPrimitive?.content == "list_apps"
            check(listOnly || lease.acquire(conversationId, holder)) { "另一会话正在使用手机，请等待它结束或先停止该任务" }
            val result = when (name) {
                Tools.DEVICE_OBSERVE -> controller.observe()
                Tools.DEVICE_ACTION -> controller.act(args)
                Tools.DEVICE_SYSTEM -> controller.systemAction(args)
                Tools.DEVICE_SCREENSHOT -> {
                    check(attach != null) { "当前模型调用不支持原生图片" }
                    val file = controller.screenshot()
                    try {
                        currentCoroutineContext().ensureActive()
                        attach(file) ?: error("当前模型无法读取截图，请选择支持图片的模型")
                    } finally { file.delete() }
                }
                else -> error("未知设备工具")
            }
            val error = runCatching { (Json.parseToJsonElement(result) as? JsonObject)?.get("error")?.jsonPrimitive?.content }.getOrNull()
            return if (error != null) "错误: $error" else result
        }

        override fun close() = lease.release(holder)
    }
}
