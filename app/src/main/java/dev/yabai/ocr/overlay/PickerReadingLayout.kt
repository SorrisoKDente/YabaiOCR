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
 *
 * Strips already placed are passed in as [chip]'s `taken`: two balloons side by side used to get
 * their strips on the same spot, and the second one was drawn over the first (R12). A strip that
 * would land on another one moves out by whole strips along the side it is on — down before up —
 * and only gives up the side when it runs out of screen.
 */
object LabelLayout {

    /** Room between the box and the strip, and between two strips. */
    const val GAP = 6

    /** How many strips away from its box a crowding strip may go before trying the next side. */
    private const val MAX_STEPS = 24

    fun chip(
        boxLeft: Int,
        boxTop: Int,
        boxRight: Int,
        boxBottom: Int,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        taken: List<Strip> = emptyList(),
    ): Chip {
        // In order of preference: the two sides, then under and over the box. Each entry is the
        // anchored corner of the strip and whether crowding moves it sideways.
        val sides = listOf(
            Side(boxRight + GAP, boxTop, sideways = false),
            Side(boxLeft - GAP - width, boxTop, sideways = false),
            Side(boxLeft, boxBottom + GAP, sideways = true),
            Side(boxLeft, boxTop - GAP - height, sideways = true),
        )
        val offsets = offsets()
        for (side in sides) {
            for (offset in offsets) {
                val step = if (side.sideways) width + GAP else height + GAP
                val left = side.left + if (side.sideways) offset * step else 0
                val top = side.top + if (side.sideways) 0 else offset * step
                if (left < 0 || top < 0 || left + width > screenWidth || top + height > screenHeight) {
                    continue
                }
                val rect = Strip(left, top, left + width, top + height)
                if (taken.any { it.intersects(rect) }) continue
                return Chip(left = left, top = top)
            }
        }

        // Crowded everywhere: the plain rule, clamped, so a strip is never lost.
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

    /** 0, +1, −1, +2, −2… — strips stack downwards first, which is how a list reads. */
    private fun offsets(): IntArray {
        val steps = IntArray(MAX_STEPS * 2 + 1)
        steps[0] = 0
        for (step in 1..MAX_STEPS) {
            steps[step * 2 - 1] = step
            steps[step * 2] = -step
        }
        return steps
    }

    private class Side(val left: Int, val top: Int, val sideways: Boolean)
}

/** The top-left corner of a strip, in pixels. */
data class Chip(val left: Int, val top: Int)

/** The area a strip occupies, used to keep the next one off it. */
data class Strip(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    /** True when the two share any pixels. Strips that merely touch do not fight for the screen. */
    fun intersects(other: Strip): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}
