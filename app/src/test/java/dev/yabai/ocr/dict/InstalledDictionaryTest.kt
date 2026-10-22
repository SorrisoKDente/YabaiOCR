package dev.yabai.ocr.dict

import dev.yabai.ocr.core.dict.DictionaryInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Two dictionaries with the same title have to be told apart (R22).
 *
 * The title lives inside the archive — «JMdict», «Wikcionário» — and the variants of one dictionary
 * share it: English and Portuguese JMdict, three Wikcionários in three languages. On the screen they
 * looked identical, so the only thing that says which is which is what the user picked: the catalog
 * entry, or the file name they imported.
 */
class InstalledDictionaryTest {

    private fun item(title: String, source: String) = InstalledDictionary(
        id = "x",
        directory = File("."),
        info = DictionaryInfo(
            title = title,
            revision = "2025-01-01",
            format = 3,
            author = null,
            url = null,
            description = null,
            attribution = null,
            termCount = 1,
            kanjiCount = 0,
        ),
        sourceName = source,
        enabled = true,
        sizeBytes = 1,
    )

    @Test
    fun `the name carries the source when the title alone cannot tell two apart`() {
        assertEquals(
            "JMdict · JMdict (inglês)",
            item("JMdict", "JMdict (inglês)").displayName,
        )
    }

    @Test
    fun `a title that is already the source is not repeated`() {
        assertEquals("Jitendex", item("Jitendex", "Jitendex").displayName)
    }

    @Test
    fun `a dictionary with no source keeps its title`() {
        assertEquals("KANJIDIC", item("KANJIDIC", "  ").displayName)
    }
}
