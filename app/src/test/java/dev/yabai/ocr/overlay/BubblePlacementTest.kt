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
import java.io.File
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

    /**
     * Regression (R51): the bubble kept its coordinates across a rotation, and nothing re-placed it.
     *
     * The window is added with `FLAG_LAYOUT_NO_LIMITS`, so the window manager draws the bubble
     * exactly where it is told — including past the edge of the new screen, where no finger can
     * reach it and no panel can be opened from it. A corner of a portrait screen is below the bottom
     * of the same screen turned sideways.
     *
     * The bounds are given to the controller instead of turning the Robolectric display: what is
     * under test is the clamp, and a simulated display would make the test depend on how the shadow
     * builds its metrics. The wiring that calls it is the next test.
     */
    @Test
    fun `rotacionar a tela traz a bolha de volta para dentro`() {
        val controller = controller(pinned = false)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")
        drag(bubble, controller.bubbleOrigin(), Point(250, 500))
        assertEquals(Point(250, 500), controller.bubbleOrigin())

        // The phone turned: what was a 320×640 screen is now 640 wide and 320 tall.
        controller.keepBubbleOnScreen(Rect(0, 0, 640, 320))

        assertEquals(
            "the bubble stayed below the shorter screen",
            Point(250, 320 - controller.bubbleSize - context.dp(4)),
            controller.bubbleOrigin(),
        )
    }

    /** The other direction: the screen is narrower upright than it is sideways (R51). */
    @Test
    fun `girar de volta para em pe traz a bolha para a esquerda`() {
        val controller = controller(pinned = false)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")
        // Dropped past where the narrower screen will end: x=250 still fits in 320 px, so it never
        // exercised the clamp (the test was expecting the clamp to push it further out).
        drag(bubble, controller.bubbleOrigin(), Point(500, 150))

        // The same drop, with the screen as wide as it is tall in landscape and narrower upright.
        controller.keepBubbleOnScreen(Rect(0, 0, 640, 320))
        controller.keepBubbleOnScreen(Rect(0, 0, 320, 640))

        assertEquals(
            "the bubble stayed past the right edge of the narrower screen",
            Point(320 - controller.bubbleSize + context.dp(4), 150),
            controller.bubbleOrigin(),
        )
    }

    /**
     * The clamp helps only if the display change reaches it: the bubble's window is never told, so
     * the controller has to listen for the display, and let go of it with the bubble (R51).
     *
     * Read from the source for the same reason the camera rotation is: the Robolectric display is
     * not a phone that turns, and the listener is what makes a real turn call [keepBubbleOnScreen].
     */
    @Test
    fun `a bolha escuta a tela virar e e solta junto com ela`() {
        val source = sequenceOf(File(SOURCE), File("../$SOURCE"))
            .firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("OverlayController.kt not found", source.isNotEmpty())

        val onDisplayChanged = source.substringAfter("override fun onDisplayChanged(displayId: Int)", "")
            .substringBefore("}")
        assertTrue(
            "the display change does not bring the bubble back",
            onDisplayChanged.contains("keepBubbleOnScreen()"),
        )
        assertTrue(
            "the display listener is not registered with the bubble",
            source.substringAfter("fun showBubble()", "").substringBefore("fun hideBubble()")
                .contains("listenForDisplayChanges()"),
        )
        assertTrue(
            "the display listener outlives the bubble",
            source.substringAfter("fun hideBubble()", "").substringBefore("fun onBubbleTouch")
                .contains("stopListeningForDisplayChanges()") &&
                source.contains("unregisterDisplayListener"),
        )
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

    private companion object {
        /** The controller the wiring guard reads, from the module root or the repo root. */
        const val SOURCE = "app/src/main/java/dev/yabai/ocr/overlay/OverlayController.kt"
    }
}
