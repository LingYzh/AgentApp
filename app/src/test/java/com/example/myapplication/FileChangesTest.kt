package com.example.myapplication

import com.example.myapplication.data.store.DiffLineType
import com.example.myapplication.data.store.DiffLine
import com.example.myapplication.data.store.FileChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileChangesTest {

    @Test
    fun `diff reports inserted line with line numbers`() {
        val result = FileChanges.diff("one\ntwo", "one\nnew\ntwo")

        assertEquals(1, result.addedCount)
        assertEquals(0, result.removedCount)
        assertFalse(result.usedFallback)
        assertEquals(
            listOf(
                DiffLine(DiffLineType.CONTEXT, 1, 1, "one"),
                DiffLine(DiffLineType.ADDED, null, 2, "new"),
                DiffLine(DiffLineType.CONTEXT, 2, 3, "two")
            ),
            result.lines
        )
    }

    @Test
    fun `diff reports deleted line with line numbers`() {
        val result = FileChanges.diff("one\nold\ntwo", "one\ntwo")

        assertEquals(0, result.addedCount)
        assertEquals(1, result.removedCount)
        assertEquals(
            listOf(
                DiffLine(DiffLineType.CONTEXT, 1, 1, "one"),
                DiffLine(DiffLineType.REMOVED, 2, null, "old"),
                DiffLine(DiffLineType.CONTEXT, 3, 2, "two")
            ),
            result.lines
        )
    }
}
