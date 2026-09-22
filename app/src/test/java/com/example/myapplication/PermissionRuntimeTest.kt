package com.example.myapplication

import com.example.myapplication.agent.CommandPolicy
import com.example.myapplication.agent.AgentEngine
import com.example.myapplication.agent.ConversationContext
import com.example.myapplication.agent.PermissionCoordinator
import com.example.myapplication.agent.PermissionDecision
import com.example.myapplication.agent.PermissionRequestKind
import com.example.myapplication.agent.PermissionRequest
import com.example.myapplication.agent.PermissionSession
import com.example.myapplication.agent.ToolExecutor
import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ApiProvider
import com.example.myapplication.provider.ProviderFactory
import com.example.myapplication.provider.StreamEvent
import com.example.myapplication.provider.ToolSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PermissionRuntimeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: FileStore

    @Before
    fun setUp() {
        store = FileStore(tmp.root)
    }

    @Test
    fun `readonly blocks mutations but can enter plan`() = runBlocking {
        val conversation = Conversation(permissionMode = PermissionMode.READONLY)
        val executor = executor(conversation)

        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"x.txt","content":"x"}""").startsWith("错误:"))
        assertTrue(executor.execute(Tools.SAVE_MEMORY, """{"title":"x","content":"x"}""").startsWith("错误:"))
        assertTrue(executor.execute(Tools.SAVE_SKILL, """{"name":"x","description":"x","content":"x"}""").startsWith("错误:"))
        assertTrue(executor.execute(Tools.ENTER_PLAN_MODE, "{}").contains("已进入计划模式"))
        assertEquals(PermissionMode.PLAN, conversation.permissionMode)
        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"${PermissionSession(store, conversation, PermissionCoordinator()).planPath}","content":"plan"}""").contains("已写入"))
        assertFalse(store.workspaceFile("x.txt").exists())
    }

    @Test
    fun `plan permits only exact non redirected designated plan`() = runBlocking {
        val conversation = Conversation(permissionMode = PermissionMode.PLAN)
        val session = PermissionSession(store, conversation, PermissionCoordinator())
        val executor = executor(conversation, session)

        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"other.md","content":"no"}""").startsWith("错误:"))
        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"${session.planPath}","content":"steps"}""").contains("已写入"))
        assertEquals("steps", session.readPlan())
        assertTrue(executor.execute(Tools.RUN_COMMAND, """{"command":"pwd"}""").startsWith("错误:"))
    }

    @Test
    fun `child session cannot relax or write plan`() = runBlocking {
        val conversation = Conversation(permissionMode = PermissionMode.PLAN)
        val parent = PermissionSession(store, conversation, PermissionCoordinator())
        val child = parent.childSession()
        child.setMode(PermissionMode.AUTO)
        assertEquals(PermissionMode.PLAN, conversation.permissionMode)
        val childExecutor = executor(conversation, child)
        assertTrue(childExecutor.execute(
            Tools.WRITE_FILE,
            """{"path":"${parent.planPath}","content":"forbidden"}"""
        ).startsWith("错误:"))
    }

    @Test
    fun `legacy file writer profile receives plan controls from readonly`() = runBlocking {
        val conversation = Conversation(permissionMode = PermissionMode.READONLY)
        val session = PermissionSession(store, conversation, PermissionCoordinator())
        val executor = ToolExecutor(
            store = store,
            allowedTools = setOf(Tools.READ_FILE, Tools.WRITE_FILE),
            permissionSession = session
        )

        assertTrue(Tools.ENTER_PLAN_MODE in executor.specs().map { it.name })
        assertTrue(Tools.EXIT_PLAN_MODE in executor.specs().map { it.name })
        assertTrue(executor.execute(Tools.ENTER_PLAN_MODE, "{}").contains("已进入计划模式"))
        assertTrue(executor.execute(Tools.WRITE_FILE,
            """{"path":"${session.planPath}","content":"steps"}""").contains("已写入"))
    }

    @Test
    fun `legacy attachment recovery excludes only adjacent native empty failures`() {
        fun attachment(delivery: String) = MessageAttachment(
            name = "image.png", mimeType = "image/png", sizeBytes = 1,
            workspacePath = "image.png", delivery = delivery
        )
        val conversation = Conversation().also { conversation ->
            conversation.messages += ChatMessage(role = "user", attachments = listOf(attachment("native")))
            conversation.messages += ChatMessage(role = "assistant", content = "⚠ network", isError = true)
            conversation.messages += ChatMessage(role = "user", attachments = listOf(attachment("workspace")))
            conversation.messages += ChatMessage(role = "assistant", content = "⚠ network", isError = true)
            conversation.messages += ChatMessage(role = "user", attachments = listOf(attachment("native")))
            conversation.messages += ChatMessage(role = "assistant", content = "⚠ partial warning\n\n⚠ network", isError = true)
        }

        assertTrue(ConversationContext.recoverRejectedAttachments(conversation))
        assertTrue(conversation.messages[0].excludedFromContext)
        assertTrue(conversation.messages[1].excludedFromContext)
        assertFalse(conversation.messages[2].excludedFromContext)
        assertFalse(conversation.messages[3].excludedFromContext)
        assertFalse(conversation.messages[4].excludedFromContext)
        assertFalse(conversation.messages[5].excludedFromContext)
    }

    @Test
    fun `file scope is workspace and extra directory union while managed memory and skills stay available`() = runBlocking {
        val root = store.workspaceFile(".")
        val allowed = File(root, "allowed").apply { mkdirs() }
        val outside = tmp.newFolder("outside")
        val outsidePath = File(outside, "outside.txt").path.replace("\\", "\\\\")
        val conversation = Conversation(allowedDirectories = listOf(allowed.canonicalPath))
        val executor = executor(conversation)

        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"allowed/in.txt","content":"yes"}""").contains("已写入"))
        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"allowed/../outside.txt","content":"workspace"}""").contains("已写入"))
        assertTrue(File(root, "outside.txt").exists())
        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"$outsidePath","content":"no"}""").startsWith("错误:"))
        assertFalse(File(outside, "outside.txt").exists())
        assertTrue(executor.execute(Tools.SAVE_MEMORY, """{"title":"x","content":"y"}""").contains("已保存记忆"))
        assertTrue(executor.execute(Tools.SEARCH_MEMORY, """{"query":"y"}""").contains("[id="))
        val memoryId = store.listMemories().single().id
        assertTrue(executor.execute(Tools.DELETE_MEMORY, """{"id":"$memoryId"}""").contains("已删除记忆"))
        assertTrue(store.listMemories().isEmpty())
        assertTrue(executor.execute(Tools.SAVE_SKILL,
            """{"name":"managed-skill","description":"x","content":"y"}""").contains("已成功保存"))
        assertTrue(executor.execute(Tools.USE_SKILL, """{"name":"managed-skill"}""").contains("y"))
    }

    @Test
    fun `accept edit cannot alter permission metadata but auto can`() = runBlocking {
        val metadataDirectory = requireNotNull(store.configFile.parentFile).canonicalPath
        val accept = Conversation(
            permissionMode = PermissionMode.ACCEPT_EDIT,
            allowedDirectories = listOf(metadataDirectory)
        )
        val auto = Conversation(
            permissionMode = PermissionMode.AUTO,
            allowedDirectories = listOf(metadataDirectory)
        )
        val path = store.configFile.canonicalPath.replace("\\", "\\\\")

        assertTrue(executor(accept).execute(Tools.WRITE_FILE, """{"path":"$path","content":"{}"}""").startsWith("错误:"))
        assertTrue(executor(auto).execute(Tools.WRITE_FILE, """{"path":"$path","content":"{}"}""").contains("已写入"))
    }

    @Test
    fun `plan accepted into auto restores unrestricted memory and command tools`() = runBlocking {
        val coordinator = PermissionCoordinator()
        val conversation = Conversation(permissionMode = PermissionMode.PLAN)
        val session = PermissionSession(store, conversation, coordinator)
        val executor = ToolExecutor(
            store = store,
            permissionSession = session,
            commandExecutor = { command, cwd -> "ran $command in $cwd" }
        )
        assertTrue(executor.execute(Tools.WRITE_FILE,
            """{"path":"${session.planPath}","content":"execute"}""").contains("已写入"))

        val exiting = async { executor.execute(Tools.EXIT_PLAN_MODE, "{}") }
        val request = awaitPending(coordinator)
        assertTrue(coordinator.resolve(request.id, PermissionDecision.ACCEPT_AUTO))
        assertTrue(exiting.await().contains("已切换到 Auto"))

        assertEquals(PermissionMode.AUTO, conversation.permissionMode)
        assertNull(session.toolBlockReason(Tools.SAVE_MEMORY))
        assertNull(session.toolBlockReason(Tools.RUN_COMMAND))
        assertTrue(executor.execute(Tools.SAVE_MEMORY, """{"title":"x","content":"y"}""").contains("已保存记忆"))
        assertTrue(executor.execute(Tools.RUN_COMMAND, """{"command":"pwd"}""").contains("ran pwd"))
    }

    @Test
    fun `plan accepted into auto retains explicit directory scope`() = runBlocking {
        val allowed = File(store.workspaceFile("."), "allowed").apply { mkdirs() }
        val coordinator = PermissionCoordinator()
        val conversation = Conversation(
            permissionMode = PermissionMode.PLAN,
            allowedDirectories = listOf(allowed.canonicalPath)
        )
        val session = PermissionSession(store, conversation, coordinator)
        val executor = ToolExecutor(
            store = store,
            permissionSession = session,
            commandExecutor = { command, _ -> "ran $command" }
        )
        assertTrue(executor.execute(Tools.WRITE_FILE,
            """{"path":"${session.planPath}","content":"execute"}""").contains("已写入"))

        val exiting = async { executor.execute(Tools.EXIT_PLAN_MODE, "{}") }
        val request = awaitPending(coordinator)
        assertTrue(coordinator.resolve(request.id, PermissionDecision.ACCEPT_AUTO))
        exiting.await()

        assertEquals(PermissionMode.AUTO, conversation.permissionMode)
        assertTrue(session.modePrompt().contains("文件工具仍受当前工作目录和额外目录的并集限制"))
        assertTrue(executor.execute(Tools.SAVE_MEMORY, """{"title":"x","content":"y"}""").contains("已保存记忆"))
        assertTrue(executor.execute(Tools.SAVE_SKILL,
            """{"name":"managed-skill","description":"x","content":"y"}""").contains("已成功保存"))
        assertTrue(executor.execute(Tools.USE_SKILL, """{"name":"managed-skill"}""").contains("y"))
        assertTrue(executor.execute(Tools.RUN_COMMAND, """{"command":"pwd"}""").contains("ran pwd"))
        assertTrue(executor.execute(Tools.WRITE_FILE, """{"path":"allowed/in.txt","content":"yes"}""").contains("已写入"))
    }

    @Test
    fun `extra file directories do not block approved shell commands and mode tightening cancels them`() = runBlocking {
        val shellDirectory = tmp.newFolder("shell-cwd").canonicalFile
        val cwdJson = shellDirectory.path.replace("\\", "\\\\")
        val allowed = File(store.workspaceFile("."), "allowed").apply { mkdirs() }
        val coordinator = PermissionCoordinator()
        val conversation = Conversation(allowedDirectories = listOf(allowed.canonicalPath))
        var executedDirectory: String? = null
        val executor = ToolExecutor(
            store = store,
            permissionSession = PermissionSession(store, conversation, coordinator),
            commandExecutor = { _, cwd -> executedDirectory = cwd; "done" }
        )

        val approved = async { executor.execute(Tools.RUN_COMMAND, """{"command":"pwd","cwd":"$cwdJson"}""") }
        val request = awaitPending(coordinator)
        assertEquals(PermissionRequestKind.COMMAND, request.kind)
        assertTrue(coordinator.resolve(request.id, PermissionDecision.ALLOW_ONCE))
        assertEquals("done", approved.await())
        assertEquals(shellDirectory.path, executedDirectory)

        for (mode in listOf(PermissionMode.READONLY, PermissionMode.PLAN)) {
            val tighteningCoordinator = PermissionCoordinator()
            val tighteningConversation = Conversation(allowedDirectories = listOf(allowed.canonicalPath))
            var executed = false
            val tighteningExecutor = ToolExecutor(
                store = store,
                permissionSession = PermissionSession(store, tighteningConversation, tighteningCoordinator),
                commandExecutor = { _, _ -> executed = true; "unexpected" }
            )
            val pending = async { tighteningExecutor.execute(Tools.RUN_COMMAND, """{"command":"pwd"}""") }
            val pendingRequest = awaitPending(tighteningCoordinator)
            tighteningConversation.permissionMode = mode
            assertTrue(tighteningCoordinator.resolve(pendingRequest.id, PermissionDecision.ALLOW_ONCE))
            assertTrue(pending.await().startsWith("错误:"))
            assertFalse(executed)
        }
    }

    @Test
    fun `command approval feedback and cancellation do not execute`() = runBlocking {
        val coordinator = PermissionCoordinator()
        val conversation = Conversation()
        var executed = false
        val executor = ToolExecutor(
            store = store,
            permissionSession = PermissionSession(store, conversation, coordinator),
            commandExecutor = { _, _ -> executed = true; "done" }
        )
        val waiting = async { executor.execute(Tools.RUN_COMMAND, """{"command":"rm x"}""") }
        val request = awaitPending(coordinator)
        assertEquals(PermissionRequestKind.COMMAND, request.kind)
        assertTrue(coordinator.resolve(request.id, PermissionDecision.FEEDBACK, "不要删除"))
        assertTrue(waiting.await().contains("不要删除"))
        assertFalse(executed)

        val cancelled = async { executor.execute(Tools.RUN_COMMAND, """{"command":"echo x"}""") }
        assertNotNull(awaitPending(coordinator))
        cancelled.cancelAndJoin()
        assertTrue(coordinator.pending.value.isEmpty())
        assertFalse(executed)
    }

    @Test
    fun `engine revises plan after feedback then accepts and executes`() = runBlocking {
        val coordinator = PermissionCoordinator()
        val conversation = Conversation(title = "plan", permissionMode = PermissionMode.READONLY)
        conversation.messages += ChatMessage(role = "user", content = "make it")
        val session = PermissionSession(store, conversation, coordinator)
        val scripts = ArrayDeque(listOf(
            listOf(StreamEvent.ToolCall("enter", Tools.ENTER_PLAN_MODE, "{}"), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.ToolCall("draft", Tools.WRITE_FILE, """{"path":"${session.planPath}","content":"draft one"}"""), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.ToolCall("review1", Tools.EXIT_PLAN_MODE, "{}"), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.ToolCall("rewrite", Tools.WRITE_FILE, """{"path":"${session.planPath}","content":"approved plan"}"""), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.ToolCall("review2", Tools.EXIT_PLAN_MODE, "{}"), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.ToolCall("write", Tools.WRITE_FILE, """{"path":"result.txt","content":"done"}"""), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.Text("finished"), StreamEvent.Done("stop"))
        ))
        val provider = object : ApiProvider {
            val systems = mutableListOf<String>()
            override suspend fun streamChat(
                config: ProviderConfig,
                system: String,
                messages: List<ChatMessage>,
                tools: List<ToolSpec>,
                onEvent: suspend (StreamEvent) -> Unit
            ) {
                systems += system
                scripts.removeFirst().forEach { onEvent(it) }
            }
        }
        val factory = object : ProviderFactory() {
            override fun create(type: ProviderType): ApiProvider = provider
        }
        val run = async {
            AgentEngine(store, factory).run(
                conversation,
                ProviderConfig(type = ProviderType.OPENAI),
                permissionSession = session
            )
        }
        val first = awaitPending(coordinator)
        assertEquals(PermissionRequestKind.PLAN, first.kind)
        assertEquals("draft one", first.planText)
        assertTrue(coordinator.resolve(first.id, PermissionDecision.FEEDBACK, "add detail"))

        val second = awaitPending(coordinator)
        assertEquals("approved plan", second.planText)
        assertTrue(coordinator.resolve(second.id, PermissionDecision.ACCEPT_EDIT))
        run.await()

        assertEquals(PermissionMode.ACCEPT_EDIT, conversation.permissionMode)
        assertEquals("done", store.readWorkspace("result.txt"))
        assertTrue(conversation.messages.last { it.contextKind == "environment" }.content.contains("Accept Edit"))
    }

    @Test
    fun `low risk matcher rejects shell injection`() {
        assertTrue(CommandPolicy.isLowRisk("ls -la /sdcard"))
        assertTrue(CommandPolicy.isLowRisk("pwd"))
        assertFalse(CommandPolicy.isLowRisk("ls; rm -rf /"))
        assertFalse(CommandPolicy.isLowRisk("ls $(whoami)"))
        assertFalse(CommandPolicy.isLowRisk("echo hello"))
        assertFalse(CommandPolicy.isLowRisk("wc file | cat"))
    }

    private fun executor(conversation: Conversation, session: PermissionSession = PermissionSession(store, conversation, PermissionCoordinator())): ToolExecutor =
        ToolExecutor(store = store, permissionSession = session)

    private suspend fun awaitPending(coordinator: PermissionCoordinator): PermissionRequest {
        repeat(200) {
            coordinator.pending.value.firstOrNull()?.let { return it }
            delay(2)
        }
        throw AssertionError("approval request was not queued")
    }
}
