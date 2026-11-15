package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.lens.LensBlock
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.core.lens.LensSegment
import dev.yabai.ocr.core.lens.PixelBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.awt.Point

class HoverTextSelectorTest {

    private fun match(
        surface: String,
        start: Int,
        end: Int,
        term: String = surface,
    ) = TokenMatch(
        surface = surface,
        start = start,
        end = end,
        entry = DictionaryEntry(
            term = term,
            reading = "よみ",
            tags = "n",
            rulesFlags = 0,
            score = 0,
            definition = "definição de $term",
        ),
        dictionaryTitle = "Teste",
        reasons = emptyList(),
    )

    private fun result(vararg segments: LensSegment) =
        LensResult(language = "ja", segments = segments.toList(), blocks = emptyList(), elapsedMs = 1)

    /** A result whose [pieces] are all one block, in the order they are given (reading order). */
    private fun blocked(vararg pieces: LensSegment) = LensResult(
        language = "ja",
        segments = pieces.toList(),
        blocks = listOf(
            LensBlock(
                text = pieces.joinToString("") { it.text },
                lines = pieces.map { it.text },
                box = null,
                pieces = pieces.toList(),
            ),
        ),
        elapsedMs = 1,
    )

    /** Two blocks that must never be glued: different balloons are different sentences. */
    private fun twoBlocks(first: List<LensSegment>, second: List<LensSegment>) = LensResult(
        language = "ja",
        segments = first + second,
        blocks = listOf(first, second).map { pieces ->
            LensBlock(
                text = pieces.joinToString("") { it.text },
                lines = pieces.map { it.text },
                box = null,
                pieces = pieces,
            )
        },
        elapsedMs = 1,
    )

    private fun joinedOnly(term: String, start: Int, end: Int) = { text: String, _: Set<String> ->
        if (text == term) listOf(match(term.substring(start, end), start, end)) else emptyList()
    }

    private val text = "日本語を勉強します"

    private val matches = listOf(
        match("日本語", 0, 3),
        match("を", 3, 4),
        match("勉強", 4, 6),
        match("します", 6, 9),
    )

    @Test
    fun `picks the word under the pointer in horizontal text`() {
        val line = LensSegment(text, PixelBox(100, 100, 360, 40))
        val hit = HoverTextSelector.select(
            result(line),
            Point(280, 116), // 4th character: 勉
            lookup = { _, _ -> matches },
        )

        assertNotNull(hit)
        assertEquals("勉強", hit!!.match.surface)
        // 9 characters in 360 px: 勉強 occupies characters 4 and 5.
        assertEquals(PixelBox(260, 100, 80, 40), hit.tokenBox)
    }

    @Test
    fun `picks the word under the pointer in vertical text`() {
        val line = LensSegment("日本語", PixelBox(200, 100, 40, 240))
        val hit = HoverTextSelector.select(
            result(line),
            Point(220, 300), // 3rd character: 語
            lookup = { _, _ -> listOf(match("日本", 0, 2), match("語", 2, 3)) },
        )

        assertNotNull(hit)
        assertEquals("語", hit!!.match.surface)
        assertEquals(PixelBox(200, 260, 40, 80), hit.tokenBox)
    }

    @Test
    fun `the character right after a word belongs to the next word`() {
        val line = LensSegment("日本語を", PixelBox(100, 100, 160, 40))
        val hit = HoverTextSelector.select(
            result(line),
            Point(250, 116), // character index 3: を
            lookup = { _, _ -> listOf(match("日本語", 0, 3), match("を", 3, 4)) },
        )

        assertNotNull(hit)
        assertEquals("を", hit!!.match.surface)
    }

    @Test
    fun `a line with an English prefix still finds the Japanese word under the pointer`() {
        // "abc昭和" in a 365 px box: every weight unit is exactly 100 px (a,b,c = 55 each).
        val text = "abc昭和"
        val box = PixelBox(0, 0, 365, 40)

        // 200 px is over 昭. Counting all five characters as equal squares would answer 'c'.
        assertEquals(3, HoverTextSelector.characterIndex(text, box, Point(200, 20)))
        assertEquals(PixelBox(165, 0, 200, 40), HoverTextSelector.tokenBox(text, box, 3, 5))
    }

    @Test
    fun `picks the Japanese word of a mixed line`() {
        val line = LensSegment("Inflection information: 走った", PixelBox(0, 0, 730, 40))
        val hit = HoverTextSelector.select(
            result(line),
            Point(580, 20), // over 走: the Japanese part is the last fifth of the line
            lookup = { _, _ -> listOf(match("走った", 24, 27)) },
        )

        assertNotNull(hit)
        assertEquals("走った", hit!!.match.surface)
    }

