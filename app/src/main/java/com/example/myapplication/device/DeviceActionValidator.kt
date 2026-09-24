package com.example.myapplication.device

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Pure validation logic for device actions, system actions, coordinates, and snapshot identity.
 * Separated for deterministic security enforcement and JVM testability.
 */
object DeviceActionValidator {

    const val MAX_TEXT_LENGTH = 4000
    const val MIN_GESTURE_DURATION_MS = 50L
    const val MAX_GESTURE_DURATION_MS = 2000L
    const val DEFAULT_GESTURE_DURATION_MS = 300L

    val ALLOWED_UI_ACTIONS = setOf(
        "launch",
        "click",
        "long_click",
        "set_text",
        "scroll",
        "swipe",
        "back",
        "home",
        "recents"
    )

    val ALLOWED_SYSTEM_ACTIONS = setOf(
        "list_apps",
        "force_stop",
        "open_settings",
        "keyevent"
    )

    val PROTECTED_PACKAGES = setOf(
        "com.Ling.actant",
        "com.example.myapplication",
        "moe.shizuku.privileged.api",
        "rikka.shizuku",
        "android",
        "com.android.systemui",
        "com.android.settings"
    )

    val ALLOWED_KEY_CODES = setOf(
        3,   // KEYCODE_HOME
        4,   // KEYCODE_BACK
        187  // KEYCODE_APP_SWITCH
    )

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValidPackageName(pkg: String): Boolean {
        if (pkg.isBlank() || pkg.length > 256) return false
        return PACKAGE_NAME_REGEX.matches(pkg.trim())
    }

    fun isProtectedPackage(pkg: String): Boolean {
        val trimmed = pkg.trim().lowercase()
        return PROTECTED_PACKAGES.any { protectedPkg ->
            val pLower = protectedPkg.lowercase()
            trimmed == pLower || trimmed.startsWith("$pLower.")
        }
    }

    fun validateCoordinates(x: Int, y: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) {
            return "Invalid display dimensions ($width x $height)"
        }
        if (x !in 0 until width || y !in 0 until height) {
            return "Coordinates ($x, $y) out of display bounds (0 until $width, 0 until $height)"
        }
        return null
    }

    fun clampDuration(durationMs: Long?): Long {
        if (durationMs == null) return DEFAULT_GESTURE_DURATION_MS
        return durationMs.coerceIn(MIN_GESTURE_DURATION_MS, MAX_GESTURE_DURATION_MS)
    }

    fun validateSnapshotIdentity(
        requestedSnapshotId: String?,
        currentSnapshotId: String?,
        requestedPackage: String? = null,
        currentPackage: String? = null,
        requestedWindowId: Int? = null,
        currentWindowId: Int? = null
    ): String? {
        if (currentSnapshotId.isNullOrBlank()) {
            return "No active observation snapshot exists. Call observe() first."
        }
        if (requestedSnapshotId.isNullOrBlank()) {
            return "Missing snapshot_id. UI actions require a valid snapshot_id from recent observe()."
        }
        if (requestedSnapshotId != currentSnapshotId) {
            return "Stale snapshot_id: requested '$requestedSnapshotId', but current snapshot is '$currentSnapshotId'. Please re-observe."
        }
        if (!requestedPackage.isNullOrBlank() && currentPackage != null && requestedPackage != currentPackage) {
            return "Foreground package mismatch: requested '$requestedPackage', but current foreground is '$currentPackage'. Screen state changed."
        }
        if (requestedWindowId != null && requestedWindowId != -1 && currentWindowId != null && currentWindowId != -1 && requestedWindowId != currentWindowId) {
            return "Window id mismatch: requested $requestedWindowId, but current foreground window is $currentWindowId."
        }
        return null
    }

    fun validateLiveState(
        observedSessionId: Long,
        liveSessionId: Long,
        observedVersion: Long,
        liveVersion: Long,
        observedRotation: Int,
        liveRotation: Int,
        observedPackage: String,
        livePackage: String,
        observedWindowId: Int,
        liveWindowId: Int
    ): String? {
        if (observedSessionId != liveSessionId) {
            return "Accessibility service session changed since observation. Re-observe required."
        }
        if (observedVersion != liveVersion) {
            return "Screen content or scroll state changed in active window since observation (observed version $observedVersion, live is $liveVersion). Re-observe required."
        }
        if (observedRotation != liveRotation) {
            return "Screen rotation changed (observed $observedRotation, live is $liveRotation). Re-observe required."
        }
        if (observedPackage != livePackage) {
            return "Foreground package changed (observed '$observedPackage', live is '$livePackage'). Re-observe required."
        }
        if (observedWindowId != -1 && liveWindowId != -1 && observedWindowId != liveWindowId) {
            return "Foreground window changed (observed $observedWindowId, live is $liveWindowId). Re-observe required."
        }
        return null
    }

    fun resolveKeyCode(key: String?, keyCode: Int?): Int? {
        if (keyCode != null && keyCode in ALLOWED_KEY_CODES) {
            return keyCode
        }
        return when (key?.trim()?.uppercase()) {
            "HOME" -> 3
            "BACK" -> 4
            "APP_SWITCH", "RECENTS" -> 187
            else -> null
        }
    }
}
