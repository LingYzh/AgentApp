package com.example.myapplication.ui.components

import kotlin.math.max

/** Presentation only: never delays provider consumption, tool execution or persistence. */
internal class StreamingTextPacer(initial: String = "") {
    private var shown: String = initial
    private var target: String = initial

    private var lastTickMs: Long = Long.MIN_VALUE
    private var lastPlaybackMs: Long = Long.MIN_VALUE

    private var bufferedUntil: Long = Long.MIN_VALUE
    private var burstChars: Int = 0
    private var lastChunkArrivalMs: Long = Long.MIN_VALUE

    private var estimatedInputRate: Double = 0.0
    private var characterBudget: Double = 0.0

    private var finishStartTime: Long = Long.MIN_VALUE

    val hasPending: Boolean
        get() = shown.length < target.length

    /** Slow sources use small, spaced batches; fast sources publish larger batches sooner. */
    val updateIntervalMs: Long
        get() = when {
            estimatedInputRate < 0.08 -> 140L
            estimatedInputRate < 0.24 -> 110L
            else -> 90L
        }

    fun next(
        source: String,
        nowMs: Long,
        streaming: Boolean,
        immediate: Boolean = false
    ): String {
        val rawElapsed = if (lastTickMs == Long.MIN_VALUE) 0L else (nowMs - lastTickMs)
        val isLongTickGap = lastTickMs != Long.MIN_VALUE && rawElapsed > 1000L
        lastTickMs = nowMs

        // Non-append replacement, truncation, huge burst, or long tick gap must immediately sync.
        val isReplacementOrTruncation = !source.startsWith(target)
        val pendingChars = source.length - shown.length

        if (immediate || isReplacementOrTruncation || pendingChars > 4096 || isLongTickGap) {
            shown = source
            target = source
            bufferedUntil = Long.MIN_VALUE
            burstChars = 0
            lastChunkArrivalMs = Long.MIN_VALUE
            estimatedInputRate = 0.0
            characterBudget = 0.0
            finishStartTime = Long.MIN_VALUE
            lastPlaybackMs = nowMs
            return shown
        }

        val wasExhausted = shown.length >= target.length
        if (source != target) {
            val deltaChars = source.length - target.length
            if (deltaChars > 0) {
                if (streaming && wasExhausted) {
                    bufferedUntil = nowMs + 200L
                    burstChars = deltaChars
                    lastChunkArrivalMs = nowMs
                    lastPlaybackMs = Long.MIN_VALUE
                    finishStartTime = Long.MIN_VALUE
                } else {
                    val intervalMs = if (lastChunkArrivalMs == Long.MIN_VALUE) 0L else (nowMs - lastChunkArrivalMs)
                    lastChunkArrivalMs = nowMs
                    burstChars += deltaChars
                    if (intervalMs > 0L) {
                        val instantRate = deltaChars.toDouble() / intervalMs
                        estimatedInputRate = if (estimatedInputRate <= 0.0) {
                            instantRate
                        } else {
                            0.3 * instantRate + 0.7 * estimatedInputRate
                        }
                    }
                }
            }
            target = source
        }

        if (!streaming) {
            bufferedUntil = Long.MIN_VALUE
        }

        if (streaming && bufferedUntil != Long.MIN_VALUE && nowMs < bufferedUntil) {
            return shown
        }

        val playbackElapsedMs = if (bufferedUntil != Long.MIN_VALUE && nowMs >= bufferedUntil) {
            val elapsed = (nowMs - bufferedUntil).coerceIn(0L, 1000L)
            bufferedUntil = Long.MIN_VALUE
            lastPlaybackMs = nowMs
            elapsed
        } else if (lastPlaybackMs == Long.MIN_VALUE) {
            lastPlaybackMs = nowMs
            0L
        } else {
            val elapsed = (nowMs - lastPlaybackMs).coerceIn(0L, 1000L)
            lastPlaybackMs = nowMs
            elapsed
        }

        if (shown.length >= target.length) {
            characterBudget = 0.0
            finishStartTime = Long.MIN_VALUE
            return shown
        }

        val playbackRate = if (streaming) {
            if (estimatedInputRate <= 0.0) {
                // The initial observation window is fixed, independent of polling cadence.
                estimatedInputRate = (burstChars / 200.0).coerceAtLeast(0.015)
            }
            val baseRate = estimatedInputRate.coerceAtLeast(0.015)
            val currentPending = target.length - shown.length
            val steadyBacklog = baseRate * 320.0
            val excessBacklog = (currentPending - steadyBacklog).coerceAtLeast(0.0)
            // A small reserve absorbs arrival jitter and gives each reveal time to become legible.
            baseRate * 0.9 + (excessBacklog / 600.0)
        } else {
            if (finishStartTime == Long.MIN_VALUE) {
                finishStartTime = nowMs
            }
            val elapsedFinish = nowMs - finishStartTime
            val remainingMs = 200L - elapsedFinish
            if (remainingMs <= 0L) {
                shown = target
                characterBudget = 0.0
                finishStartTime = Long.MIN_VALUE
                return shown
            }
            val remainingChars = target.length - shown.length
            val finishRate = remainingChars.toDouble() / remainingMs
            val baseRate = if (estimatedInputRate > 0.0) estimatedInputRate else 0.015
            max(finishRate, baseRate)
        }

        if (playbackElapsedMs > 0L) {
            characterBudget += playbackRate * playbackElapsedMs
        }

        val charsToEmit = characterBudget.toInt()
        if (charsToEmit <= 0) {
            return shown
        }

        val safeLen = if (streaming) getSafeStreamingLength(target) else target.length
        if (safeLen <= shown.length) {
            return shown
        }

        var desiredEnd = (shown.length + charsToEmit).coerceAtMost(safeLen)
        while (desiredEnd < safeLen && !isBoundary(target, desiredEnd)) {
            desiredEnd++
        }

        if (desiredEnd > shown.length) {
            val actuallyEmitted = desiredEnd - shown.length
            characterBudget -= actuallyEmitted
            if (characterBudget < -4.0) characterBudget = -4.0
            shown = target.substring(0, desiredEnd)
        }

        if (shown.length >= target.length) {
            characterBudget = 0.0
            finishStartTime = Long.MIN_VALUE
        }

        return shown
    }
}

