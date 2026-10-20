package dev.yabai.ocr.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a reading is drawn over the page, and where the readable strip of a balloon goes.
 *
 * The two rules come straight from how Japanese typesetting does it, and from what the user asked
 * for: the reading of a **column** is a small stack of kana in the gutter to its right, the reading
 * of a **row** is a small line above it. The numbers are the screen's, not a fixture's: a column of
 * manga text is about 30 px thick on a 1080p phone and a sentence is a few hundred pixels long.
 */
class PickerReadingLayoutTest {

    @Test
    fun `a reading of a column goes to the right, stacked`() {
        val spot = RubyLayout.spot(
            left = 100f, top = 200f, width = 30f, height = 138f,
            vertical = true, from = 0f, to = 0.25f, characters = 2,
        )!!
        // One glyph thick, and a little clear of the column so it does not sit on the glyphs.
        assertEquals(134.5f, spot.x, 0.001f)
        assertEquals(200f, spot.y, 0.001f)
        assertEquals(16.5f, spot.size, 0.001f)
        assertTrue(spot.stacked)
    }

    @Test
    fun `a reading of a row goes above, centred on its characters`() {
        val spot = RubyLayout.spot(
            left = 100f, top = 300f, width = 200f, height = 40f,
            vertical = false, from = 0.25f, to = 0.75f, characters = 2,
        )!!
        assertEquals(200f, spot.x, 0.001f)
        assertEquals(278f, spot.y, 0.001f)
        assertEquals(22f, spot.size, 0.001f)
        assertTrue(!spot.stacked)
    }

    @Test
    fun `a five-kana reading beside two kanji fits the column instead of the two characters`() {
        val spot = RubyLayout.spot(
            left = 0f, top = 0f, width = 30f, height = 138f,
            vertical = true, from = 0.2f, to = 0.4f, characters = 5,
        )!!
        // Thin as the glyphs, not as the two characters it reads.
        assertEquals(16.5f, spot.size, 0.001f)
        assertEquals(27.6f, spot.y, 0.001f)
    }

    @Test
    fun `a reading near the bottom is pulled up inside the column`() {
        val spot = RubyLayout.spot(
            left = 0f, top = 0f, width = 30f, height = 138f,
            vertical = true, from = 0.9f, to = 1f, characters = 5,
        )!!
        // 5 kana at 16.5 px need 82.5 px, so the top moves up to keep the stack inside the column.
        assertEquals(55.5f, spot.y, 0.001f)
    }

    @Test
    fun `a column too thin for a legible reading gets none`() {
        assertNull(
            RubyLayout.spot(
                left = 0f, top = 0f, width = 10f, height = 138f,
                vertical = true, from = 0f, to = 1f, characters = 2,
            ),
        )
    }

    @Test
    fun `a reading with nothing to sit on gets no spot`() {
        assertNull(
            RubyLayout.spot(
                left = 0f, top = 0f, width = 30f, height = 0f,
                vertical = true, from = 0f, to = 1f, characters = 2,
            ),
        )
    }

    @Test
    fun `the strip goes to the right of the balloon when there is room`() {
        val chip = LabelLayout.chip(
            boxLeft = 100, boxTop = 300, boxRight = 200, boxBottom = 500,
            width = 220, height = 90, screenWidth = 600, screenHeight = 900,
        )
        assertEquals(Chip(left = 206, top = 300), chip)
    }

    @Test
    fun `the strip goes to the left when the right is off screen`() {
        val chip = LabelLayout.chip(
            boxLeft = 400, boxTop = 300, boxRight = 500, boxBottom = 500,
            width = 220, height = 90, screenWidth = 600, screenHeight = 900,
        )
        assertEquals(Chip(left = 174, top = 300), chip)
    }

    @Test
    fun `the strip goes below when neither side fits`() {
        val chip = LabelLayout.chip(
            boxLeft = 40, boxTop = 300, boxRight = 540, boxBottom = 500,
            width = 220, height = 90, screenWidth = 600, screenHeight = 900,
        )
        assertEquals(Chip(left = 40, top = 506), chip)
    }

    @Test
    fun `the strip never leaves the screen`() {
        val chip = LabelLayout.chip(
            boxLeft = 40, boxTop = 10, boxRight = 540, boxBottom = 60,
            width = 220, height = 90, screenWidth = 200, screenHeight = 100,
        )
        assertTrue(chip.left in 0..0)
        assertTrue(chip.top in 0..10)
    }
}
