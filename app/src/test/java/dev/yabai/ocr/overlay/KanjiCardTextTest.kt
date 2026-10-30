package dev.yabai.ocr.overlay

import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.Furigana
import dev.yabai.ocr.core.dict.KanjiEntry
import dev.yabai.ocr.core.dict.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun hit(
        surface: String,
        reading: String,
        start: Int,
        end: Int,
        definition: String = "…",
    ) = TokenMatch(
        surface = surface,
        start = start,
        end = end,
        entry = DictionaryEntry(
            term = surface,
            reading = reading,
            tags = "n",
            rulesFlags = 0,
            score = 1,
            definition = definition,
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

    /**
     * R34a: the card answered «技 is skill; art; craft» for 剣技, and never said what 剣技 means.
     *
     * The meaning has to be the one of the word the card names — the same narrowest hit that gives
     * the reading — and it has to be trimmed, because the compiled definition carries the dictionary
     * renderer's padding.
     */
    @Test
    fun `the meaning is the one of the word that reads the character`() {
        val matches = listOf(
            hit("剣技", "けんぎ", 0, 2, definition = " 1. fencing; swordsmanship "),
            hit("技", "わざ", 1, 2, definition = "1. technique"),
        )

        assertEquals("1. technique", KanjiCardText.wordMeaning(matches, 1))
        assertEquals("1. fencing; swordsmanship", KanjiCardText.wordMeaning(matches, 0))
    }

    @Test
    fun `a character no word covers has no word meaning`() {
        val matches = listOf(hit("剣技", "けんぎ", 0, 2, definition = "1. fencing"))

        assertEquals("", KanjiCardText.wordMeaning(matches, 5))
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

    /**
     * «Nenhum dicionário respondeu» was the same sentence for three different problems (R28).
     *
     * Only one of them is the user's fault — and it is not the one the card used to blame. Saying
     * «instale o KANJIDIC» when it is installed but switched off, or when a word dictionary was
     * selected by mistake, sends the user to the wrong place.
     */
    @Test
    fun `an empty kanji section says which of the three reasons it was`() {
        assertEquals(
            "nothing installed: the card has to point at the download",
            R.string.kanji_dictionary_missing,
            KanjiCardText.missingNote(installed = false, enabled = false),
        )
        assertEquals(
            "installed but switched off: the card has to point at the list",
            R.string.kanji_dictionary_disabled,
            KanjiCardText.missingNote(installed = true, enabled = false),
        )
        assertEquals(
            "installed, enabled, and the character is simply not there",
            R.string.kanji_no_dictionary,
            KanjiCardText.missingNote(installed = true, enabled = true),
        )
    }

    /** R29: the mark covers the kanji of the word the card talks about, and not the word's kana. */
    @Test
    fun `the mark of a compound is its kanji, without the kana`() {
        val text = "同時に"
        val matches = listOf(hit("同時に", "どうじに", 0, 3))

        assertEquals(0..1, KanjiCardText.kanjiSpan(text, matches, 0))
        assertEquals("the second kanji marks the same pair", 0..1, KanjiCardText.kanjiSpan(text, matches, 1))
        assertNull("に is kana: it takes no reading", KanjiCardText.kanjiSpan(text, matches, 2))
    }

    /** A character its own word reads keeps the single cell: there is no compound to outline. */
    @Test
    fun `a character read by its own word keeps the single cell`() {
        val matches = listOf(
            hit("腹が減っては", "はらがへっては", 0, 6),
            hit("腹", "はら", 0, 1),
        )

        assertNull(KanjiCardText.kanjiSpan("腹が減っては", matches, 0))
    }

    /** R29: the division is a line only when every part is one kanji with a reading of its own. */
    @Test
    fun `the division is shown only when every part is one kanji`() {
        assertEquals(
            listOf(KanjiPart("同", "どう"), KanjiPart("時", "じ")),
            KanjiCardText.parts(listOf(Furigana.Run("同", "どう"), Furigana.Run("時", "じ"))),
        )
        assertTrue(
            "a part of two kanji says the word again",
            KanjiCardText.parts(listOf(Furigana.Run("同時", "どうじ"))).isEmpty(),
        )
        assertTrue(
            "one kanji has nothing to tell apart",
            KanjiCardText.parts(listOf(Furigana.Run("腹", "はら"))).isEmpty(),
        )
        assertTrue(KanjiCardText.parts(null).isEmpty())
    }
}
