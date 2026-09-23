package com.example.myapplication.ui.components

import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.*
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import java.util.UUID
import java.net.URI

/** Markdown has no trusted local base directory; image loading is limited to remote HTTP URLs. */
internal fun remoteMarkdownImageUrl(destination: String?): String? {
    val uri = destination?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    return destination.takeIf { uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() }
}

/** Immutable semantic blocks let Compose retain completed paragraphs while a tail grows. */
internal data class MarkdownRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
    val highlight: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val math: Boolean = false,
    val imageUrl: String? = null,
    val footnoteId: String? = null,
    val footnoteBody: String? = null
)

internal sealed interface MarkdownBlock {
    data class Paragraph(val runs: List<MarkdownRun>) : MarkdownBlock
    data class Heading(val level: Int, val runs: List<MarkdownRun>) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data class Math(val text: String) : MarkdownBlock
    data class Details(val summary: List<MarkdownRun>, val blocks: List<MarkdownBlock>, val open: Boolean) : MarkdownBlock
    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock
    data class Items(val start: Int?, val items: List<List<MarkdownBlock>>) : MarkdownBlock
    data class Table(val rows: List<List<List<MarkdownRun>>>, val alignments: List<String>) : MarkdownBlock
    data class Footnotes(val entries: List<Pair<String, String>>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal object MarkdownDocument {
    private const val MAX_DETAILS_DEPTH = 24
    private val footnoteDefinition = Regex("^ {0,3}\\[\\^([^]\\n]+)]:[ \\t]*(.*)$")
    private val linkDefinition = Regex("^ {0,3}\\[[^]\\n]+]:[ \\t]*")
    private val bareUrl = Regex("(?i)(?:https?://|www\\.)[^\\s<>]+|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")

    private data class ParseContext(val footnotes: Map<String, String>) {
        val inlineTokens = mutableListOf<MarkdownRun>()
        val tokenPrefix = "\uE000MDX${UUID.randomUUID().toString().replace("-", "")}:"
        val referencedIds = mutableSetOf<String>()

        fun token(run: MarkdownRun): String {
            inlineTokens += run
            return "$tokenPrefix${inlineTokens.lastIndex}\uE001"
        }
    }

    // Parser instances are local: multiple conversation bubbles may parse on different workers.
    fun parse(text: String): List<MarkdownBlock> {
        val (source, definitions) = extractFootnotes(text)
        val context = ParseContext(definitions)
        val blocks = parseDetails(source, depth = 0, context = context)
        val unused = definitions.filterKeys { it !in context.referencedIds }.toList()
        return if (unused.isEmpty()) blocks else blocks + MarkdownBlock.Footnotes(unused)
    }

    internal fun parseInlines(text: String): List<MarkdownRun> =
        ParseContext(emptyMap()).let { context ->
            parser().parse(markInlines(text, context)).firstChild?.let { inlines(it, context) } ?: emptyList()
        }

    internal fun parseFootnoteBody(text: String): List<MarkdownBlock> =
        parseDetails(text, depth = 0, context = ParseContext(emptyMap()))

    private fun parser(): Parser =
        Parser.builder().extensions(listOf(TablesExtension.create(), StrikethroughExtension.create())).build()

    private fun parseCommonMark(text: String, context: ParseContext): List<MarkdownBlock> =
        parseMathBlocks(text, context)

    /** Recognizes only inert details/summary tags outside fenced code; every other HTML node stays literal. */
    private fun parseDetails(text: String, depth: Int, context: ParseContext): List<MarkdownBlock> {
        if (depth >= MAX_DETAILS_DEPTH) return parseCommonMark(text, context)
        val blocks = mutableListOf<MarkdownBlock>()
        var plainStart = 0
        var cursor = 0
        var fence: CodeFence? = null
        var inlineCodeTicks = 0
        while (cursor < text.length) {
            if (cursor == 0 || text[cursor - 1] == '\n') {
                val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it + 1 }
                val line = text.substring(cursor, lineEnd).trimEnd('\n', '\r')
                val candidate = logicalFence(line)
                if (fence != null) {
                    if (candidate != null && candidate.marker == fence.marker && candidate.length >= fence.length && logicalFenceTail(line, fence.marker).isBlank()) fence = null
                    cursor = lineEnd
                    continue
                }
                if (candidate != null) {
                    fence = candidate
                    cursor = lineEnd
                    continue
                }
            }
            if (fence != null) {
                cursor++
                continue
            }
            val ticks = backtickRunAt(text, cursor)
            if (ticks != 0) {
                inlineCodeTicks = if (inlineCodeTicks == 0) ticks else if (inlineCodeTicks == ticks) 0 else inlineCodeTicks
                cursor += ticks
                continue
            }
            if (inlineCodeTicks == 0 && text.regionMatches(cursor, "<details", 0, 8, ignoreCase = true) && isTagBoundary(text, cursor + 8) && !isIndentedCodeLine(text, cursor)) {
                val detail = readDetails(text, cursor, depth, context)
                if (detail != null) {
                    if (plainStart < cursor) blocks += parseCommonMark(text.substring(plainStart, cursor), context)
                    blocks += detail.block
                    cursor = detail.end
                    plainStart = cursor
                    continue
                }
            }
            cursor++
        }
        if (plainStart < text.length) blocks += parseCommonMark(text.substring(plainStart), context)
        return blocks
    }

    private data class ParsedDetails(val block: MarkdownBlock.Details, val end: Int)

    private fun readDetails(source: String, start: Int, depthLimit: Int, context: ParseContext): ParsedDetails? {
        val openingEnd = source.indexOf('>', start).takeIf { it >= 0 } ?: return null
        val opening = source.substring(start, openingEnd + 1)
        val open = Regex("(?i)\\bopen(?:\\s|=|>)").containsMatchIn(opening)
        var cursor = openingEnd + 1
        var depth = 1
        var fence: CodeFence? = null
        var inlineCodeTicks = 0
        while (cursor < source.length) {
            if (cursor == 0 || source[cursor - 1] == '\n') {
                val lineEnd = source.indexOf('\n', cursor).let { if (it < 0) source.length else it + 1 }
                val line = source.substring(cursor, lineEnd).trimEnd('\n', '\r')
                val candidate = logicalFence(line)
                if (fence != null) {
                    if (candidate != null && candidate.marker == fence.marker && candidate.length >= fence.length && logicalFenceTail(line, fence.marker).isBlank()) fence = null
                    cursor = lineEnd
                    continue
                }
                if (candidate != null) {
                    fence = candidate
                    cursor = lineEnd
                    continue
                }
            }
            if (fence != null) {
                cursor++
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
                if (depth == 0) return detailsBlock(source.substring(openingEnd + 1, cursor), open, closingEnd + 1, depthLimit, context)
                cursor = closingEnd + 1
                continue
            }
            cursor++
        }
        // Streaming often leaves the closing tag in the next sample. Render the available body now.
        return detailsBlock(source.substring(openingEnd + 1), open, source.length, depthLimit, context)
    }

    private fun detailsBlock(body: String, open: Boolean, end: Int, depth: Int, context: ParseContext): ParsedDetails {
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
        val summary = parser().parse(markInlines(summaryText, context)).firstChild?.let { inlines(it, context) }.orEmpty()
        return ParsedDetails(MarkdownBlock.Details(summary, parseDetails(content, depth + 1, context), open), end)
    }

    /** Footnote definitions are document scoped, including definitions inside a details body. */
    private fun extractFootnotes(source: String): Pair<String, Map<String, String>> {
        val lines = source.split('\n')
        val output = StringBuilder(source.length)
        val definitions = linkedMapOf<String, String>()
        var fence: CodeFence? = null
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val fenceAt = logicalFence(line)
            if (fence != null) {
                if (fenceAt != null && fenceAt.marker == fence.marker && fenceAt.length >= fence.length && logicalFenceTail(line, fence.marker).isBlank()) fence = null
            } else if (fenceAt != null) {
                fence = fenceAt
            } else {
                val definition = footnoteDefinition.matchEntire(line.trimEnd('\r'))
                if (definition != null) {
                    val id = definition.groupValues[1].trim().lowercase()
                    val body = mutableListOf(definition.groupValues[2])
                    var next = index + 1
                    while (next < lines.size) {
                        if (lines[next].startsWith("    ") || lines[next].startsWith('\t')) {
                            body += lines[next].removePrefix("    ").removePrefix("\t")
                            next++
                        } else if (lines[next].isBlank() && next + 1 < lines.size &&
                            (lines[next + 1].startsWith("    ") || lines[next + 1].startsWith('\t'))
                        ) {
                            body += ""
                            next++
                        } else break
                    }
                    definitions.putIfAbsent(id, body.joinToString("\n").trimEnd())
                    repeat(next - index) { output.append('\n') }
                    index = next
                    continue
                }
            }
            output.append(line)
            if (index < lines.lastIndex) output.append('\n')
            index++
        }
        return output.toString() to definitions
    }

