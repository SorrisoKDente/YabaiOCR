package dev.yabai.ocr.overlay

/**
 * Pinch zoom and pan for the picker, in page pixels.
 *
 * The page is the frozen capture: it is drawn at the size of the view (a whole-screen capture has one
 * pixel per screen pixel) and the boxes are drawn in the same coordinates. Scaling the canvas is
 * enough to magnify page, boxes and readings **together** — which is the point: a zoom that moved the
 * boxes away from the text would be worse than no zoom at all (R15).
 *
 * The arithmetic lives here, and not inside the view, because that is where a zoom goes wrong: the
 * point under the fingers has to stay under the fingers, and the page must not drift off the screen.
 * None of that needs a screen to be tested.
 */
class PickerZoom(
    /** Size of the page in view pixels at zoom 1. */
    var width: Float = 0f,
    var height: Float = 0f,
) {

    var scale: Float = MIN
        private set

    var offsetX: Float = 0f
        private set

    var offsetY: Float = 0f
        private set

    /** A new page (a new capture) starts unzoomed: its boxes are placed for the screen as it is. */
    fun reset(newWidth: Float, newHeight: Float) {
        width = newWidth
        height = newHeight
        scale = MIN
        offsetX = 0f
        offsetY = 0f
    }

    /**
     * Multiplies the zoom by [factor], keeping the page point under ([focusX], [focusY]) exactly where
     * it is. A pinch that drifts away from the fingers feels broken even when the scale is right.
     */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        if (factor <= 0f) return
        val next = (scale * factor).coerceIn(MIN, MAX)
        if (next == scale) return
        val pageX = toPageX(focusX)
        val pageY = toPageY(focusY)
        scale = next
        offsetX = focusX - pageX * scale
        offsetY = focusY - pageY * scale
        clamp()
    }

    /** Moves the page by a finger's travel, in view pixels. */
    fun panBy(dx: Float, dy: Float) {
        if (scale <= MIN) return
        offsetX += dx
        offsetY += dy
        clamp()
    }

    /** Where a point of the view is on the page: what a tap has to be tested against. */
    fun toPageX(x: Float): Float = (x - offsetX) / scale

    fun toPageY(y: Float): Float = (y - offsetY) / scale

    /**
     * Keeps the page covering the view: no blank edge while zoomed in, no drift while at 1×. The
     * offset of a page larger than the view is negative — it is how much of the page is off to the
     * left/top.
     */
    private fun clamp() {
        val minX = width - width * scale
        val minY = height - height * scale
        offsetX = offsetX.coerceIn(minX, 0f)
        offsetY = offsetY.coerceIn(minY, 0f)
    }

    companion object {
        /** No zoom: the page as the capture saw it. */
        const val MIN = 1f

        /** Enough to read the furigana of a 1080p page, and still finite enough to keep your bearings. */
        const val MAX = 5f
    }
}
