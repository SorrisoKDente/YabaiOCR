package dev.yabai.ocr.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.Rectangle

/**
 * Wayland ignores absolute window placement and does not honor "always on top" reliably. The
 * pickers still need to cover the captured screen, so on Wayland they open as real fullscreen
 * windows; on Windows/X11 the historical exact-bounds window is kept.
 */
internal fun pickerWindowPlacement(useFullscreen: Boolean): WindowPlacement =
    if (useFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating

internal fun pickerWindowPosition(screenBounds: Rectangle, useFullscreen: Boolean): WindowPosition =
    if (useFullscreen) {
        WindowPosition.PlatformDefault
    } else {
        WindowPosition.Absolute(x = screenBounds.x.dp, y = screenBounds.y.dp)
    }
