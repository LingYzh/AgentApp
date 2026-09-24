package com.example.myapplication

import android.app.UiAutomation
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.agent.*
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.PermissionMode
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceControlInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as AgentApp

    private fun requireEmulator() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone") || Build.HARDWARE.contains("ranchu")) {
            "This test is restricted to Android emulators"
        }
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        // Starting instrumentation kills the old target process; rebind its service in the
        // new instrumentation process without suppressing accessibility for the test.
        val component = "com.Ling.actant/com.example.myapplication.device.DeviceAccessibilityService"
        val resolver = app.contentResolver
        val existing = android.provider.Settings.Secure.getString(resolver, "enabled_accessibility_services")
            ?.takeUnless { it == "null" || it.isBlank() }?.split(":").orEmpty()
        val others = existing.filterNot { it == component }
        automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try {
            android.provider.Settings.Secure.putString(resolver, "enabled_accessibility_services", others.joinToString(":"))
            android.os.SystemClock.sleep(500)
            android.provider.Settings.Secure.putString(resolver, "enabled_accessibility_services", (others + component).joinToString(":"))
            android.provider.Settings.Secure.putInt(resolver, "accessibility_enabled", 1)
            android.os.SystemClock.sleep(500)
        } finally { automation.dropShellPermissionIdentity() }
    }

    private suspend fun observeReady(): JsonObject {
        repeat(30) {
            val state = Json.parseToJsonElement(app.deviceController.observe()).jsonObject
            if (state["snapshot_id"] != null && state["nodes"]?.jsonArray?.any { node ->
                node.jsonObject["text"]?.jsonPrimitive?.content?.contains("Device QA") == true
            } == true) return state
            delay(200)
        }
        error("Accessibility did not observe the QA activity: ${app.deviceController.status()}")
    }

    @Test fun realAccessibilityTextClickScreenshotAndStaleRejection() = runBlocking {
        requireEmulator()
        val device = app.deviceController
        val previous = device.enabled
        device.enabled = true
        val activity = instrumentation.startActivitySync(Intent(app, DeviceTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            delay(600)
            var state = observeReady()
            val input = state["nodes"]!!.jsonArray.firstOrNull { it.jsonObject["editable"]?.jsonPrimitive?.booleanOrNull == true }?.jsonObject
                ?: error("Missing editable input: $state")
            val set = Json.parseToJsonElement(device.act(buildJsonObject {
                put("action", "set_text"); put("snapshot_id", state["snapshot_id"]!!.jsonPrimitive.content)
                put("node", input["index"]!!.jsonPrimitive.content); put("text", "你好 UAH 123")
            })).jsonObject
            assertEquals(set.toString(), "accepted", set["status"]?.jsonPrimitive?.content)
            delay(250)
            state = observeReady()
            val button = state["nodes"]!!.jsonArray.firstOrNull { it.jsonObject["text"]?.jsonPrimitive?.content.equals("Apply QA text", ignoreCase = true) }?.jsonObject
                ?: error("Missing QA button: $state")
            val oldSnapshot = state["snapshot_id"]!!.jsonPrimitive.content
            val click = Json.parseToJsonElement(device.act(buildJsonObject {
                put("action", "click"); put("snapshot_id", oldSnapshot); put("node", button["index"]!!.jsonPrimitive.content)
            })).jsonObject
            assertEquals(click.toString(), "accepted", click["status"]?.jsonPrimitive?.content)
            delay(250)
            val observed = device.observe()
            assertTrue(observed, observed.contains("Result: 你好 UAH 123"))
            val stale = device.act(buildJsonObject { put("action", "back"); put("snapshot_id", oldSnapshot) })
            assertTrue(stale, Json.parseToJsonElement(stale).jsonObject.containsKey("error"))
            state = Json.parseToJsonElement(device.observe()).jsonObject
            val currentButton = state["nodes"]!!.jsonArray.first {
                it.jsonObject["text"]?.jsonPrimitive?.content.equals("Apply QA text", true)
            }.jsonObject["bounds"]!!.jsonObject
            val tap = device.act(buildJsonObject {
                put("action", "click"); put("snapshot_id", state["snapshot_id"]!!.jsonPrimitive.content)
                put("x", (currentButton["left"]!!.jsonPrimitive.int + currentButton["right"]!!.jsonPrimitive.int) / 2)
                put("y", (currentButton["top"]!!.jsonPrimitive.int + currentButton["bottom"]!!.jsonPrimitive.int) / 2)
            })
            assertEquals(tap, "accepted", Json.parseToJsonElement(tap).jsonObject["status"]?.jsonPrimitive?.content)
            state = Json.parseToJsonElement(device.observe()).jsonObject
            val scrollNode = state["nodes"]!!.jsonArray.first { it.jsonObject["scrollable"]?.jsonPrimitive?.booleanOrNull == true }.jsonObject
            val scroll = device.act(buildJsonObject {
                put("action", "scroll"); put("snapshot_id", state["snapshot_id"]!!.jsonPrimitive.content)
                put("node", scrollNode["index"]!!.jsonPrimitive.content); put("direction", "forward")
            })
            assertEquals(scroll, "accepted", Json.parseToJsonElement(scroll).jsonObject["status"]?.jsonPrimitive?.content)
            val file = device.screenshot()
            try {
                assertTrue(file.length() > 0)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                assertTrue(options.outWidth in 1..1600 && options.outHeight in 1..1600)
            } finally { file.delete() }
            state = Json.parseToJsonElement(device.observe()).jsonObject
            instrumentation.runOnMainSync {
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val layout = content.getChildAt(0) as android.view.ViewGroup
                (layout.getChildAt(0) as android.widget.TextView).text = "Changed externally"
            }
            delay(250)
            val changed = device.act(buildJsonObject {
                put("action", "back"); put("snapshot_id", state["snapshot_id"]!!.jsonPrimitive.content)
            })
            assertTrue(changed, Json.parseToJsonElement(changed).jsonObject.containsKey("error"))
            device.enabled = false
            assertTrue(device.act(buildJsonObject { put("action", "home"); put("snapshot_id", oldSnapshot) }).contains("disabled"))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            device.enabled = previous
        }
    }

    @Test fun realShizukuUidListingAndProtectedPackageRejection() = runBlocking {
        requireEmulator()
        val device = app.deviceController
        val previous = device.enabled
        device.enabled = true
        try {
            withContext(Dispatchers.Main) { device.connectShizuku() }
            withTimeout(15_000) {
                while (!Json.parseToJsonElement(device.status()).jsonObject["shizuku"]!!.jsonObject["user_service_connected"]!!.jsonPrimitive.boolean) delay(200)
            }
            val listing = device.systemAction(buildJsonObject { put("operation", "list_apps"); put("include_system", true) })
            val result = Json.parseToJsonElement(listing).jsonObject
            assertEquals(listing, "success", result["status"]?.jsonPrimitive?.content)
            assertEquals(2000, result["uid"]?.jsonPrimitive?.int)
            assertTrue(listing, listing.contains("com.Ling.actant"))
            val rejection = device.systemAction(buildJsonObject { put("operation", "force_stop"); put("package", "com.Ling.actant") })
            assertTrue(rejection, Json.parseToJsonElement(rejection).jsonObject.containsKey("error"))
            val invalid = device.systemAction(buildJsonObject { put("operation", "shell"); put("command", "id") })
            assertTrue(invalid, invalid.contains("error"))
        } finally { device.enabled = previous }
    }

    @Test fun overlayApprovalAndStopWorkAboveTargetActivity() = runBlocking {
        requireEmulator()
        val device = app.deviceController
        val previous = device.enabled
        device.enabled = true
        val activity = instrumentation.startActivitySync(Intent(app, DeviceTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val task = launch { awaitCancellation() }
        val token = "device-overlay-test"
        val session = app.deviceTools.session("device-overlay-test")
        try {
            observeReady()
            withContext(Dispatchers.Main) { DeviceTaskService.register(app, token, task) }
            delay(700)
            val state = observeReady()
            val button = state["nodes"]!!.jsonArray.first { it.jsonObject["text"]?.jsonPrimitive?.content.equals("Apply QA text", true) }.jsonObject
            val executor = ToolExecutor(app.store, permissionSession = PermissionSession(app.store,
                Conversation(permissionMode = PermissionMode.ACCEPT_EDIT), app.permissionCoordinator), deviceSession = session)
            val action = async { executor.execute(Tools.DEVICE_ACTION, buildJsonObject {
                put("action", "click"); put("snapshot_id", state["snapshot_id"]!!.jsonPrimitive.content)
                put("node", button["index"]!!.jsonPrimitive.content)
            }.toString()) }
            val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            suspend fun clickOverlay(text: String) {
                withTimeout(8_000) {
                    while (true) {
                        val windows = automation.windows
                        var clicked = false
                        try {
                            for (window in windows) {
                                if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                                val root = window.root ?: continue
                                try {
                                    val found = root.findAccessibilityNodeInfosByText(text)
                                    try {
                                        val node = found.firstOrNull { it.isClickable && it.text?.toString() == text }
                                        if (node != null) clicked = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                                    } finally { found.forEach { it.recycle() } }
                                } finally { root.recycle() }
                            }
                        } finally { windows.forEach { it.recycle() } }
                        if (clicked) break
                        delay(150)
                    }
                }
            }
            clickOverlay("允许本次")
            val result = withTimeout(8_000) { action.await() }
            assertFalse(result, result.startsWith("错误"))
            assertTrue(result, result.contains("accepted"))
            clickOverlay("停止 UAH 任务")
            withTimeout(3_000) { task.join() }
            assertTrue(task.isCancelled)
        } finally {
            task.cancel()
            withContext(Dispatchers.Main) { DeviceTaskService.finish(token); activity.finish() }
            session.close()
            device.enabled = previous
        }
    }

    @Test fun deviceGenerationSurvivesActivityOwnerAndResumesSameConversation() = runBlocking {
        requireEmulator()
        val device = app.deviceController
        val previousEnabled = device.enabled
        val originalConfig = app.store.loadConfig()
        val originalFactory = app.providerFactory
        val factoryField = AgentApp::class.java.getDeclaredField("providerFactory").apply { isAccessible = true }
        val reachedSecondRequest = CompletableDeferred<Unit>()
        val resumeAfterOwnerDestroyed = CompletableDeferred<Unit>()
        val progressed = CompletableDeferred<Unit>()
        var requestCount = 0
        val fake = object : com.example.myapplication.provider.ApiProvider {
            override suspend fun streamChat(
                config: com.example.myapplication.data.model.ProviderConfig,
                system: String,
                messages: List<com.example.myapplication.data.model.ChatMessage>,
                tools: List<com.example.myapplication.provider.ToolSpec>,
                onEvent: suspend (com.example.myapplication.provider.StreamEvent) -> Unit
            ) {
                if (requestCount++ == 0) {
                    check(tools.any { it.name == Tools.DEVICE_ACTION })
                    onEvent(com.example.myapplication.provider.StreamEvent.ToolCall("qa-launch", Tools.DEVICE_ACTION,
                        """{"action":"launch","package":"com.android.settings"}"""))
                    onEvent(com.example.myapplication.provider.StreamEvent.Done("tool_calls"))
                } else {
                    check(messages.any { it.role == "tool" && it.content.contains("accepted") })
                    reachedSecondRequest.complete(Unit)
                    resumeAfterOwnerDestroyed.await()
                    onEvent(com.example.myapplication.provider.StreamEvent.Text("设备后台任务继续运行"))
                    progressed.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        val factory = object : com.example.myapplication.provider.ProviderFactory() {
            override fun create(type: com.example.myapplication.data.model.ProviderType) = fake
        }
        val provider = com.example.myapplication.data.model.ProviderConfig(name = "Device lifecycle QA", models = listOf("device-qa"))
        val conversation = Conversation(title = "Device lifecycle QA", providerIdOverride = provider.id,
            modelOverride = "device-qa", permissionMode = PermissionMode.AUTO)
        val ownerStore = androidx.lifecycle.ViewModelStore()
        var vm: com.example.myapplication.ui.chat.ChatViewModel? = null
        val activity = instrumentation.startActivitySync(Intent(app, DeviceTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            device.enabled = true
            observeReady()
            app.store.saveConfig(originalConfig.copy(providers = originalConfig.providers + provider))
            app.store.saveConversation(conversation)
            factoryField.set(app, factory)
            withContext(Dispatchers.Main) {
                val sessions = com.example.myapplication.ui.chat.ChatSessions(androidx.lifecycle.SavedStateHandle())
                ownerStore.put("qa-sessions", sessions)
                vm = sessions.session(app, conversation.id, conversation.id, null)
            }
            withTimeout(5_000) { while (vm!!.currentModel.value.isBlank()) delay(50) }
            withContext(Dispatchers.Main) { vm!!.send("打开设置并等待") }
            withTimeout(12_000) { reachedSecondRequest.await() }
            withContext(Dispatchers.Main) { ownerStore.clear(); activity.finish() }
            assertTrue(vm!!.streaming.value)
            assertTrue(DeviceTaskService.active.value > 0)
            resumeAfterOwnerDestroyed.complete(Unit)
            withTimeout(5_000) { progressed.await() }
            withContext(Dispatchers.Main) {
                val reopened = com.example.myapplication.ui.chat.ChatSessions(androidx.lifecycle.SavedStateHandle())
                    .session(app, "reopened-${conversation.id}", conversation.id, null)
                assertSame(vm, reopened)
                DeviceTaskService.stopAll()
            }
            withTimeout(5_000) { while (vm!!.streaming.value) delay(50) }
            assertEquals(0, DeviceTaskService.active.value)
            assertTrue(app.store.loadConversation(conversation.id)!!.messages.any { it.content.contains("设备后台任务继续运行") })
        } finally {
            withContext(Dispatchers.Main) { vm?.stop(); ownerStore.clear(); activity.finish() }
            withTimeoutOrNull(5_000) { while (vm?.streaming?.value == true) delay(50) }
            factoryField.set(app, originalFactory)
            app.store.saveConfig(originalConfig)
            app.store.deleteConversation(conversation.id)
            device.enabled = previousEnabled
        }
    }
}
