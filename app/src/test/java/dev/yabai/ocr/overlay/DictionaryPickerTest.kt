package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
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
 * Tests for the dictionary selector in the panel.
 *
 * The panel is where a wrong or missing reading is noticed, and «this word is only in the other
 * dictionary» is a common enough question that answering it should not cost a trip to the settings
 * screen and another capture. What these tests hold down is that the header says which dictionary is
 * answering, that the chooser offers «Todos» plus each installed one, and that a choice is *handed to
 * the app* rather than applied here: storing the setting and running the lookup again is the app's
 * business, and the panel must not pretend to know it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class DictionaryPickerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the header says which dictionary is answering`() {
        assertEquals("JMdict ▾", pickerLabel(layout(resultPanel(TWO, "JMdict"), 320)))
        assertEquals("Todos ▾", pickerLabel(layout(resultPanel(TWO, null), 320)))
    }

    @Test
    fun `one dictionary is nothing to choose between`() {
        val panel = layout(resultPanel(listOf("JMdict"), null), 320)

        assertNull("the selector should stay out of the way", picker(panel))
    }

    @Test
    fun `the chooser is closed until it is asked for`() {
        val panel = layout(resultPanel(TWO, "JMdict"), 320)

        assertNull("the chooser was already open", chooser(panel))
    }

    @Test
    fun `the chooser offers all of them plus each installed dictionary`() {
        val panel = layout(resultPanel(THREE, "Jitendex"), 320)

        picker(panel)!!.performClick()
        layout(panel, 320)

        assertEquals(
            listOf(context.getString(R.string.dictionary_all), "JMdict", "Jitendex", "KANJIDIC"),
            chips(panel),
        )
    }

    @Test
    fun `choosing a dictionary tells the app which one, and folds the chooser away`() {
        val asked = ArrayList<String>()
        val panel = resultPanel(TWO, null)
        panel.callbacks = panelCallbacks(asked)
        layout(panel, 320)
        picker(panel)!!.performClick()
        layout(panel, 320)

        chip(panel, "JMdict").performClick()
        layout(panel, 320)

        assertEquals(listOf("dictionary JMdict"), asked)
        assertNull("the chooser stayed open after the answer was given", chooser(panel))
    }

    @Test
    fun `asking for all of them again is a choice too`() {
        val asked = ArrayList<String>()
        val panel = resultPanel(TWO, "JMdict")
        panel.callbacks = panelCallbacks(asked)
        layout(panel, 320)
        picker(panel)!!.performClick()
        layout(panel, 320)

        chip(panel, context.getString(R.string.dictionary_all)).performClick()

        assertEquals(listOf("dictionary todos"), asked)
    }

    @Test
    fun `opening the chooser unfolds a folded dictionary section`() {
        val panel = layout(resultPanel(TWO, "JMdict"), 320)
        header(panel).performClick()
        layout(panel, 320)

        assertEquals("the section should be folded to start with", View.GONE, body(panel).visibility)

        picker(panel)!!.performClick()
        layout(panel, 320)

        assertEquals(
            "the chooser lives in the body, so the body has to be there to see it",
            View.VISIBLE,
            body(panel).visibility,
        )
        assertTrue("the chooser did not open", chooser(panel) != null)
    }

    // --- the panel under test -------------------------------------------------------------------

    /** A finished result whose dictionary section came back with [titles] installed. */
    private fun resultPanel(titles: List<String>, selected: String?): ResultOverlay =
        ResultOverlay(context).apply {
            showResult(
                text = SENTENCE,
                meta = "Google Lens · 2348 ms · ja",
                notes = emptyList(),
                offlineAvailable = true,
                showLines = false,
                segmentLines = emptyList(),
                canRepick = false,
            )
            setWords(
                ResultOverlay.WordsState.Done(
                    matches = listOf(match("職員")),
                    kanji = emptyList(),
                    dictionaryCount = titles.size,
                    titles = titles,
                    selected = selected,
                ),
            )
        }

    private fun match(term: String): TokenMatch = TokenMatch(
        surface = term,
        start = 0,
        end = term.length,
        entry = DictionaryEntry(
            term = term,
            reading = "しょくいん",
            tags = "n",
            rulesFlags = 0,
            score = 1,
            definition = "1. funcionário; empregado",
        ),
        dictionaryTitle = "JMdict",
        reasons = emptyList(),
    )

    // --- helpers --------------------------------------------------------------------------------

    /** The dictionary section's header: the panel tags it with the fold key. */
    private fun header(root: ViewGroup): LinearLayout =
        byTag(root, "dictionary") as? LinearLayout ?: error("the panel has no dictionary section")

    /** The dictionary section's body, which is what folding takes away. */
    private fun body(root: ViewGroup): View = (header(root).parent as ViewGroup).getChildAt(1)

    /** The control in that header that says which dictionary is answering, if it is offered. */
    private fun picker(root: ViewGroup): TextView? {
        val sectionHeader = header(root)
        for (index in 0 until sectionHeader.childCount) {
            val child = sectionHeader.getChildAt(index)
            if (child is TextView && child.tag == ACTION_BUTTON_TAG) return child
        }
        return null
    }

    private fun pickerLabel(root: ViewGroup): String? = picker(root)?.text?.toString()

    /** The row of dictionaries, or null while it is closed. */
    private fun chooser(root: ViewGroup): ViewGroup? =
        byTag(root, ResultOverlay.DICTIONARY_CHOOSER_TAG) as? ViewGroup

    private fun chips(root: ViewGroup): List<String> {
        val row = chooser(root) ?: return emptyList()
        return (0 until row.childCount).map { index -> (row.getChildAt(index) as TextView).text.toString() }
    }

    private fun chip(root: ViewGroup, label: String): View {
        val row = chooser(root) ?: error("the chooser is not open")
        val index = chips(root).indexOf(label)
        check(index >= 0) { "no «$label» among ${chips(root)}" }
        return row.getChildAt(index)
    }

    /** The first view in the tree carrying [tag], which is how the panel's pieces are found. */
    private fun byTag(root: ViewGroup, tag: Any): View? {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child.tag == tag) return child
            if (child is ViewGroup) byTag(child, tag)?.let { return it }
        }
        return null
    }

    private fun panelCallbacks(asked: MutableList<String>): ResultOverlay.Callbacks =
        object : ResultOverlay.Callbacks {
            override fun onCopyOriginal(text: String) = Unit
            override fun onCopyTranslation(text: String) = Unit
            override fun onAnalyse(text: String) = Unit
            override fun onRepick() = Unit
            override fun onRetryOffline() = Unit
            override fun onImageToggle() = Unit
            override fun onAskAi(text: String) = Unit

            override fun onDictionaryPicked(title: String?) {
                asked.add("dictionary ${title ?: "todos"}")
            }

            override fun onDismiss() = Unit
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
        const val SENTENCE = "職員さんに聞いたら外出したって"
        val TWO = listOf("JMdict", "KANJIDIC")
        val THREE = listOf("JMdict", "Jitendex", "KANJIDIC")
    }
}
