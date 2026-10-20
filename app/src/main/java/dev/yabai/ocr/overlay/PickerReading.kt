package dev.yabai.ocr.overlay

import android.graphics.Rect

/**
 * How the picker shows the readings of the text it is boxing.
 *
 * The two ways are deliberately both available: a **ruby** is the reading drawn beside the kanji,
 * the way the page itself would print it, and a **label** is a readable strip with the balloon's
 * sentence and its readings in parentheses. The first keeps the page intact but is as small as
 * printed ruby; the second is easy to read but covers part of the drawing. Which one is better is a
 * question nobody can answer from a screenshot, so the user picks.
 */
enum class PickerReadingMode { OFF, RUBY, LABEL }

/** One reading to draw: where in the piece it belongs, as a fraction of the piece's length. */
data class PickerReading(val text: String, val from: Float, val to: Float)

/** A column or row of a block, on screen, with the readings of the characters in it. */
data class PickerPiece(val rect: Rect, val vertical: Boolean, val readings: List<PickerReading>)

/**
 * One box the picker offers, ready to draw.
 *
 * [rect] is where it sits on screen, [label] its sentence with the readings written in — only filled
 * in [PickerReadingMode.LABEL] — and [pieces] the columns or rows with the readings placed on them —
 * only filled in [PickerReadingMode.RUBY].
 */
data class PickerBlock(
    val rect: Rect,
    val label: String? = null,
    val pieces: List<PickerPiece> = emptyList(),
)
