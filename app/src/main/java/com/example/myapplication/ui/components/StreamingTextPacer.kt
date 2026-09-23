package com.example.myapplication.ui.components

import kotlin.math.ceil

/** Presentation only: never delays provider consumption, tool execution or persistence. */
internal class StreamingTextPacer(initial: String = "") {
    private var shown = initial
    private var target = initial
    private var changedAt = 0L
    private var lastStepAt = 0L

    fun next(source: String, nowMs: Long, streaming: Boolean): String {
        if (source != target) {
            target = source
            changedAt = nowMs
        }
        val elapsed = (nowMs - lastStepAt).coerceIn(16L, 64L)
        lastStepAt = nowMs
        val pending = source.length - shown.length
        // Edits, termination and very large bursts must not replay an artificial typing queue.
        if (!streaming || !source.startsWith(shown) || pending > 4096 || nowMs - changedAt >= 120L) {
            shown = source
        } else if (pending > 0) {
            var end = (shown.length + ceil(pending * elapsed / 80.0).toInt().coerceAtLeast(1))
                .coerceAtMost(source.length)
            // Never split a UTF-16 surrogate pair. Include adjacent combining marks too.
            if (end < source.length && end > 0 && Character.isHighSurrogate(source[end - 1]) &&
                Character.isLowSurrogate(source[end])) end++
            while (end < source.length && Character.getType(source.codePointAt(end)) in setOf(
                    Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
                    Character.ENCLOSING_MARK.toInt())) {
                end += Character.charCount(source.codePointAt(end))
            }
            shown = source.substring(0, end)
        }
        return shown
    }
}

/** Full parsing preserves global reference semantics; equal blocks retain Compose identity. */
internal fun reuseMarkdownBlocks(previous: List<MarkdownBlock>, next: List<MarkdownBlock>): List<MarkdownBlock> =
    next.mapIndexed { index, block -> previous.getOrNull(index)?.takeIf { it == block } ?: block }
