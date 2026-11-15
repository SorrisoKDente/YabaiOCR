package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.WindowManager
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.MainActivity
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Where the bubble stays after a drag (R35c).
 *
 * The floating bubble used to be a side tab: whatever the user did, letting go sent it to the
 * nearest edge. That is the right behaviour for someone who wants it out of the way, but not for
 * someone reading one panel of a page who just put the bubble next to it. Free placement is now the
 * default — the drop is the position, with the screen as the only limit — and «Prender a bolha nas
 * bordas» brings the old behaviour back.
 *
 * What these tests pin down is the difference between the two modes at the only moment it exists:
 * the release of a drag. Both are driven through the real touch listener, and both use a drop in the
 * middle of the screen, where the two modes disagree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class BubblePlacementTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The whole point of the round: a free bubble stays where the finger let go. */
    @Test
    fun `em modo livre a bolha fica onde o dedo soltou`() {
        val controller = controller(pinned = false)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")
        val target = Point(176, 283)

        drag(bubble, controller.bubbleOrigin(), target)

        assertEquals("the free bubble was pulled out of the middle", target, controller.bubbleOrigin())
    }

    /** The option is not a removal: pinned mode is still a side tab, exactly as before. */
    @Test
    fun `prender nas bordas continua grudando na lateral`() {
        val controller = controller(pinned = true)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")

        drag(bubble, controller.bubbleOrigin(), Point(176, 283))

        val origin = controller.bubbleOrigin()
        val side = context.resources.displayMetrics.widthPixels - controller.bubbleSize - context.dp(6)
        assertEquals("the pinned bubble did not go to the nearest side", side, origin.x)
        assertEquals("the pinned bubble lost its height", 283, origin.y)
    }

    /**
     * A cancelled gesture never happened: the bubble goes back to where it was resting — which, in
     * free mode, is the middle of the screen, not an edge.
     */
    @Test
    fun `cancelar o arrasto devolve a bolha ao lugar de antes`() {
        val controller = controller(pinned = false)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")
        drag(bubble, controller.bubbleOrigin(), Point(176, 283))
        val resting = controller.bubbleOrigin()

        drag(bubble, resting, Point(240, 320), cancel = true)

        assertEquals("the cancelled drag moved the bubble", resting, controller.bubbleOrigin())
    }

    /** The pause is not a reset: a bubble left beside a panel is still there on the way back. */
    @Test
    fun `pausar e retomar no modo livre nao move a bolha`() {
        val controller = controller(pinned = false)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")
        drag(bubble, controller.bubbleOrigin(), Point(176, 283))
        val resting = controller.bubbleOrigin()

        controller.setPaused(true)
        controller.setPaused(false)

        assertEquals("the bubble came back somewhere else", resting, controller.bubbleOrigin())
    }

    /**
     * Regression: the drag limit came from `resources.displayMetrics`, which is the area the app
     * draws in and already has the navigation bar taken out. The bubble therefore stopped one bar
     * short of the bottom in portrait and of the right edge with the phone rotated left — exactly the
     * strip where the system buttons sit, which the overlay window may use.
     */
    @Test
    fun `a bolha alcanca a borda do display inteiro`() {
        val controller = OverlayController(context)
        val params = WindowManager.LayoutParams()
        val wholeDisplay = Rect(0, 0, 1080, 2400)
        params.x = 5000
        params.y = 5000

        controller.clampBubbleTo(params, wholeDisplay)

        assertEquals(1080 - controller.bubbleSize + context.dp(4), params.x)
        assertEquals(2400 - controller.bubbleSize - context.dp(4), params.y)
    }

    /** The choice is a setting like any other: off by default, saved for the next start. */
    @Test
    fun `a escolha comeca livre e e guardada nos ajustes`() {
        val settings = AppSettings(context)
        assertFalse("the bubble should start free", settings.bubbleOnEdge)

        settings.bubbleOnEdge = true

        assertTrue("the choice was not saved", AppSettings(context).bubbleOnEdge)
    }

    /** The default controller follows the saved choice, without the service wiring anything. */
    @Test
    fun `o controlador obedece a escolha guardada`() {
        AppSettings(context).bubbleOnEdge = true
        val controller = OverlayController(context)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")

        drag(bubble, controller.bubbleOrigin(), Point(176, 283))

        val side = context.resources.displayMetrics.widthPixels - controller.bubbleSize - context.dp(6)
        assertEquals("the saved choice did not reach the controller", side, controller.bubbleOrigin().x)
    }

    /** The checkbox is the setting: turning it on in «Ajustes» is what the bubble obeys. */
    @Test
    fun `a opcao dos ajustes grava a escolha`() {
        AppSettings(context).bubbleOnEdge = false
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val check = activity.findViewById<CheckBox>(R.id.checkBubbleOnEdge)
        assertFalse("the checkbox does not show the default", check.isChecked)

        check.isChecked = true

        assertTrue("the checkbox did not save the choice", AppSettings(activity).bubbleOnEdge)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun controller(pinned: Boolean): OverlayController =
        OverlayController(context) { pinned }

    /**
     * A drag from the bubble's current corner to [target], delivered as the phone delivers it.
     *
     * The finger starts away from the bubble's bounds on purpose: the event goes to the window's
     * root, which is the bubble itself, and only the raw coordinates matter to the drag.
     */
    private fun drag(bubble: View, start: Point, target: Point, cancel: Boolean = false) {
        val finger = Point(100, 100)
        val endX = finger.x + (target.x - start.x)
        val endY = finger.y + (target.y - start.y)
        bubble.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, finger.x, finger.y))
        bubble.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, endX, endY))
        bubble.dispatchTouchEvent(
            touch(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, endX, endY),
        )
    }

    private fun touch(action: Int, x: Int, y: Int): MotionEvent =
        MotionEvent.obtain(0L, 0L, action, x.toFloat(), y.toFloat(), 0)
}
