package com.example.myapplication

import com.example.myapplication.agent.AgentEngine
import com.example.myapplication.agent.SubagentRunner
import com.example.myapplication.data.model.AppConfig
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.Conversation
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
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

    override suspend fun streamChat(
        config: ProviderConfig,
        system: String,
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
        onEvent: suspend (StreamEvent) -> Unit
    ) {
        receivedConfigs += config
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

    @Test
    fun `plain text reply ends loop`() = runBlocking {
        val fake = FakeProvider(ArrayDeque(listOf(
            listOf(StreamEvent.Text("你好"), StreamEvent.Text("!"), StreamEvent.Done("stop"))
        )))
        val engine = AgentEngine(store, FakeFactory(fake))
        val conv = newConversation()
        engine.run(conv, config)
        assertEquals(2, conv.messages.size)
        assertEquals("你好!", conv.messages.last().content)
        assertTrue(conv.messages.last().toolCalls.isEmpty())
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
        assertEquals(4, conv.messages.size)
        assertEquals("user", conv.messages[0].role)
        assertEquals("assistant", conv.messages[1].role)
        assertEquals("write_file", conv.messages[1].toolCalls.single().name)
        assertEquals("tool", conv.messages[2].role)
        assertEquals("c1", conv.messages[2].toolCallId)
        assertEquals("已生成 out.txt", conv.messages[3].content)
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
        assertEquals("调研一下 Kotlin 协程", fake.receivedMessages[1].single().content)
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
