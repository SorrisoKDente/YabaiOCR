package dev.yabai.ocr.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides which recognised blocks become boxes in the picker.
 *
 * The case that made this worth a file: a **single column** of vertical manga text. Its box is a
 * glyph wide and a sentence long, and the old rule — both sides at least one text line tall —
 * silently left it out while the same text inside a three-column balloon got a box.
 */
class PickerTargetsTest {

    /** 12 dp at the density of a 1080p phone. */
    private val minTap = 33

    @Test
    fun `a single column of vertical text is offered`() {
        // 「やばい!!」 measured from a real capture, in screen pixels: 30 x 138.
        assertTrue(PickerTargets.offered(width = 30, height = 138, minTapSize = minTap))
    }

    @Test
    fun `a column shorter than it is thick is still offered`() {
        // A one-character column: 30 x 34. Thin, but a normal glyph, not a fragment.
        assertTrue(PickerTargets.offered(width = 30, height = 34, minTapSize = minTap))
    }

    @Test
    fun `a horizontal line of text is offered`() {
        assertTrue(PickerTargets.offered(width = 240, height = 44, minTapSize = minTap))
    }

    @Test
    fun `a speck is not offered`() {
        assertFalse(PickerTargets.offered(width = 10, height = 10, minTapSize = minTap))
    }

    @Test
    fun `a sliver that is long but too thin is not offered`() {
        // A line of 240 px that is only 10 px thick is a rule or a fragment, not a column.
        assertFalse(PickerTargets.offered(width = 240, height = 10, minTapSize = minTap))
    }

    @Test
    fun `nothing without both sides is offered`() {
        assertFalse(PickerTargets.offered(width = 0, height = 138, minTapSize = minTap))
        assertFalse(PickerTargets.offered(width = 30, height = 0, minTapSize = minTap))
    }
}
