package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxMenuRefreshTest {

    private val applicationsDir = File("/home/deck/.local/share/applications")

    @Test
    fun `plasma 6 is tried before plasma 5`() {
        val commands = mutableListOf<List<String>>()

        val refreshed = refreshLinuxApplicationMenu(
            family = DesktopFamily.KDE,
            applicationsDir = applicationsDir,
            run = {
                commands += it
                false
            },
        )

        assertFalse(refreshed)
        assertEquals(
            listOf(
                listOf("kbuildsycoca6", "--noincremental"),
                listOf("kbuildsycoca5", "--noincremental"),
            ),
            commands,
        )
    }

    @Test
    fun `the first working menu refresh stops the fallback`() {
        val commands = mutableListOf<List<String>>()

        val refreshed = refreshLinuxApplicationMenu(
            family = DesktopFamily.KDE,
            applicationsDir = applicationsDir,
            run = {
                commands += it
                true
            },
        )

        assertTrue(refreshed)
        assertEquals(listOf(listOf("kbuildsycoca6", "--noincremental")), commands)
    }

    @Test
    fun `the menu helper does not inherit the appimage mount`() {
        val environment = mapOf(
            "PATH" to "/usr/bin",
            "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/run/user/1000/bus",
            "APPIMAGE" to "/home/deck/Applications/Yabai_OCR.AppImage",
            "APPDIR" to "/tmp/.mount_Yabai",
            "APPDIR_LIBRARY_PATH" to "/tmp/.mount_Yabai/lib",
            "APPIMAGE_EXTRACT_AND_RUN" to "1",
            "ARGV0" to "/home/deck/Applications/Yabai_OCR.AppImage",
            "OWD" to "/home/deck",
            "LD_LIBRARY_PATH" to "/tmp/.mount_Yabai/lib",
        )

        assertEquals(
            mapOf(
                "PATH" to "/usr/bin",
                "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/run/user/1000/bus",
            ),
            menuRefreshEnvironment(environment),
        )
    }

    @Test
    fun `other desktops refresh the mime database of the applications folder`() {
        val commands = mutableListOf<List<String>>()

        val refreshed = refreshLinuxApplicationMenu(
            family = DesktopFamily.GNOME,
            applicationsDir = applicationsDir,
            run = {
                commands += it
                false
            },
        )

        assertFalse(refreshed)
        assertEquals(
            listOf(listOf("update-desktop-database", applicationsDir.absolutePath)),
            commands,
        )
    }
}
