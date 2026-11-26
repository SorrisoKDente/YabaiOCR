package dev.yabai.ocr.desktop

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a reading is drawn when the picker explains the text on screen.
 *
 * Ruby is set the way Japanese typesetting sets it: a small reading **above** a run of horizontal
 * text, centred on the run, and a small column **to the right** of a run of vertical text, one
 * kana under the other (in vertical Japanese the reading is printed in the gutter to the right,
 * which is also the side the eye leaves the column on).
 */
data class RubySpot(
    /** Left of the reading when [stacked], the centre of the run when it is not. */
    val x: Float,
    /** Top of the reading. */
    val y: Float,
    /** Font size in canvas pixels. */
    val size: Float,
    val stacked: Boolean,
)

object PickerRuby {

    /** Below this the reading is a smear: a smear is worse than no reading at all. */
    const val MIN_SIZE_PX = 7f

    /**
     * Ruby stops being ruby and becomes noise above this fraction of the glyph. The mobile picker
     * uses the same ratio, so the desktop and the phone set the same reading the same size.
     */
    const val SIZE_RATIO = 0.55f

    /** Air between the run and its reading, in canvas pixels. */
    const val GAP = 1f

    /** Air kept between the reading and the text it steps away from. */
    private const val AIR = 1f

    /**
     * Where to draw [characters] kana for a run, or null when there is no room for even a small
     * reading on either side.
     *
     * [span] is the part of the line the reading covers, already in canvas coordinates; [vertical]
     * says which side the reading goes to; [blocked] are the boxes of every other piece of text on
     * the page. The reading never covers them: it tries its full size on the ruby side, then the
     * same size on the other side, and when a gutter is narrower it **shrinks** into whatever room
     * is left, down to [MIN_SIZE_PX] (R57, R58).
     */
    fun spot(
        span: Rectangle,
        vertical: Boolean,
        characters: Int,
        blocked: List<Rectangle> = emptyList(),
    ): RubySpot? {
        if (characters <= 0 || span.width <= 0 || span.height <= 0) return null

        if (vertical) {
            // As thick as the run's glyphs, and never taller than the run can hold: five kana
            // beside two kanji are exactly the real case, and crushing them into the run's own
            // length would make them unreadable.
            val size = min(span.width * SIZE_RATIO, span.height.toFloat() / characters)
            if (size < MIN_SIZE_PX) return null
            fun placed(at: Float, x: Float): RubySpot? =
                RubySpot(x = x, y = span.y.toFloat(), size = at, stacked = true)
                    .takeIf { it.isFree(characters, blocked) }

            val right = span.x + span.width
            val bandTop = span.y.toFloat()
            val bandBottom = bandTop + size * characters
            placed(size, right + span.width * 0.15f)?.let { return it }
            shrunkToFit(size, freeWidth(blocked, right.toFloat(), toRight = true, bandTop, bandBottom))
                ?.let { fitted -> placed(fitted, right + AIR)?.let { return it } }

            placed(size, span.x - span.width * 0.15f - size)?.let { return it }
            shrunkToFit(size, freeWidth(blocked, span.x.toFloat(), toRight = false, bandTop, bandBottom))
                ?.let { fitted -> placed(fitted, span.x - AIR - fitted)?.let { return it } }
            return null
        }

        val size = span.height * SIZE_RATIO
        if (size < MIN_SIZE_PX) return null
        val centre = span.x + span.width / 2f
        fun placed(at: Float, y: Float): RubySpot? =
            RubySpot(x = centre, y = y, size = at, stacked = false)
                .takeIf { it.isFree(characters, blocked) }
        val bandLeft = centre - size * characters / 2f
        val bandRight = centre + size * characters / 2f

        placed(size, span.y - size - GAP)?.let { return it }
        shrunkToFit(size, freeHeight(blocked, span.y.toFloat(), toBottom = false, bandLeft, bandRight))
            ?.let { fitted -> placed(fitted, span.y - fitted - GAP)?.let { return it } }
        placed(size, span.y + span.height + GAP)?.let { return it }
        shrunkToFit(size, freeHeight(blocked, (span.y + span.height).toFloat(), toBottom = true, bandLeft, bandRight))
            ?.let { fitted -> placed(fitted, span.y + span.height + GAP)?.let { return it } }
        return null
    }

    /**
     * The reading shrunk to [free] room, leaving [AIR] of clear space on each side, or null when not
     * even [MIN_SIZE_PX] fits there. Unlimited room keeps the full [size].
     *
     * The room is measured across the reading's **full-size** box: every smaller reading sits inside
     * it (a narrower line stays centred on the same characters), so one measurement covers them all.
     */
    private fun shrunkToFit(size: Float, free: Float): Float? =
        if (!free.isFinite()) size else minOf(size, free - 2f * AIR).takeIf { it >= MIN_SIZE_PX }

