package dev.yabai.ocr.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage

object ScreenCapture {
    fun captureScreen(): BufferedImage {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultScreen = ge.defaultScreenDevice
        val bounds = defaultScreen.defaultConfiguration.bounds
        return Robot(defaultScreen).createScreenCapture(bounds)
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
