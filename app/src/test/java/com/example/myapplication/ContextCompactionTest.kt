package com.example.myapplication

import com.example.myapplication.agent.*
import com.example.myapplication.data.model.*
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContextCompactionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val config = ProviderConfig(name = "test", model = "alias", type = ProviderType.OPENAI)
    private fun conversation() = Conversation(messages = (0..11).map {
        ChatMessage(role = if (it % 2 == 0) "user" else "assistant", content = "record-$it " + "content ".repeat(100))
    }.toMutableList())

    private fun factory(action: suspend (List<ChatMessage>, suspend (StreamEvent) -> Unit) -> Unit) =
        object : ProviderFactory() {
            override fun create(type: ProviderType) = object : ApiProvider {
                override suspend fun streamChat(config: ProviderConfig, system: String, messages: List<ChatMessage>,
                    tools: List<ToolSpec>, onEvent: suspend (StreamEvent) -> Unit) {
                    assertTrue(tools.isEmpty())
                    assertTrue(messages.all { it.attachments.isEmpty() })
                    action(messages, onEvent)
                }
            }
        }

    @Test fun `compaction preserves original history latest environment and append only continuation`() = runBlocking {
        val conv = conversation()
        conv.messages.add(0, ChatMessage(role = "user", content = "old mode", contextKind = "environment"))
        val latest = ChatMessage(role = "user", content = "current mode", contextKind = "environment")
        conv.messages.add(3, latest)
        val originals = conv.messages.toList()
        val compactor = ContextCompactor(FileStore(tmp.root), factory { _, emit -> emit(StreamEvent.Text("用户目标、已完成工作与下一步。")) })
        val result = compactor.compact(conv, config)
        assertNull(conv.contextCompaction)
        assertEquals(originals, conv.messages)
        conv.contextCompaction = result
        val replay = ContextWindows.replay(conv)
        assertEquals("compaction", replay.first().contextKind)
        assertTrue(replay.contains(latest))
        assertFalse(replay.contains(originals.first()))
        val changed = ChatMessage(role = "user", content = "new mode", contextKind = "environment")
        conv.messages += changed
        assertEquals(replay, ContextWindows.replay(conv).dropLast(1))
        assertEquals(changed, ContextWindows.replay(conv).last())
        val store = FileStore(tmp.root)
        store.saveConversation(conv)
        assertEquals(ContextWindows.replay(conv), ContextWindows.replay(store.loadConversation(conv.id)!!))
    }

    @Test fun `parallel tool calls and their media are never split`() {
        val call = ChatMessage(role = "assistant", toolCalls = listOf(ToolCallInfo("a", "read_file", "{}"), ToolCallInfo("b", "read_file", "{}")))
        val messages = listOf(ChatMessage(role = "user", content = "task"), call,
            ChatMessage(role = "tool", toolCallId = "a"), ChatMessage(role = "tool", toolCallId = "b"),
            ChatMessage(role = "user", originToolCallId = "a")) + List(5) { ChatMessage(role = "assistant", content = "tail") }
        assertEquals(1, ContextWindows.compressionBoundary(messages))
        val longer = messages + ChatMessage(role = "user", content = "next")
        assertEquals(5, ContextWindows.compressionBoundary(longer))
    }

    @Test fun `failed or cancelled summary leaves previous compaction unchanged`() = runBlocking {
        val conv = conversation()
        val previous = ContextCompaction("已有摘要", listOf(conv.messages.first().id), "earlier")
        conv.contextCompaction = previous
        val original = conv.messages.toList()
        for (cancel in listOf(false, true)) {
            val compactor = ContextCompactor(FileStore(tmp.root), factory { _, emit ->
                if (cancel) throw CancellationException("stop") else emit(StreamEvent.Error("upstream"))
            })
            try { compactor.compact(conv, config); fail("must fail") }
            catch (e: CancellationException) { assertTrue(cancel) }
            catch (e: IllegalStateException) { assertFalse(cancel) }
            assertEquals(previous, conv.contextCompaction)
            assertEquals(original, conv.messages)
        }
    }

    @Test fun `repeated compression carries summary and does not resurrect failed attachments`() = runBlocking {
        val conv = conversation()
        val excluded = ChatMessage(role = "user", content = "failed-secret", excludedFromContext = true,
            attachments = listOf(MessageAttachment(name = "bad", mimeType = "bad/type", sizeBytes = 1, workspacePath = "bad", delivery = "native")))
        conv.messages += excluded
        var request = ""
        val compactor = ContextCompactor(FileStore(tmp.root), factory { messages, emit ->
            request = messages.single().content
            emit(StreamEvent.Text("保留前轮重要目标和文件路径。"))
        })
        conv.contextCompaction = compactor.compact(conv, config)
        val firstCovered = conv.contextCompaction!!.coveredMessageIds
        conv.messages += List(8) { ChatMessage(role = "user", content = "more ".repeat(100)) }
        val second = compactor.compact(conv, config)
        assertTrue(request.contains("保留前轮重要目标"))
        assertFalse(request.contains("failed-secret"))
        assertTrue(second.coveredMessageIds.containsAll(firstCovered))
        conv.contextCompaction = second
        assertFalse(ContextWindows.replay(conv).contains(excluded))
    }

    @Test fun `overview separates upstream counts from estimate and uses configured model capacity`() {
        val conv = conversation()
        conv.lastContextUsage = ContextUsageRecord(TokenUsage(123, 45, cacheReadTokens = 100), "other-model")
        val overview = ContextWindows.overview(conv, config.copy(contextWindowOverrides = mapOf("alias" to 200000)), null)
        assertEquals(200000, overview.maxTokens)
        assertEquals(123L, overview.lastUsage!!.usage.inputTokens)
        assertTrue(overview.estimatedTokens > 123)
        assertNull(ContextWindows.overview(conv, config, null).maxTokens)
    }

    @Test fun `chunking preserves complete transcript without cutting surrogate pairs`() {
        val text = ("对话记录😀 " + "ASCII contents ".repeat(70)).repeat(50)
        val compactor = ContextCompactor(FileStore(tmp.root), factory { _, _ -> })
        val chunks = compactor.splitTranscript(text, 1024)
        assertTrue(chunks.size > 1)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { ContextWindows.estimate(it) <= 1024 })
        assertTrue(chunks.none { Character.isHighSurrogate(it.last()) })
    }
}
