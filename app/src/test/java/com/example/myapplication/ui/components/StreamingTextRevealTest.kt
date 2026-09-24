package com.example.myapplication.ui.components

import org.junit.Assert.*
import org.junit.Test

class StreamingTextRevealTest {

    @Test
    fun initialHistoricalTextDoesNotAnimate() {
        val tracker = StreamingRevealTracker()
        tracker.initHistorical("Existing historical text")

        assertFalse(tracker.hasActiveRanges)
        tracker.update("Existing historical text", 0L, enabled = true)
        assertFalse(tracker.hasActiveRanges)
    }

    @Test
    fun initHistoricalPreservesBaselineForSubsequentAppends() {
        val tracker = StreamingRevealTracker()
        // Simulate lifecycle resume calling initHistorical
        tracker.initHistorical("Baseline text")
        assertFalse(tracker.hasActiveRanges)

        // Next chunk arrives: should only fade in the new portion, not the entire text
        tracker.update("Baseline text + addition", 100_000_000L, enabled = true)
        assertTrue(tracker.hasActiveRanges)
        assertEquals(1, tracker.activeRanges.size)
        assertEquals(13, tracker.activeRanges[0].start)
        assertEquals("Baseline text + addition".length, tracker.activeRanges[0].end)
    }

    @Test
    fun firstLiveBlockAppearanceFadesInInitialChunk() {
        val tracker = StreamingRevealTracker()
        val text = "First live block"

        tracker.update(text, 0L, enabled = true, isFirstBlockAppearance = true)

        assertTrue(tracker.hasActiveRanges)
        assertEquals(1, tracker.activeRanges.size)
        val range = tracker.activeRanges[0]
        assertEquals(0, range.start)
        assertEquals(text.length, range.end)
        assertEquals(0L, range.startNs)

        // Erase alpha: starts at 1.0 (fully masked / invisible), ends at 0.0 (fully visible)
        assertEquals(1.0f, tracker.getEraseAlpha(range, 0L), 0.001f)
        assertEquals(0.5f, tracker.getEraseAlpha(range, 110_000_000L), 0.01f)
        assertEquals(0.0f, tracker.getEraseAlpha(range, 220_000_000L), 0.001f)

        val hasRemaining = tracker.prune(220_000_000L)
        assertFalse(hasRemaining)
        assertFalse(tracker.hasActiveRanges)
    }

    @Test
    fun subsequentAppendsCreateNewRangesWithoutResettingPreviousRanges() {
        val tracker = StreamingRevealTracker()

        // Batch 1 arrives at t = 0ms
        tracker.update("Hello", 0L, enabled = true)
        assertEquals(1, tracker.activeRanges.size)
        val range1 = tracker.activeRanges[0]
        assertEquals(0, range1.start)
        assertEquals(5, range1.end)
        assertEquals(0L, range1.startNs)

        // Batch 2 arrives at t = 100ms
        tracker.update("Hello world", 100_000_000L, enabled = true)
        assertEquals(2, tracker.activeRanges.size)

        // Invariant: Batch 1's start timestamp MUST NOT be reset by Batch 2's arrival
        assertEquals(0L, tracker.activeRanges[0].startNs)
        assertEquals(100_000_000L, tracker.activeRanges[1].startNs)
        assertEquals(5, tracker.activeRanges[1].start)
        assertEquals(11, tracker.activeRanges[1].end)

        // Mid-point check at t = 150ms:
        val alpha1At150 = tracker.getEraseAlpha(tracker.activeRanges[0], 150_000_000L)
        val alpha2At150 = tracker.getEraseAlpha(tracker.activeRanges[1], 150_000_000L)
        assertEquals(0.318f, alpha1At150, 0.01f)
        assertEquals(0.773f, alpha2At150, 0.01f)

        // At t = 220ms: Batch 1 completes; Batch 2 remains active
        val hasRemainingAt220 = tracker.prune(220_000_000L)
        assertTrue(hasRemainingAt220)
        assertEquals(1, tracker.activeRanges.size)
        assertEquals(5, tracker.activeRanges[0].start)
        assertEquals(11, tracker.activeRanges[0].end)

        // At t = 320ms: Batch 2 completes
        val hasRemainingAt320 = tracker.prune(320_000_000L)
        assertFalse(hasRemainingAt320)
        assertFalse(tracker.hasActiveRanges)
    }