    @Test
    fun `snaps to the nearest word when the pointer is on punctuation`() {
        // Punctuation cannot start a word, so the word next to it is the answer. Kanji and kana can,
        // and there the card must stay honest instead of snapping — see the novels regressions below.
        val line = LensSegment("日本語、", PixelBox(100, 100, 160, 40))
        val hit = HoverTextSelector.select(
            result(line),
            Point(250, 116), // character index 3: 、, which has no entry of its own
            lookup = { _, _ -> listOf(match("日本語", 0, 3)) },
        )

        assertNotNull(hit)
        assertEquals("日本語", hit!!.match.surface)
    }

    @Test
    fun `chooses the nearest line, not the first one in the OCR order`() {
        val far = LensSegment("日本語", PixelBox(0, 0, 100, 20))
        val near = LensSegment("語学", PixelBox(300, 0, 100, 20))
        val hit = HoverTextSelector.select(
            result(far, near),
            Point(320, 10),
            lookup = { line, _ ->
                if (line == "語学") listOf(match("語学", 0, 2)) else emptyList()
            },
        )

        assertNotNull(hit)
        assertEquals("語学", hit!!.match.surface)
    }

    @Test
    fun `a line without entries does not swallow a nearby line that has one`() {
        val empty = LensSegment("abc", PixelBox(100, 100, 100, 20))
        val filled = LensSegment("日本語", PixelBox(100, 130, 100, 20))
        val hit = HoverTextSelector.select(
            result(empty, filled),
            Point(110, 108),
            lookup = { line, _ ->
                if (line == "日本語") listOf(match("日本語", 0, 3)) else emptyList()
            },
        )

        assertNotNull(hit)
        assertEquals("日本語", hit!!.match.surface)
    }

    @Test
    fun `answers nothing when the pointer is far from every line`() {
        val line = LensSegment("日本語", PixelBox(0, 0, 100, 20))
        val hit = HoverTextSelector.select(
            result(line),
            Point(500, 500),
            lookup = { _, _ -> matches },
        )

        assertNull(hit)
    }

    @Test
    fun `character index is clamped to the line`() {
        val box = PixelBox(100, 100, 160, 40)
        assertEquals(0, HoverTextSelector.characterIndex("日本語を", box, Point(0, 0)))
        assertEquals(3, HoverTextSelector.characterIndex("日本語を", box, Point(9999, 9999)))
    }

    // --- words split between two lines (D56) ------------------------------------------------

    /**
     * A manga line often ends in the middle of a word: 「一生懸」 above and 「命」 below. Until this
     * round the hover read one line at a time, so neither half was in the dictionary and the card
     * stayed empty on the most important word of the page.
     */
    @Test
    fun `a word split between two horizontal lines is read across them`() {
        val first = LensSegment("一生懸", PixelBox(100, 100, 240, 40))
        val second = LensSegment("命", PixelBox(100, 160, 80, 40))
        val hit = HoverTextSelector.select(
            blocked(first, second),
            Point(120, 176), // the only character of the second line
            lookup = joinedOnly("一生懸命", 0, 4),
        )

        assertNotNull("the word that crosses the lines was not recognised", hit)
        assertEquals("一生懸命", hit!!.match.surface)
        // The highlight covers both printed halves, not just the line under the pointer.
        assertEquals(PixelBox(100, 100, 240, 100), hit.tokenBox)
    }

    /**
     * Vertical Japanese moves from the right column to the one on its left, so «the next line» is a
     * column at the side — the reason the neighbour comes from the reading order and not from "the
     * line below the pointer".
     */
    @Test
    fun `a word split between two columns is read across them`() {
        val right = LensSegment("一生懸", PixelBox(200, 100, 40, 240))
        val left = LensSegment("命", PixelBox(160, 100, 40, 80))
        val hit = HoverTextSelector.select(
            blocked(right, left),
            Point(180, 116), // first character of the left column
            lookup = joinedOnly("一生懸命", 0, 4),
        )

        assertNotNull("the word that crosses the columns was not recognised", hit)
        assertEquals("一生懸命", hit!!.match.surface)
        assertEquals(160, hit.tokenBox.x)
        assertEquals(240, hit.tokenBox.right)
    }

    /** Two balloons are two sentences: their lines are never glued into one candidate. */
    @Test
    fun `lines of different blocks are never joined`() {
        val first = LensSegment("一生懸", PixelBox(100, 100, 240, 40))
        val second = LensSegment("命", PixelBox(100, 300, 80, 40))
        val hit = HoverTextSelector.select(
            twoBlocks(listOf(first), listOf(second)),
            Point(120, 316),
            lookup = joinedOnly("一生懸命", 0, 4),
        )

        assertNull("lines from two balloons were glued together", hit)
    }

