package com.example.myapplication.device

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Controller handling UI observation, node traversal, action execution,
 * gesture dispatching, and screenshot captures via accessibility service.
 */
class AccessibilityController(private val appContext: Context) {

    private val snapshotSequence = AtomicLong(0)

    @Volatile
    var currentSnapshot: ActiveSnapshot? = null
        private set

    data class SnapshotNode(
        val index: Int,
        val text: String?,
        val description: String?,
        val viewId: String?,
        val className: String?,
        val bounds: Rect,
        val actions: List<String>,
        val isClickable: Boolean,
        val isScrollable: Boolean,
        val isEditable: Boolean,
        val isFocused: Boolean
    )

    data class ActiveSnapshot(
        val snapshotId: String,
        val serviceSessionId: Long,
        val serviceVersion: Long,
        val packageName: String,
        val windowId: Int,
        val displayWidth: Int,
        val displayHeight: Int,
        val displayRotation: Int,
        val timestamp: Long,
        val nodes: List<SnapshotNode>
    )

    suspend fun observe(service: DeviceAccessibilityService): String {
        return withContext(Dispatchers.Main) {
            val root = getTargetApplicationRoot(service)
                ?: return@withContext buildJsonObject {
                    put("error", "No active window or content found on screen. Screen may be locked or off.")
                }.toString()

            val displayMetrics = service.resources.displayMetrics
            val displayWidth = displayMetrics.widthPixels
            val displayHeight = displayMetrics.heightPixels
            val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { service.display?.rotation ?: 0 } catch (_: Throwable) { 0 }
            } else {
                0
            }
            val displayBounds = Rect(0, 0, displayWidth, displayHeight)

            val foregroundPackage = root.packageName?.toString()
                ?: DeviceAccessibilityService.lastForegroundPackage
                ?: "unknown"
            val windowId = root.windowId
            DeviceAccessibilityService.observedWindowId = windowId

            val usefulNodes = mutableListOf<SnapshotNode>()
            val maxVisited = 1000
            val maxDepth = 30
            val maxUsefulNodes = 150
            val maxQueueSize = 400
            var visits = 0

            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue.add(root to 0)

            try {
                while (queue.isNotEmpty() && visits < maxVisited && usefulNodes.size < maxUsefulNodes) {
                    val (current, depth) = queue.removeFirst()
                    visits++

                    val bounds = Rect()
                    current.getBoundsInScreen(bounds)
                    val isVisible = current.isVisibleToUser && Rect.intersects(bounds, displayBounds) && bounds.width() > 0 && bounds.height() > 0

                    val isPassword = current.isPassword
                    val text = if (isPassword) "[PASSWORD]" else current.text?.toString()?.take(500)
                    val desc = if (isPassword) "[PASSWORD]" else current.contentDescription?.toString()?.take(500)
                    val viewId = current.viewIdResourceName
                    val className = current.className?.toString()
                    val isClickable = current.isClickable
                    val isScrollable = current.isScrollable
                    val isEditable = current.isEditable
                    val isFocused = current.isFocused

                    val actions = mutableListOf<String>()
                    for (action in current.actionList) {
                        when (action.id) {
                            AccessibilityNodeInfo.ACTION_CLICK -> actions.add("click")
                            AccessibilityNodeInfo.ACTION_LONG_CLICK -> actions.add("long_click")
                            AccessibilityNodeInfo.ACTION_SET_TEXT -> actions.add("set_text")
                            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> actions.add("scroll_forward")
                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> actions.add("scroll_backward")
                        }
                    }

                    val isUseful = isVisible && (
                        !text.isNullOrBlank() ||
                        !desc.isNullOrBlank() ||
                        isClickable ||
                        isScrollable ||
                        isEditable ||
                        actions.isNotEmpty()
                    )

                    if (isUseful) {
                        usefulNodes.add(
                            SnapshotNode(
                                index = usefulNodes.size,
                                text = text,
                                description = desc,
                                viewId = viewId,
                                className = className,
                                bounds = bounds,
                                actions = actions,
                                isClickable = isClickable,
                                isScrollable = isScrollable,
                                isEditable = isEditable,
                                isFocused = isFocused
                            )
                        )
                    }

                    if (depth < maxDepth && queue.size < maxQueueSize) {
                        val childCount = current.childCount
                        for (i in 0 until minOf(childCount, maxVisited - visits)) {
                            if (queue.size >= maxQueueSize) break
                            val child = current.getChild(i)
                            if (child != null) {
                                queue.add(child to (depth + 1))
                            }
                        }
                    }

                    if (current !== root) {
                        try { current.recycle() } catch (_: Throwable) {}
                    }
                }
            } finally {
                while (queue.isNotEmpty()) {
                    val (node, _) = queue.removeFirst()
                    if (node !== root) {
                        try { node.recycle() } catch (_: Throwable) {}
                    }
                }
                try { root.recycle() } catch (_: Throwable) {}
            }

            val snapshotId = "snap_${DeviceAccessibilityService.serviceSessionId}_${DeviceAccessibilityService.stateVersion}_${snapshotSequence.incrementAndGet()}"
            val snapshot = ActiveSnapshot(
                snapshotId = snapshotId,
                serviceSessionId = DeviceAccessibilityService.serviceSessionId,
                serviceVersion = DeviceAccessibilityService.stateVersion,
                packageName = foregroundPackage,
                windowId = windowId,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                displayRotation = displayRotation,
                timestamp = System.currentTimeMillis(),
                nodes = usefulNodes
            )
            currentSnapshot = snapshot

            buildBoundedObserveJson(snapshot)
        }
    }

    private fun buildBoundedObserveJson(snapshot: ActiveSnapshot): String {
        val maxChars = 20_000

        fun render(nodes: List<SnapshotNode>, truncated: Boolean): String {
            return buildJsonObject {
                put("snapshot_id", snapshot.snapshotId)
                put("package", snapshot.packageName)
                put("window_id", snapshot.windowId)
                put("display_width", snapshot.displayWidth)
                put("display_height", snapshot.displayHeight)
                put("display_rotation", snapshot.displayRotation)
                put("node_count", nodes.size)
                if (truncated) {
                    put("truncated", true)
                }
                put("nodes", buildJsonArray {
                    for (node in nodes) {
                        add(buildJsonObject {
                            put("index", node.index)
                            node.text?.let { put("text", it) }
                            node.description?.let { put("description", it) }
                            node.viewId?.let { put("view_id", it) }
                            node.className?.let { put("class_name", it) }
                            put("bounds", buildJsonObject {
                                put("left", node.bounds.left)
                                put("top", node.bounds.top)
                                put("right", node.bounds.right)
                                put("bottom", node.bounds.bottom)
                            })
                            if (node.actions.isNotEmpty()) {
                                put("actions", buildJsonArray { node.actions.forEach { add(it) } })
                            }
                            if (node.isClickable) put("clickable", true)
                            if (node.isScrollable) put("scrollable", true)
                            if (node.isEditable) put("editable", true)
                            if (node.isFocused) put("focused", true)
                        })
                    }
                })
            }.toString()
        }

        val full = render(snapshot.nodes, false)
        if (full.length <= maxChars) {
            return full
        }

        var low = 0
        var high = snapshot.nodes.size
        var bestJson = render(emptyList(), true)

        while (low <= high) {
            val mid = (low + high) / 2
            val candidate = render(snapshot.nodes.take(mid), mid < snapshot.nodes.size)
            if (candidate.length <= maxChars) {
                bestJson = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return bestJson
    }

    suspend fun act(service: DeviceAccessibilityService, args: JsonObject): String = withContext(Dispatchers.Main) {
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext buildJsonObject {
                put("status", "error")
                put("error", "Missing 'action' parameter")
            }.toString()

        if (action !in DeviceActionValidator.ALLOWED_UI_ACTIONS) {
            return@withContext buildJsonObject {
                put("status", "error")
                put("error", "Action '$action' is not permitted. Allowed: ${DeviceActionValidator.ALLOWED_UI_ACTIONS}")
            }.toString()
        }

        val liveRoot = getTargetApplicationRoot(service)
            ?: return@withContext buildJsonObject {
                put("status", "error")
                put("error", "Screen is not accessible (no active window, screen may be locked or off)")
            }.toString()

        try {
            val livePackage = liveRoot.packageName?.toString()
                ?: DeviceAccessibilityService.lastForegroundPackage
                ?: "unknown"
            val liveWindowId = liveRoot.windowId
            val liveRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { service.display?.rotation ?: 0 } catch (_: Throwable) { 0 }
            } else {
                0
            }
            val liveSessionId = DeviceAccessibilityService.serviceSessionId
            val liveVersion = DeviceAccessibilityService.stateVersion

            val snapshot = currentSnapshot
            val snapshotId = args["snapshot_id"]?.jsonPrimitive?.contentOrNull
            val reqPkg = args["package"]?.jsonPrimitive?.contentOrNull
            val reqWindow = args["window_id"]?.jsonPrimitive?.intOrNull

            // Validate snapshot and live state immediately before every action
            if (action != "launch" || snapshotId != null) {
                if (snapshot == null) {
                    return@withContext buildJsonObject {
                        put("status", "error")
                        put("error", "No active observation snapshot exists. Call observe() first.")
                    }.toString()
                }
                if (snapshotId.isNullOrBlank()) {
                    return@withContext buildJsonObject {
                        put("status", "error")
                        put("error", "Missing snapshot_id. UI actions require a valid snapshot_id from recent observe().")
                    }.toString()
                }
                if (snapshotId != snapshot.snapshotId) {
                    return@withContext buildJsonObject {
                        put("status", "error")
                        put("error", "Stale snapshot_id: requested '$snapshotId', but active snapshot is '${snapshot.snapshotId}'. Re-observe required.")
                    }.toString()
                }
                if (!reqPkg.isNullOrBlank() && reqPkg != snapshot.packageName) {
                    return@withContext buildJsonObject {
                        put("status", "error")
                        put("error", "Requested package '$reqPkg' does not match snapshot package '${snapshot.packageName}'.")
                    }.toString()
                }
                if (reqWindow != null && reqWindow != -1 && reqWindow != snapshot.windowId) {
                    return@withContext buildJsonObject {
                        put("status", "error")
                        put("error", "Requested window $reqWindow does not match snapshot window ${snapshot.windowId}.")
                    }.toString()
                }

                // Live state validation
                val liveErr = DeviceActionValidator.validateLiveState(
                    observedSessionId = snapshot.serviceSessionId,
                    liveSessionId = liveSessionId,
                    observedVersion = snapshot.serviceVersion,
                    liveVersion = liveVersion,
                    observedRotation = snapshot.displayRotation,
                    liveRotation = liveRotation,
                    observedPackage = snapshot.packageName,
                    livePackage = livePackage,
                    observedWindowId = snapshot.windowId,
                    liveWindowId = liveWindowId
                )
                if (liveErr != null) {
                    return@withContext buildJsonObject {
                        put("status", "error")
                        put("error", liveErr)
                    }.toString()
                }
            }

            val details: String = when (action) {
                "launch" -> {
                    val targetPkg = args["package"]?.jsonPrimitive?.contentOrNull
                        ?: return@withContext buildJsonObject {
                            put("status", "error")
                            put("error", "Missing 'package' for launch action")
                        }.toString()

                    if (!DeviceActionValidator.isValidPackageName(targetPkg)) {
                        return@withContext buildJsonObject {
                            put("status", "error")
                            put("error", "Invalid package name: $targetPkg")
                        }.toString()
                    }

                    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(targetPkg)
                        ?: return@withContext buildJsonObject {
                            put("status", "error")
                            put("error", "Package '$targetPkg' does not have a launchable main activity")
                        }.toString()

                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    "Launched $targetPkg"
                }

                "click" -> executeClick(service, liveRoot, args, snapshot)
                "long_click" -> executeLongClick(service, liveRoot, args, snapshot)
                "set_text" -> executeSetText(service, liveRoot, args, snapshot!!)
                "scroll" -> executeScroll(service, liveRoot, args, snapshot)
                "swipe" -> executeSwipe(service, args, snapshot)

                "back" -> {
                    val performed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    if (!performed) {
                        return@withContext buildJsonObject {
                            put("status", "error")
                            put("error", "performGlobalAction(GLOBAL_ACTION_BACK) returned false")
                        }.toString()
                    }
                    "Dispatched global BACK"
                }

                "home" -> {
                    val performed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    if (!performed) {
                        return@withContext buildJsonObject {
                            put("status", "error")
                            put("error", "performGlobalAction(GLOBAL_ACTION_HOME) returned false")
                        }.toString()
                    }
                    "Dispatched global HOME"
                }

                "recents" -> {
                    val performed = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    if (!performed) {
                        return@withContext buildJsonObject {
                            put("status", "error")
                            put("error", "performGlobalAction(GLOBAL_ACTION_RECENTS) returned false")
                        }.toString()
                    }
                    "Dispatched global RECENTS"
                }

                else -> {
                    return@withContext buildJsonObject {
                        put("status", "error")
                        put("error", "Unsupported action: $action")
                    }.toString()
                }
            }

            // Distinguish accepted action from verified task success.
            delay(300L)
            var freshObserve: JsonObject? = null
            var freshError: String? = null
            try {
                val freshStr = observe(service)
                freshObserve = Json.parseToJsonElement(freshStr).jsonObject
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                freshError = t.message
            }

            buildJsonObject {
                put("status", "accepted")
                put("action", action)
                put("verified_success", false)
                put("details", details)
                if (freshObserve != null) {
                    put("fresh_observe", freshObserve)
                } else if (freshError != null) {
                    put("fresh_observe_error", freshError)
                }
            }.toString()

        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            buildJsonObject {
                put("status", "error")
                put("error", "Action execution failed: ${t.message}")
            }.toString()
        } finally {
            try { liveRoot.recycle() } catch (_: Throwable) {}
        }
    }

    private suspend fun executeClick(
        service: DeviceAccessibilityService,
        liveRoot: AccessibilityNodeInfo,
        args: JsonObject,
        snapshot: ActiveSnapshot?
    ): String {
        val nodeIndex = args["node"]?.jsonPrimitive?.intOrNull
        if (nodeIndex != null) {
            val target = snapshot?.nodes?.find { it.index == nodeIndex }
                ?: throw IllegalArgumentException("Node index $nodeIndex not found in current snapshot")

            val liveNode = reResolveNode(liveRoot, target)
            try {
                val success = liveNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!success) {
                    throw IllegalStateException("ACTION_CLICK returned false on node $nodeIndex")
                }
                return "Clicked node $nodeIndex via ACTION_CLICK"
            } finally {
                if (liveNode !== liveRoot) {
                    try { liveNode.recycle() } catch (_: Throwable) {}
                }
            }
        }

        val x = args["x"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Click requires 'node' index or ('x', 'y') coordinates")
        val y = args["y"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Click requires 'y' coordinate when 'x' is provided")

        val width = snapshot?.displayWidth ?: service.resources.displayMetrics.widthPixels
        val height = snapshot?.displayHeight ?: service.resources.displayMetrics.heightPixels
        val coordErr = DeviceActionValidator.validateCoordinates(x, y, width, height)
        if (coordErr != null) {
            throw IllegalArgumentException(coordErr)
        }

        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val success = service.performGesture(path, 100L)
        if (!success) {
            throw IllegalStateException("Click gesture at ($x, $y) failed or was cancelled by the system")
        }
        return "Clicked at coordinate ($x, $y)"
    }

    private suspend fun executeLongClick(
        service: DeviceAccessibilityService,
        liveRoot: AccessibilityNodeInfo,
        args: JsonObject,
        snapshot: ActiveSnapshot?
    ): String {
        val nodeIndex = args["node"]?.jsonPrimitive?.intOrNull
        if (nodeIndex != null) {
            val target = snapshot?.nodes?.find { it.index == nodeIndex }
                ?: throw IllegalArgumentException("Node index $nodeIndex not found in current snapshot")

            val liveNode = reResolveNode(liveRoot, target)
            try {
                val success = liveNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                if (!success) {
                    throw IllegalStateException("ACTION_LONG_CLICK returned false on node $nodeIndex")
                }
                return "Long-clicked node $nodeIndex via ACTION_LONG_CLICK"
            } finally {
                if (liveNode !== liveRoot) {
                    try { liveNode.recycle() } catch (_: Throwable) {}
                }
            }
        }

        val x = args["x"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Long click requires 'node' index or ('x', 'y') coordinates")
        val y = args["y"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Long click requires 'y' coordinate")

        val width = snapshot?.displayWidth ?: service.resources.displayMetrics.widthPixels
        val height = snapshot?.displayHeight ?: service.resources.displayMetrics.heightPixels
        val coordErr = DeviceActionValidator.validateCoordinates(x, y, width, height)
        if (coordErr != null) {
            throw IllegalArgumentException(coordErr)
        }

        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val success = service.performGesture(path, 800L)
        if (!success) {
            throw IllegalStateException("Long click gesture at ($x, $y) failed or was cancelled by the system")
        }
        return "Long-clicked at coordinate ($x, $y)"
    }

    private fun executeSetText(
        service: DeviceAccessibilityService,
        liveRoot: AccessibilityNodeInfo,
        args: JsonObject,
        snapshot: ActiveSnapshot
    ): String {
        val nodeIndex = args["node"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("set_text requires 'node' index")
        val text = args["text"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("set_text requires 'text' parameter")

        if (text.length > DeviceActionValidator.MAX_TEXT_LENGTH) {
            throw IllegalArgumentException("Text exceeds maximum allowed length of ${DeviceActionValidator.MAX_TEXT_LENGTH} characters")
        }

        val target = snapshot.nodes.find { it.index == nodeIndex }
            ?: throw IllegalArgumentException("Node index $nodeIndex not found in current snapshot")

        val liveNode = reResolveNode(liveRoot, target)

        try {
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = liveNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            if (!success) {
                throw IllegalStateException("ACTION_SET_TEXT returned false on node $nodeIndex. Node may not support text editing.")
            }
            return "Set text on node $nodeIndex (${text.length} chars)"
        } finally {
            if (liveNode !== liveRoot) {
                try { liveNode.recycle() } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun executeScroll(
        service: DeviceAccessibilityService,
        liveRoot: AccessibilityNodeInfo,
        args: JsonObject,
        snapshot: ActiveSnapshot?
    ): String {
        val direction = args["direction"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "down"
        val nodeIndex = args["node"]?.jsonPrimitive?.intOrNull

        if (nodeIndex != null) {
            val target = snapshot?.nodes?.find { it.index == nodeIndex }
                ?: throw IllegalArgumentException("Node index $nodeIndex not found in current snapshot")

            val liveNode = reResolveNode(liveRoot, target)
            try {
                val action = when (direction) {
                    "down", "forward", "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    "up", "backward", "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                val success = liveNode.performAction(action)
                if (!success) {
                    throw IllegalStateException("ACTION_SCROLL returned false on node $nodeIndex for direction '$direction'")
                }
                return "Scrolled node $nodeIndex $direction via ACTION_SCROLL"
            } finally {
                if (liveNode !== liveRoot) {
                    try { liveNode.recycle() } catch (_: Throwable) {}
                }
            }
        }

        // Screen-wide scroll
        val width = snapshot?.displayWidth ?: service.resources.displayMetrics.widthPixels
        val height = snapshot?.displayHeight ?: service.resources.displayMetrics.heightPixels
        val midX = width / 2f
        val startY: Float
        val endY: Float

        if (direction == "up") {
            startY = height * 0.3f
            endY = height * 0.7f
        } else {
            startY = height * 0.7f
            endY = height * 0.3f
        }

        val path = Path().apply {
            moveTo(midX, startY)
            lineTo(midX, endY)
        }
        val success = service.performGesture(path, 300L)
        if (!success) {
            throw IllegalStateException("Screen scroll gesture $direction failed or was cancelled by the system")
        }
        return "Scrolled screen $direction"
    }

    private suspend fun executeSwipe(
        service: DeviceAccessibilityService,
        args: JsonObject,
        snapshot: ActiveSnapshot?
    ): String {
        val x = args["x"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("swipe requires 'x'")
        val y = args["y"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("swipe requires 'y'")
        val x2 = args["x2"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("swipe requires 'x2'")
        val y2 = args["y2"]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("swipe requires 'y2'")
        val durationMs = DeviceActionValidator.clampDuration(args["duration_ms"]?.jsonPrimitive?.longOrNull)

        val width = snapshot?.displayWidth ?: service.resources.displayMetrics.widthPixels
        val height = snapshot?.displayHeight ?: service.resources.displayMetrics.heightPixels

        val c1 = DeviceActionValidator.validateCoordinates(x, y, width, height)
        if (c1 != null) throw IllegalArgumentException("Start coordinate: $c1")
        val c2 = DeviceActionValidator.validateCoordinates(x2, y2, width, height)
        if (c2 != null) throw IllegalArgumentException("End coordinate: $c2")

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val success = service.performGesture(path, durationMs)
        if (!success) {
            throw IllegalStateException("Swipe gesture from ($x, $y) to ($x2, $y2) failed or was cancelled by the system")
        }
        return "Swiped from ($x, $y) to ($x2, $y2) over ${durationMs}ms"
    }

    private fun reResolveNode(
        liveRoot: AccessibilityNodeInfo,
        target: SnapshotNode
    ): AccessibilityNodeInfo {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(liveRoot to 0)
        val maxVisits = 800
        val maxDepth = 30
        val maxQueueSize = 300
        var visits = 0

        try {
            while (queue.isNotEmpty() && visits < maxVisits) {
                val (current, depth) = queue.removeFirst()
                visits++

                val bounds = Rect()
                current.getBoundsInScreen(bounds)
                val viewId = current.viewIdResourceName
                val className = current.className?.toString()
                val text = if (current.isPassword) "[PASSWORD]" else current.text?.toString()?.take(500)
                val desc = if (current.isPassword) "[PASSWORD]" else current.contentDescription?.toString()?.take(500)

                val boundsMatch = bounds == target.bounds
                val classMatch = className == target.className
                val viewIdMatch = viewId == target.viewId
                val textMatch = text == target.text
                val descMatch = desc == target.description

                if (boundsMatch && classMatch && viewIdMatch && textMatch && descMatch) {
                    matches.add(current)
                } else {
                    if (depth < maxDepth && queue.size < maxQueueSize) {
                        val childCount = current.childCount
                        for (i in 0 until minOf(childCount, maxVisits - visits)) {
                            if (queue.size >= maxQueueSize) break
                            val child = current.getChild(i)
                            if (child != null) {
                                queue.add(child to (depth + 1))
                            }
                        }
                    }

                    if (current !== liveRoot) {
                        try { current.recycle() } catch (_: Throwable) {}
                    }
                }
            }
        } finally {
            while (queue.isNotEmpty()) {
                val (node, _) = queue.removeFirst()
                if (node !== liveRoot && !matches.contains(node)) {
                    try { node.recycle() } catch (_: Throwable) {}
                }
            }
        }

        if (matches.isEmpty()) {
            throw IllegalStateException(
                "Stale node reference: node ${target.index} (viewId=${target.viewId}, text=${target.text}, bounds=${target.bounds}) could not be resolved on current screen. Screen content has changed."
            )
        }

        if (matches.size > 1) {
            for (m in matches) {
                if (m !== liveRoot) {
                    try { m.recycle() } catch (_: Throwable) {}
                }
            }
            throw IllegalStateException(
                "Ambiguous node reference: multiple nodes (${matches.size}) match identical identity on screen for node ${target.index}. Cannot safely target."
            )
        }

        return matches.first()
    }

    private fun getTargetApplicationRoot(service: DeviceAccessibilityService): AccessibilityNodeInfo? {
        val windows = try { service.windows } catch (_: Throwable) { null }
        try {
        val overlayWindowIds = windows?.filter { it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            ?.map { it.id }?.toSet() ?: emptySet()

        val activeRoot = service.rootInActiveWindow
        if (activeRoot != null) {
            if (activeRoot.windowId !in overlayWindowIds) {
                return activeRoot
            }
            try { activeRoot.recycle() } catch (_: Throwable) {}
        }

        if (windows.isNullOrEmpty()) return null

        val candidateWindows = windows.filter { it.id !in overlayWindowIds }
        if (candidateWindows.isEmpty()) return null

        // 1. Focused application window
        val focusedApp = candidateWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }?.root
        if (focusedApp != null) return focusedApp

        // 2. Active application window
        val activeApp = candidateWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }?.root
        if (activeApp != null) return activeApp

        // 3. Any application window
        val anyApp = candidateWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.root
        if (anyApp != null) return anyApp

        // 4. Any focused non-overlay window (e.g. system dialog or keyboard)
        val focusedOther = candidateWindows.firstOrNull { it.isFocused }?.root
        if (focusedOther != null) return focusedOther

        // 5. Fallback to any non-overlay window root
        return candidateWindows.firstNotNullOfOrNull { it.root }
        } finally { windows?.forEach { it.recycle() } }
    }

    suspend fun screenshot(service: DeviceAccessibilityService): File {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw UnsupportedOperationException("Screenshots via accessibility require Android 11+ (API 30+). Current API: ${Build.VERSION.SDK_INT}")
        }

        val bitmap = service.captureScreenshotBitmap()
        var scaledBitmap: Bitmap? = null
        try {
            val maxDim = 1600
            val width = bitmap.width
            val height = bitmap.height

            scaledBitmap = if (width > maxDim || height > maxDim) {
                val scale = if (width >= height) {
                    maxDim.toFloat() / width
                } else {
                    maxDim.toFloat() / height
                }
                val tw = (width * scale).toInt().coerceAtLeast(1)
                val th = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, tw, th, true)
            } else {
                bitmap
            }

            val capturesDir = File(appContext.cacheDir, "device-captures")
            if (!capturesDir.exists()) {
                capturesDir.mkdirs()
            }

            val filename = "capture_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.png"
            val targetFile = File(capturesDir, filename)
            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            try {
                val files = capturesDir.listFiles()?.filter { it.isFile && it.name.endsWith(".png") }
                if (files != null && files.size > 20) {
                    val sorted = files.sortedBy { it.lastModified() }
                    val toDeleteCount = sorted.size - 20
                    for (i in 0 until toDeleteCount) {
                        sorted[i].delete()
                    }
                }
            } catch (_: Throwable) {}

            return targetFile
        } finally {
            if (scaledBitmap != null && scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
        }
    }
}
