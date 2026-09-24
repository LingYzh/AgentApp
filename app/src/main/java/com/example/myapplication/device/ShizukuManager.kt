package com.example.myapplication.device

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import rikka.shizuku.Shizuku

/**
 * Manages Shizuku binder lifecycle, permissions, and UserService IPC binding.
 * Shizuku death does not break accessibility service operations.
 */
class ShizukuManager(
    private val appContext: Context,
    private val isEnabled: () -> Boolean
) {

    private val _isUserServiceConnected = MutableStateFlow(false)
    val isUserServiceConnected = _isUserServiceConnected.asStateFlow()

    @Volatile
    var userServiceProxy: DeviceUserServiceProxy? = null
        private set

    @Volatile
    var cachedUid: Int? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, DeviceUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("device_service")
            .debuggable(false)
            .version(2)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // Late onServiceConnected after disabling must not revive connection
            if (!isEnabled()) {
                if (service != null) {
                    try {
                        DeviceUserServiceProxy(service).destroy()
                    } catch (_: Throwable) {}
                }
                try {
                    Shizuku.unbindUserService(userServiceArgs, this, true)
                } catch (_: Throwable) {}
                userServiceProxy = null
                _isUserServiceConnected.value = false
                cachedUid = null
                return
            }

            if (service != null) {
                val proxy = DeviceUserServiceProxy(service)
                userServiceProxy = proxy
                _isUserServiceConnected.value = true
                lastError = null
                cachedUid = try {
                    proxy.getUid()
                } catch (_: Throwable) {
                    null
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userServiceProxy = null
            _isUserServiceConnected.value = false
            cachedUid = null
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        lastError = null
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userServiceProxy = null
        _isUserServiceConnected.value = false
        cachedUid = null
        lastError = "Shizuku binder disconnected or server died"
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            lastError = null
        } else {
            lastError = "Shizuku permission denied by user"
        }
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        } catch (t: Throwable) {
            // Expected in test environments without Shizuku framework
            lastError = "Shizuku initialization: ${t.message}"
        }
    }

    fun isBinderAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun getVersion(): Int {
        return try {
            Shizuku.getVersion()
        } catch (_: Throwable) {
            -1
        }
    }

    fun isPermissionGranted(): Boolean {
        if (!isBinderAvailable()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        if (!isBinderAvailable()) {
            lastError = "Shizuku service is not running or binder is absent"
            return
        }
        try {
            if (!Shizuku.isPreV11() && !isPermissionGranted()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (t: Throwable) {
            lastError = "Failed to request Shizuku permission: ${t.message}"
        }
    }

    @Synchronized
    fun connect() {
        if (!isEnabled()) {
            lastError = "Device control is globally disabled"
            return
        }
        if (!isBinderAvailable()) {
            lastError = "Shizuku binder not available. Please ensure Shizuku app is running."
            return
        }
        if (!isPermissionGranted()) {
            lastError = "Shizuku permission not granted. Please call requestShizukuPermission() first."
            return
        }
        if (_isUserServiceConnected.value && userServiceProxy != null) {
            return // already connected
        }

        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            lastError = null
        } catch (t: Throwable) {
            lastError = "Failed to bind Shizuku UserService: ${t.message}"
            userServiceProxy = null
            _isUserServiceConnected.value = false
            cachedUid = null
        }
    }

    @Synchronized
    fun disconnect() {
        cachedUid = null
        try {
            userServiceProxy?.destroy()
        } catch (_: Throwable) {}
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Throwable) {}
        userServiceProxy = null
        _isUserServiceConnected.value = false
    }

    fun getStatusJson(): JsonObject {
        val binderAvailable = isBinderAvailable()
        val version = getVersion()
        val permission = isPermissionGranted()
        val connected = _isUserServiceConnected.value
        val uid = if (connected) cachedUid else null

        return buildJsonObject {
            put("binder_available", binderAvailable)
            put("version", version)
            put("permission_granted", permission)
            put("user_service_connected", connected)
            if (uid != null) {
                put("user_service_uid", uid)
            }
            if (lastError != null) {
                put("error", lastError)
            }
        }
    }
}
