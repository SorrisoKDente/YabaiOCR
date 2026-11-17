package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.DictFiles
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list of dictionary names the desktop screens show (hover card, result panel, quick search,
 * settings and the manager).
 *
 * The regression these tests hold (ROADMAP D81): the names used to be collected with
 * `CompactDictionary.open`, which reads the whole compiled key index — 69 MB across the five
 * dictionaries of a real Steam Deck install — and the hover card asks for this list on every
 * composition. The card appeared a tenth of a second after the answer it already had, on the UI
 * thread. A name lives in `meta.json`; the index is for looking words up.
 */
class DictionaryTitlesTest {

    private fun root(): File = Files.createTempDirectory("yabai-titles").toFile()

    private fun dictionary(dir: File, name: String, meta: String): File {
        val entry = File(dir, name)
        entry.mkdirs()
        File(entry, DictFiles.META).writeText(meta, Charsets.UTF_8)
        return entry
    }

    @Test
    fun `listing titles reads the metadata and not the compiled index`() {
        val root = root()
        try {
            // A real install writes meta.json and the terms.*/kanji.* parts; only the former is
            // needed to name it. No index here: opening it would fail, naming it must not.
            dictionary(root, "jitendex.yabai", """{"title":"Jitendex PT-BR","termCount":10}""")

            assertEquals(listOf("Jitendex PT-BR"), DesktopDictLoader.installedTitles(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `every installed dictionary is named, in folder order`() {
        val root = root()
        try {
            dictionary(root, "a.yabai", """{"title":"JMdict [2026-11-10]"}""")
            dictionary(root, "b.yabai", """{"title":"JMnedict [2026-11-15]"}""")

            assertEquals(
                listOf("JMdict [2026-11-10]", "JMnedict [2026-11-15]"),
                DesktopDictLoader.installedTitles(root).sorted(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a folder that is not a dictionary has no title`() {
        val root = root()
        try {
            File(root, "alguma-pasta").mkdirs()
            dictionary(root, "real.yabai", """{"title":"Jitendex.org"}""")

            assertEquals(listOf("Jitendex.org"), DesktopDictLoader.installedTitles(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a dictionary compiled without a title is skipped`() {
        val root = root()
        try {
            dictionary(root, "sem-titulo.yabai", """{"title":"","termCount":3}""")
            dictionary(root, "sem-meta.yabai", """{"termCount":3}""")

            assertTrue(DesktopDictLoader.installedTitles(root).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a missing dictionary folder lists nothing`() {
        val missing = File(root(), "nao-existe")

        assertTrue(DesktopDictLoader.installedTitles(missing).isEmpty())
    }

    @Test
    fun `a broken metadata file does not take the other names with it`() {
        val root = root()
        try {
            dictionary(root, "quebrado.yabai", "{isto nao é json")
            dictionary(root, "bom.yabai", """{"title":"surasura 擬声語"}""")

            assertEquals(listOf("surasura 擬声語"), DesktopDictLoader.installedTitles(root))
        } finally {
            root.deleteRecursively()
        }
    }
}