    /** Room from [edge] to the nearest box on that side, across [bandTop, bandBottom). */
    private fun freeWidth(
        blocked: List<Rectangle>,
        edge: Float,
        toRight: Boolean,
        bandTop: Float,
        bandBottom: Float,
    ): Float {
        var nearest = if (toRight) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
        for (box in blocked) {
            if (box.y + box.height <= bandTop || box.y >= bandBottom) continue
            if (toRight) {
                if (box.x >= edge) nearest = minOf(nearest, box.x.toFloat())
            } else {
                if (box.x + box.width <= edge) nearest = maxOf(nearest, (box.x + box.width).toFloat())
            }
        }
        if (!nearest.isFinite()) return Float.POSITIVE_INFINITY
        return if (toRight) nearest - edge else edge - nearest
    }

    /** Room from [edge] to the nearest box above or below, across [bandLeft, bandRight). */
    private fun freeHeight(
        blocked: List<Rectangle>,
        edge: Float,
        toBottom: Boolean,
        bandLeft: Float,
        bandRight: Float,
    ): Float {
        var nearest = if (toBottom) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
        for (box in blocked) {
            if (box.x + box.width <= bandLeft || box.x >= bandRight) continue
            if (toBottom) {
                if (box.y >= edge) nearest = minOf(nearest, box.y.toFloat())
            } else {
                if (box.y + box.height <= edge) nearest = maxOf(nearest, (box.y + box.height).toFloat())
            }
        }
        if (!nearest.isFinite()) return Float.POSITIVE_INFINITY
        return if (toBottom) nearest - edge else edge - nearest
    }

    /** The room the reading will take: as thick as one glyph, as long as its kana. */
    private fun RubySpot.area(characters: Int): Rectangle = if (stacked) {
        Rectangle(
            x.roundToInt(),
            y.roundToInt(),
            size.roundToInt().coerceAtLeast(1),
            (size * characters).roundToInt().coerceAtLeast(1),
        )
    } else {
        val width = (size * characters).roundToInt().coerceAtLeast(1)
        Rectangle(
            (x - width / 2f).roundToInt(),
            y.roundToInt(),
            width,
            size.roundToInt().coerceAtLeast(1),
        )
    }

    private fun RubySpot.isFree(characters: Int, blocked: List<Rectangle>): Boolean {
        val area = area(characters)
        return blocked.none { it.intersects(area) }
    }
}

object PickerLabels {

    /** Room between the box and the strip, and between two strips. */
    const val GAP = 6

    /** How many strips away from its box a crowded strip may go before trying the next side. */
    private const val MAX_STEPS = 24

    /**
     * Where the readable strip of a block goes, given the room on screen.
     *
     * To the **right** of the box when it fits — the side the eye leaves a column of vertical text
     * on —, then to the left, then below, then above, clamped to the screen in every case. A strip
     * that overlaps another block is worse than one pushed along its side, and a strip drawn half
     * off-screen is worse than both, so a crowded strip first steps away from its own box and only
     * then gives up the side.
     */
    fun place(
        box: Rectangle,
        strip: Dimension,
        screen: Dimension,
        taken: List<Rectangle> = emptyList(),
    ): Point {
        val width = strip.width.coerceAtLeast(1)
        val height = strip.height.coerceAtLeast(1)

        // Anchored corner of each side, and whether crowding moves the strip sideways.
        val sides = listOf(
            Side(box.x + box.width + GAP, box.y, sideways = false),
            Side(box.x - GAP - width, box.y, sideways = false),
            Side(box.x, box.y + box.height + GAP, sideways = true),
            Side(box.x, box.y - GAP - height, sideways = true),
        )

        for (side in sides) {
            for (offset in offsets()) {
                val step = if (side.sideways) width + GAP else height + GAP
                val left = side.left + if (side.sideways) offset * step else 0
                val top = side.top + if (side.sideways) 0 else offset * step
                if (left < 0 || top < 0 || left + width > screen.width || top + height > screen.height) {
                    continue
                }
                val rect = Rectangle(left, top, width, height)
                if (taken.any { it.intersects(rect) }) continue
                return Point(left, top)
            }
        }

        // Crowded everywhere: the plain rule, clamped, so a strip is never lost.
        val left = when {
            box.x + box.width + GAP + width <= screen.width -> box.x + box.width + GAP
            box.x - GAP - width >= 0 -> box.x - GAP - width
            else -> box.x
        }
        val top = when {
            box.x + box.width + GAP + width <= screen.width || box.x - GAP - width >= 0 -> box.y
            box.y + box.height + GAP + height <= screen.height -> box.y + box.height + GAP
            else -> box.y - GAP - height
        }
        return Point(
            left.coerceIn(0, (screen.width - width).coerceAtLeast(0)),
            top.coerceIn(0, (screen.height - height).coerceAtLeast(0)),
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

    private data class Side(val left: Int, val top: Int, val sideways: Boolean)
}
