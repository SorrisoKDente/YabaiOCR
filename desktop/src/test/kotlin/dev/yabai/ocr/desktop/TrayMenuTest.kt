package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrayMenuTest {

    @Test
    fun `linux always uses the native menu on any session`() {
        assertEquals(TrayMenuKind.NATIVE, trayMenuKindFor(OsFamily.LINUX, SessionType.WAYLAND))
        assertEquals(TrayMenuKind.NATIVE, trayMenuKindFor(OsFamily.LINUX, SessionType.X11))
        assertEquals(TrayMenuKind.NATIVE, trayMenuKindFor(OsFamily.LINUX, SessionType.UNKNOWN))
    }

    @Test
    fun `windows keeps the custom menu`() {
        assertEquals(TrayMenuKind.CUSTOM, trayMenuKindFor(OsFamily.WINDOWS, SessionType.UNKNOWN))
        assertEquals(TrayMenuKind.CUSTOM, trayMenuKindFor(OsFamily.MACOS, SessionType.UNKNOWN))
    }

    @Test
    fun `a minimized start opens the window when the tray failed`() {
        assertFalse(shouldShowMainWindowOnStart(startMinimized = true, trayReady = true))
        assertTrue(shouldShowMainWindowOnStart(startMinimized = true, trayReady = false))
    }

    @Test
    fun `a normal start always opens the window`() {
        assertTrue(shouldShowMainWindowOnStart(startMinimized = false, trayReady = true))
        assertTrue(shouldShowMainWindowOnStart(startMinimized = false, trayReady = false))
    }
}
