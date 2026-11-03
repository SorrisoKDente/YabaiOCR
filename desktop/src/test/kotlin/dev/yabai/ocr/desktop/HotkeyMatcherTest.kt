package dev.yabai.ocr.desktop

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyMatcherTest {

    private fun stroke(
        keyCode: Int,
        keyText: String,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
    ) = KeyStroke(keyCode, keyText, ctrl, alt, shift, meta)

    @Test
    fun `the default hover shortcut matches ctrl shift h`() {
        val spec = HotkeyMatcher.parse("Ctrl + Shift + H")

        assertNotNull(spec)
        assertTrue(
            HotkeyMatcher.matches(
                spec,
                stroke(NativeKeyEvent.VC_H, "H", ctrl = true, shift = true),
            ),
        )
    }

    @Test
    fun `a shortcut with shift does not fire without shift`() {
        val spec = HotkeyMatcher.parse("Ctrl + Shift + H")

        assertFalse(
            HotkeyMatcher.matches(
                spec,
                stroke(NativeKeyEvent.VC_H, "H", ctrl = true),
            ),
        )
        assertFalse(
            HotkeyMatcher.matches(
                spec,
                stroke(NativeKeyEvent.VC_H, "H", ctrl = true, shift = true, alt = true),
            ),
        )
    }

    @Test
    fun `parsing accepts different separators and letter case`() {
        val compact = HotkeyMatcher.parse("ctrl+shift+h")
        val padded = HotkeyMatcher.parse("Ctrl + Shift + H")

        assertEquals(padded, compact)
        assertTrue(
            HotkeyMatcher.matches(
                compact,
                stroke(NativeKeyEvent.VC_H, "h", ctrl = true, shift = true),
            ),
        )
    }

    @Test
    fun `named keys use the native code so localised key text still matches`() {
        val spec = HotkeyMatcher.parse("Ctrl + Page Up")

        assertNotNull(spec)
        assertTrue(
            HotkeyMatcher.matches(
                spec,
                stroke(NativeKeyEvent.VC_PAGE_UP, "PageUp", ctrl = true),
            ),
        )
    }

    @Test
    fun `function keys are matched by number`() {
        val spec = HotkeyMatcher.parse("Alt + F4")

        assertNotNull(spec)
        assertTrue(HotkeyMatcher.matches(spec, stroke(NativeKeyEvent.VC_F4, "F4", alt = true)))
        assertFalse(HotkeyMatcher.matches(spec, stroke(NativeKeyEvent.VC_F5, "F5", alt = true)))
    }

    @Test
    fun `a shortcut with two main keys is rejected instead of silently using one`() {
        assertEquals(null, HotkeyMatcher.parse("Ctrl + Shift + H + J"))
        assertEquals(null, HotkeyMatcher.parse(""))
    }

    @Test
    fun `the settings defaults all parse and have the expected modifiers`() {
        val defaults = listOf("Ctrl + Shift + O", "Ctrl + Shift + D", "Ctrl + Shift + H")

        for (raw in defaults) {
            val spec = HotkeyMatcher.parse(raw)
            assertNotNull("could not parse $raw", spec)
            assertEquals(true, spec!!.ctrl)
            assertEquals(true, spec.shift)
            assertEquals(false, spec.alt)
            assertEquals(false, spec.meta)
        }
    }
}
