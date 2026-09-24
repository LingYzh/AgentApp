package com.example.myapplication

import com.example.myapplication.ui.components.MarkdownBlock
import com.example.myapplication.ui.components.MarkdownDocument
import com.example.myapplication.ui.components.SyntaxHighlighter
import com.example.myapplication.ui.components.SyntaxKind
import com.example.myapplication.ui.components.remoteMarkdownImageUrl
import org.junit.Assert.*
import org.junit.Test

class MarkdownDocumentTest {
    @Test fun `chat punctuation does not lower or raise a whole sentence`() {
        listOf("喵~主人下午好呀！Nya~❤", "你好~世界~", "开心^微笑^").forEach { source ->
            val paragraph = MarkdownDocument.parse(source).single() as MarkdownBlock.Paragraph
            assertEquals(source, paragraph.runs.joinToString("") { it.text })
            assertTrue(paragraph.runs.none { it.subscript || it.superscript })
        }
        val explicit = MarkdownDocument.parse("<sub>下标文字</sub> <sup>上标文字</sup> H~2~O x^2^")
            .single() as MarkdownBlock.Paragraph
        assertTrue(explicit.runs.any { it.subscript && it.text == "下标文字" })
        assertTrue(explicit.runs.any { it.superscript && it.text == "上标文字" })
        assertEquals(2, explicit.runs.count { it.text == "2" && (it.subscript || it.superscript) })
    }

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

    @Test fun `defined footnotes work across details and unknown references stay literal`() {
        val source = """
            Outside[^1] and [参考^1], unknown[^missing].

            <details><summary>Inside[^1]</summary>Body [^1]</details>

            [^1]: Footnote **content** with [link](https://example.com).
        """.trimIndent()
        val blocks = MarkdownDocument.parse(source)
        val outside = blocks.first() as MarkdownBlock.Paragraph
        assertEquals(2, outside.runs.count { it.footnoteId == "1" })
        assertTrue(outside.runs.any { it.text.contains("[^missing]") })
        assertTrue(outside.runs.first { it.footnoteId == "1" }.footnoteBody!!.contains("**content**"))
        val details = blocks.filterIsInstance<MarkdownBlock.Details>().single()
        assertEquals("1", details.summary.first { it.footnoteId != null }.footnoteId)
        assertEquals("1", (details.blocks.single() as MarkdownBlock.Paragraph).runs.first { it.footnoteId != null }.footnoteId)
        assertEquals(2, blocks.size)
    }

    @Test fun `extensions leave escaped and code literals alone`() {
        val source = """
            `[^1] ==raw== ${'$'}x${'$'}` \[^1] \==literal== ==marked== H~2~O x^2^ ${'$'}x + y${'$'}

            ```text
            [^1] ==raw==
            ```

            [^1]: Defined note.
        """.trimIndent()
        val blocks = MarkdownDocument.parse(source)
        val paragraph = blocks.first() as MarkdownBlock.Paragraph
        assertTrue(paragraph.runs.any { it.code && it.text.contains("[^1]") })
        assertTrue(paragraph.runs.any { it.text.contains("[^1]") && it.footnoteId == null })
        assertTrue(paragraph.runs.any { it.highlight && it.text == "marked" })
        assertTrue(paragraph.runs.any { it.subscript && it.text == "2" })
        assertTrue(paragraph.runs.any { it.superscript && it.text == "2" })
        assertTrue(paragraph.runs.any { it.math && it.text == "x + y" })
        assertTrue(blocks[1] is MarkdownBlock.Code)
    }

    @Test fun `images and bare links have distinct semantics`() {
        val blocks = MarkdownDocument.parse("![chart alt](https://example.com/chart.png) and www.example.com or mail@example.com and https://example.com/a(b).")
        val runs = (blocks.single() as MarkdownBlock.Paragraph).runs
        assertEquals("https://example.com/chart.png", runs.first().imageUrl)
        assertEquals("chart alt", runs.first().text)
        assertTrue(runs.any { it.link == "https://www.example.com" })
        assertTrue(runs.any { it.link == "mailto:mail@example.com" })
        assertTrue(runs.any { it.link == "https://example.com/a(b)" })
    }

