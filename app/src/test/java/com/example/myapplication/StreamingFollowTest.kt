package com.example.myapplication

import com.example.myapplication.ui.chat.listIsAtBottom
import com.example.myapplication.ui.chat.updateFollowLatest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingFollowTest {

    @Test
    fun `bottom detection accepts the configured tolerance only`() {
        assertTrue(
            listIsAtBottom(
                lastVisibleIndex = 4,
                lastVisibleEnd = 1_080,
                viewportEnd = 1_000,
                lastItemIndex = 4
            )
        )
        assertFalse(
            listIsAtBottom(
                lastVisibleIndex = 4,
                lastVisibleEnd = 1_081,
                viewportEnd = 1_000,
                lastItemIndex = 4
            )
        )
        assertFalse(
            listIsAtBottom(
                lastVisibleIndex = 3,
                lastVisibleEnd = 900,
                viewportEnd = 1_000,
                lastItemIndex = 4
            )
        )
    }

    @Test
    fun `only user movement changes follow state`() {
        assertTrue(updateFollowLatest(current = true, userScrolling = false, atBottom = false))
        assertFalse(updateFollowLatest(current = true, userScrolling = true, atBottom = false))
        assertTrue(updateFollowLatest(current = false, userScrolling = true, atBottom = true))
        assertFalse(updateFollowLatest(current = false, userScrolling = false, atBottom = true))
    }
}
