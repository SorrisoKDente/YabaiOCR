package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PNG for what PNG compresses, JPEG for what it does not (ROADMAP D83).
 *
 * The hover sends the whole monitor to Lens, and on this Deck the wait is the upload: a 1 540 KB PNG
 * of a manga page took 3.4 s, while the same page as a 299 KB JPEG took 1.0 s with no segment lost.
 * A UI screen must keep going as PNG — it is 149 KB there, lossless, and changed text is exactly
 * what the precision rule forbids.
 */
class LensPayloadTest {

    private fun pngMagics(bytes: ByteArray): Boolean =
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()

    private fun jpegMagics(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    /** Flat white with a few dark blocks: what a text screen looks like to a compressor. */
    private fun screenLike(width: Int = 1280, height: Int = 800): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.color = Color.BLACK
        for (row in 0 until height step 20) graphics.fillRect(40, row, width - 80, 8)
        graphics.dispose()
        return image
    }

    /** Random pixels: what artwork looks like to a compressor. */
    private fun artworkLike(width: Int = 1280, height: Int = 800): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = Random(7)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF))
            }
        }
        return image
    }

    @Test
    fun `a text screen goes as PNG`() {
        val bytes = LensPayload.encode(screenLike())

        assertTrue("a 1280x800 text screen must stay lossless", pngMagics(bytes))
        assertTrue("it should be far under the limit", bytes.size <= LensPayload.PNG_LIMIT_BYTES)
    }

    @Test
    fun `an image PNG cannot compress goes as JPEG`() {
        val image = artworkLike()
        val png = LensPayload.encodePng(image)
        val sent = LensPayload.encode(image)

        assertTrue("the fixture must really be heavy as PNG", png.size > LensPayload.PNG_LIMIT_BYTES)
        assertTrue("artwork must not be uploaded as a megabyte PNG", jpegMagics(sent))
        assertTrue("JPEG must be smaller than the PNG it replaces", sent.size < png.size)
    }

    @Test
    fun `the JPEG keeps the image size`() {
        val image = artworkLike(width = 640, height = 400)
        val decoded = javax.imageio.ImageIO.read(LensPayload.encodeJpeg(image).inputStream())

        assertEquals(640, decoded.width)
        assertEquals(400, decoded.height)
    }

    @Test
    fun `a small image that JPEG cannot beat is never made bigger`() {
        // Flat colour stays smaller as PNG even when it is large: the encoder must not trade bytes
        // for format when JPEG is the larger of the two.
        val image = screenLike(width = 1600, height = 1200)
        val png = LensPayload.encodePng(image)
        val sent = LensPayload.encode(image)

        assertTrue("never send more than the PNG", sent.size <= png.size)
        assertEquals("and keep the lossless format when it wins", true, pngMagics(sent))
    }
}
