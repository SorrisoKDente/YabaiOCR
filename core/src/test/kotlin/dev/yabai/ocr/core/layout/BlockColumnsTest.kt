package dev.yabai.ocr.core.layout

import dev.yabai.ocr.core.lens.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which reported blocks are columns of the same vertical paragraph.
 *
 * The numbers are a phone's: a column of manga text is about 40 px wide and a few hundred tall, the
 * gap between two columns of one balloon is smaller than a glyph, and the white space between two
 * balloons is several glyphs wide.
 */
class BlockColumnsTest {

    private fun box(x: Int, y: Int, width: Int, height: Int) = PixelBox(x, y, width, height)

    @Test
    fun `the two columns of a vertical balloon are one paragraph`() {
        // 「戦う術が」 / 「無い」: the right column is longer and starts higher, and both are the same
        // sentence — the shape the offline engine reports as two blocks.
        val columns = listOf(
            box(60, 100, 40, 180), // 無い, left column
            box(110, 90, 40, 300), // 戦う術が, right column
        )

        assertEquals(listOf(listOf(1, 0)), BlockColumns.groups(columns))
    }

    @Test
    fun `columns of two different balloons stay apart`() {
        val balloons = listOf(
            box(60, 100, 40, 300),
            box(300, 100, 40, 300),
        )

        assertEquals(listOf(listOf(1), listOf(0)), BlockColumns.groups(balloons))
    }

    @Test
    fun `a gap wider than a glyph is a different paragraph`() {
        val columns = listOf(
            box(60, 100, 40, 300),
            box(140, 100, 40, 300), // a whole glyph of white space between them
        )

        assertEquals(listOf(listOf(1), listOf(0)), BlockColumns.groups(columns))
    }

    @Test
    fun `columns that do not share a band are different paragraphs`() {
        // Balloons stacked in the same panel: nothing of the lower one overlaps the upper one.
        val columns = listOf(
            box(60, 100, 40, 120),
            box(110, 600, 40, 120),
        )

        assertEquals(listOf(listOf(1), listOf(0)), BlockColumns.groups(columns))
    }

    @Test
    fun `a column of one glyph is not a column`() {
        // A single character is as wide as it is tall, so it cannot be told apart from a row of
        // horizontal text; merging on that shape would join unrelated captions.
        val boxes = listOf(
            box(60, 100, 40, 40),
            box(105, 100, 40, 40),
        )

        assertEquals(listOf(listOf(1), listOf(0)), BlockColumns.groups(boxes))
    }

    @Test
    fun `a box without geometry is a paragraph of its own`() {
        val boxes = listOf(box(60, 100, 40, 300), null)

        assertEquals(listOf(listOf(0), listOf(1)), BlockColumns.groups(boxes))
    }
}
