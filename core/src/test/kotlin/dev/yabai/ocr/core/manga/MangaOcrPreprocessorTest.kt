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
    fun `prepareRecognitionInput produces 224x224 tensor buffer`() {
        val cropPixels = IntArray(30 * 80) { 0x00FF00 } // 30x80 crop
        val buffer = MangaOcrPreprocessor.prepareRecognitionInput(cropPixels, 30, 80, targetSize = 224)

        assertNotNull(buffer)
        assertEquals(1 * 3 * 224 * 224, buffer.capacity())
    }
}
