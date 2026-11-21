package dev.yabai.ocr.desktop

import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    /**
     * The hover's rest time left 420 ms for 250 ms: 420 ms was a third of the whole latency the user
     * waits for, and the gate only has to know the pointer stopped (ROADMAP D82).
     */
    @Test
    fun `the hover rest time defaults to 250 ms and round-trips`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertEquals(250L, settings.hoverRestMillis)

        settings.hoverRestMillis = 150

        assertEquals(150L, DesktopSettings(file).hoverRestMillis)
        assertEquals("150", read(file).getProperty("hover_rest_millis"))
    }

    @Test
    fun `a hover rest time outside the allowed range is clamped`() {
        val file = tempSettings()

        write(file, "hover_rest_millis" to "0")
        assertEquals(HOVER_REST_MIN_MS, DesktopSettings(file).hoverRestMillis)

        write(file, "hover_rest_millis" to "99999")
        assertEquals(HOVER_REST_MAX_MS, DesktopSettings(file).hoverRestMillis)

        write(file, "hover_rest_millis" to "nao e numero")
        assertEquals(HOVER_REST_DEFAULT_MS, DesktopSettings(file).hoverRestMillis)
    }

    /** D103: the result window reopens where it was left, so the corner is part of the settings. */
    @Test
    fun `the result window corner round-trips and an absent one is null`() {
        val file = tempSettings()
        val settings = DesktopSettings(file)

        assertNull("a fresh install must not place the window anywhere", settings.resultWindowX)
        assertNull(settings.resultWindowY)

        settings.resultWindowX = 1536
        settings.resultWindowY = 240

        val reloaded = DesktopSettings(file)
        assertEquals(1536, reloaded.resultWindowX)
        assertEquals(240, reloaded.resultWindowY)
        assertEquals("1536", read(file).getProperty("result_window_x"))
        assertEquals("240", read(file).getProperty("result_window_y"))
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
