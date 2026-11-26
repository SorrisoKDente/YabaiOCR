package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.io.File

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

    /**
     * R58: a narrow gutter is not a dead end. The reading shrinks into it instead of disappearing,
     * and stays clear of the next column.
     */
    @Test
    fun `a narrow gutter shrinks the reading instead of dropping it`() {
        val spot = PickerRuby.spot(
            Rectangle(100, 100, 30, 120),
            vertical = true,
            characters = 3,
            blocked = listOf(Rectangle(141, 95, 40, 130)), // 11 px of gutter
        )!!

        // The 16.5 px reading does not fit, but 9 px do; it hugs the run with 1 px of air.
        assertEquals(9f, spot.size, 0.01f)
        assertEquals(131f, spot.x, 0.01f)
        assertTrue(spot.stacked)
    }

    /** A full-size reading may sit closer than the usual 15% offset to avoid the next column. */
    @Test
    fun `a full-size reading hugs the run when the usual offset does not fit`() {
        val spot = PickerRuby.spot(
            Rectangle(100, 100, 30, 120),
            vertical = true,
            characters = 3,
            blocked = listOf(Rectangle(149, 95, 50, 130)),
        )!!

        assertEquals(16.5f, spot.size, 0.01f)
        assertEquals(131f, spot.x, 0.01f)
    }

    @Test
    fun `a row shrinks upward when the line above is close`() {
        val spot = PickerRuby.spot(
            Rectangle(100, 100, 60, 40),
            vertical = false,
            characters = 2,
            blocked = listOf(Rectangle(100, 50, 60, 40)), // 10 px above the run
        )!!

        assertEquals(8f, spot.size, 0.01f)
        assertEquals(91f, spot.y, 0.01f)
    }

    /** Only when neither gutter has room for a legible reading is none drawn. */
    @Test
    fun `a reading disappears only when both gutters are too narrow`() {
        assertNull(
            PickerRuby.spot(
                Rectangle(100, 100, 30, 120),
                vertical = true,
                characters = 3,
                blocked = listOf(Rectangle(130, 95, 20, 130), Rectangle(82, 95, 18, 130)),
            ),
        )
    }

    /** R57: a reading does not cover the column beside the one it belongs to. */
    @Test
    fun `ruby steps to the left gutter when the right one is another column`() {
        val spot = PickerRuby.spot(
            Rectangle(100, 100, 30, 120),
            vertical = true,
            characters = 3,
            blocked = listOf(Rectangle(134, 95, 30, 130)),
        )!!

        assertEquals(100f - 30f * 0.15f - 16.5f, spot.x, 0.01f)
        assertEquals(100f, spot.y, 0.01f)
    }

    @Test
    fun `ruby of a row goes below when the line above is taken`() {
        val spot = PickerRuby.spot(
            Rectangle(100, 100, 60, 40),
            vertical = false,
            characters = 2,
            blocked = listOf(Rectangle(100, 60, 60, 40)),
        )!!

        assertEquals(100f + 40f + PickerRuby.GAP, spot.y, 0.01f)
    }

    @Test
    fun `ruby with both sides covered is not drawn at all`() {
        assertNull(
            PickerRuby.spot(
                Rectangle(100, 100, 30, 120),
                vertical = true,
                characters = 3,
                blocked = listOf(Rectangle(0, 0, 400, 400)),
            ),
        )
    }

    @Test
    fun `a reading is not drawn when the run is too thin to read it`() {
        assertNull(PickerRuby.spot(Rectangle(0, 0, 100, 10), vertical = false, characters = 1))
        assertNull(PickerRuby.spot(Rectangle(0, 0, 4, 100), vertical = true, characters = 1))
    }

    /**
     * R57: the two halves of the rule are wiring — the printed stretches reach the scan, and the
     * layout is told where the other text is. The arithmetic is pinned above.
     */
    @Test
    fun `the picker skips printed furigana and keeps ruby off the other text`() {
        val source = listOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/SegmentPickerWindow.kt"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/SegmentPickerWindow.kt"),
        ).first { it.exists() }.readText()

        assertTrue(
            "the scan draws every dictionary reading, including over the page's own furigana",
            source.contains("withoutPrintedReadings(piece.printed"),
        )
        assertTrue(
            "the scan draws ruby without knowing where the rest of the text is",
            source.contains("PickerRuby.spot(span, vertical, reading.text.length, blocked)"),
        )
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
