package dev.yabai.ocr.core.layout

import dev.yabai.ocr.core.lens.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order boxes are read in, which is not the order either recogniser reports them in.
 *
 * The numbers are a phone screen's, scaled to the manga the user reads: a column of vertical text is
 * about 40 px wide and a few hundred tall, a balloon is three or four columns side by side, and a
 * horizontal caption is a row a few hundred pixels wide and 30 tall.
 */
class ReadingOrderTest {

    private fun box(x: Int, y: Int, width: Int, height: Int) = PixelBox(x, y, width, height)

    @Test
    fun `columns of a vertical balloon are read right to left`() {
        // The order the offline engine reports them in: leftmost first, because the page is scanned
        // top to bottom and left to right.
        val columns = listOf(
            box(60, 100, 40, 300),
            box(110, 100, 40, 300),
            box(160, 100, 40, 300),
        )

        assertEquals(listOf(2, 1, 0), ReadingOrder.order(columns))
    }

    @Test
    fun `rows of a horizontal paragraph are read top to bottom, left to right`() {
        val rows = listOf(
            box(0, 40, 200, 30), // second row, left
            box(220, 0, 200, 30), // first row, right
            box(220, 40, 200, 30), // second row, right
            box(0, 0, 200, 30), // first row, left
        )

        assertEquals(listOf(3, 1, 0, 2), ReadingOrder.order(rows))
    }

    @Test
    fun `the two halves of a split column stay together, top half first`() {
        // A column the recogniser broke in two arrives bottom half first.
        val halves = listOf(
            box(160, 100, 40, 140), // right column, top half
            box(60, 100, 40, 300), // the column to its left
            box(160, 240, 40, 160), // right column, bottom half
        )

        assertEquals(listOf(0, 2, 1), ReadingOrder.order(halves))
    }

    @Test
    fun `a box without geometry keeps the order the recogniser gave`() {
        val boxes = listOf(box(60, 100, 40, 300), null, box(160, 100, 40, 300))

        assertEquals(listOf(0, 1, 2), ReadingOrder.order(boxes))
    }

    @Test
    fun `a single box and an empty list keep their order`() {
        assertEquals(listOf(0), ReadingOrder.order(listOf(box(10, 10, 40, 200))))
        assertEquals(emptyList<Int>(), ReadingOrder.order(emptyList()))
    }

    @Test
    fun `a page of vertical balloons is read from the right edge`() {
        // Two balloons side by side on a manga page, each one a block. The right balloon is read
        // first, even though it is the last one the offline engine reports.
        val balloons = listOf(
            box(30, 400, 220, 300),
            box(560, 120, 220, 300),
        )

        assertEquals(listOf(1, 0), ReadingOrder.order(balloons))
    }
}