    private fun parseMathBlocks(source: String, context: ParseContext): List<MarkdownBlock> {
        val lines = source.split('\n')
        val result = mutableListOf<MarkdownBlock>()
        val ordinary = StringBuilder()
        var fence: CodeFence? = null
        var index = 0
        fun flush() {
            if (ordinary.isNotEmpty()) {
                result += blocks(parser().parse(markInlines(ordinary.toString(), context)), context)
                ordinary.clear()
            }
        }
        while (index < lines.size) {
            val line = lines[index].trimEnd('\r')
            val fenceAt = logicalFence(line)
            if (fence != null) {
                if (fenceAt != null && fenceAt.marker == fence.marker && fenceAt.length >= fence.length && logicalFenceTail(line, fence.marker).isBlank()) fence = null
            } else if (fenceAt != null) {
                fence = fenceAt
            } else {
                val trimmed = line.trim()
                val closing = when (trimmed) { "$$" -> "$$"; "\\[" -> "\\]"; else -> null }
                val singleLine = when {
                    trimmed.startsWith("$$$$") && trimmed.endsWith("$$$$") -> null
                    trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4 -> trimmed.substring(2, trimmed.length - 2)
                    trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 4 -> trimmed.substring(2, trimmed.length - 2)
                    else -> null
                }
                if (closing != null || singleLine != null) {
                    flush()
                    if (singleLine != null) {
                        result += MarkdownBlock.Math(singleLine.trim())
                    } else {
                        val formula = mutableListOf<String>()
                        index++
                        while (index < lines.size && lines[index].trim() != closing) {
                            formula += lines[index].trimEnd('\r')
                            index++
                        }
                        result += MarkdownBlock.Math(formula.joinToString("\n").trim('\n'))
                    }
                    index++
                    continue
                }
            }
            ordinary.append(line)
            if (index < lines.lastIndex) ordinary.append('\n')
            index++
        }
        flush()
        return result
    }

