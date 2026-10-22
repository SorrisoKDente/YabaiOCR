package dev.yabai.ocr.dict

import dev.yabai.ocr.core.dict.DictionaryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The choice of dictionary behind the kanji card (R30).
 *
 * The card needs a dictionary with kanji data for the entry and for the reading of each kanji of a
 * compound; a word dictionary cannot answer either. What this holds down is that the stored id is
 * turned into the title the lookup engine filters by, and that a choice whose dictionary was
 * disabled, removed or never had kanji data falls back to «todos» instead of silencing the card.
 */
class KanjiDefaultTest {

    @Test
    fun `the chosen dictionary names the title the engine knows`() {
        val installed = listOf(
            item("kanjidic-en", "KANJIDIC [2026-290]", kanji = 13_000),
            item("kanjidic-pt", "KANJIDIC (Portuguese) [2026-290]", kanji = 2_000),
        )

        assertEquals(
            "KANJIDIC (Portuguese) [2026-290]",
            KanjiDefault.titleOf(installed, "kanjidic-pt"),
        )
    }

    @Test
    fun `no choice means every kanji dictionary may answer`() {
        val installed = listOf(item("kanjidic-en", "KANJIDIC", kanji = 13_000))

        assertNull(KanjiDefault.titleOf(installed, ""))
        assertNull(KanjiDefault.titleOf(installed, "   "))
    }

    @Test
    fun `a disabled dictionary is not a choice anymore`() {
        val installed = listOf(item("kanjidic-pt", "KANJIDIC (Portuguese)", kanji = 2_000, enabled = false))

        assertNull(
            "a disabled dictionary cannot answer, so the card has to fall back",
            KanjiDefault.titleOf(installed, "kanjidic-pt"),
        )
    }

    @Test
    fun `a word dictionary cannot be chosen for the kanji card`() {
        val installed = listOf(
            item("jmdict-en", "JMdict", kanji = 0),
            item("kanjidic-en", "KANJIDIC", kanji = 13_000),
        )

        assertNull(
            "JMdict has no kanji entries; choosing it would be the empty card again",
            KanjiDefault.titleOf(installed, "jmdict-en"),
        )
    }

    @Test
    fun `an id that is no longer installed falls back to everyone`() {
        val installed = listOf(item("kanjidic-en", "KANJIDIC", kanji = 13_000))

        assertNull(KanjiDefault.titleOf(installed, "kanjidic-pt"))
    }

    @Test
    fun `the chooser only offers enabled dictionaries with kanji data`() {
        val installed = listOf(
            item("jmdict-en", "JMdict", kanji = 0),
            item("kanjidic-en", "KANJIDIC", kanji = 13_000),
            item("kanjidic-pt", "KANJIDIC (Portuguese)", kanji = 2_000, enabled = false),
            item("kanjidic-rare", "KANJIDIC rare", kanji = 500),
        )

        assertEquals(
            listOf("kanjidic-en", "kanjidic-rare"),
            KanjiDefault.candidates(installed).map { it.id },
        )
    }

    private fun item(
        id: String,
        title: String,
        kanji: Int,
        enabled: Boolean = true,
    ): InstalledDictionary = InstalledDictionary(
        id = id,
        directory = File(id),
        info = DictionaryInfo(
            title = title,
            revision = "2026-01-01",
            format = 3,
            author = null,
            url = null,
            description = null,
            attribution = null,
            termCount = 0,
            kanjiCount = kanji,
            compiledWith = 3,
        ),
        sourceName = title,
        enabled = enabled,
        sizeBytes = 0,
    )
}
