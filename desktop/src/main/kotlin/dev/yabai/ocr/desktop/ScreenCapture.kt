package dev.yabai.ocr.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * Size, in device pixels, of the native variant to ask `Robot` for.
 *
 * Asking with the user-space size returns the downscaled variant — the very image that forced the
 * picker to upscale and resample the screen. On an unscaled display the two are the same.
 */
internal fun nativeCaptureSize(bounds: Rectangle, scaleX: Double, scaleY: Double): Dimension =
    Dimension(
        (bounds.width * scaleX).roundToInt(),
        (bounds.height * scaleY).roundToInt(),
    )

object ScreenCapture {
    /**
     * Bounds of the primary screen, in AWT coordinates.
     *
     * The picker sizes its window to this rectangle so the captured image is shown at the same
     * size it was taken, taskbar included — maximizing to the work area would squeeze the image and
     * resample every glyph.
     */
    fun screenBounds(): Rectangle {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        return ge.defaultScreenDevice.defaultConfiguration.bounds
    }

    /**
     * Captures the primary screen at its **native** (device) resolution.
     *
     * `createScreenCapture` returns the user-space variant: on a display scaled at 150% that is
     * only two thirds of the pixels the screen really has, so drawing it back over the screen
     * upscales — and visibly resamples — every glyph. The multi-resolution variant labelled with
     * the device size is the framebuffer itself, which the picker can draw 1:1.
     */
    fun captureScreen(): BufferedImage {
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = device.defaultConfiguration.bounds
        val transform = device.defaultConfiguration.defaultTransform
        val robot = Robot(device)
        val variants = robot.createMultiResolutionScreenCapture(bounds)
        val size = nativeCaptureSize(bounds, transform.scaleX, transform.scaleY)
        val native = variants.getResolutionVariant(size.width.toDouble(), size.height.toDouble())
        return native as? BufferedImage ?: robot.createScreenCapture(bounds)
    }

    fun captureScreenAsImageBitmap(): ImageBitmap {
        return captureScreen().toComposeImageBitmap()
    }

    fun cropImage(source: BufferedImage, x: Int, y: Int, width: Int, height: Int): BufferedImage {
        val safeX = x.coerceIn(0, source.width - 1)
        val safeY = y.coerceIn(0, source.height - 1)
        val safeWidth = width.coerceAtMost(source.width - safeX).coerceAtLeast(1)
        val safeHeight = height.coerceAtMost(source.height - safeY).coerceAtLeast(1)
        return source.getSubimage(safeX, safeY, safeWidth, safeHeight)
    }
}
