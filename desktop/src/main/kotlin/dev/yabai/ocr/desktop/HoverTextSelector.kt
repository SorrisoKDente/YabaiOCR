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
 * OCR returns whole lines with one box each, not one box per word. The line box is divided evenly
 * between its characters — the same arithmetic [KanjiGrid] already uses for tapping a character —
 * and the line is segmented by the dictionary engine. The hit whose `[start, end)` contains the
 * pointer's character is the word under the cursor; if the pointer is on punctuation, the nearest
 * hit within a couple of characters is used instead.
 *
 * Keeping this separate from screen capture and Lens makes the part that decides *what* to look up
 * testable without a screen, a network call or a dictionary.
 */
object HoverTextSelector {

    /** How far from a line's box the pointer may be and still select that line. */
    const val SEGMENT_TOLERANCE_PX = 36.0

    /** How many characters away a hit may be when the pointer is on punctuation. */
    const val TOKEN_SNAP_CHARS = 3

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
                tokenBox = KanjiGrid.boxOf(segment.text, box, match.start, match.end),
            )
        }
        return null
    }

    /** Character index of [point] along a line box, clamped to the text. */
    fun characterIndex(text: String, box: PixelBox, point: Point): Int {
        if (text.isEmpty()) return 0
        val count = text.length
        val index = if (isVertical(box)) {
            val step = box.height.toDouble() / count
            ((point.y - box.y) / step).toInt()
        } else {
            val step = box.width.toDouble() / count
            ((point.x - box.x) / step).toInt()
        }
        return index.coerceIn(0, count - 1)
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
}
