package com.example.myapplication.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Core accessibility service for observing window nodes, performing gestures,
 * and capturing screenshots (API 30+).
 */
class DeviceAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceSessionId = System.currentTimeMillis()
        _stateVersion.incrementAndGet()
        _isConnected.value = true
        try {
            com.example.myapplication.agent.DeviceControlOverlay.attach(this)
        } catch (_: Throwable) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val currentWindows = try { windows } catch (_: RuntimeException) { emptyList() }
        val isOverlay = try {
            val wId = event.windowId
            if (wId != -1) {
                currentWindows.any { it.id == wId && it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            } else false
        } catch (_: Throwable) {
            false
        } finally {
            currentWindows.forEach { it.recycle() }
        }
        if (isOverlay) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank()) {
                    lastForegroundPackage = pkg
                }
                if (event.windowId != -1) {
                    lastWindowId = event.windowId
                }
                _stateVersion.incrementAndGet()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (observedWindowId == -1 || event.windowId == observedWindowId) _stateVersion.incrementAndGet()
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        try {
            com.example.myapplication.agent.DeviceControlOverlay.detach(this)
        } catch (_: Throwable) {}
        if (instance === this) {
            instance = null
            _isConnected.value = false
            _stateVersion.incrementAndGet()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        try {
            com.example.myapplication.agent.DeviceControlOverlay.detach(this)
        } catch (_: Throwable) {}
        if (instance === this) {
            instance = null
            _isConnected.value = false
            _stateVersion.incrementAndGet()
        }
        super.onDestroy()
    }

    /**
     * Dispatches a gesture stroke asynchronously with a timeout.
     * Throws CancellationException on timeout or job cancellation.
     */
    suspend fun performGesture(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val timeout = durationMs + 1500L

        val finished = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            return withTimeout(timeout) {
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        finished.complete(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        finished.complete(false)
                    }
                }

                val dispatched = dispatchGesture(gesture, callback, null)
                if (!dispatched) finished.complete(false)
                finished.await()
            }
        } catch (cancelled: CancellationException) {
            // Android has no direct cancelGesture API. Keep the operation lock until the
            // submitted stroke finishes; cancellation must not allow another run to overlap it.
            withContext(kotlinx.coroutines.NonCancellable) {
                val remaining = (started + timeout - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)
                kotlinx.coroutines.withTimeoutOrNull(remaining) { finished.await() }
            }
            throw cancelled
        }
    }

    /**
     * Captures a screenshot via AccessibilityService takeScreenshot API (Android 11 / API 30+).
     * HardwareBuffer is closed immediately after converting to a software Bitmap.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun captureScreenshotBitmap(): Bitmap {
        return withTimeout(5000L) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        var hwBitmap: Bitmap? = null
                        var softwareBitmap: Bitmap? = null

                        try {
                            hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            if (hwBitmap != null) {
                                softwareBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            }
                        } catch (t: Throwable) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(t)
                            }
                            return
                        } finally {
                            hardwareBuffer.close()
                            hwBitmap?.recycle()
                        }

                        if (softwareBitmap != null) {
                            if (continuation.isActive) {
                                continuation.resume(softwareBitmap) {
                                    softwareBitmap.recycle()
                                }
                            } else {
                                softwareBitmap.recycle()
                            }
                        } else {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException("Failed to wrap screenshot hardware buffer into bitmap")
                                )
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) {
                            val msg = when (errorCode) {
                                ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
                                    "Secure window content cannot be captured (FLAG_SECURE is active)"
                                ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                                    "Screenshot rate limit exceeded; minimum interval required"
                                ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                                    "Accessibility service not ready or disabled"
                                ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
                                    "Invalid display ID specified for screenshot"
                                else ->
                                    "Screenshot capture failed with code $errorCode"
                            }
                            continuation.resumeWithException(IllegalStateException(msg))
                        }
                    }
                }

                try {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
                } catch (t: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(t)
                    }
                }
            }
        }
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected = _isConnected.asStateFlow()

        @Volatile
        var instance: DeviceAccessibilityService? = null
            private set

        private val _stateVersion = AtomicLong(1)
        val stateVersion: Long get() = _stateVersion.get()

        @Volatile
        var serviceSessionId: Long = 0L
            private set

        @Volatile
        var lastForegroundPackage: String? = null
            private set

        @Volatile
        var lastWindowId: Int = -1
            private set

        @Volatile
        var observedWindowId: Int = -1
    }
}
