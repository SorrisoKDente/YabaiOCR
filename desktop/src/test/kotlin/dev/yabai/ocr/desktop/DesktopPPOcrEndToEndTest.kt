package dev.yabai.ocr.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File

/**
 * The desktop offline engine on the real models, through the settings the app really uses.
 *
 * Opt-in because the files are downloaded from the settings window, not shipped:
 *
 * ```powershell
 * .\gradlew.bat :desktop:test -Pppocr="C:\caminho\da\casa" --tests "*DesktopPPOcrEndToEndTest*"
 * ```
 *
 * `home/ppocr_v6` must hold `manga_det.onnx`, `manga_rec.onnx` and `ppocrv6_dict.txt`.
 *
 * It proves the wiring the desktop needs: the chosen engine, the model folder under the user's
 * settings home, and the answer arriving as the result shape the windows already read.
 */
class DesktopPPOcrEndToEndTest {

    private val home: File? =
        System.getProperty("yabai.ppocr")?.takeIf { it.isNotBlank() }?.let { File(it) }

    @Test
    fun `reads a drawn page through the desktop settings`() {
        assumeTrue("set -Pppocr=<home> to enable", home != null)
        val settings = DesktopSettings(File(home!!, "settings.properties"), home)
        assumeTrue("models not installed in ${settings.ppOcrDir}", settings.ppOcrDir.isDirectory)

        settings.engine = DesktopEngineChoice.PP_OCR
        val page = drawPage()

        val answer = DesktopOcr.recognize(page, settings)

        println("engine: ${answer.engine}, ${answer.result.elapsedMs} ms")
        println("texto: ${answer.result.joinLines().replace("\n", " / ")}")
        answer.result.blocks.forEach { println("caixa ${it.box}: ${it.text}") }

        assertTrue("nothing was recognised", answer.result.joinLines().isNotBlank())
        assertTrue("vertical balloon: ${answer.result.joinLines()}", answer.result.joinLines().contains("これはテストです"))
        assertTrue("horizontal balloon: ${answer.result.joinLines()}", answer.result.joinLines().contains("テストの文章です"))
        assertFalse("no blocks to pick from", answer.result.blocks.size < 2)
        assertTrue("the label must name the engine", DesktopOcr.label(answer.engine).contains("PP-OCR"))
    }

    private fun drawPage(): BufferedImage {
        val width = 900
        val height = 1400
        val page = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = page.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color(0xE0, 0xE0, 0xE0)
            g.fillRect(0, 0, width, height)
            val family = japaneseFamily()

            g.color = Color.WHITE
            g.fillOval(650, 60, 180, 600)
            g.color = Color.BLACK
            g.drawOval(650, 60, 180, 600)
            g.font = Font(family, Font.PLAIN, 46)
            var y = 150
            for (character in "これはテストです") {
                g.drawString(character.toString(), 715, y)
                y += 58
            }

            g.color = Color.WHITE
            g.fillOval(60, 820, 520, 170)
            g.color = Color.BLACK
            g.drawOval(60, 820, 520, 170)
            g.font = Font(family, Font.PLAIN, 48)
            g.drawString("テストの文章です", 110, 920)
        } finally {
            g.dispose()
        }
        return page
    }

    private fun japaneseFamily(): String {
        val wanted = listOf(
            "Yu Gothic", "Yu Gothic UI", "Meiryo", "Meiryo UI", "MS Gothic", "MS PGothic",
            "Noto Sans CJK JP", "Hiragino Sans",
        )
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        return wanted.firstOrNull { it in available } ?: Font.SANS_SERIF
    }
}
