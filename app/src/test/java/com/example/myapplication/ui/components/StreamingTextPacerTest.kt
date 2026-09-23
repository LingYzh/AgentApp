package com.example.myapplication.ui.components

import org.junit.Assert.*
import org.junit.Test

class StreamingTextPacerTest {
    @Test fun burstAdvancesMonotonicallyAndDrainsWithin120Milliseconds() {
        val pacer = StreamingTextPacer()
        val source = "中文分片 mixed words ".repeat(10)
        var previous = ""
        for (time in listOf(0L, 32L, 64L, 96L, 120L)) {
            val visible = pacer.next(source, time, true)
            assertTrue(visible.startsWith(previous))
            assertTrue(source.startsWith(visible))
            if (time == 0L) assertTrue(visible.length in 1 until source.length)
            previous = visible
        }
        assertEquals(source, previous)
    }

    @Test fun finishCancellationReplacementAndHugeBurstDoNotLeavePendingText() {
        val pacer = StreamingTextPacer()
        pacer.next("a".repeat(100), 0, true)
        assertEquals("a".repeat(100), pacer.next("a".repeat(100), 16, false))
        assertEquals("replacement", pacer.next("replacement", 32, true))
        assertEquals("", pacer.next("", 48, true))
        assertEquals("b".repeat(5000), pacer.next("b".repeat(5000), 64, true))
    }

    @Test fun continuousInputDoesNotSplitSurrogatePairsOrLoseSuffix() {
        val pacer = StreamingTextPacer()
        var source = ""
        repeat(100) { index ->
            source += "中😀e\u0301"
            val visible = pacer.next(source, index * 32L, true)
            assertFalse(visible.lastOrNull()?.let { Character.isHighSurrogate(it) } ?: false)
            assertTrue(source.startsWith(visible))
        }
        assertEquals(source, pacer.next(source, 3300, true))
    }

    @Test fun unchangedBlocksKeepIdentityButLaterReferencesCanStillChangeEarlierBlocks() {
        val before = MarkdownDocument.parse("Stable paragraph.\n\nTail")
        val after = reuseMarkdownBlocks(before, MarkdownDocument.parse("Stable paragraph.\n\nTail grows"))
        assertSame(before[0], after[0])
        assertNotSame(before[1], after[1])
        val unresolved = MarkdownDocument.parse("[link][ref]\n\nTail")
        val resolved = reuseMarkdownBlocks(unresolved, MarkdownDocument.parse("[link][ref]\n\n[ref]: https://example.com"))
        assertNotEquals(unresolved[0], resolved[0])
    }
}
