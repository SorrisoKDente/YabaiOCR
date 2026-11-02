package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object AppIconGenerator {
    fun createTrayImage(size: Int = 16): BufferedImage {
        return createIconImage(size)
    }

    fun createWindowImage(size: Int = 64): BufferedImage {
        return createIconImage(size)
    }

    private fun createIconImage(size: Int): BufferedImage {
        val bi = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = bi.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Dark background matching mobile icon (#1B1D24)
        g.color = Color(0x1B, 0x1D, 0x24)
        val arc = size / 4
        g.fillRoundRect(0, 0, size, size, arc, arc)

        // White scan viewfinder corners and text lines matching mobile ic_scan
        g.color = Color.WHITE
        val stroke = maxOf(1, size / 16)
        val m = size / 6
        val len = size / 3

        // Top-Left corner
        g.fillRect(m, m, len, stroke)
        g.fillRect(m, m, stroke, len)

        // Top-Right corner
        g.fillRect(size - m - len, m, len, stroke)
        g.fillRect(size - m - stroke, m, stroke, len)

        // Bottom-Left corner
        g.fillRect(m, size - m - stroke, len, stroke)
        g.fillRect(m, size - m - len, stroke, len)

        // Bottom-Right corner
        g.fillRect(size - m - len, size - m - stroke, len, stroke)
        g.fillRect(size - m - stroke, size - m - len, stroke, len)

        // Center text lines (representing OCR text)
        val lineW = size * 3 / 5
        val lineH = maxOf(1, size / 12)
        val startX = (size - lineW) / 2
        val startY = size / 2 - lineH / 2

        g.fillRect(startX, startY - lineH, lineW, lineH)
        g.fillRect(startX, startY + lineH, lineW * 4 / 5, lineH)

        g.dispose()
        return bi
    }
}
