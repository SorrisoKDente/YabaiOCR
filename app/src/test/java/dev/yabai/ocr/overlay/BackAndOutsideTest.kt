package dev.yabai.ocr.overlay

import android.content.Context
import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * What BACK and a tap outside the card do.
 *
 * The panel is not always the last thing on screen: after a whole-screen capture the boxes that the
 * text was picked from are still there. BACK used to close everything — throwing the capture away
 * and, since the screenshot was already in memory, leaving the user to capture again to get it back.
 * A tap outside the card used to do nothing at all, because the window was only as tall as the card
 * and the touch never reached it.
 *
 * The rule these tests pin: one step out. Back to the boxes when the panel has them behind it, out of
 * the panel when it does not (a region capture), and the segment picker answers BACK like «Cancelar».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class BackAndOutsideTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The window the panel is put in while a test drives it. */
    private var activity: Activity? = null

    @Test
    fun `back goes to the boxes when the panel came from a whole-screen capture`() {
        val asked = ArrayList<String>()
        val panel = layout(panel(canRepick = true, asked = asked))

        back(panel)

        assertEquals(listOf("repick"), asked)
    }

    @Test
    fun `back closes the panel when there is nothing behind it`() {
        val asked = ArrayList<String>()
        val panel = layout(panel(canRepick = false, asked = asked))

        back(panel)

        assertEquals(listOf("dismissed"), asked)
    }

    /** The empty area is the gesture: the same step as BACK, without reaching for the key. */
    @Test
    fun `a tap outside the card steps back`() {
        val asked = ArrayList<String>()
        val panel = layout(panel(canRepick = true, asked = asked))

        tap(panel, x = context.resources.displayMetrics.widthPixels / 2f, y = 600f)

        assertEquals(listOf("repick"), asked)
    }

    @Test
    fun `a tap outside the card closes the panel when there is nothing behind it`() {
        val asked = ArrayList<String>()
        val panel = layout(panel(canRepick = false, asked = asked))

        tap(panel, x = context.resources.displayMetrics.widthPixels / 2f, y = 600f)

        assertEquals(listOf("dismissed"), asked)
    }

    /**
     * Regression: making the root react to taps is only safe while the card swallows its own. The
     * card is the panel — a tap on its padding is not a tap outside it.
     */
    @Test
    fun `a tap on the card is not a tap outside`() {
        val asked = ArrayList<String>()
        val panel = layout(panel(canRepick = true, asked = asked))

        tap(panel, x = context.resources.displayMetrics.widthPixels / 2f, y = 12f)

        assertEquals(emptyList<String>(), asked)
    }

    /** While the boxes are up, BACK is «Cancelar» rather than a back in the app underneath. */
    @Test
    fun `back leaves the segment picker`() {
        var cancelled = 0
        val picker = TextPickerOverlay(context).apply { onCancel = { cancelled++ } }
        picker.show(listOf(Rect(10, 10, 100, 100)))
        layout(picker)

        back(picker)

        assertEquals("BACK did not cancel the picker", 1, cancelled)
    }

    /**
     * The window has to cover the screen for the outside tap to exist at all: a window as tall as the
     * card leaves the area outside it to the app underneath, where the panel cannot hear the touch.
     */
    @Test
    fun `the result window covers the screen`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = OverlayController(context)
        controller.showResult(
            text = "淀みなき拳で。",
            meta = "Google Lens · 1 ms · ja",
            notes = emptyList(),
            offlineAvailable = false,
            showLines = false,
            lines = emptyList(),
            canRepick = true,
        )

        val panel = windows().filterIsInstance<ResultOverlay>().singleOrNull()
            ?: error("the controller did not put a result panel on screen")
        val params = panel.layoutParams as? WindowManager.LayoutParams

        assertNotNull("the result panel has no window parameters", params)
        assertEquals(
            "the panel window is not as tall as the screen, so a tap outside the card is lost",
            WindowManager.LayoutParams.MATCH_PARENT,
            params!!.height,
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun panel(canRepick: Boolean, asked: MutableList<String>): ResultOverlay =
        ResultOverlay(context).apply {
            callbacks = object : ResultOverlay.Callbacks {
                override fun onCopyOriginal(text: String) = Unit
                override fun onCopyTranslation(text: String) = Unit
                override fun onAnalyse(text: String) = Unit
                override fun onRepick() {
                    asked.add("repick")
                }

                override fun onRetryOffline() = Unit
                override fun onImageToggle() = Unit
                override fun onDictionaryPicked(title: String?) = Unit
                override fun onAskAi(text: String) = Unit
                override fun onDismiss() {
                    asked.add("dismissed")
                }
            }
            showResult(
                text = "淀みなき拳で。",
                meta = "Google Lens · 1 ms · ja",
                notes = emptyList(),
                offlineAvailable = false,
                showLines = false,
                segmentLines = emptyList(),
                canRepick = canRepick,
            )
        }

    /** Lays [view] out the way the window manager would: the whole screen, and with the focus. */
    private fun <T : View> layout(view: T): T {
        val screen = context.resources.displayMetrics
        // A real window: `View.post` — which is how a tap becomes a click — only reaches the main
        // looper once the view is attached to one.
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity?.setContentView(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(screen.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screen.heightPixels, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, screen.widthPixels, screen.heightPixels)
        // A window is focused when it is on screen; without it a ViewGroup sends key events
        // straight back to the system, which is exactly what does not happen on the phone.
        view.requestFocus()
        return view
    }

    private fun back(view: View) {
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }

    private fun tap(view: View, x: Float, y: Float) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        // A tap is a click posted to the main looper, not a call made inside the touch.
        shadowOf(Looper.getMainLooper()).idle()
        down.recycle()
        up.recycle()
    }

    /** Everything on screen, the way the window manager sees it. */
    private fun windows(): List<View> {
        val manager = context.getSystemService(WindowManager::class.java)
        return Shadow.extract<ShadowWindowManagerImpl>(manager).views
    }
}
