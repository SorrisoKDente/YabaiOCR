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
    fun `a stale entry is refreshed with the current AppImage and window class`() {
        // The dock kept the gear because the installed entry named an older AppImage and predated
        // StartupWMClass; the running app only rewrote the icon.
        val dataHome = tempDir()
        val wmClass = wmClassForMainClass("dev.yabai.ocr.desktop.MainKt")!!
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { "/home/deck/Downloads/Yabai_OCR-1.0.9-revisao3.AppImage" },
            wmClass = { wmClass },
            iconBytes = { byteArrayOf(0x89.toByte(), 0x50) },
        )
        integration.desktopFile.parentFile?.mkdirs()
        integration.desktopFile.writeText(
            applicationDesktopEntry("/home/deck/Downloads/Old.AppImage", "old-wm-class"),
        )

        assertTrue(integration.ensureTrayIcon())

        val entry = integration.desktopFile.readText()
        assertTrue(entry.contains("Exec=\"/home/deck/Downloads/Yabai_OCR-1.0.9-revisao3.AppImage\""))
        assertTrue(entry.contains("StartupWMClass=$wmClass"))
        assertFalse(entry.contains("old-wm-class"))
        assertFalse(entry.contains("Old.AppImage"))
    }

    @Test
    fun `an absent entry is not created by the tray icon path`() {
        val dataHome = tempDir()
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { "/home/deck/Applications/Yabai OCR.AppImage" },
            iconBytes = { byteArrayOf(0x89.toByte(), 0x50) },
        )

        assertTrue(integration.ensureTrayIcon())

        assertTrue(integration.iconFile.isFile)
        assertFalse("creating the menu entry is 'Integrar ao sistema'\'s job", integration.desktopFile.exists())
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

    @Test
    fun `screenshot authorization writes only the hidden entry for the running executable`() {
        val dataHome = tempDir()
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { "/home/deck/Applications/Yabai.AppImage" },
            runningExecutable = { "/tmp/.mount_Yabai/yabai-ocr" },
            iconBytes = { byteArrayOf(1) },
        )

        assertTrue(integration.ensureScreenshotAuthorization())

        val entry = integration.authorizationDesktopFile.readText()
        assertTrue(entry.contains("Exec=\"/tmp/.mount_Yabai/yabai-ocr\""))
        assertTrue(entry.contains("X-KDE-DBUS-Restricted-Interfaces=org.kde.KWin.ScreenShot2"))
        // The menu entry is a different concern and must not be created by this call.
        assertFalse(integration.desktopFile.exists())
    }

    @Test
    fun `the tray icon is written on its own, without installing the menu entry`() {
        // The StatusNotifier item advertises `IconName=yabai-ocr`; without the file in the user's
        // icon theme GNOME drew its generic gear instead of the app icon.
        val dataHome = tempDir()
        val icon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { null },
            iconBytes = { icon },
        )

        assertTrue(integration.ensureTrayIcon())

        assertTrue(integration.iconFile.isFile)
        assertTrue(icon.contentEquals(integration.iconFile.readBytes()))
        assertFalse("the menu entry is a separate choice", integration.desktopFile.exists())
    }

    @Test
    fun `screenshot authorization without a running binary writes nothing`() {
        val dataHome = tempDir()
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { null },
            runningExecutable = { null },
            iconBytes = { null },
        )

        assertFalse(integration.ensureScreenshotAuthorization())
        assertFalse(integration.authorizationDesktopFile.exists())
    }
}
