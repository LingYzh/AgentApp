package com.example.myapplication

import com.example.myapplication.agent.*
import com.example.myapplication.data.model.*
import com.example.myapplication.data.store.FileStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class PermissionBoundaryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `plan replacement does not modify another hard linked file`() = runBlocking {
        val store = FileStore(temp.root)
        val conv = Conversation(permissionMode = PermissionMode.PLAN)
        val session = PermissionSession(store, conv, PermissionCoordinator())
        val original = store.writeWorkspace("original.txt", "keep")
        val plan = session.planFile()
        plan.parentFile!!.mkdirs()
        try { Files.createLink(plan.toPath(), original.toPath()) }
        catch (e: Exception) { assumeNoException(e); return@runBlocking }
        val result = ToolExecutor(store, permissionSession = session).execute(Tools.WRITE_FILE,
            """{"path":"${session.planPath}","content":"new plan"}""")
        assertFalse(result.startsWith("错误"))
        assertEquals("keep", original.readText())
        assertEquals("new plan", plan.readText())
    }

    @Test fun `directory constraint keeps managed memory available but blocks ordinary files`() = runBlocking {
        val store = FileStore(temp.root)
        val conv = Conversation(allowedDirectories = listOf(store.workspaceDir.canonicalPath))
        val executor = ToolExecutor(store, permissionSession = PermissionSession(store, conv, PermissionCoordinator()))
        assertTrue(executor.execute(Tools.SAVE_MEMORY, """{"title":"x","content":"y"}""").contains("已保存记忆"))
        assertTrue(store.listMemories().isNotEmpty())
        val memory = store.listMemories().single()
        assertTrue(executor.execute(Tools.SAVE_MEMORY,
            """{"id":"${memory.id}","title":"updated","content":"new content"}""").contains("已保存记忆"))
        assertEquals(1, store.listMemories().size)
        assertEquals("new content", store.readMemory(memory.id))
        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"../outside.txt","content":"y"}""").startsWith("错误"))
    }

    @Test fun `explicit exact approval supports configured commands without prefix grants`() = runBlocking {
        val store = FileStore(temp.root)
        store.saveConfig(AppConfig(autoApprovedCommands = listOf("echo configured")))
        val coordinator = PermissionCoordinator()
        val session = PermissionSession(store, Conversation(), coordinator)
        assertNull(session.authorizeCommand("echo configured", store.workspaceDir.path))
        val waiting = async { session.authorizeCommand("echo configured; echo extra", store.workspaceDir.path) }
        val request = withTimeout(2000) {
            while (coordinator.pending.value.isEmpty()) yield()
            coordinator.pending.value.single()
        }
        assertFalse(request.canAlwaysAllow)
        coordinator.resolve(request.id, PermissionDecision.DENY)
        assertNotNull(waiting.await())
    }

    @Test fun `cancelled plan approval cannot be accepted afterwards`() = runBlocking {
        val store = FileStore(temp.root)
        val coordinator = PermissionCoordinator()
        val conv = Conversation(permissionMode = PermissionMode.PLAN)
        val session = PermissionSession(store, conv, coordinator)
        store.writeWorkspace(session.planPath, "plan")
        val waiting = async { session.exitPlan() }
        val request = withTimeout(2000) {
            while (coordinator.pending.value.isEmpty()) yield()
            coordinator.pending.value.single()
        }
        waiting.cancelAndJoin()
        assertFalse(coordinator.resolve(request.id, PermissionDecision.ACCEPT_AUTO))
        assertEquals(PermissionMode.PLAN, conv.permissionMode)
    }
}
