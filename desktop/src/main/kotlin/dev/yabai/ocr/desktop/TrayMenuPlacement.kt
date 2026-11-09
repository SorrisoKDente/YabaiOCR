package dev.yabai.ocr.desktop

import java.awt.Point
import java.awt.Rectangle

/**
 * Where the tray menu goes, in screen coordinates.
 *
 * The menu is anchored by its **bottom** edge, like the Steam tray menu: the bottom-left corner
 * sits on the cursor and the menu grows up and to the right. Near the right edge that would be
 * clipped, so the menu flips to its bottom-right corner on the cursor and grows to the left. If
 * there is no room above, it opens below the cursor instead.
 *
 * [workArea] is the screen minus the taskbar (and any other reserved edge). Clamping to it keeps
 * the menu out of the taskbar and inside the monitor, including monitors with negative X/Y.
 */
internal fun placeTrayMenu(
    anchor: Point,
    menuWidth: Int,
    menuHeight: Int,
    workArea: Rectangle,
): Point {
    require(menuWidth > 0 && menuHeight > 0) { "menu size must be positive" }

    val right = workArea.x + workArea.width
    val bottom = workArea.y + workArea.height

    // Right edge on the cursor by default; flip only when the default would leave the screen.
    var x = if (anchor.x + menuWidth <= right) anchor.x else anchor.x - menuWidth

    // Bottom edge on the cursor, opening upwards. Without room above, open downwards.
    var y = anchor.y - menuHeight
    if (y < workArea.y) y = anchor.y

    // A menu larger than the screen would make coerceIn's range invalid; pin it to the top-left.
    val maxX = maxOf(workArea.x, right - menuWidth)
    val maxY = maxOf(workArea.y, bottom - menuHeight)
    x = x.coerceIn(workArea.x, maxX)
    y = y.coerceIn(workArea.y, maxY)

    return Point(x, y)
}

/**
 * Where the menu opens when the session reports no global pointer (native Wayland), so the tray
 * menu still has a reachable, on-screen position instead of the icon-relative mouse coordinates.
 */
internal fun trayMenuFallbackAnchor(workArea: Rectangle, margin: Int = 8): Point =
    Point(
        workArea.x + workArea.width - margin,
        workArea.y + workArea.height - margin,
    )
