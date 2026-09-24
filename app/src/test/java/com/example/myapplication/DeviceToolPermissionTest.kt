package com.example.myapplication

import com.example.myapplication.agent.*
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.store.FileStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeviceToolPermissionTest {
    @get:Rule val temp = TemporaryFolder()

    private class Backend : DeviceToolSession {
        override var enabled = true
        var calls = 0
        var cancel = false
        override suspend fun execute(name: String, args: JsonObject, attach: ((File) -> String?)?): String {
            if (cancel) throw CancellationException("device cancelled")
            calls++
            return "accepted"
        }
        override fun close() = Unit
    }

    @Test fun disabledAndUnauthorizedToolsCannotReachDevice() = runBlocking {
        val store = FileStore(temp.root)
        val backend = Backend().apply { enabled = false }
        val executor = ToolExecutor(store, deviceSession = backend)
        assertTrue(executor.specs().any { it.name == Tools.DEVICE_STATUS })
        assertFalse(executor.specs().any { it.name == Tools.DEVICE_ACTION })
        assertTrue(executor.execute(Tools.DEVICE_OBSERVE, "{}").startsWith("错误"))
        backend.enabled = true
        val restricted = ToolExecutor(store, allowedTools = setOf(Tools.READ_FILE), deviceSession = backend)
        assertTrue(restricted.execute(Tools.DEVICE_OBSERVE, "{}").startsWith("错误"))
        assertEquals(0, backend.calls)
    }

    @Test fun readonlyAndPlanRejectUiAndSystemMutationsButAllowObservation() = runBlocking {
        for (mode in listOf(PermissionMode.READONLY, PermissionMode.PLAN)) {
            val store = FileStore(temp.newFolder())
            val backend = Backend()
            val session = PermissionSession(store, Conversation(permissionMode = mode), PermissionCoordinator())
            val executor = ToolExecutor(store, permissionSession = session, deviceSession = backend)
            assertTrue(executor.execute(Tools.DEVICE_ACTION, """{"action":"home"}""").startsWith("错误"))
            assertTrue(executor.execute(Tools.DEVICE_SYSTEM, """{"operation":"force_stop","package":"com.example.other"}""").startsWith("错误"))
            assertEquals("accepted", executor.execute(Tools.DEVICE_OBSERVE, "{}"))
            assertEquals("accepted", executor.execute(Tools.DEVICE_SYSTEM, """{"operation":"list_apps"}"""))
            assertEquals(2, backend.calls)
        }
    }

    @Test fun approvalIsSpecificAndModeIsRecheckedAfterApproval() = runBlocking {
        val store = FileStore(temp.root)
        val conversation = Conversation(permissionMode = PermissionMode.ACCEPT_EDIT)
        val coordinator = PermissionCoordinator()
        val backend = Backend()
        val executor = ToolExecutor(store, permissionSession = PermissionSession(store, conversation, coordinator), deviceSession = backend)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            executor.execute(Tools.DEVICE_ACTION, """{"action":"home"}""")
        }
        val request = coordinator.pending.value.single()
        assertEquals(PermissionRequestKind.DEVICE_ACTION, request.kind)
        assertEquals(0, backend.calls)
        assertFalse(request.canAlwaysAllow)
        conversation.permissionMode = PermissionMode.READONLY
        coordinator.resolve(request.id, PermissionDecision.ALLOW_ONCE)
        assertTrue(result.await().startsWith("错误"))
        assertEquals(0, backend.calls)
    }

    @Test fun autoDispatchesAndCancellationPropagates() = runBlocking {
        val store = FileStore(temp.root)
        val backend = Backend()
        val executor = ToolExecutor(store, permissionSession = PermissionSession(store,
            Conversation(permissionMode = PermissionMode.AUTO), PermissionCoordinator()), deviceSession = backend)
        assertEquals("accepted", executor.execute(Tools.DEVICE_ACTION, """{"action":"home"}"""))
        backend.cancel = true
        try {
            executor.execute(Tools.DEVICE_OBSERVE, "{}")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun screenLeaseSerializesConversationsAndTracksChildHolders() {
        val lease = DeviceLease()
        assertTrue(lease.acquire("a", "root"))
        assertTrue(lease.acquire("a", "child"))
        assertFalse(lease.acquire("b", "other"))
        lease.release("root")
        assertFalse(lease.acquire("b", "other"))
        lease.release("child")
        assertTrue(lease.acquire("b", "other"))
        lease.release("nonexistent")
        assertFalse(lease.acquire("a", "root"))
    }
}
