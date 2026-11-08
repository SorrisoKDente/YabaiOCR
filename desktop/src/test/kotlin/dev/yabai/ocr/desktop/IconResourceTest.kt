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
    fun `the windows ico is still present for the windows path`() {
        val stream = javaClass.getResourceAsStream("/ic_scan_windows.ico")

        assertTrue("ic_scan_windows.ico must remain in resources", stream != null)
        assertTrue(stream!!.use { it.readBytes() }.isNotEmpty())
    }
}
