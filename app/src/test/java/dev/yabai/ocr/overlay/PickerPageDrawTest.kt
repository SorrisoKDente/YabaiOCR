package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The frozen capture is really painted, under the boxes, and it zooms with them (R15).
 *
 * This is the part a unit test could not otherwise see: the picker draws a bitmap it is handed, and
 * a zoom that forgot to scale the canvas would show a magnified box over an unmagnified page — the
 * one outcome the feature exists to avoid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickerPageDrawTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** A page that is blank except for a red square in its middle. */
    private fun page(): Bitmap {
        val bitmap = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        Canvas(bitmap).drawRect(200f, 200f, 250f, 250f, paint)
        return bitmap
    }

    private fun picker(): TextPickerOverlay = TextPickerOverlay(context).apply {
        onPick = {}
        onCancel = {}
        show(emptyList(), PickerReadingMode.OFF, page())
        measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, 320, 640)
    }

    /** Draws the picker the way the window manager would, over a white screen. */
    private fun drawn(overlay: TextPickerOverlay): Bitmap {
        val out = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        overlay.draw(Canvas(out))
        return out
    }

    @Test
    fun `the page is drawn under the boxes`() {
        val shot = drawn(picker())

        assertEquals("the red square of the page is on screen", Color.RED, shot.getPixel(225, 225))
        assertNotEquals("blank page stays blank", Color.RED, shot.getPixel(300, 300))
    }

    @Test
    fun `the page zooms together with the boxes`() {
        val overlay = picker()

        overlay.zoomBy(2f, 200f, 200f)
        val shot = drawn(overlay)

        // The square was 200..250 on the page; at 2× around 200 it now reaches 300 on the screen.
        assertEquals("the page was magnified", Color.RED, shot.getPixel(290, 290))
        assertEquals(Color.RED, shot.getPixel(225, 225))
    }

    /**
     * Regression R23: the window is not always exactly the capture.
     *
     * With a shorter window the page is drawn squeezed into it. A box that kept its page pixels
     * would be painted over the wrong part of the page, and about a dozen pixels off is exactly the
     * amount that makes a translator point at the wrong balloon.
     */
    @Test
    fun `a box is drawn over the piece of page it stands for`() {
        val overlay = TextPickerOverlay(context).apply {
            onPick = {}
            onCancel = {}
            // Page 320..360, drawn at 300..337 in a 600-pixel-tall window.
            show(listOf(PickerBlock(Rect(100, 320, 200, 360))), PickerReadingMode.OFF, page())
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 600)
        }
        val shot = Bitmap.createBitmap(320, 600, Bitmap.Config.ARGB_8888)
        shot.eraseColor(Color.WHITE)
        overlay.draw(Canvas(shot))

        val barePage = shot.getPixel(150, 100)
        assertNotEquals(
            "the box is not over the page it labels",
            barePage,
            shot.getPixel(150, 318),
        )
        assertEquals("the box was drawn past the page it labels", barePage, shot.getPixel(150, 345))
    }
}
