package dev.yabai.ocr.core.lens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream

/**
 * Furigana, the small kana a manga prints beside a kanji to give its reading.
 *
 * Lens reports a reading as a line of its own, so without this the sentence comes out as
 * `しょくいん職員さんに聞いた`: the reading glued to the word it explains. These tests hold down both
 * ends of the fix — a reading is recognised, and a column of text that merely happens to be smaller
 * than its neighbour is left alone.
 *
 * The main fixture is a **recorded response from the live endpoint** for `test_furigana.png`, a page
 * drawn for this purpose (vertical and horizontal text, with and without furigana). Recording it is
 * what turned "a reading is probably about half the glyph size" into numbers: a vertical reading came
 * back as a 16×92 box 8px from a 45×375 column, a horizontal one as 89×17 16px above a 185×41 row.
 */
class FuriganaTest {

    @Test
    fun `a reading is not part of the sentence`() {
        val built = fixture(FuriganaMode.REMOVE)

        assertEquals(
            listOf(
                "職員さんに聞いた",
                "もう死んでいる",
                "職員さん 外出したって",
            ),
            built.blocks.map { it.text },
        )
    }

    @Test
    fun `a reading can be kept where it was printed`() {
        val built = fixture(FuriganaMode.PARENTHESES)

        // The reading belongs after the characters it covers, not at the start of the column: the
        // vertical しょくいん reads 職員, and so does the horizontal one.
        assertEquals(
            listOf(
                "職員（しょくいん）さんに聞いた",
                "もう死んでいる",
                "職員（しょくいん）さん 外出したって",
            ),
            built.blocks.map { it.text },
        )
    }

    @Test
    fun `a line without furigana is untouched in both modes`() {
        for (mode in FuriganaMode.entries) {
            val built = fixture(mode)
            assertTrue(
                "$mode changed a line that has no reading beside it",
                built.blocks.any { it.text == "もう死んでいる" },
            )
        }
    }

    /**
     * The raw view of what the recogniser saw keeps the reading: the panel offers it as the "texto
     * cru", and a cleaning step that also cleans it would be lying about the OCR.
     */
    @Test
    fun `the raw lines still show the reading`() {
        val built = fixture(FuriganaMode.REMOVE)

        assertEquals(
            listOf("しょくいん", "職員さんに聞いた", "もう死んでいる", "しょくいん", "職員さん", "外出したって"),
            built.segments.map { it.text },
        )
        assertEquals(
            listOf("しょくいん", "職員さんに聞いた"),
            built.blocks.first().lines,
        )
    }

    /** The recording and the page it was made from have to stay a pair. */
    @Test
    fun `the recording has the page it came from`() {
        val png = javaClass.classLoader!!.getResourceAsStream("test_furigana.png")!!.use { it.readBytes() }
        val image = ImageIO.read(ByteArrayInputStream(png))

        assertEquals(800, image.width)
        assertEquals(1000, image.height)
    }

    /**
     * Two kana-only columns of one balloon, as a live response reports them: `もう` at 29px and
     * `死んでいる` at 37px. Same size (78%), 17px apart — a column of its own, not a reading.
     */
    @Test
    fun `a kana column of its own is not a reading`() {
        val blocks = blocks(
            box(304, 227, 29, 81) to "もう",
            box(250, 229, 37, 203) to "死んでいる",
        )

        assertEquals(listOf("もう 死んでいる"), blocks.map { it.text })
    }

    /** A small line with no kanji to explain is just a small line. */
    @Test
    fun `a small kana line with nothing to explain keeps its kana`() {
        val blocks = blocks(
            box(100, 100, 16, 90) to "しょくいん",
            box(600, 100, 45, 375) to "職員さんに聞いた",
        )

        assertEquals(listOf("しょくいん", "職員さんに聞いた"), blocks.map { it.text })
    }

    /**
     * A reading is printed *touching* the kanji it reads; the same small kana a gutter away is a
     * column of another balloon, and a balloon is not a reading.
     */
    @Test
    fun `a small kana line across a gutter is not a reading`() {
        val blocks = blocks(
            box(0, 0, 45, 600) to "職員室の職員",
            // Thin enough (16 of 45), over the characters it would read — but 155px away.
            box(200, 100, 16, 300) to "しょくいんしつ",
        )

        assertEquals(listOf("職員室の職員", "しょくいんしつ"), blocks.map { it.text })
    }

    /** Two readings on one line, each written after the characters it is printed over. */
    @Test
    fun `each reading lands after its own characters`() {
        val blocks = blocks(
            // Six characters of 100px each, so 職員室 is the first three and 職員 the last two.
            box(0, 0, 45, 600) to "職員室の職員",
            box(49, 0, 16, 300) to "しょくいんしつ",
            box(49, 400, 16, 200) to "しょくいん",
            mode = FuriganaMode.PARENTHESES,
        )

        assertEquals(
            listOf("職員室（しょくいんしつ）の職員（しょくいん）"),
            blocks.map { it.text },
        )
    }

    /** A line with no box cannot be placed, so it is neither a reading nor a place to put one. */
    @Test
    fun `a line without a box is left alone`() {
        val blocks = blocks(
            box(0, 0, 45, 400) to "職員さんに聞いた",
            null to "しょくいん",
        )

        assertEquals(listOf("職員さんに聞いた", "しょくいん"), blocks.map { it.text })
    }

    /**
     * The position is a number of *characters*, and a kanji outside the basic plane is two Java
     * `char`s: counting them instead would split the pair and write the reading inside the kanji.
     */
    @Test
    fun `a reading after a kanji outside the basic plane is not written inside it`() {
        val blocks = blocks(
            // 𠮟 is U+20B9F, i.e. the two UTF-16 units \uD840\uDC1F.
            box(0, 0, 45, 300) to "職員𠮟",
            box(49, 200, 16, 100) to "しかる",
            mode = FuriganaMode.PARENTHESES,
        )

        assertEquals(listOf("職員𠮟（しかる）"), blocks.map { it.text })
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun fixture(mode: FuriganaMode): LensResultBuilder.Built {
        val bytes = javaClass.classLoader!!.getResourceAsStream("lens_furigana.bin")!!.use { it.readBytes() }
        val parsed = LensProto.parseResponse(bytes, 800, 1000)
        return LensResultBuilder.build(parsed, 800, 1000, mode)
    }

    private fun blocks(
        vararg lines: Pair<PixelBox?, String>,
        mode: FuriganaMode = FuriganaMode.REMOVE,
    ): List<LensBlock> = LensResultBuilder.groupIntoBlocks(
        lines.map { LensResultBuilder.Line(it.second, it.first) },
        mode,
    )

    private fun box(x: Int, y: Int, width: Int, height: Int) = PixelBox(x, y, width, height)
}
