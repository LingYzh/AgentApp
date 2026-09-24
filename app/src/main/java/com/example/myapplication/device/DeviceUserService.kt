package com.example.myapplication.device

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.Parcel
import android.os.Process
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Privileged backend service executed via Shizuku UserService.
 * Runs in a separate process with shell or root UID.
 * Supports manual Binder transactions for destroy, UID query, app listing,
 * force-stopping applications, keyevent injection, and bounded request cancellation.
 */
class DeviceUserService : Binder, IDeviceUserService {

    private var context: Context? = null

    private class RequestState {
        val lock = Any()
        @Volatile var isCancelled = false
        @Volatile var process: java.lang.Process? = null
    }

    private val maxTrackedRequests = 300
    private val requestStates = object : LinkedHashMap<String, RequestState>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RequestState>?): Boolean {
            return size > maxTrackedRequests
        }
    }
    private val statesLock = Any()

    private fun getOrCreateState(requestId: String): RequestState {
        synchronized(statesLock) {
            return requestStates.getOrPut(requestId) { RequestState() }
        }
    }

    private fun finishRequest(requestId: String) {
        val state = synchronized(statesLock) { requestStates[requestId] } ?: return
        synchronized(state.lock) { state.process = null }
    }

    constructor() : super()

    constructor(context: Context?) : super() {
        this.context = context
    }

    override fun asBinder(): Binder = this

    override fun getUid(): Int = Process.myUid()

    override fun cancel(requestId: String): Boolean {
        val state = getOrCreateState(requestId)
        synchronized(state.lock) {
            state.isCancelled = true
            val proc = state.process
            if (proc != null) {
                proc.terminateProcess()
                try {
                    proc.awaitExit(1000)
                } catch (_: InterruptedException) {}
            }
        }
        return true
    }

    override fun listApps(requestId: String, onlyLaunchable: Boolean, includeSystem: Boolean): String {
        val state = getOrCreateState(requestId)

        synchronized(state.lock) {
            if (state.isCancelled) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "list_apps cancelled before execution")
                    put("cancelled", true)
                    put("uid", getUid())
                }.toString()
            }
        }

        val packages = mutableSetOf<String>()
        var usedFallback = false
        var fallbackError: String? = null

        // 1. Try Context PackageManager if available
        try {
            val pm = context?.packageManager
            if (pm != null) {
                val installed = pm.getInstalledApplications(0)
                for (app in installed) {
                    if (state.isCancelled) break
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!isSystem || includeSystem) {
                        if (!onlyLaunchable || pm.getLaunchIntentForPackage(app.packageName) != null) {
                            packages.add(app.packageName)
                        }
                    }
                    if (packages.size >= 250) break
                }
            }
        } catch (_: Throwable) {
            // Context resolution failure in Shizuku daemon process
        }

        if (state.isCancelled) {
            finishRequest(requestId)
            return buildJsonObject {
                put("status", "error")
                put("error", "list_apps cancelled")
                put("cancelled", true)
                put("uid", getUid())
            }.toString()
        }

        // 2. Fallback to /system/bin/pm list packages if context query was empty
        if (packages.isEmpty()) {
            usedFallback = true
            val process: java.lang.Process
            synchronized(state.lock) {
                if (state.isCancelled) {
                    finishRequest(requestId)
                    return buildJsonObject {
                        put("status", "error")
                        put("error", "list_apps cancelled")
                        put("cancelled", true)
                        put("uid", getUid())
                    }.toString()
                }
                try {
                    val cmd = mutableListOf("/system/bin/pm", "list", "packages")
                    if (!includeSystem) {
                        cmd.add("-3") // third-party apps only
                    }
                    process = ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start()
                    state.process = process
                } catch (t: Throwable) {
                    finishRequest(requestId)
                    return buildJsonObject {
                        put("status", "error")
                        put("error", "Failed to start pm list packages: ${t.message}")
                        put("uid", getUid())
                    }.toString()
                }
            }

            // Drain while waiting: even bounded package output can fill the OS pipe.
            val output = BoundedOutput(process)
            try {
                val finished = process.awaitExit(5000)
                if (!finished) {
                    process.terminateProcess()
                    process.awaitExit(1000)
                    return buildJsonObject {
                        put("status", "error")
                        put("error", "pm list packages timed out after 5s")
                        put("uid", getUid())
                    }.toString()
                }

                if (state.isCancelled) {
                    return buildJsonObject {
                        put("status", "error")
                        put("error", "list_apps cancelled")
                        put("cancelled", true)
                        put("uid", getUid())
                    }.toString()
                }

                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    return buildJsonObject {
                        put("status", "error")
                        put("error", "pm list packages failed with exit code $exitCode")
                        put("uid", getUid())
                    }.toString()
                }

                output.text().lineSequence().filter { it.startsWith("package:") }.take(250).forEach { line ->
                    val pkg = line.removePrefix("package:").trim()
                    if (!state.isCancelled && DeviceActionValidator.isValidPackageName(pkg)) packages.add(pkg)
                }
            } catch (t: Throwable) {
                fallbackError = t.message
            } finally {
                if (process.isRunning()) process.terminateProcess()
                process.inputStream.close()
                finishRequest(requestId)
            }
        } else {
            finishRequest(requestId)
        }

        if (state.isCancelled) {
            return buildJsonObject {
                put("status", "error")
                put("error", "list_apps cancelled")
                put("cancelled", true)
                put("uid", getUid())
            }.toString()
        }

        if (packages.isEmpty() && fallbackError != null) {
            return buildJsonObject {
                put("status", "error")
                put("error", "Failed to list packages: $fallbackError")
                put("uid", getUid())
            }.toString()
        }

        val json = buildJsonObject {
            put("status", "success")
            put("uid", getUid())
            put("count", packages.size)
            if (usedFallback) {
                put("fallback_mode", true)
                if (onlyLaunchable) {
                    put("note", "Launchable activity filtering is unavailable in fallback mode; all matching packages returned.")
                }
            }
            put("packages", buildJsonArray {
                packages.sorted().forEach { add(it) }
            })
        }
        return json.toString()
    }

    override fun forceStop(requestId: String, packageName: String): String {
        val state = getOrCreateState(requestId)

        if (!DeviceActionValidator.isValidPackageName(packageName)) {
            return buildJsonObject {
                put("status", "error")
                put("error", "Invalid package name: $packageName")
                put("uid", getUid())
            }.toString()
        }

        if (DeviceActionValidator.isProtectedPackage(packageName)) {
            return buildJsonObject {
                put("status", "error")
                put("error", "Package '$packageName' is protected and cannot be force-stopped")
                put("uid", getUid())
            }.toString()
        }

        val process: java.lang.Process
        synchronized(state.lock) {
            if (state.isCancelled) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "force_stop cancelled before execution")
                    put("cancelled", true)
                    put("uid", getUid())
                }.toString()
            }
            try {
                process = ProcessBuilder(listOf("/system/bin/am", "force-stop", packageName))
                    .redirectErrorStream(true)
                    .start()
                state.process = process
            } catch (t: Throwable) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "Failed to start force-stop $packageName: ${t.message}")
                    put("uid", getUid())
                }.toString()
            }
        }

        return try {
            val finished = process.awaitExit(5000)
            if (!finished) {
                process.terminateProcess()
                process.awaitExit(1000)
                return buildJsonObject {
                    put("status", "error")
                    put("error", "force-stop timed out after 5s")
                    put("uid", getUid())
                }.toString()
            }

            if (state.isCancelled) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "force_stop cancelled")
                    put("cancelled", true)
                    put("uid", getUid())
                }.toString()
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "am force-stop failed with exit code $exitCode")
                    put("package", packageName)
                    put("uid", getUid())
                }.toString()
            }
            buildJsonObject {
                put("status", "success")
                put("package", packageName)
                put("exit_code", exitCode)
                put("uid", getUid())
            }.toString()
        } catch (t: Throwable) {
            buildJsonObject {
                put("status", "error")
                put("error", "Failed to force-stop $packageName: ${t.message}")
                put("uid", getUid())
            }.toString()
        } finally {
            finishRequest(requestId)
        }
    }

    override fun keyevent(requestId: String, keyCode: Int): String {
        val state = getOrCreateState(requestId)

        if (keyCode !in DeviceActionValidator.ALLOWED_KEY_CODES) {
            return buildJsonObject {
                put("status", "error")
                put("error", "Keycode $keyCode is not allowed. Only HOME(3), BACK(4), APP_SWITCH(187) are permitted.")
                put("uid", getUid())
            }.toString()
        }

        val process: java.lang.Process
        synchronized(state.lock) {
            if (state.isCancelled) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "keyevent cancelled before execution")
                    put("cancelled", true)
                    put("uid", getUid())
                }.toString()
            }
            try {
                process = ProcessBuilder(listOf("/system/bin/input", "keyevent", keyCode.toString()))
                    .redirectErrorStream(true)
                    .start()
                state.process = process
            } catch (t: Throwable) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "Failed to start keyevent $keyCode: ${t.message}")
                    put("uid", getUid())
                }.toString()
            }
        }

        return try {
            val finished = process.awaitExit(3000)
            if (!finished) {
                process.terminateProcess()
                process.awaitExit(1000)
                return buildJsonObject {
                    put("status", "error")
                    put("error", "keyevent injection timed out")
                    put("uid", getUid())
                }.toString()
            }

            if (state.isCancelled) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "keyevent cancelled")
                    put("cancelled", true)
                    put("uid", getUid())
                }.toString()
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "input keyevent $keyCode failed with exit code $exitCode")
                    put("uid", getUid())
                }.toString()
            }
            buildJsonObject {
                put("status", "success")
                put("key_code", keyCode)
                put("uid", getUid())
            }.toString()
        } catch (t: Throwable) {
            buildJsonObject {
                put("status", "error")
                put("error", "Failed to inject keyevent $keyCode: ${t.message}")
                put("uid", getUid())
            }.toString()
        } finally {
            finishRequest(requestId)
        }
    }

    override fun openSettings(requestId: String): String {
        val state = getOrCreateState(requestId)

        val process: java.lang.Process
        synchronized(state.lock) {
            if (state.isCancelled) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "open_settings cancelled before execution")
                    put("cancelled", true)
                    put("uid", getUid())
                }.toString()
            }
            try {
                process = ProcessBuilder(listOf("/system/bin/am", "start", "-a", "android.settings.SETTINGS"))
                    .redirectErrorStream(true)
                    .start()
                state.process = process
            } catch (t: Throwable) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "Failed to start open settings: ${t.message}")
                    put("uid", getUid())
                }.toString()
            }
        }

        return try {
            val finished = process.awaitExit(3000)
            if (!finished) {
                process.terminateProcess()
                process.awaitExit(1000)
                return buildJsonObject {
                    put("status", "error")
                    put("error", "open_settings timed out")
                    put("uid", getUid())
                }.toString()
            }

            if (state.isCancelled) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "open_settings cancelled")
                    put("cancelled", true)
                    put("uid", getUid())
                }.toString()
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                return buildJsonObject {
                    put("status", "error")
                    put("error", "am start -a android.settings.SETTINGS failed with exit code $exitCode")
                    put("uid", getUid())
                }.toString()
            }
            buildJsonObject {
                put("status", "success")
                put("action", "android.settings.SETTINGS")
                put("uid", getUid())
            }.toString()
        } catch (t: Throwable) {
            buildJsonObject {
                put("status", "error")
                put("error", "Failed to open settings: ${t.message}")
                put("uid", getUid())
            }.toString()
        } finally {
            finishRequest(requestId)
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun java.lang.Process.isRunning(): Boolean = try { exitValue(); false } catch (_: IllegalThreadStateException) { true }

    private fun java.lang.Process.awaitExit(timeoutMs: Long): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (isRunning()) {
            if (android.os.SystemClock.elapsedRealtime() >= deadline) return false
            Thread.sleep(20)
        }
        return true
    }

    private fun java.lang.Process.terminateProcess() {
        if (android.os.Build.VERSION.SDK_INT >= 26) destroyForcibly() else destroy()
    }

    private class BoundedOutput(process: java.lang.Process) {
        private val bytes = java.io.ByteArrayOutputStream()
        private val reader = Thread({
            runCatching {
                process.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        synchronized(bytes) {
                            val keep = minOf(count, 64 * 1024 - bytes.size())
                            if (keep > 0) bytes.write(buffer, 0, keep)
                        }
                    }
                }
            }
        }, "device-package-output").apply { isDaemon = true; start() }

        fun text(): String {
            reader.join(1000)
            check(!reader.isAlive) { "Package output did not finish" }
            return synchronized(bytes) { bytes.toString("UTF-8") }
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            IDeviceUserService.TRANSACTION_DESTROY -> {
                data.enforceInterface(IDeviceUserService.DESCRIPTOR)
                reply?.writeNoException()
                destroy()
                return true
            }
            IDeviceUserService.TRANSACTION_GET_UID -> {
                data.enforceInterface(IDeviceUserService.DESCRIPTOR)
                reply?.writeNoException()
                reply?.writeInt(getUid())
                return true
            }
            IDeviceUserService.TRANSACTION_LIST_APPS -> {
                data.enforceInterface(IDeviceUserService.DESCRIPTOR)
                val requestId = data.readString() ?: ""
                val onlyLaunchable = data.readInt() == 1
                val includeSystem = data.readInt() == 1
                val result = listApps(requestId, onlyLaunchable, includeSystem)
                reply?.writeNoException()
                reply?.writeString(result)
                return true
            }
            IDeviceUserService.TRANSACTION_FORCE_STOP -> {
                data.enforceInterface(IDeviceUserService.DESCRIPTOR)
                val requestId = data.readString() ?: ""
                val pkg = data.readString() ?: ""
                val result = forceStop(requestId, pkg)
                reply?.writeNoException()
                reply?.writeString(result)
                return true
            }
            IDeviceUserService.TRANSACTION_KEYEVENT -> {
                data.enforceInterface(IDeviceUserService.DESCRIPTOR)
                val requestId = data.readString() ?: ""
                val keyCode = data.readInt()
                val result = keyevent(requestId, keyCode)
                reply?.writeNoException()
                reply?.writeString(result)
                return true
            }
            IDeviceUserService.TRANSACTION_OPEN_SETTINGS -> {
                data.enforceInterface(IDeviceUserService.DESCRIPTOR)
                val requestId = data.readString() ?: ""
                val result = openSettings(requestId)
                reply?.writeNoException()
                reply?.writeString(result)
                return true
            }
            IDeviceUserService.TRANSACTION_CANCEL -> {
                data.enforceInterface(IDeviceUserService.DESCRIPTOR)
                val requestId = data.readString() ?: ""
                val cancelled = cancel(requestId)
                reply?.writeNoException()
                reply?.writeInt(if (cancelled) 1 else 0)
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }
}
