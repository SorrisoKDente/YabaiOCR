package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XdgTriggerTest {

    @Test
    fun `modifiers and letters use the xdg names`() {
        assertEquals("CTRL+SHIFT+O", toXdgTrigger("Ctrl + Shift + O"))
        assertEquals("ALT+F1", toXdgTrigger("Alt + F1"))
        assertEquals("CTRL+SHIFT+Insert", toXdgTrigger("Ctrl + Shift + Insert"))
    }

    @Test
    fun `super becomes logo and space keeps its keysym name`() {
        assertEquals("LOGO+space", toXdgTrigger("Super + Space"))
        assertEquals("SHIFT+LOGO+Q", toXdgTrigger("Meta + Shift + Q"))
    }

    @Test
    fun `the order the user wrote the modifiers does not change the trigger`() {
        assertEquals("CTRL+SHIFT+Q", toXdgTrigger("Shift + Ctrl + Q"))
        assertEquals(toXdgTrigger("Ctrl + Alt + H"), toXdgTrigger("Alt + Ctrl + H"))
    }

    @Test
    fun `named keys and function keys get their keysym names`() {
        assertEquals("CTRL+Page_Up", toXdgTrigger("Ctrl + Page Up"))
        assertEquals("ALT+Return", toXdgTrigger("Alt + Enter"))
        assertEquals("CTRL+F13", toXdgTrigger("Ctrl + F13"))
        assertEquals("CTRL+Delete", toXdgTrigger("Ctrl + Delete"))
    }

    @Test
    fun `a shortcut without a modifier is refused instead of bound globally`() {
        assertNull(toXdgTrigger("O"))
        assertNull(toXdgTrigger("Space"))
    }

    @Test
    fun `keys the portal cannot name are refused`() {
        assertNull(toXdgTrigger("Ctrl + Shift + ç"))
        assertNull(toXdgTrigger("Ctrl + Shift + ;"))
    }

    @Test
    fun `malformed shortcuts are refused`() {
        assertNull(toXdgTrigger("Ctrl + Shift + O + J"))
        assertNull(toXdgTrigger("Ctrl + Shift"))
        assertNull(toXdgTrigger(""))
    }
}
