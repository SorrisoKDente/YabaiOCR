package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.Furigana
import dev.yabai.ocr.core.dict.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The «Furigana» block: the reading of the recognised sentence, written beside the kanji.
 *
 * The reading comes from the dictionary engine, but where it *lands* is the panel's business: a
 * reading over the wrong characters is worse than none, so these tests hold the block down to the
 * text it was given and to the two things that would spoil it — writing into the recognised text
 * (which feeds the lookup and the translation) and showing a guess as if it were a word.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class FuriganaPanelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the reading of the sentence is written beside the kanji`() {
        val panel = panel()

        val text = furiganaText(panel)
        assertEquals("職員（しょくいん）さんに聞（き）いた", text.text.toString())
        assertTrue("the reading has to be selectable to be copied", text.isTextSelectable)
        assertEquals("職員さんに聞いた", panel.currentText())
    }

    @Test
    fun `nothing to read means no block at all`() {
        val panel = panel(furigana = null)

        assertNull("the block should not be there", section(panel, SECTION_FURIGANA))
        assertEquals("a missing block must not touch the sentence", "職員さんに聞いた", panel.currentText())
    }

    /** A reading taken from the kanji data is a guess, and the panel says so under it. */
    @Test
    fun `a guessed reading is called out under the sentence`() {
        val sure = panel(furigana = Furigana.Annotated("職員（しょくいん）さんに聞（き）いた", uncertain = 0))
        assertNull("no guess, no note", note(sure))

        val guess = panel(furigana = Furigana.Annotated("滉（こう?）", uncertain = 1))
        assertEquals(context.getString(R.string.result_furigana_note), note(guess)?.text.toString())
    }

    @Test
    fun `the block folds, and stays folded when the text is looked up again`() {
        val panel = panel()
        section(panel, SECTION_FURIGANA)!!.performClick()
        assertEquals(View.GONE, body(panel, SECTION_FURIGANA).visibility)

        analyseAgain(panel, furigana = Furigana.Annotated("職員（よみ）さん", uncertain = 0))

        assertEquals("the fold was forgotten", View.GONE, body(panel, SECTION_FURIGANA).visibility)
        assertEquals("folding the reading folded the words too", View.VISIBLE, body(panel, SECTION_DICTIONARY).visibility)
    }

    /** The reading is of the sentence above it, so it comes before the words it was read from. */
    @Test
    fun `the block sits above the dictionary`() {
        val panel = panel()
        val words = wordsSection(panel)
        val headers = (0 until words.childCount).map { words.getChildAt(it).let { row -> (row as ViewGroup).getChildAt(0).tag } }

        assertTrue(
            "expected the reading before the words, got $headers",
            headers.indexOf(SECTION_FURIGANA) < headers.indexOf(SECTION_DICTIONARY),
        )
    }

    // --- the panel under test ---------------------------------------------------------------------

    private fun panel(
        furigana: Furigana.Annotated? = Furigana.Annotated("職員（しょくいん）さんに聞（き）いた", uncertain = 0),
    ): ResultOverlay = ResultOverlay(context).apply {
        show(this)
        analyseAgain(this, furigana)
    }

    /** The same entry point the service uses for a finished capture. */
    private fun show(panel: ResultOverlay) {
        panel.showResult(
            text = "職員さんに聞いた",
            meta = "Google Lens · 2348 ms · ja",
            notes = listOf("texto obtido com o Google Lens"),
            offlineAvailable = true,
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

    private fun body(root: ViewGroup, key: String): View =
        (section(root, key)!!.parent as ViewGroup).getChildAt(1)

    /** The container the panel fills with sections; the dictionary block is always one of them. */
    private fun wordsSection(root: ViewGroup): ViewGroup =
        (section(root, SECTION_DICTIONARY)!!.parent as ViewGroup).parent as ViewGroup

    private fun furiganaText(root: ViewGroup): TextView =
        body(root, SECTION_FURIGANA).let { (it as ViewGroup).getChildAt(0) as TextView }

    private fun note(root: ViewGroup): TextView? {
        val body = body(root, SECTION_FURIGANA) as ViewGroup
        return if (body.childCount > 1) body.getChildAt(1) as TextView else null
    }

    private companion object {
        const val SECTION_FURIGANA = "furigana"
        const val SECTION_DICTIONARY = "dictionary"
    }
}
