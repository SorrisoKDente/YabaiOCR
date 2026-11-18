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
    fun `a path inside the appimage mount is never written`() {
        // `/tmp/.mount_*` disappears when the app closes: an entry written with it opens nothing
        // until the next login brings the app back, which is what made reopening look like it
        // needed a reboot. The .AppImage file is the only usable target.
        val appDir = Files.createTempDirectory("yabai-mount").toFile().apply { deleteOnExit() }
        // Do not scan the real ~/Applications: a real AppImage (e.g. Cemu) would be a legitimate
        // fallback and this test is about the mount path, not about what else is installed.
        val userHome = Files.createTempDirectory("yabai-home").toFile().apply { deleteOnExit() }
        val launching = File(appDir, "opt/yabai-ocr/bin/yabai-ocr").apply {
            parentFile?.mkdirs()
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }

        val resolved = resolveLinuxExecutable(
            env = mapOf("APPDIR" to appDir.absolutePath),
            userHome = userHome.absolutePath,
            command = launching.absolutePath,
            jpackageAppPath = launching.absolutePath,
        )

        assertNull("the mount path never outlives the app: $resolved", resolved)
        assertTrue(isInsideAppImageMount(launching.absolutePath, mapOf("APPDIR" to appDir.absolutePath)))
        assertFalse(
            isInsideAppImageMount("/home/deck/Applications/Yabai_OCR.AppImage", mapOf("APPDIR" to appDir.absolutePath)),
        )
        assertFalse("no APPDIR means nothing to compare", isInsideAppImageMount(launching.absolutePath, emptyMap()))
    }

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

    /**
     * The taskbar reads the entry's `Icon=`; the theme name goes through the desktop's icon cache,
     * and the Steam Deck kept showing a placeholder there while the tray (which receives pixels
     * itself) already had the new icon (ROADMAP D83). The installed file by absolute path needs no
     * cache at all.
     */
    @Test
    fun `the entry names the installed icon file by absolute path`() {
        val entry = applicationDesktopEntry(
            "/home/deck/Applications/Yabai OCR.AppImage",
            wmClass,
            "/home/deck/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png",
        )

        assertTrue(
            entry.contains("Icon=/home/deck/.local/share/icons/hicolor/256x256/apps/yabai-ocr.png"),
        )
        assertFalse(entry.contains("Icon=yabai-ocr\n"))
    }

    @Test
    fun `without a file the theme name stays the icon`() {
        val entry = applicationDesktopEntry("/home/deck/Applications/Yabai OCR.AppImage", wmClass)

        assertTrue(entry.contains("Icon=yabai-ocr"))
    }

    /**
     * A menu entry that names another app is how the taskbar lost the icon (ROADMAP D83): the entry's
     * `Exec` was the AppImage of the program that launched the app, because `$APPIMAGE` was inherited
     * through the environment.
     */
    @Test
    fun `an APPIMAGE inherited from the launching app is ignored`() {
        val env = mapOf(
            "APPIMAGE" to "/home/deck/Downloads/Freebuff-0.0.203-linux-x86_64.AppImage",
            "APPDIR" to "/tmp/.mount_FreebuZZZZZZ",
        )
        val candidates = linuxExecutableCandidates(
            env = env,
            userHome = "/home/deck",
            command = "/tmp/.mount_YabaiAAAAAA/opt/yabai-ocr/bin/yabai-ocr",
            jpackageAppPath = null,
        )

        assertFalse(candidates.any { it.contains("Freebuff") })
        assertTrue(candidates.contains("/tmp/.mount_YabaiAAAAAA/opt/yabai-ocr/bin/yabai-ocr"))
    }

    @Test
    fun `the APPIMAGE the process really came from is kept`() {
        val env = mapOf(
            "APPIMAGE" to "/home/deck/Downloads/Yabai_OCR-1.0.10.AppImage",
            "APPDIR" to "/tmp/.mount_YabaiAAAAAA",
        )
        val candidates = linuxExecutableCandidates(
            env = env,
            userHome = "/home/deck",
            command = "/tmp/.mount_YabaiAAAAAA/opt/yabai-ocr/bin/yabai-ocr",
            jpackageAppPath = null,
        )

        assertEquals("/home/deck/Downloads/Yabai_OCR-1.0.10.AppImage", candidates.first())
    }

    @Test
    fun `an APPIMAGE without APPDIR is trusted as before`() {
        val candidates = linuxExecutableCandidates(
            env = mapOf("APPIMAGE" to "/home/deck/Applications/Yabai.AppImage"),
            userHome = "/home/deck",
            command = "/usr/lib/jvm/java/bin/java",
            jpackageAppPath = null,
        )

        assertEquals("/home/deck/Applications/Yabai.AppImage", candidates.first())
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
