package com.example.myapplication.ui.components

import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.*
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension

/** Immutable semantic blocks let Compose retain completed paragraphs while a tail grows. */
internal data class MarkdownRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null
)

internal sealed interface MarkdownBlock {
    data class Paragraph(val runs: List<MarkdownRun>) : MarkdownBlock
    data class Heading(val level: Int, val runs: List<MarkdownRun>) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock
    data class Items(val start: Int?, val items: List<List<MarkdownBlock>>) : MarkdownBlock
    data class Table(val rows: List<List<List<MarkdownRun>>>, val alignments: List<String>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal object MarkdownDocument {
    // Parser instances are local: multiple conversation bubbles may parse on different workers.
    fun parse(text: String): List<MarkdownBlock> {
        val parser = Parser.builder().extensions(listOf(TablesExtension.create(), StrikethroughExtension.create())).build()
        return blocks(parser.parse(text))
    }

    private fun children(node: Node): List<Node> = buildList {
        var child = node.firstChild
        while (child != null) { add(child); child = child.next }
    }

    private fun blocks(parent: Node): List<MarkdownBlock> = children(parent).mapNotNull { node ->
        when (node) {
            is Paragraph -> MarkdownBlock.Paragraph(inlines(node))
            is Heading -> MarkdownBlock.Heading(node.level, inlines(node))
            is FencedCodeBlock -> MarkdownBlock.Code(node.info.orEmpty().trim().substringBefore(' '), node.literal.removeSuffix("\n"))
            is IndentedCodeBlock -> MarkdownBlock.Code("", node.literal.removeSuffix("\n"))
            is BlockQuote -> MarkdownBlock.Quote(blocks(node))
            is BulletList -> MarkdownBlock.Items(null, children(node).map(::blocks))
            is OrderedList -> MarkdownBlock.Items(node.startNumber, children(node).map(::blocks))
            is ThematicBreak -> MarkdownBlock.Rule
            is TableBlock -> {
                val rows = children(node).flatMap(::children)
                val cells = rows.map { row -> children(row).map(::inlines) }
                val alignments = rows.firstOrNull()?.let(::children).orEmpty().map {
                    (it as? TableCell)?.alignment?.name.orEmpty()
                }
                MarkdownBlock.Table(cells, alignments)
            }
            is HtmlBlock -> MarkdownBlock.Paragraph(listOf(MarkdownRun(node.literal.trimEnd())))
            else -> null
        }
    }

    private fun inlines(parent: Node, style: MarkdownRun = MarkdownRun("")): List<MarkdownRun> =
        children(parent).flatMap { node ->
            when (node) {
                is Text -> listOf(style.copy(text = node.literal))
                is Code -> listOf(style.copy(text = node.literal, code = true))
                is SoftLineBreak -> listOf(style.copy(text = "\n"))
                is HardLineBreak -> listOf(style.copy(text = "\n"))
                is StrongEmphasis -> inlines(node, style.copy(bold = true))
                is Emphasis -> inlines(node, style.copy(italic = true))
                is Strikethrough -> inlines(node, style.copy(strike = true))
                is Link -> inlines(node, style.copy(link = node.destination))
                is Image -> inlines(node, style.copy(link = node.destination))
                is HtmlInline -> listOf(style.copy(text = if (node.literal.matches(Regex("(?i)<br\\s*/?>"))) "\n" else node.literal))
                else -> inlines(node, style)
            }
        }
}
