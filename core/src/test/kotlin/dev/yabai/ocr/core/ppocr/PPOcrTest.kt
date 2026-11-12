package dev.yabai.ocr.core.ppocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the PP-OCR pipeline that can be checked without a 23 MB model.
 *
 * Each one is a place where getting it wrong is silent: the detector gets a page with no text, the
 * recogniser returns an empty string, or a balloon is read in the wrong order.
 */
class PPOcrTest {

    // --- Detection size -------------------------------------------------------------------------

    @Test
    fun `a page is read at 960 on the long side in multiples of 32`() {
        val (width, height) = detectionSize(900, 1400)

        assertEquals(960, maxOf(width, height))
        assertEquals(0, width % 32)
        assertEquals(0, height % 32)
        // 900 * 960/1400 = 617 -> 608
        assertEquals(608, width)
    }

    @Test
    fun `a webtoon strip gets the pixel budget of a 3 by 4 page, not a 960 long side`() {
        val (width, height) = detectionSize(800, 4000)

        assertEquals(0, width % 32)
        assertEquals(0, height % 32)
        // The budget is ~0.75 * 960^2 pixels, not 960 x 960.
        val area = width.toLong() * height
        val budget = (0.75 * 960 * 960).toLong()
        assertTrue("area $area is far from the $budget budget", area in (budget * 8 / 10)..(budget * 12 / 10))
    }

    @Test
    fun `tiny crops are upscaled to the 960 long side, aligned to 32`() {
        // The reference pipeline scales small crops up; a 10 px line is otherwise a few pixels of
        // the detector's map and comes back with no text at all.
        val (width, height) = detectionSize(10, 12)

        assertEquals(960, maxOf(width, height))
        assertEquals(0, width % 32)
        assertEquals(0, height % 32)
    }

    // --- Recognition geometry -------------------------------------------------------------------

    @Test
    fun `a vertical line is turned a quarter turn counter-clockwise`() {
        // 2 wide, 3 tall: rows [1 2] [3 4] [5 6]
        val pixels = intArrayOf(1, 2, 3, 4, 5, 6)

        val rotated = PPOcrPreprocess.rotateCounterClockwise(pixels, 2, 3)

        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        // The bottom of the column becomes the left of the line, like cv2.ROTATE_90_COUNTERCLOCKWISE.
        assertTrue(rotated.pixels.contentEquals(intArrayOf(2, 4, 6, 1, 3, 5)))
    }

    @Test
    fun `the recogniser keeps 48 px of height and the crop aspect`() {
        assertEquals(48, PPOcrSpec.RECOGNITION_HEIGHT)
        assertEquals(96, recognitionWidth(400, 200))
        // Never below the minimum, or the model gets a sliver.
        assertEquals(16, recognitionWidth(10, 200))
    }

    @Test
    fun `vertical crops are rotated before being resized`() {
        val pixels = IntArray(20 * 100) { 0xFF000000.toInt() or 0x00FF00 }
        val prepared = PPOcrPreprocess.recognizerInput(pixels, 20, 100)

        assertEquals(PPOcrSpec.RECOGNITION_HEIGHT, prepared.height)
        // 20 wide, 100 tall -> rotated to 100 x 20 -> 48 tall keeps the 5:1 aspect.
        assertEquals(240, prepared.width)
    }

    @Test
    fun `the detector input is normalised with the ImageNet mean and std`() {
        // A pure white page: (1.0 - mean) / std per channel.
        val pixels = IntArray(32 * 32) { 0xFFFFFFFF.toInt() }
        val input = PPOcrPreprocess.detectorInput(pixels, 32, 32, 32, 32)
        val plane = 32 * 32

        assertEquals((1.0f - 0.485f) / 0.229f, input.get(0), 1e-4f)
        assertEquals((1.0f - 0.456f) / 0.224f, input.get(plane), 1e-4f)
        assertEquals((1.0f - 0.406f) / 0.225f, input.get(2 * plane), 1e-4f)
    }

    @Test
    fun `the recogniser input is normalised to minus one and one`() {
        val black = PPOcrPreprocess.recognizerInput(IntArray(10 * 50), 10, 50)
        val white = PPOcrPreprocess.recognizerInput(IntArray(10 * 50) { 0xFFFFFFFF.toInt() }, 10, 50)

        assertEquals(-1f, black.buffer.get(0), 1e-4f)
        assertEquals(1f, white.buffer.get(0), 1e-4f)
    }

