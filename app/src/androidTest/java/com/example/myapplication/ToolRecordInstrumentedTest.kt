package com.example.myapplication

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolRecordInstrumentedTest {
    @Test fun longDeviceRecordsExpandWrapResizeAndCopyWithoutCrashing() {
        check(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        fun clickLabel(label: String) {
            var clicked = false
            repeat(20) {
                if (!clicked) {
                    val root = automation.rootInActiveWindow
                    fun visit(node: AccessibilityNodeInfo): Boolean {
                        if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                            var target: AccessibilityNodeInfo? = node
                            while (target != null) {
                                if (target.isClickable) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                target = target.parent
                            }
                        }
                        for (index in 0 until node.childCount) {
                            val child = node.getChild(index) ?: continue
                            if (visit(child)) return true
                        }
                        return false
                    }
                    clicked = root?.let(::visit) == true
                    if (!clicked) Thread.sleep(100)
                }
            }
            assertTrue("Missing control: $label", clicked)
            instrumentation.waitForIdleSync()
            Thread.sleep(400)
        }
        for (tool in listOf("device_observe", "device_action")) {
            val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ToolRecordTestActivity::class.java)
                .putExtra("tool", tool).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try {
                instrumentation.waitForIdleSync()
                clickLabel(tool)
                clickLabel("换行")
                clickLabel("不换行")
                clickLabel("展开高度")
                clickLabel("限制高度")
                clickLabel("复制入参和回参")
                instrumentation.runOnMainSync {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    assertEquals("{}\n\n" + ToolRecordTestActivity.payload, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }
}
