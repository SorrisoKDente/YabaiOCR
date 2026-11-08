package dev.yabai.ocr.desktop

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

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

    fun createTrayImage(size: Int = 16): Image {
        return getTrayImage()
    }

    private fun loadResourceIcon(resourceName: String): Image? {
        return runCatching {
            val stream = AppIconGenerator::class.java.getResourceAsStream("/$resourceName") ?: return null
            val bytes = stream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            // 1. Try parsing as ICO binary container to extract original PNG/BMP frame
            val fromIco = parseIcoBytes(bytes)
            if (fromIco != null) return fromIco

            // 2. Direct ImageIO read (for PNG/BMP/JPG)
            ImageIO.read(ByteArrayInputStream(bytes))
        }.getOrNull()
    }

    /**
     * Parses Microsoft ICO binary format and extracts the original PNG/BMP image frame.
     */
    private fun parseIcoBytes(bytes: ByteArray): BufferedImage? {
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