    /** Nonstandard inline marks become opaque tokens before CommonMark parses emphasis and links. */
    private fun markInlines(source: String, context: ParseContext): String {
        val result = StringBuilder(source.length)
        var cursor = 0
        var fence: CodeFence? = null
        var inlineTicks = 0
        var destinationDepth = 0
        while (cursor < source.length) {
            val lineStart = cursor == 0 || source[cursor - 1] == '\n'
            if (lineStart) {
                val lineEnd = source.indexOf('\n', cursor).let { if (it < 0) source.length else it + 1 }
                val line = source.substring(cursor, lineEnd).trimEnd('\n', '\r')
                val opening = logicalFence(line)
                if (fence != null) {
                    if (opening != null && opening.marker == fence.marker && opening.length >= fence.length && logicalFenceTail(line, fence.marker).isBlank()) fence = null
                    result.append(source, cursor, lineEnd)
                    cursor = lineEnd
                    continue
                }
                if (opening != null) {
                    fence = opening
                    result.append(source, cursor, lineEnd)
                    cursor = lineEnd
                    continue
                }
                if (line.startsWith("    ") || line.startsWith('\t') || linkDefinition.containsMatchIn(line)) {
                    result.append(source, cursor, lineEnd)
                    cursor = lineEnd
                    continue
                }
            }
            val char = source[cursor]
            if (inlineTicks == 0 && char == '\\' && !escaped(source, cursor) && source.startsWith("==", cursor + 1)) {
                val literalEnd = source.indexOf("==", cursor + 3)
                if (literalEnd >= 0 && '\n' !in source.substring(cursor, literalEnd)) {
                    result.append(source, cursor, literalEnd + 2)
                    cursor = literalEnd + 2
                    continue
                }
            }
            if (char == '`') {
                val ticks = backtickRunAt(source, cursor)
                inlineTicks = if (inlineTicks == 0) ticks else if (inlineTicks == ticks) 0 else inlineTicks
                result.append(source, cursor, cursor + ticks)
                cursor += ticks
                continue
            }
            if (inlineTicks == 0) {
                if (destinationDepth > 0) {
                    if (char == '(') destinationDepth++
                    if (char == ')') destinationDepth--
                } else if (char == '(' && cursor > 0 && source[cursor - 1] == ']') {
                    destinationDepth = 1
                }
                if (destinationDepth == 0 && !escaped(source, cursor)) {
                    val markerEnd = if (char == '[') source.indexOf(']', cursor + 1).takeIf { it in (cursor + 2)..(cursor + 100) } else null
                    val footnote = markerEnd?.let { Regex("^\\[([^]\\n]*?)\\^([^]\\n]+)]$").matchEntire(source.substring(cursor, it + 1)) }
                    if (footnote != null) {
                        val after = source.getOrNull(cursor + footnote.value.length)
                        val id = footnote.groupValues[2].trim().lowercase()
                        val body = context.footnotes[id]
                        if (body != null && after != '(' && after != '[') {
                            val prefix = footnote.groupValues[1]
                            context.referencedIds += id
                            result.append(context.token(MarkdownRun("${prefix}[$id]", footnoteId = id, footnoteBody = body)))
                            cursor += footnote.value.length
                            continue
                        }
                    }
                    val safeHtml = if (char == '<') Regex("^<(sup|sub|mark|kbd)>([^<>\\n]+)</\\1>", RegexOption.IGNORE_CASE)
                        .find(source.substring(cursor, minOf(source.length, cursor + 256))) else null
                    if (safeHtml != null) {
                        val body = safeHtml.groupValues[2]
                        val run = when (safeHtml.groupValues[1].lowercase()) {
                            "sup" -> MarkdownRun(body, superscript = true)
                            "sub" -> MarkdownRun(body, subscript = true)
                            "mark" -> MarkdownRun(body, highlight = true)
                            else -> MarkdownRun(body, code = true)
                        }
                        result.append(context.token(run))
                        cursor += safeHtml.value.length
                        continue
                    }
                    val delimiter = when {
                        source.startsWith("==", cursor) -> "=="
                        char == '~' && source.getOrNull(cursor + 1) != '~' && source.getOrNull(cursor - 1) != '~' -> "~"
                        char == '^' && source.getOrNull(cursor + 1) != '^' -> "^"
                        char == '$' && source.getOrNull(cursor + 1) != '$' && source.getOrNull(cursor - 1) != '$' -> "$"
                        source.startsWith("\\(", cursor) -> "\\("
                        else -> null
                    }
                    if (delimiter != null) {
                        val closeDelimiter = if (delimiter == "\\(") "\\)" else delimiter
                        var end = source.indexOf(closeDelimiter, cursor + delimiter.length)
                        while (end >= 0 && escaped(source, end)) {
                            end = source.indexOf(closeDelimiter, end + closeDelimiter.length)
                        }
                        if (end > cursor + delimiter.length && '\n' !in source.substring(cursor + delimiter.length, end) &&
                            (delimiter !in setOf("~", "^") || source.substring(cursor + delimiter.length, end).none { it.isWhitespace() }) &&
                            !source[cursor + delimiter.length].isWhitespace() && !source[end - 1].isWhitespace() &&
                            (delimiter != "$" || source.getOrNull(end + 1)?.isDigit() != true)
                        ) {
                            val content = source.substring(cursor + delimiter.length, end)
                            val run = when (delimiter) {
                                "==" -> MarkdownRun(content, highlight = true)
                                "~" -> MarkdownRun(content, subscript = true)
                                "^" -> MarkdownRun(content, superscript = true)
                                else -> MarkdownRun(content, math = true)
                            }
                            result.append(context.token(run))
                            cursor = end + closeDelimiter.length
                            continue
                        }
                    }
                }
            }
            result.append(char)
            cursor++
        }
        return result.toString()
    }

