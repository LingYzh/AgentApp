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

class FileDeletionTest {
    @get:Rule val temp = TemporaryFolder()

    private fun setup(mode: PermissionMode): Triple<FileStore, PermissionSession, ToolExecutor> {
        val store = FileStore(temp.newFolder())
        val session = PermissionSession(store, Conversation(permissionMode = mode), PermissionCoordinator())
        return Triple(store, session, ToolExecutor(store, permissionSession = session))
    }

    private suspend fun pending(session: PermissionSession): PermissionRequest = withTimeout(2000) {
        while (session.coordinator.pending.value.isEmpty()) yield()
        session.coordinator.pending.value.single()
    }

    @Test fun `auto deletes one file without approval but never a directory`() = runBlocking {
        val (store, session, executor) = setup(PermissionMode.AUTO)
        val file = store.writeWorkspace("a.txt", "keep")
        store.writeWorkspace("nested/a.txt", "keep")
        assertTrue(executor.execute(Tools.DELETE_FILE, """{"path":"a.txt"}""").startsWith("已删除"))
        assertFalse(file.exists())
        assertTrue(executor.execute(Tools.DELETE_FILE, """{"path":"nested"}""").startsWith("错误"))
        assertTrue(store.workspaceFile("nested/a.txt").exists())
        assertTrue(session.coordinator.pending.value.isEmpty())
    }

    @Test fun `readonly and plan cannot delete even designated plan`() = runBlocking {
        for (mode in listOf(PermissionMode.READONLY, PermissionMode.PLAN)) {
            val (store, session, executor) = setup(mode)
            val file = store.writeWorkspace(session.planPath, "plan")
            assertTrue(executor.execute(Tools.DELETE_FILE, """{"path":"${session.planPath}"}""").startsWith("错误"))
            assertTrue(file.exists())
            assertTrue(session.coordinator.pending.value.isEmpty())
        }
    }

    @Test fun `accept edit asks each time and never grants always allow`() = runBlocking {
        val (store, session, executor) = setup(PermissionMode.ACCEPT_EDIT)
        val file = store.writeWorkspace("a.txt", "keep")
        for (decision in listOf(PermissionDecision.DENY, PermissionDecision.ALLOW_ALWAYS, PermissionDecision.ALLOW_ONCE)) {
            val job = async { executor.execute(Tools.DELETE_FILE, """{"path":"a.txt"}""") }
            val request = pending(session)
            assertEquals(PermissionRequestKind.FILE_DELETE, request.kind)
            assertEquals(file.canonicalPath, request.filePath)
            assertFalse(request.canAlwaysAllow)
            session.coordinator.resolve(request.id, decision)
            val result = job.await()
            assertEquals(decision != PermissionDecision.ALLOW_ONCE, result.startsWith("错误"))
            assertEquals(decision != PermissionDecision.ALLOW_ONCE, file.exists())
        }
    }

    @Test fun `mode scope and file changes while approving prevent deletion`() = runBlocking {
        for (change in 0..2) {
            val (store, session, executor) = setup(PermissionMode.ACCEPT_EDIT)
            val file = store.writeWorkspace("a.txt", "keep")
            val filePath = file.path.replace("\\", "\\\\")
            val job = async { executor.execute(Tools.DELETE_FILE, """{"path":"$filePath"}""") }
            val request = pending(session)
            when (change) {
                0 -> session.setMode(PermissionMode.READONLY)
                1 -> session.conversation.workingDirectory = temp.newFolder().canonicalPath
                2 -> file.writeText("changed and longer")
            }
            session.coordinator.resolve(request.id, PermissionDecision.ALLOW_ONCE)
            assertTrue(job.await().startsWith("错误"))
            assertTrue(file.exists())
        }
    }

    @Test fun `cancelled child approval leaves file and no pending request`() = runBlocking {
        val (store, parent, _) = setup(PermissionMode.ACCEPT_EDIT)
        val file = store.writeWorkspace("a.txt", "keep")
        val executor = ToolExecutor(store, permissionSession = parent.childSession())
        val job = async { executor.execute(Tools.DELETE_FILE, """{"path":"a.txt"}""") }
        val request = pending(parent)
        job.cancelAndJoin()
        assertTrue(file.exists())
        assertTrue(parent.coordinator.pending.value.isEmpty())
        assertFalse(parent.coordinator.resolve(request.id, PermissionDecision.ALLOW_ONCE))
    }

    @Test fun `delete respects scopes metadata and explicit tool allowlist`() = runBlocking {
        val (store, session, executor) = setup(PermissionMode.AUTO)
        val file = store.writeWorkspace("a.txt", "keep")
        session.conversation.workingDirectory = temp.newFolder("external-cwd").canonicalPath
        val workspacePath = file.path.replace("\\", "\\\\")
        assertTrue(executor.execute(Tools.DELETE_FILE, """{"path":"$workspacePath"}""").startsWith("错误"))
        assertTrue(file.exists())
        session.conversation.workingDirectory = null
        val restricted = ToolExecutor(store, allowedTools = setOf(Tools.READ_FILE), permissionSession = session)
        assertFalse(restricted.specs().any { it.name == Tools.DELETE_FILE })
        assertTrue(restricted.execute(Tools.DELETE_FILE, """{"path":"a.txt"}""").startsWith("错误"))
        store.saveConfig(AppConfig())
        session.setMode(PermissionMode.ACCEPT_EDIT)
        val args = com.example.myapplication.provider.ProviderJson.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.JsonPrimitive(store.configFile.path))
            })
        assertTrue(executor.execute(Tools.DELETE_FILE, args).startsWith("错误"))
        assertTrue(session.coordinator.pending.value.isEmpty())
    }

    @Test fun `symbolic link deletion is refused`() = runBlocking {
        val (store, _, executor) = setup(PermissionMode.AUTO)
        val target = store.writeWorkspace("a.txt", "keep")
        val link = store.workspaceFile("link.txt")
        try { Files.createSymbolicLink(link.toPath(), target.toPath()) }
        catch (e: Exception) { assumeNoException(e); return@runBlocking }
        assertTrue(executor.execute(Tools.DELETE_FILE, """{"path":"link.txt"}""").startsWith("错误"))
        assertTrue(target.exists())
        assertTrue(link.exists())
    }
}
