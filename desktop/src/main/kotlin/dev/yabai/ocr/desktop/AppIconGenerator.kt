package dev.yabai.ocr.desktop

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resource order for the window/tray icon. Linux gets a real PNG so the desktop theme picks it up;
 * Windows keeps the historical ICO first, with the PNG as a fallback.
 */
internal fun iconResourceCandidates(isLinux: Boolean): List<String> =
    if (isLinux) {
        listOf("ic_scan_linux.png", "ic_scan_windows.ico", "ic_scan_windows.png")
    } else {
        listOf("ic_scan_windows.ico", "ic_scan_windows.png")
    }

object AppIconGenerator {

    fun getTrayImage(): Image {
        return getTaskbarImage()
    }

    fun getTaskbarImage(): Image {
        for (resource in iconResourceCandidates(DesktopPlatform.isLinux)) {
            loadResourceIcon(resource)?.let { return it }
        }
        return createEmptyImage()
    }

    fun createWindowImage(size: Int = 64): Image {
        return getTaskbarImage()
    }

    /**
     * The tray icon with the hover status badge.
     *
     * A tray icon is the only part of the app that is always on screen, so the same pixels carry the
     * state on Windows (AWT) and on Linux (StatusNotifier `IconPixmap`). The badge is drawn at the
     * base PNG's native size and let the host scale it down, which keeps the app glyph sharp.
     */
    fun createTrayImage(hoverActive: Boolean): Image =
        createTrayStatusImage(getTrayImage(), hoverActive)

    private fun loadResourceIcon(resourceName: String): Image? {
        return runCatching {
            val stream = AppIconGenerator::class.java.getResourceAsStream("/$resourceName") ?: return null
            val bytes = stream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            // 1. Try parsing as ICO binary container to extract original PNG/BMP frame
            val fromIco = icoFrame(bytes)
            if (fromIco != null) return fromIco

            // 2. Direct ImageIO read (for PNG/BMP/JPG)
            ImageIO.read(ByteArrayInputStream(bytes))
        }.getOrNull()
    }

    /**
     * Parses Microsoft ICO binary format and extracts the original PNG/BMP image frame.
     *
     * Internal so the test can compare the raster icons against the artwork the Windows icon packs
     * (R52): the PNGs are the fallback on every platform, and a stale one made the Help window show
     * the old design.
     */
    internal fun icoFrame(bytes: ByteArray): BufferedImage? {
        if (bytes.size < 6) return null

        // If file starts with PNG magic bytes (0x89 'P' 'N' 'G')
        if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()) {
            return ImageIO.read(ByteArrayInputStream(bytes))
        }

        // Check ICO header: Reserved = 0, Type = 1 (ICO)
        val reserved = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        val type = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        val count = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)

        if (reserved != 0 || type != 1 || count <= 0) return null

        var bestImage: BufferedImage? = null
        var bestSize = -1

        for (i in 0 until count) {
            val entryOffset = 6 + i * 16
            if (entryOffset + 16 > bytes.size) break

            var width = bytes[entryOffset].toInt() and 0xFF
            var height = bytes[entryOffset + 1].toInt() and 0xFF
            if (width == 0) width = 256
            if (height == 0) height = 256

            val bytesInRes = (bytes[entryOffset + 8].toInt() and 0xFF) or
                    ((bytes[entryOffset + 9].toInt() and 0xFF) shl 8) or
                    ((bytes[entryOffset + 10].toInt() and 0xFF) shl 16) or
                    ((bytes[entryOffset + 11].toInt() and 0xFF) shl 24)

            val imageOffset = (bytes[entryOffset + 12].toInt() and 0xFF) or
                    ((bytes[entryOffset + 13].toInt() and 0xFF) shl 8) or
                    ((bytes[entryOffset + 14].toInt() and 0xFF) shl 16) or
                    ((bytes[entryOffset + 15].toInt() and 0xFF) shl 24)

            if (imageOffset >= 0 && imageOffset + bytesInRes <= bytes.size && bytesInRes > 0) {
                val frameData = bytes.copyOfRange(imageOffset, imageOffset + bytesInRes)
                val img = runCatching { ImageIO.read(ByteArrayInputStream(frameData)) }.getOrNull()
                if (img != null && width >= bestSize) {
                    bestSize = width
                    bestImage = img
                }
            }
        }

        return bestImage
    }

    private fun createEmptyImage(): BufferedImage {
        return BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    }
}

