package dev.yabai.ocr.core.ppocr

import dev.yabai.ocr.core.lens.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hover recognises only the lines around the pointer. Every line is still recognised on its own
 * pixels, so the text of the lines that are read does not change — what changes is the bill for the
 * rest of the page.
 */
class PPOcrFocusTest {

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        PPOcrLine(text = text, left = left, top = top, right = right, bottom = bottom)

    @Test
    fun keepsTheLineUnderThePointerAndItsNeighbours() {
        val under = line("食べる", 100, 100, 160, 130)
        val right = line("の", 166, 100, 190, 130)
        val far = line("遠い", 900, 400, 960, 430)

        val kept = linesToRecognize(listOf(under, right, far), PixelBox(120, 110, 1, 1), radiusPx = 160)

        assertEquals(listOf(under, right), kept)
    }

    @Test
    fun withoutAFocusEveryLineIsReadUnchanged() {
        val page = listOf(line("上", 0, 0, 40, 20), line("下", 0, 900, 40, 920))

        assertEquals(page, linesToRecognize(page, focus = null))
    }

    @Test
    fun dropsEveryLineOfARestingPageWhenThePointerIsOnNothing() {
        val page = (0 until 40).map { line("行$it", 40, 900 + it * 30, 300, 920 + it * 30) }

        assertTrue(linesToRecognize(page, PixelBox(1700, 40, 1, 1), radiusPx = 160).isEmpty())
    }

    @Test
    fun theWordSplitBetweenTwoColumnsKeepsBothHalvesRecognised() {
        // A vertical column is ~25 px wide and its neighbour starts right after it: the two halves
        // of a word printed across them must both be read.
        val first = line("救", 400, 100, 425, 500)
        val second = line("い", 427, 100, 452, 500)

        val kept = linesToRecognize(listOf(first, second), PixelBox(410, 480, 1, 1), radiusPx = HOVER_FOCUS_RADIUS_PX)

        assertEquals(listOf(first, second), kept)
    }

    @Test
    fun theRadiusIsMeasuredFromTheBoxEdge() {
        val near = line("近", 500, 500, 520, 520)

        // A pointer 159 px above the box is inside the radius; 161 px is not.
        assertEquals(1, linesToRecognize(listOf(near), PixelBox(510, 341, 1, 1), 160).size)
        assertEquals(0, linesToRecognize(listOf(near), PixelBox(510, 338, 1, 1), 160).size)
        // Horizontally the same distance is measured the same way.
        assertEquals(0, linesToRecognize(listOf(near), PixelBox(681, 510, 1, 1), 160).size)
    }
}
