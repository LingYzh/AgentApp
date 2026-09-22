package com.example.myapplication.ui.components

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
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.codeSurfaceColor

/** Copy always uses the original record, including whitespace and line endings. */
@Composable
fun InlineCodePanel(title: String, text: String, emptyLabel: String = "本次命令没有输出") {
    var wrap by rememberSaveable { mutableStateOf(false) }
    var tall by rememberSaveable { mutableStateOf(false) }
    var copied by remember(text) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val horizontal = rememberScrollState()
    val lines = remember(text) { text.split('\n') }
    Surface(color = codeSurfaceColor(), shape = MaterialTheme.shapes.small) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { wrap = !wrap }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                    Icon(Icons.AutoMirrored.Filled.WrapText, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (wrap) "不换行" else "换行", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }, Modifier.size(40.dp)) {
                    Crossfade(copied, label = "copy generic code") {
                        Icon(if (it) Icons.Default.Check else Icons.Default.ContentCopy, if (it) "已复制" else "复制", Modifier.size(17.dp))
                    }
                }
            }
            if (text.isEmpty()) {
                Text(emptyLabel, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else SelectionContainer {
                BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(tween(240))) {
                    val longest = remember(text) { lines.maxOfOrNull(::codeColumns) ?: 0 }
                    val width = if (wrap) maxWidth else maxOf(maxWidth,
                        with(LocalDensity.current) { (longest * 8).sp.toDp() } + 24.dp)
                    Box(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)) {
                        LazyColumn(
                            Modifier.width(width).heightIn(max = if (tall) 520.dp else 238.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            itemsIndexed(lines) { _, line ->
                                Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                    lineHeight = 22.sp, softWrap = wrap)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { tall = !tall }, Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (tall) "限制高度" else "展开高度", style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(14.dp))
            }
        }
    }
}

/** A command and its matching output are one historical record and intentionally share a panel. */
@Composable
fun ToolRecordPanel(
    command: String?,
    rawCommandFallback: String?,
    output: String?,
    exitCode: Int?,
    running: Boolean,
    awaitingApproval: Boolean,
    queued: Boolean,
    cancelled: Boolean,
    failed: Boolean,
    isShell: Boolean = true,
    toolName: String = "Shell"
) {
    // The owning tool row is keyed by conversation/message/call. Output arriving must not
    // reset the user's reading settings for this same invocation.
    var wrap by rememberSaveable { mutableStateOf(false) }
    var tall by rememberSaveable { mutableStateOf(false) }
    var copied by remember(command, rawCommandFallback, output) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val originalCommand = command ?: rawCommandFallback.orEmpty()
    val commandLines = remember(originalCommand) { originalCommand.split('\n') }
    val outputLines = remember(output) { output?.split('\n').orEmpty() }
    val horizontal = rememberScrollState()
    val copyText = remember(originalCommand, output) {
        buildString {
            append(originalCommand)
            if (output != null) {
                if (isNotEmpty()) append("\n\n")
                append(output)
            }
        }
    }
    val footer = when {
        isShell && exitCode != null -> "退出码 $exitCode"
        awaitingApproval -> "等待审批"
        running -> "执行中 · 等待返回"
        cancelled -> "已中止 · 保留已有输出"
        queued -> "等待执行"
        failed -> if (isShell) "命令失败，退出码未报告" else "工具调用失败"
        output == null -> "此调用尚未返回结果"
        else -> if (isShell) "退出码未报告" else "工具已返回"
    }
    Surface(color = codeSurfaceColor(), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(toolName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { wrap = !wrap }, contentPadding = PaddingValues(horizontal = 7.dp)) {
                    Icon(Icons.AutoMirrored.Filled.WrapText, "切换换行", Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (wrap) "不换行" else "换行", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(copyText)); copied = true }, Modifier.size(40.dp)) {
                    Crossfade(copied, label = "copy shell") {
                        Icon(if (it) Icons.Default.Check else Icons.Default.ContentCopy,
                            if (it) "已复制" else if (isShell) "复制命令和输出" else "复制入参和回参", Modifier.size(17.dp))
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(tween(240))) {
                val longest = remember(commandLines, outputLines) {
                    (commandLines + outputLines).maxOfOrNull(::codeColumns) ?: 0
                }
                val width = if (wrap) maxWidth else maxOf(maxWidth,
                    with(LocalDensity.current) { (longest * 8).sp.toDp() } + 28.dp)
                Box(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)) {
                    SelectionContainer {
                        LazyColumn(
                            Modifier.width(width).heightIn(max = if (tall) 520.dp else 238.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            if (!isShell) item { Text("入参", Modifier.padding(bottom = 8.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (originalCommand.isBlank()) {
                                item {
                                    Text(if (isShell) "原始命令未保存在此记录中。" else "原始入参未保存在此记录中。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 22.sp)
                                }
                            } else {
                                itemsIndexed(commandLines) { _, line ->
                                    Text(if (!isShell || command == null) line else "$ " + line, color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 22.sp,
                                        softWrap = wrap)
                                }
                            }
                            if (!isShell) item { Text("回参", Modifier.padding(top = 16.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            when {
                                output == null -> item {
                                    Text(when {
                                        awaitingApproval -> "尚未执行，暂无输出。批准后才会运行。"
                                        running -> "正在执行，等待本次输出。"
                                        queued -> "等待执行，暂无输出。"
                                        else -> "此调用尚未返回结果。"
                                    }, Modifier.padding(top = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp, lineHeight = 21.sp)
                                }
                                output.isEmpty() -> item {
                                    Text(if (isShell) "（本次命令未产生输出）" else "（本次工具返回空内容）", Modifier.padding(top = if (isShell) 16.dp else 0.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp, lineHeight = 21.sp)
                                }
                                else -> {
                                    if (isShell) item { Spacer(Modifier.height(16.dp)) }
                                    itemsIndexed(outputLines) { _, line ->
                                        Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 22.sp,
                                            softWrap = wrap)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(footer, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { tall = !tall }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                    Text(if (tall) "限制高度" else "展开高度", style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(14.dp))
                }
            }
        }
    }
}