    /** A candidate whose token ends where the line ends is the line's own word, not a crossing. */
    @Test
    fun `a token that does not cross the line boundary is rejected`() {
        val first = LensSegment("一生懸", PixelBox(100, 100, 240, 40))
        val second = LensSegment("命", PixelBox(100, 160, 80, 40))
        val hit = HoverTextSelector.select(
            blocked(first, second),
            Point(120, 176),
            // The same joined string, but the hit starts exactly on the boundary: nothing crosses.
            lookup = joinedOnly("一生懸命", 3, 4),
        )

        assertNull("a word contained in the line itself was taken for a crossing", hit)
    }

    /** The pointer does not have to be near an edge when the word is complete in the line. */
    @Test
    fun `a complete word wins over a short crossing`() {
        val line = LensSegment("日本語を", PixelBox(100, 100, 160, 40))
        val next = LensSegment("読む", PixelBox(100, 160, 80, 40))
        val hit = HoverTextSelector.select(
            blocked(line, next),
            Point(140, 116), // last character of the line: the tail edge, where joining is tried
            lookup = { text, _ ->
                when (text) {
                    "日本語を" -> listOf(match("日本語", 0, 3))
                    // Crosses the boundary but is shorter than the word the line has.
                    "日本語を読む" -> listOf(match("を読", 3, 5))
                    else -> emptyList()
                }
            },
        )

        assertNotNull(hit)
        assertEquals("日本語", hit!!.match.surface)
    }

    // --- answering the wrong word (reported on novels) -----------------------------------------

    /**
     * Regression: pointing at 納 in 「…捉えて納」 answered 捉えて, the word three characters above.
     * When the line has entries and none of them covers the character, the pointer was snapping to
     * the nearest word — and a kanji at the end of a column is exactly where a word continues in the
     * next column, so the answer was always the neighbour.
     */
    @Test
    fun `a kanji with no entry of its own does not answer with the word above it`() {
        val line = LensSegment("指先が捉えて納", PixelBox(100, 100, 280, 40))
        val hit = HoverTextSelector.select(
            result(line),
            Point(360, 116), // the last character: 納
            lookup = { _, _ -> listOf(match("捉えて", 3, 6)) },
        )

        assertNull("the card answered the word above the pointer", hit)
    }

    /**
     * Regression: the pointer was inside a line that had entries, none covered the character, and
     * the search went on to the next candidate — a column several lines away. The card then showed
     * a word the user was not looking at.
     */
    @Test
    fun `a line with entries does not answer from another line`() {
        val pointed = LensSegment("振り返", PixelBox(100, 100, 120, 40))
        val other = LensSegment("を", PixelBox(100, 160, 40, 40))
        val hit = HoverTextSelector.select(
            result(pointed, other),
            Point(110, 116), // 振, whose word continues in the next column
            lookup = { line, _ ->
                if (line == "振り返") listOf(match("返", 2, 3)) else listOf(match("を", 0, 1))
            },
        )

        assertNull("the card answered a word from another line", hit)
    }

    /**
     * Regression: the feature did nothing in novels, where Lens reports each column as its own
     * block. Adjacency is decided by the printed boxes, so two columns of the same sentence join
     * even when the OCR split them into separate blocks.
     */
    @Test
    fun `adjacent columns in different blocks still join`() {
        val right = LensSegment("納", PixelBox(200, 100, 40, 80))
        val left = LensSegment("得した", PixelBox(160, 100, 40, 120))
        val hit = HoverTextSelector.select(
            twoBlocks(listOf(right), listOf(left)),
            Point(220, 116), // the only character of the right column
            lookup = joinedOnly("納得した", 0, 2),
        )

        assertNotNull("columns of one sentence in separate blocks were not joined", hit)
        assertEquals("納得", hit!!.match.surface)
        assertEquals(160, hit.tokenBox.x)
        assertEquals(240, hit.tokenBox.right)
    }

    /**
     * Regression: Lens reports the columns of a vertical page in its own arrangement, not in the
     * order they are read, and with every column as its own block there is no block order to fall
     * back on either. The raw order put the column on the *left* after the pointed one, so «the next
     * line» was the column already read and no word ever crossed — the feature looked dead in
     * novels even though the boxes are adjacent. The order comes from the geometry now.
     */
    @Test
    fun `columns reported out of reading order are still joined`() {
        val left = LensSegment("得した", PixelBox(160, 100, 40, 120))
        val right = LensSegment("納", PixelBox(200, 100, 40, 80))
        // Reported left first; read right (納) and then left (得した).
        val hit = HoverTextSelector.select(
            result(left, right),
            Point(220, 116), // the only character of the right column
            lookup = joinedOnly("納得した", 0, 2),
        )

        assertNotNull("the reading order of the reported columns was trusted", hit)
        assertEquals("納得", hit!!.match.surface)
    }
}
