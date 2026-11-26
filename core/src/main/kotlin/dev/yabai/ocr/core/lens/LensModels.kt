package dev.yabai.ocr.core.lens

import dev.yabai.ocr.core.layout.ReadingOrder

/** Image bytes already encoded for transport, plus the dimensions they really have. */
class EncodedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mime: String = "image/png",
)

/** Axis-aligned box in pixels, relative to the image that was submitted. */
data class PixelBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

/**
 * A stretch of a line the page itself already prints a reading over, in characters.
 *
 * The picker draws readings from the dictionary, and drawing one over characters that already carry
 * a printed reading would double the ruby and cover the print. [start] is the first character and
 * [count] how many it spans.
 */
data class PrintedReading(val start: Int, val count: Int) {

    /** True when the characters `[start, end)` are at least partly covered by this reading. */
    fun covers(start: Int, end: Int): Boolean = start < this.start + count && this.start < end
}

/** True when any of the page's own readings covers the characters `[start, end)`. */
fun List<PrintedReading>.covers(start: Int, end: Int): Boolean = any { it.covers(start, end) }

/**
 * The readings worth drawing for a piece: the ones the page does not already print itself.
 *
 * [start] and [end] read the character span of one reading, which is what the dictionary hands out
 * (the mobile and the desktop picker use different shapes for the same thing). A reading that even
 * partly covers printed furigana is dropped: the print is already there, and a second reading over it
 * would double the ruby and cover the page (R57).
 */
fun <T> List<T>.withoutPrintedReadings(
    printed: List<PrintedReading>,
    start: (T) -> Int,
    end: (T) -> Int,
): List<T> = if (printed.isEmpty()) this else filterNot { printed.covers(start(it), end(it)) }

/** A recognised line of text plus where it sits in the submitted image. */
data class LensSegment(
    val text: String,
    val box: PixelBox?,
    /** The stretches of [text] the page already prints a reading over, if any. */
    val printed: List<PrintedReading> = emptyList(),
)

/**
 * One text block — in manga, one speech balloon — plus where it sits.
 *
 * This is what the on-screen picker offers: Lens splits a page down to single columns, and a column
 * is too thin to tap and only a fragment of a sentence. [lines] are those columns as Lens reported
 * them, [pieces] the same columns with their boxes, in reading order, and [text] is them joined back
 * into the sentence they make up.
 */
data class LensBlock(
    val text: String,
    val lines: List<String>,
    val box: PixelBox?,
    /**
     * The columns (or rows) of the block with their boxes, readings excluded and in reading order.
     * Whoever draws a reading where the text is — beside a column, above a row — needs to know where
     * each stretch of the sentence was printed.
     */
    val pieces: List<LensSegment> = emptyList(),
)

data class LensResult(
    /** BCP-47-ish language reported by Lens, e.g. "ja". Empty when unknown. */
    val language: String,
    val segments: List<LensSegment>,
    /** One entry per paragraph, in reading order. */
    val blocks: List<LensBlock>,
    /** Wall-clock duration of the whole round trip, for the UI. */
    val elapsedMs: Long,
) {
    val isEmpty: Boolean get() = segments.none { it.text.isNotBlank() }

    /**
     * Lines joined in reading order.
     *
     * The order comes from the boxes, not from the list: Lens reports the columns of a vertical page
     * in its own arrangement, and the panel printed «え入れる。» *above* «…血の絨毯がまた誰かを迎» — the
     * sentence cut and its second half shown first. [ReadingOrder] is the rule the blocks are built
     * with as well.
     */
    fun joinLines(): String {
        val lines = segments.filter { it.text.isNotBlank() }
        if (lines.size < 2) return lines.joinToString("\n") { it.text }
        return ReadingOrder.order(lines.map { it.box })
            .joinToString("\n") { lines[it].text }
    }
}

class LensException(
    message: String,
    val httpStatus: Int = -1,
    /**
     * Value of the `Retry-After` header in seconds, when the server sent one.
     *
     * Google only sends this on the responses that refuse us (429/403); a successful call carries
     * no rate-limit information at all.
     */
    val retryAfterSeconds: Int? = null,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
