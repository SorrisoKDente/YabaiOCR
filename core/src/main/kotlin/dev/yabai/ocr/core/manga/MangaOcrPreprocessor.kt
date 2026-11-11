package dev.yabai.ocr.core.manga

import java.nio.FloatBuffer

/**
 * Preprocessing utilities for detection and recognition ONNX models.
 * Converts pixel arrays into NCHW FloatBuffers with normalization.
 */
object MangaOcrPreprocessor {

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    data class DetectionPreprocessResult(
        val buffer: FloatBuffer,
        val targetWidth: Int,
        val targetHeight: Int,
    )

    /**
     * Preprocesses an image for DBNet-based comic text detection.
     * Resizes dimensions to multiples of 32 and normalizes in NCHW order.
     */
    fun prepareDetectionInput(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): DetectionPreprocessResult {
        val targetWidth = ((width + 31) / 32) * 32
        val targetHeight = ((height + 31) / 32) * 32

        val buffer = FloatBuffer.allocate(1 * 3 * targetHeight * targetWidth)

        for (c in 0..2) {
            val m = MEAN[c]
            val s = STD[c]
            for (y in 0 until targetHeight) {
                val srcY = (y * height / targetHeight).coerceIn(0, height - 1)
                for (x in 0 until targetWidth) {
                    val srcX = (x * width / targetWidth).coerceIn(0, width - 1)
                    val pixel = pixels[srcY * width + srcX]
                    val color = when (c) {
                        0 -> (pixel shr 16) and 0xFF // Red
                        1 -> (pixel shr 8) and 0xFF  // Green
                        else -> pixel and 0xFF       // Blue
                    }
                    val normalized = (color / 255.0f - m) / s
                    buffer.put(normalized)
                }
            }
        }
        buffer.rewind()

        return DetectionPreprocessResult(buffer, targetWidth, targetHeight)
    }

    /**
     * Preprocesses a cropped text bounding box for Manga-OCR ViT recognition (224x224 target size).
     */
    fun prepareRecognitionInput(
        cropPixels: IntArray,
        cropW: Int,
        cropH: Int,
        targetSize: Int = 224,
    ): FloatBuffer {
        val buffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)

        for (c in 0..2) {
            val m = MEAN[c]
            val s = STD[c]
            for (y in 0 until targetSize) {
                val srcY = (y * cropH / targetSize).coerceIn(0, cropH - 1)
                for (x in 0 until targetSize) {
                    val srcX = (x * cropW / targetSize).coerceIn(0, cropW - 1)
                    val pixel = cropPixels[srcY * cropW + srcX]
                    val color = when (c) {
                        0 -> (pixel shr 16) and 0xFF
                        1 -> (pixel shr 8) and 0xFF
                        else -> pixel and 0xFF
                    }
                    val normalized = (color / 255.0f - m) / s
                    buffer.put(normalized)
                }
            }
        }
        buffer.rewind()
        return buffer
    }
}
