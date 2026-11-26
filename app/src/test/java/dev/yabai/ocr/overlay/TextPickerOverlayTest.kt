package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import dev.yabai.ocr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What a gesture means in the text picker once the page can be zoomed (R15).
 *
 * The boxes are drawn on page coordinates and the fingers are on the window, so a tap has to be
 * converted before it can be tested against a box — and a pan must not be read as "outside", which
 * would close the picker the moment the user drags a zoomed page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class TextPickerOverlayTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val box = Rect(100, 100, 200, 200)

    /** The picker as the controller shows it: one box over the frozen page, laid out on a phone. */
    private fun picker(onPick: (Int) -> Unit, onCancel: () -> Unit): TextPickerOverlay {
        val page = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
        return TextPickerOverlay(context).apply {
            this.onPick = onPick
            this.onCancel = onCancel
            show(listOf(PickerBlock(box)), PickerReadingMode.OFF, page)
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 640)
        }
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 10L, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun tap(view: View, x: Float, y: Float) {
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
    }

    /**
     * R48b: the actions are above the phone's own buttons.
     *
     * The picker's window is the whole screen — the page must keep the size it was captured at — and
     * the navigation bar is drawn over its last strip. A bar 40 dp above the bottom of the window sat
     * *on* the system buttons, half of «Cancelar», «Texto todo» and «Tirar outra foto» behind them.
     */
    @Test
    fun `the actions are drawn above the system navigation buttons`() {
        val overlay = picker({}, {})
        val bar = overlay.actionBar
        val page = overlay.pageView
        val navigationBar = navigationBarHeight()
        val barBottom = bar.top + bar.height

        assertTrue(
            "the action bar (bottom at $barBottom px) is drawn over the system buttons " +
                "($navigationBar px of a 640 px screen)",
            barBottom <= 640 - navigationBar,
        )
        // The page itself, not the overlay: the overlay is 640 px tall whatever its children do, so
        // measuring it would pass even if the bar were lifted by shrinking the drawing area.
        assertEquals("the page lost width to the action bar", 320, page.width)
        assertEquals("the page lost height to the action bar", 640, page.height)
        assertEquals("the page was pushed down to make room for the bar", 0, page.top)
    }

    /**
     * R60: «Cancelar» is red, not the accent blue of the forward actions.
     *
     * The picker's bar was built from the plain action button, so «Cancelar» and «Texto todo» had
     * the same fill and the way out looked like the way forward.
     */
    @Test
    fun `cancel is red instead of the accent`() {
        val overlay = picker({}, {})
        val cancel = (overlay.actionBar as android.view.ViewGroup).getChildAt(0)
        val fill = (cancel.background as android.graphics.drawable.GradientDrawable)
            .color?.defaultColor

        assertEquals(context.getColor(R.color.warn), fill)
    }

    /** The height of the bar the picker has to clear, with the same fallback the picker uses. */
    private fun navigationBarHeight(): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) {
            context.resources.getDimensionPixelSize(id)
        } else {
            (48 * context.resources.displayMetrics.density).toInt()
        }
    }

    @Test
    fun `showing a new page forgets the zoom of the old one`() {
        val overlay = picker({}, {})
        overlay.zoomBy(3f, 40f, 40f)
        assertEquals(3f, overlay.zoomScale, 0.0001f)

        overlay.show(listOf(PickerBlock(box)), PickerReadingMode.OFF, Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888))

        assertEquals(
            "the boxes of a new capture are placed for the screen, not for the old zoom",
            1f,
            overlay.zoomScale,
            0.0001f,
        )
    }

    /**
     * Regression: with the page zoomed 2× from the top-left corner, the box at 100..200 on the page
     * sits at 200..400 on the screen. A tap at 300,300 is inside the box as the user sees it; testing
     * the raw touch against page coordinates would miss it and close the picker.
     */
    @Test
    fun `a tap lands on the box it covers after zooming`() {
        var picked = -1
        var cancelled = false
        val overlay = picker({ picked = it }, { cancelled = true })

        overlay.zoomBy(2f, 0f, 0f)
        tap(overlay, 300f, 300f)

        assertEquals(0, picked)
        assertFalse("a tap inside a box is not a cancel", cancelled)
    }

    /**
     * Regression R23: the window is not always exactly the capture.
     *
     * With the status bar on screen the picker's window is shorter than the screenshot, and the page
     * is drawn squeezed into it. The box has to be squeezed with the page: a tap where the box is
     * *drawn* is a tap on the box, or every box drifts off its text by the height of the bars.
     */
    @Test
    fun `a tap lands on the box it covers when the window is shorter than the page`() {
        var picked = -1
        val overlay = TextPickerOverlay(context).apply {
            onPick = { picked = it }
            onCancel = {}
            show(
                // Page 320..360, which the 600-pixel-tall window draws at 300..337.
                listOf(PickerBlock(Rect(100, 320, 200, 360))),
                PickerReadingMode.OFF,
                Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888),
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 320, 600)
        }

        tap(overlay, 150f, 310f)

        assertEquals("a tap on the drawn box missed it", 0, picked)
    }

    @Test
    fun `a tap outside every box still closes the picker`() {
        var cancelled = false
        val overlay = picker({}, { cancelled = true })

        tap(overlay, 300f, 300f)

        assertTrue(cancelled)
    }

    /** A drag on a zoomed page moves the page; lifting the finger is not a choice. */
    @Test
    fun `dragging a zoomed page picks nothing and keeps the picker open`() {
        var picked = -1
        var cancelled = false
        val overlay = picker({ picked = it }, { cancelled = true })

        overlay.zoomBy(2f, 0f, 0f)
        touch(overlay, MotionEvent.ACTION_DOWN, 250f, 250f)
        touch(overlay, MotionEvent.ACTION_MOVE, 150f, 150f)
        touch(overlay, MotionEvent.ACTION_UP, 150f, 150f)

        assertEquals(-1, picked)
        assertFalse("a pan is not a cancel", cancelled)
    }

    /** At 1× there is nothing to pan, so a finger that wobbles a little is still a tap. */
    @Test
    fun `a small wobble at one to one is still a tap`() {
        var picked = -1
        val overlay = picker({ picked = it }, {})

        touch(overlay, MotionEvent.ACTION_DOWN, 150f, 150f)
        touch(overlay, MotionEvent.ACTION_MOVE, 151f, 151f)
        touch(overlay, MotionEvent.ACTION_UP, 151f, 151f)

        assertEquals(0, picked)
    }
}
