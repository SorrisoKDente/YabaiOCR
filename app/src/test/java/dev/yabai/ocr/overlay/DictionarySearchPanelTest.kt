package dev.yabai.ocr.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration

/**
 * The dictionary search card (R31).
 *
 * The bubble's «Dicionário» opens the result panel with an empty field: the same word cards, kanji
 * block and dictionary chooser, but the text is a query, not a capture. What these tests hold down is
 * that the card offers a search box and not a set of capture actions, that a pause in typing reaches
 * the app as a query, that suggestions are chips which put their word back in the box, and that the
 * dictionary chooser keeps working — «todos» or one dictionary, as with a translation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class DictionarySearchPanelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the card is a search box, not the capture result`() {
        val panel = searchPanel()

        val field = editor(panel)
        assertEquals("a search starts with an empty field", "", field.text.toString())
        assertEquals(
            context.getString(R.string.dictionary_search_hint),
            field.hint.toString(),
        )

        val texts = labels(panel).map { it.text.toString() }
        assertTrue(
            "the card should say it is a dictionary: $texts",
            texts.contains(context.getString(R.string.dictionary_search_title)),
        )
        assertFalse(
            "a search has nothing to copy: $texts",
            texts.contains(context.getString(R.string.result_copy_original)),
        )
        assertFalse(
            "a search is not a question to the AI: $texts",
            texts.contains(context.getString(R.string.result_ask_ai)),
        )
        assertFalse(
            "a search has no translation: $texts",
            texts.contains(context.getString(R.string.result_translation_label)),
        )
        assertNull(
            "a search has no grid of capture actions",
            findActionGrid(panel),
        )
    }

    @Test
    fun `a pause in typing reaches the app as a query`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)

        editor(panel).setText("日本")
        assertTrue("the panel asked before the pause was over: ${recorder.searches}", recorder.searches.isEmpty())

        idleForSearch()
        assertEquals(listOf("日本"), recorder.searches)
    }

    @Test
    fun `the suggestions are chips, and one puts its word in the box`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)
        editor(panel).setText("日本")
        idleForSearch()

        panel.setWords(
            ResultOverlay.WordsState.Done(
                matches = emptyList(),
                kanji = emptyList(),
                dictionaryCount = TITLES.size,
                titles = TITLES,
                query = "日本",
                suggestions = listOf("日本語", "日本人"),
            ),
        )
        layout(panel)

        val chips = labels(panel).filter { it.text.toString() in setOf("日本語", "日本人") }
        assertEquals(listOf("日本語", "日本人"), chips.map { it.text.toString() })

        chips.first().performClick()
        idleForSearch()

        assertEquals("the chip did not fill the box", "日本語", editor(panel).text.toString())
        assertEquals(
            "the chip did not ask for an answer of its own",
            listOf("日本", "日本語"),
            recorder.searches,
        )
    }

    @Test
    fun `an empty box says what to type instead of «nothing found»`() {
        val panel = searchPanel()

        val texts = labels(panel).map { it.text.toString() }
        assertTrue(
            "an empty box needs an instruction, got $texts",
            texts.contains(context.getString(R.string.dictionary_search_empty)),
        )
        assertFalse(
            "an empty box did not search for anything, so «nothing found» is a lie: $texts",
            texts.contains(context.getString(R.string.dictionary_no_match)),
        )
    }

    @Test
    fun `the dictionary chooser works in the search card`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder, selected = null)

        val picker = labels(panel).firstOrNull { it.text.toString() == "Todos ▾" }
            ?: error("the search card offered no dictionary chooser: ${labels(panel).map { it.text }}")
        picker.performClick()
        layout(panel)

        assertEquals(
            listOf("Todos", "JMdict", "Jitendex"),
            labels(panel).filter { it.text.toString() in setOf("Todos", "JMdict", "Jitendex") }
                .map { it.text.toString() },
        )

        labels(panel).first { it.text.toString() == "JMdict" }.performClick()

        assertEquals(listOf<String?>("JMdict"), recorder.dictionaries)
    }

    @Test
    fun `the query is kept after an answer lands`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)
        editor(panel).setText("日本")
        idleForSearch()

        panel.setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf(match()),
                kanji = emptyList(),
                dictionaryCount = TITLES.size,
                titles = TITLES,
                query = "日本",
                furigana = dev.yabai.ocr.core.dict.Furigana.Annotated("日本（にほん）", uncertain = 0),
            ),
        )

        assertEquals(
            "the dictionary answer wrote its reading over the query",
            "日本",
            editor(panel).text.toString(),
        )
    }

    @Test
    fun `back closes the search`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)

        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertEquals("there is no capture behind a search to go back to", 1, recorder.dismissed)
    }

    @Test
    fun `space in the search box does not close it`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)

        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
        panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))

        assertEquals("the keyboard's space closed the search", 0, recorder.dismissed)
    }

    @Test
    fun `the clear button empties the field and asks for an empty answer`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)
        editor(panel).setText("日本")
        idleForSearch()
        assertEquals(listOf("日本"), recorder.searches)

        // Acha o botão ✕ ao lado do campo pelo conteúdo de acessibilidade
        val clearButton = labels(panel).firstOrNull {
            it.contentDescription?.toString() == context.getString(R.string.dictionary_search_clear)
        } ?: error("the clear button was not found: ${labels(panel).map { it.contentDescription }}")
        clearButton.performClick()

        assertEquals("the clear button did not empty the field", "", editor(panel).text.toString())
        idleForSearch()
        assertEquals(listOf("日本", ""), recorder.searches)
    }

    @Test
    fun `the button beside an empty box pastes the clipboard and searches`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "  剣技\n"))

        val paste = searchButton(panel, R.string.dictionary_search_paste_description)
        paste.performClick()

        assertEquals("the pasted query did not reach the box", "剣技", editor(panel).text.toString())
        assertEquals(
            "after pasting, the button has something to clear again",
            context.getString(R.string.dictionary_search_clear),
            paste.contentDescription.toString(),
        )
        idleForSearch()
        assertEquals(listOf("剣技"), recorder.searches)
    }

    @Test
    fun `the button turns back into Colar when the field is emptied`() {
        val panel = searchPanel()

        assertEquals(
            "an empty box offers the paste action",
            context.getString(R.string.dictionary_search_paste),
            searchButton(panel, R.string.dictionary_search_paste_description).text.toString(),
        )

        editor(panel).setText("日本")
        val clear = searchButton(panel, R.string.dictionary_search_clear)
        assertEquals("✕", clear.text.toString())

        clear.performClick()

        assertEquals("", editor(panel).text.toString())
        searchButton(panel, R.string.dictionary_search_paste_description)
    }

    @Test
    fun `an empty clipboard says so instead of emptying the box`() {
        val recorder = Recorder()
        val panel = searchPanel(recorder)
        clipboard.setPrimaryClip(ClipData.newPlainText("vazio", ""))

        searchButton(panel, R.string.dictionary_search_paste_description).performClick()

        assertEquals("an empty clipboard must not write anything", "", editor(panel).text.toString())
        assertEquals(
            context.getString(R.string.dictionary_search_clipboard_empty),
            ShadowToast.getTextOfLatestToast(),
        )
        idleForSearch()
        assertTrue(
            "an empty clipboard must not ask the dictionary: " +
                recorder.searches.joinToString("|") { "<$it>" },
            recorder.searches.isEmpty(),
        )
    }

    @Test
    fun `the last query is pre-filled when passed to the search card`() {
        val recorder = Recorder()
        val panel = ResultOverlay(context).apply {
            callbacks = recorder
            showSearch(TITLES, null, TITLES.size, initialQuery = "日本")
            layout(this)
        }

        assertEquals("the field did not start with the last query", "日本", editor(panel).text.toString())
        // A busca da query pré-preenchida é disparada logo ao abrir (sem esperar a pausa de digitação).
        assertTrue("the pre-filled query was not searched: ${recorder.searches}",
            recorder.searches.contains("日本"))
        idleForSearch()
        assertEquals(
            "opening the card must not search the same query twice",
            listOf("日本"),
            recorder.searches,
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun searchPanel(
        recorder: Recorder = Recorder(),
        selected: String? = null,
    ): ResultOverlay = ResultOverlay(context).apply {
        callbacks = recorder
        showSearch(TITLES, selected, TITLES.size)
        layout(this)
    }

    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, 320, view.measuredHeight)
    }

    private fun idleForSearch() {
        shadowOf(Looper.getMainLooper()).idleFor(SEARCH_DELAY)
    }

    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    /** O botão ao lado do campo, encontrado pelo estado que ele anuncia (R35b). */
    private fun searchButton(root: ViewGroup, description: Int): TextView =
        labels(root).firstOrNull { it.contentDescription?.toString() == context.getString(description) }
            ?: error(
                "no button described as «${context.getString(description)}»: " +
                    labels(root).map { it.contentDescription },
            )

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
        return find(root) ?: error("the panel has no editable field")
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

    private fun findActionGrid(root: ViewGroup): ActionGrid? {
        for (index in 0 until root.childCount) {
            when (val child = root.getChildAt(index)) {
                is ActionGrid -> return child
                is ViewGroup -> findActionGrid(child)?.let { return it }
            }
        }
        return null
    }

    private fun match(): TokenMatch = TokenMatch(
        surface = "日本",
        start = 0,
        end = 2,
        entry = DictionaryEntry(
            term = "日本",
            reading = "にほん",
            tags = "n",
            rulesFlags = 0,
            score = 200,
            definition = "1. Japão",
        ),
        dictionaryTitle = "JMdict",
        reasons = emptyList(),
    )

    private class Recorder : ResultOverlay.Callbacks {
        val searches = ArrayList<String>()
        val dictionaries = ArrayList<String?>()
        var dismissed = 0

        override fun onCopyOriginal(text: String) = Unit
        override fun onCopyTranslation(text: String) = Unit
        override fun onAnalyse(text: String) = Unit
        override fun onRepick() = Unit
        override fun onRetryOffline() = Unit
        override fun onImageToggle() = Unit
        override fun onAskAi(text: String) = Unit

        override fun onDictionaryPicked(title: String?) {
            dictionaries.add(title)
        }

        override fun onSearchChanged(text: String) {
            searches.add(text)
        }

        override fun onDismiss() {
            dismissed++
        }
    }

    private companion object {
        val TITLES = listOf("JMdict", "Jitendex")
        val SEARCH_DELAY: Duration = Duration.ofMillis(400)
    }
}
