package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
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
private fun MarkdownBlocks(blocks: List<MarkdownBlock>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { index, block ->
            key(index, block.javaClass) {
                when (block) {
                    is MarkdownBlock.Paragraph -> RichMarkdownText(block.runs)
                    is MarkdownBlock.Heading -> {
                        val size = when (block.level) { 1 -> 24; 2 -> 21; 3 -> 18; else -> 16 }
                        RichMarkdownText(block.runs, style = TextStyle(fontSize = size.sp,
                            lineHeight = (size + 8).sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp))
                    }
                    is MarkdownBlock.Code -> MarkdownCode(block)
                    is MarkdownBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(Modifier.fillMaxHeight().width(3.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        MarkdownBlocks(block.blocks, Modifier.weight(1f).padding(start = 14.dp))
                    }
                    is MarkdownBlock.Items -> MarkdownList(block)
                    is MarkdownBlock.Table -> MarkdownTable(block)
                    MarkdownBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun RichMarkdownText(
    runs: List<MarkdownRun>, modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    textAlign: TextAlign = TextAlign.Start
) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val annotated = remember(runs, colors, uriHandler) {
        buildAnnotatedString {
            runs.forEach { run ->
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
                    fontSize = if (run.code) 14.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                    background = if (run.code) colors.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Unspecified,
                    color = if (run.link != null) colors.primary else androidx.compose.ui.graphics.Color.Unspecified,
                    textDecoration = when {
                        run.strike -> TextDecoration.LineThrough
                        run.link != null -> TextDecoration.Underline
                        else -> null
                    }
                )) { append(run.text) }
                if (run.link != null) pop()
            }
        }
    }
    Text(annotated, modifier.fillMaxWidth(), style = style, color = colors.onSurface, textAlign = textAlign)
}

@Composable
private fun MarkdownList(block: MarkdownBlock.Items) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        block.items.forEachIndexed { index, original ->
            val paragraph = original.firstOrNull() as? MarkdownBlock.Paragraph
            val leading = paragraph?.runs?.firstOrNull()?.text.orEmpty()
            val task = Regex("^\\[([ xX])]\\s").find(leading)
            val marker = if (task != null) {
                if (task.groupValues[1].equals("x", true)) "☑" else "☐"
            } else block.start?.let { "${it + index}." } ?: "•"
            val content = if (task != null && paragraph != null) {
                listOf(paragraph.copy(runs = paragraph.runs.mapIndexed { i, run ->
                    if (i == 0) run.copy(text = run.text.drop(task.value.length)) else run
                })) + original.drop(1)
            } else original
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(marker, Modifier.widthIn(min = 18.dp), fontSize = 16.sp, lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                MarkdownBlocks(content, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarkdownCode(block: MarkdownBlock.Code) {
    val clipboard = LocalClipboardManager.current
    var wrap by remember { mutableStateOf(true) }
    var copied by remember(block.text) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.language.ifBlank { "代码" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                TextButton(onClick = { wrap = !wrap }) { Text(if (wrap) "不换行" else "换行", style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = { clipboard.setText(AnnotatedString(block.text)); copied = true }) {
                    Text(if (copied) "已复制" else "复制", style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val scrollModifier = if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
            Box(Modifier.fillMaxWidth().then(scrollModifier).padding(14.dp)) {
                Text(block.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp,
                    softWrap = wrap, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

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
