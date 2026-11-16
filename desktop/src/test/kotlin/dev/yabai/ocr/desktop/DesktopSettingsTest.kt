package dev.yabai.ocr.desktop

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSettingsTest {

    private fun tempSettings(): File =
        File.createTempFile("yabai-desktop-settings", ".properties").apply { deleteOnExit() }

    private fun write(file: File, vararg pairs: Pair<String, String>) {
        val properties = Properties()
        pairs.forEach { (key, value) -> properties.setProperty(key, value) }
        file.outputStream().use { properties.store(it, "test") }
    }

    private fun read(file: File): Properties =
        Properties().apply { file.inputStream().use { load(it) } }

    @Test
    fun `checking for updates on start is on by default and round-trips`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertTrue(settings.checkUpdatesOnStart)

        settings.checkUpdatesOnStart = false

        assertFalse(DesktopSettings(file).checkUpdatesOnStart)
        assertEquals("false", read(file).getProperty("check_updates_on_start"))
    }

    @Test
    fun `the last dictionary import folder is remembered`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertEquals("a fresh install must not open a folder", "", settings.lastDictionaryImportDirectory)

        settings.lastDictionaryImportDirectory = "C:\\Users\\Eric PC\\Documentos\\Dicionarios"

        assertEquals(
            "C:\\Users\\Eric PC\\Documentos\\Dicionarios",
            DesktopSettings(file).lastDictionaryImportDirectory,
        )
        assertEquals(
            "C:\\Users\\Eric PC\\Documentos\\Dicionarios",
            read(file).getProperty("last_dict_import_dir"),
        )
    }

    @Test
    fun `the capture monitor defaults to the primary screen and round-trips`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertEquals("PRIMARY", settings.captureMonitor)

        settings.captureMonitor = "MOUSE"

        assertEquals("MOUSE", DesktopSettings(file).captureMonitor)
        assertEquals("MOUSE", read(file).getProperty("capture_monitor"))
    }

    @Test
    fun `a blank capture monitor in the file reads as the primary screen`() {
        val file = tempSettings()
        write(file, "capture_monitor" to "   ")

        assertEquals("PRIMARY", DesktopSettings(file).captureMonitor)
    }

    @Test
    fun `reads the legacy dictionary directory key`() {
        val file = tempSettings()
        write(file, "custom_dict_directory" to "E:\\Documentos\\DicionariosJapones")

        val settings = DesktopSettings(file)

        assertEquals("E:\\Documentos\\DicionariosJapones", settings.customDictDirectoryPath)
        assertEquals("E:\\Documentos\\DicionariosJapones", settings.dictDirectory().path)
    }

    @Test
    fun `writes the canonical key and removes the legacy one`() {
        val file = tempSettings()
        write(
            file,
            "custom_dict_directory" to "E:\\old",
            "custom_dict_dir" to "E:\\old",
        )

        DesktopSettings(file).customDictDirectoryPath = "D:\\Dicionarios"

        val saved = read(file)
        assertEquals("D:\\Dicionarios", saved.getProperty("custom_dict_dir"))
        assertFalse(saved.containsKey("custom_dict_directory"))

        val reloaded = DesktopSettings(file)
        assertEquals("D:\\Dicionarios", reloaded.customDictDirectoryPath)
    }

    @Test
    fun `falls back to the default dictionary folder when no key exists`() {
        val file = tempSettings()
        write(file, "engine" to "LENS")

        val settings = DesktopSettings(file)

        assertTrue(settings.customDictDirectoryPath.endsWith(".yabai_ocr${File.separator}dicts"))
    }

    @Test
    fun `full-screen capture and region crop have distinct shortcuts by default`() {
        val settings = DesktopSettings(tempSettings())

        assertEquals("Ctrl + Shift + O", settings.globalHotkey)
        assertEquals("Ctrl + Shift + A", settings.regionHotkey)
        assertNotEquals(settings.globalHotkey, settings.regionHotkey)
    }

    @Test
    fun `the region shortcut is read from its own key`() {
        val file = tempSettings()
        write(file, "global_hotkey" to "Insert", "region_hotkey" to "Ctrl + Alt + R")

        val settings = DesktopSettings(file)

        assertEquals("Insert", settings.globalHotkey)
        assertEquals("Ctrl + Alt + R", settings.regionHotkey)
    }

    @Test
    fun `darkTheme defaults to true and persists changes`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertTrue(settings.darkTheme)

        settings.darkTheme = false
        assertFalse(DesktopSettings(file).darkTheme)

        settings.darkTheme = true
        assertTrue(DesktopSettings(file).darkTheme)
    }

    @Test
    fun `closeToTray defaults to true and persists changes`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertTrue(settings.closeToTray)

        settings.closeToTray = false
        assertFalse(DesktopSettings(file).closeToTray)

        settings.closeToTray = true
        assertTrue(DesktopSettings(file).closeToTray)
    }

    /**
     * The search starts on words and remembers a switch to meanings (R35f).
     *
     * The mode is a preference because the first meaning query builds a sidecar index; it must not
     * silently come back off, or the user would pay — and wait — for the build again on every visit.
     */
    @Test
    fun `searchByTranslation defaults to false and persists the switch`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertFalse(settings.searchByTranslation)

        settings.searchByTranslation = true
        assertTrue(DesktopSettings(file).searchByTranslation)

        settings.searchByTranslation = false
        assertFalse(DesktopSettings(file).searchByTranslation)
    }
}
