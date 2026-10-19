package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Tests for closing the service by dragging the bubble onto a ✕.
 *
 * The gesture itself is a handful of lines in `onBubbleTouch`; what it can get wrong is where the ✕
 * is, what counts as landing on it and — above all — whether letting go there closes the service
 * without asking. That last one is the line between a feature and an accident, so it is what these
 * tests pin: a drop on the ✕ only ever *asks*.
 *
 * N6 added the pull: near the ✕ the bubble is *drawn* towards it, and the drop is judged on where it
 * is drawn — so what the user sees coming is what happens on release.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class DragToCloseTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Centred, inside the screen, and low enough to be the natural place to drop something. */
    @Test
    fun `the cross sits centred near the bottom of the screen`() {
        val target = controller().closeTargetRect()
        val screen = context.resources.displayMetrics

        assertEquals("the cross is not centred", screen.widthPixels / 2, target.centerX())
        assertTrue(
            "the cross is off screen: $target",
            target.top >= 0 && target.bottom <= screen.heightPixels,
        )
        assertTrue(
            "the cross is not near the bottom: ${target.bottom} of ${screen.heightPixels}",
            target.bottom > screen.heightPixels - 150,
        )
        assertTrue("the cross is too small to drop on: ${target.width()}px", target.width() >= 48)
    }

    /**
     * The rule and the highlight are the same circle, so the armed ✕ can never disagree with what
     * letting go does: the bubble *centre* has to land on the ✕, and half the bubble hanging outside
     * it still counts.
     */
    @Test
    fun `only a bubble centred on the cross counts as a drop`() {
        val controller = controller()
        val target = controller.closeTargetRect()
        val radius = target.width() / 2f

        assertTrue("centred on the cross", controller.isOver(target.exactCenterX(), target.exactCenterY()))
        assertTrue(
            "half on the cross",
            controller.isOver(target.exactCenterX() + radius - 3f, target.exactCenterY()),
        )
        assertFalse(
            "beside the cross",
            controller.isOver(target.exactCenterX() + radius + 1f, target.exactCenterY()),
        )
        assertFalse(
            "above the cross",
            controller.isOver(target.exactCenterX(), target.top - controller.bubbleSize.toFloat()),
        )
        assertFalse("far from the cross", controller.isOver(60f, 60f))
    }

    /**
     * N6: the bubble is *pulled* towards the ✕ as it gets close, so the drop that closes the service
     * announces itself instead of being a surprise at the end of an unrelated drag.
     */
    @Test
    fun `far from the cross the bubble follows the finger exactly`() {
        val controller = controller()
        val target = controller.closeTargetRect()

        assertEquals(
            "a bubble nowhere near the ✕ was moved",
            Point(12, 12),
            controller.pullTowardsCloseTarget(12, 12),
        )
        // The bubble *centre* sits exactly on the edge of the reach: the pull starts inside it.
        val edge = controller.centred(target.exactCenterX() + controller.closePullRadius, target.exactCenterY())
        assertEquals(
            "the pull reached further than it says it does",
            Point(edge.x, edge.y),
            controller.pullTowardsCloseTarget(edge.x, edge.y),
        )
    }

    @Test
    fun `near the cross the bubble is drawn pulled towards it`() {
        val controller = controller()
        val target = controller.closeTargetRect()
        val reach = controller.closePullRadius.toFloat()

        val finger = controller.centred(target.exactCenterX() + reach / 2f, target.exactCenterY())
        val gap = controller.distanceFrom(target, controller.pullTowardsCloseTarget(finger.x, finger.y))

        assertTrue("the bubble was not pulled towards the ✕: $gap of ${reach / 2f}", gap < reach / 2f)
        assertTrue("the pull took the bubble past the ✕", gap > 0f)
        // Halfway out the pull is a quarter of the way in: three quarters of the gap are left.
        assertEquals(reach / 2f * 0.75f, gap, 1.5f)
    }

    @Test
    fun `right on the cross the bubble is drawn centred on it`() {
        val controller = controller()
        val target = controller.closeTargetRect()

        val finger = controller.centred(target.exactCenterX(), target.exactCenterY())
        val drawn = controller.pullTowardsCloseTarget(finger.x, finger.y)

        assertEquals(target.exactCenterX(), drawn.x + controller.bubbleSize / 2f, 1f)
        assertEquals(target.exactCenterY(), drawn.y + controller.bubbleSize / 2f, 1f)
    }

    /** The pull is a pull: it never draws the bubble farther from the ✕ than the finger is. */
    @Test
    fun `the pull never pushes the bubble away from the cross`() {
        val controller = controller()
        val target = controller.closeTargetRect()

        // Walked from the far side inwards: getting closer to the ✕ must never draw the bubble
        // farther from it. Each position is rounded to whole pixels, so the check allows a pixel.
        var previous = Float.MAX_VALUE
        var distance = controller.closePullRadius + 40
        while (distance >= 0) {
            val finger = controller.centred(target.exactCenterX() + distance, target.exactCenterY())
            val gap = controller.distanceFrom(target, controller.pullTowardsCloseTarget(finger.x, finger.y))

            assertTrue("at $distance the bubble is farther than the finger: $gap", gap <= distance + 1f)
            assertTrue("at $distance the pull went backwards: $gap > $previous", gap <= previous + 2f)
            previous = gap
            distance -= 7
        }
    }

    /**
     * The whole gesture, through the touch listener the phone uses: a drag that ends near the ✕ is
     * pulled onto it, and letting go there asks to close. Without the pull the bubble would still be
     * beside the ✕ and letting go would do nothing.
     */
    @Test
    fun `dragging near the cross draws the bubble onto it and the release asks`() {
        val asked = ArrayList<String>()
        val controller = controller(asked)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")
        val target = controller.closeTargetRect()

        val finger = Point(100, 100)
        val wanted = controller.centred(target.exactCenterX() + 45f, target.exactCenterY())
        val start = controller.bubbleOrigin()
        assertFalse("the finger alone was already on the ✕", controller.isOverCloseTarget(wanted.x, wanted.y))

        bubble.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, finger.x, finger.y))
        bubble.dispatchTouchEvent(
            touch(
                MotionEvent.ACTION_MOVE,
                finger.x + (wanted.x - start.x),
                finger.y + (wanted.y - start.y),
            ),
        )

        val drawn = controller.bubbleOrigin()
        assertTrue("the ✕ did not come up during the drag", controller.isCloseTargetVisible())
        assertTrue("the bubble was not pulled towards the ✕: $drawn", drawn.x < wanted.x)
        assertTrue("the bubble is not drawn on the ✕: $drawn", controller.isOverCloseTarget(drawn.x, drawn.y))

        bubble.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 0, 0))

        assertTrue("letting go on the ✕ asked nothing", controller.isCloseConfirmVisible())
        assertEquals("the release closed the service without asking", emptyList<String>(), asked)
    }

    @Test
    fun `dropping the bubble on the cross asks before closing`() {
        val asked = ArrayList<String>()
        val controller = controller(asked)
        val target = controller.closeTargetRect()

        controller.endDrag(
            (target.exactCenterX() - controller.bubbleSize / 2f).toInt(),
            (target.exactCenterY() - controller.bubbleSize / 2f).toInt(),
        )

        assertTrue("nothing asked whether to close", controller.isCloseConfirmVisible())
        assertEquals("the service was closed without asking", emptyList<String>(), asked)
    }

    @Test
    fun `dropping the bubble away from the cross asks nothing`() {
        val asked = ArrayList<String>()
        val controller = controller(asked)

        controller.endDrag(60, 60)

        assertFalse("a question appeared for no reason", controller.isCloseConfirmVisible())
        assertEquals(emptyList<String>(), asked)
    }

    /** «Encerrar» has to be the only thing that stops the service, and «Cancelar» has to do nothing. */
    @Test
    fun `the question closes the service only when it is answered yes`() {
        val asked = ArrayList<String>()
        val answers = buttons(controller(asked).buildCloseConfirm())

        assertEquals(listOf("Cancelar", "Encerrar"), answers.map { it.text.toString() })

        answers[0].performClick()
        assertEquals("«Cancelar» closed the service", emptyList<String>(), asked)

        answers[1].performClick()
        assertEquals("«Encerrar» did not close the service", listOf("stop"), asked)
    }

    @Test
    fun `the cross is a red cross with a name`() {
        val target = controller().buildCloseTarget()

        assertNotNull("the cross has nothing drawn on it", target.drawable)
        assertEquals(
            context.getString(R.string.overlay_drop_to_close),
            target.contentDescription?.toString(),
        )
        assertEquals(
            "the cross is not in the theme's red",
            context.getColor(R.color.warn),
            target.imageTintList?.defaultColor,
        )
    }

    /**
     * The red marks the *text*, not the button, so the safe answer keeps the louder background and
     * closing the service stays the quiet option — the opposite would sell the accident this whole
     * question exists to prevent.
     */
    @Test
    fun `the warning red is on the text, not on the button`() {
        val answers = buttons(controller().buildCloseConfirm())

        assertEquals(
            "«Encerrar» lost the warning red",
            context.getColor(R.color.warn),
            answers[1].currentTextColor,
        )
        assertEquals(
            "«Cancelar» was painted as the dangerous answer",
            context.getColor(R.color.text_primary),
            answers[0].currentTextColor,
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun controller(asked: MutableList<String> = ArrayList()): OverlayController =
        OverlayController(context).apply { callbacks = RecordingCallbacks(asked) }

    /** Asks about a bubble whose *centre* is at ([centreX], [centreY]). */
    private fun OverlayController.isOver(centreX: Float, centreY: Float): Boolean {
        val half = bubbleSize / 2
        return isOverCloseTarget((centreX - half).toInt(), (centreY - half).toInt())
    }

    /** A bubble whose *centre* is at ([centreX], [centreY]), as the controller takes a position. */
    private fun OverlayController.centred(centreX: Float, centreY: Float): Point {
        val half = bubbleSize / 2f
        return Point((centreX - half).roundToInt(), (centreY - half).roundToInt())
    }

    /** How far the *centre* of a drawn bubble is from the ✕. */
    private fun OverlayController.distanceFrom(target: Rect, drawn: Point): Float {
        val half = bubbleSize / 2f
        return hypot(
            target.exactCenterX() - (drawn.x + half),
            target.exactCenterY() - (drawn.y + half),
        )
    }

    private fun touch(action: Int, x: Int, y: Int): MotionEvent =
        MotionEvent.obtain(0L, 0L, action, x.toFloat(), y.toFloat(), 0)

    /** Every button in a panel, in the order the panel added them. */
    private fun buttons(root: View): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(view: View) {
            if (view.tag == ACTION_BUTTON_TAG) found.add(view as TextView)
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
        }
        walk(root)
        return found
    }
}
