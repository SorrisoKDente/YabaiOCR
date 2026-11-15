package dev.yabai.ocr.overlay

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.Furigana
import dev.yabai.ocr.core.dict.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The readings the manga did not print, written into the recognised text.
 *
 * The reading comes from the dictionary engine, but where it *lands* is the panel's business: it goes
 * into the sentence the user is already reading, beside the kanji it reads, instead of a «Furigana»
 * block of its own — a whole page included, which is what N5 asked for. These tests hold three things
 * down: that the reading reaches the text, that it does not overwrite a correction, and that writing
 * it does not make the dictionary read its own answer back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class FuriganaPanelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the reading of the sentence is written into the recognised text`() {
        val panel = panel()

        assertEquals("職員（しょくいん）さんに聞（き）いた", panel.currentText())
        assertNull("the reading is not a section of its own any more", section(panel, SECTION_FURIGANA))
    }

    /** N5: the whole page, not just the piece a trecho happened to contain. */
    @Test
    fun `the reading of the whole page is written into the text`() {
        val panel = panel(
            text = "職員さんに聞いた\n外出したって",
            furigana = Furigana.Annotated("職員（しょくいん）さんに聞（き）いた\n外出（がいしゅつ）したって", uncertain = 0),
        )

        assertEquals(
            "職員（しょくいん）さんに聞（き）いた\n外出（がいしゅつ）したって",
            panel.currentText(),
        )
    }

    @Test
    fun `nothing to read puts the recognised text back`() {
        val panel = panel()
        assertEquals("職員（しょくいん）さんに聞（き）いた", panel.currentText())

        analyseAgain(panel, furigana = null)

        assertEquals("a missing reading must not leave a stale one behind", "職員さんに聞いた", panel.currentText())
        assertNull("and there is nothing to explain", note(panel))
    }

    /** A reading taken from the kanji data is a guess, and the panel says so under the words. */
    @Test
    fun `a guessed reading is called out under the sentence`() {
        val sure = panel(furigana = Furigana.Annotated("職員（しょくいん）さんに聞（き）いた", uncertain = 0))
        assertNull("no guess, no note", note(sure))

        val guess = panel(
            text = "滉",
            furigana = Furigana.Annotated("滉（こう?）", uncertain = 1),
        )
        assertEquals(context.getString(R.string.result_furigana_note), note(guess)?.text.toString())
    }

    /** A lookup can land after the user has corrected the sentence; the correction wins. */
    @Test
    fun `an edit is not overwritten by a later lookup`() {
        val panel = panel()
        editor(panel).setText("職員さんに聞いた！")

        analyseAgain(panel, furigana = Furigana.Annotated("職員（しょくいん）さんに聞（き）いた？", uncertain = 0))

        assertEquals("職員さんに聞いた！", panel.currentText())
    }

    /**
     * The write is the panel's, not the user's: a reading landing after another must replace the
     * previous one instead of counting as an edit and locking the text.
     */
    @Test
    fun `a new reading replaces the previous one`() {
        val panel = panel()
        assertEquals("職員（しょくいん）さんに聞（き）いた", panel.currentText())

        analyseAgain(panel, furigana = Furigana.Annotated("職員（しょくいん）さんに聞いた", uncertain = 0))

        assertEquals("職員（しょくいん）さんに聞いた", panel.currentText())
    }

    /** Writing the reading is not a correction, so it must not buy another lookup. */
    @Test
    fun `writing the reading does not ask for another lookup`() {
        val asked = ArrayList<String>()
        val panel = panel(callbacks = recordingCallbacks(asked))

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_000))

        assertEquals("the panel asked to analyse its own writing", emptyList<String>(), asked)
        assertEquals("職員（しょくいん）さんに聞（き）いた", panel.currentText())
    }

    // --- the panel under test ---------------------------------------------------------------------

    private fun panel(
        text: String = "職員さんに聞いた",
        furigana: Furigana.Annotated? = Furigana.Annotated("職員（しょくいん）さんに聞（き）いた", uncertain = 0),
        callbacks: ResultOverlay.Callbacks? = null,
    ): ResultOverlay = ResultOverlay(context).apply {
        this.callbacks = callbacks
        show(this, text)
        analyseAgain(this, furigana)
    }

    /** The same entry point the service uses for a finished capture. */
    private fun show(panel: ResultOverlay, text: String) {
        panel.showResult(
            text = text,
            meta = "Google Lens · 2348 ms · ja",
            notes = listOf("texto obtido com o Google Lens"),
            showLines = false,
            segmentLines = emptyList(),
            canRepick = true,
        )
    }

    /** Exactly what the service does when a lookup lands. */
    private fun analyseAgain(panel: ResultOverlay, furigana: Furigana.Annotated?) {
        panel.setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf(
                    TokenMatch(
                        surface = "職員",
                        start = 0,
                        end = 2,
                        entry = DictionaryEntry(
                            term = "職員",
                            reading = "しょくいん",
                            tags = "n",
                            rulesFlags = 0,
                            score = 1,
                            definition = "1. funcionário",
                        ),
                        dictionaryTitle = "JMdict",
                        reasons = emptyList(),
                    ),
                ),
                kanji = emptyList(),
                dictionaryCount = 1,
                furigana = furigana,
            ),
        )
    }

    // --- helpers ----------------------------------------------------------------------------------

    /** The section header the user taps, found by the key the panel tags it with. */
    private fun section(root: ViewGroup, key: String): View? {
        fun find(group: ViewGroup): View? {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child.tag == key) return child
                if (child is ViewGroup) find(child)?.let { return it }
            }
            return null
        }
        return find(root)
    }

    /** The container the panel fills with sections; the dictionary block is always one of them. */
    private fun wordsSection(root: ViewGroup): ViewGroup =
        (section(root, SECTION_DICTIONARY)!!.parent as ViewGroup).parent as ViewGroup

    /** The note the panel writes when a reading is a guess; it sits above the word list. */
    private fun note(root: ViewGroup): TextView? =
        wordsSection(root).getChildAt(0) as? TextView

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

    /** A [ResultOverlay.Callbacks] that only remembers what it was asked to analyse. */
    private fun recordingCallbacks(asked: MutableList<String>): ResultOverlay.Callbacks =
        object : ResultOverlay.Callbacks {
            override fun onCopyOriginal(text: String) = Unit
            override fun onCopyTranslation(text: String) = Unit
            override fun onAnalyse(text: String) {
                asked.add(text)
            }

            override fun onRepick() = Unit
            override fun onRetryOffline() = Unit
            override fun onImageToggle() = Unit
            override fun onAskAi(text: String) = Unit
            override fun onDictionaryPicked(title: String?) = Unit
            override fun onDismiss() = Unit
        }

    private companion object {
        const val SECTION_FURIGANA = "furigana"
        const val SECTION_DICTIONARY = "dictionary"
    }
}