    @Test fun `images only load remote http urls`() {
        assertEquals("https://example.com/a.png", remoteMarkdownImageUrl("https://example.com/a.png"))
        assertEquals("http://example.com/a.png", remoteMarkdownImageUrl("http://example.com/a.png"))
        listOf("file:///data/data/private.png", "content://example/image", "../local.png", "https:///missing-host.png", "javascript:alert(1)")
            .forEach { assertNull(it, remoteMarkdownImageUrl(it)) }
    }

    @Test fun `block math and mermaid fences keep their source`() {
        val blocks = MarkdownDocument.parse("""
            ${'$'}${'$'}
            x^2 + y^2
            ${'$'}${'$'}

            \[
            a + b
            \]

            ```mermaid
            graph TD
                A --> B
            ```
        """.trimIndent())
        assertEquals("x^2 + y^2", (blocks[0] as MarkdownBlock.Math).text)
        assertEquals("a + b", (blocks[1] as MarkdownBlock.Math).text)
        assertEquals("mermaid", (blocks[2] as MarkdownBlock.Code).language)
    }

    @Test fun `custom syntax does not rewrite reference destinations or nested code`() {
        val source = """
            [API][id] and [click](https://example.com/a^b^?q==raw==)

            [id]: https://example.com/a^b^ "Title"

            > ```text
            > [^1] ==literal== ${'$'}x${'$'}
            > ```

            - ```text
              [^1] ==literal==
              ```

            [^1]: Real definition.
        """.trimIndent()
        val blocks = MarkdownDocument.parse(source)
        val paragraph = blocks.first() as MarkdownBlock.Paragraph
        assertEquals("https://example.com/a^b^", paragraph.runs.first { it.text == "API" }.link)
        assertEquals("https://example.com/a^b^?q==raw==", paragraph.runs.first { it.text == "click" }.link)
        val quote = blocks.filterIsInstance<MarkdownBlock.Quote>().single()
        assertTrue(quote.blocks.single() is MarkdownBlock.Code)
        assertTrue(blocks.filterIsInstance<MarkdownBlock.Items>().single().items.single().single() is MarkdownBlock.Code)
    }

    @Test fun `unknown footnotes currency and private use text remain unchanged`() {
        val privateUse = "\uE0000\uE001"
        val source = "Unknown[^404], $privateUse, ${'$'}10 and ${'$'}20"
        val runs = (MarkdownDocument.parse(source).single() as MarkdownBlock.Paragraph).runs
        assertEquals(source, runs.joinToString("") { it.text })
        assertTrue(runs.none { it.footnoteId != null || it.math })
    }

    @Test fun `safe simple html marks and math fences are semantic`() {
        val source = "<sup>2</sup> <sub>3</sub> <mark>yellow</mark> <kbd>Ctrl</kbd>"
        val runs = (MarkdownDocument.parse(source).single() as MarkdownBlock.Paragraph).runs
        assertTrue(runs.any { it.text == "2" && it.superscript })
        assertTrue(runs.any { it.text == "3" && it.subscript })
        assertTrue(runs.any { it.text == "yellow" && it.highlight })
        assertTrue(runs.any { it.text == "Ctrl" && it.code })
        listOf("math", "latex", "tex").forEach { language ->
            assertEquals("a+b", (MarkdownDocument.parse("```$language\na+b\n```").single() as MarkdownBlock.Math).text)
        }
    }

    @Test fun `multparagraph footnote body survives and unused definitions stay visible`() {
        val source = """
            Referenced[^used].

            [^used]: First paragraph.

                Second paragraph with **emphasis**.

            [^unused]: Important extra note.
        """.trimIndent()
        val blocks = MarkdownDocument.parse(source)
        val run = (blocks.first() as MarkdownBlock.Paragraph).runs.first { it.footnoteId == "used" }
        assertEquals("First paragraph.\n\nSecond paragraph with **emphasis**.", run.footnoteBody)
        val unused = blocks.last() as MarkdownBlock.Footnotes
        assertEquals(listOf("unused"), unused.entries.map { it.first })
        assertEquals("Important extra note.", unused.entries.single().second)
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
