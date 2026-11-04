package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

/**
 * Where the picker draws a reading and where it puts the readable strip.
 *
 * Both rules are the mobile ones, and both are silent when wrong: a reading a few pixels off sits
 * on the next kanji, and a strip that leaves the screen is simply not read. The tests pin the side
 * chosen, the size of the ruby and the clamping.
 */
class PickerReadingsTest {

    @Test
    fun `ruby sits above a horizontal run, centred on it`() {
        val spot = PickerRuby.spot(Rectangle(100, 100, 60, 40), vertical = false, characters = 2)!!

        assertEquals(130f, spot.x, 0.01f)              // centre of the run
        assertEquals(40 * 0.55f, spot.size, 0.01f)     // no taller than the glyphs
        assertEquals(100f - spot.size - PickerRuby.GAP, spot.y, 0.01f)
        assertEquals(false, spot.stacked)
    }

    @Test
    fun `ruby of vertical text goes to the right gutter, stacked`() {
        val spot = PickerRuby.spot(Rectangle(100, 100, 30, 120), vertical = true, characters = 3)!!

        assertTrue(spot.stacked)
        assertEquals((100 + 30) + 30 * 0.15f, spot.x, 0.01f)
        assertEquals(100f, spot.y, 0.01f)
        // The run is tall and narrow, so the width is what limits the reading here.
        assertEquals(16.5f, spot.size, 0.01f)
    }

    @Test
    fun `a short run caps the reading by its height so every kana fits`() {
        // Width alone would allow 33 px, but only 10 px of run per kana is available; drawing 33
        // would stack the characters over each other and lose the end of the reading.
        val spot = PickerRuby.spot(Rectangle(0, 0, 60, 30), vertical = true, characters = 3)!!

        assertEquals(10f, spot.size, 0.01f)
    }

    @Test
    fun `a reading is not drawn when the run is too thin to read it`() {
        assertNull(PickerRuby.spot(Rectangle(0, 0, 100, 10), vertical = false, characters = 1))
        assertNull(PickerRuby.spot(Rectangle(0, 0, 4, 100), vertical = true, characters = 1))
    }

    @Test
    fun `a run with no area or no characters has no reading`() {
        assertNull(PickerRuby.spot(Rectangle(0, 0, 0, 40), vertical = false, characters = 1))
        assertNull(PickerRuby.spot(Rectangle(0, 0, 40, 0), vertical = false, characters = 1))
        assertNull(PickerRuby.spot(Rectangle(0, 0, 40, 40), vertical = false, characters = 0))
    }

    @Test
    fun `the strip goes to the right when there is room`() {
        val point = PickerLabels.place(
            box = Rectangle(10, 10, 50, 30),
            strip = Dimension(100, 20),
            screen = Dimension(400, 300),
        )

        assertEquals(Point(66, 10), point)
    }

    @Test
    fun `the strip goes left when the right side does not fit`() {
        val point = PickerLabels.place(
            box = Rectangle(250, 10, 50, 30),
            strip = Dimension(100, 20),
            screen = Dimension(400, 300),
        )

        assertEquals(Point(144, 10), point)
    }

    @Test
    fun `the strip goes below when neither side fits`() {
        val point = PickerLabels.place(
            box = Rectangle(10, 10, 340, 30),
            strip = Dimension(100, 20),
            screen = Dimension(400, 300),
        )

        assertEquals(Point(10, 46), point)
    }

    @Test
    fun `the strip goes above when the bottom is the edge`() {
        val point = PickerLabels.place(
            box = Rectangle(10, 260, 340, 30),
            strip = Dimension(100, 20),
            screen = Dimension(400, 300),
        )

        assertEquals(Point(10, 234), point)
    }

    @Test
    fun `a crowded strip steps down its side instead of covering the previous one`() {
        val strip = Dimension(100, 20)
        val taken = listOf(Rectangle(66, 10, 100, 20))

        val point = PickerLabels.place(
            box = Rectangle(10, 10, 50, 30),
            strip = strip,
            screen = Dimension(400, 300),
            taken = taken,
        )

        assertEquals(Point(66, 36), point) // one strip below, with the gap
    }

    @Test
    fun `a crowded strip below its box steps sideways instead of over the box`() {
        val strip = Dimension(100, 20)
        val taken = listOf(Rectangle(10, 46, 100, 20))

        val point = PickerLabels.place(
            box = Rectangle(10, 10, 340, 30),
            strip = strip,
            screen = Dimension(400, 300),
            taken = taken,
        )

        // One strip to the right along the bottom: a strip that stepped down here would leave the
        // screen and, worse, creep back over the block it explains.
        assertEquals(Point(116, 46), point)
    }

    @Test
    fun `a strip is never lost when every side is crowded`() {
        val strip = Dimension(100, 20)
        val screen = Dimension(120, 100)

        val point = PickerLabels.place(
            box = Rectangle(10, 10, 100, 80),
            strip = strip,
            screen = screen,
        )

        assertEquals(Point(10, 0), point)
        assertTrue(point.x >= 0 && point.y >= 0)
        assertTrue(point.x + strip.width <= screen.width)
        assertTrue(point.y + strip.height <= screen.height)
    }

    @Test
    fun `the strip stays on screen at every corner`() {
        val strip = Dimension(120, 40)
        val screen = Dimension(300, 200)
        val boxes = listOf(
            Rectangle(0, 0, 60, 30),
            Rectangle(240, 0, 60, 30),
            Rectangle(0, 170, 60, 30),
            Rectangle(240, 170, 60, 30),
        )

        val placed = mutableListOf<Rectangle>()
        for (box in boxes) {
            val point = PickerLabels.place(box, strip, screen, placed)
            val rect = Rectangle(point.x, point.y, strip.width, strip.height)
            assertTrue("fora da tela: $rect", rect.x >= 0 && rect.y >= 0)
            assertTrue("fora da tela: $rect", rect.x + rect.width <= screen.width)
            assertTrue("fora da tela: $rect", rect.y + rect.height <= screen.height)
            placed += rect
        }

        // Side by side in a corner, the strips must not land on top of each other.
        assertEquals(placed.size, placed.toSet().size)
    }
}
