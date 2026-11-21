package dev.yabai.ocr.desktop

import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import javax.swing.JFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The result window and the quick dictionary have to reopen where they were left, and come back when
 * the user asks for them while they are minimised (D103).
 *
 * The corner is only honoured while a monitor still contains it: a display that was unplugged would
 * otherwise leave the window off every screen — the state where it cannot be found or moved back.
 * The minimised half is the existing [activateOnOpen], now called on every request rather than once
 * at window creation.
 */
class WindowRestoreTest {

    private val primary = Rectangle(0, 0, 1280, 720)
    private val secondary = Rectangle(1280, 0, 1920, 1080)

    @Test
    fun `a corner a monitor still contains is used as saved`() {
        assertEquals(
            Point(1500, 200),
            rememberedWindowCorner(1500, 200, listOf(primary, secondary)),
        )
        assertEquals(Point(40, 40), rememberedWindowCorner(40, 40, listOf(primary)))
    }

    @Test
    fun `a corner left on a monitor that is gone is dropped`() {
        assertNull(
            "a window saved on the second monitor must not reopen off screen",
            rememberedWindowCorner(1500, 200, listOf(primary)),
        )
    }

    @Test
    fun `a window that never moved has no corner`() {
        assertNull(rememberedWindowCorner(null, null, listOf(primary)))
        assertNull("half a position is not a position", rememberedWindowCorner(10, null, listOf(primary)))
    }

    @Test
    fun `with no screens there is nowhere to place it`() {
        assertNull(rememberedWindowCorner(10, 10, emptyList()))
    }

    /** The window a saved corner produces is the window AWT actually opens at that corner. */
    @Test
    fun `the saved corner is where the window opens`() {
        assumeFalse("needs a display", GraphicsEnvironment.isHeadless())
        val screens = screenBoundsNow()
        val corner = rememberedWindowCorner(60, 40, screens)
        assumeTrue("this desktop does not have (60, 40) on a monitor", corner != null)

        val window = JFrame()
        try {
            window.setBounds(corner!!.x, corner.y, 400, 300)
            window.isVisible = true
            assertEquals("AWT ignored the saved corner", Point(60, 40), window.location)
        } finally {
            window.dispose()
        }
    }

    /** A minimised window comes back when the user asks for it again. */
    @Test
    fun `a minimised window is restored by asking for it`() {
        assumeFalse("needs a display", GraphicsEnvironment.isHeadless())
        val window = JFrame()
        try {
            window.setBounds(100, 100, 320, 240)
            window.isVisible = true
            window.extendedState = window.extendedState or Frame.ICONIFIED
            assumeTrue(
                "this window manager does not minimise subframes",
                window.extendedState and Frame.ICONIFIED != 0,
            )

            activateOnOpen(window)

            assertEquals(
                "the window stayed minimised, so asking for it did nothing",
                0,
                window.extendedState and Frame.ICONIFIED,
            )
        } finally {
            window.dispose()
        }
    }

    /**
     * Both windows must go through the helper and remember their corner; a window that only reads
     * the setting would open in the right place but never save a new one, and the next result would
     * come back where the user last left it *before* the move.
     */
    @Test
    fun `both windows save and restore their corner`() {
        val result = source("ResultWindow.kt")
        val quick = source("QuickDictWindow.kt")
        val main = source("Main.kt")

        assertTrue(
            "the result window does not place itself at the saved corner",
            result.contains("rememberedWindowCorner(settings.resultWindowX, settings.resultWindowY"),
        )
        assertTrue(
            "the result window does not save the corner it was moved to",
            result.contains("settings.resultWindowX = absolute.x.value.toInt()"),
        )
        assertTrue(
            "the result window only activates once, at creation",
            result.contains("LaunchedEffect(croppedImage, initialText)") &&
                result.contains("activateOnOpen(window)"),
        )

        assertTrue(
            "the quick dictionary does not place itself at the saved corner",
            quick.contains("rememberedWindowCorner(settings.quickDictX, settings.quickDictY"),
        )
        assertTrue(
            "the quick dictionary does not come back on a new request",
            quick.contains("LaunchedEffect(activation)") && quick.contains("activateOnOpen(window)"),
        )

        assertTrue(
            "the tray and the shortcut must count as requests",
            Regex("""quickDictRequest\+\+""").findAll(main).count() >= 2,
        )
        assertTrue(
            "the request counter is not handed to the window",
            main.contains("QuickDictWindow(activation = quickDictRequest"),
        )
    }

    private fun source(name: String): String {
        val found = sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("$name not found", found.isNotEmpty())
        return found
    }
}
