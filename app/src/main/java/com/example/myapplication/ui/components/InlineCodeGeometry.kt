package com.example.myapplication.ui.components

/** Character geometry from one visual line of an inline code range. */
internal data class InlineCodeGlyphBounds(
    val character: Char,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/** Font-size-aware metrics measured with the inline code font and current Compose density. */
internal data class InlineCodeFontMetrics(val ascent: Float, val descent: Float)

/** A horizontally connected code background with one normalized line height. */
internal data class InlineCodeSegmentBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Builds inline-code background segments for a single visual line.
 *
 * Compose can return line-height rectangles for line-ending or zero-width characters. Those
 * bounds are not drawable glyphs, so they must not create padded background boxes. Glyph bounds
 * determine horizontal segments only; the measured code-font ascent and descent set their height.
 */
internal fun inlineCodeBackgroundSegments(
    glyphs: List<InlineCodeGlyphBounds>,
    baseline: Float,
    fontMetrics: InlineCodeFontMetrics,
    horizontalJoinTolerance: Float = 0.5f
): List<InlineCodeSegmentBounds> {
    if (!baseline.isFinite() ||
        !fontMetrics.ascent.isFinite() || fontMetrics.ascent <= 0f ||
        !fontMetrics.descent.isFinite() || fontMetrics.descent <= 0f ||
        !horizontalJoinTolerance.isFinite() || horizontalJoinTolerance < 0f
    ) {
        return emptyList()
    }

    val drawableGlyphs = glyphs.filter { glyph ->
        glyph.character != '\n' && glyph.character != '\r' &&
            glyph.character != '\u2028' && glyph.character != '\u2029' &&
            glyph.left.isFinite() && glyph.top.isFinite() &&
            glyph.right.isFinite() && glyph.bottom.isFinite() &&
            glyph.right > glyph.left && glyph.bottom > glyph.top
    }
    if (drawableGlyphs.isEmpty()) return emptyList()

    val top = baseline - fontMetrics.ascent
    val bottom = baseline + fontMetrics.descent
    if (!top.isFinite() || !bottom.isFinite() || bottom <= top) return emptyList()

    val ordered = drawableGlyphs.sortedBy(InlineCodeGlyphBounds::left)
    val segments = mutableListOf<InlineCodeSegmentBounds>()
    var left = ordered.first().left
    var right = ordered.first().right

    ordered.drop(1).forEach { glyph ->
        if (glyph.left <= right + horizontalJoinTolerance) {
            right = maxOf(right, glyph.right)
        } else {
            segments += InlineCodeSegmentBounds(left, top, right, bottom)
            left = glyph.left
            right = glyph.right
        }
    }
    segments += InlineCodeSegmentBounds(left, top, right, bottom)
    return segments
}
