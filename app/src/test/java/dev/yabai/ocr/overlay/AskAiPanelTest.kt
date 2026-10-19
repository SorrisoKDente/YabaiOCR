package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import dev.yabai.ocr.R
import org.junit.Assert.assertEquals
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
 * Tests for «Perguntar à IA» in the result panel.
 *
 * The button is the whole feature as far as the panel is concerned: it hands the text it is showing to
 * whoever is listening, and everything else — the clipboard, the prompt, which app opens — happens
 * behind [ResultOverlay.Callbacks.onAskAi]. What these tests hold down is that the button is where
 * the setting says it should be, that it asks about the text *on screen* rather than the text as it
 * was recognised (a corrected kanji is exactly what somebody would ask about), and that a panel with
 * no text in it does not offer the question at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class AskAiPanelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the panel offers the question when the setting allows it`() {
        val with = labels(layout(panel(canAskAi = true), 320))
        val without = labels(layout(panel(canAskAi = false), 320))
        val question = context.getString(R.string.result_ask_ai)

        assertTrue("no «$question» button", question in with)
        assertTrue("the button showed up with the setting off", question !in without)
    }

    /** It is one more way of taking the text out of the panel, so it sits with the copy buttons. */
    @Test
    fun `the question is offered next to the copy buttons`() {
        val panel = layout(panel(canAskAi = true), 320)
        val question = button(panel, context.getString(R.string.result_ask_ai))

        assertEquals(
            "the question is not in the row of copy buttons",
            button(panel, context.getString(R.string.result_copy_original)).parent,
            question.parent,
        )
        assertTrue(
            "the question is not the first thing in the row",
            question.left <= button(panel, context.getString(R.string.result_copy_original)).left,
        )
    }

    @Test
    fun `the question is asked about the text on screen, corrections included`() {
        val panel = panel(canAskAi = true)
        val asked = ArrayList<String>()
        panel.callbacks = panelCallbacks(asked)
        layout(panel, 320)

        editor(panel).setText(CORRECTED)
        button(panel, context.getString(R.string.result_ask_ai)).performClick()

        assertEquals(listOf("ask ai$CORRECTED"), asked)
    }

    /** Nothing was recognised, so there is nothing to ask about. */
    @Test
    fun `an error panel does not offer the question`() {
        val panel = ResultOverlay(context).apply { showError(FAILED, offlineAvailable = true) }

        assertTrue(
            "the error panel offered to ask about a text it does not have",
            labels(layout(panel, 320)).none { it == context.getString(R.string.result_ask_ai) },
        )
    }

    /**
     * A share that found nowhere to go is news for the panel, not for a toast: a toast raised by the
     * service is drawn behind whatever app the user is looking at, which is how the failure went
     * unseen in the first place.
     */
    @Test
    fun `a note is written in the panel that is open`() {
        val (controller, panel) = openPanel()
        val note = context.getString(R.string.result_ai_no_app)

        controller.showResultNote(note)
        layout(panel, 320)

        assertTrue("the note is not on screen", note in texts(panel))
    }

    /** Pressing again while the answer is still there is not news; the panel should not grow. */
    @Test
    fun `the same note is written once`() {
        val (controller, panel) = openPanel()
        val note = context.getString(R.string.result_ai_no_app)

        controller.showResultNote(note)
        controller.showResultNote(note)
        layout(panel, 320)

        assertEquals(1, texts(panel).count { it == note })
    }

    // --- the panel under test -------------------------------------------------------------------

    /**
     * The panel asks for nothing by itself: it hands the question to the controller, and this is that
     * one line of wiring, which is where a «button that does nothing» would come from.
     */
    @Test
    fun `the question reaches the app through the controller`() {
        val asked = ArrayList<String>()
        val controller = OverlayController(context).apply { callbacks = RecordingCallbacks(asked) }

        ShadowSettings.setCanDrawOverlays(true)
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
        val panel = windows().filterIsInstance<ResultOverlay>().firstOrNull()
            ?: error("the controller did not put a result panel on screen")

        button(panel, context.getString(R.string.result_ask_ai)).performClick()

        assertEquals(listOf("ask ai$SENTENCE"), asked)
    }

    private fun panel(canAskAi: Boolean): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = SENTENCE,
            meta = "Google Lens · 2348 ms · ja",
            notes = emptyList(),
            offlineAvailable = true,
            showLines = false,
            segmentLines = emptyList(),
            canRepick = true,
            canAskAi = canAskAi,
        )
        setTranslation(ResultOverlay.TranslationState.Done(TRANSLATION))
    }

    // --- helpers --------------------------------------------------------------------------------

    /** A controller with a result panel on screen, the way the service leaves it after a capture. */
    private fun openPanel(): Pair<OverlayController, ResultOverlay> {
        val controller = OverlayController(context).apply { callbacks = RecordingCallbacks(ArrayList()) }
        ShadowSettings.setCanDrawOverlays(true)
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
        val panel = windows().filterIsInstance<ResultOverlay>().firstOrNull()
            ?: error("the controller did not put a result panel on screen")
        return controller to panel
    }

    private fun panelCallbacks(asked: MutableList<String>): ResultOverlay.Callbacks =
        object : ResultOverlay.Callbacks {
            override fun onCopyOriginal(text: String) = Unit
            override fun onCopyTranslation(text: String) = Unit
            override fun onAnalyse(text: String) = Unit
            override fun onRepick() = Unit
            override fun onRetryOffline() = Unit
            override fun onImageToggle() = Unit
            override fun onDictionaryPicked(title: String?) = Unit
            override fun onDismiss() = Unit

            override fun onAskAi(text: String) {
                asked.add("ask ai$text")
            }
        }

    /** The panel's editable recognised text. */
    private fun editor(root: ViewGroup): EditText {
        fun find(group: ViewGroup): EditText? {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is EditText -> return child
                    is ViewGroup -> find(child)?.let { return it }
                }
            }
            return null
        }
        return find(root) ?: error("the panel has no editable text")
    }

    /** Every button of the panel, in the order they appear. */
    private fun buttons(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView && child.tag == ACTION_BUTTON_TAG && child.visibility == View.VISIBLE) {
                    found.add(child)
                }
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    private fun button(root: ViewGroup, label: String): TextView =
        buttons(root).firstOrNull { it.text.toString() == label }
            ?: error("the panel has no «$label» button")

    private fun labels(root: ViewGroup): List<String> = buttons(root).map { it.text.toString() }

    /** Every string on screen, notes included. */
    private fun texts(root: ViewGroup): List<String> {
        val found = ArrayList<String>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView) found.add(child.text.toString())
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    /** Everything on screen, the way the window manager sees it. */
    private fun windows(): List<View> {
        val manager = context.getSystemService(WindowManager::class.java)
        return Shadow.extract<ShadowWindowManagerImpl>(manager).views
    }

    /** Measures and lays out [view] the way the window manager would at a screen [width]. */
    private fun <T : View> layout(view: T, width: Int): T {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, width, view.measuredHeight)
        return view
    }

    private companion object {
        const val SENTENCE = "しょくいんネオの職員さんに聞いたら非童帝くんいうから…いしゅつ外出したって"
        const val TRANSLATION = "Perguntei ao funcionário e ele disse que saiu por aí."
        const val CORRECTED = "職員さんに聞いた"
        const val FAILED = "Não foi possível reconhecer o texto."
    }
}
