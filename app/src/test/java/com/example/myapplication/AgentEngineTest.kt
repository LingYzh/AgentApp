package com.example.myapplication

import com.example.myapplication.agent.AgentEngine
import com.example.myapplication.agent.SubagentRunner
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.PermissionMode
import com.example.myapplication.data.model.MessageAttachment
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ApiProvider
import com.example.myapplication.provider.ProviderFactory
import com.example.myapplication.provider.StreamEvent
import com.example.myapplication.provider.ToolSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 按脚本回放事件的假 Provider */
private class FakeProvider(
    private val script: ArrayDeque<List<StreamEvent>>
) : ApiProvider {
    val receivedTools = mutableListOf<List<ToolSpec>>()
    val receivedMessages = mutableListOf<List<ChatMessage>>()
    val receivedConfigs = mutableListOf<ProviderConfig>()
    val receivedSystems = mutableListOf<String>()

    override suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    ) {
        receivedConfigs += config
        receivedSystems += system
        receivedTools += tools
        receivedMessages += messages.toList()
        val events = script.removeFirstOrNull() ?: listOf(StreamEvent.Done(null))
        events.forEach { onEvent(it) }
    }
}

private class FakeFactory(private val provider: ApiProvider) : ProviderFactory() {
    override fun create(type: ProviderType): ApiProvider = provider
}

class AgentEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: FileStore
    private val config = ProviderConfig(name = "fake", type = ProviderType.OPENAI, model = "m-main")

    @Before
    fun setUp() {
        store = FileStore(tmp.root)
    }

    private fun newConversation(): Conversation =
        Conversation(title = "t").also {
            it.messages += ChatMessage(role = "user", content = "hi")
        }

    private fun visible(messages: List<ChatMessage>): List<ChatMessage> =
        messages.filter { it.contextKind == null }

    @Test fun `thinking only truncation is visible and partial tool calls are not executed`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(listOf(
            StreamEvent.Thinking("unfinished reasoning"),
            StreamEvent.ToolCall("write", "write_file", """{"path":"must-not-write.txt","content":"partial"}"""),
            StreamEvent.Done("max_tokens")
        ))))
        val conv = newConversation()
        AgentEngine(store, FakeFactory(fake)).run(conv, config)
        val reply = conv.messages.first { it.role == "assistant" }
        assertTrue(reply.isError)
        assertTrue(reply.content.contains("截断"))
        assertEquals("unfinished reasoning", reply.thinking)
        assertFalse(store.workspaceFile("must-not-write.txt").exists())
        assertTrue(conv.messages.last { it.role == "tool" }.isError)
    }

    @Test fun `live effort changes apply next request and usage is persisted`() = runBlocking {
        val conv = newConversation()
        val received = mutableListOf<com.example.myapplication.data.model.ReasoningEffort?>()
        val provider = object : ApiProvider {
            override suspend fun streamChat(config: ProviderConfig, system: String, messages: List<ChatMessage>,
                tools: List<ToolSpec>, onEvent: suspend (StreamEvent) -> Unit) {
                received += config.reasoningEffort
                if (received.size == 1) {
                    conv.reasoningEffortOverride = com.example.myapplication.data.model.ReasoningEffort.XHIGH
                    onEvent(StreamEvent.ToolCall("list", "list_files", "{}"))
                } else {
                    onEvent(StreamEvent.Text("done"))
                    onEvent(StreamEvent.Usage(com.example.myapplication.data.model.TokenUsage(123, 45)))
                }
                onEvent(StreamEvent.Done("stop"))
            }
        }
        AgentEngine(store, FakeFactory(provider)).run(conv, config)
        assertEquals(listOf(null, com.example.myapplication.data.model.ReasoningEffort.XHIGH), received)
        assertEquals(123L, store.loadConversation(conv.id)!!.lastContextUsage!!.usage.inputTokens)
    }

    @Test fun `opaque provider blocks persist and reach the next tool round`() = runBlocking {
        val blocks = listOf(com.example.myapplication.provider.ProviderJson.parseToJsonElement(
            """{"type":"thinking","thinking":"summary","signature":"opaque-signature"}""") as kotlinx.serialization.json.JsonObject)
        val provider = FakeProvider(ArrayDeque(listOf(
            listOf(StreamEvent.Thinking("summary"), StreamEvent.ProviderBlocks("anthropic", blocks),
                StreamEvent.ToolCall("read", "list_files", "{}"), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.Text("done"), StreamEvent.Done("stop"))
        )))
        val conversation = newConversation()
        AgentEngine(store, FakeFactory(provider)).run(conversation, config)
        assertEquals(blocks, provider.receivedMessages[1].first { it.toolCalls.isNotEmpty() }.providerBlocks["anthropic"])
        assertEquals(blocks, store.loadConversation(conversation.id)!!.messages.first { it.toolCalls.isNotEmpty() }.providerBlocks["anthropic"])
    }

    @Test
    fun `large workspace image is delivered after every tool result`() = runBlocking {
        store.workspaceFile("large.png").writeBytes(ByteArray(5 * 1024 * 1024))
        store.writeWorkspace("notes.txt", "hello")
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(StreamEvent.ToolCall("image", "read_file", """{"path":"large.png"}"""),
                StreamEvent.ToolCall("text", "read_file", """{"path":"notes.txt"}"""), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.Text("seen"), StreamEvent.Done("stop"))
        )))
        val vision = config.copy(capabilityOverrides = mapOf(config.model to
            com.example.myapplication.data.model.ModelCapabilities(image = true)))
        AgentEngine(store, FakeFactory(fake)).run(newConversation(), vision)
        val sent = visible(fake.receivedMessages.last())
        assertEquals(listOf("user", "assistant", "tool", "tool", "user"), sent.map { it.role })
        assertEquals("hello", sent[3].content)
        assertEquals("image", sent.last().originToolCallId)
        assertEquals(5L * 1024 * 1024, sent.last().attachments.single().sizeBytes)
        assertEquals("native", sent.last().attachments.single().delivery)
    }

    @Test
    fun `plain text reply ends loop`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(StreamEvent.Text("你好"), StreamEvent.Text("!"), StreamEvent.Done("stop"))
        )))
        val engine = AgentEngine(store, FakeFactory(fake))
        val conv = newConversation()
        engine.run(conv, config)
        val visible = visible(conv.messages)
        assertEquals(2, visible.size)
        assertEquals("你好!", visible.last().content)
        assertTrue(visible.last().toolCalls.isEmpty())
    }

    @Test
    fun `permission changes append environment while system and tool schema stay stable`() = runBlocking {
        val fake = FakeProvider(ArrayDeque<List<StreamEvent>>(listOf(
            listOf(StreamEvent.ToolCall("plan", "enter_plan_mode", "{}"), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.Text("planning"), StreamEvent.Done("stop"))
        )))
        val conversation = newConversation().also { it.permissionMode = PermissionMode.READONLY }

        AgentEngine(store, FakeFactory(fake)).run(conversation, config)

        val environments = conversation.messages.filter { it.contextKind == "environment" }
        assertEquals(2, environments.size)
        assertTrue(environments.first().content.contains("READONLY"))
        assertTrue(environments.last().content.contains("PLAN"))
        assertEquals(listOf(conversation.systemPromptSnapshot, conversation.systemPromptSnapshot), fake.receivedSystems)
        assertEquals(fake.receivedTools[0], fake.receivedTools[1])
        val firstRequest = fake.receivedMessages.first()
        assertEquals(firstRequest, fake.receivedMessages.last().take(firstRequest.size))
    }

    @Test
    fun `unchanged environment is reused across sends and scope update only appends`() = runBlocking {
        val fake = FakeProvider(ArrayDeque<List<StreamEvent>>(List(3) {
            listOf(StreamEvent.Text("ok"), StreamEvent.Done("stop"))
        }))
        val conversation = newConversation()
        val engine = AgentEngine(store, FakeFactory(fake))
        engine.run(conversation, config)
        val prefix = conversation.messages.toList()
        conversation.messages += ChatMessage(role = "user", content = "again")
        engine.run(conversation, config)
        assertEquals(1, conversation.messages.count { it.contextKind == "environment" })
        assertEquals(prefix, fake.receivedMessages[1].take(prefix.size))
        val secondPrefix = conversation.messages.toList()
        conversation.allowedDirectories = listOf(store.workspaceDir.canonicalPath)
        conversation.messages += ChatMessage(role = "user", content = "new scope")
        engine.run(conversation, config)
        assertEquals(2, conversation.messages.count { it.contextKind == "environment" })
        assertEquals(secondPrefix, fake.receivedMessages[2].take(secondPrefix.size))
        assertEquals(1, fake.receivedSystems.distinct().size)
        assertEquals(1, fake.receivedTools.distinct().size)
    }

    @Test
    fun `empty rejection removes all new media in a tool batch and keeps protocol results`() = runBlocking {
        store.writeWorkspace("one.png", "one")
        store.writeWorkspace("two.png", "two")
        val fake = FakeProvider(ArrayDeque<List<StreamEvent>>(listOf(
            listOf(StreamEvent.ToolCall("one", "read_file", """{"path":"one.png"}"""),
                StreamEvent.ToolCall("two", "read_file", """{"path":"two.png"}"""), StreamEvent.Done("tool_calls")),
            listOf(StreamEvent.Error("unsupported image")),
            listOf(StreamEvent.Text("recovered"), StreamEvent.Done("stop"))
        )))
        val conversation = newConversation()
        val engine = AgentEngine(store, FakeFactory(fake))
        val vision = config.copy(capabilityOverrides = mapOf(config.model to
            com.example.myapplication.data.model.ModelCapabilities(image = true)))
        engine.run(conversation, vision)
        val media = conversation.messages.filter { it.originToolCallId != null }
        assertEquals(2, media.size)
        assertTrue(media.all { it.excludedFromContext })
        assertTrue(conversation.messages.filter { it.role == "tool" }.none { it.excludedFromContext })
        conversation.messages += ChatMessage(role = "user", content = "continue as text")
        engine.run(conversation, config)
        assertTrue(fake.receivedMessages.last().none { it.attachments.isNotEmpty() })
        assertEquals(2, fake.receivedMessages.last().count { it.role == "tool" })
    }

    @Test
    fun `empty failed attachment request is excluded without losing an older accepted attachment`() = runBlocking {
        val attachment = MessageAttachment(
            name = "image.png", mimeType = "image/png", sizeBytes = 1,
            workspacePath = "image.png", delivery = "native"
        )
        val conversation = Conversation(title = "attachments").also {
            it.messages += ChatMessage(role = "user", content = "older", attachments = listOf(attachment))
            it.messages += ChatMessage(role = "assistant", content = "accepted")
            it.messages += ChatMessage(role = "user", content = "retry me", attachments = listOf(attachment))
        }
        val fake = FakeProvider(ArrayDeque<List<StreamEvent>>(listOf(
            listOf(StreamEvent.Error("network")),
            listOf(StreamEvent.Text("recovered"), StreamEvent.Done("stop"))
        )))
        val engine = AgentEngine(store, FakeFactory(fake))

        engine.run(conversation, config)
        assertFalse(conversation.messages[0].excludedFromContext)
        assertTrue(conversation.messages[2].excludedFromContext)
        assertTrue(conversation.messages.last { it.role == "assistant" }.excludedFromContext)

        conversation.messages += ChatMessage(role = "user", content = "new request")
        engine.run(conversation, config)
        val replay = fake.receivedMessages.last()
        assertTrue(replay.any { it.content == "older" && !it.excludedFromContext })
        assertFalse(replay.any { it.content == "retry me" })
    }

    @Test
    fun `tool call writes file then final reply`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            // 第一轮：模型调用 write_file
            listOf(
                StreamEvent.ToolCall("c1", "write_file", """{"path":"out.txt","content":"生成内容"}"""),
                StreamEvent.Done("tool_calls")
            ),
            // 第二轮：最终回复
            listOf(StreamEvent.Text("已生成 out.txt"), StreamEvent.Done("stop"))
        )))
        val engine = AgentEngine(store, FakeFactory(fake))
        val conv = newConversation()
        engine.run(conv, config)

        // 文件已写入工作区
        assertEquals("生成内容", store.readWorkspace("out.txt"))
        // 消息序列：user → assistant(toolCalls) → tool → assistant(final)
        val visible = visible(conv.messages)
        assertEquals(4, visible.size)
        assertEquals("user", visible[0].role)
        assertEquals("assistant", visible[1].role)
        assertEquals("write_file", visible[1].toolCalls.single().name)
        assertEquals("tool", visible[2].role)
        assertEquals("c1", visible[2].toolCallId)
        assertEquals("已生成 out.txt", visible[3].content)
        // 第二轮请求里 Provider 收到了 tool 结果
        assertEquals("tool", fake.receivedMessages[1].last().role)
    }

    @Test
    fun `loop cap stops runaway tool calls`() = runBlocking {
        val infiniteCall = List(5) {
            listOf(
                StreamEvent.ToolCall("c$it", "list_files", "{}"),
                StreamEvent.Done("tool_calls")
            )
        }
        val fake = FakeProvider(ArrayDeque(infiniteCall))
        val engine = AgentEngine(store, FakeFactory(fake))
        val conv = newConversation()
        engine.run(conv, config, maxLoops = 2)
        assertTrue(conv.messages.last().content.contains("最大工具循环"))
    }

    @Test
    fun `thinking is captured`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(StreamEvent.Thinking("想一下"), StreamEvent.Text("答"), StreamEvent.Done("stop"))
        )))
        val engine = AgentEngine(store, FakeFactory(fake))
        val conv = newConversation()
        engine.run(conv, config)
        assertEquals("想一下", conv.messages.last().thinking)
        assertEquals("答", conv.messages.last().content)
    }

    @Test
    fun `subagent runs with task prompt and no nested run_subagent`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            // 主 Agent 委派（新签名：task）
            listOf(
                StreamEvent.ToolCall("s1", "run_subagent", """{"task":"调研一下 Kotlin 协程"}"""),
                StreamEvent.Done("tool_calls")
            ),
            // 子 Agent 回复
            listOf(StreamEvent.Text("调研结论"), StreamEvent.Done("stop")),
            // 主 Agent 总结
            listOf(StreamEvent.Text("结论是…"), StreamEvent.Done("stop"))
        )))
        val runner = SubagentRunner(store, FakeFactory(fake))
        val engine = AgentEngine(store, FakeFactory(fake), runner)
        val conv = newConversation()
        engine.run(conv, config)

        assertEquals("结论是…", conv.messages.last().content)
        // 第二次调用是子代理请求：任务作为 user 消息，工具集中不含 run_subagent，模型继承主代理
        assertEquals("调研一下 Kotlin 协程", visible(fake.receivedMessages[1]).single().content)
        assertEquals("m-main", fake.receivedConfigs[1].model)
        val subagentTools = fake.receivedTools[1].map { it.name }
        assertTrue("run_subagent" !in subagentTools)
        assertTrue("read_file" in subagentTools)
    }

    @Test
    fun `subagent uses settings-forced model with highest priority`() = runBlocking {
        val forced = ProviderConfig(id = "p2", name = "forced", type = ProviderType.OPENAI, model = "m-forced")
        store.saveConfig(
            AppConfig(
                providers = listOf(config, forced),
                selectedProviderId = config.id,
                subagentProviderId = forced.id,
                subagentModel = "m-forced"
            )
        )
        val fake = FakeProvider(ArrayDeque(listOf(
            // 主代理尝试指定别的模型，应被设置覆盖
            listOf(
                StreamEvent.ToolCall("s1", "run_subagent", """{"task":"干活","model":"m-other"}"""),
                StreamEvent.Done("tool_calls")
            ),
            listOf(StreamEvent.Text("done"), StreamEvent.Done("stop")),
            listOf(StreamEvent.Text("ok"), StreamEvent.Done("stop"))
        )))
        val runner = SubagentRunner(store, FakeFactory(fake))
        val engine = AgentEngine(store, FakeFactory(fake), runner)
        engine.run(newConversation(), config)

        assertEquals("m-forced", fake.receivedConfigs[1].model)
    }

    @Test
    fun `subagent model can be specified by main agent`() = runBlocking {
        val other = ProviderConfig(
            id = "p2", name = "备用", type = ProviderType.OPENAI,
            model = "m-default", models = listOf("m-default", "m-pro")
        )
        store.saveConfig(
            AppConfig(providers = listOf(config, other), selectedProviderId = config.id)
        )
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(
                StreamEvent.ToolCall("s1", "run_subagent", """{"task":"干活","provider_name":"备用","model":"m-pro"}"""),
                StreamEvent.Done("tool_calls")
            ),
            listOf(StreamEvent.Text("done"), StreamEvent.Done("stop")),
            listOf(StreamEvent.Text("ok"), StreamEvent.Done("stop"))
        )))
        val runner = SubagentRunner(store, FakeFactory(fake))
        val engine = AgentEngine(store, FakeFactory(fake), runner)
        engine.run(newConversation(), config)

        assertEquals("p2", fake.receivedConfigs[1].id)
        assertEquals("m-pro", fake.receivedConfigs[1].model)
    }

    @Test
    fun `cancellation preserves partial stream and propagates`() = runBlocking {
        val emitted = CompletableDeferred<Unit>()
        val fake = object : ApiProvider {
            override suspend fun streamChat(
                config: ProviderConfig,
                system: String,
                messages: List<ChatMessage>,
                tools: List<ToolSpec>,
                onEvent: suspend (StreamEvent) -> Unit
            ) {
                onEvent(StreamEvent.Thinking("思考片段"))
                onEvent(StreamEvent.Text("部分回复"))
                emitted.complete(Unit)
                awaitCancellation()
            }
        }
        val conv = newConversation()
        val engine = AgentEngine(store, FakeFactory(fake))
        val job = launch { engine.run(conv, config) }

        emitted.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        val assistant = conv.messages.last { it.role == "assistant" }
        assertTrue(assistant.content.contains("部分回复"))
        assertTrue(assistant.content.contains("已停止"))
        assertEquals("思考片段", assistant.thinking)
        assertTrue(store.loadConversation(conv.id)?.messages?.last()?.content?.contains("已停止") == true)
    }

    @Test
    fun `cancellation before subsequent tool leaves balanced responses`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(
                StreamEvent.ToolCall("first", "list_files", "{}"),
                StreamEvent.ToolCall("second", "write_file", """{"path":"should-not-exist.txt","content":"x"}"""),
                StreamEvent.Done("tool_calls")
            )
        )))
        val conv = newConversation()
        val engine = AgentEngine(store, FakeFactory(fake))
        lateinit var runJob: Job
        runJob = launch(start = CoroutineStart.LAZY) {
            engine.run(
                conv,
                config,
                callbacks = AgentEngine.Callbacks(
                    onMessageAdded = { message ->
                        if (message.role == "tool" && message.toolCallId == "first") {
                            runJob.cancel()
                        }
                    }
                )
            )
        }
        runJob.start()
        runJob.join()

        assertTrue(runJob.isCancelled)
        val toolMessages = conv.messages.filter { it.role == "tool" }
        assertEquals(listOf("first", "second"), toolMessages.map { it.toolCallId })
        assertTrue(toolMessages[1].content.contains("未执行"))
        assertTrue(store.listWorkspace().none { it == "should-not-exist.txt" })
    }

    @Test
    fun `stream error with tool calls skips every tool`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(
                StreamEvent.ToolCall("bad", "write_file", """{"path":"error.txt","content":"x"}"""),
                StreamEvent.Error("上游失败"),
                StreamEvent.Done("error")
            )
        )))
        val conv = newConversation()
        AgentEngine(store, FakeFactory(fake)).run(conv, config)

        val assistant = conv.messages.first { it.role == "assistant" }
        val tool = conv.messages.single { it.role == "tool" }
        assertTrue(assistant.isError)
        assertTrue(assistant.content.contains("上游失败"))
        assertTrue(tool.content.contains("未执行"))
        assertTrue(store.listWorkspace().none { it == "error.txt" })
    }

    @Test
    fun `loop cap balances all pending tool calls`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(
                StreamEvent.ToolCall("c1", "write_file", """{"path":"cap-1.txt","content":"x"}"""),
                StreamEvent.ToolCall("c2", "write_file", """{"path":"cap-2.txt","content":"x"}"""),
                StreamEvent.Done("tool_calls")
            )
        )))
        val conv = newConversation()
        AgentEngine(store, FakeFactory(fake)).run(conv, config, maxLoops = 0)

        val toolMessages = conv.messages.filter { it.role == "tool" }
        assertEquals(listOf("c1", "c2"), toolMessages.map { it.toolCallId })
        assertTrue(toolMessages.all { it.content.contains("未执行") })
        assertTrue(conv.messages.last().content.contains("最大工具循环"))
        assertTrue(store.listWorkspace().none { it.startsWith("cap-") })
    }

    @Test
    fun `subagent failure is not replaced by an earlier assistant reply`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(
                StreamEvent.Text("正在处理"),
                StreamEvent.ToolCall("first", "list_files", "{}"),
                StreamEvent.Done("tool_calls")
            ),
            listOf(StreamEvent.Error("连接失败"))
        )))
        val result = SubagentRunner(store, FakeFactory(fake)).run("任务", null, null, config)

        assertTrue(result.startsWith("错误:"))
        assertTrue(result.contains("连接失败"))
        assertTrue(!result.contains("正在处理"))
    }
}
