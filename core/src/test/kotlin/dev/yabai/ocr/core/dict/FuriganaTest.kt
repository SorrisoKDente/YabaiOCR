package dev.yabai.ocr.core.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading a dictionary knows, written after the kanji it belongs to.
 *
 * The words and the segmentation come from the dictionary engine, so they are built by hand here:
 * what is under test is where the reading goes, not whether a lookup finds the word (that is
 * `DictionaryCompileTest`). A wrong reading is worse than a missing one — it teaches the wrong
 * character — so most of these tests are about the annotation being dropped rather than guessed.
 */
class FuriganaTest {

    // --- where the reading of a word goes ---------------------------------------------------------

    @Test
    fun `a verb keeps its okurigana out of the reading`() {
        assertEquals(listOf(Furigana.Run("食", "た")), Furigana.split("食べる", "たべる"))
    }

    @Test
    fun `a word made of kanji reads whole`() {
        assertEquals(listOf(Furigana.Run("職員", "しょくいん")), Furigana.split("職員", "しょくいん"))
    }

    @Test
    fun `each kanji run of a mixed word takes its own part of the reading`() {
        assertEquals(
            listOf(Furigana.Run("申", "もう"), Furigana.Run("込", "こ")),
            Furigana.split("申し込み", "もうしこみ"),
        )
        assertEquals(
            listOf(Furigana.Run("有", "あ"), Furigana.Run("難", "がた")),
            Furigana.split("有り難い", "ありがたい"),
        )
        assertEquals(listOf(Furigana.Run("客様", "きゃくさま")), Furigana.split("お客様", "おきゃくさま"))
    }

    /** A word in kana only has no kanji to annotate, and that is not an error. */
    @Test
    fun `a word without kanji has nothing to read`() {
        assertEquals(emptyList<Furigana.Run>(), Furigana.split("さん", "さん"))
    }

    @Test
    fun `a reading that does not line up with the word is refused`() {
        assertNull("食 takes no しょく in 食べる", Furigana.split("食べる", "しょくいん"))
        assertNull("a reading that is not kana is not a reading", Furigana.split("職員", "職員"))
        assertNull("a word with no characters has no reading", Furigana.split("", "よみ"))
    }

    /** Katakana and hiragana spell the same reading, so they have to line up. */
    @Test
    fun `a katakana spelling lines up with a hiragana reading`() {
        assertEquals(listOf(Furigana.Run("食", "た")), Furigana.split("食ベル", "たべる"))
    }

    // --- the readings of a kanji standing on its own ----------------------------------------------

    @Test
    fun `a lone kanji reads kun first, without the okurigana`() {
        // Exactly how KANJIDIC writes them: a space between readings, a dot where the okurigana
        // begins. 食 reads た in 食べる, and that is what belongs beside the character.
        val entry = KanjiEntry("食", "corpo", onYomi = "ショク ジキ", kunYomi = "た.べる く.う")
        assertEquals(listOf("た", "く", "ショク"), Furigana.readingsOf(entry))
    }

    @Test
    fun `readings written with another separator are still read`() {
        val entry = KanjiEntry("灼", "queimar", onYomi = "シャク・サク", kunYomi = "やく、あらた")
        assertEquals(listOf("やく", "あらた", "シャク"), Furigana.readingsOf(entry))
    }

    @Test
    fun `a reading that only repeats is written once`() {
        val entry = KanjiEntry("淀", "lago", onYomi = "テン", kunYomi = "よど.む・よど")
        assertEquals(listOf("よど", "テン"), Furigana.readingsOf(entry))
    }

    @Test
    fun `what is not a reading is thrown away`() {
        val entry = KanjiEntry("燵", "b", onYomi = "・", kunYomi = "こたつ、not kana")
        assertEquals(listOf("こたつ"), Furigana.readingsOf(entry))
    }

    // --- a whole sentence -------------------------------------------------------------------------

    @Test
    fun `every kanji run gets its reading after it`() {
        val text = "職員さんに聞いた"
        val annotated = Furigana.sentence(
            text,
            listOf(
                match("職員", 0, 2, "職員", "しょくいん"),
                match("聞いた", 5, 8, "聞く", "きく"),
            ),
            kanji = { null },
        )

        assertEquals("職員（しょくいん）さんに聞（き）いた", annotated?.text)
        assertEquals("a word's reading is not a guess", 0, annotated?.uncertain)
    }

