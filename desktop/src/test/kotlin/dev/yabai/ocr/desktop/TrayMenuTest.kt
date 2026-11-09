package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrayMenuTest {

    @Test
    fun `linux wayland uses the custom menu because the native popup is not delivered`() {
        assertEquals(TrayMenuKind.CUSTOM, trayMenuKindFor(OsFamily.LINUX, SessionType.WAYLAND))
    }

    @Test
    fun `linux x11 keeps the native menu`() {
        assertEquals(TrayMenuKind.NATIVE, trayMenuKindFor(OsFamily.LINUX, SessionType.X11))
        assertEquals(TrayMenuKind.NATIVE, trayMenuKindFor(OsFamily.LINUX, SessionType.UNKNOWN))
    }

    @Test
    fun `linux opens the custom menu on the right button only`() {
        assertFalse(trayMenuOpensOn(java.awt.event.MouseEvent.BUTTON1, false, showOnLeftClick = false))
        assertTrue(trayMenuOpensOn(java.awt.event.MouseEvent.BUTTON3, false, showOnLeftClick = false))
        assertTrue(trayMenuOpensOn(java.awt.event.MouseEvent.NOBUTTON, true, showOnLeftClick = false))
    }

    @Test
    fun `windows still opens the menu on both buttons`() {
        assertTrue(trayMenuOpensOn(java.awt.event.MouseEvent.BUTTON1, false, showOnLeftClick = true))
        assertTrue(trayMenuOpensOn(java.awt.event.MouseEvent.BUTTON3, false, showOnLeftClick = true))
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
