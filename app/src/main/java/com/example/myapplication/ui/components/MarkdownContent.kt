package com.example.myapplication.ui.components

import com.example.myapplication.ui.components.UiTextButton
import com.example.myapplication.ui.theme.codeSurfaceColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Native Compose blocks: no AndroidView replacement or whole-message text layout reset. */
@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val latest by rememberUpdatedState(text)
    // Initial text must have its real height on first composition (especially when scrolling
    // back into an older message). Only subsequent streamed updates parse in the background.
    var document by remember { mutableStateOf(MarkdownDocument.parse(text)) }
    var parsedText by remember { mutableStateOf(text) }
    // Sampling continues to draw during an uninterrupted stream. Keep the previous document
    // visible until parsing completes, including the final update. Never animate replacements.
    LaunchedEffect(streaming, if (streaming) null else text) {
        do {
            val source = latest
            if (parsedText != source) {
                val parsed = withContext(Dispatchers.Default) { MarkdownDocument.parse(source) }
                document = parsed
                parsedText = source
            }
            if (!streaming) break
            delay(120)
        } while (isActive)
    }
    SelectionContainer(modifier.fillMaxWidth()) { MarkdownBlocks(document) }
}

@Composable
private fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    paragraphStyle: TextStyle? = null,
    paragraphColor: Color? = null
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { index, block ->
            key(index, block.javaClass) {
                when (block) {
                    is MarkdownBlock.Paragraph -> RichMarkdownText(
                        block.runs,
                        style = paragraphStyle ?: TextStyle(fontSize = 16.sp, lineHeight = 29.sp),
                        color = paragraphColor ?: MaterialTheme.colorScheme.onSurface
                    )
                    is MarkdownBlock.Heading -> {
                        val size = when (block.level) { 1 -> 27; 2 -> 21; 3 -> 18; else -> 16 }
                        RichMarkdownText(block.runs, style = TextStyle(fontSize = size.sp,
                            lineHeight = (size + 8).sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp))
                    }
                    is MarkdownBlock.Code -> MarkdownCode(block, Modifier.padding(top = 7.dp, bottom = 11.dp))
                    is MarkdownBlock.Details -> MarkdownDetails(block)
                    is MarkdownBlock.Quote -> MarkdownQuote(block, Modifier.padding(top = 8.dp, bottom = 11.dp))
                    is MarkdownBlock.Items -> MarkdownList(block, paragraphStyle, paragraphColor)
                    is MarkdownBlock.Table -> MarkdownTable(block)
                    MarkdownBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MarkdownQuote(block: MarkdownBlock.Quote, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 10.dp, bottomEnd = 10.dp),
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.fillMaxHeight().width(3.dp).background(Color(0xFFD97757)))
            MarkdownBlocks(
                block.blocks,
                Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, end = 15.dp, bottom = 1.dp),
                paragraphStyle = TextStyle(fontSize = 14.sp, lineHeight = 26.sp),
                paragraphColor = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarkdownDetails(block: MarkdownBlock.Details) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(block.open) }
    val bodyState = rememberSaveableStateHolder()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }
                .padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val chevron = if (expanded) Modifier else Modifier.graphicsLayer { rotationZ = -90f }
                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp).then(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                RichMarkdownText(block.summary, Modifier.weight(1f).padding(start = 9.dp),
                    style = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium))
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                bodyState.SaveableStateProvider("details-content") {
                    MarkdownBlocks(block.blocks, Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun RichMarkdownText(
    runs: List<MarkdownRun>, modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 29.sp),
    textAlign: TextAlign = TextAlign.Start,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val presentation = remember(runs, colors, uriHandler, style) {
        val codeRanges = mutableListOf<InlineCodeRange>()
        buildAnnotatedString {
            runs.forEach { run ->
                val start = length
                if (run.link != null) pushLink(LinkAnnotation.Url(run.link,
                    styles = TextLinkStyles(style = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = {
                        if (run.link.substringBefore(':').lowercase() in setOf("https", "http", "mailto", "tel")) {
                            runCatching { uriHandler.openUri(run.link) }
                        }
                    }))
                withStyle(SpanStyle(
                    fontWeight = if (run.bold) FontWeight.SemiBold else null,
                    fontStyle = if (run.italic) FontStyle.Italic else null,
                    fontFamily = if (run.code) FontFamily.Monospace else null,
                    fontSize = if (run.code) inlineCodeFontSize(style) else TextUnit.Unspecified,
                    color = if (run.link != null) colors.primary else androidx.compose.ui.graphics.Color.Unspecified,
                    textDecoration = when {
                        run.strike -> TextDecoration.LineThrough
                        run.link != null -> TextDecoration.Underline
                        else -> null
                    }
                )) { append(run.text) }
                if (run.link != null) pop()
                if (run.code && length > start) codeRanges += InlineCodeRange(start, length)
            }
        }.let { MarkdownTextPresentation(it, codeRanges) }
    }
    var layout by remember(presentation) { mutableStateOf<TextLayoutResult?>(null) }
    val decoratedModifier = modifier.fillMaxWidth().drawBehind {
        layout?.let { drawInlineCodeBackgrounds(it, presentation.codeRanges, colors.surfaceContainerLow, colors.outlineVariant) }
    }
    Text(
        presentation.annotated,
        decoratedModifier,
        style = style,
        color = color,
        textAlign = textAlign,
        onTextLayout = { layout = it }
    )
}

private data class InlineCodeRange(val start: Int, val endExclusive: Int)

private data class MarkdownTextPresentation(
    val annotated: AnnotatedString,
    val codeRanges: List<InlineCodeRange>
)

private fun inlineCodeFontSize(style: TextStyle): androidx.compose.ui.unit.TextUnit =
    if (style.fontSize == TextUnit.Unspecified) 14.sp else style.fontSize * .88f

private fun DrawScope.drawInlineCodeBackgrounds(
    layout: TextLayoutResult,
    ranges: List<InlineCodeRange>,
    background: Color,
    outline: Color
) {
    val horizontalPadding = 5.dp.toPx()
    val verticalPadding = 1.dp.toPx()
    val corner = CornerRadius(5.dp.toPx())
    val stroke = Stroke(1.dp.toPx())
    val source = layout.layoutInput.text
    ranges.forEach { range ->
        if (range.start >= range.endExclusive || range.start >= layout.layoutInput.text.length) return@forEach
        val lastOffset = (range.endExclusive - 1).coerceAtMost(layout.layoutInput.text.length - 1)
        val firstLine = layout.getLineForOffset(range.start)
        val lastLine = layout.getLineForOffset(lastOffset)
        for (line in firstLine..lastLine) {
            val start = maxOf(range.start, layout.getLineStart(line))
            val end = minOf(lastOffset + 1, layout.getLineEnd(line))
            if (start >= end) continue
            val codeGlyphs = (start until end).map(layout::getBoundingBox)
                .filter { it.width > 0f || it.height > 0f }
                .sortedBy(Rect::left)
            codeGlyphs.foldVisualSegments().forEach { segment ->
                val (leftPadding, rightPadding) = inlineCodePadding(
                    layout, source, line, range, segment, horizontalPadding
                )
                val left = (segment.left - leftPadding).coerceAtLeast(0f)
                val right = (segment.right + rightPadding).coerceAtMost(size.width)
                val top = (segment.top - verticalPadding).coerceAtLeast(0f)
                val bottom = (segment.bottom + verticalPadding).coerceAtMost(size.height)
                val box = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
                drawRoundRect(background, Offset(left, top), box, corner)
                drawRoundRect(outline, Offset(left, top), box, corner, style = stroke)
            }
        }
    }
}

private fun List<Rect>.foldVisualSegments(): List<Rect> {
    if (isEmpty()) return emptyList()
    val segments = mutableListOf<Rect>()
    var current = first()
    drop(1).forEach { glyph ->
        if (glyph.left <= current.right + .5f) {
            current = Rect(
                left = minOf(current.left, glyph.left), top = minOf(current.top, glyph.top),
                right = maxOf(current.right, glyph.right), bottom = maxOf(current.bottom, glyph.bottom)
            )
        } else {
            segments += current
            current = glyph
        }
    }
    segments += current
    return segments
}

private fun DrawScope.inlineCodePadding(
    layout: TextLayoutResult,
    source: AnnotatedString,
    line: Int,
    range: InlineCodeRange,
    segment: Rect,
    desired: Float
): Pair<Float, Float> {
    val lineStart = layout.getLineStart(line)
    val lineEnd = layout.getLineEnd(line)
    var leftNeighbor: Pair<Rect, Char>? = null
    var rightNeighbor: Pair<Rect, Char>? = null
    for (offset in lineStart until lineEnd) {
        if (offset in range.start until range.endExclusive) continue
        val glyph = layout.getBoundingBox(offset)
        if (glyph.right <= segment.left && (leftNeighbor == null || glyph.right > leftNeighbor!!.first.right)) {
            leftNeighbor = glyph to source[offset]
        }
        if (glyph.left >= segment.right && (rightNeighbor == null || glyph.left < rightNeighbor!!.first.left)) {
            rightNeighbor = glyph to source[offset]
        }
    }
    return edgePadding(segment.left, leftNeighbor, desired, fromLeft = true) to
        edgePadding(segment.right, rightNeighbor, desired, fromLeft = false)
}

private fun edgePadding(edge: Float, neighbor: Pair<Rect, Char>?, desired: Float, fromLeft: Boolean): Float {
    if (neighbor == null) return desired
    val glyph = neighbor.first
    val gap = if (fromLeft) edge - glyph.right else glyph.left - edge
    return if (neighbor.second.isWhitespace()) {
        minOf(desired, (gap + glyph.width).coerceAtLeast(0f))
    } else {
        minOf(desired, (gap / 2f).coerceAtLeast(0f))
    }
}

@Composable
private fun MarkdownList(
    block: MarkdownBlock.Items,
    paragraphStyle: TextStyle? = null,
    paragraphColor: Color? = null
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        block.items.forEachIndexed { index, original ->
            val paragraph = original.firstOrNull() as? MarkdownBlock.Paragraph
            val leading = paragraph?.runs?.firstOrNull()?.text.orEmpty()
            val task = Regex("^\\[([ xX])]\\s").find(leading)
            val taskChecked = task?.groupValues?.get(1)?.equals("x", true)
            val marker = block.start?.let { "${it + index}." } ?: "•"
            val content = if (task != null && paragraph != null) {
                listOf(paragraph.copy(runs = paragraph.runs.mapIndexed { i, run ->
                    if (i == 0) run.copy(text = run.text.drop(task.value.length)) else run
                })) + original.drop(1)
            } else original
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (taskChecked != null) {
                    Box(Modifier.width(18.dp).padding(top = 8.dp)) { MarkdownTaskCheck(taskChecked) }
                } else {
                    Text(marker, Modifier.widthIn(min = 18.dp), fontSize = 16.sp, lineHeight = 29.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MarkdownBlocks(content, Modifier.weight(1f), paragraphStyle, paragraphColor)
            }
        }
    }
}

@Composable
private fun MarkdownTaskCheck(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val success = if (colors.surface.luminance() < .5f) Color(0xFFABC399) else Color(0xFF526447)
    Canvas(Modifier.size(14.dp).semantics { contentDescription = if (checked) "已完成" else "未完成" }) {
        val strokeWidth = 1.3.dp.toPx()
        val inset = strokeWidth / 2f
        val corner = CornerRadius(3.dp.toPx())
        val box = Size(size.width - strokeWidth, size.height - strokeWidth)
        if (checked) {
            drawRoundRect(success, Offset(inset, inset), box, corner)
            drawLine(colors.background, Offset(3.4.dp.toPx(), 7.3.dp.toPx()), Offset(6.0.dp.toPx(), 9.7.dp.toPx()),
                strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(colors.background, Offset(6.0.dp.toPx(), 9.7.dp.toPx()), Offset(10.8.dp.toPx(), 4.6.dp.toPx()),
                strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        } else {
            drawRoundRect(colors.onSurfaceVariant, Offset(inset, inset), box, corner, style = Stroke(strokeWidth))
        }
    }
}

@Composable
private fun MarkdownCode(block: MarkdownBlock.Code, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var wrap by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var animateResize by remember { mutableStateOf(false) }
    LaunchedEffect(wrap) { if (animateResize) { delay(240); animateResize = false } }
    var copied by remember(block.text) { mutableStateOf(false) }
    var highlighted by remember(block.language, block.text) { mutableStateOf<List<SyntaxToken>?>(null) }
    LaunchedEffect(block.language, block.text) {
        highlighted = withContext(Dispatchers.Default) { SyntaxHighlighter.tokens(block.language, block.text) }
    }
    Surface(shape = RoundedCornerShape(13.dp), color = codeSurfaceColor(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.language.ifBlank { "代码" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                UiTextButton(onClick = { animateResize = true; wrap = !wrap }) { Text(if (wrap) "不换行" else "换行", style = MaterialTheme.typography.labelMedium) }
                UiTextButton(onClick = { clipboard.setText(AnnotatedString(block.text)); copied = true }) {
                    androidx.compose.animation.Crossfade(copied, label = "copy code") {
                        Text(if (it) "已复制" else "复制", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val horizontal = rememberScrollState()
            val scrollModifier = if (wrap) Modifier else Modifier.horizontalScroll(horizontal)
            Box(Modifier.fillMaxWidth().animateContentSize(if (animateResize) tween(240) else snap()).then(scrollModifier).padding(14.dp)) {
                CodeText(highlighted, block.text, wrap)
            }
        }
    }
}

@Composable
private fun CodeText(tokens: List<SyntaxToken>?, raw: String, wrap: Boolean) {
    val colors = MaterialTheme.colorScheme
    val syntax = if (colors.surface.luminance() < .5f) {
        CodeSyntaxColors(
            keyword = Color(0xFFE8AD94), string = Color(0xFFB4CEA5),
            number = Color(0xFFBABBE8), comment = Color(0xFFADAA9E)
        )
    } else {
        CodeSyntaxColors(
            keyword = Color(0xFF9B482C), string = Color(0xFF40643D),
            number = Color(0xFF5D6199), comment = Color(0xFF79766D)
        )
    }
    val annotated = remember(tokens, colors) {
        tokens?.takeUnless { source -> source.all { it.kind == SyntaxKind.PLAIN } }?.let { source ->
            buildAnnotatedString {
                source.forEach { token ->
                    withStyle(SpanStyle(color = when (token.kind) {
                        SyntaxKind.KEYWORD -> syntax.keyword
                        SyntaxKind.STRING -> syntax.string
                        SyntaxKind.COMMENT -> syntax.comment
                        SyntaxKind.NUMBER -> syntax.number
                        SyntaxKind.TYPE -> syntax.keyword.copy(alpha = .82f)
                        SyntaxKind.FUNCTION -> syntax.string.copy(alpha = .9f)
                        SyntaxKind.PLAIN -> colors.onSurface
                    })) { append(token.text) }
                }
            }
        }
    }
    if (annotated == null) {
        Text(raw, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 24.sp,
            softWrap = wrap, color = colors.onSurface)
    } else {
        Text(annotated, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 24.sp, softWrap = wrap)
    }
}

private data class CodeSyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color
)

@Composable
private fun MarkdownTable(block: MarkdownBlock.Table) {
    val columns = block.rows.maxOfOrNull { it.size } ?: return
    if (columns == 0) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = if (columns <= 2) maxWidth / columns else 156.dp
        Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                block.rows.forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    Row(Modifier.width(cellWidth * columns).background(
                        if (rowIndex == 0) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)) {
                        repeat(columns) { column ->
                            RichMarkdownText(row.getOrElse(column) { emptyList() },
                                Modifier.width(cellWidth).padding(horizontal = 12.dp, vertical = 10.dp),
                                style = TextStyle(fontSize = 14.sp, lineHeight = 22.sp,
                                    fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal),
                                textAlign = when (block.alignments.getOrNull(column)) {
                                    "RIGHT" -> TextAlign.End
                                    "CENTER" -> TextAlign.Center
                                    else -> TextAlign.Start
                                })
                        }
                    }
                }
            }
        }
    }
}
