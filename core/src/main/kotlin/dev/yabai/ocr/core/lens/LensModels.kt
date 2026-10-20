package dev.yabai.ocr.core.lens

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

/** A recognised line of text plus where it sits in the submitted image. */
data class LensSegment(val text: String, val box: PixelBox?)

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

    /** Lines joined in reading order. */
    fun joinLines(): String = segments
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { it.text }
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
