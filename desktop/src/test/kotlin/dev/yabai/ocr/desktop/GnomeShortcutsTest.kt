package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnomeShortcutsTest {

    @Test
    fun `the stored shortcuts become gnome accelerators`() {
        assertEquals("<Control><Shift>o", gnomeAccelerator("Ctrl + Shift + O"))
        assertEquals("<Control><Shift>a", gnomeAccelerator("Control+Shift+A"))
        assertEquals("<Alt>F4", gnomeAccelerator("Alt + F4"))
        assertEquals("<Super>space", gnomeAccelerator("Super + Space"))
        assertEquals("<Control>1", gnomeAccelerator("Ctrl + 1"))
        assertEquals("<Control><Shift>x", gnomeAccelerator("  ctrl +   shift  + x "))
    }

    @Test
    fun `a combination gnome cannot bind is left out instead of guessed`() {
        assertNull(gnomeAccelerator(""))
        assertNull(gnomeAccelerator("   "))
        // No key at all, just a modifier.
        assertNull(gnomeAccelerator("Ctrl + Shift"))
        // A name GNOME does not know.
        assertNull(gnomeAccelerator("Hyper + Q"))
        assertNull(gnomeAccelerator("Ctrl + PrintScreen"))
    }

    @Test
    fun `every configured shortcut in the settings maps to an id`() {
        val bindings = hotkeyBindings(
            fullScreen = "Ctrl + Shift + O",
            region = "Ctrl + Shift + A",
            quickDict = "Ctrl + Shift + D",
            hover = "Ctrl + Shift + H",
            hoverCycle = "Ctrl + Shift + X",
        )

        assertEquals(
            mapOf(
                HotkeyIds.FULL_SCREEN to "<Control><Shift>o",
                HotkeyIds.REGION to "<Control><Shift>a",
                HotkeyIds.QUICK_DICT to "<Control><Shift>d",
                HotkeyIds.HOVER_TOGGLE to "<Control><Shift>h",
                HotkeyIds.HOVER_CYCLE to "<Control><Shift>x",
            ),
            gnomeShortcutBindings(bindings),
        )
    }

    @Test
    fun `the dbus object publishes the bindings and takes the pressed id`() {
        var pressed = ""
        val obj = ShortcutsDbusObject(
            onShortcutPressed = { pressed = it },
            bindings = { mapOf(HotkeyIds.HOVER_TOGGLE to "<Control><Shift>h") },
        )

        assertEquals(mapOf(HotkeyIds.HOVER_TOGGLE to "<Control><Shift>h"), obj.shortcutBindings())
        obj.shortcutPressed(HotkeyIds.HOVER_TOGGLE)
        assertEquals(HotkeyIds.HOVER_TOGGLE, pressed)
        assertEquals(KWinScript.SHORTCUTS_PATH, obj.getObjectPath())
    }

    @Test
    fun `the extension registers and dispatches the shortcuts itself`() {
        // The GNOME bridge has no portal to fall back on, so the contract lives in the JS resource.
        val source = javaClass.classLoader
            .getResourceAsStream("gnome/yabai-ocr@yabai.dev/extension.js")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("extension.js not packaged")

        assertTrue("must ask the app for the accelerators", source.contains("ShortcutBindings"))
        assertTrue("must register them with the Shell", source.contains("addHotKey"))
        assertTrue("must release them on disable", source.contains("removeHotKey"))
        assertTrue("must dispatch the pressed shortcut", source.contains("ShortcutPressed"))
        assertTrue("must poll bindings after a settings edit", source.contains("SHORTCUT_REFRESH_SECONDS"))
    }
}
