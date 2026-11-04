package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.lens.PixelBox
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where the boxes of a full-screen OCR sit on the picker's canvas, and which one the pointer is on.
 *
 * Lens reports boxes in the pixels of the image that was submitted. The picker draws that same
 * image stretched to the window, so every box has to be mapped twice: to the canvas to draw and
 * select, and back to the image to crop what was clicked. On a HiDPI screen the capture is in
 * physical pixels while the canvas is in logical ones, so the two scales are not 1 — treating them
 * as equal would put every box a few percent away from the text it belongs to.
 *
 * Pure arithmetic, no Compose and no screen: the rules that decide what gets drawn and cropped are
 * tested without a display.
 */
object PickerBoxes {

    /** Box in the image's pixels mapped to the canvas that draws it. */
    fun toCanvas(box: PixelBox, image: Dimension, canvas: Dimension): Rectangle {
        if (image.width <= 0 || image.height <= 0) return Rectangle()
        val scaleX = canvas.width.toDouble() / image.width
        val scaleY = canvas.height.toDouble() / image.height
        return Rectangle(
            (box.x * scaleX).roundToInt(),
            (box.y * scaleY).roundToInt(),
            max(1, (box.width * scaleX).roundToInt()),
            max(1, (box.height * scaleY).roundToInt()),
        )
    }

    /** Canvas rectangle mapped back to the image, which is what the crop is taken from. */
    fun toImage(rect: Rectangle, image: Dimension, canvas: Dimension): Rectangle {
        if (canvas.width <= 0 || canvas.height <= 0) return Rectangle()
        val scaleX = image.width.toDouble() / canvas.width
        val scaleY = image.height.toDouble() / canvas.height
        return Rectangle(
            (rect.x * scaleX).roundToInt(),
            (rect.y * scaleY).roundToInt(),
            max(1, (rect.width * scaleX).roundToInt()),
            max(1, (rect.height * scaleY).roundToInt()),
        )
    }

    /**
     * [rect] grown by [pad] on every side and kept inside the image.
     *
     * The crop needs a little air around the recognised line: the glyphs' descenders and the
     * spaces between them help the second OCR, and a box cut exactly on the ink loses both. The
     * clamp matters at the screen edges, where expanding would leave the image.
     */
    fun padded(rect: Rectangle, pad: Int, image: Dimension): Rectangle {
        val left = (rect.x - pad).coerceIn(0, max(0, image.width - 1))
        val top = (rect.y - pad).coerceIn(0, max(0, image.height - 1))
        val right = (rect.x + rect.width + pad).coerceIn(left + 1, image.width)
        val bottom = (rect.y + rect.height + pad).coerceIn(top + 1, image.height)
        return Rectangle(left, top, right - left, bottom - top)
    }

    /**
     * Index of the box under [point], or null.
     *
     * Blocks may overlap (a paragraph's box and the boxes of its columns); the **smallest** one
     * wins, because it is the one whose text the pointer is actually printed on. Ties keep the
     * earlier box, which is reading order.
     */
    fun hit(boxes: List<Rectangle?>, point: Point): Int? {
        var best: Int? = null
        var bestArea = Long.MAX_VALUE
        for (index in boxes.indices) {
            val box = boxes[index] ?: continue
            if (!box.contains(point)) continue
            val area = box.width.toLong() * box.height
            if (area < bestArea) {
                best = index
                bestArea = area
            }
        }
        return best
    }

    /**
     * A crop that is never empty and never leaves the image, even if the box has zero size.
     * Used when the user clicks a block Lens could not box.
     */
    fun sanitize(rect: Rectangle, image: Dimension): Rectangle {
        val width = rect.width.coerceIn(1, max(1, image.width))
        val height = rect.height.coerceIn(1, max(1, image.height))
        val x = rect.x.coerceIn(0, max(0, image.width - width))
        val y = rect.y.coerceIn(0, max(0, image.height - height))
        return Rectangle(x, y, width, height)
    }
}
