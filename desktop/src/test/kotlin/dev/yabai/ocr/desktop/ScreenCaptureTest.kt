package dev.yabai.ocr.desktop

import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenCaptureTest {

    @Test
    fun `a display scaled at 150 percent asks for the native device size`() {
        val size = nativeCaptureSize(Rectangle(0, 0, 1280, 720), 1.5, 1.5)

        assertEquals(1920, size.width)
        assertEquals(1080, size.height)
    }

    @Test
    fun `an unscaled display asks for exactly the screen bounds`() {
        val size = nativeCaptureSize(Rectangle(0, 0, 1024, 768), 1.0, 1.0)

        assertEquals(1024, size.width)
        assertEquals(768, size.height)
    }

    @Test
    fun `a fractional scale rounds to the nearest device pixel`() {
        val size = nativeCaptureSize(Rectangle(0, 0, 1366, 768), 1.25, 1.25)

        assertEquals(1708, size.width) // 1707.5
        assertEquals(960, size.height)
    }
}
