package com.example.myapplication.ui.chat

import com.example.myapplication.ui.components.inertWhen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.example.myapplication.ui.components.UiTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.DialogWindowProvider
import com.example.myapplication.data.model.ContextOverview
import com.example.myapplication.data.model.ContextSegment
import com.example.myapplication.data.model.ContextUsageRecord
import com.example.myapplication.data.model.ReasoningEffort
import com.example.myapplication.data.model.ReasoningProtocol
import com.example.myapplication.data.model.ReasoningSupport
import com.example.myapplication.provider.anthropicBudgetFor
import com.example.myapplication.provider.geminiBudgetFor
import java.text.DateFormat
import java.util.Date
import java.text.NumberFormat
import kotlin.math.roundToInt

/** Compact entry only; the sheet retains all eight categories and server-reported usage. */
@Composable
internal fun CompactContextUsage(overview: ContextOverview?, onClick: () -> Unit) {
    val capacity = overview?.maxTokens?.takeIf { it > 0 }
    val used = overview?.estimatedTokens ?: 0L
    val colors = overview?.segments.orEmpty().map { it.tokens to contextSegmentColor(it.key) }
    val track = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())
            drawArc(track, -90f, 360f, false, style = stroke)
            if (used > 0L) {
                var start = -90f
                val denominator = maxOf(capacity?.toLong() ?: used, used, 1L)
                colors.forEach { (tokens, color) ->
                    val sweep = tokens.toFloat() / denominator * 360f
                    drawArc(color, start, sweep, false, style = stroke)
                    start += sweep
                }
            }
        }
        Text(if (capacity == null) "容量未配置" else "约 ${used * 100 / capacity}%",
            fontSize = 12.sp,
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
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReasoningEffortMenu(
    support: ReasoningSupport?,
    override: ReasoningEffort?,
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
    // Imported/unsupported values still present a concrete supported session selection.
    val fallbackEffort = available.firstOrNull { it == ReasoningEffort.MEDIUM }
        ?: available[available.size / 2]
    val effective = override?.takeIf { it in available } ?: fallbackEffort
    var draftIndex by remember(override, available) {
        mutableStateOf(available.indexOf(effective))
    }
    val effectiveIndex = available.indexOf(effective)
    val previewEffort = available[draftIndex]
    val selectedLabel = effortLabel(effective)
    Box(modifier) {
        UiTextButton(onClick = { draftIndex = effectiveIndex; detailed = false; expanded = true }, enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(selectedLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ExpandMore, null, Modifier.size(16.dp))
            }
        }
        if (expanded) {
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            // Material measures the sheet inside the actual window, including Android 16
            // edge-to-edge insets. A fullscreen custom Dialog overmeasured the footer.
            ModalBottomSheet(
                onDismissRequest = { draftIndex = effectiveIndex; expanded = false },
                sheetState = sheetState,
                dragHandle = null,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = if (detailed) .32f else .15f)
            ) {
                ReasoningSheetSystemBars()
                // Animate both directions inside one inset-aware sheet. Outgoing controls
                // become inert immediately; they must not apply a second selection mid-exit.
                AnimatedContent(
                    targetState = detailed,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 60)) + slideInVertically(tween(240)) { it / 20 })
                            .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 30 })
                            .using(SizeTransform(clip = true) { _, _ -> tween(280) })
                    },
                    label = "reasoningPanelMode"
                ) { showDetails ->
                val panelModifier = if (showDetails) {
                    Modifier.fillMaxWidth().fillMaxHeight(.9f)
                } else {
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 64.dp).fillMaxWidth()
                }
                Surface(
                    modifier = panelModifier.inertWhen(showDetails != detailed),
                    shape = if (showDetails) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(22.dp),
                    color = if (showDetails) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
                    border = if (showDetails) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (showDetails) {
                        ReasoningEffortDetails(
                            support, available, previewEffort, enabled,
                            onChange = { effort ->
                                draftIndex = available.indexOf(effort)
                                onChange(effort)
                                detailed = false
                            },
                            onClose = { draftIndex = effectiveIndex; expanded = false }
                        )
                    } else {
                    Column(Modifier.heightIn(max = 540.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.AutoAwesome, null,
                                Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            UiTextButton(onClick = { detailed = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            effortLabel(previewEffort),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, null,
                                            Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    Text("当前会话档位 · 查看全部档位",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            androidx.compose.material3.IconButton(onClick = {
                                draftIndex = available.indexOf(fallbackEffort)
                                onChange(fallbackEffort)
                            }, enabled = enabled) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Refresh, "重置为默认思考强度",
                                    Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DiscreteReasoningSlider(
                            available = available,
                            selectedIndex = draftIndex,
                            enabled = enabled,
                            onPreview = { draftIndex = it },
                            onCommit = { index ->
                                draftIndex = index
                                onChange(available[index])
                            }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(effortLabel(available.first()), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(effortLabel(available.last()), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(reasoningWireField(support, previewEffort), modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                            style = MaterialTheme.typography.labelMedium, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("拖动后松手应用；点上方文字查看全部字段值。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun ReasoningEffortDetails(
    support: ReasoningSupport?,
    available: List<ReasoningEffort>,
    selectedEffort: ReasoningEffort,
    enabled: Boolean,
    onChange: (ReasoningEffort) -> Unit,
    onClose: () -> Unit
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(Unit) { scrollState.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("思考强度", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            androidx.compose.material3.IconButton(onClick = onClose) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, "关闭", Modifier.size(22.dp))
            }
        }
        Text("为当前会话选择思考档位。档位只来自当前模型支持列表。", Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp)
                .selectableGroup()
        ) {
            available.forEach { effort ->
                ReasoningEffortOption(
                    title = effort.label,
                    wireValue = effort.wireValue,
                    detail = reasoningWireField(support, effort),
                    selected = selectedEffort == effort,
                    enabled = enabled,
                    onClick = { onChange(effort) }
                )
            }
            Text("执行中调整只影响下一次模型请求。", Modifier.padding(top = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

    }
}

@Composable
private fun ReasoningEffortOption(
    title: String,
    wireValue: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(horizontal = 2.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(wireValue, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(detail, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
    HorizontalDivider()
}

@Composable
private fun ReasoningSheetSystemBars() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val lightBackground = MaterialTheme.colorScheme.background.luminance() > .5f
    // Material 3 1.3 follows the system theme here; honor the app's manual theme too.
    DisposableEffect(window, lightBackground) {
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        val originalStatus = controller?.isAppearanceLightStatusBars
        val originalNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = lightBackground
        controller?.isAppearanceLightNavigationBars = lightBackground
        onDispose {
            if (originalStatus != null) controller?.isAppearanceLightStatusBars = originalStatus
            if (originalNavigation != null) controller?.isAppearanceLightNavigationBars = originalNavigation
        }
    }
}

/** Material owns accessible input; its slots draw the compact prototype track and thumb. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiscreteReasoningSlider(
    available: List<ReasoningEffort>,
    selectedIndex: Int,
    enabled: Boolean,
    onPreview: (Int) -> Unit,
    onCommit: (Int) -> Unit
) {
    val count = available.size
    // An external reset must also discard any preview left by a cancelled gesture.
    var pendingCommitIndex by remember(selectedIndex, available) { mutableStateOf<Int?>(null) }
    val fraction = if (count <= 1) 0f else selectedIndex.toFloat() / (count - 1)
    val inactiveTrack = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 1f else .55f)
    val activeTrack = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .45f)
    val activeTick = MaterialTheme.colorScheme.onPrimary.copy(alpha = .38f)
    val inactiveTick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        // Material's thumb travels from 17dp to width-17dp. Draw the complete pill
        // behind it, with ticks at those same centers, outside the clipped track slot.
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 28.dp.toPx()
            val top = (size.height - trackHeight) / 2
            val inset = 17.dp.toPx().coerceAtMost(size.width / 2)
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            val corners = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
            drawRoundRect(inactiveTrack, androidx.compose.ui.geometry.Offset(0f, top),
                androidx.compose.ui.geometry.Size(size.width, trackHeight), corners)
            val fill = inset + (size.width - 2 * inset) * fraction
            // Clip the complete pill at the thumb center. Rounding a shorter pill
            // creates a visible rounded gap beside intermediate slider positions.
            clipRect(
                left = if (rtl) size.width - fill else 0f,
                right = if (rtl) size.width else fill
            ) {
                drawRoundRect(activeTrack, androidx.compose.ui.geometry.Offset(0f, top),
                    androidx.compose.ui.geometry.Size(size.width, trackHeight), corners)
            }
            repeat(count) { index ->
                val progress = if (count <= 1) 0f else index.toFloat() / (count - 1)
                val position = inset + (size.width - 2 * inset) * progress
                drawCircle(if (index <= selectedIndex) activeTick else inactiveTick, 2.dp.toPx(),
                    androidx.compose.ui.geometry.Offset(if (rtl) size.width - position else position, size.height / 2))
            }
        }
    Slider(
        value = selectedIndex.toFloat(),
        onValueChange = { next ->
            pendingCommitIndex = next.roundToInt().coerceIn(0, count - 1)
            onPreview(requireNotNull(pendingCommitIndex))
        },
        onValueChangeFinished = {
            val finalIndex = pendingCommitIndex ?: selectedIndex
            pendingCommitIndex = null
            onCommit(finalIndex)
        },
        enabled = enabled,
        valueRange = 0f..(count - 1).toFloat(),
        steps = (count - 2).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth().height(52.dp).semantics {
            contentDescription = "思考强度"
            stateDescription = effortLabel(available[selectedIndex])
        },
        thumb = {
            Box(
                Modifier
                    .size(34.dp)
                    .shadow(4.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
            )
        },
        track = { Spacer(Modifier.fillMaxWidth().height(28.dp)) }
    )
    }
}

private fun reasoningWireField(support: ReasoningSupport?, effort: ReasoningEffort?): String {
    if (effort == null) return "当前未发送思考字段"
    return when (support?.protocol) {
        ReasoningProtocol.OPENAI_CHAT_COMPLETIONS -> "reasoning_effort = \"${effort.wireValue}\""
        ReasoningProtocol.ANTHROPIC_ADAPTIVE -> if (effort == ReasoningEffort.NONE) {
            "thinking.type = \"disabled\""
        } else {
            "thinking.type = \"adaptive\" · output_config.effort = \"${effort.wireValue}\""
        }
        ReasoningProtocol.ANTHROPIC_MANUAL -> if (effort == ReasoningEffort.NONE) {
            "thinking.type = \"disabled\""
        } else {
            "thinking.type = \"enabled\" · thinking.budget_tokens = ${anthropicBudgetFor(effort)}"
        }
        ReasoningProtocol.GEMINI_THINKING_LEVEL -> "generationConfig.thinkingConfig.thinkingLevel = \"${effort.wireValue}\""
        ReasoningProtocol.GEMINI_THINKING_BUDGET -> support.modelId.takeIf { it.isNotBlank() }?.let { modelId ->
            "generationConfig.thinkingConfig.thinkingBudget = ${geminiBudgetFor(modelId, effort)}"
        } ?: "generationConfig.thinkingConfig.thinkingBudget"
        ReasoningProtocol.UNSUPPORTED, null -> "当前适配器不会发送思考字段"
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
    onDismiss: () -> Unit,
    feedback: androidx.compose.material3.SnackbarHostState? = null
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
        com.example.myapplication.ui.components.UiScaffold(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.92f),
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = { Column(Modifier.padding(horizontal = 22.dp)) { PanelHeading("上下文占用", onDismiss) } },
            snackbarHost = { feedback?.let { com.example.myapplication.ui.components.TopFeedbackHost(it) } }
        ) { panelPadding ->
            Column(Modifier.fillMaxSize().padding(panelPadding).padding(horizontal = 22.dp)) {
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
