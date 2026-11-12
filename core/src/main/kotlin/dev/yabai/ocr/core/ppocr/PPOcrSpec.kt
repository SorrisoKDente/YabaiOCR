package dev.yabai.ocr.core.ppocr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The numbers the PP-OCRv6_manga pipeline is documented to use.
 *
 * They are not guesses: the model card and the reference ONNX example ship exactly these, and the
 * detection map is only meaningful at the resolution it was trained for.
 */
object PPOcrSpec {
    /** Detection input long side. */
    const val DETECTION_LONG_SIDE = 960

    /** Pages longer than this ratio get the pixel budget of a 3:4 page instead of a long side. */
    const val STRIP_ASPECT = 2.0

    /** Binarisation threshold of the probability map. */
    const val MAP_THRESHOLD = 0.15f

    /** Minimum average confidence inside a candidate box. */
    const val BOX_THRESHOLD = 0.25f

    /** How far the shrunk DB kernel is grown back into the real glyph box. */
    const val UNCLIP_RATIO = 1.4f

    /** Every detection/resize step works on a multiple of this. */
    const val SIZE_ALIGN = 32

    /** Height the recogniser reads a line at. */
    const val RECOGNITION_HEIGHT = 48

    /** Shortest crop width the recogniser accepts, before the 48 px height scaling. */
    const val RECOGNITION_MIN_WIDTH = 16

    /** 0 = «blank» in the CTC vocabulary. */
    const val CTC_BLANK = 0
}

/**
 * A line of text the detector found, in the pixels of the image that was handed in.
 *
 * [vertical] says the line is taller than wide — printed top to bottom, the shape a manga balloon
 * usually has. The recogniser reads it after a quarter turn, so the flag is only for the caller.
 */
data class PPOcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val vertical: Boolean = bottom - top > right - left,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class PPOcrBlock(
    val text: String,
    val lines: List<String>,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val pieces: List<PPOcrLine>,
)

data class PPOcrOutcome(
    val text: String,
    val lines: List<String>,
    val elapsedMs: Long,
    val blocks: List<PPOcrBlock> = emptyList(),
    val detected: List<PPOcrLine> = emptyList(),
    val engineLabel: String = "PP-OCRv6",
)

/** The size the detector reads the page at, before the multiple-of-32 rounding. */
fun detectionRatio(width: Int, height: Int): Double {
    if (width <= 0 || height <= 0) return 1.0
    val longest = max(width, height).toDouble()
    val shortest = min(width, height).toDouble()
    return if (longest / shortest > PPOcrSpec.STRIP_ASPECT) {
        // Long webtoon strip: give it the pixel budget of a 3:4 page, not a 960 long side, or the
        // page is squeezed into a few pixels of height and nothing is detected.
        min(1.0, sqrt(0.75 * PPOcrSpec.DETECTION_LONG_SIDE * PPOcrSpec.DETECTION_LONG_SIDE / (shortest * longest)))
    } else {
        PPOcrSpec.DETECTION_LONG_SIDE / longest
    }
}

/** Detector input size: aligned to 32 and never below it. */
fun detectionSize(width: Int, height: Int): Pair<Int, Int> {
    val ratio = detectionRatio(width, height)
    val targetWidth = max((width * ratio / PPOcrSpec.SIZE_ALIGN).roundToInt() * PPOcrSpec.SIZE_ALIGN, PPOcrSpec.SIZE_ALIGN)
    val targetHeight = max((height * ratio / PPOcrSpec.SIZE_ALIGN).roundToInt() * PPOcrSpec.SIZE_ALIGN, PPOcrSpec.SIZE_ALIGN)
    return targetWidth to targetHeight
}

/** Recogniser input width for a crop [cropWidth] x [cropHeight] (already turned upright). */
fun recognitionWidth(cropWidth: Int, cropHeight: Int): Int {
    if (cropHeight <= 0) return PPOcrSpec.RECOGNITION_MIN_WIDTH
    return max(
        PPOcrSpec.RECOGNITION_MIN_WIDTH,
        (PPOcrSpec.RECOGNITION_HEIGHT.toDouble() * cropWidth / cropHeight).roundToInt(),
    )
}
