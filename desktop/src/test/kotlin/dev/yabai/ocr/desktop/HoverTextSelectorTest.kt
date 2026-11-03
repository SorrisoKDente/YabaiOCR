package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
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
    fun `snaps to the nearest word when the pointer is on punctuation`() {
        val line = LensSegment("日本語を", PixelBox(100, 100, 160, 40))
        val hit = HoverTextSelector.select(
            result(line),
            Point(250, 116), // character index 3: を, which has no entry here
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
}
