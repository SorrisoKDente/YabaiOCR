package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPlatformTest {

    @Test
    fun `windows, linux and mac are recognized from the os name`() {
        assertEquals(OsFamily.WINDOWS, detectOsFamily("Windows 11"))
        assertEquals(OsFamily.LINUX, detectOsFamily("Linux"))
        assertEquals(OsFamily.MACOS, detectOsFamily("Mac OS X"))
        assertEquals(OsFamily.OTHER, detectOsFamily("FreeBSD"))
    }

    @Test
    fun `xdg session type wins over an xwayland display variable`() {
        assertEquals(
            SessionType.WAYLAND,
            detectSessionType(mapOf("XDG_SESSION_TYPE" to "wayland", "DISPLAY" to ":0")),
        )
        assertEquals(
            SessionType.X11,
            detectSessionType(mapOf("XDG_SESSION_TYPE" to "x11", "WAYLAND_DISPLAY" to "wayland-0")),
        )
    }

    @Test
    fun `wayland display alone is treated as wayland`() {
        assertEquals(
            SessionType.WAYLAND,
            detectSessionType(mapOf("WAYLAND_DISPLAY" to "wayland-0")),
        )
    }

    @Test
    fun `display alone is treated as x11`() {
        assertEquals(SessionType.X11, detectSessionType(mapOf("DISPLAY" to ":0")))
    }

    @Test
    fun `no graphical variables means unknown`() {
        assertEquals(SessionType.UNKNOWN, detectSessionType(emptyMap()))
    }

    @Test
    fun `kde and gnome are recognized from the current desktop`() {
        assertEquals(DesktopFamily.KDE, detectDesktopFamily(mapOf("XDG_CURRENT_DESKTOP" to "KDE")))
        assertEquals(
            DesktopFamily.GNOME,
            detectDesktopFamily(mapOf("XDG_CURRENT_DESKTOP" to "ubuntu:GNOME")),
        )
        assertEquals(
            DesktopFamily.OTHER,
            detectDesktopFamily(mapOf("XDG_CURRENT_DESKTOP" to "sway")),
        )
        assertEquals(DesktopFamily.UNKNOWN, detectDesktopFamily(emptyMap()))
    }

    @Test
    fun `steamos is recognized from the environment or os release`() {
        assertTrue(isSteamOs(mapOf("SteamOS" to "1"), null))
        assertTrue(isSteamOs(emptyMap(), "NAME=\"SteamOS\"\nID=steamos\n"))
        assertFalse(isSteamOs(emptyMap(), "ID=arch\n"))
        assertFalse(isSteamOs(emptyMap(), null))
    }
}