    @Test
    fun replacementOrTruncationCancelsActiveRangesImmediately() {
        val tracker = StreamingRevealTracker()
        tracker.update("Initial stream", 0L, enabled = true)
        assertTrue(tracker.hasActiveRanges)

        // Non-append replacement: cancels old ranges and displays immediately
        tracker.update("Completely new text", 50_000_000L, enabled = true)
        assertFalse(tracker.hasActiveRanges)

        // Append more text
        tracker.update("Completely new text with more", 80_000_000L, enabled = true)
        assertTrue(tracker.hasActiveRanges)

        // Truncation: cancels ranges immediately
        tracker.update("Completely", 100_000_000L, enabled = true)
        assertFalse(tracker.hasActiveRanges)
    }

    @Test
    fun disabledRevealingCancelsActiveRangesImmediately() {
        val tracker = StreamingRevealTracker()
        tracker.update("Initial stream", 0L, enabled = true)
        assertTrue(tracker.hasActiveRanges)

        // Disabling reveal clears all ranges
        tracker.update("Initial stream with more", 50_000_000L, enabled = false)
        assertFalse(tracker.hasActiveRanges)

        // Subsequent appends while disabled create no ranges
        tracker.update("Initial stream with more words", 100_000_000L, enabled = false)
        assertFalse(tracker.hasActiveRanges)
    }

    @Test
    fun zeroMotionScaleRevealsImmediatelyWithoutRanges() {
        val tracker = StreamingRevealTracker()
        // System animation scale = 0f (animations disabled)
        tracker.update("Fast text", 0L, enabled = true, scaleFactor = 0f)
        assertFalse(tracker.hasActiveRanges)
    }

    @Test
    fun scaledMotionDurationAdjustsPruneAndAlphaConsistently() {
        val tracker = StreamingRevealTracker() // 220ms base
        tracker.update("Scaled text", 0L, enabled = true)

        val range = tracker.activeRanges[0]
        val scaleFactor = 2.0f // 2x animation duration = 440ms

        // At 220ms with 2x scale, progress is 50%, eraseAlpha is 0.5f
        assertEquals(0.5f, tracker.getEraseAlpha(range, 220_000_000L, scaleFactor), 0.01f)
        // Prune at 220ms should NOT remove the range
        assertTrue(tracker.prune(220_000_000L, scaleFactor))
        assertEquals(1, tracker.activeRanges.size)

        // At 440ms, range completes
        assertEquals(0.0f, tracker.getEraseAlpha(range, 440_000_000L, scaleFactor), 0.001f)
        assertFalse(tracker.prune(440_000_000L, scaleFactor))
        assertFalse(tracker.hasActiveRanges)
    }

    @Test
    fun pruneRemovesOnlyExpiredRangesAcrossMultipleBatches() {
        val tracker = StreamingRevealTracker()

        // Batch 1 at 0ms, Batch 2 at 50ms, Batch 3 at 150ms
        tracker.update("A", 0L, enabled = true)
        tracker.update("AB", 50_000_000L, enabled = true)
        tracker.update("ABC", 150_000_000L, enabled = true)
        assertEquals(3, tracker.activeRanges.size)

        // At 220ms: Batch 1 expires (0ms + 220ms)
        tracker.prune(220_000_000L)
        assertEquals(2, tracker.activeRanges.size)
        assertEquals(1, tracker.activeRanges[0].start)
        assertEquals(2, tracker.activeRanges[1].start)

        // At 270ms: Batch 2 expires (50ms + 220ms)
        tracker.prune(270_000_000L)
        assertEquals(1, tracker.activeRanges.size)
        assertEquals(2, tracker.activeRanges[0].start)

        // At 370ms: Batch 3 expires (150ms + 220ms)
        tracker.prune(370_000_000L)
        assertFalse(tracker.hasActiveRanges)
    }
}
