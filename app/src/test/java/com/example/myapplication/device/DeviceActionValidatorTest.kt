package com.example.myapplication.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceActionValidatorTest {

    @Test
    fun `allowed ui actions match allowlist contract exactly`() {
        val expected = setOf(
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
        assertEquals(expected, DeviceActionValidator.ALLOWED_UI_ACTIONS)

        // Ensure arbitrary actions are blocked
        assertFalse(DeviceActionValidator.ALLOWED_UI_ACTIONS.contains("shell"))
        assertFalse(DeviceActionValidator.ALLOWED_UI_ACTIONS.contains("reboot"))
        assertFalse(DeviceActionValidator.ALLOWED_UI_ACTIONS.contains("exec"))
        assertFalse(DeviceActionValidator.ALLOWED_UI_ACTIONS.contains("input"))
    }

    @Test
    fun `allowed system actions match Shizuku allowlist contract`() {
        val expected = setOf(
            "list_apps",
            "force_stop",
            "open_settings",
            "keyevent"
        )
        assertEquals(expected, DeviceActionValidator.ALLOWED_SYSTEM_ACTIONS)

        assertFalse(DeviceActionValidator.ALLOWED_SYSTEM_ACTIONS.contains("pm_install"))
        assertFalse(DeviceActionValidator.ALLOWED_SYSTEM_ACTIONS.contains("sh"))
        assertFalse(DeviceActionValidator.ALLOWED_SYSTEM_ACTIONS.contains("reboot"))
    }

    @Test
    fun `package name validation enforces valid identifiers and blocks shell injection`() {
        assertTrue(DeviceActionValidator.isValidPackageName("com.example.app"))
        assertTrue(DeviceActionValidator.isValidPackageName("org.shizuku.privileged.api"))
        assertTrue(DeviceActionValidator.isValidPackageName("a.b.c_1"))

        // Invalid package names / shell injection attempts
        assertFalse(DeviceActionValidator.isValidPackageName(""))
        assertFalse(DeviceActionValidator.isValidPackageName("   "))
        assertFalse(DeviceActionValidator.isValidPackageName("com.example; rm -rf /"))
        assertFalse(DeviceActionValidator.isValidPackageName("com.example && echo pwned"))
        assertFalse(DeviceActionValidator.isValidPackageName("com.example|reboot"))
        assertFalse(DeviceActionValidator.isValidPackageName("123.com"))
        assertFalse(DeviceActionValidator.isValidPackageName("com..app"))
        assertFalse(DeviceActionValidator.isValidPackageName("singleword"))
    }

    @Test
    fun `protected packages cannot be targeted for force stop`() {
        // App identity
        assertTrue(DeviceActionValidator.isProtectedPackage("com.Ling.actant"))
        assertTrue(DeviceActionValidator.isProtectedPackage("com.example.myapplication"))
        assertTrue(DeviceActionValidator.isProtectedPackage("com.Ling.actant.service"))

        // Shizuku
        assertTrue(DeviceActionValidator.isProtectedPackage("moe.shizuku.privileged.api"))
        assertTrue(DeviceActionValidator.isProtectedPackage("rikka.shizuku"))

        // System core
        assertTrue(DeviceActionValidator.isProtectedPackage("android"))
        assertTrue(DeviceActionValidator.isProtectedPackage("com.android.systemui"))
        assertTrue(DeviceActionValidator.isProtectedPackage("com.android.settings"))

        // Normal 3rd-party apps should NOT be protected
        assertFalse(DeviceActionValidator.isProtectedPackage("com.tencent.mm"))
        assertFalse(DeviceActionValidator.isProtectedPackage("com.google.android.apps.maps"))
        assertFalse(DeviceActionValidator.isProtectedPackage("org.mozilla.firefox"))
    }

    @Test
    fun `coordinates validation respects display bounds`() {
        val width = 1080
        val height = 2400

        assertNull(DeviceActionValidator.validateCoordinates(0, 0, width, height))
        assertNull(DeviceActionValidator.validateCoordinates(540, 1200, width, height))
        assertNull(DeviceActionValidator.validateCoordinates(1079, 2399, width, height))

        // Out of bounds
        assertNotNull(DeviceActionValidator.validateCoordinates(-1, 500, width, height))
        assertNotNull(DeviceActionValidator.validateCoordinates(500, -1, width, height))
        assertNotNull(DeviceActionValidator.validateCoordinates(1080, 500, width, height))
        assertNotNull(DeviceActionValidator.validateCoordinates(500, 2400, width, height))
        assertNotNull(DeviceActionValidator.validateCoordinates(100, 100, 0, 0))
    }

    @Test
    fun `duration clamping stays strictly in 50ms to 2000ms range`() {
        assertEquals(300L, DeviceActionValidator.clampDuration(null))
        assertEquals(50L, DeviceActionValidator.clampDuration(10L))
        assertEquals(50L, DeviceActionValidator.clampDuration(0L))
        assertEquals(50L, DeviceActionValidator.clampDuration(-100L))
        assertEquals(2000L, DeviceActionValidator.clampDuration(5000L))
        assertEquals(500L, DeviceActionValidator.clampDuration(500L))
    }

    @Test
    fun `key code resolution permits only back home and app switch`() {
        assertEquals(3, DeviceActionValidator.resolveKeyCode("HOME", null))
        assertEquals(3, DeviceActionValidator.resolveKeyCode("home", null))
        assertEquals(3, DeviceActionValidator.resolveKeyCode(null, 3))

        assertEquals(4, DeviceActionValidator.resolveKeyCode("BACK", null))
        assertEquals(4, DeviceActionValidator.resolveKeyCode("back", null))
        assertEquals(4, DeviceActionValidator.resolveKeyCode(null, 4))

        assertEquals(187, DeviceActionValidator.resolveKeyCode("APP_SWITCH", null))
        assertEquals(187, DeviceActionValidator.resolveKeyCode("RECENTS", null))
        assertEquals(187, DeviceActionValidator.resolveKeyCode(null, 187))

        // Disallowed keycodes
        assertNull(DeviceActionValidator.resolveKeyCode("POWER", null))
        assertNull(DeviceActionValidator.resolveKeyCode(null, 26)) // KEYCODE_POWER
        assertNull(DeviceActionValidator.resolveKeyCode(null, 24)) // KEYCODE_VOLUME_UP
        assertNull(DeviceActionValidator.resolveKeyCode("UNKNOWN", null))
    }

    @Test
    fun `snapshot identity validation detects stale snapshots and package mismatches`() {
        val currentSnap = "snap_1001_1_1"
        val currentPkg = "com.example.notes"
        val currentWindow = 42

        // Success when all match
        assertNull(
            DeviceActionValidator.validateSnapshotIdentity(
                requestedSnapshotId = currentSnap,
                currentSnapshotId = currentSnap,
                requestedPackage = currentPkg,
                currentPackage = currentPkg,
                requestedWindowId = currentWindow,
                currentWindowId = currentWindow
            )
        )

        // Missing active observation
        val noCurrentErr = DeviceActionValidator.validateSnapshotIdentity(
            requestedSnapshotId = currentSnap,
            currentSnapshotId = null
        )
        assertNotNull(noCurrentErr)
        assertTrue(noCurrentErr!!.contains("No active observation"))

        // Missing requested snapshot_id
        val missingReqErr = DeviceActionValidator.validateSnapshotIdentity(
            requestedSnapshotId = null,
            currentSnapshotId = currentSnap
        )
        assertNotNull(missingReqErr)
        assertTrue(missingReqErr!!.contains("Missing snapshot_id"))

        // Stale snapshot ID
        val staleSnapErr = DeviceActionValidator.validateSnapshotIdentity(
            requestedSnapshotId = "snap_1001_1_0",
            currentSnapshotId = currentSnap
        )
        assertNotNull(staleSnapErr)
        assertTrue(staleSnapErr!!.contains("Stale snapshot_id"))

        // Package mismatch
        val pkgMismatchErr = DeviceActionValidator.validateSnapshotIdentity(
            requestedSnapshotId = currentSnap,
            currentSnapshotId = currentSnap,
            requestedPackage = "com.other.app",
            currentPackage = currentPkg
        )
        assertNotNull(pkgMismatchErr)
        assertTrue(pkgMismatchErr!!.contains("Foreground package mismatch"))

        // Window ID mismatch
        val windowMismatchErr = DeviceActionValidator.validateSnapshotIdentity(
            requestedSnapshotId = currentSnap,
            currentSnapshotId = currentSnap,
            requestedPackage = currentPkg,
            currentPackage = currentPkg,
            requestedWindowId = 99,
            currentWindowId = currentWindow
        )
        assertNotNull(windowMismatchErr)
        assertTrue(windowMismatchErr!!.contains("Window id mismatch"))
    }

    @Test
    fun `live state validation detects session, version, rotation, package, and window changes`() {
        val sessionId = 1000L
        val version = 5L
        val rotation = 0
        val pkg = "com.example.app"
        val windowId = 12

        // Matching live state succeeds
        assertNull(
            DeviceActionValidator.validateLiveState(
                observedSessionId = sessionId,
                liveSessionId = sessionId,
                observedVersion = version,
                liveVersion = version,
                observedRotation = rotation,
                liveRotation = rotation,
                observedPackage = pkg,
                livePackage = pkg,
                observedWindowId = windowId,
                liveWindowId = windowId
            )
        )

        // Session change (service restarted or unbound)
        val sessionErr = DeviceActionValidator.validateLiveState(
            observedSessionId = sessionId,
            liveSessionId = 2000L,
            observedVersion = version,
            liveVersion = version,
            observedRotation = rotation,
            liveRotation = rotation,
            observedPackage = pkg,
            livePackage = pkg,
            observedWindowId = windowId,
            liveWindowId = windowId
        )
        assertNotNull(sessionErr)
        assertTrue(sessionErr!!.contains("session changed"))

        // State version change (same window content or scroll change)
        val verErr = DeviceActionValidator.validateLiveState(
            observedSessionId = sessionId,
            liveSessionId = sessionId,
            observedVersion = version,
            liveVersion = 6L,
            observedRotation = rotation,
            liveRotation = rotation,
            observedPackage = pkg,
            livePackage = pkg,
            observedWindowId = windowId,
            liveWindowId = windowId
        )
        assertNotNull(verErr)
        assertTrue(verErr!!.contains("Screen content or scroll state changed"))

        // Screen rotation change
        val rotErr = DeviceActionValidator.validateLiveState(
            observedSessionId = sessionId,
            liveSessionId = sessionId,
            observedVersion = version,
            liveVersion = version,
            observedRotation = 0,
            liveRotation = 1,
            observedPackage = pkg,
            livePackage = pkg,
            observedWindowId = windowId,
            liveWindowId = windowId
        )
        assertNotNull(rotErr)
        assertTrue(rotErr!!.contains("rotation changed"))

        // Foreground package change
        val pkgErr = DeviceActionValidator.validateLiveState(
            observedSessionId = sessionId,
            liveSessionId = sessionId,
            observedVersion = version,
            liveVersion = version,
            observedRotation = rotation,
            liveRotation = rotation,
            observedPackage = "com.foo",
            livePackage = "com.bar",
            observedWindowId = windowId,
            liveWindowId = windowId
        )
        assertNotNull(pkgErr)
        assertTrue(pkgErr!!.contains("package changed"))

        // Window ID change
        val winErr = DeviceActionValidator.validateLiveState(
            observedSessionId = sessionId,
            liveSessionId = sessionId,
            observedVersion = version,
            liveVersion = version,
            observedRotation = rotation,
            liveRotation = rotation,
            observedPackage = pkg,
            livePackage = pkg,
            observedWindowId = 12,
            liveWindowId = 34
        )
        assertNotNull(winErr)
        assertTrue(winErr!!.contains("window changed"))
    }

    @Test
    fun `max text length constant is 4000 characters`() {
        assertEquals(4000, DeviceActionValidator.MAX_TEXT_LENGTH)
    }

    @Test
    fun `package name rejection handles whitespace and illegal characters`() {
        assertFalse(DeviceActionValidator.isValidPackageName("com.example.app/evil"))
        assertFalse(DeviceActionValidator.isValidPackageName("com.example.app\u0000"))
        assertFalse(DeviceActionValidator.isValidPackageName("com..app"))
        assertFalse(DeviceActionValidator.isValidPackageName(".com.app"))
        assertFalse(DeviceActionValidator.isValidPackageName("com.app."))
    }
}
