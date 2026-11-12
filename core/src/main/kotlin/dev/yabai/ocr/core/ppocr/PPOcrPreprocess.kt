package dev.yabai.ocr.core.ppocr

import java.nio.FloatBuffer

/**
 * Preprocessing for the two PP-OCRv6_manga ONNX models.
 *
 * Both were trained with PaddleOCR's pipeline, and their contracts differ from every other engine in
 * the app:
 *
 * - the **detector** takes RGB **with the ImageNet mean/std** (0.485/0.456/0.406 and
 *   0.229/0.224/0.225), unlike the comic-text-detector, which takes plain 0..1;
 * - the **recogniser** takes a line crop **48 px tall**, width proportional to the crop, scaled to
 *   -1..1 (mean and std 0.5), and the crop must be turned upright first — a vertical Japanese line is
 *   rotated a quarter turn counter-clockwise exactly like `cv2.ROTATE_90_COUNTERCLOCKWISE`.
 *
 * Feeding either the wrong one gives the detector a page with no text and the recogniser empty
 * output, so these are the two places worth a test.
 */
object PPOcrPreprocess {

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** NCHW RGB of the whole page, ImageNet-normalised, resized to [targetWidth] x [targetHeight]. */
    fun detectorInput(
        pixels: IntArray,
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): FloatBuffer {
        val buffer = FloatBuffer.allocate(3 * targetHeight * targetWidth)
        val plane = targetHeight * targetWidth

        for (channel in 0..2) {
            for (y in 0 until targetHeight) {
                val sourceY = ((y + 0.5) * height / targetHeight - 0.5)
                for (x in 0 until targetWidth) {
                    val sourceX = ((x + 0.5) * width / targetWidth - 0.5)
                    val value = bilinearChannel(pixels, width, height, sourceX, sourceY, channel) / 255f
                    buffer.put(channel * plane + y * targetWidth + x, (value - MEAN[channel]) / STD[channel])
                }
            }
        }
        return buffer
    }

    /**
     * One line crop, turned upright and scaled to 48 px of height.
     *
     * Returns the tensor buffer and the width it was built at, so the caller knows the input shape.
     */
    fun recognizerInput(cropPixels: IntArray, cropWidth: Int, cropHeight: Int): RecognitionInput {
        val upright = if (cropHeight > cropWidth) {
            // A vertical line is read after a quarter turn, so the text runs left to right.
            rotateCounterClockwise(cropPixels, cropWidth, cropHeight)
        } else {
            UprightPixels(cropPixels, cropWidth, cropHeight)
        }

        val targetWidth = recognitionWidth(upright.width, upright.height)
        val targetHeight = PPOcrSpec.RECOGNITION_HEIGHT
        val buffer = FloatBuffer.allocate(3 * targetHeight * targetWidth)
        val plane = targetHeight * targetWidth

        for (channel in 0..2) {
            for (y in 0 until targetHeight) {
                val sourceY = ((y + 0.5) * upright.height / targetHeight - 0.5)
                for (x in 0 until targetWidth) {
                    val sourceX = ((x + 0.5) * upright.width / targetWidth - 0.5)
                    val value = bilinearChannel(
                        upright.pixels,
                        upright.width,
                        upright.height,
                        sourceX,
                        sourceY,
                        channel,
                    ) / 255f
                    buffer.put(channel * plane + y * targetWidth + x, (value - 0.5f) / 0.5f)
                }
            }
        }

        return RecognitionInput(buffer, targetWidth, targetHeight)
    }

    data class RecognitionInput(
        val buffer: FloatBuffer,
        val width: Int,
        val height: Int,
    )

    /** Pixels that already run left to right, with the size they turned into. */
    data class UprightPixels(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
    )

    /** `cv2.ROTATE_90_COUNTERCLOCKWISE`: the result is `height` wide and `width` tall. */
    fun rotateCounterClockwise(pixels: IntArray, width: Int, height: Int): UprightPixels {
        val rotated = IntArray(pixels.size)
        val targetWidth = height
        val targetHeight = width
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                // Same mapping as np.rot90/cv2.ROTATE_90_COUNTERCLOCKWISE:
                // destination (x, y) reads source (column width - 1 - y, row x).
                // Turning the other way reads the column bottom to top, which decoded to
                // «F2ーと上判れこ» on a page that says «これはテストです».
                rotated[y * targetWidth + x] = pixels[x * width + (width - 1 - y)]
            }
        }
        return UprightPixels(rotated, targetWidth, targetHeight)
    }

    /** Also used by the tests: rotation has to be a plain, checkable function. */
    fun rotatedPixels(pixels: IntArray, width: Int, height: Int): IntArray =
        rotateCounterClockwise(pixels, width, height).pixels

    private fun bilinearChannel(
        pixels: IntArray,
        width: Int,
        height: Int,
        sourceX: Double,
        sourceY: Double,
        channel: Int,
    ): Float {
        val x0 = sourceX.toInt().coerceIn(0, width - 1)
        val y0 = sourceY.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = (sourceX - x0).coerceIn(0.0, 1.0)
        val fy = (sourceY - y0).coerceIn(0.0, 1.0)

        val topLeft = channelAt(pixels[y0 * width + x0], channel)
        val topRight = channelAt(pixels[y0 * width + x1], channel)
        val bottomLeft = channelAt(pixels[y1 * width + x0], channel)
        val bottomRight = channelAt(pixels[y1 * width + x1], channel)

        val top = topLeft * (1 - fx) + topRight * fx
        val bottom = bottomLeft * (1 - fx) + bottomRight * fx
        return (top * (1 - fy) + bottom * fy).toFloat()
    }

    private fun channelAt(pixel: Int, channel: Int): Double = when (channel) {
        0 -> ((pixel shr 16) and 0xFF).toDouble()
        1 -> ((pixel shr 8) and 0xFF).toDouble()
        else -> (pixel and 0xFF).toDouble()
    }
}
