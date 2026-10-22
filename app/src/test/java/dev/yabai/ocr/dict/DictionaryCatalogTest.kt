package dev.yabai.ocr.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * The list of dictionaries the app offers itself (R30).
 *
 * A catalogue entry that cannot be downloaded leaves the user with a button that fails, and one with
 * an unstable URL stops working as soon as the publisher cuts a new release. What this holds down is
 * that every entry points somewhere, and that the Portuguese Jitendex is a **terms** dictionary whose
 * descriptor lives at ggjz.org's stable address — the archive itself is behind the descriptor.
 */
class DictionaryCatalogTest {

    @Test
    fun `every entry has an id, a language and somewhere to download from`() {
        val ids = HashSet<String>()
        for (entry in DictionaryCatalog.entries) {
            assertTrue("an entry has no id", entry.id.isNotBlank())
            assertTrue("duplicate id ${entry.id}", ids.add(entry.id))
            assertTrue("${entry.id} has no name", entry.name.isNotBlank())
            assertTrue("${entry.id} has no description", entry.description.isNotBlank())
            assertTrue("${entry.id} has no attribution", entry.attribution.isNotBlank())
            assertTrue("${entry.id} has an unknown language ${entry.language}", entry.language in setOf("pt", "en", "ja"))
            assertTrue(
                "${entry.id} must offer one of url/indexUrl",
                (entry.url != null) != (entry.indexUrl != null),
            )
            val address = URI(entry.url ?: entry.indexUrl)
            assertEquals("${entry.id} is not https", "https", address.scheme)
            assertTrue("${entry.id} has no host", !address.host.isNullOrBlank())
        }
    }

    @Test
    fun `the portuguese jitendex is offered as a terms dictionary`() {
        val entry = DictionaryCatalog.find("ggjz-pt")

        assertTrue("the ggjz.org dictionary is not in the catalogue", entry != null)
        entry!!
        assertEquals(DictionaryCatalog.Kind.TERMS, entry.kind)
        assertEquals("pt", entry.language)
        assertTrue("the name should say it is Portuguese: ${entry.name}", entry.name.contains("português"))
        assertTrue(
            "the descriptor is the part that must stay stable: ${entry.indexUrl}",
            entry.indexUrl == "https://ggjz.org/yomitan-ptbr.json",
        )
        assertTrue(
            "the attribution should name both the original and the translation: ${entry.attribution}",
            entry.attribution.contains("Jitendex") && entry.attribution.contains("ggjz.org"),
        )
    }

    @Test
    fun `kanji dictionaries are marked as such, so the card can choose between them`() {
        val kanji = DictionaryCatalog.entries.filter { it.kind == DictionaryCatalog.Kind.KANJI }

        assertTrue("the catalogue offers no kanji dictionary at all", kanji.isNotEmpty())
        assertEquals(
            setOf("kanjidic-en", "kanjidic-pt"),
            kanji.map { it.id }.toSet(),
        )
        assertTrue(
            "a word dictionary must not be offered as a kanji source",
            DictionaryCatalog.entries.filter { it.kind == DictionaryCatalog.Kind.TERMS }
                .none { it.id.startsWith("kanjidic") },
        )
    }
}
