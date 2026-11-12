package dev.yabai.ocr.core.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MangaOcrPreprocessorTest {

    @Test
    fun `prepareDetectionInput aligns target dimensions to multiples of 32`() {
        val pixels = IntArray(100 * 50) { 0xFF0000 } // 100x50 image
        val result = MangaOcrPreprocessor.prepareDetectionInput(pixels, 100, 50)

        // 100 aligned to multiple of 32 is 128; 50 aligned to multiple of 32 is 64
        assertEquals(128, result.targetWidth)
        assertEquals(64, result.targetHeight)
        assertNotNull(result.buffer)
        assertEquals(1 * 3 * 64 * 128, result.buffer.capacity())
    }

    @Test
    fun `prepareDetectionInput respects fixed target dimensions`() {
        val pixels = IntArray(1600 * 928) { 0xFF0000 }
        val result = MangaOcrPreprocessor.prepareDetectionInput(pixels, 1600, 928, fixedWidth = 1024, fixedHeight = 1024)

        assertEquals(1024, result.targetWidth)
        assertEquals(1024, result.targetHeight)
        assertNotNull(result.buffer)
        assertEquals(1 * 3 * 1024 * 1024, result.buffer.capacity())
    }

    @Test
    fun `prepareRecognitionInput produces 224x224 tensor buffer`() {
        val cropPixels = IntArray(30 * 80) { 0x00FF00 } // 30x80 crop
        val buffer = MangaOcrPreprocessor.prepareRecognitionInput(cropPixels, 30, 80, targetSize = 224)

        assertNotNull(buffer)
        assertEquals(1 * 3 * 224 * 224, buffer.capacity())
    }

    @Test
    fun `detection input is rgb scaled to zero one without a mean`() {
        // The comic-text-detector takes the pixel over 255 and nothing else; the old ImageNet mean
        // and std left white at 1.75 and black at -2.12, which the detector does not recognise.
        val white = MangaOcrPreprocessor.prepareDetectionInput(
            IntArray(1) { 0xFFFFFFFF.toInt() },
            1,
            1,
            fixedWidth = 1,
            fixedHeight = 1,
        )
        assertEquals(1f, white.buffer.get(0), 1e-6f)

        val red = MangaOcrPreprocessor.prepareDetectionInput(
            IntArray(1) { 0xFFFF0000.toInt() },
            1,
            1,
            fixedWidth = 1,
            fixedHeight = 1,
        )
        assertEquals(1f, red.buffer.get(0), 1e-6f)
        assertEquals(0f, red.buffer.get(1), 1e-6f)
        assertEquals(0f, red.buffer.get(2), 1e-6f)
    }

    @Test
    fun `recognition input is grayscale normalised to minus one one`() {
        val white = MangaOcrPreprocessor.prepareRecognitionInput(
            IntArray(1) { 0xFFFFFFFF.toInt() },
            1,
            1,
            targetSize = 1,
        )
        assertEquals(1f, white.get(0), 1e-6f)

        // Mid grey 0x80 is 128/255, and (128/255 - 0.5) / 0.5 is about 0.0039, not 0.09.
        val grey = MangaOcrPreprocessor.prepareRecognitionInput(
            IntArray(1) { 0xFF808080.toInt() },
            1,
            1,
            targetSize = 1,
        )
        assertEquals(0.00392f, grey.get(0), 1e-4f)
    }

    @Test
    fun `recognition input throws the colour away instead of feeding three different channels`() {
        // manga-ocr reads `convert("L")`; a red crop is one grey value copied into R, G and B.
        val red = MangaOcrPreprocessor.prepareRecognitionInput(
            IntArray(1) { 0xFFFF0000.toInt() },
            1,
            1,
            targetSize = 1,
        )
        val expected = (0.299f - 0.5f) / 0.5f
        assertEquals(expected, red.get(0), 1e-3f)
        assertEquals(red.get(0), red.get(1), 1e-6f)
        assertEquals(red.get(0), red.get(2), 1e-6f)
    }
}
