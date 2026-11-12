package dev.yabai.ocr.core.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.FloatBuffer

/**
 * Reading the comic-text-detector's `blk` output.
 *
 * The case that made this worth a file: the detector answers with **decoded text lines** in `blk`
 * (`cx, cy, w, h, confidence, horizontal, vertical`, in its own 1024x1024 space) and the engine was
 * reading output 0 as a probability heatmap. Coordinates read as confidences put everything above
 * the threshold, so the whole page flooded into one box and the recogniser was handed the entire
 * screen instead of a balloon.
 */
class ComicTextDetectorBlocksTest {

    /** One `blk` row: centre, size, confidence and the two class scores. */
    private fun row(
        cx: Float,
        cy: Float,
        width: Float,
        height: Float,
        confidence: Float,
        horizontal: Float = 0.9f,
        vertical: Float = 0.1f,
    ): FloatArray = floatArrayOf(cx, cy, width, height, confidence, horizontal, vertical)

    private fun buffer(vararg rows: FloatArray): FloatBuffer =
        FloatBuffer.wrap(rows.flatMap { it.toList() }.toFloatArray())

    private fun boxes(
        data: FloatBuffer,
        rows: Int,
        origWidth: Int = 1024,
        origHeight: Int = 1024,
    ): List<ComicDetectedBox> = ComicTextDetector.boxesFromBlocks(
        data = data,
        rows = rows,
        stride = ComicTextDetector.BLOCK_STRIDE,
        origWidth = origWidth,
        origHeight = origHeight,
        targetWidth = ComicTextDetector.DETECTION_SIZE,
        targetHeight = ComicTextDetector.DETECTION_SIZE,
    )

    @Test
    fun `a block row becomes the rectangle of that text line`() {
        val detected = boxes(buffer(row(cx = 512f, cy = 512f, width = 256f, height = 64f, confidence = 0.9f)), rows = 1)

        assertEquals(1, detected.size)
        val box = detected.first()
        // 512 - 128 = 384, 512 - 32 = 480, 512 + 128 = 640, 512 + 32 = 544.
        assertEquals(384, box.left)
        assertEquals(480, box.top)
        assertEquals(640, box.right)
        assertEquals(544, box.bottom)
        assertFalse("a wide line is horizontal", box.isVertical)
    }

    @Test
    fun `blocks are scaled from the detector input back to the image`() {
        // A 2048x512 image squashed into the 1024x1024 input: x doubles, y halves.
        val detected = boxes(
            data = buffer(row(cx = 512f, cy = 512f, width = 256f, height = 64f, confidence = 0.9f)),
            rows = 1,
            origWidth = 2048,
            origHeight = 512,
        )

        val box = detected.single()
        assertEquals(768, box.left)
        assertEquals(240, box.top)
        assertEquals(1280, box.right)
        assertEquals(272, box.bottom)
    }

    @Test
    fun `the class scores say a column is vertical`() {
        val detected = boxes(
            buffer(row(cx = 512f, cy = 512f, width = 64f, height = 256f, confidence = 0.9f, horizontal = 0.1f, vertical = 0.9f)),
            rows = 1,
        )

        assertTrue(detected.single().isVertical)
    }

    @Test
    fun `a row below the confidence threshold is not a box`() {
        val detected = boxes(buffer(row(512f, 512f, 256f, 64f, confidence = 0.1f)), rows = 1)

        assertTrue(detected.isEmpty())
    }

    @Test
    fun `the same region in both classes is one box, not two`() {
        val detected = boxes(
            buffer(
                row(512f, 512f, 256f, 64f, confidence = 0.5f, horizontal = 0.9f, vertical = 0.1f),
                row(512f, 512f, 256f, 64f, confidence = 0.4f, horizontal = 0.1f, vertical = 0.9f),
            ),
            rows = 2,
        )

        assertEquals(1, detected.size)
    }

    @Test
    fun `two guesses for the same line keep only the more confident one`() {
        val detected = boxes(
            buffer(
                row(512f, 512f, 256f, 64f, confidence = 0.9f),
                row(514f, 512f, 258f, 66f, confidence = 0.6f),
                row(512f, 100f, 256f, 64f, confidence = 0.8f),
            ),
            rows = 3,
        )

        assertEquals(2, detected.size)
        assertEquals(480, detected.first().top)
        assertEquals(68, detected.last().top)
    }

    @Test
    fun `a page of text gives one box per line and not the whole page`() {
        val detected = boxes(
            buffer(
                row(200f, 200f, 120f, 40f, confidence = 0.8f),
                row(200f, 400f, 120f, 40f, confidence = 0.8f),
                row(200f, 600f, 120f, 40f, confidence = 0.8f),
            ),
            rows = 3,
        )

        assertEquals(3, detected.size)
        assertTrue("no box may cover the page", detected.all { it.bottom - it.top < 1024 / 2 })
    }
}
