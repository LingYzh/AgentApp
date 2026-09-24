package com.example.myapplication.device

import android.content.Context
import android.os.Build
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Public facade for device automation and privileged operations.
 * Coordinates accessibility node observation, gestures, screenshots,
 * and privileged Shizuku operations through a serialized mutex.
 */
class DeviceController(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val controllerMutex = Mutex()
    private val accessibilityController = AccessibilityController(appContext)
    private val shizukuManager = ShizukuManager(appContext) { enabled }
    private val systemActionExecutor = Executors.newCachedThreadPool()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var quarantinedFuture: Future<*>? = null

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (!value) {
                cleanupScope.launch {
                    controllerMutex.withLock {
                        if (!enabled) shizukuManager.disconnect()
                    }
                }
            }
        }

    private fun checkQuarantine(): String? {
        val pending = quarantinedFuture
        if (pending != null) {
            if (!pending.isDone) {
                return "Device controller is quarantined: a previous privileged system action is still completing or hung in background. All device operations are rejected to prevent overlap."
            } else {
                quarantinedFuture = null
            }
        }
        return null
    }

    fun status(): String {
        val isEnabled = enabled
        val isAccessConnected = DeviceAccessibilityService.isConnected.value
        val screenshotSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isAccessConnected
        val shizukuStatus = shizukuManager.getStatusJson()
        val isQuarantined = quarantinedFuture?.isDone == false

        return buildJsonObject {
            put("enabled", isEnabled)
            put("quarantined", isQuarantined)
            put("accessibility_connected", isAccessConnected)
            put("accessibility", buildJsonObject {
                put("connected", isAccessConnected)
                put("service_available", DeviceAccessibilityService.instance != null)
                put("session_id", DeviceAccessibilityService.serviceSessionId)
                put("state_version", DeviceAccessibilityService.stateVersion)
                DeviceAccessibilityService.lastForegroundPackage?.let {
                    put("last_foreground_package", it)
                }
            })
            put("screenshot_supported", screenshotSupported)
            put("shizuku", shizukuStatus)
            put("limitations", buildJsonArray {
                add("Accessibility service must be enabled manually by the user in Android Accessibility Settings.")
                add("Screenshots require Android 11+ (API 30+) and cannot capture secure windows (FLAG_SECURE).")
                add("Lockscreen cannot be bypassed.")
                add("UI actions reject stale snapshots when foreground package, window, or content state changes.")
                add("Shizuku operates with shell privileges (UID 2000) and cannot read private files of other apps.")
            })
        }.toString()
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun connectShizuku() {
        shizukuManager.connect()
    }

    suspend fun observe(): String {
        return controllerMutex.withLock {
            currentCoroutineContext().ensureActive()
            checkQuarantine()?.let { errMsg ->
                return@withLock buildJsonObject { put("error", errMsg) }.toString()
            }
            if (!enabled) {
                return@withLock buildJsonObject {
                    put("error", "Device control is globally disabled. Enable it in device settings first.")
                }.toString()
            }

            val service = DeviceAccessibilityService.instance
                ?: return@withLock buildJsonObject {
                    put("error", "Accessibility service not connected. Please enable Used AI Harness in Android Accessibility Settings.")
                }.toString()

            try {
                accessibilityController.observe(service)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                buildJsonObject {
                    put("error", "Failed to observe screen: ${t.message}")
                }.toString()
            }
        }
    }

    suspend fun act(args: JsonObject): String {
        return controllerMutex.withLock {
            currentCoroutineContext().ensureActive()
            checkQuarantine()?.let { errMsg ->
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", errMsg)
                }.toString()
            }
            if (!enabled) {
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "Device control is globally disabled. Enable it in device settings first.")
                }.toString()
            }

            val service = DeviceAccessibilityService.instance
                ?: return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "Accessibility service not connected. Please enable Used AI Harness in Android Accessibility Settings.")
                }.toString()

            try {
                accessibilityController.act(service, args)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                buildJsonObject {
                    put("status", "error")
                    put("error", "Action execution failed: ${t.message}")
                }.toString()
            }
        }
    }

    suspend fun screenshot(): File {
        return controllerMutex.withLock {
            currentCoroutineContext().ensureActive()
            checkQuarantine()?.let { errMsg ->
                throw IllegalStateException(errMsg)
            }
            if (!enabled) {
                throw IllegalStateException("Device control is globally disabled. Enable it in device settings first.")
            }

            val service = DeviceAccessibilityService.instance
                ?: throw IllegalStateException("Accessibility service not connected. Please enable Used AI Harness in Android Accessibility Settings.")

            try {
                accessibilityController.screenshot(service)
            } catch (c: CancellationException) {
                throw c
            }
        }
    }

    suspend fun systemAction(args: JsonObject): String {
        return controllerMutex.withLock {
            currentCoroutineContext().ensureActive()
            checkQuarantine()?.let { errMsg ->
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", errMsg)
                }.toString()
            }
            if (!enabled) {
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "Device control is globally disabled. Enable it in device settings first.")
                }.toString()
            }

            val operation = args["operation"]?.jsonPrimitive?.contentOrNull
                ?: args["action"]?.jsonPrimitive?.contentOrNull
                ?: return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "Missing 'operation' parameter for system action")
                }.toString()

            if (operation !in DeviceActionValidator.ALLOWED_SYSTEM_ACTIONS) {
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "System operation '$operation' is not allowed. Allowed: ${DeviceActionValidator.ALLOWED_SYSTEM_ACTIONS}")
                }.toString()
            }

            if (!shizukuManager.isBinderAvailable()) {
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "Shizuku binder is not available. Please ensure Shizuku app is running.")
                }.toString()
            }

            if (!shizukuManager.isPermissionGranted()) {
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "Shizuku permission is not granted. Please request permission first.")
                }.toString()
            }

            if (shizukuManager.userServiceProxy == null) {
                withContext(Dispatchers.Main) { shizukuManager.connect() }
                val deadline = android.os.SystemClock.elapsedRealtime() + 4000
                while (shizukuManager.userServiceProxy == null && enabled &&
                    android.os.SystemClock.elapsedRealtime() < deadline) delay(50)
                check(enabled) { "Device control was disabled while connecting" }
            }

            val proxy = shizukuManager.userServiceProxy
                ?: return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "Shizuku privileged UserService is not connected. ${shizukuManager.lastError ?: ""}".trim())
                }.toString()

            val requestId = UUID.randomUUID().toString()
            val future = systemActionExecutor.submit<String> {
                when (operation) {
                    "list_apps" -> {
                        val onlyLaunchable = args["only_launchable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
                        val includeSystem = args["include_system"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                        proxy.listApps(requestId, onlyLaunchable, includeSystem)
                    }

                    "force_stop" -> {
                        val pkg = args["package"]?.jsonPrimitive?.contentOrNull
                            ?: return@submit buildJsonObject {
                                put("status", "error")
                                put("error", "Missing 'package' parameter for force_stop")
                            }.toString()
                        proxy.forceStop(requestId, pkg)
                    }

                    "open_settings" -> {
                        proxy.openSettings(requestId)
                    }

                    "keyevent" -> {
                        val key = args["key"]?.jsonPrimitive?.contentOrNull
                        val keyCode = args["key_code"]?.jsonPrimitive?.intOrNull
                        val resolved = DeviceActionValidator.resolveKeyCode(key, keyCode)
                            ?: return@submit buildJsonObject {
                                put("status", "error")
                                put("error", "Invalid key/key_code. Only HOME(3), BACK(4), APP_SWITCH(187) are permitted.")
                            }.toString()
                        proxy.keyevent(requestId, resolved)
                    }

                    else -> {
                        buildJsonObject {
                            put("status", "error")
                            put("error", "Unsupported system operation: $operation")
                        }.toString()
                    }
                }
            }

            try {
                val startTime = android.os.SystemClock.elapsedRealtime()
                val timeoutMs = 8000L
                while (!future.isDone) {
                    currentCoroutineContext().ensureActive()
                    if (android.os.SystemClock.elapsedRealtime() - startTime > timeoutMs) {
                        throw TimeoutException("System operation '$operation' timed out after 8s")
                    }
                    delay(50L)
                }
                currentCoroutineContext().ensureActive()
                return@withLock future.get()
            } catch (c: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        systemActionExecutor.submit<Boolean> { proxy.cancel(requestId) }.get(1500, TimeUnit.MILLISECONDS)
                    } catch (_: Throwable) {}

                    try {
                        future.get(6, TimeUnit.SECONDS)
                    } catch (_: Throwable) {}
                }
                if (!future.isDone) {
                    quarantinedFuture = future
                }
                throw c
            } catch (t: Throwable) {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        systemActionExecutor.submit<Boolean> { proxy.cancel(requestId) }.get(1500, TimeUnit.MILLISECONDS)
                    } catch (_: Throwable) {}

                    try {
                        future.get(6, TimeUnit.SECONDS)
                    } catch (_: Throwable) {}
                }
                if (!future.isDone) {
                    quarantinedFuture = future
                }
                return@withLock buildJsonObject {
                    put("status", "error")
                    put("error", "System operation '$operation' failed: ${t.message}")
                }.toString()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "device_control_prefs"
        private const val KEY_ENABLED = "device_control_enabled"
    }
}
