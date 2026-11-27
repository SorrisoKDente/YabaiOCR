package dev.yabai.ocr.core.anki

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card keeps the text the user saw, not the dictionary's spelling: the ruby goes over the
 * surface, and a reading that does not line up is left out instead of being pinned to a character
 * that is not in the sentence.
 */
class AnkiNoteTest {

    private fun match(
        surface: String,
        start: Int = 0,
        term: String = surface,
        reading: String = "たべる",
        definition: String = "comer",
        tags: String = "v1",
        dictionary: String = "Jitendex",
    ) = TokenMatch(
        surface = surface,
        start = start,
        end = start + surface.length,
        entry = DictionaryEntry(
            term = term,
            reading = reading,
            tags = tags,
            rulesFlags = 0,
            score = 1,
            definition = definition,
        ),
        dictionaryTitle = dictionary,
        reasons = emptyList(),
    )

    @Test
    fun `the word field rubies the kanji of the surface, not of the dictionary form`() {
        val note = AnkiNote.word(
            match("食べました", start = 2, term = "食べる"),
            "私は食べました。",
            "27/11/2026",
        )

        assertEquals(
            "<ruby>食<rt>た</rt></ruby>べました",
            note.field(AnkiFields.WORD),
        )
        assertEquals("たべる", note.field(AnkiFields.READING))
        assertTrue(
            "the sentence does not bold the word as it was read",
            note.field(AnkiFields.SENTENCE).contains("<b>食べました</b>"),
        )
        assertEquals("Jitendex · 27/11/2026", note.field(AnkiFields.SOURCE))
    }

    @Test
    fun `a definition with markup characters is escaped`() {
        val note = AnkiNote.word(
            match("食べる", definition = "comer <verbo> & « comer»"),
            "食べる",
            "27/11/2026",
        )

        assertEquals(
            "comer &lt;verbo&gt; &amp; « comer»<br><i>v1</i>",
            note.field(AnkiFields.MEANING),
        )
    }

    @Test
    fun `a word with no reading is written without ruby`() {
        val note = AnkiNote.word(match("食べる", reading = ""), "食べる", "27/11/2026")

        assertEquals("食べる", note.field(AnkiFields.WORD))
        assertEquals("", note.field(AnkiFields.READING))
    }

    @Test
    fun `an old form whose kanji are not in the surface gets no ruby`() {
        // The entry is 両膝; the sentence printed 兩膝 (R46e). Pinning りょうひざ to 兩 would teach
        // a reading for a character that is not the one in the entry.
        val note = AnkiNote.word(
            match("兩膝", term = "両膝", reading = "りょうひざ"),
            "兩膝が痛い。",
            "27/11/2026",
        )

        assertEquals("兩膝", note.field(AnkiFields.WORD))
    }

    @Test
    fun `the sentence card keeps the context and bolds the first matched word`() {
        val text = "私は食べました。"
        val note = AnkiNote.sentence(
            text,
            listOf(match("食べました", start = 2, term = "食べる")),
            "27/11/2026",
        )

        assertEquals("<ruby>食<rt>た</rt></ruby>べました", note.field(AnkiFields.WORD))
        assertEquals(
            "私は<b>食べました</b>。",
            note.field(AnkiFields.SENTENCE),
        )
        assertEquals("comer", note.field(AnkiFields.MEANING))
        assertEquals("Jitendex · 27/11/2026", note.field(AnkiFields.SOURCE))
    }

    @Test
    fun `the plain text twin drops the markup and the empty fields`() {
        val note = AnkiNote.word(match("食べました", term = "食べる"), "私は食べました。", "27/11/2026")
        val plain = AnkiHtml.plain(note)

        assertTrue(plain.contains("食べました"))
        assertTrue("the ruby markup survived the plain text", !plain.contains("<ruby>"))
        assertTrue("the bold markup survived the plain text", !plain.contains("<b>"))
        assertTrue(plain.contains("私は食べました。"))
        assertTrue("an empty field was printed", !plain.contains("\n\n"))
    }
}