    // --- CTC decoding ---------------------------------------------------------------------------

    @Test
    fun `repeated classes collapse and blanks are dropped`() {
        val vocabulary = listOf("blank", "あ", "い", "う", " ")

        assertEquals("あいう", PPOcrDecoding.decodeIndices(intArrayOf(1, 2, 2, 0, 3, 3, 0), vocabulary))
        // A character printed twice is still one glyph: the collapse is not optional.
        assertEquals("ああ", PPOcrDecoding.decodeIndices(intArrayOf(1, 0, 1), vocabulary))
    }

    @Test
    fun `decoding reads the best class at every step`() {
        val vocabulary = listOf("blank", "あ", "い")
        // T = 3, V = 3, flat
        val logits = floatArrayOf(
            0.1f, 0.9f, 0.0f, // あ
            0.1f, 0.9f, 0.0f, // あ (collapses with the previous step)
            0.9f, 0.0f, 0.1f, // blank
        )

        assertEquals("あ", PPOcrDecoding.decode(logits, vocabulary, classesPerStep = 3))
    }

    @Test
    fun `the vocabulary starts with blank and ends with a space, like the reference`() {
        val vocabulary = PPOcrDecoding.vocabulary(listOf("あ", "い\n"))

        assertEquals("blank", vocabulary.first())
        assertEquals("あ", vocabulary[1])
        assertEquals("い", vocabulary[2])
        assertEquals(" ", vocabulary.last())
    }

    // --- DB post-processing ---------------------------------------------------------------------

    @Test
    fun `one blob of confidence becomes one unclipped box`() {
        val map = FloatArray(20 * 20)
        // A 6x6 region at (6,6), well inside the map.
        for (y in 6 until 12) for (x in 6 until 12) map[y * 20 + x] = 0.9f

        val boxes = PPOcrPostprocess.boxes(map, 20, 20, 200, 200)

        assertEquals(1, boxes.size)
        val box = boxes.first()
        // Scaled x10 and grown by the unclip (area * 1.4 / perimeter = 2.1 map px = 21 image px),
        // but still around the blob.
        assertTrue("left ${box.left}", box.left in 30..50)
        assertTrue("right ${box.right}", box.right in 140..160)
        assertTrue("top ${box.top}", box.top in 30..50)
        assertTrue("bottom ${box.bottom}", box.bottom in 140..160)
        assertFalse("a square blob is not vertical text", box.vertical)
    }

    @Test
    fun `a faint blob below the box threshold is not text`() {
        val map = FloatArray(20 * 20)
        for (y in 6 until 12) for (x in 6 until 12) map[y * 20 + x] = 0.18f

        assertTrue(PPOcrPostprocess.boxes(map, 20, 20, 200, 200).isEmpty())
    }

    @Test
    fun `a single pixel is not a line`() {
        val map = FloatArray(20 * 20)
        map[10 * 20 + 10] = 0.9f

        assertTrue(PPOcrPostprocess.boxes(map, 20, 20, 200, 200).isEmpty())
    }

    @Test
    fun `two separate blobs are two boxes`() {
        val map = FloatArray(40 * 40)
        for (y in 5 until 11) for (x in 5 until 11) map[y * 40 + x] = 0.9f
        for (y in 25 until 31) for (x in 25 until 31) map[y * 40 + x] = 0.9f

        assertEquals(2, PPOcrPostprocess.boxes(map, 40, 40, 400, 400).size)
    }

    @Test
    fun `manga reading order is right to left, and a column stays above the next`() {
        val left = PPOcrLine("", 10, 10, 60, 400)
        val right = PPOcrLine("", 200, 10, 250, 400)
        val below = PPOcrLine("", 200, 500, 250, 900)

        val ordered = PPOcrPostprocess.readingOrder(listOf(left, right, below))

        assertEquals(listOf("", "", ""), ordered.map { it.text })
        assertEquals(200, ordered[0].left) // rightmost column of the first row
        assertEquals(10, ordered[1].left)
        assertEquals(500, ordered[2].top) // the next row comes after the whole first row
    }
}
