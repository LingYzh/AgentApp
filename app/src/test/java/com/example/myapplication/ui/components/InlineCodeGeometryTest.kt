package com.example.myapplication.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineCodeGeometryTest {
    @Test
    fun `line breaks and invalid rectangles do not create background segments`() {
        val segments = inlineCodeBackgroundSegments(
            glyphs = listOf(
                glyph('\n', left = 0f, top = 0f, right = 0f, bottom = 29f),
                glyph('\r', left = 2f, top = 0f, right = 5f, bottom = 29f),
                glyph('x', left = 8f, top = 4f, right = 8f, bottom = 16f),
                glyph('y', left = 10f, top = 4f, right = 9f, bottom = 16f),
                glyph('z', left = 12f, top = Float.NaN, right = 18f, bottom = 16f)
            ),
            baseline = 15f,
            fontMetrics = InlineCodeFontMetrics(ascent = 10f, descent = 3f)
        )

        assertTrue(segments.isEmpty())
    }

    @Test
    fun `mixed glyph bounds use measured font metrics relative to the line baseline`() {
        val segments = inlineCodeBackgroundSegments(
            glyphs = listOf(
                glyph('a', left = 0f, top = 4f, right = 5f, bottom = 17f),
                glyph('b', left = 5f, top = 4f, right = 10f, bottom = 17f),
                glyph('中', left = 10f, top = -4f, right = 20f, bottom = 20f),
                glyph('c', left = 20f, top = 4f, right = 25f, bottom = 17f),
                glyph('d', left = 25f, top = 5f, right = 30f, bottom = 14f)
            ),
            baseline = 14f,
            fontMetrics = InlineCodeFontMetrics(ascent = 10f, descent = 3f)
        )

        assertEquals(1, segments.size)
        assertEquals(0f, segments.single().left, 0.001f)
        assertEquals(30f, segments.single().right, 0.001f)
        assertEquals(4f, segments.single().top, 0.001f)
        assertEquals(17f, segments.single().bottom, 0.001f)
    }

    @Test
    fun `a single short symbol uses measured code font height`() {
        val segments = inlineCodeBackgroundSegments(
            glyphs = listOf(glyph('~', left = 1f, top = -20f, right = 4f, bottom = 30f)),
            baseline = 14f,
            fontMetrics = InlineCodeFontMetrics(ascent = 9f, descent = 3f)
        )

        assertEquals(1, segments.size)
        assertEquals(5f, segments.single().top, 0.001f)
        assertEquals(17f, segments.single().bottom, 0.001f)
    }

    @Test
    fun `uniformly tall glyph bounds do not increase code background height`() {
        val segments = inlineCodeBackgroundSegments(
            glyphs = listOf(
                glyph('[', left = 1f, top = -8f, right = 5f, bottom = 32f),
                glyph('x', left = 5f, top = -8f, right = 10f, bottom = 32f),
                glyph(']', left = 10f, top = -8f, right = 14f, bottom = 32f)
            ),
            baseline = 15f,
            fontMetrics = InlineCodeFontMetrics(ascent = 11f, descent = 3f)
        )

        assertEquals(1, segments.size)
        assertEquals(4f, segments.single().top, 0.001f)
        assertEquals(18f, segments.single().bottom, 0.001f)
    }

    @Test
    fun `short symbols stay visible and separated visual runs stay separate`() {
        val segments = inlineCodeBackgroundSegments(
            glyphs = listOf(
                glyph('~', left = 1f, top = 5f, right = 4f, bottom = 16f),
                glyph('>', left = 4.25f, top = 5f, right = 7f, bottom = 16f),
                glyph('`', left = 18f, top = 5f, right = 20f, bottom = 16f)
            ),
            baseline = 14f,
            fontMetrics = InlineCodeFontMetrics(ascent = 9f, descent = 3f)
        )

        assertEquals(2, segments.size)
        assertEquals(1f, segments.first().left, 0.001f)
        assertEquals(7f, segments.first().right, 0.001f)
        assertEquals(18f, segments.last().left, 0.001f)
        assertEquals(20f, segments.last().right, 0.001f)
        assertTrue(segments.all { it.top == 5f && it.bottom == 17f })
    }

    private fun glyph(character: Char, left: Float, top: Float, right: Float, bottom: Float) =
        InlineCodeGlyphBounds(character, left, top, right, bottom)
}
