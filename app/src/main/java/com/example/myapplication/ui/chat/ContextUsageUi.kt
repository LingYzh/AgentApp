package com.example.myapplication.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.ContextOverview
import com.example.myapplication.data.model.ContextSegment
import com.example.myapplication.data.model.ContextUsageRecord
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.ReasoningSupport
import java.text.DateFormat
import java.util.Date
import java.text.NumberFormat

@Composable
internal fun ContextUsageBar(
    overview: ContextOverview?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimated = overview?.segments?.sumOf { it.tokens } ?: 0L
    val maximum = overview?.maxTokens
    val label = if (maximum == null) {
        "上下文约 ${formatTokens(estimated)} · 未配置容量"
    } else {
        "上下文约 ${formatTokens(estimated)} / ${formatTokens(maximum.toLong())}"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("详情", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        SegmentedContextBar(overview, Modifier.fillMaxWidth().height(5.dp))
    }
}

@Composable
internal fun ReasoningEffortMenu(
    support: ReasoningSupport?,
    override: ReasoningEffort?,
    modelDefault: ReasoningEffort?,
    enabled: Boolean,
    onChange: (ReasoningEffort?) -> Unit
) {
    val available = support?.efforts.orEmpty()
    if (available.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = override?.let(::effortLabel) ?: modelDefault?.let { "跟随 · ${effortLabel(it)}" } ?: "跟随模型"
    Box {
        FilterChip(
            selected = override != null,
            onClick = { expanded = true },
            enabled = enabled,
            label = { Text("思考 · $selectedLabel", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("跟随模型${modelDefault?.let { "（${effortLabel(it)}）" } ?: ""}") },
                onClick = { onChange(null); expanded = false }
            )
            available.forEach { effort ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(effortLabel(effort)) },
                    onClick = { onChange(effort); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextUsageSheet(
    overview: ContextOverview?,
    isCompacting: Boolean,
    compactionProgress: String?,
    canCompact: Boolean,
    onCompact: () -> Unit,
    onCancelCompaction: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmCompaction by remember { mutableStateOf(false) }
    if (confirmCompaction) {
        AlertDialog(
            onDismissRequest = { confirmCompaction = false },
            title = { Text("压缩上下文？") },
            text = { Text("这会改变后续模型请求使用的上下文及其缓存。原始消息会保留在会话记录中。") },
            confirmButton = {
                TextButton(onClick = { confirmCompaction = false; onCompact() }) { Text("开始压缩") }
            },
            dismissButton = { TextButton(onClick = { confirmCompaction = false }) { Text("取消") } }
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val estimated = overview?.estimatedTokens ?: 0L
        val maxTokens = overview?.maxTokens
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("上下文占用", style = MaterialTheme.typography.headlineSmall)
            if (maxTokens == null) {
                Text(
                    "当前模型没有配置上下文容量。以下仅为本地估算；请在模型设置中填写容量后查看比例。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("约 ${exactTokens(estimated)} / ${exactTokens(maxTokens.toLong())} tokens · ${"%.1f".format(estimated * 100.0 / maxTokens)}%",
                    style = MaterialTheme.typography.titleSmall)
            }
            SegmentedContextBar(overview, Modifier.fillMaxWidth().height(8.dp))
            Text("本地估算 · 占已用上下文的比例", style = MaterialTheme.typography.titleSmall)
            Text("文本按字符近似估算；媒体按固定近似值计入，实际用量会受分辨率、页数和供应商处理方式影响。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (overview?.segments.isNullOrEmpty()) {
                Text("暂无可计入上下文的内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                overview.segments.forEach { segment ->
                    ContextSegmentRow(segment, estimated)
                }
            }
            overview?.compactedMessages?.takeIf { it > 0 }?.let {
                Text("已压缩 $it 条较早消息；原始记录仍会保留。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ContextUsageRecordCard(overview?.lastUsage)
            if (isCompacting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(compactionProgress ?: "正在压缩上下文…", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancelCompaction) { Text("取消") }
                }
            } else {
                OutlinedButton(onClick = { confirmCompaction = true }, enabled = canCompact, modifier = Modifier.fillMaxWidth()) {
                    Text("压缩上下文")
                }
                if (!canCompact) Text("仅空闲的主会话可以压缩上下文。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ContextSegmentRow(segment: ContextSegment, total: Long) {
    val percent = if (total > 0) segment.tokens * 100.0 / total else 0.0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(contextSegmentColor(segment.key)))
        Spacer(Modifier.width(8.dp))
        Text(segment.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("${exactTokens(segment.tokens)} · ${"%.1f".format(percent)}%", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContextUsageRecordCard(record: ContextUsageRecord?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("上次模型报告", style = MaterialTheme.typography.titleSmall)
            if (record == null) {
                Text("尚未收到供应商 token 用量。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(record.model, style = MaterialTheme.typography.labelMedium)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.recordedAt)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                UsageLine("输入", record.usage.inputTokens)
                UsageLine("输出", record.usage.outputTokens)
                UsageLine("思考（已计入输出）", record.usage.reasoningTokens)
                record.usage.cacheReadTokens?.let { UsageLine("缓存读取（已计入输入）", it) }
                record.usage.cacheWriteTokens?.let { UsageLine("缓存写入（已计入输入）", it) }
            }
        }
    }
}

@Composable
private fun UsageLine(label: String, tokens: Long?) {
    tokens?.let {
        Text("$label · ${exactTokens(it)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SegmentedContextBar(overview: ContextOverview?, modifier: Modifier = Modifier) {
    val segments = overview?.segments.orEmpty().filter { it.tokens > 0 }
    val total = overview?.estimatedTokens ?: 0L
    val capacity = overview?.maxTokens?.toLong()?.takeIf { it > 0 }
    // If no capacity is known, the visible bar is still a truthful composition of the estimate.
    val denominator = (capacity ?: total).coerceAtLeast(total).coerceAtLeast(1L)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        segments.forEach { segment ->
            Box(
                Modifier
                    .weight(segment.tokens.toFloat() / denominator)
                    .fillMaxHeight()
                    .background(contextSegmentColor(segment.key))
            )
        }
        val remaining = denominator - total
        if (remaining > 0) Spacer(Modifier.weight(remaining.toFloat() / denominator).fillMaxHeight())
    }
}

@Composable
private fun contextSegmentColor(key: String): Color = when (key) {
    "system" -> Color(0xFF6B91CA)
    "tools" -> Color(0xFF9C82C6)
    "environment" -> Color(0xFF8995A5)
    "user" -> Color(0xFF50A78F)
    "assistant" -> Color(0xFFD3A05B)
    "results" -> Color(0xFFCF7F8B)
    "attachments" -> Color(0xFF5BA9BD)
    "summary" -> Color(0xFFA0AF70)
    else -> MaterialTheme.colorScheme.outlineVariant
}

internal fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    tokens >= 1_000 -> "%.1fk".format(tokens / 1_000.0)
    else -> tokens.toString()
}

private fun effortLabel(effort: ReasoningEffort): String = "${effort.wireValue} · ${effort.label}"

private fun exactTokens(tokens: Long): String = NumberFormat.getIntegerInstance().format(tokens)
