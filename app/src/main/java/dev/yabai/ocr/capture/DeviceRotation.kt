package dev.yabai.ocr.capture

import android.view.Surface

/**
 * The turn a photo has to be written in, from how the phone is held (R48f).
 *
 * `OrientationEventListener` reports the phone's own tilt, in degrees, while CameraX expects one of
 * the `Surface.ROTATION_*` values — and the two are numbered in opposite directions: a phone turned
 * 90° counter-clockwise is `ROTATION_270`. This is the mapping Android's camera samples use, and one
 * range the wrong way round turns every photo taken in that position by 180°.
 *
 * Kept apart from the screen so the four ranges can be measured without a camera or a device.
 */
internal object DeviceRotation {

    /** What `OrientationEventListener` reports while the phone is lying flat. */
    const val UNKNOWN = -1

    /** The `Surface.ROTATION_*` value for an orientation, in degrees, as reported by the sensor. */
    fun surfaceRotation(orientation: Int): Int = when (orientation) {
        in 45..134 -> Surface.ROTATION_270
        in 135..224 -> Surface.ROTATION_180
        in 225..314 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }

    /**
     * The turn, clockwise, the picker's page takes so it follows the phone (R48g).
     *
     * Zero whenever the page already has the phone's frame: the screen turned with the phone, or the
     * phone is held upright. A portrait window with the phone held sideways is the case this exists
     * for — «giro automático» off, or a display turn that has not landed yet. The page is then laid
     * out in the phone's frame, which is sideways to the window, and the picker turns it into the
     * window: the turn puts the picture's top where the world appears to be, on the right when the
     * phone's right side is up and on the left when its left side is.
     */
    fun pageTurn(rotation: Int, portraitWindow: Boolean): Int {
        if (!portraitWindow) return 0
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
}
