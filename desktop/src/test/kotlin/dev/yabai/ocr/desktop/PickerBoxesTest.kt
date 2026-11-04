package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.lens.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

/**
 * The picker maps Lens boxes to its canvas and back, and decides which box a click meant.
 *
 * These are the rules that put the box on the text and take the crop from the right place: a
 * scaling mistake is invisible in a screenshot (the box is still a box) but scores every click on
 * the neighbouring paragraph, so it is worth pinning without a display.
 */
class PickerBoxesTest {

    private val image = Dimension(1280, 720)

    @Test
    fun `a box is scaled from the image to the canvas`() {
        assertEquals(
            Rectangle(50, 25, 100, 20),
            PickerBoxes.toCanvas(PixelBox(100, 50, 200, 40), image, Dimension(640, 360)),
        )
    }

    @Test
    fun `a canvas rectangle is mapped back to the image before cropping`() {
        assertEquals(
            Rectangle(100, 50, 200, 40),
            PickerBoxes.toImage(Rectangle(50, 25, 100, 20), image, Dimension(640, 360)),
        )
    }

    @Test
    fun `a one-to-one canvas keeps the box where Lens put it`() {
        assertEquals(
            Rectangle(300, 200, 180, 44),
            PickerBoxes.toCanvas(PixelBox(300, 200, 180, 44), image, image),
        )
    }

    @Test
    fun `an empty image or canvas maps to an empty rectangle instead of dividing by zero`() {
        assertEquals(Rectangle(), PickerBoxes.toCanvas(PixelBox(1, 2, 3, 4), Dimension(0, 0), image))
        assertEquals(Rectangle(), PickerBoxes.toImage(Rectangle(1, 2, 3, 4), image, Dimension(0, 0)))
    }

    @Test
    fun `padding adds air around the box`() {
        assertEquals(
            Rectangle(88, 38, 24, 24),
            PickerBoxes.padded(Rectangle(100, 50, 0, 0), pad = 12, image = image),
        )
    }

    @Test
    fun `padding never leaves the image`() {
        // top-left corner
        assertEquals(
            Rectangle(0, 0, 22, 22),
            PickerBoxes.padded(Rectangle(2, 2, 8, 8), pad = 12, image = Dimension(100, 50)),
        )
        // bottom-right corner
        val crop = PickerBoxes.padded(Rectangle(90, 40, 8, 8), pad = 12, image = Dimension(100, 50))
        assertEquals(Rectangle(78, 28, 22, 22), crop)
        assertEquals(100, crop.x + crop.width)
        assertEquals(50, crop.y + crop.height)
    }

    @Test
    fun `the smallest containing box wins, because it is the text under the pointer`() {
        val paragraph = Rectangle(0, 0, 400, 200)
        val line = Rectangle(40, 60, 120, 30)

        assertEquals(1, PickerBoxes.hit(listOf(paragraph, line), Point(80, 70)))
        assertEquals(0, PickerBoxes.hit(listOf(paragraph, line), Point(380, 180)))
        assertNull(PickerBoxes.hit(listOf(paragraph, line), Point(500, 500)))
    }

    @Test
    fun `a block without a box is skipped without shifting the indices`() {
        val boxes = listOf<Rectangle?>(null, Rectangle(10, 10, 20, 20))

        assertEquals(1, PickerBoxes.hit(boxes, Point(15, 15)))
        assertNull(PickerBoxes.hit(boxes, Point(0, 0)))
    }

    @Test
    fun `a zero-sized box still yields a crop of at least one pixel inside the image`() {
        assertEquals(
            Rectangle(0, 0, 1, 1),
            PickerBoxes.sanitize(Rectangle(-5, -5, 0, 0), Dimension(100, 50)),
        )
        // A box wider than the room left is shifted back inside, not shrunk: the crop must still
        // contain every pixel the box covered.
        val rounded = PickerBoxes.sanitize(Rectangle(98, 48, 10, 10), Dimension(100, 50))
        assertEquals(90, rounded.x)
        assertEquals(40, rounded.y)
        assertEquals(10, rounded.width)
        assertEquals(10, rounded.height)
        assertEquals(100, rounded.x + rounded.width)
        assertEquals(50, rounded.y + rounded.height)
    }
}
