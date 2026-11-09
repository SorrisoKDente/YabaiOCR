package dev.yabai.ocr.desktop

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconResourceTest {

    @Test
    fun `linux prefers a png and windows keeps the ico first`() {
        assertEquals("ic_scan_linux.png", iconResourceCandidates(isLinux = true).first())
        assertEquals("ic_scan_windows.ico", iconResourceCandidates(isLinux = false).first())
    }

    @Test
    fun `the linux icon is a readable png`() {
        val stream = javaClass.getResourceAsStream("/ic_scan_linux.png")

        assertTrue("ic_scan_linux.png must exist in resources", stream != null)
        val image: BufferedImage? = stream!!.use { ImageIO.read(it) }
        assertEquals(256, image?.width)
        assertEquals(256, image?.height)
    }

    @Test
    fun `the linux icon stays visible on light and dark trays`() {
        val stream = javaClass.getResourceAsStream("/ic_scan_linux.png")!!
        val image: BufferedImage = stream.use { ImageIO.read(it) }

        // The badge behind the glyph must not be transparent/near-white: on a light Plasma panel
        // the previous white-on-transparent glyph disappeared completely.
        val badge = image.getRGB(20, 128)
        assertTrue("the badge must be opaque", (badge ushr 24) and 0xFF > 200)
        val badgeLuminance = relativeLuminance(badge)
        assertTrue("badge luminance $badgeLuminance is too close to white", badgeLuminance < 0.35)
        assertTrue("badge contrast on white ${contrastOnWhite(badge)}", contrastOnWhite(badge) >= 3.0)

        // The glyph itself stays white so it is readable on top of the badge.
        val glyph = image.getRGB(64, 48)
        assertTrue("the glyph must be light", relativeLuminance(glyph) > 0.75)
    }

    private fun relativeLuminance(rgb: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = channel((rgb shr 16) and 0xFF)
        val g = channel((rgb shr 8) and 0xFF)
        val b = channel(rgb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastOnWhite(rgb: Int): Double =
        (1.0 + 0.05) / (relativeLuminance(rgb) + 0.05)

    @Test
    fun `the windows ico is still present for the windows path`() {
        val stream = javaClass.getResourceAsStream("/ic_scan_windows.ico")

        assertTrue("ic_scan_windows.ico must remain in resources", stream != null)
        assertTrue(stream!!.use { it.readBytes() }.isNotEmpty())
    }
}
