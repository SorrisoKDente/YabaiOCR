package dev.yabai.ocr.desktop

import java.awt.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `Frame.MAXIMIZED_HORZ`, kept as a constant because the JDK exposes it differently across lines. */
private const val MAXIMIZED_HORZ = 0x2

class WindowActivationTest {

    @Test
    fun `the iconified bit is the one that gets cleared`() {
        val iconified = Frame.ICONIFIED or MAXIMIZED_HORZ

        val restored = unminimizedExtendedState(iconified)

        assertEquals(0, restored and Frame.ICONIFIED)
        // What was not the problem must survive: a window that was maximised stays maximised.
        assertTrue(restored and MAXIMIZED_HORZ != 0)
    }

    @Test
    fun `a normal window is left as it is`() {
        assertEquals(0, unminimizedExtendedState(0))
    }
}
