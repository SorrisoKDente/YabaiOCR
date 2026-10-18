package dev.yabai.ocr.overlay

import android.content.Context
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

/**
 * Tests for closing the service by dragging the bubble onto a ✕.
 *
 * The gesture itself is a handful of lines in `onBubbleTouch`; what it can get wrong is where the ✕
 * is, what counts as landing on it and — above all — whether letting go there closes the service
 * without asking. That last one is the line between a feature and an accident, so it is what these
 * tests pin: a drop on the ✕ only ever *asks*.
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
