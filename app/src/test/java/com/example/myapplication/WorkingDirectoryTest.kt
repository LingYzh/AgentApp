package com.example.myapplication

import com.example.myapplication.agent.PermissionCoordinator
import com.example.myapplication.agent.PermissionSession
import com.example.myapplication.agent.ToolExecutor
import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.store.FileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkingDirectoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: FileStore

    @Before
    fun setUp() {
        store = FileStore(temp.root)
    }

    @Test
    fun `relative files list and shell start from selected directory`() = runBlocking {
        val project = store.workspaceFile("project").also { check(it.mkdirs()) }.canonicalFile
        val conversation = Conversation(
            permissionMode = PermissionMode.AUTO,
            workingDirectory = project.path
        )
        val session = PermissionSession(store, conversation, PermissionCoordinator())
        var commandDirectory = ""
        val executor = ToolExecutor(
            store = store,
            permissionSession = session,
            commandExecutor = { _, cwd -> commandDirectory = cwd; "command complete" }
        )

        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"note.txt","content":"inside"}""").contains("已写入"))
        assertEquals("inside", store.workspaceFile("project/note.txt").readText())
        assertTrue(executor.execute(Tools.LIST_FILES, "{}").contains("note.txt"))
        assertEquals("command complete", executor.execute(Tools.RUN_COMMAND, """{"command":"pwd"}"""))
        assertEquals(project.path, commandDirectory)
    }

    @Test
    fun `workspace attachment references require workspace in file scope after changing directory`() = runBlocking {
        val project = temp.newFolder("external-project").canonicalFile
        store.writeWorkspace("attachments/imported/note.txt", "saved attachment")
        val session = PermissionSession(
            store,
            Conversation(workingDirectory = project.path),
            PermissionCoordinator()
        )

        val result = ToolExecutor(store, permissionSession = session).execute(
            Tools.READ_FILE,
            """{"path":"attachments/imported/note.txt"}"""
        )

        assertTrue(result.contains("文件工具有效范围"))
        session.conversation.allowedDirectories = listOf(store.workspaceDir.canonicalPath)
        assertEquals("saved attachment", ToolExecutor(store, permissionSession = session).execute(
            Tools.READ_FILE,
            """{"path":"attachments/imported/note.txt"}"""
        ))
    }

    @Test
    fun `invalid selected directory is rejected and plan path stays workspace based`() = runBlocking {
        val project = store.workspaceFile("project").also { check(it.mkdirs()) }.canonicalFile
        val missing = store.workspaceFile("missing").canonicalFile
        val invalid = Conversation(workingDirectory = missing.path)
        val invalidSession = PermissionSession(store, invalid, PermissionCoordinator())
        try {
            invalidSession.validateWorkingDirectory(missing.path)
            throw AssertionError("expected invalid directory")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message?.contains("不存在") == true)
        }
        assertTrue(ToolExecutor(store, permissionSession = invalidSession)
            .execute(Tools.LIST_FILES, "{}").contains("工作目录不存在"))

        val plan = Conversation(permissionMode = PermissionMode.PLAN, workingDirectory = project.path)
        val planSession = PermissionSession(store, plan, PermissionCoordinator())
        val result = ToolExecutor(store, permissionSession = planSession).execute(
            Tools.WRITE_FILE,
            """{"path":"${planSession.planPath}","content":"steps"}"""
        )
        assertTrue(result.contains("已写入"))
        assertEquals("steps", planSession.planFile().readText())
    }

    @Test
    fun `validation requires an absolute existing directory independently from extra scope`() {
        val allowed = temp.newFolder("allowed").canonicalFile
        val session = PermissionSession(
            store,
            Conversation(allowedDirectories = listOf(allowed.path)),
            PermissionCoordinator()
        )

        try {
            session.validateWorkingDirectory("relative")
            throw AssertionError("expected relative path rejection")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message?.contains("绝对路径") == true)
        }
        assertEquals(null, session.validateWorkingDirectory(null))
        assertEquals(allowed.path, session.validateWorkingDirectory(allowed.path))
    }

    @Test
    fun `absolute additional directory remains usable after selected cwd is deleted`() = runBlocking {
        val allowed = temp.newFolder("allowed").canonicalFile
        val file = File(allowed, "note.txt").also { it.writeText("external") }
        val jsonPath = file.path.replace("\\", "\\\\")
        val deletedCwd = temp.newFolder("deleted-cwd").canonicalFile
        val session = PermissionSession(
            store,
            Conversation(workingDirectory = deletedCwd.path, allowedDirectories = listOf(allowed.path)),
            PermissionCoordinator()
        )
        check(deletedCwd.delete())

        val result = ToolExecutor(store, permissionSession = session).execute(
            Tools.READ_FILE,
            """{"path":"$jsonPath"}"""
        )

        assertEquals("external", result)
        assertTrue(ToolExecutor(store, permissionSession = session).execute(Tools.LIST_FILES, "{}").contains("工作目录不存在"))
    }
}
