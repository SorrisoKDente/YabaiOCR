package dev.yabai.ocr.util

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the piece of the capture that goes beside the text.
 *
 * The screen next to one line of text is a thumbnail, so a picked box has to be cut out of the frame
 * — and the cutting has to survive the boxes the engines hand out, which are not always inside the
 * picture they claim to describe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockImageTest {

    private val crop: Bitmap = Bitmap.createBitmap(400, 900, Bitmap.Config.ARGB_8888)

    @Test
    fun `the box the text came from is what gets cut out`() {
        val piece = BlockImage.from(crop, Rect(40, 60, 140, 260))

        assertEquals(100, piece.width)
        assertEquals(200, piece.height)
    }

    @Test
    fun `a box that is not there is the whole frame`() {
        assertSame(crop, BlockImage.from(crop, null))
        assertSame(crop, BlockImage.from(crop, Rect(120, 120, 120, 400)))
    }

    /** An engine box that runs off the picture is clipped to what is really in it. */
    @Test
    fun `a box that hangs off the edge is clipped`() {
        val piece = BlockImage.from(crop, Rect(380, 880, 900, 1800))

        assertEquals(20, piece.width)
        assertEquals(20, piece.height)
    }

    /** A box that describes another picture entirely must not cost the user the image. */
    @Test
    fun `a box that is nowhere near the frame is the whole frame`() {
        assertSame(crop, BlockImage.from(crop, Rect(1200, 2000, 1400, 2200)))
    }
}
