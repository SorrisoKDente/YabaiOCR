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
 *
 * The last three tests come from a second, real print (a balloon of five vertical columns a glyph
 * apart, in the N4 report), where every reading has a column on each side and used to be written
 * after the wrong one: the column it belongs to is the one it is printed *against*.
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
     * A balloon from a printed page: five columns of vertical text a few pixels apart, each reading
     * printed in the gutter to the right of the kanji it reads — which is also the gap between two
     * columns, so every reading has a line on *each* side that fits the rules. The line on the left
     * is the one that comes first in the sentence, and that is where readings used to be written: the
     * print came out `ネオ（き）の職員さ（しょくいん）んに聞い（ヒドー）たら非童（がいしゅつ）帝くん…`,
     * every reading after the wrong characters.
     *
     * `き` is squeezed right up against the previous column (one pixel into its box), and its own
     * column is five pixels away: what places it is the side ruby is printed on, not being closest to
     * it. The boxes are that balloon's proportions (columns of five to seven characters, a glyph
     * apart, readings a third of the thickness), in pixels of a capture — and with the fix undone
     * they reproduce the print's output character for character, which is what makes them worth
     * trusting as the geometry of the case.
     */
    @Test
    fun `a reading in a gutter belongs to the column it touches`() {
        val blocks = blocks(
            box(371, 400, 16, 205) to "しょくいん",
            box(325, 100, 45, 700) to "ネオの職員さん",
            box(310, 200, 16, 80) to "き",
            box(260, 100, 45, 500) to "に聞いたら",
            box(241, 100, 16, 300) to "ヒドー",
            box(195, 100, 45, 500) to "非童帝くん",
            box(176, 100, 16, 200) to "がいしゅつ",
            box(130, 100, 45, 600) to "外出したって",
            box(65, 100, 45, 500) to "いうから…",
            mode = FuriganaMode.PARENTHESES,
        )

        assertEquals(
            listOf(
                "ネオの職員（しょくいん）さん に聞（き）いたら 非童帝（ヒドー）くん " +
                    "外出（がいしゅつ）したって いうから…",
            ),
            blocks.map { it.text },
        )
    }

    /**
     * A reading is typeset fitted to the characters it reads, and "fitted" is a hair of a character:
     * five kana beside two kanji come back as a box a few percent longer than the two. That hair used
     * to be rounded up to a whole extra character — the same print had `職員さ（しょくいん）ん`.
     */
    @Test
    fun `a reading a hair longer than its characters keeps them`() {
        val blocks = blocks(
            // Eight characters of 100px each: 職員 is the first two, and the reading ends at 205px.
            box(0, 0, 45, 800) to "職員さんに聞いた",
            box(49, 0, 16, 205) to "しょくいん",
            mode = FuriganaMode.PARENTHESES,
        )

        assertEquals(listOf("職員（しょくいん）さんに聞いた"), blocks.map { it.text })
    }

    /**
     * The ruby side decides *which* line a reading belongs to, not whether it is a reading: a page
     * that prints its furigana on the other side is still read, exactly as before.
     */
    @Test
    fun `a reading printed on the other side is still a reading`() {
        val blocks = blocks(
            box(0, 0, 45, 600) to "職員室の職員",
            // Four pixels to the *left* of the column, over the last two characters.
            box(-20, 400, 16, 200) to "しょくいん",
            mode = FuriganaMode.PARENTHESES,
        )

        assertEquals(listOf("職員室の職員（しょくいん）"), blocks.map { it.text })
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