    private fun escaped(source: String, offset: Int): Boolean {
        var backslashes = 0
        var index = offset - 1
        while (index >= 0 && source[index] == '\\') { backslashes++; index-- }
        return backslashes % 2 == 1
    }

    private fun logicalFence(line: String): CodeFence? {
        val content = line.replace(Regex("^ {0,3}(?:> ?)*"), "")
            .replace(Regex("^(?:[-+*]|\\d+[.)]) +"), "")
            .trimStart(' ', '\t')
        val marker = content.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val length = content.takeWhile { it == marker }.length
        return if (length >= 3) CodeFence(marker, length) else null
    }

    private fun logicalFenceTail(line: String, marker: Char): String {
        val content = line.replace(Regex("^ {0,3}(?:> ?)*"), "")
            .replace(Regex("^(?:[-+*]|\\d+[.)]) +"), "")
            .trimStart(' ', '\t')
        return content.dropWhile { it == marker }
    }

    private data class CodeFence(val marker: Char, val length: Int)

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

    private fun blocks(parent: Node, context: ParseContext): List<MarkdownBlock> = children(parent).mapNotNull { node ->
        when (node) {
            is Paragraph -> MarkdownBlock.Paragraph(inlines(node, context))
            is Heading -> MarkdownBlock.Heading(node.level, inlines(node, context))
            is FencedCodeBlock -> {
                val language = node.info.orEmpty().trim().substringBefore(' ')
                val literal = node.literal.removeSuffix("\n")
                if (language.lowercase() in setOf("math", "latex", "tex")) MarkdownBlock.Math(literal)
                else MarkdownBlock.Code(language, literal)
            }
            is IndentedCodeBlock -> MarkdownBlock.Code("", node.literal.removeSuffix("\n"))
            is BlockQuote -> MarkdownBlock.Quote(blocks(node, context))
            is BulletList -> MarkdownBlock.Items(null, children(node).map { blocks(it, context) })
            is OrderedList -> MarkdownBlock.Items(node.startNumber, children(node).map { blocks(it, context) })
            is ThematicBreak -> MarkdownBlock.Rule
            is TableBlock -> {
                val rows = children(node).flatMap(::children)
                val cells = rows.map { row -> children(row).map { inlines(it, context) } }
                val alignments = rows.firstOrNull()?.let(::children).orEmpty().map {
                    (it as? TableCell)?.alignment?.name.orEmpty()
                }
                MarkdownBlock.Table(cells, alignments)
            }
            is HtmlBlock -> MarkdownBlock.Paragraph(listOf(MarkdownRun(node.literal.trimEnd())))
            else -> null
        }
    }

