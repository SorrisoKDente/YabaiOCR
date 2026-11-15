package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.KanjiEntry
import dev.yabai.ocr.core.dict.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for folding the result panel: sections on their own, and the whole card down to one line.
 *
 * The panel hangs from the top of the screen and grows until the screen runs out, and in manga the
 * text being read is underneath it. What these tests hold down is that folding actually gives screen
 * back, and that the one line a folded card keeps is the answer rather than a label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class PanelFoldTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The dictionary is the long section; folding it has to shorten the card, not just hide it. */
    @Test
    @Config(qualifiers = "w320dp-h1600dp-mdpi")
    fun `folding a section shortens the card and unfolding brings it back`() {
        val panel = panel()
        val before = layout(panel, 320).measuredHeight

        section(panel, SECTION_DICTIONARY).performClick()
        val folded = layout(panel, 320).measuredHeight
        assertTrue("folding the dictionary saved no height: $before → $folded", folded < before)
        assertEquals(View.GONE, body(panel, SECTION_DICTIONARY).visibility)

        section(panel, SECTION_DICTIONARY).performClick()
        assertEquals(View.VISIBLE, body(panel, SECTION_DICTIONARY).visibility)
        assertEquals("unfolding did not give the height back", before, layout(panel, 320).measuredHeight)
    }

    /** A folded section has to stay folded: a re-analysis rebuilds every block from scratch. */
    @Test
    fun `a folded section stays folded when the text is looked up again`() {
        val panel = panel()
        section(panel, SECTION_DICTIONARY).performClick()

        analyseAgain(panel)

        assertNotNull("the dictionary left the panel", section(panel, SECTION_DICTIONARY))
        assertEquals("the fold was forgotten", View.GONE, body(panel, SECTION_DICTIONARY).visibility)
        assertEquals(
            "the other sections folded with it",
            View.VISIBLE,
            body(panel, SECTION_TRANSLATION).visibility,
        )
    }

    /** The header's chevron folds the whole card to one line, and that line keeps the translation. */
    @Test
    fun `the whole card folds to a strip that still shows the translation`() {
        val panel = panel()
        assertEquals(context.getString(R.string.result_fold), foldControl(panel).contentDescription)

        foldControl(panel).performClick()

        val card = card(panel)
        for (index in 1 until card.childCount) {
            assertEquals(
                "row $index of the card is still on screen",
                View.GONE,
                card.getChildAt(index).visibility,
            )
        }
        assertEquals(TRANSLATION, headerText(panel).toString())
        assertEquals(context.getString(R.string.result_unfold), foldControl(panel).contentDescription)

        foldControl(panel).performClick()

        assertEquals(View.VISIBLE, card.getChildAt(1).visibility)
        assertEquals(context.getString(R.string.result_title), headerText(panel).toString())
    }

    /**
     * Folding is about the answer in front of the user: a new capture opens unfolded.
     *
     * A second capture also reuses the panel's own section views, and a view that already has a
     * parent cannot be added to another one — so this is the test that caught the two sections being
     * put into a card that was already gone.
     */
    @Test
    fun `a new capture opens unfolded`() {
        val panel = panel()
        foldControl(panel).performClick()

        show(panel, SENTENCE)

        assertEquals(View.VISIBLE, card(panel).getChildAt(1).visibility)
        assertEquals(context.getString(R.string.result_title), headerText(panel).toString())
    }

    // --- the panel under test -------------------------------------------------------------------

    /** A capture of a line of manga, translated, with kanji and a handful of dictionary entries. */
    private fun panel(): ResultOverlay = ResultOverlay(context).apply {
        show(this, SENTENCE)
        analyseAgain(this)
    }

    /** Exactly what the service does when a lookup lands: the two sections are rebuilt in place. */
    private fun analyseAgain(panel: ResultOverlay) {
        panel.setTranslation(ResultOverlay.TranslationState.Done(TRANSLATION))
        panel.setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf("職員", "外出", "非童帝").mapIndexed { index, term ->
                    TokenMatch(
                        surface = term,
                        start = index,
                        end = index + term.length,
                        entry = DictionaryEntry(
                            term = term,
                            reading = "よみ",
                            tags = "n",
                            rulesFlags = 0,
                            score = 1,
                            definition = "1. definição\n2. segunda definição\n3. terceira definição",
                        ),
                        dictionaryTitle = "JMdict",
                        reasons = listOf("past"),
                    )
                },
                kanji = listOf(KanjiEntry(character = "職", body = "emprego; cargo")),
                dictionaryCount = 1,
            ),
        )
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

    // --- helpers --------------------------------------------------------------------------------

    /** The card, which is the overlay's only child. */
    private fun card(root: ViewGroup): LinearLayout = root.getChildAt(0) as LinearLayout

    /** The text of the card's top row: the title, or the answer when the card is folded. */
    private fun headerText(root: ViewGroup): CharSequence =
        (card(root).getChildAt(0) as ViewGroup).getChildAt(0).let { (it as TextView).text }

    /** The header's fold control: an image, not one of the panel's buttons. */
    private fun foldControl(root: ViewGroup): ImageView {
        val header = card(root).getChildAt(0) as ViewGroup
        for (index in 0 until header.childCount) {
            val child = header.getChildAt(index)
            if (child is ImageView) return child
        }
        error("the panel has no fold control")
    }

    /** The section header the user taps, found by the key the panel tags it with. */
    private fun section(root: ViewGroup, key: String): View {
        fun find(group: ViewGroup): View? {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child.tag == key) return child
                if (child is ViewGroup) find(child)?.let { return it }
            }
            return null
        }
        return find(root) ?: error("the panel has no «$key» section")
    }

    /** The body a section header hides. */
    private fun body(root: ViewGroup, key: String): View =
        (section(root, key).parent as ViewGroup).getChildAt(1)

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

        const val SECTION_TRANSLATION = "translation"
        const val SECTION_DICTIONARY = "dictionary"
    }
}
