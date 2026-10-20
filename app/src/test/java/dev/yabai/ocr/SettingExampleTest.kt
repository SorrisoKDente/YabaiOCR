package dev.yabai.ocr

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The example shown next to «Escrever no painel a leitura do dicionário».
 *
 * An example is the whole explanation of a setting on a phone: nobody reads «mostrar a leitura dos
 * kanji» and pictures a gutter of kana, they read the sentence. It showed `職員さんに聞（き）いた` —
 * only the last word annotated — which is the opposite of what the setting does, and the opposite of
 * what the setting above it («manter o furigana», `職員（しょくいん）`) shows. R5.
 *
 * The check is a rule, not a copy: every word written in kanji in the example must be followed by its
 * reading in parentheses, and the reading must be kana. It fails if the example is changed back, if a
 * reading is dropped, or if the sentence is swapped for one with no kanji (where «every word has a
 * reading» would otherwise be true of nothing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingExampleTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the readings example annotates every kanji word`() {
        val sentence = exampleOf(R.string.setting_furigana_readings)
        val words = kanjiWords(sentence)

        assertEquals(
            "the example no longer has the two kanji words that make the point: «$sentence»",
            listOf("職員", "聞"),
            words.map { it.first },
        )
        for ((word, reading) in words) {
            assertTrue(
                "«$word» is left without a reading in «$sentence»: the example is showing the " +
                    "opposite of what the setting does",
                reading.isNotEmpty(),
            )
            assertTrue(
                "«$word» is annotated with «$reading», which is not kana: «$sentence»",
                reading.all { isKana(it) },
            )
        }
    }

    /**
     * The two furigana settings describe one sentence: «manter o furigana» keeps what is printed
     * (`職員（しょくいん）`), «escrever a leitura do dicionário» writes the reading where the page had
     * none. The first is a word of the second, so the pair cannot drift into two different examples.
     */
    @Test
    fun `the two furigana examples are the same sentence`() {
        val kept = exampleOf(R.string.setting_furigana)
        val shown = exampleOf(R.string.setting_furigana_readings)

        assertTrue(
            "the examples have drifted apart: «$kept» is not part of «$shown»",
            shown.contains(kept),
        )
    }

    /**
     * The two settings look alike — both put `職員（しょくいん）` on screen — and were read as
     * duplicates. What tells them apart is where the reading comes from: the page for the printed
     * furigana, the dictionary for the panel's. Each label has to say which, and only one of them may
     * talk about the page.
     */
    @Test
    fun `each furigana setting says where its reading comes from`() {
        val printed = context.getString(R.string.setting_furigana)
        val fromDictionary = context.getString(R.string.setting_furigana_readings)

        assertTrue(
            "«$printed» does not say the reading is the one printed on the page",
            printed.contains("página"),
        )
        assertTrue(
            "«$fromDictionary» does not say the reading comes from the dictionary",
            fromDictionary.contains("dicionário"),
        )
        assertTrue(
            "«$fromDictionary» still talks about the page, so it reads as the other setting",
            !fromDictionary.contains("página"),
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    /** The Japanese part of a setting label: everything after the dash, which introduces it. */
    private fun exampleOf(id: Int): String {
        val label = context.getString(id)
        assertTrue("the setting has no example in it: «$label»", label.contains('—'))
        return label.substringAfter('—').trim()
    }

    /**
     * Every run of kanji in [sentence], paired with the reading that follows it in parentheses —
     * empty when the run is not annotated.
     */
    private fun kanjiWords(sentence: String): List<Pair<String, String>> {
        val words = ArrayList<Pair<String, String>>()
        var index = 0
        while (index < sentence.length) {
            if (!isKanji(sentence[index])) {
                index++
                continue
            }
            val start = index
            while (index < sentence.length && isKanji(sentence[index])) index++
            val rest = sentence.substring(index)
            val reading = if (rest.startsWith('（')) rest.substringAfter('（').substringBefore('）') else ""
            words.add(sentence.substring(start, index) to reading)
        }
        return words
    }

    private fun isKanji(char: Char): Boolean = char in '\u4E00'..'\u9FFF'

    /** Hiragana, katakana and the long-vowel mark: what a reading is written with. */
    private fun isKana(char: Char): Boolean =
        char in '\u3041'..'\u3096' || char in '\u30A1'..'\u30FA' || char == 'ー'
}
