package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import dev.yabai.ocr.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What is actually sent to Lens, size-wise.
 *
 * The cap is the one number in the OCR path that trades accuracy for bytes, and it was measured
 * against the real endpoint twice. First on a drawn page: at 720x1600 the kana printed beside a kanji
 * are not recognised, at 1080x2400 they come back as a reading. Then on the real page that reported
 * the loss, where the opposite happened — at 1080x2400 the recogniser drops an oversized stylised 淀
 * that 720x1600 reads. The word wins over the redundant reading, so the default brings a phone screen
 * down, and the test that matters is that it does: sending the screen native is exactly the
 * regression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImagePreparerTest {

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /**
     * A full-screen capture goes down to 720x1600. Native is what costs the kanji — this is the
     * regression, so it is pinned here rather than left to the constant.
     */
    @Test
    fun `a phone screen is brought down at the default`() {
        val encoded = ImagePreparer.encode(
            bitmap(1080, 2400),
            AppSettings.DEFAULT_MAX_DIMENSION,
            upscaleSmall = false,
        )

        assertEquals(720, encoded.width)
        assertEquals(1600, encoded.height)
    }

    @Test
    fun `a screen bigger than the phone is brought down, keeping the proportions`() {
        val encoded = ImagePreparer.encode(bitmap(1440, 3200), 2400, upscaleSmall = false)

        assertEquals(1080, encoded.width)
        assertEquals(2400, encoded.height)
    }

    @Test
    fun `a crop is enlarged when the setting is on`() {
        val encoded = ImagePreparer.encode(bitmap(300, 200), 2400, upscaleSmall = true)

        assertEquals(600, encoded.width)
        assertEquals(400, encoded.height)
    }

    @Test
    fun `a crop is left alone when the setting is off`() {
        val encoded = ImagePreparer.encode(bitmap(300, 200), 2400, upscaleSmall = false)

        assertEquals(300, encoded.width)
        assertEquals(200, encoded.height)
    }

    /** PNG, like the app has always sent: flat manga art compresses well and stays lossless. */
    @Test
    fun `the image goes as a png with bytes in it`() {
        val encoded = ImagePreparer.encode(bitmap(120, 80), 2400, upscaleSmall = false)

        assertEquals("image/png", encoded.mime)
        assertTrue("the payload must not be empty", encoded.bytes.size > 8)
        val magic = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertTrue(
            "expected a PNG signature, got ${encoded.bytes.take(4)}",
            encoded.bytes.copyOfRange(0, 4).contentEquals(magic),
        )
    }

    /** The upscale is capped, so a tiny capture cannot be blown up into a huge payload. */
    @Test
    fun `a tiny crop is only doubled`() {
        val encoded = ImagePreparer.encode(bitmap(1, 1), 2400, upscaleSmall = true)

        assertEquals(2, encoded.width)
        assertEquals(2, encoded.height)
    }

    /** And it is capped by the dimension setting as well, not just by the factor. */
    @Test
    fun `a crop is not enlarged past the dimension cap`() {
        val encoded = ImagePreparer.encode(bitmap(400, 200), 600, upscaleSmall = true)

        assertEquals(600, encoded.width)
        assertEquals(300, encoded.height)
    }
}
