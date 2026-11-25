package dev.yabai.ocr.capture

import android.view.Gravity
import android.view.Surface

/**
 * The window side that shows each edge of the phone, for the camera's shutter and ✕ (R48p).
 *
 * Upright, the shutter is centred on the phone's bottom edge and the ✕ in its top-right corner.
 * The window turns with the screen, so the display rotation — the opposite of the phone's turn
 * (R48f) — says which side of the window shows those edges. The icons themselves stay upright.
 */
internal object CameraControls {

    enum class Side { TOP, BOTTOM, START, END }

    /** The window sides the two controls are pinned to. */
    data class Placement(
        val shutterSide: Side,
        val closeVertical: Side,
        val closeHorizontal: Side,
    ) {
        val shutterGravity: Int
            get() = when (shutterSide) {
                Side.TOP, Side.BOTTOM -> gravity(shutterSide) or Gravity.CENTER_HORIZONTAL
                Side.START, Side.END -> gravity(shutterSide) or Gravity.CENTER_VERTICAL
            }

        val closeGravity: Int get() = gravity(closeVertical) or gravity(closeHorizontal)
    }

    /** The placement for the display's rotation relative to the phone's natural frame. */
    fun placement(displayRotation: Int): Placement {
        val rotation = ((displayRotation % 360) + 360) % 360
        return when (rotation) {
            // Phone turned left: its bottom edge is the window's right side, its top-right corner
            // the window's top-left one.
            Surface.ROTATION_90 -> Placement(Side.END, Side.TOP, Side.START)
            Surface.ROTATION_180 -> Placement(Side.TOP, Side.BOTTOM, Side.START)
            Surface.ROTATION_270 -> Placement(Side.START, Side.BOTTOM, Side.END)
            else -> Placement(Side.BOTTOM, Side.TOP, Side.END)
        }
    }

    private fun gravity(side: Side): Int = when (side) {
        Side.TOP -> Gravity.TOP
        Side.BOTTOM -> Gravity.BOTTOM
        Side.START -> Gravity.START
        Side.END -> Gravity.END
    }
}
