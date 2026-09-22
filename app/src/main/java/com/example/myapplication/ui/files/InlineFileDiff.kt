package com.example.myapplication.ui.files

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.store.DiffLineType
import com.example.myapplication.data.store.FileDiffResult
import com.example.myapplication.ui.components.SyntaxHighlighter
import com.example.myapplication.ui.components.SyntaxKind
import com.example.myapplication.ui.components.SyntaxToken
import com.example.myapplication.ui.components.codeColumns
import com.example.myapplication.ui.theme.codeSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Historical snapshots only. Never read the current file to fill gaps in a recorded change. */
@Composable
fun InlineFileDiff(change: FileChange, result: FileDiffResult?) {
    var wrap by rememberSaveable(change.path) { mutableStateOf(false) }
    var tall by rememberSaveable(change.path) { mutableStateOf(false) }
    var copied by remember(result) { mutableStateOf(false) }
    var moreOpen by rememberSaveable(change.path) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val horizontal = rememberScrollState()
    val language = remember(change.path) { languageForPath(change.path) }
    val tokens by produceState<List<List<SyntaxToken>>?>(null, result, language) {
        value = result?.takeIf { !it.usedFallback && !it.previewOmitted }?.let { diff ->
            withContext(Dispatchers.Default) { diff.lines.map { SyntaxHighlighter.tokens(language, it.text) } }
        }
    }
    val pathName = remember(change.path) { change.path.substringAfterLast('/').substringAfterLast('\\') }
    val rawPatch = remember(result) {
        result?.takeIf { !it.usedFallback && !it.previewOmitted }?.lines?.joinToString("\n") { line ->
            (when (line.type) {
                DiffLineType.ADDED -> "+"
                DiffLineType.REMOVED -> "-"
                DiffLineType.CONTEXT -> " "
            }) + line.text
        }.orEmpty()
    }
    Surface(color = codeSurfaceColor(), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(pathName, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                result?.takeIf { !it.usedFallback && !it.previewOmitted }?.let { FileDiffStats(it) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { wrap = !wrap }, Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.WrapText, "切换 Diff 自动换行", Modifier.size(16.dp))
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(rawPatch)); copied = true },
                    enabled = rawPatch.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                    Crossfade(copied, label = "copy diff") {
                        Icon(if (it) Icons.Default.Check else Icons.Default.ContentCopy, if (it) "已复制补丁" else "复制 Diff", Modifier.size(17.dp))
                    }
                }
                Box {
                    IconButton(onClick = { moreOpen = true }, Modifier.size(40.dp)) {
                        Icon(Icons.Default.MoreVert, "更多原始快照操作", Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        change.before?.let { before ->
                            DropdownMenuItem(text = { Text("复制修改前${if (change.previewOmitted) "片段" else "原文"}") }, onClick = {
                                clipboard.setText(AnnotatedString(before)); moreOpen = false
                            })
                        }
                        DropdownMenuItem(text = { Text("复制修改后${if (change.previewOmitted) "片段" else "原文"}") }, onClick = {
                            clipboard.setText(AnnotatedString(change.after)); moreOpen = false
                        })
                    }
                }
            }
            when {
                result == null -> Text("正在计算此次 Diff…", Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.previewOmitted -> SnapshotNotice("快照已截断：不提供完整增删统计。")
                result.usedFallback -> SnapshotNotice("无法显示完整逐行 Diff：${result.fallbackReason.orEmpty()}")
                else -> DiffRows(result, tokens, wrap, tall, horizontal)
            }
            if (result?.usedFallback == true || result?.previewOmitted == true) {
                result.beforeText?.let { SnapshotRows("修改前快照", it, wrap, tall) }
                SnapshotRows("修改后快照", result.afterText, wrap, tall)
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已执行的变更 · 不是待应用补丁", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { tall = !tall }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                    Text(if (tall) "限制高度" else "展开高度", style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SnapshotNotice(message: String) {
    Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SnapshotRows(title: String, text: String, wrap: Boolean, tall: Boolean) {
    val lines = remember(text) { text.split('\n') }
    val horizontal = rememberScrollState()
    Text(title, Modifier.padding(start = 12.dp, top = 4.dp), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = if (wrap) maxWidth else maxOf(maxWidth,
            with(LocalDensity.current) { ((lines.maxOfOrNull(::codeColumns) ?: 0) * 8).sp.toDp() } + 24.dp)
        Box(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)) {
            SelectionContainer {
                LazyColumn(Modifier.width(width).heightIn(max = if (tall) 520.dp else 180.dp),
                    contentPadding = PaddingValues(12.dp)) {
                    itemsIndexed(lines) { _, line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 22.sp, softWrap = wrap,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffRows(
    result: FileDiffResult,
    tokens: List<List<SyntaxToken>>?,
    wrap: Boolean,
    tall: Boolean,
    horizontal: androidx.compose.foundation.ScrollState
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val longest = remember(result) { result.lines.maxOfOrNull { codeColumns(it.text) } ?: 0 }
    BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(tween(240))) {
        val width = if (wrap) maxWidth else maxOf(maxWidth,
            with(LocalDensity.current) { (longest * 8).sp.toDp() } + 62.dp)
        Box(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)) {
            SelectionContainer {
                LazyColumn(Modifier.width(width).heightIn(max = if (tall) 560.dp else 270.dp),
                    contentPadding = PaddingValues(vertical = 3.dp)) {
                    itemsIndexed(result.lines) { index, line ->
                        val markerColor = when (line.type) {
                            DiffLineType.ADDED -> if (dark) Color(0xFF82C897) else Color(0xFF32754C)
                            DiffLineType.REMOVED -> if (dark) Color(0xFFED9990) else Color(0xFFB13E37)
                            DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val background = when (line.type) {
                            DiffLineType.ADDED -> if (dark) Color(0xFF24352A) else Color(0xFFE8F0E8)
                            DiffLineType.REMOVED -> if (dark) Color(0xFF442A26) else Color(0xFFF6E8E4)
                            DiffLineType.CONTEXT -> Color.Transparent
                        }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 25.dp).background(background).drawBehind {
                                if (line.type != DiffLineType.CONTEXT) drawRect(markerColor, size = Size(3.dp.toPx(), size.height))
                            },
                            verticalAlignment = Alignment.Top
                        ) {
                            Text((line.newLineNumber ?: line.oldLineNumber)?.toString().orEmpty(), Modifier.width(32.dp),
                                textAlign = TextAlign.End, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                lineHeight = 25.sp, color = markerColor, softWrap = false)
                            Text(when (line.type) {
                                DiffLineType.ADDED -> "+"
                                DiffLineType.REMOVED -> "−"
                                DiffLineType.CONTEXT -> " "
                            }, Modifier.width(17.dp), textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp, lineHeight = 25.sp, color = markerColor)
                            DiffCode(tokens?.getOrNull(index), line.text, wrap, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffCode(tokens: List<SyntaxToken>?, raw: String, wrap: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < .5f
    val annotated = remember(tokens, colors, dark) {
        tokens?.takeUnless { source -> source.all { it.kind == SyntaxKind.PLAIN } }?.let { source ->
            buildAnnotatedString {
                source.forEach { token ->
                    val color = when (token.kind) {
                        SyntaxKind.KEYWORD, SyntaxKind.TYPE -> if (dark) Color(0xFFE8AD94) else Color(0xFF9B482C)
                        SyntaxKind.STRING, SyntaxKind.FUNCTION -> if (dark) Color(0xFFB4CEA5) else Color(0xFF40643D)
                        SyntaxKind.NUMBER -> if (dark) Color(0xFFBABBE8) else Color(0xFF5D6199)
                        SyntaxKind.COMMENT -> if (dark) Color(0xFFADAA9E) else Color(0xFF79766D)
                        SyntaxKind.PLAIN -> colors.onSurface
                    }
                    withStyle(SpanStyle(color = color)) { append(token.text) }
                }
            }
        }
    }
    if (annotated == null) Text(raw, modifier.padding(end = 12.dp), fontFamily = FontFamily.Monospace,
        fontSize = 11.5.sp, lineHeight = 25.sp, softWrap = wrap, color = colors.onSurface)
    else Text(annotated, modifier.padding(end = 12.dp), fontFamily = FontFamily.Monospace,
        fontSize = 11.5.sp, lineHeight = 25.sp, softWrap = wrap)
}

private fun languageForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "js", "jsx" -> "js"
    "ts", "tsx" -> "ts"
    "py" -> "python"
    "sh", "bash", "zsh" -> "shell"
    "json" -> "json"
    "yml", "yaml" -> "yaml"
    "html", "xml" -> "html"
    "css" -> "css"
    "sql" -> "sql"
    else -> ""
}
