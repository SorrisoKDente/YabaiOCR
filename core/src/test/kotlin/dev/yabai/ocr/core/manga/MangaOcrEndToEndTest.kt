package dev.yabai.ocr.core.manga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File

/**
 * The whole manga-ocr path — detector, recogniser and tokenizer — on the real ONNX files.
 *
 * Opt-in because the models are 210 MB and are downloaded by the app, not by the repository:
 *
 * ```powershell
 * .\gradlew.bat :core:test -Pmanga="C:\caminho\com\os\tres\modelos" --tests "*MangaOcrEndToEndTest*"
 * ```
 *
 * The folder must hold `comic_text_detector.onnx`, `manga_ocr.onnx` and `vocab.txt`, the three files
 * the app downloads. What it proves is the pair of contracts that was wrong: the detector's `blk`
 * output really does describe the lines, and the recogniser really does need the decode loop — with
 * either mistake on its own this reads nothing at all.
 */
class MangaOcrEndToEndTest {

    private val modelDir: File? =
        System.getProperty("yabai.manga")?.takeIf { it.isNotBlank() }?.let { File(it) }

    @Test
    fun `reads a drawn page with vertical and horizontal text`() {
        assumeTrue("set -Pmanga=<dir> to enable", modelDir != null)
        val store = MangaOcrModelStore(modelDir!!)
        assumeTrue("model files not found in $modelDir", store.isAvailable())

        val page = drawPage()
        val width = page.width
        val height = page.height
        val pixels = page.getRGB(0, 0, width, height, null, 0, width)

        val outcome = MangaOcrCoreEngine(store.detectorFile, store.recognizerFile, store.vocabFile).use {
            it.recognizePixels(pixels, width, height)
        }

        println("manga-ocr em ${outcome.elapsedMs} ms")
        println("texto: ${outcome.text.replace("\n", " / ")}")
        outcome.blocks.forEach { block ->
            println("caixa ${block.left},${block.top}-${block.right},${block.bottom}: ${block.text}")
        }

        assertTrue("nothing was recognised", outcome.text.isNotBlank())
        // The text is the data: both balloons have to come back as they were printed, not as
        // something that merely looks like reading. Feeding the detector's coordinates to the
        // heatmap path produced `はは`, `立目千` and `7人一刀` for these two lines.
        assertTrue("vertical balloon: ${outcome.text}", outcome.text.contains("これはテストです"))
        assertTrue("horizontal balloon: ${outcome.text}", outcome.text.contains("テストの文章です"))
        assertFalse("a 2-block page came back as one paragraph", outcome.blocks.size < 2)
        assertTrue(
            "a box escaped the page",
            outcome.blocks.all { it.left >= 0 && it.top >= 0 && it.right <= width && it.bottom <= height },
        )
        assertTrue(
            "a box covers the whole page, so the detector found no line",
            outcome.blocks.all { (it.right - it.left) * (it.bottom - it.top) < width * height / 2 },
        )
    }

    /**
     * A page with two speech balloons: a vertical column on the right, a horizontal line on the left.
     *
     * Balloons and not bare text, because that is what the detector was trained on: text printed on
     * an empty white page is not a manga block to it, and it scores it below its own threshold.
     */
    private fun drawPage(): BufferedImage {
        val width = 900
        val height = 1400
        val page = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = page.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color(0xE0, 0xE0, 0xE0)
            g.fillRect(0, 0, width, height)

            // Vertical balloon, top right.
            g.color = Color.WHITE
            g.fillOval(650, 60, 180, 600)
            g.color = Color.BLACK
            g.drawOval(650, 60, 180, 600)
            g.font = japaneseFont(46)
            var y = 150
            for (character in "これはテストです") {
                g.drawString(character.toString(), 715, y)
                y += 58
            }

            // Horizontal balloon, bottom left.
            g.color = Color.WHITE
            g.fillOval(60, 820, 520, 170)
            g.color = Color.BLACK
            g.drawOval(60, 820, 520, 170)
            g.font = japaneseFont(48)
            g.drawString("テストの文章です", 110, 920)
        } finally {
            g.dispose()
        }
        return page
    }

    /** A font that actually has kana, or the page would be drawn as empty boxes. */
    private fun japaneseFont(size: Int): Font {
        val wanted = listOf(
            "Yu Gothic", "Yu Gothic UI", "Meiryo", "Meiryo UI", "MS Gothic", "MS PGothic",
            "Noto Sans CJK JP", "Hiragino Sans",
        )
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val family = wanted.firstOrNull { it in available } ?: Font.SANS_SERIF
        return Font(family, Font.PLAIN, size)
    }
}
