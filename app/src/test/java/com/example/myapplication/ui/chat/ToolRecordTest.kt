package com.example.myapplication.ui.chat

import com.example.myapplication.data.model.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class ToolRecordTest {
    @Test fun exitMetadataMustBeAnExactRunnerPrefix() {
        assertEquals(CommandRecord("  hello\n", 0), commandRecord("退出码 0\n  hello\n"))
        assertEquals(CommandRecord("failed", 2), commandRecord("错误: 命令退出码 2\nfailed"))
        assertEquals(CommandRecord("output says 退出码 0", null), commandRecord("output says 退出码 0"))
        assertEquals(CommandRecord("", 0), commandRecord("退出码 0"))
    }

    @Test fun missingResultIsNotCompletedOrEmptyOutput() {
        assertEquals("命令尚未返回", commandCaption(null, false, false, false))
        assertEquals("等待命令批准", commandCaption(null, true, false, true))
        assertEquals("正在运行命令", commandCaption(null, true, false, false))
        assertEquals("运行了命令", commandCaption(ChatMessage(role = "tool", content = ""), false, false, false))
        assertEquals("命令已中止", commandCaption(ChatMessage(role = "tool", content = "执行已中断，结果需确认", isError = true), false, false, false))
        assertEquals("命令执行失败", commandCaption(ChatMessage(role = "tool", content = "错误: 命令退出码 2\n取消 is in stdout", isError = true), false, false, false))
        assertEquals("命令执行失败", commandCaption(ChatMessage(role = "tool", content = "失败", isError = true), false, false, false))
    }
}
