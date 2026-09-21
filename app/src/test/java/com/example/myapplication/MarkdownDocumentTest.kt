package com.example.myapplication

import com.example.myapplication.ui.components.MarkdownBlock
import com.example.myapplication.ui.components.MarkdownDocument
import org.junit.Assert.*
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun `streaming tail changes do not change preceding semantic blocks`() {
        val prefix = "## 完成\n\n第一段 **重点**。\n\n"
        val before = MarkdownDocument.parse(prefix + "正在输出")
        val after = MarkdownDocument.parse(prefix + "正在输出更多内容")
        assertEquals(before.dropLast(1), after.dropLast(1))
        assertTrue((before[1] as MarkdownBlock.Paragraph).runs.any { it.bold && it.text == "重点" })
    }

    @Test fun `unfinished code fence is already rendered as code`() {
        val before = MarkdownDocument.parse("```kotlin\nval x = 1")
        val after = MarkdownDocument.parse("```kotlin\nval x = 1\n```\n")
        assertEquals(before, after)
        assertEquals("kotlin", (before.single() as MarkdownBlock.Code).language)
    }

    @Test fun `tables preserve alignment inline style and escaped pipe`() {
        val parsed = MarkdownDocument.parse("| A | B |\n| :--- | ---: |\n| **value** | a\\|b |")
        val table = parsed.single() as MarkdownBlock.Table
        assertEquals(listOf("LEFT", "RIGHT"), table.alignments)
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[1][0].any { it.bold })
        assertEquals("a|b", table.rows[1][1].joinToString("") { it.text })
    }

    @Test fun `nested lists quotes references and task syntax survive parsing`() {
        val list = MarkdownDocument.parse("3. first\n   - [x] task\n4. next").single() as MarkdownBlock.Items
        assertEquals(3, list.start)
        assertTrue(list.items.first().last() is MarkdownBlock.Items)
        val quote = MarkdownDocument.parse("> [docs][link]\n\n[link]: https://example.com").single() as MarkdownBlock.Quote
        assertEquals("https://example.com", (quote.blocks.single() as MarkdownBlock.Paragraph).runs.single().link)
    }
}
