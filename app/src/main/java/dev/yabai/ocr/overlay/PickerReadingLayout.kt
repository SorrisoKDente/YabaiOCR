package dev.yabai.ocr.overlay

/**
 * Where a reading goes when it is drawn beside the characters it reads.
 *
 * The rule is the one Japanese typesetting uses, which is also the one the user asked for: a reading
 * of **vertical** text is a small column in the gutter to the **right** of the characters, one kana
 * under the other; a reading of **horizontal** text is a small line **above** the characters. The
 * positions are fractions of the line's length, so a reading of the first two characters of an
 * eight-character column starts at the top and covers a quarter of it, whatever the pixels are.
 */
object RubyLayout {

    /** The size below which a reading is not drawn at all: a smear is worse than nothing. */
    private const val MIN_SIZE = 7f

    /** Above this fraction of the glyph size a reading stops being ruby and starts being noise. */
    private const val SIZE_RATIO = 0.55f

    /**
     * Where to draw [text] over [characters] characters of a line, or null when the line is too thin
     * to carry a legible reading.
     *
     * @param left,top,width,height the line's box on screen.
     * @param vertical true for a column of vertical text, false for a row.
     * @param from,to the stretch the reading belongs to, as fractions of the line's length.
     */
    fun spot(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        vertical: Boolean,
        from: Float,
        to: Float,
        characters: Int,
    ): RubySpot? {
        if (characters <= 0 || width <= 0f || height <= 0f) return null
        if (vertical) {
            val spanTop = top + height * from.coerceIn(0f, 1f)
            // As thin as the glyphs of the line it explains, and never more than the line can hold:
            // five kana beside two kanji are exactly the case, and squeezing them into the two
            // characters' own stretch would make them unreadable.
            val size = minOf(width * SIZE_RATIO, height / characters)
            if (size < MIN_SIZE) return null
            // Anchored on the characters it reads, but pushed up rather than running past the
            // bottom of the column.
            val y = spanTop.coerceAtMost(top + height - size * characters).coerceAtLeast(top)
            return RubySpot(
                x = left + width + width * 0.15f,
                y = y,
                size = size,
                stacked = true,
            )
        }

        val size = height * SIZE_RATIO
        if (size < MIN_SIZE) return null
        val start = left + width * from.coerceIn(0f, 1f)
        val end = left + width * to.coerceIn(0f, 1f)
        return RubySpot(
            // A horizontal reading is centred over the characters, so it is laid out around the
            // middle of the stretch rather than from its start.
            x = (start + end) / 2f,
            y = top - size,
            size = size,
            stacked = false,
        )
    }
}

/**
 * A place to draw a reading.
 *
 * [x] is the **left** of the reading when [stacked] — a column of kana — and the **centre** of the
 * stretch when it is not. [y] is the top of the reading in both cases, which is the baseline the
 * caller adds the font's ascent to.
 */
data class RubySpot(val x: Float, val y: Float, val size: Float, val stacked: Boolean)

/**
 * Where the readable strip of a balloon goes, given the room on screen.
 *
 * To the **right** of the box when it fits — the side the eye leaves a column of vertical text on —,
 * then to the left, then below, then above, clamped to the screen in every case. A strip that
 * overlaps the balloon is worse than one that sits at the edge of the screen, but a strip drawn
 * half off-screen is worse than both.
 */
object LabelLayout {

    /** Room between the box and the strip, and between the strip and the edge of the screen. */
    const val GAP = 6

    fun chip(
        boxLeft: Int,
        boxTop: Int,
        boxRight: Int,
        boxBottom: Int,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Chip {
        val left = when {
            boxRight + GAP + width <= screenWidth -> boxRight + GAP
            boxLeft - GAP - width >= 0 -> boxLeft - GAP - width
            else -> boxLeft
        }
        val top = when {
            boxRight + GAP + width <= screenWidth || boxLeft - GAP - width >= 0 -> boxTop
            boxBottom + GAP + height <= screenHeight -> boxBottom + GAP
            else -> boxTop - GAP - height
        }
        return Chip(
            left = left.coerceIn(0, (screenWidth - width).coerceAtLeast(0)),
            top = top.coerceIn(0, (screenHeight - height).coerceAtLeast(0)),
        )
    }
}

/** The top-left corner of a strip, in pixels. */
data class Chip(val left: Int, val top: Int)
