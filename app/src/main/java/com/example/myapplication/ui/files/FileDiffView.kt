package com.example.myapplication.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.data.model.FileChange
import com.example.myapplication.data.store.DiffLine
import com.example.myapplication.data.store.DiffLineType
import com.example.myapplication.data.store.FileChanges
import com.example.myapplication.data.store.FileDiffResult

private val DiffAddedColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
        Color(0xFF81C995) else Color(0xFF1E7A3B)

@Composable
internal fun FileDiffStats(
    result: FileDiffResult,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    if (result.usedFallback || result.previewOmitted) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+${result.addedCount}",
            color = DiffAddedColor,
            style = textStyle
        )
        Text(
            text = "−${result.removedCount}",
            color = MaterialTheme.colorScheme.error,
            style = textStyle
        )
    }
}

/** 对话中查看一次工具写入的修改。 */
@Composable
fun FileDiffDialog(change: FileChange, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "文件更改",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    UiTextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    text = change.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FileDiffContent(change = change, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 文件页“更改”页签和弹窗共用的 diff 内容。 */
@Composable
fun FileDiffContent(
    change: FileChange,
    modifier: Modifier = Modifier
) {
    val result by androidx.compose.runtime.produceState<FileDiffResult?>(null, change) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { FileChanges.diff(change) }
    }
    Column(modifier.verticalScroll(rememberScrollState())) { InlineFileDiff(change, result) }
}

/**
 * Bounded conversation preview. The complete, selectable diff remains available in [FileDiffDialog].
 */
@Composable
fun FileDiffPreview(
    change: FileChange,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = remember(change) { FileChanges.diff(change) }
    val previewRows = remember(result) { diffPreviewRows(result.lines) }
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = change.path.substringAfterLast('/'),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (change.path.contains('/')) {
                        Text(
                            text = change.path,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                UiTextButton(
                    onClick = onOpenFull,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)
                ) {
                    Text("完整")
                }
            }
            FileDiffStats(
                result = result,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                textStyle = MaterialTheme.typography.labelMedium
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (result.previewOmitted) {
                Text(
                    text = "快照已截断",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            result.fallbackReason?.let { reason ->
                Text(
                    text = reason,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (previewRows.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(previewRows) { row ->
                        row.line?.let { DiffLineRow(it) } ?: Text(
                            "··· 省略 ${row.omitted} 行未修改内容",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = if (!result.usedFallback && !result.previewOmitted) "内容未变化" else "无法生成逐行预览",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val background = when (line.type) {
        DiffLineType.ADDED -> if (darkSurface) Color(0xFF173B27) else Color(0xFFE9F7EC)
        DiffLineType.REMOVED -> if (darkSurface) Color(0xFF4A1B1A) else Color(0xFFFCE8E6)
        DiffLineType.CONTEXT -> Color.Transparent
    }
    val marker = when (line.type) {
        DiffLineType.ADDED -> "+"
        DiffLineType.REMOVED -> "-"
        DiffLineType.CONTEXT -> " "
    }
    val markerColor = when (line.type) {
        DiffLineType.ADDED -> DiffAddedColor
        DiffLineType.REMOVED -> MaterialTheme.colorScheme.error
        DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(background),
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier
                .width(86.dp)
                .padding(start = 4.dp, end = 2.dp, top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = line.oldLineNumber?.toString().orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = line.newLineNumber?.toString().orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = marker,
            color = markerColor,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 6.dp, top = 3.dp)
        )
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun FallbackBlocks(result: FileDiffResult, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
    ) {
        if (result.beforeText != null) {
            Text(
                "原内容",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
            SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                Text(
                    result.beforeText,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        Text(
            "新内容",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            Text(
                result.afterText,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                softWrap = true,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

private fun resultToText(result: FileDiffResult): String {
    if (result.lines.isNotEmpty()) {
        return result.lines.joinToString("\n") { line ->
            val marker = when (line.type) {
                DiffLineType.ADDED -> "+"
                DiffLineType.REMOVED -> "-"
                DiffLineType.CONTEXT -> " "
            }
            "$marker ${line.text}"
        }
    }
    return buildString {
        result.beforeText?.let {
            appendLine("--- 原内容 ---")
            appendLine(it)
        }
        appendLine("+++ 新内容 +++")
        append(result.afterText)
    }
}


internal data class DiffPreviewRow(val line: DiffLine? = null, val omitted: Int = 0)

/** Keep changed hunks visible instead of starting a long preview with unchanged lines. */
internal fun diffPreviewRows(lines: List<DiffLine>): List<DiffPreviewRow> {
    val visible = BooleanArray(lines.size)
    lines.forEachIndexed { index, line ->
        if (line.type != DiffLineType.CONTEXT) {
            for (nearby in maxOf(0, index - 2)..minOf(lines.lastIndex, index + 2)) visible[nearby] = true
        }
    }
    if (visible.none { it }) return emptyList()
    return buildList {
        var omitted = 0
        lines.forEachIndexed { index, line ->
            if (!visible[index]) omitted++
            else {
                if (omitted > 0) { add(DiffPreviewRow(omitted = omitted)); omitted = 0 }
                add(DiffPreviewRow(line = line))
            }
        }
        if (omitted > 0) add(DiffPreviewRow(omitted = omitted))
    }
}
