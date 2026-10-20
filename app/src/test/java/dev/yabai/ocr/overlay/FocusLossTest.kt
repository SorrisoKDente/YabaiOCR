package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.ai.AskAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * Tests for the panel going away when the user leaves the app (R9).
 *
 * The panel is not a screen of this app: it is a floating window drawn over everything, and it belongs
 * to no task. Pressing HOME sends the manga reader behind it to the background and leaves the panel
 * floating over the launcher — the translation of a page nobody is looking at any more, with nothing
 * behind it to go back to. The signal used here is the window losing the input focus, which costs no
 * permission: the window is focusable anyway, for the editable text and for BACK.
 *
 * Both halves of that are held down here: the panel goes when the focus is taken by something that is
 * not this app, and it stays when it is this app that took the focus away on purpose — the list of apps
 * to ask, which is drawn over the panel and must come back to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class FocusLossTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `losing the window focus takes the panel down`() {
        val callbacks = RecordingCallbacks()
        val controller = openPanel(callbacks)
        val panel = panelOnScreen() ?: error("the controller did not put a result panel on screen")

        panel.onWindowFocusChanged(false)

        assertTrue("the panel was left floating over the launcher", panelOnScreen() == null)
        assertTrue(
            "the service was not told, so it keeps analysing and holding the frame",
            "result dismissed" in callbacks.calls,
        )
        // Nothing else was touched: the bubble is what the user comes back to.
        assertTrue("the controller still thinks a panel is open", !controller.isResultVisible())
    }

    /** The panel holding the focus is its normal state; coming back to it must not close it. */
    @Test
    fun `gaining the window focus leaves the panel alone`() {
        openPanel(RecordingCallbacks())
        val panel = panelOnScreen() ?: error("the controller did not put a result panel on screen")

        panel.onWindowFocusChanged(true)

        assertSame("the panel closed when it took the focus", panel, panelOnScreen())
    }

    /**
     * The list of apps is a window of this app, drawn over the panel: it takes the focus on purpose.
     * Dismissing it has to come back to the answer that was on screen, not to nothing.
     */
    @Test
    fun `the app list taking the focus does not take the panel with it`() {
        val controller = openPanel(RecordingCallbacks())
        val panel = panelOnScreen() ?: error("the controller did not put a result panel on screen")
        controller.showAskAiApps(listOf(AskAi.Target("ChatGPT", "com.openai.chatgpt")), SENTENCE)

        panel.onWindowFocusChanged(false)

        assertSame("the panel went away under the list of apps", panel, panelOnScreen())
        assertTrue("the list of apps went away with it", windows().any { it is AppPickerOverlay })

        controller.hideAppPicker()

        assertSame("dismissing the list left nothing to come back to", panel, panelOnScreen())
    }

    /**
     * Closing the panel drops its window, and dropping a window drops its focus. That late loss is the
     * panel's own doing and must not be read as the user walking away — the service would be told twice
     * about a panel it has already forgotten.
     */
    @Test
    fun `taking the panel down does not dismiss it a second time`() {
        val callbacks = RecordingCallbacks()
        openPanel(callbacks)
        val panel = panelOnScreen() ?: error("the controller did not put a result panel on screen")

        findButton(panel, context.getString(R.string.result_close))!!.performClick()
        panel.onWindowFocusChanged(false)

        assertEquals(listOf("result dismissed"), callbacks.calls)
    }

    // --- the panel under test -------------------------------------------------------------------

    private fun openPanel(callbacks: RecordingCallbacks): OverlayController {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = OverlayController(context).apply { this.callbacks = callbacks }
        controller.showResult(
            text = SENTENCE,
            meta = "Google Lens · 2348 ms · ja",
            notes = emptyList(),
            offlineAvailable = true,
            showLines = false,
            lines = emptyList(),
            canRepick = true,
            canAskAi = true,
        )
        return controller
    }

    private fun panelOnScreen(): ResultOverlay? = windows().filterIsInstance<ResultOverlay>().firstOrNull()

    /** The button labelled [label] anywhere in [root], or null. */
    private fun findButton(root: ViewGroup, label: String): View? {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is TextView && child.text.toString() == label) return child
            if (child is ViewGroup) findButton(child, label)?.let { return it }
        }
        return null
    }

    private fun windows(): List<View> {
        val manager = context.getSystemService(WindowManager::class.java)
        return Shadow.extract<ShadowWindowManagerImpl>(manager).views
    }

    private companion object {
        const val SENTENCE = "職員さんに聞いた"
    }
}
