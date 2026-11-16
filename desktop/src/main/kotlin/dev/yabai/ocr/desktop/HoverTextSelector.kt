package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.layout.KanjiGrid
import dev.yabai.ocr.core.layout.ReadingOrder
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

    /**
     * How close to a line's edge the pointer has to be for a word to be looked up across lines.
     *
     * A word that started in the previous line leaves at most a few of its characters at the head of
     * this one; anywhere else the word under the pointer is complete in this line, and joining lines
     * would only invent words.
     */
    const val BRIDGE_EDGE_CHARS = 3

    /**
     * How many characters of the neighbouring line are carried into the candidate.
     *
     * A dictionary term never exceeds `LookupEngine.MAX_TERM_LENGTH` (16), so a longer tail cannot
     * contribute to a hit and only makes the candidate string bigger.
     */
    const val BRIDGE_CHARS = 16

    /**
     * How far apart two lines may be and still be neighbours, in glyphs.
     *
     * Larger than the block builder's 0.75 on purpose: a notepad (and a book) sets its lines about a
     * glyph and a quarter apart, so a wrapped word's other half measured 19 px from a 25 px line and
     * was refused as «another block» — the hover worked in some layouts and not in others, and
     * adding a couple of characters to the line was enough to flip it. Two balloons of a manga are
     * further apart than this, and a word still has to be longer than the line's own to win.
     */
    private const val MERGE_GAP_RATIO = 1.25

    /** Advance of a full-width Japanese cell; the unit every other weight is relative to. */
    private const val FULL_WIDTH = 1.0

    /** Letters, digits and ASCII punctuation: about half a Japanese square. */
    private const val HALF_WIDTH = 0.55

    /** A space is narrower than a letter in most fonts. */
    private const val SPACE_WIDTH = 0.34

    /**
     * The selection itself. The lookup is injected rather than a [LookupEngine] taken directly: the
     * engine signature (the match cap, the scope) belongs to the caller, and the geometry can be
     * tested without a compiled dictionary on disk.
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
            val index = characterIndex(segment.text, box, imagePoint)
            val inside = distanceToBox(imagePoint, box) <= 0.0

            val matches = lookup(segment.text, only)
            val covering = matches.firstOrNull { index >= it.start && index < it.end }
            // Snapping is for punctuation and blanks, where there is no word to answer with. A
            // kanji or kana that finds nothing is the head of a word printed in the next line, and
            // snapping answered with a word *above* it — always a neighbour, never the pointer's.
            val snapped = covering ?: if (snapAllowed(segment.text, index)) {
                matches
                    .filter { distanceToRange(index, it.start, it.end) <= TOKEN_SNAP_CHARS }
                    .minByOrNull { distanceToRange(index, it.start, it.end) }
            } else null
            val direct = snapped?.let { match ->
                HoverHit(match, segment, tokenBox(segment.text, box, match.start, match.end))
            }

            // A word printed across the line boundary is only a better answer when it is *longer*
            // than what this line alone offers: standing near an edge is no reason to throw away a
            // complete word the pointer is on.
            val crossed = crossingMatch(result, segment, index, box, lookup, only)
            val chosen = when {
                crossed != null && (direct == null || crossed.span > direct.match.span()) -> crossed.hit
                else -> direct
            }
            if (chosen != null) return chosen

            // The pointer is inside a line that *does* have entries, yet none covers the character.
            // Falling through to the next candidate then named a word from another column, and the
            // card looked like it was reading the wrong text. A line with no entries at all keeps
            // the old behaviour: it does not swallow a pointer that a nearby line can answer.
            if (inside && matches.isNotEmpty()) return null
        }
        return null
    }

    /** Whether the character at [index] may answer with a nearby word instead of its own. */
    private fun snapAllowed(text: String, index: Int): Boolean {
        val character = text.getOrNull(index) ?: return false
        return !character.isLetterOrDigit()
    }

    /** A hit whose word begins in one printed line and ends in the next one of the same block. */
    private class Crossing(val hit: HoverHit, val span: Int)

    private fun TokenMatch.span(): Int = end - start

    /**
     * The word under the pointer when it is split between two lines (or two columns) of one block.
     *
     * The lines are taken from `LensResult.blocks[].pieces`, which are already in reading order and
     * already have the furigana readings removed: with the raw segments a reading printed beside a
     * kanji would be glued into the sentence. Which neighbour to join follows that same order —
     * below in horizontal text, to the *right* in vertical text — so «next» is never guessed from
     * screen coordinates.
     */
    private fun crossingMatch(
        result: LensResult,
        segment: LensSegment,
        index: Int,
        box: PixelBox,
        lookup: (String, Set<String>) -> List<TokenMatch>,
        only: Set<String>,
    ): Crossing? {
        val text = segment.text
        if (text.isEmpty()) return null
        val (pieces, position) = neighbourhoodOf(result, segment) ?: return null

        val nearHead = index < BRIDGE_EDGE_CHARS
        val nearTail = index >= text.length - BRIDGE_EDGE_CHARS
        if (!nearHead && !nearTail) return null

        val before = if (nearHead) joinWithPrevious(pieces, position, text, box, index, lookup, only) else null
        val after = if (nearTail) joinWithNext(pieces, position, text, box, index, lookup, only) else null
        return listOfNotNull(before, after).maxByOrNull { it.span }
    }

    /** The line, its position, and the lines around it in reading order. */
    private fun neighbourhoodOf(
        result: LensResult,
        segment: LensSegment,
    ): Pair<List<LensSegment>, Int>? {
        val pieces = blockPiecesOf(result, segment)
        if (pieces != null && pieces.size >= 2) {
            val position = pieces.indexOfFirst { it.text == segment.text && it.box == segment.box }
            if (position >= 0) return pieces to position
        }
        // Lens does not always put the columns of one vertical sentence in the same block: in a
        // novel each column often arrives as its own block, which is why the feature looked dead
        // there. The raw segments are no help for the order either — Lens reports the columns of a
        // page in any arrangement, and «the next one in the list» was then the column on the
        // *right*, the one already read. The geometry knows the order, so it is worked out from the
        // boxes; only the lines it says are adjacent are joined, so a different balloon still
        // cannot be glued on.
        val all = result.segments.filter { it.text.isNotBlank() && it.box != null }
        val ordered = if (all.size > 1) {
            ReadingOrder.order(all.map { it.box }).map { all[it] }
        } else {
            all
        }
        val position = ordered.indexOfFirst { it.text == segment.text && it.box == segment.box }
        if (position < 0) return null
        return ordered to position
    }

    /** The block that holds [segment], with its pieces in reading order. */
    private fun blockPiecesOf(result: LensResult, segment: LensSegment): List<LensSegment>? =
        result.blocks
            .firstOrNull { block ->
                block.pieces.any { piece -> piece.text == segment.text && piece.box == segment.box }
            }
            ?.pieces
            ?.filter { it.text.isNotBlank() && it.box != null }
            ?.takeIf { it.isNotEmpty() }

    /**
     * Whether two printed lines are neighbours on the page.
     *
     * Same direction, a shared band across it, and a gap along it shorter than one glyph — the rule
     * the block builder uses. A word is never joined with a line from another balloon: those are
     * separated by more than a glyph of white space, or sit on a different band.
     */
    internal fun adjacent(first: PixelBox, second: PixelBox): Boolean {
        val firstVertical = isVertical(first)
        if (firstVertical != isVertical(second)) return false
        return if (firstVertical) {
            val shared = minOf(first.bottom, second.bottom) - maxOf(first.y, second.y)
            if (shared < 0.5 * minOf(first.height, second.height)) return false
            maxOf(second.x - first.right, first.x - second.right) <= MERGE_GAP_RATIO * maxOf(first.width, second.width)
        } else {
            val shared = minOf(first.right, second.right) - maxOf(first.x, second.x)
            if (shared < 0.5 * minOf(first.width, second.width)) return false
            maxOf(second.y - first.bottom, first.y - second.bottom) <= MERGE_GAP_RATIO * maxOf(first.height, second.height)
        }
    }

    private fun joinWithPrevious(
        pieces: List<LensSegment>,
        position: Int,
        text: String,
        box: PixelBox,
        index: Int,
        lookup: (String, Set<String>) -> List<TokenMatch>,
        only: Set<String>,
    ): Crossing? {
        val previous = pieces.getOrNull(position - 1) ?: return null
        val previousBox = previous.box ?: return null
        if (!adjacent(previousBox, box)) return null
        val carried = previous.text.takeLast(BRIDGE_CHARS)
        if (carried.isEmpty()) return null

        val joined = carried + text
        val boundary = carried.length
        val pointer = boundary + index
        val match = crossingMatchIn(joined, boundary, pointer, lookup(joined, only)) ?: return null

        val head = tokenBox(
            previous.text,
            previousBox,
            previous.text.length - carried.length + match.start,
            previous.text.length,
        )
        val tail = tokenBox(text, box, 0, match.end - boundary)
        // `line` stays the one under the pointer: it is what the card anchors itself to.
        val pointerLine = pieces[position]
        return Crossing(HoverHit(match, pointerLine, union(head, tail)), match.span())
    }

    private fun joinWithNext(
        pieces: List<LensSegment>,
        position: Int,
        text: String,
        box: PixelBox,
        index: Int,
        lookup: (String, Set<String>) -> List<TokenMatch>,
        only: Set<String>,
    ): Crossing? {
        val next = pieces.getOrNull(position + 1) ?: return null
        val nextBox = next.box ?: return null

        if (!adjacent(box, nextBox)) return null
        val following = next.text.take(BRIDGE_CHARS)
        if (following.isEmpty()) return null

        val joined = text + following
        val boundary = text.length
        val match = crossingMatchIn(joined, boundary, index, lookup(joined, only)) ?: return null

        val head = tokenBox(text, box, match.start, text.length)
        val tail = tokenBox(next.text, nextBox, 0, match.end - boundary)
        val pointerLine = pieces[position]
        return Crossing(HoverHit(match, pointerLine, union(head, tail)), match.span())
    }

    /**
     * The match of [joined] that covers [pointer] and really crosses the boundary.
     *
     * A token that begins and ends on the same side is the word the line alone already had, and
     * accepting it here would move the highlight to a neighbour that is not part of the word.
     */
    private fun crossingMatchIn(
        joined: String,
        boundary: Int,
        pointer: Int,
        matches: List<TokenMatch>,
    ): TokenMatch? = matches
        .filter { pointer >= it.start && pointer < it.end }
        .filter { it.start < boundary && it.end > boundary }
        .filter { it.start >= 0 && it.end <= joined.length }
        .maxByOrNull { it.span() }

    /** The smallest box that contains both halves of a word split between two lines. */
    private fun union(first: PixelBox, second: PixelBox): PixelBox {
        val left = minOf(first.x, second.x)
        val top = minOf(first.y, second.y)
        val right = maxOf(first.right, second.right)
        val bottom = maxOf(first.bottom, second.bottom)
        return PixelBox(left, top, right - left, bottom - top)
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
