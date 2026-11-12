package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.ppocr.PPOcrBlock
import dev.yabai.ocr.core.ppocr.PPOcrLine
import dev.yabai.ocr.core.ppocr.PPOcrOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which engine the desktop runs — and in which order.
 *
 * The case that made this worth a file: the windows offered «Automático», «Somente Google Lens» and
 * «Somente Manga-OCR», and every capture went to Lens anyway. The setting existed, the priority list
 * existed, and nothing read either of them.
 */
class DesktopOcrOrderTest {

    @Test
    fun `a fixed engine runs alone`() {
        assertEquals(
            listOf(DesktopEngineChoice.LENS),
            DesktopOcr.engineOrder(DesktopEngineChoice.LENS, listOf(DesktopEngineChoice.PP_OCR)),
        )
        assertEquals(
            listOf(DesktopEngineChoice.PP_OCR),
            DesktopOcr.engineOrder(DesktopEngineChoice.PP_OCR, listOf(DesktopEngineChoice.LENS)),
        )
    }

    @Test
    fun `automatic mode follows the priority list`() {
        assertEquals(
            listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR),
            DesktopOcr.engineOrder(
                DesktopEngineChoice.AUTO,
                listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR),
            ),
        )
        assertEquals(
            listOf(DesktopEngineChoice.PP_OCR, DesktopEngineChoice.LENS),
            DesktopOcr.engineOrder(
                DesktopEngineChoice.AUTO,
                listOf(DesktopEngineChoice.PP_OCR, DesktopEngineChoice.LENS),
            ),
        )
    }

    @Test
    fun `automatic mode still has an engine when the list is empty or nonsense`() {
        assertEquals(
            listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR),
            DesktopOcr.engineOrder(DesktopEngineChoice.AUTO, emptyList()),
        )
        assertEquals(
            listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR),
            DesktopOcr.engineOrder(DesktopEngineChoice.AUTO, listOf(DesktopEngineChoice.AUTO, DesktopEngineChoice.AUTO)),
        )
    }

    @Test
    fun `the same engine is not tried twice`() {
        assertEquals(
            listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR),
            DesktopOcr.engineOrder(
                DesktopEngineChoice.AUTO,
                listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR),
            ),
        )
    }

    @Test
    fun `the label says which engine read the page`() {
        assertEquals("Google Lens", DesktopOcr.label(DesktopEngineChoice.LENS))
        assertEquals("PP-OCRv6 (offline)", DesktopOcr.label(DesktopEngineChoice.PP_OCR))
    }

    @Test
    fun `a pp-ocr answer becomes the shape the desktop reads`() {
        val outcome = PPOcrOutcome(
            text = "これはテストです\nテストの文章です",
            lines = listOf("これはテストです", "テストの文章です"),
            elapsedMs = 1200,
            blocks = listOf(
                PPOcrBlock(
                    text = "これはテストです",
                    lines = listOf("これは", "テストです"),
                    left = 700,
                    top = 100,
                    right = 740,
                    bottom = 560,
                    pieces = listOf(
                        PPOcrLine("これは", 700, 100, 740, 300),
                        PPOcrLine("テストです", 700, 300, 740, 560),
                    ),
                ),
                PPOcrBlock(
                    text = "テストの文章です",
                    lines = listOf("テストの文章です"),
                    left = 100,
                    top = 860,
                    right = 500,
                    bottom = 920,
                    pieces = listOf(PPOcrLine("テストの文章です", 100, 860, 500, 920)),
                ),
            ),
        )

        val result = DesktopPPOcr.toLensResult(outcome)

        assertEquals(2, result.blocks.size)
        assertEquals(700, result.blocks.first().box?.x)
        assertEquals(40, result.blocks.first().box?.width)
        // The columns of the balloon are the segments, so hover and the panel keep their boxes.
        assertEquals(3, result.segments.size)
        assertEquals("これは", result.segments.first().text)
        assertEquals(100, result.segments.first().box?.y)
        assertEquals("テストの文章です", result.segments.last().text)
        // A block without pieces is still one segment, not a sentence with nowhere to point at.
        assertEquals(3, result.segments.size)
        assertTrue(result.joinLines().contains("テストの文章です"))
    }
}
