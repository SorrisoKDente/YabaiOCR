package dev.yabai.ocr.overlay

import android.content.Context
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.Furigana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What leaves the panel when the text on screen carries readings (R20).
 *
 * The panel writes the furigana into the recognised sentence — 「職員（しょくいん）さんに聞（き）いた」 — so the
 * reading sits beside the kanji it reads. That is right for reading and wrong for sending: the
 * clipboard and «Perguntar à IA» have to get 「職員さんに聞いた」, or every copied line carries
 * parentheses that are not part of the text.
 *
 * The exception is the user's own correction: once they type, the editor is the only copy of what
 * they meant, and the clean original would undo it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class CopyCleanTextTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val clean = "職員さんに聞いた"
    private val annotated = "職員（しょくいん）さんに聞（き）いた"

    @Test
    fun `the panel does show the readings`() {
        val panel = annotatedPanel()

        // The premise of every test here: the panel really did write the readings into the sentence.
        assertEquals(annotated, editor(panel).text.toString())
    }

    @Test
    fun `copying the original sends the sentence without the readings`() {
        val recorder = Recorder()
        val panel = annotatedPanel().apply { callbacks = recorder }

        click(panel, context.getString(R.string.result_copy_original))

        assertEquals(listOf(clean), recorder.copied)
    }

    @Test
    fun `asking the AI sends the sentence without the readings`() {
        val recorder = Recorder()
        val panel = annotatedPanel().apply { callbacks = recorder }

        click(panel, context.getString(R.string.result_ask_ai))

        assertEquals(listOf(clean), recorder.asked)
    }

    @Test
    fun `a corrected sentence is sent as the user typed it`() {
        val recorder = Recorder()
        val panel = annotatedPanel().apply { callbacks = recorder }
        val corrected = "職員さんに訊いた"

        editor(panel).setText(corrected)
        click(panel, context.getString(R.string.result_copy_original))
        click(panel, context.getString(R.string.result_ask_ai))

        assertEquals(listOf(corrected), recorder.copied)
        assertEquals(listOf(corrected), recorder.asked)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun annotatedPanel(): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = clean,
            meta = "Google Lens · 1200 ms · ja",
            notes = emptyList(),
            showLines = false,
            segmentLines = emptyList(),
            canRepick = false,
            canAskAi = true,
        )
        setWords(
            ResultOverlay.WordsState.Done(
                matches = emptyList(),
                kanji = emptyList(),
                dictionaryCount = 1,
                furigana = Furigana.Annotated(annotated, uncertain = 0),
            ),
        )
        measure(
            android.view.View.MeasureSpec.makeMeasureSpec(320, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, 320, measuredHeight)
    }

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

    private fun click(root: ViewGroup, label: String) {
        val button = labels(root).firstOrNull { it.text.toString() == label }
            ?: error("no «$label» button in the panel")
        assertTrue("«$label» cannot be clicked", button.performClick())
    }

    private fun labels(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is TextView -> found.add(child)
                    is ViewGroup -> walk(child)
                }
            }
        }
        walk(root)
        return found
    }

    private class Recorder : ResultOverlay.Callbacks {
        val copied = ArrayList<String>()
        val asked = ArrayList<String>()

        override fun onCopyOriginal(text: String) {
            copied.add(text)
        }

        override fun onCopyTranslation(text: String) = Unit
        override fun onAnalyse(text: String) = Unit
        override fun onRepick() = Unit
        override fun onRetryOffline() = Unit
        override fun onImageToggle() = Unit
        override fun onDictionaryPicked(title: String?) = Unit
        override fun onDismiss() = Unit

        override fun onAskAi(text: String) {
            asked.add(text)
        }
    }
}
