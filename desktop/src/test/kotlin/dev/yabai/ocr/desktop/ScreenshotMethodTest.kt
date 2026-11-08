package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotMethodTest {

    private val writes = mutableListOf<Pair<String, String>>()
    private val setter: (String, String) -> Unit = { key, value -> writes.add(key to value) }

    @Test
    fun `kde wayland selects the screencast method before awt starts`() {
        val method = configureScreenshotMethod(
            isLinux = true,
            session = SessionType.WAYLAND,
            desktop = DesktopFamily.KDE,
            setProperty = setter,
        )

        assertEquals("dbusScreencast", method)
        assertEquals(listOf(AWT_SCREENSHOT_METHOD_PROPERTY to "dbusScreencast"), writes)
    }

    @Test
    fun `gnome wayland keeps the jdk default instead of forcing screencast`() {
        val method = configureScreenshotMethod(
            isLinux = true,
            session = SessionType.WAYLAND,
            desktop = DesktopFamily.GNOME,
            setProperty = setter,
        )

        assertNull(method)
        assertEquals(emptyList<Pair<String, String>>(), writes)
    }

    @Test
    fun `x11 and windows keep the x11 capture path`() {
        assertNull(screenshotMethodOverride(true, SessionType.X11, DesktopFamily.KDE))
        assertNull(screenshotMethodOverride(false, SessionType.WAYLAND, DesktopFamily.KDE))
        assertNull(screenshotMethodOverride(false, SessionType.X11, DesktopFamily.UNKNOWN))
    }
}
