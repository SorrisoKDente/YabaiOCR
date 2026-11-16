package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tray menu lost its colours on Windows: the rows carried emoji, which Swing paints in a single
 * ink colour. These tests pin the replacement — an icon drawn by the app for every row, in colour —
 * and the installer fix that lets an update replace the files of a running tray app.
 */
class TrayGlyphsTest {

    @Test
    fun `every tray row keeps its label without the emoji prefix`() {
        val labels = listOf(
            "\uD83D\uDDA5\uFE0F Capturar Tela Inteira (OCR)",
            "\u2702\uFE0F Capturar Área (OCR)",
            "\uD83D\uDD0D Busca Rápida de Dicionário",
            "\uD83D\uDCAC Modo Hover [Ativo]",
            "\uD83D\uDCD6 Gerenciar Dicionários",
            "\u2699\uFE0F Ajustes e Configurações",
            "\u2715 Sair do Yabai OCR",
        )
        assertEquals(
            listOf(
                "Capturar Tela Inteira (OCR)",
                "Capturar Área (OCR)",
                "Busca Rápida de Dicionário",
                "Modo Hover [Ativo]",
                "Gerenciar Dicionários",
                "Ajustes e Configurações",
                "Sair do Yabai OCR",
            ),
            labels.map(::withoutEmojiPrefix),
        )
    }

    @Test
    fun `a label that is already plain text is returned unchanged`() {
        assertEquals("Snippets", withoutEmojiPrefix("Snippets"))
        assertEquals("Trechos", withoutEmojiPrefix("  Trechos  "))
    }

    @Test
    fun `each glyph is painted with a visible colour of its own`() {
        val colours = TrayGlyph.values().map { glyph ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            TrayGlyphIcon(glyph).paintIcon(null, g, 0, 0)
            g.dispose()

            val pixels = (image.raster.dataBuffer as DataBufferInt).data
            val inked = pixels.filter { alphaOf(it) > 0 }
            assertTrue("$glyph painted almost nothing: ${inked.size} pixels", inked.size > 20)

            val reference = trayGlyphColor(glyph)
            // Antialiasing gives many shades; the untouched accent has to be there, and the ink has
            // to be chromatic, otherwise the row is the same grey silhouette as before.
            assertTrue(
                "$glyph does not use its accent ${Integer.toHexString(reference.rgb)}: ${inked.distinct()}",
                inked.any { it == reference.rgb },
            )
            val saturation = maxOf(reference.red, reference.green, reference.blue) -
                minOf(reference.red, reference.green, reference.blue)
            assertTrue("$glyph is too grey to read as a colour", saturation >= 40)
            reference.rgb
        }
        assertEquals("two rows share the same colour", colours.size, colours.distinct().size)
    }

    @Test
    fun `the glyph icon reports the size Swing asks for`() {
        val icon = TrayGlyphIcon(TrayGlyph.MONITOR)
        assertEquals(16, icon.iconWidth)
        assertEquals(16, icon.iconHeight)
    }

    @Test
    fun `the Windows installer terminates the tray app before replacing its files`() {
        val script = File("package/windows/installer.iss")
        assertTrue("installer script not found at ${script.absolutePath}", script.isFile)
        val text = script.readText()

        // The app hides its window instead of quitting, so Restart Manager alone never ends it.
        assertTrue(
            "Inno's polite close cannot end a tray app; it must not be relied on",
            text.contains("CloseApplications=no"),
        )
        assertTrue(
            "the installer has to end the running process before copying files",
            text.contains("taskkill.exe") && text.contains("/F"),
        )
        assertTrue(
            "the kill must run before the files are written, not after",
            text.contains("function PrepareToInstall("),
        )
    }

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    private val Color.rgb: Int get() = (red shl 16) or (green shl 8) or blue
}
