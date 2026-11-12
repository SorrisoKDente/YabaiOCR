package dev.yabai.ocr.core.manga

import java.nio.FloatBuffer

/**
 * Preprocessing utilities for the detection and recognition ONNX models.
 *
 * Each model has its own contract, and neither of them is the ImageNet one:
 *
 * - the **comic-text-detector** takes RGB scaled to 0..1 with no mean/std at all;
 * - **manga-ocr** takes the crop converted to grayscale (the model was trained on `L` images
 *   turned back into RGB) resized to 224x224 and normalised to -1..1, i.e. mean and std 0.5.
 *
 * Feeding either one the ImageNet mean/std it does not expect leaves the detector without text and
 * the recogniser without a single token.
 */
object MangaOcrPreprocessor {

    /** manga-ocr's `ViTImageProcessor`: mean 0.5, std 0.5 on every channel. */
    private const val RECOGNITION_MEAN = 0.5f
    private const val RECOGNITION_STD = 0.5f

    data class DetectionPreprocessResult(
        val buffer: FloatBuffer,
        val targetWidth: Int,
        val targetHeight: Int,
    )

    /**
     * Preprocesses an image for the comic-text-detector.
     *
     * Resizes to the target (a multiple of 32, or the model's own fixed size) and writes NCHW RGB in
     * 0..1 — the detector's contract, see `ComicTextDetector`.
     */
    fun prepareDetectionInput(
        pixels: IntArray,
        width: Int,
        height: Int,
        fixedWidth: Int? = null,
        fixedHeight: Int? = null,
    ): DetectionPreprocessResult {
        val targetWidth = fixedWidth ?: (((width + 31) / 32) * 32)
        val targetHeight = fixedHeight ?: (((height + 31) / 32) * 32)

        val buffer = FloatBuffer.allocate(1 * 3 * targetHeight * targetWidth)

        for (c in 0..2) {
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
                    buffer.put(color / 255.0f)
                }
            }
        }
        buffer.rewind()

        return DetectionPreprocessResult(buffer, targetWidth, targetHeight)
    }

    /**
     * Preprocesses a cropped text bounding box for manga-ocr (224x224 target size).
     *
     * The crop is converted to grayscale the way PIL's `convert("L")` does — the recogniser was
     * trained on manga-ocr's `img.convert("L").convert("RGB")` — and normalised to -1..1.
     */
    fun prepareRecognitionInput(
        cropPixels: IntArray,
        cropW: Int,
        cropH: Int,
        targetSize: Int = 224,
    ): FloatBuffer {
        val buffer = FloatBuffer.allocate(1 * 3 * targetSize * targetSize)
        val luma = FloatArray(targetSize * targetSize)

        for (y in 0 until targetSize) {
            val srcY = (y * cropH / targetSize).coerceIn(0, cropH - 1)
            for (x in 0 until targetSize) {
                val srcX = (x * cropW / targetSize).coerceIn(0, cropW - 1)
                val pixel = cropPixels[srcY * cropW + srcX]
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                // ITU-R 601-2 luma, the coefficients PIL uses for "L".
                luma[y * targetSize + x] = (red * 0.299f + green * 0.587f + blue * 0.114f) / 255.0f
            }
        }

        for (c in 0..2) {
            for (index in luma.indices) {
                buffer.put((luma[index] - RECOGNITION_MEAN) / RECOGNITION_STD)
            }
        }
        buffer.rewind()
        return buffer
    }
}
