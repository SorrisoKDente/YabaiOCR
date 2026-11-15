package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private val wmClass = wmClassForMainClass("dev.yabai.ocr.desktop.MainKt")!!

    @Test
    fun `autostart entry uses the real path and minimized flag`() {
        val entry = autostartDesktopEntry("/home/deck/Applications/Yabai OCR.AppImage", wmClass)

        assertTrue(entry.contains("Exec=\"/home/deck/Applications/Yabai OCR.AppImage\" \"--minimized\""))
        assertTrue(entry.contains("Icon=yabai-ocr"))
        assertTrue(entry.startsWith("[Desktop Entry]"))
    }

    @Test
    fun `application entry does not force minimized startup`() {
        val entry = applicationDesktopEntry("/home/deck/Applications/Yabai OCR.AppImage", wmClass)

        assertTrue(entry.contains("Exec=\"/home/deck/Applications/Yabai OCR.AppImage\""))
        assertTrue(!entry.contains("--minimized"))
    }

    @Test
    fun `the window class is derived from the main class`() {
        // `xprop` on the running app shows WM_CLASS dev-yabai-ocr-desktop-MainKt: AWT derives it from
        // the main class with dots replaced by dashes. Keeping the derivation here means renaming the
        // entry point cannot leave a stale literal behind.
        assertEquals(
            "dev-yabai-ocr-desktop-MainKt",
            wmClassForMainClass("dev.yabai.ocr.desktop.MainKt --minimized"),
        )
        assertEquals("dev-yabai-ocr-desktop-MainKt", wmClassForMainClass("  dev.yabai.ocr.desktop.MainKt  "))
        // A different entry point must produce a different class: a hardcoded constant would pass the
        // first assertion and break the moment the main class is renamed.
        assertEquals("com-example-Other", wmClassForMainClass("com.example.Other --minimized"))
        assertNull("a jar is not a class name", wmClassForMainClass("app.jar"))
        assertNull(wmClassForMainClass(""))
        assertNull(wmClassForMainClass(null))
    }

    @Test
    fun `both entries declare the window class gnome uses to find the icon`() {
        assertTrue(applicationDesktopEntry("/x/Yabai.AppImage", wmClass).contains("StartupWMClass=$wmClass"))
        assertTrue(autostartDesktopEntry("/x/Yabai.AppImage", wmClass).contains("StartupWMClass=$wmClass"))
        // Unknown class: the line is left out instead of writing a wrong one.
        assertFalse(applicationDesktopEntry("/x/Yabai.AppImage", null).contains("StartupWMClass"))
    }

    @Test
    fun `the app entry authorizes the silent kwin capture interface`() {
        val entry = applicationDesktopEntry("/home/deck/Applications/Yabai OCR.AppImage", wmClass)

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
