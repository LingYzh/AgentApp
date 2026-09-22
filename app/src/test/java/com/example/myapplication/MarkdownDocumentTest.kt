package com.example.myapplication

import com.example.myapplication.ui.components.MarkdownBlock
import com.example.myapplication.ui.components.MarkdownDocument
import com.example.myapplication.ui.components.SyntaxHighlighter
import com.example.myapplication.ui.components.SyntaxKind
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

    @Test fun `details supports nested blocks inline summary and open state`() {
        val parsed = MarkdownDocument.parse("""
            <details open><summary>**Build** `42`</summary>
            before
            <details><summary>Inner</summary>inside</details>
            </details>
        """.trimIndent()).single() as MarkdownBlock.Details
        assertTrue(parsed.open)
        assertTrue(parsed.summary.any { it.bold && it.text == "Build" })
        assertTrue(parsed.summary.any { it.code && it.text == "42" })
        val nested = parsed.blocks.filterIsInstance<MarkdownBlock.Details>().single()
        assertFalse(nested.open)
        assertEquals("inside", (nested.blocks.single() as MarkdownBlock.Paragraph).runs.single().text)
    }

    @Test fun `details parser ignores tags inside fenced code and recovers streaming tail`() {
        val code = MarkdownDocument.parse("```html\n<details><summary>not a node</summary></details>\n```").single()
        assertTrue(code is MarkdownBlock.Code)
        val inlineCode = MarkdownDocument.parse("`<details>not a node</details>`").single() as MarkdownBlock.Paragraph
        assertTrue(inlineCode.runs.single().code)
        val partial = MarkdownDocument.parse("<details><summary>Loading **now**</summary>body")
            .single() as MarkdownBlock.Details
        assertEquals("Loading now", partial.summary.joinToString("") { it.text })
        assertEquals("body", (partial.blocks.single() as MarkdownBlock.Paragraph).runs.single().text)
        val incompleteSummary = MarkdownDocument.parse("<details><summary>Still **loading**")
            .single() as MarkdownBlock.Details
        assertTrue(incompleteSummary.summary.any { it.bold && it.text == "loading" })
        assertTrue(incompleteSummary.blocks.isEmpty())
    }

    @Test fun `details scanner follows CommonMark fence indentation and length`() {
        val longerFence = MarkdownDocument.parse("````html\n```\n<details><summary>literal</summary></details>\n```\n````").single()
        assertTrue(longerFence is MarkdownBlock.Code)
        val indentedFence = MarkdownDocument.parse("   ```html\n<details><summary>literal</summary></details>\n   ```").single()
        assertTrue(indentedFence is MarkdownBlock.Code)
        val indentedCode = MarkdownDocument.parse("    <details><summary>literal</summary></details>").single()
        assertTrue(indentedCode is MarkdownBlock.Code)
    }

    @Test fun `deep details fall back before recursion can exhaust the stack`() {
        val source = buildString {
            repeat(40) { append("<details>") }
            append("body")
            repeat(40) { append("</details>") }
        }
        assertTrue(MarkdownDocument.parse(source).first() is MarkdownBlock.Details)
    }

    @Test fun `long single line without details remains a paragraph`() {
        val source = "ordinary text ".repeat(10_000) + "end"
        val parsed = MarkdownDocument.parse(source).single() as MarkdownBlock.Paragraph
        assertEquals(source, parsed.runs.joinToString("") { it.text })
    }

    @Test fun `ordinary html remains inert text`() {
        val parsed = MarkdownDocument.parse("<script>alert(1)</script>\n\n<span onclick=\"bad()\">text</span>")
        assertTrue(parsed.all { it is MarkdownBlock.Paragraph })
        assertTrue((parsed[0] as MarkdownBlock.Paragraph).runs.joinToString("") { it.text }.contains("<script>"))
    }

    @Test fun `syntax highlighter preserves text and has safe unknown fallback`() {
        val kotlin = "fun greet(name: String): Int = 42 // note"
        val tokens = SyntaxHighlighter.tokens("kotlin", kotlin)
        assertEquals(kotlin, tokens.joinToString("") { it.text })
        assertTrue(tokens.any { it.kind == SyntaxKind.KEYWORD })
        assertTrue(tokens.any { it.kind == SyntaxKind.TYPE })
        assertTrue(tokens.any { it.kind == SyntaxKind.FUNCTION })
        assertTrue(tokens.any { it.kind == SyntaxKind.NUMBER })
        assertTrue(tokens.any { it.kind == SyntaxKind.COMMENT })
        val long = "x".repeat(20_000)
        assertEquals(listOf(long), SyntaxHighlighter.tokens("brainfuck", long).map { it.text })
        val recognizedLong = "identifier ".repeat(2_000)
        assertEquals(recognizedLong, SyntaxHighlighter.tokens("kotlin", recognizedLong).joinToString("") { it.text })
    }

    @Test fun `common fenced languages keep their source while exposing presentation tokens`() {
        val samples = mapOf(
            "java" to "class App { String value = \"x\"; }",
            "js" to "const value = 1 // note",
            "ts" to "interface Value { id: number }",
            "python" to "def run(): # note",
            "shell" to "if true; then echo \"ok\"; fi",
            "json" to "{\"value\": 42, \"ready\": true}",
            "yaml" to "ready: true # note",
            "html" to "<!-- note --><details open>",
            "css" to "@media screen { color: #fff; }",
            "sql" to "SELECT * FROM item -- note"
        )
        samples.forEach { (language, source) ->
            val tokens = SyntaxHighlighter.tokens(language, source)
            assertEquals(source, tokens.joinToString("") { it.text })
            assertTrue("$language was not highlighted", tokens.any { it.kind != SyntaxKind.PLAIN })
        }
    }
}
