package com.example.myapplication.ui.files

import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.store.DiffLineType
import com.example.myapplication.data.store.FileDiffResult
import com.example.myapplication.ui.components.InlineCodePanel

/** Historical snapshots only. Never read the current file to fill gaps in a recorded change. */
@Composable
fun InlineFileDiff(change: FileChange, result: FileDiffResult?) {
    var wrap by rememberSaveable { mutableStateOf(false) }
    var tall by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val horizontal = rememberScrollState()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small) {
        Column {
            Text(change.path, Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium)
            if (result == null) Text("正在计算此次 Diff…", Modifier.padding(10.dp))
            else {
                if (!result.usedFallback && !result.previewOmitted) FileDiffStats(result, Modifier.padding(horizontal = 10.dp))
                if (result.previewOmitted) Text("快照已截断：不提供完整增删统计。", Modifier.padding(10.dp))
                if (result.usedFallback) Text("无法显示完整逐行 Diff：${result.fallbackReason.orEmpty()}", Modifier.padding(10.dp))
                Row {
                    UiTextButton(onClick = { wrap = !wrap }) { Text(if (wrap) "不换行" else "换行") }
                    UiTextButton(onClick = { tall = !tall }) { Text(if (tall) "收起高度" else "展开高度") }
                    UiTextButton(onClick = {
                        val raw = result.lines.joinToString("\n") {
                            (when (it.type) { DiffLineType.ADDED -> "+"; DiffLineType.REMOVED -> "-"; else -> " " }) + it.text
                        }
                        clipboard.setText(AnnotatedString(raw)); copied = true
                    }, enabled = !result.usedFallback && !result.previewOmitted) {
                        androidx.compose.animation.Crossfade(copied, label = "copy diff") { Text(if (it) "已复制" else "复制 Diff") }
                    }
                }
                Row {
                    change.before?.let { before ->
                        UiTextButton(onClick = { clipboard.setText(AnnotatedString(before)) }) { Text("复制修改前${if (change.previewOmitted) "片段" else "原文"}") }
                    }
                    UiTextButton(onClick = { clipboard.setText(AnnotatedString(change.after)) }) { Text("复制修改后${if (change.previewOmitted) "片段" else "原文"}") }
                }
                if (result.usedFallback) {
                    result.beforeText?.let { InlineCodePanel("修改前快照", it) }
                    InlineCodePanel("修改后快照", result.afterText)
                } else {
                    // Lazy rows keep large diffs bounded; one shared horizontal viewport preserves alignment.
                    val longest = remember(result) { result.lines.maxOfOrNull { com.example.myapplication.ui.components.codeColumns(it.text) } ?: 0 }
                    val codeWidth = with(LocalDensity.current) { (longest * 8).sp.toDp() } + 100.dp
                    BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(tween(240))) {
                        val width = if (wrap) maxWidth else maxOf(maxWidth, codeWidth)
                        Box(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)) {
                            LazyColumn(Modifier.width(width).heightIn(max = if (tall) 560.dp else 270.dp)) {
                                itemsIndexed(result.lines) { _, line ->
                                    val background = when (line.type) {
                                        DiffLineType.ADDED -> if (dark) Color(0xFF24352A) else Color(0xFFE8F0E8)
                                        DiffLineType.REMOVED -> if (dark) Color(0xFF442A26) else Color(0xFFF6E8E4)
                                        else -> Color.Transparent
                                    }
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(background).padding(vertical = 2.dp)) {
                                        Box(Modifier.width(2.dp).fillMaxHeight().background(when (line.type) {
                                            DiffLineType.ADDED -> if (dark) Color(0xFF82C897) else Color(0xFF32754C)
                                            DiffLineType.REMOVED -> if (dark) Color(0xFFED9990) else Color(0xFFB13E37)
                                            else -> Color.Transparent
                                        }))
                                        Text(line.oldLineNumber?.toString().orEmpty(), Modifier.width(36.dp), fontSize = 11.sp)
                                        Text(line.newLineNumber?.toString().orEmpty(), Modifier.width(36.dp), fontSize = 11.sp)
                                        Text(when (line.type) { DiffLineType.ADDED -> "+"; DiffLineType.REMOVED -> "−"; else -> " " }, Modifier.width(16.dp))
                                        SelectionContainer(Modifier.weight(1f)) {
                                            Text(line.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                                lineHeight = 21.sp, softWrap = wrap)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
