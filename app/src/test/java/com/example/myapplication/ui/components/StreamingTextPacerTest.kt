package com.example.myapplication.ui.components

import org.junit.Assert.*
import org.junit.Test

class StreamingTextPacerTest {

    @Test
    fun initialSnapshotIsNotReplayed() {
        val initialText = "Existing historical message snapshot"
        val pacer = StreamingTextPacer(initialText)

        assertFalse(pacer.hasPending)
        val visible = pacer.next(initialText, 0L, streaming = true)
        assertEquals(initialText, visible)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun initialBufferDelaysOutputBy200MillisecondsThenPlaysSmoothly() {
        val pacer = StreamingTextPacer()
        val source = "Hello, world! This is a smoothly streamed response."

        assertEquals("", pacer.next(source, 0L, streaming = true))
        assertTrue(pacer.hasPending)
        assertEquals("", pacer.next(source, 60L, streaming = true))
        assertEquals("", pacer.next(source, 120L, streaming = true))
        assertEquals("", pacer.next(source, 199L, streaming = true))

        val at200 = pacer.next(source, 200L, streaming = true)
        assertEquals("", at200)

        val at250 = pacer.next(source, 250L, streaming = true)
        assertTrue(at250.isNotEmpty())
        assertTrue(source.startsWith(at250))
        assertTrue(pacer.hasPending)

        var previous = at250
        for (time in 280L..1500L step 32L) {
            val visible = pacer.next(source, time, streaming = true)
            assertTrue(visible.startsWith(previous))
            assertTrue(source.startsWith(visible))
            previous = visible
            if (!pacer.hasPending) break
        }
        assertEquals(source, previous)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun continuousSteadyInputPacesMonotonicallyAndBoundsLatency() {
        val pacer = StreamingTextPacer()
        var source = ""
        var previous = ""

        for (tick in 0..100) {
            val nowMs = tick * 32L
            val chunkIndex = (nowMs / 100L).toInt()
            val expectedTotalChars = (chunkIndex.coerceAtMost(20)) * 10
            if (expectedTotalChars > source.length) {
                source = "A".repeat(expectedTotalChars)
            }

            val visible = pacer.next(source, nowMs, streaming = true)
            assertTrue("Visible text must advance monotonically", visible.startsWith(previous))
            assertTrue("Visible text must be a prefix of source", source.startsWith(visible))

            val pending = source.length - visible.length
            if (nowMs >= 300L && source.length > 0) {
                assertTrue("Backlog ($pending) must remain bounded", pending <= 70)
            }
            previous = visible
        }

        for (tick in 101..160) {
            val nowMs = tick * 32L
            val visible = pacer.next(source, nowMs, streaming = true)
            assertTrue(visible.startsWith(previous))
            previous = visible
            if (!pacer.hasPending) break
        }
        assertEquals(source, previous)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun pauseDrainsAtCurrentRateAndWaitsThenRebuffersOnNewInput() {
        val pacer = StreamingTextPacer()
        val firstPart = "First segment of upstream generation."

        pacer.next(firstPart, 0L, streaming = true)

        var visible = pacer.next(firstPart, 250L, streaming = true)
        assertTrue(visible.isNotEmpty())

        var drainedAt = -1L
        for (time in 282L..2000L step 32L) {
            val nextVisible = pacer.next(firstPart, time, streaming = true)
            assertTrue(nextVisible.startsWith(visible))
            visible = nextVisible
            if (visible == firstPart && !pacer.hasPending) {
                drainedAt = time
                break
            }
        }
        assertTrue("Backlog must eventually drain", drainedAt > 0L)
        assertFalse(pacer.hasPending)

        val idleTime = drainedAt + 300L
        assertEquals(firstPart, pacer.next(firstPart, idleTime, streaming = true))
        assertFalse(pacer.hasPending)

        val fullText = firstPart + " Second segment after pause."
        val newArrivalMs = idleTime + 32L
        assertEquals(firstPart, pacer.next(fullText, newArrivalMs, streaming = true))
        assertTrue(pacer.hasPending)

        assertEquals(firstPart, pacer.next(fullText, newArrivalMs + 100L, streaming = true))
        assertEquals(firstPart, pacer.next(fullText, newArrivalMs + 199L, streaming = true))

        val resumed = pacer.next(fullText, newArrivalMs + 250L, streaming = true)
        assertTrue(resumed.length > firstPart.length)
        assertTrue(fullText.startsWith(resumed))
    }

    @Test
    fun briefPauseWithoutExhaustionDoesNotRebuffer() {
        val pacer = StreamingTextPacer()
        val initialBurst = "A".repeat(100)

        pacer.next(initialBurst, 0L, streaming = true)
        val at250 = pacer.next(initialBurst, 250L, streaming = true)
        assertTrue("Characters playing", at250.isNotEmpty() && at250.length < 100)
        assertTrue(pacer.hasPending)

        val extendedBurst = "A".repeat(150)
        pacer.next(extendedBurst, 260L, streaming = true)

        val at292 = pacer.next(extendedBurst, 292L, streaming = true)
        assertTrue("Must continue advancing without 200ms re-buffer", at292.length > at250.length)
    }

    @Test
    fun sparseShortTextDoesNotDiluteRateToZero() {
        val pacer = StreamingTextPacer()
        val text = "Hi"

        pacer.next(text, 0L, streaming = true)

        for (time in 32L..192L step 32L) {
            pacer.next(text, time, streaming = true)
        }

        var emitted = ""
        for (time in 200L..500L step 32L) {
            emitted = pacer.next(text, time, streaming = true)
            if (emitted == text) break
        }
        assertEquals("Sparse short text must complete without stalling", text, emitted)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun normalFinishDrainsSmoothlyWithin200Milliseconds() {
        val pacer = StreamingTextPacer()
        val text = "Long finishing message that was partially played before completion."

        pacer.next(text, 0L, streaming = true)
        val partiallyShown = pacer.next(text, 250L, streaming = true)
        assertTrue(partiallyShown.isNotEmpty() && partiallyShown.length < text.length)
        assertTrue(pacer.hasPending)

        val atFinish = pacer.next(text, 260L, streaming = false)
        assertTrue("Must not dump all backlog instantly on tick 1", atFinish.length < text.length)
        assertTrue(pacer.hasPending)

        var previous = atFinish
        for (time in 292L..450L step 32L) {
            val visible = pacer.next(text, time, streaming = false)
            assertTrue(visible.startsWith(previous))
            previous = visible
        }

        val fullyDrained = pacer.next(text, 460L, streaming = false)
        assertEquals(text, fullyDrained)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun nonStreamingFinishStartingAtTimeZeroDrainsWithin200Milliseconds() {
        val pacer = StreamingTextPacer()
        val text = "A".repeat(100)

        val at0 = pacer.next(text, 0L, streaming = false)
        assertTrue("Must not dump all 100 characters on first tick at t=0", at0.length < 100)
        assertTrue(pacer.hasPending)

        var previous = at0
        for (time in 32L..192L step 32L) {
            val visible = pacer.next(text, time, streaming = false)
            assertTrue("Text must advance monotonically from 0L", visible.startsWith(previous))
            assertTrue(visible.length >= previous.length)
            previous = visible
        }

        val at200 = pacer.next(text, 200L, streaming = false)
        assertEquals(text, at200)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun replacementOrTruncationDuringBufferingSyncsImmediately() {
        val pacer = StreamingTextPacer()
        val originalTarget = "Original target text that was buffering"

        val at0 = pacer.next(originalTarget, 0L, streaming = true)
        assertEquals("", at0)
        assertTrue(pacer.hasPending)

        val replaced = "Replaced completely new content"
        val at50 = pacer.next(replaced, 50L, streaming = true)
        assertEquals(replaced, at50)
        assertFalse(pacer.hasPending)

        val pacerTrunc = StreamingTextPacer()
        pacerTrunc.next("Long text being buffered", 0L, streaming = true)
        assertEquals("", pacerTrunc.next("Long text being buffered", 50L, streaming = true))
        val truncated = pacerTrunc.next("Long", 80L, streaming = true)
        assertEquals("Long", truncated)
        assertFalse(pacerTrunc.hasPending)
    }

    @Test
    fun bufferDurationIsNotCountedIntoPlaybackBudgetWhenLeavingBuffer() {
        val pacerJump = StreamingTextPacer()
        val pacerStep = StreamingTextPacer()
        val text = "A".repeat(100)

        // PacerJump: starts at 0L (buffers until 200L), then jumps directly to 232L
        pacerJump.next(text, 0L, streaming = true)
        val visibleJump = pacerJump.next(text, 232L, streaming = true)

        // PacerStep: starts at 0L, ticks at 200L (buffer expires), then ticks at 232L
        pacerStep.next(text, 0L, streaming = true)
        val visibleAt200 = pacerStep.next(text, 200L, streaming = true)
        assertEquals("", visibleAt200)
        val visibleStep = pacerStep.next(text, 232L, streaming = true)

        // Invariant: whether jumping directly to 232L or stepping through 200L,
        // exactly 32ms of playback time has elapsed since buffer expired.
        assertEquals(visibleStep.length, visibleJump.length)

        // Physical upper bound: at 100 chars / 232ms (~0.43 chars/ms),
        // 32ms budgets around 14 chars, which is far below full text (100).
        assertTrue(
            "Output must reflect only ~32ms of playback (~14 chars), not full text (${visibleJump.length})",
            visibleJump.length in 10..25
        )
    }

    @Test
    fun immediateCancellationAndReplacementAndHugeBurst() {
        val pacer = StreamingTextPacer()
        val text = "Some streaming text."

        pacer.next(text, 0L, streaming = true)

        val cancelled = pacer.next(text, 50L, streaming = false, immediate = true)
        assertEquals(text, cancelled)
        assertFalse(pacer.hasPending)

        val replaced = pacer.next("Completely new content", 82L, streaming = true)
        assertEquals("Completely new content", replaced)
        assertFalse(pacer.hasPending)

        val truncated = pacer.next("Short", 114L, streaming = true)
        assertEquals("Short", truncated)
        assertFalse(pacer.hasPending)

        val huge = "B".repeat(5000)
        val hugeResult = pacer.next(huge, 146L, streaming = true)
        assertEquals(huge, hugeResult)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun longTickGapSyncsToSourceDirectly() {
        val pacer = StreamingTextPacer()
        val text = "Content being streamed when user backgrounded app."

        pacer.next(text, 0L, streaming = true)
        pacer.next(text, 250L, streaming = true)
        assertTrue(pacer.hasPending)

        val resumed = pacer.next(text, 1750L, streaming = true)
        assertEquals("Must sync to source on long tick gap", text, resumed)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun unicodePreservesClustersAndWaitsForIncompleteEnd() {
        val pacer = StreamingTextPacer()
        val complexUnicode = "Text 😀 e\u0301 \u2764\uFE0F 👍🏽 👨‍👩‍👧‍👦 🇨🇳 end."

        pacer.next(complexUnicode, 0L, streaming = true)

        var previous = ""
        for (time in 200L..2500L step 32L) {
            val visible = pacer.next(complexUnicode, time, streaming = true)
            assertFalse(
                "Visible must not end with an isolated high surrogate",
                visible.lastOrNull()?.let { Character.isHighSurrogate(it) } ?: false
            )
            assertFalse(
                "Visible must not end with a dangling ZWJ",
                visible.endsWith("\u200D")
            )
            assertTrue(visible.startsWith(previous))
            previous = visible
            if (!pacer.hasPending) break
        }
        assertEquals(complexUnicode, previous)

        val pacerFinishIncomplete = StreamingTextPacer()
        val finishedWithIncomplete = pacerFinishIncomplete.next("Broken \uD83D", 0L, streaming = false, immediate = true)
        assertEquals("Broken \uD83D", finishedWithIncomplete)
    }

    @Test
    fun trailingIncompleteClusterWaitsUntilCompletedAcrossTime() {
        val pacerZwj = StreamingTextPacer()
        val danglingZwj = "Prefix \uD83D\uDC68\u200D"

        pacerZwj.next(danglingZwj, 0L, streaming = true)
        assertEquals("", pacerZwj.next(danglingZwj, 200L, streaming = true))
        assertEquals("Prefix ", pacerZwj.next(danglingZwj, 500L, streaming = true))
        assertEquals("Prefix ", pacerZwj.next(danglingZwj, 800L, streaming = true))
        assertTrue(pacerZwj.hasPending)

        val completedZwj = "Prefix \uD83D\uDC68\u200D\uD83D\uDC69"
        pacerZwj.next(completedZwj, 832L, streaming = true)
        var visible = ""
        for (time in 864L..1500L step 32L) {
            visible = pacerZwj.next(completedZwj, time, streaming = true)
            if (!pacerZwj.hasPending) break
        }
        assertEquals(completedZwj, visible)
        assertFalse(pacerZwj.hasPending)

        val pacerRi = StreamingTextPacer()
        val singleRi = "Prefix \uD83C\uDDE8"
        pacerRi.next(singleRi, 0L, streaming = true)
        assertEquals("", pacerRi.next(singleRi, 200L, streaming = true))
        assertEquals("Prefix ", pacerRi.next(singleRi, 500L, streaming = true))
        assertEquals("Prefix ", pacerRi.next(singleRi, 800L, streaming = true))
        assertTrue(pacerRi.hasPending)

        val completeFlag = "Prefix \uD83C\uDDE8\uD83C\uDDF3"
        pacerRi.next(completeFlag, 832L, streaming = true)
        for (time in 864L..1500L step 32L) {
            visible = pacerRi.next(completeFlag, time, streaming = true)
            if (!pacerRi.hasPending) break
        }
        assertEquals("Prefix 🇨🇳", visible)
        assertFalse(pacerRi.hasPending)

        val pacerSurrogate = StreamingTextPacer()
        val trailingHighSurrogate = "Prefix \uD83D"
        pacerSurrogate.next(trailingHighSurrogate, 0L, streaming = true)
        assertEquals("", pacerSurrogate.next(trailingHighSurrogate, 200L, streaming = true))
        assertEquals("Prefix ", pacerSurrogate.next(trailingHighSurrogate, 500L, streaming = true))
        assertEquals("Prefix ", pacerSurrogate.next(trailingHighSurrogate, 800L, streaming = true))
        assertTrue(pacerSurrogate.hasPending)

        val completeSurrogate = "Prefix \uD83D\uDE00"
        pacerSurrogate.next(completeSurrogate, 832L, streaming = true)
        for (time in 864L..1500L step 32L) {
            visible = pacerSurrogate.next(completeSurrogate, time, streaming = true)
            if (!pacerSurrogate.hasPending) break
        }
        assertEquals("Prefix 😀", visible)
        assertFalse(pacerSurrogate.hasPending)
    }

    // --- Throughput Tier Verification: 40, 70, 120, 300 simulated tok/s ---
    // Note: Simulated token units are fixed at 4 UTF-16 characters (throughput benchmark, not tokenizer).

    data class TierPacingResult(
        val tierTokPerSec: Int,
        val totalSourceChars: Int,
        val maxBacklogMs: Double,
        val maxCharsEmittedPerTick: Int,
        val steadyExhaustionCount: Int,
        val finalVisible: String
    )

    private fun runTierSimulation(
        tokPerSec: Int,
        durationMs: Long = 10_000L,
        chunkIntervalMs: Long = 100L,
        samplingIntervalMs: Long = 48L
    ): TierPacingResult {
        val pacer = StreamingTextPacer()
        val charsPerSec = tokPerSec * 4 // 1 simulated token unit = 4 UTF-16 chars
        val charsPerChunk = charsPerSec * chunkIntervalMs / 1000L
        val inputRateCharsPerMs = charsPerSec / 1000.0

        var source = ""
        var previousVisible = ""
        var maxBacklogChars = 0
        var maxEmittedPerTick = 0
        var steadyExhaustions = 0

        var nextChunkTime = 0L
        val totalTicks = (durationMs / samplingIntervalMs).toInt()

        for (tick in 0..totalTicks) {
            val nowMs = tick * samplingIntervalMs

            while (nextChunkTime <= nowMs && nextChunkTime < durationMs) {
                // Append simulated token chunk (4 UTF-16 chars per unit)
                source += "Word".repeat((charsPerChunk / 4).toInt())
                nextChunkTime += chunkIntervalMs
            }

            val visible = pacer.next(source, nowMs, streaming = true)
            assertTrue("Monotonic advance at $nowMs ms for tier $tokPerSec", visible.startsWith(previousVisible))

            val emittedInTick = visible.length - previousVisible.length
            maxEmittedPerTick = maxOf(maxEmittedPerTick, emittedInTick)

            val pending = source.length - visible.length
            maxBacklogChars = maxOf(maxBacklogChars, pending)

            // In steady streaming (after initial 200ms buffer and warmup, up to stream end),
            // check whether backlog drops to 0 which would cause unwanted re-buffering.
            if (nowMs in 500L until (durationMs - 200L)) {
                if (pending == 0 && source.isNotEmpty()) {
                    steadyExhaustions++
                }
            }

            previousVisible = visible
        }

        // Finish draining after 10 seconds of input
        var finishTime = durationMs
        var drained = previousVisible
        while (pacer.hasPending && finishTime <= durationMs + 600L) {
            finishTime += samplingIntervalMs
            drained = pacer.next(source, finishTime, streaming = false)
        }
        assertEquals("Full text must match at completion for tier $tokPerSec", source, drained)

        val maxBacklogMs = maxBacklogChars / inputRateCharsPerMs
        return TierPacingResult(
            tierTokPerSec = tokPerSec,
            totalSourceChars = source.length,
            maxBacklogMs = maxBacklogMs,
            maxCharsEmittedPerTick = maxEmittedPerTick,
            steadyExhaustionCount = steadyExhaustions,
            finalVisible = drained
        )
    }

    @Test
    fun tier40TokensPerSecondPacesSmoothlyAndBoundsLatency() {
        val result = runTierSimulation(tokPerSec = 40)
        // 40 tok/s = 160 chars/s (0.16 chars/ms). Max backlog must remain <= 600ms equivalent.
        assertTrue("Max backlog (${result.maxBacklogMs}ms) must be <= 600ms", result.maxBacklogMs <= 600.0)
        assertTrue("No steady-state buffer exhaustions during continuous input", result.steadyExhaustionCount == 0)
        assertTrue("Per-tick emission bounded proportionally", result.maxCharsEmittedPerTick in 5..30)
    }

    @Test
    fun tier70TokensPerSecondPacesSmoothlyAndBoundsLatency() {
        val result = runTierSimulation(tokPerSec = 70)
        // 70 tok/s = 280 chars/s (0.28 chars/ms).
        assertTrue("Max backlog (${result.maxBacklogMs}ms) must be <= 600ms", result.maxBacklogMs <= 600.0)
        assertTrue("No steady-state buffer exhaustions during continuous input", result.steadyExhaustionCount == 0)
        assertTrue("Per-tick emission bounded proportionally", result.maxCharsEmittedPerTick in 10..45)
    }

    @Test
    fun tier120TokensPerSecondPacesSmoothlyAndBoundsLatency() {
        val result = runTierSimulation(tokPerSec = 120)
        // 120 tok/s = 480 chars/s (0.48 chars/ms).
        assertTrue("Max backlog (${result.maxBacklogMs}ms) must be <= 600ms", result.maxBacklogMs <= 600.0)
        assertTrue("No steady-state buffer exhaustions during continuous input", result.steadyExhaustionCount == 0)
        assertTrue("Per-tick emission bounded proportionally", result.maxCharsEmittedPerTick in 15..75)
    }

    @Test
    fun tier300TokensPerSecondPacesSmoothlyAndBoundsLatency() {
        val result = runTierSimulation(tokPerSec = 300)
        // 300 tok/s = 1200 chars/s (1.20 chars/ms).
        assertTrue("Max backlog (${result.maxBacklogMs}ms) must be <= 600ms", result.maxBacklogMs <= 600.0)
        assertTrue("No steady-state buffer exhaustions during continuous input", result.steadyExhaustionCount == 0)
        assertTrue("Per-tick emission bounded proportionally", result.maxCharsEmittedPerTick in 40..170)
    }

    @Test
    fun displayBatchCadencesKeepAllSpeedTiersBounded() {
        for (tier in listOf(40, 70, 120, 300)) {
            // Include the slowest render cadence so slow devices cannot hide queue growth.
            val result = runTierSimulation(tier, samplingIntervalMs = 140L)
            assertTrue("$tier: ${result.maxBacklogMs}ms backlog", result.maxBacklogMs <= 650.0)
            assertEquals("$tier: continuous input should not repeatedly rebuffer", 0, result.steadyExhaustionCount)
            assertTrue("$tier: no oversized burst", result.maxCharsEmittedPerTick <= tier * 4 * .25)
            println("tier=$tier simulated tokens/s, maxBacklogMs=${result.maxBacklogMs}, " +
                "maxBatchChars=${result.maxCharsEmittedPerTick}, exhaustions=${result.steadyExhaustionCount}")
        }
    }

    @Test
    fun tierSwitchingMaintainsMonotonicPacingAndCompletes() {
        val pacer = StreamingTextPacer()
        var source = ""
        var previous = ""

        // Phase 1 (0..3000ms): 40 tok/s (16 chars / 100ms)
        // Phase 2 (3000..6000ms): 120 tok/s (48 chars / 100ms)
        // Phase 3 (6000..8000ms): 300 tok/s (120 chars / 100ms)
        // Phase 4 (8000..10000ms): 70 tok/s (28 chars / 100ms)
        var nextChunkMs = 0L
        for (tick in 0..(10_000 / 48)) {
            val nowMs = tick * 48L
            while (nextChunkMs <= nowMs && nextChunkMs < 10_000L) {
                val charsThisChunk = when {
                    nextChunkMs < 3000L -> 16
                    nextChunkMs < 6000L -> 48
                    nextChunkMs < 8000L -> 120
                    else -> 28
                }
                source += "X".repeat(charsThisChunk)
                nextChunkMs += 100L
            }
            val visible = pacer.next(source, nowMs, streaming = true)
            assertTrue("Monotonic advance during tier switch at $nowMs ms", visible.startsWith(previous))
            previous = visible
        }

        var finishTime = 10_000L
        while (pacer.hasPending && finishTime <= 10_600L) {
            finishTime += 48L
            previous = pacer.next(source, finishTime, streaming = false)
        }
        assertEquals("Full text matches after tier switching", source, previous)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun twoSecondUpstreamPauseDrainsCompletelyAndRebuffersOnResume() {
        val pacer = StreamingTextPacer()
        var source = ""
        var previous = ""

        // 1. Initial 1500ms streaming at 70 tok/s (28 chars / 100ms)
        var nextChunkMs = 0L
        for (tick in 0..(1500 / 48)) {
            val nowMs = tick * 48L
            while (nextChunkMs <= nowMs && nextChunkMs < 1500L) {
                source += "Data".repeat(7) // 28 chars
                nextChunkMs += 100L
            }
            val visible = pacer.next(source, nowMs, streaming = true)
            assertTrue(visible.startsWith(previous))
            previous = visible
        }

        // 2. Upstream pauses for 2.0s (1500ms to 3500ms): drains and waits idle
        var drainedAt = -1L
        for (nowMs in 1536L..3500L step 48L) {
            val visible = pacer.next(source, nowMs, streaming = true)
            assertTrue(visible.startsWith(previous))
            previous = visible
            if (visible == source && !pacer.hasPending) {
                if (drainedAt < 0L) drainedAt = nowMs
            }
        }
        assertTrue("Backlog must drain to zero during 2s pause", drainedAt in 1500L..2000L)
        assertEquals(source, previous)
        assertFalse(pacer.hasPending)

        // 3. Upstream resumes at 3500ms with new chunk
        val resumeText = source + "ResumeChunk".repeat(4)
        val at3504 = pacer.next(resumeText, 3504L, streaming = true)
        assertEquals("Must re-buffer on resume after pause", source, at3504)
        assertTrue(pacer.hasPending)

        // Stays buffering during 200ms re-buffer window
        assertEquals(source, pacer.next(resumeText, 3600L, streaming = true))
        assertEquals(source, pacer.next(resumeText, 3696L, streaming = true))

        // Beyond 200ms (at 3744ms), begins emitting resumed text
        val at3744 = pacer.next(resumeText, 3744L, streaming = true)
        assertTrue(at3744.length > source.length)
        assertTrue(resumeText.startsWith(at3744))

        // Normal completion
        var finishTime = 3800L
        var finalVisible = at3744
        while (pacer.hasPending && finishTime <= 4500L) {
            finishTime += 48L
            finalVisible = pacer.next(resumeText, finishTime, streaming = false)
        }
        assertEquals(resumeText, finalVisible)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun cancellationDuringHighThroughputSyncsImmediately() {
        val pacer = StreamingTextPacer()
        var source = ""
        for (i in 1..10) {
            source += "A".repeat(120)
            pacer.next(source, i * 100L, streaming = true)
        }
        assertTrue(pacer.hasPending)

        val cancelled = pacer.next(source, 1050L, streaming = false, immediate = true)
        assertEquals(source, cancelled)
        assertFalse(pacer.hasPending)
    }

    @Test
    fun unchangedBlocksKeepIdentityButLaterReferencesCanStillChangeEarlierBlocks() {
        val before = MarkdownDocument.parse("Stable paragraph.\n\nTail")
        val after = reuseMarkdownBlocks(before, MarkdownDocument.parse("Stable paragraph.\n\nTail grows"))
        assertSame(before[0], after[0])
        assertNotSame(before[1], after[1])
        val unresolved = MarkdownDocument.parse("[link][ref]\n\nTail")
        val resolved = reuseMarkdownBlocks(unresolved, MarkdownDocument.parse("[link][ref]\n\n[ref]: https://example.com"))
        assertNotEquals(unresolved[0], resolved[0])
    }
}
