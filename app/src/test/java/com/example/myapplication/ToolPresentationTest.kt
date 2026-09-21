package com.example.myapplication

import com.example.myapplication.agent.Tools
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ToolCallInfo
import com.example.myapplication.ui.chat.presentTool
import org.junit.Assert.*
import org.junit.Test

class ToolPresentationTest {
    @Test fun `directory listings keep readable directory names`() {
        val presentation = presentTool(ToolCallInfo("list", Tools.LIST_FILES, "{}"),
            ChatMessage(role = "tool", content = "/storage/emulated/0/Download/\n/storage/emulated/0/Documents/"))
        assertTrue(presentation.summary!!.contains("Download/"))
        assertTrue(presentation.summary!!.contains("Documents/"))
        assertTrue(presentation.summary!!.contains("2"))
    }

    @Test fun `plan feedback is not presented as acceptance`() {
        val presentation = presentTool(ToolCallInfo("plan", Tools.EXIT_PLAN_MODE, "{}"),
            ChatMessage(role = "tool", content = "计划未接受。用户反馈：请保留原文件"))
        assertEquals("提交计划", presentation.title)
        assertTrue(presentation.summary!!.contains("未接受"))
        assertFalse(presentation.summary!!.contains("已结束"))
    }

    @Test fun `raw json is not the default summary`() {
        val presentation = presentTool(ToolCallInfo("read", Tools.READ_FILE, """{"path":"data.json"}"""),
            ChatMessage(role = "tool", content = """{"secret":"raw"}"""))
        assertEquals("data.json", presentation.path)
        assertFalse(presentation.summary!!.contains("secret"))
    }
}
