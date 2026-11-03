package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.LookupEngine
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.layout.KanjiGrid
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.core.lens.LensSegment
import dev.yabai.ocr.core.lens.PixelBox
import java.awt.Point
import kotlin.math.hypot

/** The dictionary hit that belongs to the word under the pointer, with the word's on-screen box. */
data class HoverHit(
    val match: TokenMatch,
    val line: LensSegment,
    /** Box of the hit inside the submitted crop, in the crop's pixel space. */
    val tokenBox: PixelBox,
)

/**
 * Decides which word of an OCR result the pointer is on.
 *
 * OCR returns whole lines with one box each, not one box per word. The line box is divided between
 * its characters — the same idea [KanjiGrid] uses for tapping a character — and the line is
 * segmented by the dictionary engine. The hit whose `[start, end)` contains the pointer's character
 * is the word under the cursor; if the pointer is on punctuation, the nearest hit within a couple of
 * characters is used instead.
 *
 * The division is **weighted**, not uniform. A line like «Inflection information: 走った» spends most
 * of its width on narrow Latin letters; counting them as full Japanese squares put the pointer
 * several characters away from 走 and the card answered the wrong word or nothing at all. Half-width
 * characters are counted as such, so the fraction of the box a character occupies follows the print.
 *
 * Keeping this separate from screen capture and Lens makes the part that decides *what* to look up
 * testable without a screen, a network call or a dictionary.
 */
object HoverTextSelector {

    /** How far from a line's box the pointer may be and still select that line. */
    const val SEGMENT_TOLERANCE_PX = 36.0

    /** How many characters away a hit may be when the pointer is on punctuation. */
    const val TOKEN_SNAP_CHARS = 3

    /** Advance of a full-width Japanese cell; the unit every other weight is relative to. */
    private const val FULL_WIDTH = 1.0

    /** Letters, digits and ASCII punctuation: about half a Japanese square. */
    private const val HALF_WIDTH = 0.55

    /** A space is narrower than a letter in most fonts. */
    private const val SPACE_WIDTH = 0.34

    fun select(
        result: LensResult,
        imagePoint: Point,
        engine: LookupEngine,
        only: Set<String> = emptySet(),
    ): HoverHit? = select(
        result,
        imagePoint,
        lookup = { text, filter -> engine.lookup(text, maxMatches = 96, only = filter) },
        only = only,
    )

    /**
     * Same selection with the lookup injected, so the geometry can be tested without a compiled
     * dictionary on disk.
     */
    fun select(
        result: LensResult,
        imagePoint: Point,
        lookup: (text: String, only: Set<String>) -> List<TokenMatch>,
        only: Set<String> = emptySet(),
    ): HoverHit? {
        // The nearest line wins, but a line with no dictionary hit must not swallow the pointer:
        // try the following ones within the tolerance instead of answering nothing.
        val candidates = result.segments
            .filter { it.text.isNotBlank() && it.box != null }
            .map { it to distanceToBox(imagePoint, it.box!!) }
            .filter { it.second <= SEGMENT_TOLERANCE_PX }
            .sortedBy { it.second }

        for ((segment, _) in candidates) {
            val box = segment.box ?: continue
            val matches = lookup(segment.text, only)
            if (matches.isEmpty()) continue

            val index = characterIndex(segment.text, box, imagePoint)
            val covering = matches.firstOrNull { index >= it.start && index < it.end }
            val match = covering ?: matches
                .filter { distanceToRange(index, it.start, it.end) <= TOKEN_SNAP_CHARS }
                .minByOrNull { distanceToRange(index, it.start, it.end) }
            if (match == null) continue

            return HoverHit(
                match = match,
                line = segment,
                tokenBox = tokenBox(segment.text, box, match.start, match.end),
            )
        }
        return null
    }

    /** Character index of [point] along a line box, clamped to the text. */
    fun characterIndex(text: String, box: PixelBox, point: Point): Int {
        if (text.isEmpty()) return 0
        return if (isVertical(box)) {
            // Vertical Japanese is a stack of full-width cells; no weighting needed.
            val step = box.height.toDouble() / text.length
            val index = ((point.y - box.y) / step).toInt()
            index.coerceIn(0, text.length - 1)
        } else {
            val fraction = ((point.x - box.x).toDouble() / box.width.coerceAtLeast(1))
                .coerceIn(0.0, 0.999_999)
            indexAtFraction(text, fraction)
        }
    }

    /**
     * Where characters `[from, to)` of [text] sit inside [box].
     *
     * Horizontal lines use the same weights as [characterIndex], so the box handed back is the one
     * that contains the pointer when the word was chosen by the pointer. Vertical lines keep
     * [KanjiGrid]'s even grid, which is exact for Japanese set in a column.
     */
    fun tokenBox(text: String, box: PixelBox, from: Int, to: Int): PixelBox {
        if (text.isEmpty() || box.width <= 0 || box.height <= 0) return box
        if (isVertical(box)) return KanjiGrid.boxOf(text, box, from, to)

        val start = from.coerceIn(0, text.length - 1)
        val end = to.coerceIn(start + 1, text.length)
        val total = weight(text)
        if (total <= 0.0) return box

        val before = weight(text.substring(0, start))
        val inside = weight(text.substring(start, end))
        val left = box.x + (box.width * (before / total)).toInt()
        val right = box.x + (box.width * ((before + inside) / total)).toInt()
        return PixelBox(left, box.y, (right - left).coerceAtLeast(1), box.height)
    }

    /** A column of vertical Japanese is taller than it is wide; anything else is read across. */
    fun isVertical(box: PixelBox): Boolean = box.height >= box.width

    /** Euclidean distance from [point] to the nearest edge of [box]; 0 inside it. */
    fun distanceToBox(point: Point, box: PixelBox): Double {
        val dx = when {
            point.x < box.x -> box.x - point.x.toDouble()
            point.x >= box.right -> point.x - box.right.toDouble()
            else -> 0.0
        }
        val dy = when {
            point.y < box.y -> box.y - point.y.toDouble()
            point.y >= box.bottom -> point.y - box.bottom.toDouble()
            else -> 0.0
        }
        return hypot(dx, dy)
    }

    /** Distance in characters from [index] to `[start, end)`; 0 when the index is inside it. */
    fun distanceToRange(index: Int, start: Int, end: Int): Int = when {
        index < start -> start - index
        index >= end -> index - end + 1
        else -> 0
    }

    /**
     * The character that occupies [fraction] of the line's width.
     *
     * Latin text and kana do not advance the same amount, so the fraction is mapped through the
     * character weights instead of `fraction * length`.
     */
    private fun indexAtFraction(text: String, fraction: Double): Int {
        val total = weight(text)
        if (total <= 0.0) return 0
        val target = fraction * total
        var edge = 0.0
        for (index in text.indices) {
            edge += weight(text[index])
            if (target < edge) return index
        }
        return text.length - 1
    }

    /** Approximate advance width of one character, in full-width cells. */
    private fun weight(character: Char): Double = when {
        character == ' ' || character == '\t' -> SPACE_WIDTH
        character.code < 0x2E80 -> HALF_WIDTH
        else -> FULL_WIDTH
    }

    private fun weight(text: String): Double {
        var total = 0.0
        for (character in text) total += weight(character)
        return total
    }
}
