package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the catalogue offers, and what the catalogue may touch on disk.
 *
 * The Portuguese and English Wiktionary entries were removed on request: their entries were shown
 * wrong in both languages and the dictionaries already offered cover more ground, while anyone who
 * wants them can import the file from another source. The monolingual Japanese one stays — it is the
 * one whose definitions are written in the language being learnt.
 */
class DictionaryCatalogEntriesTest {

    @Test
    fun `the pt and en wiktionary entries are not offered anymore`() {
        val ids = DesktopDictionaryCatalog.entries.map { it.id }

        assertFalse("the japanese → portuguese wiktionary is still offered", "wty-ja-pt" in ids)
        assertFalse("the japanese → english wiktionary is still offered", "wty-ja-en" in ids)
        assertTrue(
            "an entry still points at a localized wiktionary: $ids",
            ids.none { it.startsWith("wty-ja-pt") || it.startsWith("wty-ja-en") },
        )
    }

    @Test
    fun `the monolingual japanese wiktionary stays`() {
        val entry = DesktopDictionaryCatalog.entries.find { it.id == "wty-ja-ja" }

        assertEquals("the monolingual wiktionary was removed with the others", "ja", entry?.language)
        assertEquals(
            "the monolingual wiktionary must keep its descriptor",
            "https://github.com/yomidevs/wiktionary-to-yomitan/releases/latest/download/kty-ja-ja-index.json",
            entry?.indexUrl,
        )
    }

    @Test
    fun `an entry only owns the files its own button wrote`() {
        val dir = java.nio.file.Files.createTempDirectory("yabai-catalog").toFile()
        try {
            val installed = File(dir, "wty-ja-ja.yabai").apply { mkdirs() }
            val imported = File(dir, "meu-jmdict-pt.yabai").apply { mkdirs() }
            val entry = DesktopDictionaryCatalog.entries.first { it.id == "wty-ja-ja" }

            val owned = installedDictionaryFilesFor(entry, listOf(installed, imported))

            assertEquals(listOf("wty-ja-ja.yabai"), owned.map { it.name })
            assertFalse("a hand-imported dictionary was claimed by the catalogue", imported in owned)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a cancelled install stops at the next checkpoint`() {
        checkCancelled { false }

        var stopped = false
        try {
            checkCancelled { true }
        } catch (e: DictionaryDownloadCancelled) {
            stopped = true
        }
        assertTrue("the install kept going after the user pressed Cancel", stopped)
    }
}
