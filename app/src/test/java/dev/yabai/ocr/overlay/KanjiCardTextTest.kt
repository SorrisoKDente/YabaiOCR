package dev.yabai.ocr.overlay

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.KanjiEntry
import dev.yabai.ocr.core.dict.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the kanji card says, chosen from the dictionary hits (R17).
 *
 * The reading of a character is not the reading of the dictionary entry: 腹 is はら in 腹が減っては,
 * and フク alone in a word list. The card has to pick the word that actually covers the finger, and
 * the words elsewhere on the page have to be the ones that are really there — not every headword
 * that happens to start with the character.
 */
class KanjiCardTextTest {

    private fun hit(surface: String, reading: String, start: Int, end: Int) = TokenMatch(
        surface = surface,
        start = start,
        end = end,
        entry = DictionaryEntry(
            term = surface,
            reading = reading,
            tags = "n",
            rulesFlags = 0,
            score = 1,
            definition = "…",
        ),
        dictionaryTitle = "JMdict",
        reasons = emptyList(),
    )

    @Test
    fun `the narrowest word covering the character is the one that reads it`() {
        val matches = listOf(
            hit("腹が減っては", "はらがへっては", 0, 6),
            hit("腹", "はら", 0, 1),
        )

        assertEquals(KanjiWord("腹", "はら"), KanjiCardText.wordAt(matches, 0))
    }

    @Test
    fun `a word that does not cover the character is not the answer`() {
        val matches = listOf(hit("腹", "はら", 0, 1))

        assertNull(KanjiCardText.wordAt(matches, 2))
    }

    @Test
    fun `a hit without a reading is skipped`() {
        val matches = listOf(
            hit("が", "", 1, 2),
            hit("腹", "はら", 0, 1),
        )

        assertEquals(KanjiWord("腹", "はら"), KanjiCardText.wordAt(matches, 0))
    }

    @Test
    fun `the words on the page are the compounds the card shows`() {
        val matches = listOf(
            hit("職員", "しょくいん", 0, 2),
            hit("職", "しょく", 0, 1),
            hit("員", "いん", 1, 2),
        )

        val compounds = KanjiCardText.compounds(matches, "職")

        assertEquals(listOf(KanjiWord("職員", "しょくいん"), KanjiWord("職", "しょく")), compounds)
    }

    @Test
    fun `the same word twice is said once`() {
        val matches = listOf(
            hit("職員", "しょくいん", 0, 2),
            hit("職員", "しょくいん", 10, 12),
        )

        assertEquals(1, KanjiCardText.compounds(matches, "職").size)
    }

    @Test
    fun `the list of compounds stops at the limit`() {
        val matches = (1..9).map { hit("職$it", "しょく$it", 0, 2) }

        assertEquals(4, KanjiCardText.compounds(matches, "職", limit = 4).size)
    }

    @Test
    fun `the dictionary readings are trimmed and missing ones come back empty`() {
        val entry = KanjiEntry("腹", "barriga", onYomi = " フク ", kunYomi = " はら ")

        assertEquals("フク", KanjiCardText.onYomi(entry))
        assertEquals("はら", KanjiCardText.kunYomi(entry))
        assertEquals("barriga", KanjiCardText.meaning(entry))
        assertEquals("", KanjiCardText.onYomi(null))
        assertEquals("", KanjiCardText.kunYomi(null))
        assertEquals("", KanjiCardText.meaning(null))
    }
}
