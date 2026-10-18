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

data class LensResult(
    /** BCP-47-ish language reported by Lens, e.g. "ja". Empty when unknown. */
    val language: String,
    val segments: List<LensSegment>,
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
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
