package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxAutostartTest {

    private fun tempDir(): File =
        Files.createTempDirectory("yabai-autostart").toFile().apply { deleteOnExit() }

    @Test
    fun `enabling writes an xdg entry pointing at the appimage`() {
        val configHome = tempDir()
        val autostart = LinuxAutostart(configHome) { "/home/deck/Applications/Yabai OCR.AppImage" }

        autostart.setEnabled(true)

        assertTrue(autostart.entryFile.isFile)
        val text = autostart.entryFile.readText()
        assertTrue(text.contains("Exec=\"/home/deck/Applications/Yabai OCR.AppImage\" \"--minimized\""))
        assertTrue(text.contains("Icon=yabai-ocr"))
        assertTrue(autostart.isEnabled())
    }

    @Test
    fun `disabling deletes the entry instead of hiding it`() {
        val configHome = tempDir()
        val autostart = LinuxAutostart(configHome) { "/home/deck/Applications/Yabai.AppImage" }
        autostart.setEnabled(true)

        autostart.setEnabled(false)

        assertFalse(autostart.entryFile.exists())
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `a missing executable does not leave a broken entry`() {
        val configHome = tempDir()
        val autostart = LinuxAutostart(configHome) { null }

        autostart.setEnabled(true)

        assertFalse(autostart.entryFile.exists())
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `xdg config home overrides the default location`() {
        assertEquals(
            File("/tmp/yabai-config"),
            defaultConfigHome(mapOf("XDG_CONFIG_HOME" to "/tmp/yabai-config"), "/home/deck"),
        )
        assertEquals(
            File("/home/deck/.config"),
            defaultConfigHome(emptyMap(), "/home/deck"),
        )
        assertEquals(
            File("/home/deck/.config"),
            defaultConfigHome(mapOf("XDG_CONFIG_HOME" to "  "), "/home/deck"),
        )
    }

    @Test
    fun `yabai home overrides the data directory`() {
        assertEquals(
            File("/tmp/yabai-home"),
            defaultDesktopHome(mapOf("YABAI_HOME" to "/tmp/yabai-home"), "/home/deck"),
        )
        assertEquals(
            File("/home/deck/.yabai_ocr"),
            defaultDesktopHome(emptyMap(), "/home/deck"),
        )
    }
}
