package dev.yabai.ocr.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a capture stops at the picker.
 *
 * The case that made this worth a file: a whole-screen capture that recognised **one** block went
 * straight to the result panel, so the page was never offered as boxes — with Manga-OCR, whose
 * detector reports the page as one line when it fails, that was every single capture.
 */
class PickerDecisionTest {

    @Test
    fun `one recognised block still gets a box on the page`() {
        assertTrue(PickerDecision.offersPicker(wholeScreen = true, pickText = true, placeableBlocks = 1))
    }

    @Test
    fun `a page of balloons gets its boxes`() {
        assertTrue(PickerDecision.offersPicker(wholeScreen = true, pickText = true, placeableBlocks = 4))
    }

    @Test
    fun `nothing recognised goes straight to the panel`() {
        assertFalse(PickerDecision.offersPicker(wholeScreen = true, pickText = true, placeableBlocks = 0))
    }

    @Test
    fun `a region capture is already a selection`() {
        assertFalse(PickerDecision.offersPicker(wholeScreen = false, pickText = true, placeableBlocks = 4))
    }

    @Test
    fun `turning the setting off sends the whole screen to the panel`() {
        assertFalse(PickerDecision.offersPicker(wholeScreen = true, pickText = false, placeableBlocks = 4))
    }
}