/** Returns safe length of source excluding incomplete grapheme clusters at the end during streaming. */
internal fun getSafeStreamingLength(source: String): Int {
    if (source.isEmpty()) return 0

    var end = source.length

    if (Character.isHighSurrogate(source[end - 1])) {
        end--
    }

    if (end > 0 && source[end - 1] == '\u200D') {
        end--
        while (end > 0) {
            val cp = Character.codePointBefore(source, end)
            end -= Character.charCount(cp)
            if (end > 0 && source[end - 1] == '\u200D') {
                end--
            } else if (end > 0 && !isBoundary(source, end)) {
                continue
            } else {
                break
            }
        }
    }

    var riCount = 0
    var p = end
    while (p > 0) {
        val cp = Character.codePointBefore(source, p)
        if (isRegionalIndicator(cp)) {
            riCount++
            p -= Character.charCount(cp)
        } else {
            break
        }
    }
    if (riCount % 2 != 0) {
        end -= 2
    }

    while (end > 0 && !isBoundary(source, end)) {
        end--
    }

    return end.coerceAtLeast(0)
}

internal fun isBoundary(s: CharSequence, index: Int): Boolean {
    if (index <= 0 || index >= s.length) return true
    if (Character.isLowSurrogate(s[index]) && Character.isHighSurrogate(s[index - 1])) {
        return false
    }
    val cp = Character.codePointAt(s, index)
    if (isCombiningMark(cp)) return false
    if (isVariationSelector(cp)) return false
    if (isEmojiModifier(cp)) return false
    if (isTagSpec(cp)) return false
    if (cp == 0x200D) return false
    if (s[index - 1] == '\u200D') return false

    if (isRegionalIndicator(cp)) {
        var prevRiCount = 0
        var p = index
        while (p > 0) {
            val prevCp = Character.codePointBefore(s, p)
            if (isRegionalIndicator(prevCp)) {
                prevRiCount++
                p -= Character.charCount(prevCp)
            } else {
                break
            }
        }
        if (prevRiCount % 2 != 0) return false
    }
    return true
}

private fun isCombiningMark(cp: Int): Boolean {
    val type = Character.getType(cp)
    return type == Character.NON_SPACING_MARK.toInt() ||
           type == Character.COMBINING_SPACING_MARK.toInt() ||
           type == Character.ENCLOSING_MARK.toInt()
}

private fun isVariationSelector(cp: Int): Boolean =
    (cp in 0xFE00..0xFE0F) || (cp in 0xE0100..0xE01EF)

private fun isEmojiModifier(cp: Int): Boolean =
    cp in 0x1F3FB..0x1F3FF

private fun isRegionalIndicator(cp: Int): Boolean =
    cp in 0x1F1E6..0x1F1FF

private fun isTagSpec(cp: Int): Boolean =
    cp in 0xE0020..0xE007F

/** Full parsing preserves global reference semantics; equal blocks retain Compose identity. */
internal fun reuseMarkdownBlocks(previous: List<MarkdownBlock>, next: List<MarkdownBlock>): List<MarkdownBlock> =
    next.mapIndexed { index, block -> previous.getOrNull(index)?.takeIf { it == block } ?: block }
