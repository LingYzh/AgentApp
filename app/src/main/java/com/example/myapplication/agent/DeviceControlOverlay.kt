package com.example.myapplication.agent

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.myapplication.AgentApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

/** Approval stays above the target application without taking its keyboard/window focus. */
object DeviceControlOverlay {
    private var scope: CoroutineScope? = null
    private var manager: WindowManager? = null
    private var view: LinearLayout? = null
    private var attachedService: AccessibilityService? = null
    private var renderKey: String? = null

    fun attach(service: AccessibilityService) {
        detach()
        attachedService = service
        manager = service.getSystemService(WindowManager::class.java)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { tasks ->
            tasks.launch {
                combine(DeviceTaskService.active, AgentApp.instance.permissionCoordinator.pending) { count, pending ->
                    count to pending.firstOrNull { it.kind == PermissionRequestKind.DEVICE_ACTION }
                }.collect { (count, request) ->
                    val key = if (count > 0) "${request?.id ?: "stop"}:$count" else null
                    if (key != renderKey) {
                        clearView()
                        renderKey = key
                        if (key != null) show(service, request)
                    }
                }
            }
        }
    }

    private fun show(service: AccessibilityService, request: PermissionRequest?) {
        val density = service.resources.displayMetrics.density
        val layout = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            setBackgroundColor(Color.rgb(34, 40, 48))
            elevation = 8 * density
        }
        fun label(text: String) = TextView(service).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 7
        }
        fun button(text: String, block: () -> Unit) = Button(service).apply {
            this.text = text
            textSize = 13f
            setOnClickListener { block() }
        }
        if (request != null) {
            layout.addView(label("UAH · 授权操作手机"))
            layout.addView(label(request.command.take(450)))
            val buttons = LinearLayout(service)
            buttons.addView(button("允许本次") {
                clearView()
                AgentApp.instance.permissionCoordinator.resolve(request.id, PermissionDecision.ALLOW_ONCE)
            })
            buttons.addView(button("拒绝") {
                clearView()
                AgentApp.instance.permissionCoordinator.resolve(request.id, PermissionDecision.DENY)
            })
            layout.addView(buttons)
        }
        layout.addView(button("停止 UAH 任务") { DeviceTaskService.stopAll() })
        val params = WindowManager.LayoutParams(
            if (request == null) WindowManager.LayoutParams.WRAP_CONTENT else (300 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; y = (64 * density).toInt() }
        try { manager?.addView(layout, params); view = layout }
        catch (_: RuntimeException) { /* Notification remains available if OEM denies overlay. */ }
    }

    private fun clearView() {
        view?.let { runCatching { manager?.removeViewImmediate(it) } }
        view = null
    }

    fun detach(service: AccessibilityService? = null) {
        if (service != null && attachedService !== service) return
        scope?.cancel()
        scope = null
        clearView()
        manager = null
        attachedService = null
        renderKey = null
    }
}
