package com.example.myapplication.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

internal val LocalStreamingReveal = staticCompositionLocalOf { false }

internal data class RevealRange(
    val start: Int,
    val end: Int,
    val startNs: Long
)

internal class StreamingRevealTracker(
    val durationNs: Long = 220_000_000L
) {
    private var lastText: String = ""
    private val _ranges = mutableListOf<RevealRange>()
    val activeRanges: List<RevealRange> get() = _ranges

    val hasActiveRanges: Boolean
        get() = _ranges.isNotEmpty()

    fun initHistorical(text: String) {
        _ranges.clear()
        lastText = text
    }

    fun update(
        text: String,
        nowNs: Long,
        enabled: Boolean,
        isFirstBlockAppearance: Boolean = false,
        scaleFactor: Float = 1f
    ) {
        if (!enabled || scaleFactor <= 0f) {
            initHistorical(text)
            return
        }

        if (!text.startsWith(lastText)) {
            _ranges.clear()
            lastText = text
            return
        }

        if (isFirstBlockAppearance && lastText.isEmpty() && text.isNotEmpty()) {
            _ranges.add(RevealRange(start = 0, end = text.length, startNs = nowNs))
            lastText = text
            return
        }

        if (text.length > lastText.length) {
            val start = lastText.length
            val end = text.length
            _ranges.add(RevealRange(start = start, end = end, startNs = nowNs))
            lastText = text
        }
    }

    fun prune(nowNs: Long, scaleFactor: Float = 1f): Boolean {
        if (_ranges.isEmpty()) return false
        val effectiveDuration = (durationNs * scaleFactor.coerceAtLeast(0f)).toLong()
        if (effectiveDuration <= 0L) {
            _ranges.clear()
            return false
        }
        _ranges.removeAll { range ->
            nowNs - range.startNs >= effectiveDuration
        }
        return _ranges.isNotEmpty()
    }

    fun getEraseAlpha(range: RevealRange, nowNs: Long, scaleFactor: Float = 1f): Float {
        val effectiveDuration = (durationNs * scaleFactor.coerceAtLeast(0f)).toLong()
        if (effectiveDuration <= 0L) return 0f
        val elapsed = (nowNs - range.startNs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
        return 1f - progress
    }

    fun clear() {
        _ranges.clear()
        lastText = ""
    }
}

internal class StreamingTextReveal(
    val modifier: Modifier,
    val onTextLayout: (TextLayoutResult) -> Unit
)

@Composable
internal fun rememberStreamingTextReveal(text: String): StreamingTextReveal {
    val enabled = LocalStreamingReveal.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var seen by rememberSaveable { mutableStateOf(false) }
    val isFirstAppearance = !seen && enabled
    SideEffect { seen = true }

    val tracker = remember {
        StreamingRevealTracker().apply {
            if (!isFirstAppearance) {
                initHistorical(text)
            }
        }
    }

    // Active range count snapshot state read strictly inside graphicsLayer and draw closures
    val activeCount = remember { mutableIntStateOf(0) }
    var scaleFactorState by remember { mutableStateOf(1f) }
    var drawClockNs by remember { mutableStateOf(0L) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val pathCache = remember { mutableMapOf<Pair<Int, Int>, Path>() }

    // Commit ranges before drawing, but only after composition succeeds. Mutating the tracker
    // inside remember would leak changes from abandoned compositions.
    SideEffect {
        val nowNs = System.nanoTime()
        drawClockNs = nowNs
        tracker.update(
            text = text,
            nowNs = nowNs,
            enabled = enabled,
            isFirstBlockAppearance = isFirstAppearance,
            scaleFactor = scaleFactorState
        )
        activeCount.intValue = tracker.activeRanges.size
    }

    LaunchedEffect(lifecycleOwner, text, enabled) {
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        scaleFactorState = scale

        if (!enabled || !tracker.hasActiveRanges || scale <= 0f) {
            if (scale <= 0f) {
                tracker.initHistorical(text)
                activeCount.intValue = 0
            }
            return@LaunchedEffect
        }

        var resumed = false
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (resumed) {
                tracker.initHistorical(text)
                activeCount.intValue = 0
                return@repeatOnLifecycle
            }
            resumed = true

            while (isActive && tracker.hasActiveRanges) {
                withFrameNanos { frameTimeNs ->
                    drawClockNs = frameTimeNs
                    tracker.prune(frameTimeNs, scale)
                    activeCount.intValue = tracker.activeRanges.size
                }
            }
        }
    }

    val onTextLayout: (TextLayoutResult) -> Unit = remember {
        { result ->
            if (textLayoutResult !== result) {
                textLayoutResult = result
                pathCache.clear()
            }
        }
    }

    val modifier = Modifier
        .graphicsLayer {
            // Read activeCount inside layer closure to switch offscreen strategy without recomposing
            compositingStrategy = if (activeCount.intValue > 0) {
                CompositingStrategy.Offscreen
            } else {
                CompositingStrategy.Auto
            }
        }
        .drawWithContent {
            drawContent()
            if (activeCount.intValue == 0 || !enabled || scaleFactorState <= 0f) {
                if (pathCache.isNotEmpty()) pathCache.clear()
                return@drawWithContent
            }

            val layout = textLayoutResult ?: return@drawWithContent
            val currentFrameNs = drawClockNs
            if (currentFrameNs == 0L) return@drawWithContent

            val textLen = layout.layoutInput.text.length
            val ranges = tracker.activeRanges
            for (range in ranges) {
                val eraseAlpha = tracker.getEraseAlpha(range, currentFrameNs, scaleFactorState)
                if (eraseAlpha > 0f) {
                    val safeStart = range.start.coerceIn(0, textLen)
                    val safeEnd = range.end.coerceIn(0, textLen)
                    if (safeStart < safeEnd) {
                        val key = Pair(safeStart, safeEnd)
                        val path = pathCache.getOrPut(key) {
                            layout.getPathForRange(safeStart, safeEnd)
                        }
                        drawPath(
                            path = path,
                            color = Color(0f, 0f, 0f, eraseAlpha),
                            blendMode = BlendMode.DstOut
                        )
                    }
                }
            }
        }

    return remember(modifier, onTextLayout) {
        StreamingTextReveal(modifier, onTextLayout)
    }
}
