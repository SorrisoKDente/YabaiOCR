package dev.yabai.ocr.overlay

import android.graphics.Matrix

/**
 * The page turned into the picker's window (R48g).
 *
 * A photo's page is laid out in the frame the phone was held in — the rectangle the picture was
 * taken in — and that rectangle is the window only when the screen turned with the phone. With the
 * rotation locked it is sideways to the window: a 2400×1080 page inside a 1080×2400 window. The page
 * is turned to fill the window instead of being squashed into it, and everything drawn in page
 * pixels — the picture, the boxes, the readings — takes the same turn, so no box can drift off its
 * text.
 *
 * The arithmetic lives here, apart from the view, because this is exactly the part that goes wrong
 * silently: a turn the drawing has and the touch does not puts every box under the wrong finger.
 */
internal object PageTurn {

    /** Whether the page reaches the window with its sides exchanged. */
    fun sideways(degrees: Int): Boolean = normalized(degrees) % 180 != 0

    /** Page pixels → window pixels: the turn, then the stretch to the window (R23). */
    fun matrix(
        degrees: Int,
        pageWidth: Int,
        pageHeight: Int,
        windowWidth: Int,
        windowHeight: Int,
    ): Matrix {
        val turn = normalized(degrees)
        val turnedWidth = if (sideways(turn)) pageHeight else pageWidth
        val turnedHeight = if (sideways(turn)) pageWidth else pageHeight
        val scaleX = if (turnedWidth > 0) windowWidth.toFloat() / turnedWidth else 1f
        val scaleY = if (turnedHeight > 0) windowHeight.toFloat() / turnedHeight else 1f
        return Matrix().apply {
            postRotate(turn.toFloat())
            postScale(scaleX, scaleY)
            // A turned page no longer starts at its own origin: its top-left corner is off to the
            // left or above, and it has to be brought back to the window's corner.
            postTranslate(
                -left(turn, pageWidth, pageHeight) * scaleX,
                -top(turn, pageWidth, pageHeight) * scaleY,
            )
        }
    }

    /** Window pixels → page pixels: what a tap has to be converted with. */
    fun inverse(
        degrees: Int,
        pageWidth: Int,
        pageHeight: Int,
        windowWidth: Int,
        windowHeight: Int,
    ): Matrix = Matrix().also {
        matrix(degrees, pageWidth, pageHeight, windowWidth, windowHeight).invert(it)
    }

    /** The smallest x of the turned page, in page pixels. */
    private fun left(turn: Int, pageWidth: Int, pageHeight: Int): Float = when (turn) {
        90 -> -pageHeight.toFloat()
        180 -> -pageWidth.toFloat()
        else -> 0f
    }

    /** The smallest y of the turned page, in page pixels. */
    private fun top(turn: Int, pageWidth: Int, pageHeight: Int): Float = when (turn) {
        180 -> -pageHeight.toFloat()
        270 -> -pageWidth.toFloat()
        else -> 0f
    }

    /** 0, 90, 180 or 270 for any angle, in either direction. */
    private fun normalized(degrees: Int): Int = ((degrees % 360) + 360) % 360
}
