package com.example.myapplication

import com.example.myapplication.provider.AnthropicStreamParser
import com.example.myapplication.provider.GeminiStreamParser
import com.example.myapplication.provider.JsonPath
import com.example.myapplication.provider.OpenAiStreamParser
import com.example.myapplication.provider.ProviderJson
import com.example.myapplication.provider.StreamEvent
import com.example.myapplication.data.model.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderParsingTest {

    // ---------- OpenAI ----------

    @Test
    fun `openai text deltas`() {
        val p = OpenAiStreamParser()
        val e1 = p.parse("""{"choices":[{"delta":{"content":"你好"},"finish_reason":null}]}""")
        val e2 = p.parse("""{"choices":[{"delta":{"content":"世界"},"finish_reason":null}]}""")
        val done = p.parse("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""") + p.finish()
        assertEquals(listOf(StreamEvent.Text("你好")), e1)
        assertEquals(listOf(StreamEvent.Text("世界")), e2)
        assertEquals(listOf(StreamEvent.Done("stop")), done)
    }

    @Test
    fun `openai reasoning content maps to thinking`() {
        val p = OpenAiStreamParser()
        val events = p.parse("""{"choices":[{"delta":{"reasoning_content":"思考一下"},"finish_reason":null}]}""")
        assertEquals(listOf(StreamEvent.Thinking("思考一下")), events)
    }

    @Test
    fun `openai length finish reason remains distinguishable from success`() {
        val p = OpenAiStreamParser()
        p.parse("""{"choices":[{"delta":{},"finish_reason":"length"}]}""")
        assertEquals(StreamEvent.Done("length"), p.finish().single())
    }

    @Test
    fun `openai tool call fragments accumulate`() {
        val p = OpenAiStreamParser()
        p.parse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"write_file","arguments":"{\"path\":"}}]}}]}""")
        p.parse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"a.txt\",\"content\":\"hi\"}"}}]}}]}""")
        p.parse("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""")
        val events = p.finish()
        val toolCall = events.filterIsInstance<StreamEvent.ToolCall>().single()
        assertEquals("call_1", toolCall.id)
        assertEquals("write_file", toolCall.name)
        assertEquals("{\"path\":\"a.txt\",\"content\":\"hi\"}", toolCall.argumentsJson)
        assertTrue(events.last() is StreamEvent.Done)
    }

    @Test
    fun `openai garbage line yields no events`() {
        val p = OpenAiStreamParser()
        assertTrue(p.parse("not json at all").isEmpty())
    }

    @Test
    fun `openai usage chunk preserves reported zeros and optional breakdown`() {
        val events = OpenAiStreamParser().parse(
            """{"choices":[],"usage":{"prompt_tokens":0,"completion_tokens":12,"prompt_tokens_details":{"cached_tokens":0},"completion_tokens_details":{"reasoning_tokens":7}}}"""
        )
        assertEquals(
            listOf(StreamEvent.Usage(TokenUsage(0, 12, cacheReadTokens = 0, reasoningTokens = 7))),
            events
        )
    }

    @Test
    fun `openai ignores null optional objects arrays and primitives`() {
        val parser = OpenAiStreamParser()

        val usageOnly = parser.parse(
            """{"usage":{"prompt_tokens":4,"completion_tokens":0,"prompt_tokens_details":null,"completion_tokens_details":null},"choices":null}"""
        )
        val emptyDelta = parser.parse(
            """{"choices":[{"finish_reason":null,"delta":null}]}"""
        )
        val text = parser.parse(
            """{"usage":null,"choices":[{"finish_reason":null,"delta":{"content":"ok","reasoning_content":null,"tool_calls":null}}]}"""
        )
        val malformedToolEntry = parser.parse(
            """{"choices":[{"finish_reason":"stop","delta":{"tool_calls":[null,{"index":null,"id":null,"function":null}]}}]}"""
        )

        assertEquals(listOf(StreamEvent.Usage(TokenUsage(4, 0))), usageOnly)
        assertTrue(emptyDelta.isEmpty())
        assertEquals(listOf(StreamEvent.Text("ok")), text)
        assertTrue(malformedToolEntry.isEmpty())
        assertEquals(listOf(StreamEvent.Done("stop")), parser.finish())
    }

    @Test
    fun `openai null frame does not interrupt tool call fragments`() {
        val parser = OpenAiStreamParser()

        parser.parse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"write_","arguments":"{\"path\":"}}]}}]}"""
        )
        assertTrue(parser.parse("""{"usage":null,"choices":null}""").isEmpty())
        parser.parse(
            """{"choices":[{"finish_reason":"tool_calls","delta":{"tool_calls":[{"index":0,"function":{"name":"file","arguments":"\"a.txt\"}"}}]}}]}"""
        )

        val events = parser.finish()
        assertEquals(
            StreamEvent.ToolCall("call_1", "write_file", "{\"path\":\"a.txt\"}"),
            events.first()
        )
        assertEquals(StreamEvent.Done("tool_calls"), events.last())
    }

    // ---------- Anthropic ----------

    @Test
    fun `anthropic text and thinking deltas`() {
        val p = AnthropicStreamParser()
        p.parse("""{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
        val t = p.parse("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"答案"}}""")
        p.parse("""{"type":"content_block_stop","index":0}""")
        p.parse("""{"type":"content_block_start","index":1,"content_block":{"type":"thinking"}}""")
        val th = p.parse("""{"type":"content_block_delta","index":1,"delta":{"type":"thinking_delta","thinking":"推理中"}}""")
        assertEquals(listOf(StreamEvent.Text("答案")), t)
        assertEquals(listOf(StreamEvent.Thinking("推理中")), th)
    }

    @Test
    fun `anthropic tool use accumulates json`() {
        val p = AnthropicStreamParser()
        p.parse("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"read_file"}}""")
        p.parse("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"path\":"}}""")
        p.parse("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"\"a.txt\"}"}}""")
        val events = p.parse("""{"type":"content_block_stop","index":0}""")
        val call = events.filterIsInstance<StreamEvent.ToolCall>().single()
        assertEquals("toolu_1", call.id)
        assertEquals("read_file", call.name)
        assertEquals("{\"path\":\"a.txt\"}", call.argumentsJson)
    }

    @Test
    fun `anthropic api error surfaces`() {
        val p = AnthropicStreamParser()
        val events = p.parse("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        assertEquals(listOf(StreamEvent.Error("Overloaded")), events)
    }

    @Test
    fun `anthropic usage merges start input cache and final output`() {
        val p = AnthropicStreamParser()
        val start = p.parse(
            """{"type":"message_start","message":{"usage":{"input_tokens":100,"cache_read_input_tokens":20,"cache_creation_input_tokens":10,"output_tokens":0}}}"""
        )
        val end = p.parse(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"cache_read_input_tokens":25,"output_tokens":45,"output_tokens_details":{"thinking_tokens":11}}}"""
        )
        assertEquals(
            StreamEvent.Usage(TokenUsage(130, 0, cacheReadTokens = 20, cacheWriteTokens = 10)),
            start.single()
        )
        assertEquals(
            StreamEvent.Usage(TokenUsage(135, 45, cacheReadTokens = 25, cacheWriteTokens = 10, reasoningTokens = 11)),
            end.single()
        )
    }

    // ---------- Gemini ----------

    @Test
    fun `gemini text and thought parts`() {
        val p = GeminiStreamParser()
        val events = p.parse(
            """{"candidates":[{"content":{"parts":[{"text":"可见文本"},{"text":"内心活动","thought":true}],"role":"model"}}]}"""
        )
        assertEquals(
            listOf(StreamEvent.Text("可见文本"), StreamEvent.Thinking("内心活动")),
            events.filterNot { it is StreamEvent.ProviderBlocks }
        )
    }

    @Test
    fun `gemini function call`() {
        val p = GeminiStreamParser()
        val events = p.parse(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"list_files","args":{"path":""}}}],"role":"model"},"finishReason":"STOP"}]}"""
        )
        val call = events.filterIsInstance<StreamEvent.ToolCall>().single()
        assertEquals("list_files", call.name)
        assertEquals("{\"path\":\"\"}", call.argumentsJson)
        assertEquals(StreamEvent.Done("STOP"), p.finish().filterIsInstance<StreamEvent.Done>().single())
    }

    @Test
    fun `gemini usage metadata maps cache and thought tokens`() {
        val events = GeminiStreamParser().parse(
            """{"usageMetadata":{"promptTokenCount":30,"candidatesTokenCount":0,"cachedContentTokenCount":8,"thoughtsTokenCount":5}}"""
        )
        assertEquals(
            listOf(StreamEvent.Usage(TokenUsage(30, 5, cacheReadTokens = 8, reasoningTokens = 5))),
            events
        )
    }

    // ---------- JsonPath ----------

    @Test
    fun `json path extracts nested value`() {
        val el = ProviderJson.parseToJsonElement(
            """{"choices":[{"delta":{"content":"hi"},"message":{"content":"full"}}],"usage":{"tokens":3}}"""
        )
        assertEquals("hi", JsonPath.extract(el, "$.choices[0].delta.content"))
        assertEquals("full", JsonPath.extract(el, "$.choices[0].message.content"))
        assertEquals("3", JsonPath.extract(el, "$.usage.tokens"))
        assertEquals(null, JsonPath.extract(el, "$.choices[5].delta.content"))
        assertEquals(null, JsonPath.extract(el, "$.nope"))
    }
}
