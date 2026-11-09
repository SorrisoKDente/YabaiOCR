package dev.yabai.ocr.desktop

import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Test

class TrayMenuPlacementTest {

    private val workArea = Rectangle(0, 0, 1920, 1040)

    @Test
    fun `the menu opens with its bottom-left corner at the cursor`() {
        val position = placeTrayMenu(Point(100, 700), 230, 300, workArea)

        assertEquals(Point(100, 400), position)
    }

    @Test
    fun `near the right edge the bottom-right corner sits on the cursor`() {
        val position = placeTrayMenu(Point(1800, 700), 230, 300, workArea)

        assertEquals(Point(1570, 400), position)
    }

    @Test
    fun `the menu never crosses the right edge of the work area`() {
        val position = placeTrayMenu(Point(1910, 700), 230, 300, workArea)

        assertEquals(1680, position.x)
        assertEquals(1910, position.x + 230)
    }

    @Test
    fun `without room above the menu opens below the cursor`() {
        val position = placeTrayMenu(Point(100, 100), 230, 300, workArea)

        assertEquals(Point(100, 100), position)
    }

    @Test
    fun `a tray icon below the work area keeps the menu above the taskbar`() {
        val position = placeTrayMenu(Point(1800, 1060), 230, 300, workArea)

        assertEquals(Point(1570, 740), position)
        assertEquals(1040, position.y + 300)
    }

    @Test
    fun `a menu wider than the monitor is pinned to the left edge`() {
        val position = placeTrayMenu(Point(100, 700), 2000, 300, workArea)

        assertEquals(0, position.x)
    }

    @Test
    fun `without a global pointer the menu falls back to the bottom-right corner`() {
        val anchor = trayMenuFallbackAnchor(workArea)
        val position = placeTrayMenu(anchor, 230, 300, workArea)

        assertEquals(Point(1682, 732), position)
        assertEquals(1912, position.x + 230)
        assertEquals(1032, position.y + 300)
    }

    @Test
    fun `a monitor with negative coordinates keeps the menu on that monitor`() {
        val leftMonitor = Rectangle(-1920, 0, 1920, 1040)

        val position = placeTrayMenu(Point(-100, 700), 230, 300, leftMonitor)

        assertEquals(Point(-330, 400), position)
        assertEquals(-100, position.x + 230)
    }
}
