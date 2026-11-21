package dev.yabai.ocr.desktop

import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle

/**
 * The corner a window was last left at, or null when it cannot be honoured (D103).
 *
 * A saved corner is only used while a monitor still contains it. Unplugging the second monitor
 * otherwise reopens the window outside every screen, where no title bar reaches the pointer and
 * nothing can be clicked to bring it back. Null means «place the window as if it had never moved».
 */
internal fun rememberedWindowCorner(x: Int?, y: Int?, screens: List<Rectangle>): Point? {
    if (x == null || y == null) return null
    val corner = Point(x, y)
    return corner.takeIf { saved -> screens.any { it.contains(saved) } }
}

/** The bounds of the monitors attached right now, in AWT coordinates; empty on a headless machine. */
internal fun screenBoundsNow(): List<Rectangle> =
    if (GraphicsEnvironment.isHeadless()) {
        emptyList()
    } else {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .map { it.defaultConfiguration.bounds }
    }
