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

    /** Air kept between the reading and the text it steps away from. */
    private const val AIR = 1f

    /**
     * Where to draw [text] over [characters] characters of a line, or null when there is no room for
     * even a small reading on either side.
     *
     * The reading goes where Japanese prints it — the right gutter of a column, above a row — and
     * never covers another piece of text. It first tries its full size there; when the gutter is
     * narrower than that, it **shrinks** to whatever the gap allows, down to [MIN_SIZE]. Only when
     * neither side has room for a legible reading is none drawn (R57, R58).
     *
     * @param left,top,width,height the line's box on screen.
     * @param vertical true for a column of vertical text, false for a row.
     * @param from,to the stretch the reading belongs to, as fractions of the line's length.
     * @param blocked the boxes of every other piece of text on the page, on screen.
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
        blocked: List<RubyBox> = emptyList(),
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
            fun yFor(heightAtSize: Float): Float =
                spanTop.coerceAtMost(top + height - heightAtSize * characters).coerceAtLeast(top)
            fun placed(at: Float, x: Float): RubySpot? =
                RubySpot(x = x, y = yFor(at), size = at, stacked = true)
                    .takeIf { it.isFree(characters, blocked) }

            val right = left + width
            val bandTop = yFor(size)
            val bandBottom = bandTop + size * characters
            placed(size, right + width * 0.15f)?.let { return it }
            shrunkToFit(size, freeWidth(blocked, right, toRight = true, bandTop, bandBottom))
                ?.let { fitted -> placed(fitted, right + AIR)?.let { return it } }

            // The other gutter, for a column that is the last one of its balloon.
            placed(size, left - width * 0.15f - size)?.let { return it }
            shrunkToFit(size, freeWidth(blocked, left, toRight = false, bandTop, bandBottom))
                ?.let { fitted -> placed(fitted, left - AIR - fitted)?.let { return it } }
            return null
        }

        val size = height * SIZE_RATIO
        if (size < MIN_SIZE) return null
        val start = left + width * from.coerceIn(0f, 1f)
        val end = left + width * to.coerceIn(0f, 1f)
        val centre = (start + end) / 2f
        fun placed(at: Float, y: Float): RubySpot? =
            RubySpot(x = centre, y = y, size = at, stacked = false)
                .takeIf { it.isFree(characters, blocked) }
        val bandLeft = centre - size * characters / 2f
        val bandRight = centre + size * characters / 2f

        // A horizontal reading is centred over the characters, so it is laid out around the middle
        // of the stretch rather than from its start.
        placed(size, top - size)?.let { return it }
        shrunkToFit(size, freeHeight(blocked, top, toBottom = false, bandLeft, bandRight))
            ?.let { fitted -> placed(fitted, top - fitted)?.let { return it } }
        placed(size, top + height)?.let { return it }
        shrunkToFit(size, freeHeight(blocked, top + height, toBottom = true, bandLeft, bandRight))
            ?.let { fitted -> placed(fitted, top + height)?.let { return it } }
        return null
    }

    /**
     * The reading shrunk to [free] room, leaving [AIR] of clear space on each side, or null when not
     * even [MIN_SIZE] fits there. Unlimited room keeps the full [size].
     *
     * The room is measured across the reading's **full-size** box. Every smaller reading sits inside
     * it — a shorter stack starts no higher and ends no lower, and a narrower line stays centred — so
     * one measurement covers every size the reading can end up at, including the pushed-up placement
     * near the end of a column.
     */
    private fun shrunkToFit(size: Float, free: Float): Float? =
        if (!free.isFinite()) size else minOf(size, free - 2f * AIR).takeIf { it >= MIN_SIZE }

    /** Room from [edge] to the nearest box on that side, across [bandTop, bandBottom). */
    private fun freeWidth(
        blocked: List<RubyBox>,
        edge: Float,
        toRight: Boolean,
        bandTop: Float,
        bandBottom: Float,
    ): Float {
        var nearest = if (toRight) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
        for (box in blocked) {
            if (box.bottom <= bandTop || box.top >= bandBottom) continue
            if (toRight) {
                if (box.left >= edge) nearest = minOf(nearest, box.left)
            } else {
                if (box.right <= edge) nearest = maxOf(nearest, box.right)
            }
        }
        if (!nearest.isFinite()) return Float.POSITIVE_INFINITY
        return if (toRight) nearest - edge else edge - nearest
    }

    /** Room from [edge] to the nearest box above or below, across [bandLeft, bandRight). */
    private fun freeHeight(
        blocked: List<RubyBox>,
        edge: Float,
        toBottom: Boolean,
        bandLeft: Float,
        bandRight: Float,
    ): Float {
        var nearest = if (toBottom) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
        for (box in blocked) {
            if (box.right <= bandLeft || box.left >= bandRight) continue
            if (toBottom) {
                if (box.top >= edge) nearest = minOf(nearest, box.top)
            } else {
                if (box.bottom <= edge) nearest = maxOf(nearest, box.bottom)
            }
        }
        if (!nearest.isFinite()) return Float.POSITIVE_INFINITY
        return if (toBottom) nearest - edge else edge - nearest
    }

    /** The room the reading will take: as thin as one glyph, as long as its kana. */
    private fun RubySpot.area(characters: Int): RubyBox = if (stacked) {
        RubyBox(x, y, x + size, y + size * characters)
    } else {
        RubyBox(x - size * characters / 2f, y, x + size * characters / 2f, y + size)
    }

    private fun RubySpot.isFree(characters: Int, blocked: List<RubyBox>): Boolean {
        val area = area(characters)
        return blocked.none { it.overlaps(area) }
    }
}

/** A rectangle on screen, used to keep a reading off the text it must not cover (R57). */
data class RubyBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    /** True when the two share any pixels. Boxes that merely touch do not fight for the page. */
    fun overlaps(other: RubyBox): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
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
