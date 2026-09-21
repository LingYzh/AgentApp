package com.example.myapplication

import com.example.myapplication.agent.AgentEngine
import com.example.myapplication.agent.SubagentRegistry
import com.example.myapplication.agent.SubagentRunner
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class StopTestFactory(private val provider: ApiProvider) : ProviderFactory() {
    override fun create(type: ProviderType): ApiProvider = provider
}

class SubagentRunnerStopTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: FileStore
    private val config = ProviderConfig(name = "fake", type = ProviderType.OPENAI, model = "m-main")

    @Before
    fun setUp() {
        store = FileStore(tmp.root)
    }

    @Test
    fun `user stop returns reason to parent and parent continues`() = runBlocking {
        val childStarted = CompletableDeferred<Unit>()
        val parentContinuationStarted = CompletableDeferred<Unit>()
        val allowParentFinish = CompletableDeferred<Unit>()
        var calls = 0
        val provider = object : ApiProvider {
            override suspend fun streamChat(
                config: ProviderConfig,
                system: String,
                messages: List<ChatMessage>,
                tools: List<ToolSpec>,
                onEvent: suspend (StreamEvent) -> Unit
            ) {
                when (++calls) {
                    1 -> {
                        onEvent(StreamEvent.ToolCall("sub-1", "run_subagent", "{\"task\":\"耗时任务\"}"))
                        onEvent(StreamEvent.Done("tool_calls"))
                    }
                    2 -> {
                        childStarted.complete(Unit)
                        awaitCancellation()
                    }
                    3 -> {
                        assertTrue(messages.last().content.contains("用户已中止子代理"))
                        assertTrue(messages.last().content.contains("不再需要"))
                        parentContinuationStarted.complete(Unit)
                        allowParentFinish.await()
                        onEvent(StreamEvent.Text("父任务已调整策略"))
                        onEvent(StreamEvent.Done("stop"))
                    }
                }
            }
        }
        val registry = SubagentRegistry()
        val runner = SubagentRunner(store, StopTestFactory(provider), registry = registry)
        val engine = AgentEngine(store, StopTestFactory(provider), runner)
        val parent = Conversation(title = "parent").also {
            it.messages += ChatMessage(role = "user", content = "开始")
        }

        val parentJob = async { engine.run(parent, config) }
        childStarted.await()
        val child = withTimeout(1_000) {
            var found = store.listChildConversations(parent.id).singleOrNull()
            while (found == null) {
                yield()
                found = store.listChildConversations(parent.id).singleOrNull()
            }
            found
        }

        assertTrue(registry.isRunning(child.id))
        assertTrue(registry.stop(child.id, "不再需要"))
        assertFalse(registry.stop(child.id, "不能覆盖首次理由"))
        parentContinuationStarted.await()
        assertTrue(parentJob.isActive)
        allowParentFinish.complete(Unit)
        parentJob.await()

        assertTrue(parentJob.isActive.not())
        assertEquals("父任务已调整策略", parent.messages.last().content)
        val savedChild = store.loadConversation(child.id)!!
        assertEquals("cancelled", savedChild.executionStatus)
        assertEquals("不再需要", savedChild.stopReason)
        assertFalse(registry.isRunning(child.id))
        assertFalse(registry.stop(child.id, "重复理由"))
        assertEquals("不再需要", store.loadConversation(child.id)!!.stopReason)
    }

    @Test
    fun `completed child cannot be stopped or reported running`() = runBlocking {
        val provider = object : ApiProvider {
            override suspend fun streamChat(
                config: ProviderConfig,
                system: String,
                messages: List<ChatMessage>,
                tools: List<ToolSpec>,
                onEvent: suspend (StreamEvent) -> Unit
            ) {
                onEvent(StreamEvent.Text("完成"))
                onEvent(StreamEvent.Done("stop"))
            }
        }
        val registry = SubagentRegistry()
        val runner = SubagentRunner(store, StopTestFactory(provider), registry = registry)

        val result = runner.run("快速任务", null, null, config)
        val child = store.listConversations().single()

        assertEquals("完成", result)
        assertEquals("completed", child.executionStatus)
        assertFalse(registry.isRunning(child.id))
        assertFalse(registry.stop(child.id, "太晚了"))
        assertEquals(null, store.loadConversation(child.id)!!.stopReason)
    }

    @Test
    fun `parent cancellation propagates through subagent`() = runBlocking {
        val childStarted = CompletableDeferred<Unit>()
        val provider = object : ApiProvider {
            var calls = 0

            override suspend fun streamChat(
                config: ProviderConfig,
                system: String,
                messages: List<ChatMessage>,
                tools: List<ToolSpec>,
                onEvent: suspend (StreamEvent) -> Unit
            ) {
                when (++calls) {
                    1 -> {
                        onEvent(StreamEvent.ToolCall("sub-1", "run_subagent", "{\"task\":\"耗时任务\"}"))
                        onEvent(StreamEvent.Done("tool_calls"))
                    }
                    2 -> {
                        childStarted.complete(Unit)
                        awaitCancellation()
                    }
                }
            }
        }
        val registry = SubagentRegistry()
        val factory = StopTestFactory(provider)
        val engine = AgentEngine(store, factory, SubagentRunner(store, factory, registry = registry))
        val parent = Conversation(title = "parent").also {
            it.messages += ChatMessage(role = "user", content = "开始")
        }

        val parentJob = async { engine.run(parent, config) }
        childStarted.await()
        parentJob.cancelAndJoin()

        assertTrue(parentJob.isCancelled)
        val child = store.listChildConversations(parent.id).single()
        assertEquals("cancelled", store.loadConversation(child.id)!!.executionStatus)
        assertFalse(registry.isRunning(child.id))
    }
}
