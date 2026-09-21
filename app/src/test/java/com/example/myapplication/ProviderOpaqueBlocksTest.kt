package com.example.myapplication

import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ProviderConfig
import com.example.myapplication.data.model.ProviderType
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.provider.AnthropicProvider
import com.example.myapplication.provider.AnthropicStreamParser
import com.example.myapplication.provider.GeminiProvider
import com.example.myapplication.provider.GeminiStreamParser
import com.example.myapplication.provider.StreamEvent
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOpaqueBlocksTest {
    private val client = OkHttpClient()

    @Test fun `unfinished signed thinking and malformed tool input are not replayed or executed`() {
        val parser = AnthropicStreamParser()
        parser.parse("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""")
        parser.parse("""{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"partial"}}""")
        parser.parse("""{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"bad","name":"write_file","input":{"path":"unexpected"}}}""")
        parser.parse("""{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{"}}""")
        val events = parser.finish()
        assertTrue(events.any { it is StreamEvent.Error })
        assertTrue(events.none { it is StreamEvent.ToolCall || it is StreamEvent.ProviderBlocks })
    }

    @Test fun `redacted thinking survives replay but does not cross protocols`() {
        val parser = AnthropicStreamParser()
        parser.parse("""{"type":"content_block_start","index":0,"content_block":{"type":"redacted_thinking","data":"opaque-redacted"}}""")
        val blocks = parser.parse("""{"type":"content_block_stop","index":0}""")
            .filterIsInstance<StreamEvent.ProviderBlocks>().single().blocks
        val message = ChatMessage(role = "assistant", content = "visible", providerBlocks = mapOf("anthropic" to blocks))
        assertEquals("opaque-redacted", blocks.single()["data"]!!.jsonPrimitive.content)
        val other = GeminiProvider(client).buildRequestBody(ProviderConfig(type = ProviderType.GEMINI, model = "gemini-2.5-flash"),
            "", listOf(message), emptyList()).toString()
        assertTrue(other.contains("visible"))
        assertTrue(!other.contains("opaque-redacted"))
    }

    @Test
    fun `Anthropic SSE thinking signature and tool block replay unchanged`() {
        val parser = AnthropicStreamParser()
        parser.parse("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""")
        parser.parse("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"reason"}}""")
        parser.parse("""{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"opaque-sign"}}""")
        parser.parse("""{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"ature"}}""")
        val thinkingBlocks = parser.parse("""{"type":"content_block_stop","index":0}""")
            .filterIsInstance<StreamEvent.ProviderBlocks>().single().blocks
        assertEquals("reason", thinkingBlocks.single()["thinking"]!!.jsonPrimitive.content)
        assertEquals("opaque-signature", thinkingBlocks.single()["signature"]!!.jsonPrimitive.content)

        parser.parse("""{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"read_file","input":{}}}""")
        parser.parse("""{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"path\":\"a.txt\"}"}}""")
        val events = parser.parse("""{"type":"content_block_stop","index":1}""")
        val blocks = events.filterIsInstance<StreamEvent.ProviderBlocks>().single().blocks
        assertEquals(2, blocks.size)
        assertEquals("tool_use", blocks[1]["type"]!!.jsonPrimitive.content)
        assertEquals("a.txt", blocks[1]["input"]!!.jsonObject["path"]!!.jsonPrimitive.content)

        val assistant = ChatMessage(
            role = "assistant",
            providerBlocks = mapOf("anthropic" to blocks),
            toolCalls = listOf(ToolCallInfo("toolu_1", "read_file", """{"path":"a.txt"}"""))
        )
        val wire = AnthropicProvider(client).buildRequestBody(
            ProviderConfig(type = ProviderType.ANTHROPIC, model = "claude-sonnet-4-6"),
            "", listOf(assistant, ChatMessage(role = "tool", toolCallId = "toolu_1", content = "ok")), emptyList()
        )
        val replayed = wire["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals(blocks, replayed.map { it.jsonObject })
        assertEquals("toolu_1", wire["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray[0]
            .jsonObject["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Gemini preserves function-call and signature-only parts in their original order`() {
        val parser = GeminiStreamParser()
        val first = parser.parse("""{"candidates":[{"content":{"parts":[{"functionCall":{"name":"read_file","args":{"path":"a.txt"}}}]}}]}""")
        assertEquals(1, first.filterIsInstance<StreamEvent.ToolCall>().size)
        val second = parser.parse("""{"candidates":[{"content":{"parts":[{"text":"","thoughtSignature":"opaque-gemini-signature"}]}}]}""")
        assertTrue(second.none { it is StreamEvent.ToolCall })
        val blocks = second.filterIsInstance<StreamEvent.ProviderBlocks>().single().blocks
        assertEquals(2, blocks.size)
        assertEquals("read_file", blocks[0]["functionCall"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("", blocks[1]["text"]!!.jsonPrimitive.content)
        assertEquals("opaque-gemini-signature", blocks[1]["thoughtSignature"]!!.jsonPrimitive.content)

        val assistant = ChatMessage(
            role = "assistant",
            providerBlocks = mapOf("gemini" to blocks),
            toolCalls = listOf(ToolCallInfo("gemini_0_read_file", "read_file", """{"path":"a.txt"}"""))
        )
        val wire = GeminiProvider(client).buildRequestBody(
            ProviderConfig(type = ProviderType.GEMINI, model = "gemini-3.8-flash"),
            "", listOf(assistant, ChatMessage(role = "tool", toolName = "read_file", content = "ok")), emptyList()
        )
        val replayed = wire["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals(blocks, replayed.map { it.jsonObject })
        assertEquals("read_file", wire["contents"]!!.jsonArray[1].jsonObject["parts"]!!.jsonArray[0]
            .jsonObject["functionResponse"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Gemini does not collapse two equal function calls`() {
        val parser = GeminiStreamParser()
        val events = parser.parse("""{"candidates":[{"content":{"parts":[
            {"functionCall":{"name":"read_file","args":{"path":"a.txt"}}},
            {"functionCall":{"name":"read_file","args":{"path":"a.txt"}}}
        ]}}]}""")
        assertEquals(2, events.filterIsInstance<StreamEvent.ToolCall>().size)
        assertEquals(
            2,
            events.filterIsInstance<StreamEvent.ProviderBlocks>().single().blocks.size
        )
    }

    @Test
    fun `Anthropic tool input supplied at block start is not replaced with empty object`() {
        val parser = AnthropicStreamParser()
        parser.parse("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"read_file","input":{"path":"a.txt"}}}""")
        val events = parser.parse("""{"type":"content_block_stop","index":0}""")
        assertEquals(
            "{\"path\":\"a.txt\"}",
            events.filterIsInstance<StreamEvent.ToolCall>().single().argumentsJson
        )
    }
}
