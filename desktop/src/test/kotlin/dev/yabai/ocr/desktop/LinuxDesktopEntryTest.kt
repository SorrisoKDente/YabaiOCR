package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxDesktopEntryTest {

    @Test
    fun `exec arguments are always quoted and escaped`() {
        val raw = "C:\\Users\\Eric PC\\a\"b\$c`d"

        assertEquals(
            "\"C:\\\\Users\\\\Eric PC\\\\a\\\"b\\\$c\\`d\"",
            desktopEntryArgument(raw),
        )
    }

    @Test
    fun `autostart entry uses the real path and minimized flag`() {
        val entry = autostartDesktopEntry("/home/deck/Applications/Yabai OCR.AppImage")

        assertTrue(entry.contains("Exec=\"/home/deck/Applications/Yabai OCR.AppImage\" \"--minimized\""))
        assertTrue(entry.contains("Icon=yabai-ocr"))
        assertTrue(entry.startsWith("[Desktop Entry]"))
    }

    @Test
    fun `application entry does not force minimized startup`() {
        val entry = applicationDesktopEntry("/home/deck/Applications/Yabai OCR.AppImage")

        assertTrue(entry.contains("Exec=\"/home/deck/Applications/Yabai OCR.AppImage\""))
        assertTrue(!entry.contains("--minimized"))
    }

    @Test
    fun `both entries declare the window class gnome uses to find the icon`() {
        // The AWT window's WM_CLASS is the main class name (checked with xprop on a running app).
        // Without StartupWMClass GNOME cannot link the window to the entry and draws its gear.
        assertEquals("dev-yabai-ocr-desktop-MainKt", APP_WM_CLASS)
        assertTrue(applicationDesktopEntry("/x/Yabai.AppImage").contains("StartupWMClass=$APP_WM_CLASS"))
        assertTrue(autostartDesktopEntry("/x/Yabai.AppImage").contains("StartupWMClass=$APP_WM_CLASS"))
    }

    @Test
    fun `the app entry authorizes the silent kwin capture interface`() {
        val entry = applicationDesktopEntry("/home/deck/Applications/Yabai OCR.AppImage")

        assertTrue(
            entry.contains("X-KDE-DBUS-Restricted-Interfaces=org.kde.KWin.ScreenShot2"),
        )
    }

    @Test
    fun `the hidden authorization entry points at the running binary`() {
        val entry = screenshotAuthorizationDesktopEntry("/tmp/.mount_Yabai/yabai-ocr")

        assertTrue(entry.contains("Exec=\"/tmp/.mount_Yabai/yabai-ocr\""))
        assertTrue(entry.contains("NoDisplay=true"))
        assertTrue(entry.contains("X-KDE-DBUS-Restricted-Interfaces=org.kde.KWin.ScreenShot2"))
    }

    @Test
    fun `candidate order prefers the running appimage then the jpackage path`() {
        val candidates = linuxExecutableCandidates(
            env = mapOf("APPIMAGE" to "/home/deck/Applications/Yabai.AppImage"),
            userHome = "/home/deck",
            command = "/usr/lib/jvm/bin/java",
            jpackageAppPath = "/opt/yabai-ocr/bin/Yabai OCR",
        )

        assertEquals("/home/deck/Applications/Yabai.AppImage", candidates[0])
        assertEquals("/opt/yabai-ocr/bin/Yabai OCR", candidates[1])
        assertTrue(candidates.contains("/home/deck/.local/bin/yabai-ocr"))
    }

    @Test
    fun `java and javaw are not treated as the application executable`() {
        val candidates = linuxExecutableCandidates(
            env = emptyMap(),
            userHome = "/home/deck",
            command = "/usr/lib/jvm/bin/java",
            jpackageAppPath = null,
        )
        val candidatesWithJavaw = linuxExecutableCandidates(
            env = emptyMap(),
            userHome = "/home/deck",
            command = "/usr/lib/jvm/bin/javaw",
            jpackageAppPath = null,
        )

        assertTrue(candidates.none { it.endsWith("/java") })
        assertTrue(candidatesWithJavaw.none { it.endsWith("/javaw") })
    }

    @Test
    fun `resolver skips a missing appimage and picks the existing launcher`() {
        val home = Files.createTempDirectory("yabai-exec").toFile().apply { deleteOnExit() }
        val existing = File(home, "Yabai OCR").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }

        val resolved = resolveLinuxExecutable(
            env = mapOf("APPIMAGE" to File(home, "missing.AppImage").path),
            userHome = home.path,
            command = null,
            jpackageAppPath = existing.path,
        )

        assertEquals(existing.absolutePath, resolved)
    }

    @Test
    fun `duplicate candidates are removed but spaces are preserved`() {
        val candidates = linuxExecutableCandidates(
            env = mapOf("APPIMAGE" to "/home/deck/My Apps/Yabai.AppImage"),
            userHome = "/home/deck",
            command = null,
            jpackageAppPath = "/home/deck/My Apps/Yabai.AppImage",
        )

        assertEquals(1, candidates.count { it == "/home/deck/My Apps/Yabai.AppImage" })
    }
}
