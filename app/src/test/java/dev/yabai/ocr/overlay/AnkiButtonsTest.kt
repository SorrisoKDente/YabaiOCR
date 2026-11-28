package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.util.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The two ways of sending a card from the phone's panel (A5).
 *
 * The panel's only job is to say **which** card the user wants: the word under the finger, or the
 * whole answer. The service behind the callback builds the note, queues it and writes the notice.
 * What these tests hold down is that the buttons are where the roadmap puts them, that they carry
 * the sentence the match was found in, that the per-word button stays out of the action grid, and
 * that the notice's «Desfazer» is a real action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class AnkiButtonsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val sentence = "職員さんに聞いた"
    private val match = TokenMatch(
        surface = "職員",
        start = 0,
        end = 2,
        entry = DictionaryEntry(
            term = "職員",
            reading = "しょくいん",
            tags = "n",
            rulesFlags = 0,
            score = 1,
            definition = "funcionário; empregado",
        ),
        dictionaryTitle = "JMdict",
        reasons = emptyList(),
    )

    @Test
    fun `the per-word button sends the word and the sentence it came from`() {
        val panel = panel()
        layout(panel)
        val recorder = Recorder()
        panel.callbacks = recorder

        val button = tagged(panel, ResultOverlay.ANKI_WORD_TAG).single()
        assertTrue(
            "the per-word target is only ${button.height}px tall",
            button.height >= context.dp(44),
        )
        button.performClick()

        assertEquals(match, recorder.word)
        assertEquals(sentence, recorder.sentence)
    }

    @Test
    fun `the sentence action sends the text and the matches`() {
        val panel = panel()
        layout(panel)
        val recorder = Recorder()
        panel.callbacks = recorder

        button(panel, context.getString(R.string.result_anki)).performClick()

        assertEquals(sentence, recorder.text)
        assertEquals(listOf(match), recorder.matches)
    }

    @Test
    fun `the sentence action waits for the first word`() {
        val panel = ResultOverlay(context).apply {
            showResult(
                text = sentence,
                meta = "Google Lens · 1 ms · ja",
                notes = emptyList(),
                showLines = false,
                segmentLines = emptyList(),
                canRepick = false,
                canAnki = true,
            )
        }
        layout(panel)
        val action = allButtons(panel).first { it.text == context.getString(R.string.result_anki) }

        assertEquals("the sentence card was offered before there was a word", View.GONE, action.visibility)

        panel.setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf(match),
                kanji = emptyList(),
                dictionaryCount = 1,
            ),
        )
        layout(panel)

        assertEquals(View.VISIBLE, action.visibility)
    }

    @Test
    fun `the setting off hides both buttons`() {
        val panel = panel(canAnki = false)
        layout(panel)

        assertTrue("a word still offered its own Anki button", tagged(panel, ResultOverlay.ANKI_WORD_TAG).isEmpty())
        assertTrue(
            "the sentence still offered its Anki button",
            allButtons(panel).none { it.text == context.getString(R.string.result_anki) },
        )
    }

    @Test
    fun `an error panel offers neither button`() {
        val panel = ResultOverlay(context).apply { showError("nada reconhecido", offlineAvailable = false) }
        layout(panel)

        assertTrue(tagged(panel, ResultOverlay.ANKI_WORD_TAG).isEmpty())
        assertTrue(
            "an error panel offered to send a card",
            allButtons(panel).none { it.text == context.getString(R.string.result_anki) },
        )
    }

    @Test
    fun `the notice can be undone and repeated text is written once`() {
        val panel = panel()
        var undone = false
        panel.addNote("Adicionado a “Yabai”", undo = { undone = true })
        panel.addNote("Adicionado a “Yabai”", undo = { undone = true })
        layout(panel)

        val message = "Adicionado a “Yabai”"
        assertEquals(
            "the same notice was written twice",
            1,
            texts(panel).count { it.text.toString() == message },
        )

        tagged(panel, ResultOverlay.ANKI_UNDO_TAG).single().performClick()

        assertTrue("the undo action did not reach the service", undone)
        assertTrue(
            "the undone line stayed above the answer",
            texts(panel).none { it.text.toString() == message },
        )
    }

    private fun panel(canAnki: Boolean = true): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = sentence,
            meta = "Google Lens · 1 ms · ja",
            notes = emptyList(),
            showLines = false,
            segmentLines = emptyList(),
            canRepick = false,
            canAskAi = true,
            canAnki = canAnki,
        )
        setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf(match),
                kanji = emptyList(),
                dictionaryCount = 1,
            ),
        )
    }

    private fun layout(view: View): View {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 640)
        return view
    }

    private fun tagged(root: ViewGroup, tag: String): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView && child.tag == tag) found.add(child)
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    private fun allButtons(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView && child.tag == ACTION_BUTTON_TAG) found.add(child)
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    private fun button(root: ViewGroup, label: String): TextView =
        allButtons(root).firstOrNull { it.text.toString() == label && it.visibility == View.VISIBLE }
            ?: error("the panel has no visible «$label» button")

    private fun texts(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is TextView) found.add(child)
                if (child is ViewGroup) walk(child)
            }
        }
        walk(root)
        return found
    }

    private class Recorder : ResultOverlay.Callbacks {
        var word: TokenMatch? = null
        var sentence = ""
        var text = ""
        var matches: List<TokenMatch> = emptyList()

        override fun onAnkiWord(match: TokenMatch, sentence: String) {
            word = match
            this.sentence = sentence
        }

        override fun onAnkiSentence(text: String, matches: List<TokenMatch>) {
            this.text = text
            this.matches = matches
        }

        override fun onCopyOriginal(text: String) = Unit
        override fun onCopyTranslation(text: String) = Unit
        override fun onAnalyse(text: String) = Unit
        override fun onRepick() = Unit
        override fun onRetryOffline() = Unit
        override fun onImageToggle() = Unit
        override fun onDictionaryPicked(title: String?) = Unit
        override fun onAskAi(text: String) = Unit
        override fun onDismiss() = Unit
    }
}