    private fun inlines(parent: Node, context: ParseContext, style: MarkdownRun = MarkdownRun("")): List<MarkdownRun> =
        children(parent).flatMap { node ->
            when (node) {
                is Text -> expandedText(node.literal, context, style)
                is Code -> listOf(style.copy(text = node.literal, code = true))
                is SoftLineBreak -> listOf(style.copy(text = "\n"))
                is HardLineBreak -> listOf(style.copy(text = "\n"))
                is StrongEmphasis -> inlines(node, context, style.copy(bold = true))
                is Emphasis -> inlines(node, context, style.copy(italic = true))
                is Strikethrough -> inlines(node, context, style.copy(strike = true))
                is Link -> inlines(node, context, style.copy(link = node.destination))
                is Image -> listOf(style.copy(text = inlines(node, context).joinToString("") { it.text }, imageUrl = node.destination))
                is HtmlInline -> listOf(style.copy(text = if (node.literal.matches(Regex("(?i)<br\\s*/?>"))) "\n" else node.literal))
                else -> inlines(node, context, style)
            }
        }

    private fun expandedText(source: String, context: ParseContext, style: MarkdownRun): List<MarkdownRun> {
        val tokens = Regex("${Regex.escape(context.tokenPrefix)}(\\d+)\\uE001")
        val runs = mutableListOf<MarkdownRun>()
        var cursor = 0
        tokens.findAll(source).forEach { match ->
            if (match.range.first > cursor) runs += linkedText(source.substring(cursor, match.range.first), style)
            val token = match.groupValues[1].toIntOrNull()?.let(context.inlineTokens::getOrNull)
            runs += if (token == null) style.copy(text = match.value) else style.copy(
                text = token.text,
                code = token.code || style.code,
                highlight = token.highlight,
                superscript = token.superscript,
                subscript = token.subscript,
                math = token.math,
                footnoteId = token.footnoteId,
                footnoteBody = token.footnoteBody
            )
            cursor = match.range.last + 1
        }
        if (cursor < source.length) runs += linkedText(source.substring(cursor), style)
        return runs
    }

    private fun linkedText(source: String, style: MarkdownRun): List<MarkdownRun> {
        if (style.link != null) return listOf(style.copy(text = source))
        val result = mutableListOf<MarkdownRun>()
        var cursor = 0
        bareUrl.findAll(source).forEach { match ->
            var trimmed = match.value.trimEnd('.', ',', ';', ':', '!', '?')
            while (trimmed.endsWith(')') && trimmed.count { it == ')' } > trimmed.count { it == '(' }) {
                trimmed = trimmed.dropLast(1)
            }
            if (trimmed.isEmpty()) return@forEach
            if (match.range.first > cursor) result += style.copy(text = source.substring(cursor, match.range.first))
            val destination = when {
                trimmed.startsWith("www.", true) -> "https://$trimmed"
                '@' in trimmed && "://" !in trimmed -> "mailto:$trimmed"
                else -> trimmed
            }
            result += style.copy(text = trimmed, link = destination)
            cursor = match.range.first + trimmed.length
        }
        if (cursor < source.length) result += style.copy(text = source.substring(cursor))
        return result
    }
}
