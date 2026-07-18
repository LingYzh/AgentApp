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

private class FakeFactory(private val provider: FakeProvider) : ProviderFactory() {
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
}
