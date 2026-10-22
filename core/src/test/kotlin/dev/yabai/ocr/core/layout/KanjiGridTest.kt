package dev.yabai.ocr.core.layout

import dev.yabai.ocr.core.lens.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which character of a column or row of Japanese a finger is on (R17).
 *
 * The box comes from the recogniser, the text comes from the recogniser, and the finger comes from
 * the user: this is the arithmetic in the middle, and it has to be exact — a cell off by one is a
 * reading for the wrong character.
 */
class KanjiGridTest {

    private val column = PixelBox(300, 100, 40, 240)

    @Test
    fun `a finger on the third character of a vertical column gets it`() {
        // Six characters of 40 px each; the third cell is 180..220.
        assertEquals(2, KanjiGrid.cell("腹が減っては", column, 320, 200)?.index)
        assertEquals('減', KanjiGrid.cell("腹が減っては", column, 320, 200)?.character)
    }

    @Test
    fun `the first and the last character are reachable`() {
        assertEquals(0, KanjiGrid.cell("腹が減っては", column, 300, 100)?.index)
        assertEquals(5, KanjiGrid.cell("腹が減っては", column, 339, 339)?.index)
    }

    @Test
    fun `a finger slightly past the end still gets the last character`() {
        assertEquals(5, KanjiGrid.cell("腹が減っては", column, 350, 400)?.index)
    }

    @Test
    fun `a horizontal row is read from left to right`() {
        val row = PixelBox(100, 300, 240, 40)

        assertEquals(1, KanjiGrid.cell("腹が減っては", row, 150, 320)?.index)
        assertEquals('が', KanjiGrid.cell("腹が減っては", row, 150, 320)?.character)
    }

    @Test
    fun `the cells tile the box without gaps or overlaps`() {
        val text = "腹が減っては"
        val cells = text.indices.map { KanjiGrid.boxOf(text, column, it) }

        assertEquals(column.y, cells.first().y)
        assertEquals(column.bottom, cells.last().bottom)
        for (index in 1 until cells.size) {
            assertEquals("cell $index starts where the previous one ends", cells[index - 1].bottom, cells[index].y)
        }
        for (cell in cells) assertEquals(column.width, cell.width)
    }

    @Test
    fun `a row is cut into columns of equal width`() {
        val row = PixelBox(100, 300, 240, 40)
        val cells = "職員さん".indices.map { KanjiGrid.boxOf("職員さん", row, it) }

        assertEquals(row.x, cells.first().x)
        assertEquals(row.right, cells.last().right)
        for (index in 1 until cells.size) {
            assertEquals(cells[index - 1].right, cells[index].x)
        }
    }

    @Test
    fun `nothing to read means no cell`() {
        assertNull(KanjiGrid.cell("", column, 320, 200))
        assertNull(KanjiGrid.cell("腹", PixelBox(0, 0, 0, 0), 0, 0))
    }

    /** R29: the mark of a word covers its kanji, and its edges are the edges of those cells. */
    @Test
    fun `a range of characters covers exactly their cells`() {
        val text = "同時に"
        val column = PixelBox(100, 100, 40, 120)
        val first = KanjiGrid.boxOf(text, column, 0)
        val second = KanjiGrid.boxOf(text, column, 1)
        val third = KanjiGrid.boxOf(text, column, 2)

        // 同時: from the top of the first cell to the bottom of the second, and no further.
        assertEquals(
            PixelBox(first.x, first.y, first.width, second.bottom - first.y),
            KanjiGrid.boxOf(text, column, 0, 2),
        )
        // A range of one is the cell itself, so the two ways of asking agree.
        assertEquals(second, KanjiGrid.boxOf(text, column, 1, 2))
        assertEquals(third, KanjiGrid.boxOf(text, column, 2, 3))

        val row = PixelBox(100, 300, 240, 40)
        val left = KanjiGrid.boxOf(text, row, 0)
        val right = KanjiGrid.boxOf(text, row, 2)
        assertEquals(
            PixelBox(left.x, left.y, right.right - left.x, row.height),
            KanjiGrid.boxOf(text, row, 0, 3),
        )
    }

    @Test
    fun `a single character fills the whole strip`() {
        val cell = KanjiGrid.cell("腹", column, 310, 250)

        assertEquals(0, cell?.index)
        assertEquals(column, cell?.box)
    }
}
