package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Pausing the floating reader (R33c): the bubble goes away, the panels it owns go with it, and
 * resuming puts the bubble back where it was instead of at the default corner.
 *
 * The service keeps the capture session alive while paused — that is the difference from «Parar» —
 * so the thing to pin down here is the overlay side: nothing may remain on top of the reader app,
 * and a capture that was already running may not bring the bubble back after the pause.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class PauseTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `pausing hides the bubble and resuming puts it back in place`() {
        val controller = OverlayController(context)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")
        val origin = controller.bubbleOrigin()

        controller.setPaused(true)

        assertTrue("the reader was not marked paused", controller.isPaused())
        assertEquals("the bubble stayed on screen while paused", View.GONE, bubble.visibility)

        controller.setPaused(false)

        assertFalse("the reader stayed paused", controller.isPaused())
        assertEquals("the bubble did not come back", View.VISIBLE, bubble.visibility)
        assertEquals("the bubble came back in another place", origin, controller.bubbleOrigin())
    }

    @Test
    fun `pausing takes the result panel down with it`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = OverlayController(context)
        controller.showBubble()
        controller.showResultLoading()
        assertTrue("the panel did not open", controller.isResultVisible())

        controller.setPaused(true)

        assertFalse("the result panel stayed over the reader app", controller.isResultVisible())
    }

    @Test
    fun `a capture ending after the pause does not bring the bubble back`() {
        val controller = OverlayController(context)
        controller.showBubble()
        val bubble = controller.bubbleWindow() ?: error("the bubble was not created")

        controller.hideAllForCapture()
        controller.setPaused(true)
        controller.restoreAfterCapture()

        assertEquals("the end of a capture overrode the pause", View.GONE, bubble.visibility)
    }

    @Test
    fun `a result arriving after the pause does not open the panel`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = OverlayController(context)
        controller.showBubble()
        controller.setPaused(true)

        controller.showResult(
            text = "猫",
            meta = "Google Lens · 900 ms · ja",
            notes = emptyList(),
            offlineAvailable = true,
            showLines = false,
            lines = emptyList(),
            canRepick = false,
        )

        assertFalse("a paused reader opened the result panel", controller.isResultVisible())
    }
}
