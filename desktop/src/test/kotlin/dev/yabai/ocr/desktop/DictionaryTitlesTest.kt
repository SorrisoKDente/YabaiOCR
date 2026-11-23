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

    /** The repository file, read from the project root or from the module directory. */
    private fun source(path: String): String {
        val found = sequenceOf(File(path), File("../$path"), File("desktop/$path"))
            .firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("$path not found", found.isNotEmpty())
        return found
    }

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

    /**
     * R47f: the hover card and every other chooser list the dictionaries in the priority configured
     * on the dictionaries screen. The folder's alphabetical order was what the hover switched
     * between, so the card disagreed with the screen that sets the order.
     */
    @Test
    fun `the choosers follow the configured priority`() {
        val root = root()
        try {
            val home = File(root, "home").apply { mkdirs() }
            val dir = File(home, "dicts").apply { mkdirs() }
            dictionary(dir, "a.yabai", """{"title":"Primeiro no alfabeto"}""")
            dictionary(dir, "b.yabai", """{"title":"Segundo no alfabeto"}""")
            dictionary(dir, "c.yabai", """{"title":"Desligado"}""")

            val settings = DesktopSettings(File(root, "settings.properties"), home)
            settings.customDictDirectoryPath = dir.absolutePath
            settings.setDictionaryOrder(listOf("b.yabai", "a.yabai", "c.yabai"))

            assertEquals(
                listOf("Segundo no alfabeto", "Primeiro no alfabeto", "Desligado"),
                DesktopDictLoader.installedTitles(settings),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    /** A chooser that reads the folder again would silently go back to alphabetical order. */
    @Test
    fun `every dictionary chooser asks for the priority order`() {
        val sources = mapOf(
            "HoverOcrManager.kt" to source("src/main/kotlin/dev/yabai/ocr/desktop/HoverOcrManager.kt"),
            "DictionaryManagerWindow.kt" to
                source("src/main/kotlin/dev/yabai/ocr/desktop/DictionaryManagerWindow.kt"),
            "ResultWindow.kt" to source("src/main/kotlin/dev/yabai/ocr/desktop/ResultWindow.kt"),
            "QuickDictWindow.kt" to source("src/main/kotlin/dev/yabai/ocr/desktop/QuickDictWindow.kt"),
            "SettingsWindow.kt" to source("src/main/kotlin/dev/yabai/ocr/desktop/SettingsWindow.kt"),
        )

        assertTrue(
            "the hover card still lists the folder, not the configured order",
            sources.getValue("HoverOcrManager.kt").contains("installedTitles(DesktopSettings())"),
        )
        for (name in listOf("DictionaryManagerWindow.kt", "ResultWindow.kt", "QuickDictWindow.kt")) {
            assertTrue(
                "$name does not ask for the priority order",
                sources.getValue(name).contains("installedTitles(settings)"),
            )
        }
        val settingsWindow = sources.getValue("SettingsWindow.kt")
        assertTrue(
            "the settings filters do not ask for the priority order",
            settingsWindow.contains("val dictionaryChoices = remember(customDictDir) { DesktopDictLoader.installedTitles(settings) }"),
        )
        assertEquals(
            "one of the two settings filters still lists the folder",
            2,
            Regex("dictionaryChoices\\.forEach").findAll(settingsWindow).count(),
        )
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