/** Green reads as «hover is on»; red as «hover is off», with a white minus for shape. */
internal val TRAY_HOVER_ACTIVE_COLOR = Color(0x3D, 0xDC, 0x97)
internal val TRAY_HOVER_INACTIVE_COLOR = Color(0xFF, 0x6B, 0x6B)
internal val TRAY_HOVER_RING_COLOR = Color.WHITE
internal val TRAY_HOVER_OUTLINE_COLOR = Color(0x0B, 0x0D, 0x12)
internal val TRAY_HOVER_SLASH_COLOR = Color.WHITE

/** Where the status dot sits, in pixels, for a square [size]. */
internal data class TrayHoverBadgeGeometry(
    val centerX: Int,
    val centerY: Int,
    val radius: Int,
    val ringRadius: Int,
    val outlineRadius: Int,
)

internal fun trayHoverBadgeGeometry(size: Int): TrayHoverBadgeGeometry {
    val radius = max(2, (size * 0.14f).roundToInt())
    val ringRadius = radius + max(1, (size * 0.025f).roundToInt())
    val outlineRadius = ringRadius + max(1, (size * 0.005f).roundToInt())
    // Keep the whole badge inside the square: on a 16 px icon the ideal bottom-right position would
    // clip the ring, and a clipped ring is exactly what makes a status dot unreadable.
    val center = min((size * 0.80f).roundToInt(), size - outlineRadius - 1)
        .coerceAtLeast(outlineRadius)
    return TrayHoverBadgeGeometry(center, center, radius, ringRadius, outlineRadius)
}

/**
 * Composes [base] with the hover badge.
 *
 * [size] is only for tests and previews; production uses the base image's own size so Windows and
 * the StatusNotifier host do the one downscale they already do.
 */
internal fun createTrayStatusImage(base: Image, hoverActive: Boolean, size: Int = 0): BufferedImage {
    val nativeWidth = base.getWidth(null)
    val nativeHeight = base.getHeight(null)
    val canvasSize = when {
        size > 0 -> size
        nativeWidth > 0 && nativeHeight > 0 -> max(nativeWidth, nativeHeight)
        else -> 256
    }
    val image = BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.drawImage(base, 0, 0, canvasSize, canvasSize, null)
        paintTrayHoverBadge(graphics, hoverActive, canvasSize)
    } finally {
        graphics.dispose()
    }
    return image
}

/** Draws the rounded status dot at the bottom-right of a [size]×[size] image. */
internal fun paintTrayHoverBadge(graphics: Graphics2D, hoverActive: Boolean, size: Int) {
    val badge = trayHoverBadgeGeometry(size)
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    fun circle(radius: Int, color: Color) {
        graphics.color = color
        graphics.fill(
            Ellipse2D.Float(
                badge.centerX - radius.toFloat(),
                badge.centerY - radius.toFloat(),
                radius * 2f,
                radius * 2f,
            ),
        )
    }

    // The dark outline separates a white ring from both a dark panel and the icon's white bracket.
    circle(badge.outlineRadius, TRAY_HOVER_OUTLINE_COLOR)
    circle(badge.ringRadius, TRAY_HOVER_RING_COLOR)
    circle(badge.radius, if (hoverActive) TRAY_HOVER_ACTIVE_COLOR else TRAY_HOVER_INACTIVE_COLOR)

    if (!hoverActive) {
        // The minus is the shape cue: red/green alone would fail for a colour-blind user.
        graphics.color = TRAY_HOVER_SLASH_COLOR
        graphics.stroke = BasicStroke(
            max(1.5f, size * 0.07f),
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND,
        )
        val half = badge.radius * 0.55f
        graphics.draw(
            Line2D.Float(
                badge.centerX - half,
                badge.centerY.toFloat(),
                badge.centerX + half,
                badge.centerY.toFloat(),
            ),
        )
    }
}
