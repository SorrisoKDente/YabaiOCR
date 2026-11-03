package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.lens.PixelBox
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.math.roundToInt

/**
 * Screen-coordinate arithmetic for hover OCR.
 *
 * Three coordinate spaces meet here: AWT screen pixels (where the pointer and the tooltip live),
 * the requested crop rectangle, and the image Lens actually received. On a HiDPI monitor the last
 * two can differ — Robot may hand back a larger image than the rectangle asked for — so every
 * conversion is scaled by the real image size instead of assuming 1:1. That assumption is also why
 * the old code could position the tooltip at the wrong place, or throw when the cursor was close to
 * a screen edge and the fixed crop ran past it.
 */
object HoverGeometry {

    private const val MAX_MARGIN = 2

    /** Space between the pointer and the tooltip, so the card never covers the word read. */
    const val TOOLTIP_GAP = 18

    /**
     * Bounds of the monitor that contains [point], or the nearest one when none does.
     *
     * The card and the crop must be measured against **this** rectangle, not against the union of
     * every screen: with monitors of different sizes, the union's bottom is lower than the current
     * monitor's, so a card near the bottom of the smaller screen was considered «still fits» and
     * was drawn half outside it.
     */
    fun monitorAt(monitors: List<Rectangle>, point: Point): Rectangle? {
        if (monitors.isEmpty()) return null
        monitors.firstOrNull { it.contains(point) }?.let { return it }
        return monitors.minByOrNull { squaredDistance(it, point) }
    }

    /** Bounds of the monitor under [point], in AWT screen coordinates. */
    fun screenAt(point: Point): Rectangle {
        val monitors = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .mapNotNull { runCatching { it.defaultConfiguration.bounds }.getOrNull() }
        return monitorAt(monitors, point) ?: screenBounds()
    }

    /** Distance from [point] to the rectangle, 0 when inside; squared, so no square root is needed. */
    private fun squaredDistance(rect: Rectangle, point: Point): Long {
        val dx = when {
            point.x < rect.x -> (rect.x - point.x).toLong()
            point.x >= rect.x + rect.width -> (point.x - (rect.x + rect.width)).toLong()
            else -> 0L
        }
        val dy = when {
            point.y < rect.y -> (rect.y - point.y).toLong()
            point.y >= rect.y + rect.height -> (point.y - (rect.y + rect.height)).toLong()
            else -> 0L
        }
        return dx * dx + dy * dy
    }

    /** Bounding rectangle of every monitor, in AWT screen coordinates. */
    fun screenBounds(): Rectangle {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val bounds = environment.screenDevices
            .mapNotNull { runCatching { it.defaultConfiguration.bounds }.getOrNull() }
            .reduceOrNull { a, b -> a.union(b) }
        return bounds ?: Rectangle(Toolkit.getDefaultToolkit().screenSize)
    }

    /**
     * Crop rectangle of [width]×[height] around [pointer], moved so it stays inside [screen].
     *
     * Clamping the size as well as the origin is what keeps `Robot.createScreenCapture` from
     * throwing when the pointer is near the right or bottom edge — the old fixed rectangle did not,
     * and the exception was swallowed, making the hotkey look dead.
     */
    fun planCrop(pointer: Point, screen: Rectangle, width: Int, height: Int): Rectangle {
        require(width > 0 && height > 0) { "crop size must be positive" }
        val w = width.coerceAtMost(screen.width.coerceAtLeast(1))
        val h = height.coerceAtMost(screen.height.coerceAtLeast(1))
        val maxX = screen.x + screen.width - w
        val maxY = screen.y + screen.height - h
        val x = (pointer.x - w / 2).coerceIn(screen.x, maxX.coerceAtLeast(screen.x))
        val y = (pointer.y - h / 2).coerceIn(screen.y, maxY.coerceAtLeast(screen.y))
        return Rectangle(x, y, w, h)
    }

    /** The pointer position in the pixels of the image Robot actually returned. */
    fun localPoint(pointer: Point, crop: Rectangle, imageWidth: Int, imageHeight: Int): Point {
        val scaleX = imageWidth.toDouble() / crop.width.coerceAtLeast(1)
        val scaleY = imageHeight.toDouble() / crop.height.coerceAtLeast(1)
        val x = ((pointer.x - crop.x) * scaleX).roundToInt().coerceIn(0, (imageWidth - 1).coerceAtLeast(0))
        val y = ((pointer.y - crop.y) * scaleY).roundToInt().coerceIn(0, (imageHeight - 1).coerceAtLeast(0))
        return Point(x, y)
    }

    /** A box inside the submitted image translated back to AWT screen coordinates. */
    fun toScreen(box: PixelBox, crop: Rectangle, imageWidth: Int, imageHeight: Int): Rectangle {
        val scaleX = crop.width.toDouble() / imageWidth.coerceAtLeast(1)
        val scaleY = crop.height.toDouble() / imageHeight.coerceAtLeast(1)
        val x = crop.x + (box.x * scaleX).roundToInt()
        val y = crop.y + (box.y * scaleY).roundToInt()
        val w = (box.width * scaleX).roundToInt().coerceAtLeast(1)
        val h = (box.height * scaleY).roundToInt().coerceAtLeast(1)
        return Rectangle(x, y, w, h)
    }

    /**
     * Index in [monitors] of the screen that holds most of [crop].
     *
     * `Robot` is bound to one graphics device, and asking it to capture a rectangle that lives on
     * another monitor returns black. The crop is already clamped to the union of the screens; this
     * picks the screen whose area it mostly covers, so the capture comes from the monitor the
     * pointer is really on.
     */
    fun monitorIndex(monitors: List<Rectangle>, crop: Rectangle): Int {
        if (monitors.isEmpty()) return 0
        var best = 0
        var bestArea = -1.0
        monitors.forEachIndexed { index, bounds ->
            val overlap = bounds.intersection(crop)
            val area = if (overlap.isEmpty) -1.0 else overlap.width.toDouble() * overlap.height
            if (area > bestArea) {
                bestArea = area
                best = index
            }
        }
        return best
    }

    /**
     * Top-left corner for the tooltip of [size] anchored at [pointer].
     *
     * The card normally sits below and right of the pointer, at [gap] pixels. When that would run
     * past an edge it flips to the other side, and if even the flipped card is too big it is
     * clamped — a card half off screen is worse than a card under the pointer.
     */
    fun placeTooltip(
        pointer: Point,
        size: Dimension,
        screen: Rectangle,
        gap: Int = TOOLTIP_GAP,
    ): Point {
        val width = size.width.coerceAtMost(screen.width.coerceAtLeast(1))
        val height = size.height.coerceAtMost(screen.height.coerceAtLeast(1))

        var x = pointer.x + gap
        var y = pointer.y + gap
        if (x + width > screen.x + screen.width) x = pointer.x - width - gap
        if (y + height > screen.y + screen.height) y = pointer.y - height - gap

        val maxX = (screen.x + screen.width - width).coerceAtLeast(screen.x)
        val maxY = (screen.y + screen.height - height).coerceAtLeast(screen.y)
        return Point(x.coerceIn(screen.x, maxX), y.coerceIn(screen.y, maxY))
    }

    /** True when [point] is inside [rect] grown by [margin] pixels on every side. */
    fun contains(rect: Rectangle?, point: Point, margin: Int = MAX_MARGIN): Boolean {
        if (rect == null) return false
        return point.x >= rect.x - margin && point.x < rect.x + rect.width + margin &&
            point.y >= rect.y - margin && point.y < rect.y + rect.height + margin
    }
}
