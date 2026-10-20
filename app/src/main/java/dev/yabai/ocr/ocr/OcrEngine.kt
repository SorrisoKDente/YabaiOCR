package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * One piece of a block: a column of vertical text, a row of horizontal text, with where it was
 * printed. The readings of a piece are drawn beside this box, so a reading lands next to the
 * characters it belongs to and not next to the block as a whole.
 */
data class OcrPiece(val text: String, val rect: Rect?)

/**
 * One tappable chunk of recognised text.
 *
 * [text] is the block as one flowing sentence, ready to copy or translate, and [lines] are the raw
 * lines the engine reported for it. [rect] is in the coordinate space of the bitmap that was handed
 * to the engine — the same space as the crop the service took — or null when the engine did not
 * report a position, in which case the block can still be part of the joined text but cannot be
 * offered as something to tap on screen. [pieces] are those same columns or rows, each with its own
 * box, for drawing the reading where the text is.
 */
data class OcrBlock(
    val text: String,
    val lines: List<String>,
    val rect: Rect?,
    val pieces: List<OcrPiece> = emptyList(),
)

/** What the UI needs to know about one recognition run. */
data class OcrOutcome(
    val text: String,
    val lines: List<String>,
    val engineLabel: String,
    val language: String?,
    val elapsedMs: Long,
    /** True when the preferred engine failed and an offline one answered instead. */
    val fallback: Boolean = false,
    val warning: String? = null,
    /**
     * Text blocks with their positions. In manga one of these is a speech balloon and the engine
     * reports it as several columns, already joined back together; that is the granularity the
     * on-screen picker offers.
     */
    val blocks: List<OcrBlock> = emptyList(),
)

interface OcrEngine {
    val label: String
    val requiresNetwork: Boolean
    suspend fun recognize(bitmap: Bitmap): OcrOutcome
    fun close() = Unit
}
