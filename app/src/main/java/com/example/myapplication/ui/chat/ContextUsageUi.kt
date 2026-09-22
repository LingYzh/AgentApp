package com.example.myapplication.ui.chat

import com.example.myapplication.ui.components.inertWhen
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
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
import com.example.myapplication.ui.components.UiTextButton
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

/** Compact entry only; the sheet retains all eight categories and server-reported usage. */
@Composable
internal fun CompactContextUsage(overview: ContextOverview?, onClick: () -> Unit) {
    val capacity = overview?.maxTokens?.takeIf { it > 0 }
    val used = overview?.estimatedTokens ?: 0L
    val colors = overview?.segments.orEmpty().map { it.tokens to contextSegmentColor(it.key) }
    val track = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(13.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())
            drawArc(track, -90f, 360f, false, style = stroke)
            if (capacity != null) {
                var start = -90f
                val denominator = maxOf(capacity.toLong(), used, 1L)
                colors.forEach { (tokens, color) ->
                    val sweep = tokens.toFloat() / denominator * 360f
                    drawArc(color, start, sweep, false, style = stroke)
                    start += sweep
                }
            }
        }
        Text(if (capacity == null) "容量未配置" else "约 ${used * 100 / capacity}%",
            fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
            .heightIn(min = 48.dp)
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
    onChange: (ReasoningEffort?) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    val available = support?.efforts.orEmpty()
    if (available.isEmpty()) {
        UiTextButton(onClick = {}, enabled = false, modifier = modifier) { Text("思考 · 不支持", style = MaterialTheme.typography.labelSmall) }
        return
    }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var detailed by remember { mutableStateOf(false) }
    var slider by remember(override, available) { mutableStateOf((available.indexOf(override) + 1).toFloat()) }
    val selectedLabel = override?.wireValue ?: modelDefault?.let { "跟随 · ${it.wireValue}" } ?: "跟随配置"
    Box(modifier) {
        UiTextButton(onClick = { detailed = false; expanded = true }, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(selectedLabel, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ExpandMore, null, Modifier.size(16.dp))
            }
        }
        if (expanded) androidx.compose.ui.window.Dialog(onDismissRequest = { expanded = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { expanded = false },
                contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 64.dp).fillMaxWidth()
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            androidx.compose.animation.AnimatedContent(detailed, label = "reasoning detail") { details ->
                Column(Modifier.inertWhen(details != detailed || !expanded).heightIn(max = 540.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (details) {
                        Text("思考强度", style = MaterialTheme.typography.titleMedium)
                        Text(support?.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Text("适配字段：${when (support?.protocol) {
                            com.example.myapplication.data.model.ReasoningProtocol.OPENAI_CHAT_COMPLETIONS -> "reasoning_effort"
                            com.example.myapplication.data.model.ReasoningProtocol.ANTHROPIC_ADAPTIVE -> "thinking / output_config.effort"
                            com.example.myapplication.data.model.ReasoningProtocol.ANTHROPIC_MANUAL -> "thinking.budget_tokens"
                            com.example.myapplication.data.model.ReasoningProtocol.GEMINI_THINKING_LEVEL -> "thinkingConfig.thinkingLevel"
                            com.example.myapplication.data.model.ReasoningProtocol.GEMINI_THINKING_BUDGET -> "thinkingConfig.thinkingBudget"
                            else -> "由当前适配器决定"
                        }}", style = MaterialTheme.typography.labelSmall)
                        UiTextButton(onClick = { onChange(null); expanded = false }) { Text("跟随模型配置 · null") }
                        available.forEach { effort ->
                            UiTextButton(onClick = { onChange(effort); expanded = false }) { Text(effortLabel(effort)) }
                        }
                        UiTextButton(onClick = { detailed = false }) { Text("返回滑块") }
                    } else {
                        val preview = available.getOrNull(slider.toInt() - 1)
                        UiTextButton(onClick = { detailed = true }) {
                            Text(preview?.let(::effortLabel) ?: "跟随模型配置 · null")
                        }
                        androidx.compose.material3.Slider(
                            value = slider,
                            onValueChange = { slider = it },
                            onValueChangeFinished = { onChange(available.getOrNull(slider.toInt() - 1)) },
                            valueRange = 0f..available.size.toFloat(), steps = (available.size - 1).coerceAtLeast(0),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        Text("拖动后松手应用；点上方文字查看完整字段值。", style = MaterialTheme.typography.bodySmall)
                        UiTextButton(onClick = { slider = 0f; onChange(null) }) { Text("重置为跟随模型") }
                    }
                }
            }
            }
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
                UiTextButton(onClick = { confirmCompaction = false; onCompact() }) { Text("开始压缩") }
            },
            dismissButton = { UiTextButton(onClick = { confirmCompaction = false }) { Text("取消") } }
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val estimated = overview?.estimatedTokens ?: 0L
        val maxTokens = overview?.maxTokens
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 22.dp)) {
            PanelHeading("上下文占用", onDismiss)
            Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("用不同颜色区分内容来源，空余容量保持中性。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("本地估算 · 非计费数据", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(exactTokens(estimated), fontSize = 36.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(" tokens", Modifier.padding(bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("内容类型", style = MaterialTheme.typography.labelSmall)
                Text("Token · 占已用比例", style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
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
            Spacer(Modifier.height(12.dp))
            }
            HorizontalDivider()
            Column(Modifier.padding(vertical = 12.dp)) {
            if (isCompacting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(compactionProgress ?: "正在压缩上下文…", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    UiTextButton(onClick = onCancelCompaction) { Text("取消") }
                }
            } else {
                OutlinedButton(onClick = { confirmCompaction = true }, enabled = canCompact, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("压缩上下文")
                }
                if (!canCompact) Text("仅空闲的主会话可以压缩上下文。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    val total = segments.sumOf { it.tokens }
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

private fun effortLabel(effort: ReasoningEffort): String = "${effort.label} · ${effort.wireValue}"

private fun exactTokens(tokens: Long): String = NumberFormat.getIntegerInstance().format(tokens)
