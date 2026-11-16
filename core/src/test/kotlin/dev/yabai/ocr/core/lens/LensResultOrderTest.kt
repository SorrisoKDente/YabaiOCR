package dev.yabai.ocr.core.lens

import org.junit.Assert.assertEquals
import org.junit.Test

/** The recognised text of a page is printed in the order a person reads it, not in Lens's order. */
class LensResultOrderTest {

    private fun result(vararg segments: LensSegment) =
        LensResult(language = "ja", segments = segments.toList(), blocks = emptyList(), elapsedMs = 1)

    /**
     * Regression: a vertical novel page came back with «え入れる。» before «…血の絨毯がまた誰かを迎»,
     * so the sentence was cut and its halves were printed out of order. Vertical text reads in columns
     * from the right; the boxes say which column comes first.
     */
    @Test
    fun `vertical columns are joined from right to left`() {
        val page = result(
            LensSegment("え入れる。", PixelBox(1412, 29, 24, 408)),
            LensSegment("血の絨毯がまた誰かを迎", PixelBox(1451, 29, 26, 592)),
            LensSegment("短い悲鳴が上がって、血の", PixelBox(1334, 25, 25, 306)),
        )

        assertEquals(
            "血の絨毯がまた誰かを迎\nえ入れる。\n短い悲鳴が上がって、血の",
            page.joinLines(),
        )
    }

    @Test
    fun `horizontal lines are joined from top to bottom`() {
        val page = result(
            LensSegment("え入れる。", PixelBox(20, 82, 112, 21)),
            LensSegment("血の絨毯がまた誰かを迎", PixelBox(20, 38, 700, 25)),
        )

        assertEquals("血の絨毯がまた誰かを迎\nえ入れる。", page.joinLines())
    }

    /** No geometry, no order: the lines stay as the recogniser reported them. */
    @Test
    fun `lines without boxes keep the reported order`() {
        val page = result(LensSegment("c", null), LensSegment("a", null), LensSegment("b", null))

        assertEquals("c\na\nb", page.joinLines())
    }
}
