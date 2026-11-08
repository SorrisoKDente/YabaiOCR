package dev.yabai.ocr.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerPlacementTest {

    @Test
    fun `wayland pickers open as fullscreen instead of absolute windows`() {
        val position = pickerWindowPosition(Rectangle(10, 20, 1280, 800), useFullscreen = true)

        assertEquals(WindowPlacement.Fullscreen, pickerWindowPlacement(useFullscreen = true))
        assertTrue(position is WindowPosition.PlatformDefault)
    }

    @Test
    fun `windows and x11 keep the exact captured bounds`() {
        val position = pickerWindowPosition(Rectangle(10, 20, 1280, 800), useFullscreen = false)

        assertEquals(WindowPlacement.Floating, pickerWindowPlacement(useFullscreen = false))
        val absolute = position as WindowPosition.Absolute
        assertEquals(10.dp, absolute.x)
        assertEquals(20.dp, absolute.y)
    }
}