    @Test
    fun `a kanji nothing knows is left without a reading`() {
        val text = "職員室"
        val annotated = Furigana.sentence(
            text,
            listOf(match("職員室", 0, 3, "職員室", "しょくいんしつ")),
            kanji = { null },
        )

        assertEquals("職員室（しょくいんしつ）", annotated?.text)
    }

    @Test
    fun `a kanji standing alone falls back to the kanji data, marked as the guess it is`() {
        val text = "淀み"
        val annotated = Furigana.sentence(text, emptyList()) { character ->
            if (character == "淀") KanjiEntry("淀", "lago", onYomi = "テン", kunYomi = "よど.む") else null
        }

        assertEquals("淀（よど・テン?）み", annotated?.text)
        assertEquals(1, annotated?.uncertain)
    }

    @Test
    fun `two kanji in a row are never read as if they were a word`() {
        val text = "滉洋"
        val annotated = Furigana.sentence(text, emptyList()) { character ->
            KanjiEntry(character, "b", onYomi = "", kunYomi = "よみ")
        }

        assertNull("nothing was matched and no single kanji is on its own", annotated)
    }

    @Test
    fun `a word that covers part of a kanji run is written where it ends`() {
        val text = "職員室"
        val annotated = Furigana.sentence(
            text,
            listOf(match("職員", 0, 2, "職員", "しょくいん")),
            // 室 is in no word, and the run it belongs to is already accounted for, so not even the
            // kanji data may write for it: 職員室（しょくいん） would read as if 室 were silent.
            kanji = { character -> KanjiEntry(character, "b", kunYomi = "しつ") },
        )

        assertEquals("職員（しょくいん）室", annotated?.text)
        assertEquals(0, annotated?.uncertain)
    }

    @Test
    fun `a word whose dictionary spelling does not match the sentence is left alone`() {
        val text = "職員室"
        val annotated = Furigana.sentence(
            text,
            // A hit that only covers 職 reads しょく, but the reading is being pinned to しょくいん
            // over all three characters. The characters have to agree, and here they do not.
            listOf(match("職", 0, 1, "職員", "しょくいん")),
            kanji = { null },
        )

        assertNull(annotated)
    }

    /** A kanji a word already read must not be read a second time from the kanji data. */
    @Test
    fun `a kanji a word already read is not read again from the kanji data`() {
        val text = "職"
        val annotated = Furigana.sentence(
            text,
            listOf(match("職", 0, 1, "職", "しょく")),
            kanji = { character -> KanjiEntry(character, "b", kunYomi = "もと") },
        )

        assertEquals("職（しょく）", annotated?.text)
        assertEquals("the word's reading is not a guess", 0, annotated?.uncertain)
    }

    @Test
    fun `a kanji outside the basic plane is not written inside itself`() {
        // 𠮟 is two chars to the JVM. A position counted in chars would cut it in half.
        val text = "𠮟る"
        val annotated = Furigana.sentence(
            text,
            listOf(match("𠮟る", 0, 3, "𠮟る", "しかる")),
            kanji = { null },
        )

        assertEquals("𠮟（しか）る", annotated?.text)
    }

    @Test
    fun `a reading the sentence already prints is not written a second time`() {
        val text = "職員（しょくいん）さんに聞いた"
        val annotated = Furigana.sentence(
            text,
            listOf(
                match("職員", 0, 2, "職員", "しょくいん"),
                match("聞いた", 12, 15, "聞く", "きく"),
            ),
            kanji = { null },
        )

        assertEquals("職員（しょくいん）さんに聞（き）いた", annotated?.text)
    }

    @Test
    fun `a sentence with no kanji has nothing to annotate`() {
        assertNull(Furigana.sentence("さんにきいた", emptyList()) { null })
        assertNull("an empty capture is not a sentence", Furigana.sentence("", emptyList()) { null })
    }

    @Test
    fun `nothing known means no section at all`() {
        assertNull(Furigana.sentence("職員", emptyList()) { null })
        assertTrue("the sentence itself is not the annotation", Furigana.sentence("さん", emptyList()) { null } == null)
    }

    // --- helpers ----------------------------------------------------------------------------------

    private fun match(surface: String, start: Int, end: Int, term: String, reading: String) = TokenMatch(
        surface = surface,
        start = start,
        end = end,
        entry = DictionaryEntry(
            term = term,
            reading = reading,
            tags = "n",
            rulesFlags = 0,
            score = 1,
            definition = "1. definição",
        ),
        dictionaryTitle = "JMdict",
        reasons = emptyList(),
    )
}
