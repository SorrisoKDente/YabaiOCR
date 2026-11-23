package dev.yabai.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import dev.yabai.ocr.capture.PhotoPageGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The page the camera picker draws (R47a).
 *
 * The picker stretches its page into the window, so a photo cannot be handed to it as it is: a
 * portrait picture would be widened to the screen. The photo is fitted — one scale for both axes,
 * centred — and every box the engine reported is moved by the same fit. This is the geometry that
 * decides whether a box sits on the characters it came from; the page itself has to be allocated at
 * the screen's size, with the picture inside and black around it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoPageGeometryTest {

    @Test
    fun `a landscape photo is letterboxed above and below`() {
        val layout = requireNotNull(PhotoPageGeometry.fit(400, 200, 100, 200))

        assertEquals(0.25f, layout.scale, 0.0001f)
        assertEquals(100, layout.drawnWidth)
        assertEquals(50, layout.drawnHeight)
        assertEquals(0, layout.left)
        assertEquals(75, layout.top)
        assertEquals(Rect(0, 75, 100, 125), layout.photoBounds)
    }

    @Test
    fun `a portrait photo is letterboxed on the sides`() {
        val layout = requireNotNull(PhotoPageGeometry.fit(200, 400, 100, 200))

        assertEquals(0.5f, layout.scale, 0.0001f)
        assertEquals(100, layout.drawnWidth)
        assertEquals(200, layout.drawnHeight)
        assertEquals(0, layout.left)
        assertEquals(0, layout.top)
    }

    @Test
    fun `a box of the photo lands on the same characters of the page`() {
        val layout = requireNotNull(PhotoPageGeometry.fit(400, 200, 100, 200))

        assertEquals(
            Rect(10, 80, 20, 85),
            PhotoPageGeometry.place(layout, Rect(40, 20, 80, 40)),
        )
    }

    /**
     * The allocation is part of the regression: a page at the photo's size (or a photo drawn from
     * the page's corner) would put every box off its text, and the picker draws the page stretched
     * into the window.
     */
    @Test
    fun `the page is allocated at screen size with the photo fitted on it`() {
        val photo = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val layout = requireNotNull(PhotoPageGeometry.fit(40, 20, 100, 100))

        val page = requireNotNull(PhotoPageGeometry.page(photo, layout))

        assertEquals(100, page.width)
        assertEquals(100, page.height)
        assertEquals("the photo does not start where the fit says", Color.RED, page.getPixel(50, 50))
        assertEquals("the letterbox above the photo is not black", Color.BLACK, page.getPixel(50, 10))
        assertEquals("the letterbox below the photo is not black", Color.BLACK, page.getPixel(50, 90))
    }

    @Test
    fun `a picture or a page with no area has no fit`() {
        assertNull(PhotoPageGeometry.fit(0, 20, 100, 100))
        assertNull(PhotoPageGeometry.fit(40, 0, 100, 100))
        assertNull(PhotoPageGeometry.fit(40, 20, 0, 100))
        assertNull(PhotoPageGeometry.fit(40, 20, 100, 0))
    }
}
