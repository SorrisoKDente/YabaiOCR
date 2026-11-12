package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.core.dict.DictionaryEntry
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
 * What the panel does with a Jitendex definition (R19).
 *
 * The compiled text keeps a small line grammar — sense numbers, notes, examples — and the panel is
 * expected to use it: the number bold, the note and the example smaller and greyer. The part of
 * speech belongs under the headword, not inside the definition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class DefinitionPanelTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val definition = "1. to eat\n" +
        "ℹ passive form of 呼ぶ\n" +
        "例 もっと果物を食べるべきです。\n" +
        "→ You should eat more fruit.\n" +
        "2. to live on (e.g. a salary)"

    private fun panel(stale: List<String> = emptyList()): ResultOverlay = ResultOverlay(context).apply {
        showResult(
            text = "食べる",
            meta = "Google Lens · 812 ms · ja",
            notes = emptyList(),
            offlineAvailable = true,
            showLines = false,
            segmentLines = emptyList(),
            canRepick = true,
            canAskAi = true,
        )
        setWords(
            ResultOverlay.WordsState.Done(
                matches = listOf(
                    TokenMatch(
                        surface = "食べる",
                        start = 0,
                        end = 3,
                        entry = DictionaryEntry(
                            term = "食べる",
                            reading = "たべる",
                            tags = "1-dan・transitive",
                            rulesFlags = 0,
                            score = 1,
                            definition = definition,
                        ),
                        dictionaryTitle = "Jitendex",
                        reasons = listOf("masu"),
                    ),
                ),
                kanji = emptyList(),
                dictionaryCount = 1,
                stale = stale,
            ),
        )
    }

    /** Every label in the panel, so a test can look for the one it means. */
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

    private fun definitionView(root: ViewGroup): TextView? =
        labels(root).firstOrNull { it.text.startsWith("1. to eat") }

    /**
     * Regression: Jitendex's markup glued the tags to the definition and the panel drew the result as
     * one wall of text. The definition now carries its own marks, and the panel has to paint them.
     */
    @Test
    fun `the sense number is bold and the note is smaller`() {
        val panel = panel()
        val view = definitionView(panel)
        assertNotNull("the panel lost the definition", view)
        val text = view!!.text as Spanned

        val bold = text.getSpans(0, text.length, StyleSpan::class.java)
            .firstOrNull { it.style == Typeface.BOLD }
        assertNotNull("the sense number should be bold", bold)
        assertEquals("1. ", text.subSequence(text.getSpanStart(bold), text.getSpanEnd(bold)).toString())

        val note = definition.indexOf("ℹ ")
        val noteEnd = definition.indexOf('\n', note)
        val small = text.getSpans(note, noteEnd, RelativeSizeSpan::class.java)
        assertTrue("the note line should be drawn smaller", small.isNotEmpty())
        assertEquals(note, text.getSpanStart(small.first()))
        assertEquals(noteEnd, text.getSpanEnd(small.first()))
    }

    @Test
    fun `the part of speech sits under the headword`() {
        val details = labels(panel()).firstOrNull { it.text.contains("Jitendex") }
        assertNotNull("the panel lost its details line", details)
        assertTrue(
            "the tags should be shown with the dictionary: '${details!!.text}'",
            details.text.contains("1-dan・transitive"),
        )
    }

    /**
     * Regression R26: a dictionary compiled by an older version keeps the definitions it was
     * imported with, and the panel is the screen where the difference is read. It has to say why —
     * and name the dictionary the user has to reimport — or the stale text looks like a bug in the
     * app.
     */
    @Test
    fun `a dictionary from an old format says so above its definitions`() {
        val panel = panel(stale = listOf("JMdict · JMdict (inglês)"))

        val note = labels(panel).firstOrNull {
            it.text.contains("formato antigo") && it.text.contains("JMdict (inglês)")
        }
        assertNotNull("the panel kept quiet about the old format: ${labels(panel).map { it.text }}", note)
    }

    @Test
    fun `a current dictionary adds no warning`() {
        val panel = panel()

        assertTrue(
            "the panel warned about a dictionary that is current",
            labels(panel).none { it.text.contains("formato antigo") },
        )
    }

    @Test
    fun `a definition without marks carries no styling`() {
        val plain = ResultOverlay(context).apply {
            showResult(
                text = "猫",
                meta = "",
                notes = emptyList(),
                offlineAvailable = true,
                showLines = false,
                segmentLines = emptyList(),
                canRepick = false,
                canAskAi = true,
            )
            setWords(
                ResultOverlay.WordsState.Done(
                    matches = listOf(
                        TokenMatch(
                            surface = "猫",
                            start = 0,
                            end = 1,
                            entry = DictionaryEntry(
                                term = "猫",
                                reading = "ねこ",
                                tags = "",
                                rulesFlags = 0,
                                score = 1,
                                definition = "gato",
                            ),
                            dictionaryTitle = "JMdict",
                            reasons = emptyList(),
                        ),
                    ),
                    kanji = emptyList(),
                    dictionaryCount = 1,
                ),
            )
        }
        val view = labels(plain).firstOrNull { it.text.toString().trim() == "gato" }
        assertNotNull("the plain definition should still be there", view)
        val text = view!!.text as Spanned
        assertTrue(
            "nothing to style, so the definition keeps its text",
            text.toString() == "gato" && text.getSpans(0, text.length, RelativeSizeSpan::class.java).isEmpty(),
        )
    }
}
