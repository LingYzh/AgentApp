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
    data class Details(val summary: List<MarkdownRun>, val blocks: List<MarkdownBlock>, val open: Boolean) : MarkdownBlock
    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock
    data class Items(val start: Int?, val items: List<List<MarkdownBlock>>) : MarkdownBlock
    data class Table(val rows: List<List<List<MarkdownRun>>>, val alignments: List<String>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal object MarkdownDocument {
    private const val MAX_DETAILS_DEPTH = 24

    // Parser instances are local: multiple conversation bubbles may parse on different workers.
    fun parse(text: String): List<MarkdownBlock> {
        return parseDetails(text, depth = 0)
    }

    internal fun parseInlines(text: String): List<MarkdownRun> =
        parser().parse(text).firstChild?.let(::inlines) ?: emptyList()

    private fun parser(): Parser =
        Parser.builder().extensions(listOf(TablesExtension.create(), StrikethroughExtension.create())).build()

    private fun parseCommonMark(text: String): List<MarkdownBlock> = blocks(parser().parse(text))

    /** Recognizes only inert details/summary tags outside fenced code; every other HTML node stays literal. */
    private fun parseDetails(text: String, depth: Int): List<MarkdownBlock> {
        if (depth >= MAX_DETAILS_DEPTH) return parseCommonMark(text)
        val blocks = mutableListOf<MarkdownBlock>()
        var plainStart = 0
        var cursor = 0
        var fence: CodeFence? = null
        var inlineCodeTicks = 0
        while (cursor < text.length) {
            if (fence != null) {
                val closingEnd = closingFenceEndAt(text, cursor, fence)
                if (closingEnd != null) {
                    fence = null
                    cursor = closingEnd
                } else {
                    cursor++
                }
                continue
            }
            val openingFence = openingFenceAt(text, cursor)
            if (openingFence != null) {
                fence = openingFence.fence
                cursor = openingFence.end
                continue
            }
            val ticks = backtickRunAt(text, cursor)
            if (ticks != 0) {
                inlineCodeTicks = if (inlineCodeTicks == 0) ticks else if (inlineCodeTicks == ticks) 0 else inlineCodeTicks
                cursor += ticks
                continue
            }
            if (inlineCodeTicks == 0 && text.regionMatches(cursor, "<details", 0, 8, ignoreCase = true) && isTagBoundary(text, cursor + 8) && !isIndentedCodeLine(text, cursor)) {
                val detail = readDetails(text, cursor, depth)
                if (detail != null) {
                    if (plainStart < cursor) blocks += parseCommonMark(text.substring(plainStart, cursor))
                    blocks += detail.block
                    cursor = detail.end
                    plainStart = cursor
                    continue
                }
            }
            cursor++
        }
        if (plainStart < text.length) blocks += parseCommonMark(text.substring(plainStart))
        return blocks
    }

    private data class ParsedDetails(val block: MarkdownBlock.Details, val end: Int)

    private fun readDetails(source: String, start: Int, depthLimit: Int): ParsedDetails? {
        val openingEnd = source.indexOf('>', start).takeIf { it >= 0 } ?: return null
        val opening = source.substring(start, openingEnd + 1)
        val open = Regex("(?i)\\bopen(?:\\s|=|>)").containsMatchIn(opening)
        var cursor = openingEnd + 1
        var depth = 1
        var fence: CodeFence? = null
        var inlineCodeTicks = 0
        while (cursor < source.length) {
            if (fence != null) {
                val closingEnd = closingFenceEndAt(source, cursor, fence)
                if (closingEnd != null) {
                    fence = null
                    cursor = closingEnd
                } else {
                    cursor++
                }
                continue
            }
            val openingFence = openingFenceAt(source, cursor)
            if (openingFence != null) {
                fence = openingFence.fence
                cursor = openingFence.end
                continue
            }
            val ticks = backtickRunAt(source, cursor)
            if (ticks != 0) {
                inlineCodeTicks = if (inlineCodeTicks == 0) ticks else if (inlineCodeTicks == ticks) 0 else inlineCodeTicks
                cursor += ticks
                continue
            }
            if (inlineCodeTicks == 0 && source.regionMatches(cursor, "<details", 0, 8, ignoreCase = true) && isTagBoundary(source, cursor + 8) && !isIndentedCodeLine(source, cursor)) {
                val nestedEnd = source.indexOf('>', cursor)
                if (nestedEnd >= 0) {
                    cursor = nestedEnd + 1
                    depth++
                    continue
                }
            }
            if (inlineCodeTicks == 0 && source.regionMatches(cursor, "</details", 0, 9, ignoreCase = true) && isTagBoundary(source, cursor + 9) && !isIndentedCodeLine(source, cursor)) {
                val closingEnd = source.indexOf('>', cursor).takeIf { it >= 0 } ?: source.length - 1
                depth--
                if (depth == 0) return detailsBlock(source.substring(openingEnd + 1, cursor), open, closingEnd + 1, depthLimit)
                cursor = closingEnd + 1
                continue
            }
            cursor++
        }
        // Streaming often leaves the closing tag in the next sample. Render the available body now.
        return detailsBlock(source.substring(openingEnd + 1), open, source.length, depthLimit)
    }

    private fun detailsBlock(body: String, open: Boolean, end: Int, depth: Int): ParsedDetails {
        val closedSummary = Regex("(?is)^\\s*<summary(?:\\s[^>]*)?>(.*?)</summary\\s*>").find(body)
        val openingSummary = Regex("(?is)^\\s*<summary(?:\\s[^>]*)?>").find(body)
        // A streamed summary may not have its closing tag yet. Keep its inline content useful
        // until the next sample completes the element instead of rendering a literal tag.
        val summaryText = when {
            closedSummary != null -> closedSummary.groupValues[1].trim()
            openingSummary != null -> body.substring(openingSummary.range.last + 1).trim()
            else -> ""
        }.ifBlank { "详细信息" }
        val content = when {
            closedSummary != null -> body.substring(closedSummary.range.last + 1)
            openingSummary != null -> ""
            else -> body
        }
        return ParsedDetails(MarkdownBlock.Details(parseInlines(summaryText), parseDetails(content, depth + 1), open), end)
    }

    private data class CodeFence(val marker: Char, val length: Int)

    private data class FenceStart(val fence: CodeFence, val end: Int)

    private fun openingFenceAt(source: String, offset: Int): FenceStart? {
        val marker = source.getOrNull(offset) ?: return null
        if (marker !in charArrayOf('`', '~') || !isFenceIndent(source, offset)) return null
        var markerEnd = offset
        while (source.getOrNull(markerEnd) == marker) markerEnd++
        if (markerEnd - offset < 3) return null
        val lineEnd = source.indexOf('\n', markerEnd).let { if (it < 0) source.length else it }
        return FenceStart(CodeFence(marker, markerEnd - offset), if (lineEnd == source.length) lineEnd else lineEnd + 1)
    }

    private fun closingFenceEndAt(source: String, offset: Int, opening: CodeFence): Int? {
        val candidate = openingFenceAt(source, offset) ?: return null
        if (candidate.fence.marker != opening.marker || candidate.fence.length < opening.length) return null
        val lineEnd = candidate.end - if (candidate.end > 0 && source[candidate.end - 1] == '\n') 1 else 0
        if ((offset + candidate.fence.length until lineEnd).any { source[it] != ' ' && source[it] != '\t' }) return null
        return candidate.end
    }

    private fun isFenceIndent(source: String, offset: Int): Boolean {
        val lineStart = source.lastIndexOf('\n', offset - 1) + 1
        val indent = offset - lineStart
        return indent in 0..3 && (lineStart until offset).all { source[it] == ' ' }
    }

    private fun isIndentedCodeLine(source: String, offset: Int): Boolean {
        val lineStart = source.lastIndexOf('\n', offset - 1) + 1
        var column = 0
        var cursor = lineStart
        while (cursor < offset) {
            when (source[cursor]) {
                ' ' -> column++
                '\t' -> column = ((column / 4) + 1) * 4
                else -> return false
            }
            cursor++
        }
        return column >= 4
    }

    private fun backtickRunAt(source: String, offset: Int): Int {
        if (source.getOrNull(offset) != '`') return 0
        var end = offset
        while (source.getOrNull(end) == '`') end++
        return end - offset
    }

    private fun isTagBoundary(source: String, offset: Int): Boolean = source.getOrNull(offset)?.let { it.isWhitespace() || it == '>' } != false

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
