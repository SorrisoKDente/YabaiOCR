package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxSystemIntegrationTest {

    private fun tempDir(): File =
        Files.createTempDirectory("yabai-integration").toFile().apply { deleteOnExit() }

    @Test
    fun `installing writes the application entry and the png icon under the data home`() {
        val dataHome = tempDir()
        val icon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { "/home/deck/Applications/Yabai OCR.AppImage" },
            iconBytes = { icon },
        )

        assertTrue(integration.install())

        assertTrue(integration.desktopFile.isFile)
        assertTrue(integration.desktopFile.readText().contains(
            "Exec=\"/home/deck/Applications/Yabai OCR.AppImage\"",
        ))
        assertFalse(integration.desktopFile.readText().contains("--minimized"))
        assertTrue(integration.iconFile.isFile)
        assertTrue(icon.contentEquals(integration.iconFile.readBytes()))

        assertEquals(
            File(dataHome, "applications/yabai-ocr.desktop").invariantSeparatorsPath,
            integration.desktopFile.invariantSeparatorsPath,
        )
        assertEquals(
            File(dataHome, "icons/hicolor/256x256/apps/yabai-ocr.png").invariantSeparatorsPath,
            integration.iconFile.invariantSeparatorsPath,
        )
    }

    @Test
    fun `no executable means no broken entry`() {
        val dataHome = tempDir()
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { null },
            iconBytes = { byteArrayOf(1) },
        )

        assertFalse(integration.install())
        assertFalse(integration.desktopFile.exists())
        assertFalse(integration.iconFile.exists())
    }

    @Test
    fun `a missing icon cancels the whole integration`() {
        val dataHome = tempDir()
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { "/home/deck/Applications/Yabai.AppImage" },
            iconBytes = { null },
        )

        assertFalse(integration.install())
        assertFalse(integration.desktopFile.exists())
    }

    @Test
    fun `blank executable is refused`() {
        val dataHome = tempDir()
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { "   " },
            iconBytes = { byteArrayOf(1) },
        )

        assertFalse(integration.install())
    }

    @Test
    fun `xdg data home overrides the default location`() {
        assertEquals(
            File("/tmp/yabai-data"),
            defaultDataHome(mapOf("XDG_DATA_HOME" to "/tmp/yabai-data"), "/home/deck"),
        )
        assertEquals(
            File("/home/deck/.local/share"),
            defaultDataHome(emptyMap(), "/home/deck"),
        )
        assertEquals(
            File("/home/deck/.local/share"),
            defaultDataHome(mapOf("XDG_DATA_HOME" to "  "), "/home/deck"),
        )
    }
}
