package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hover card is the only transparent window of the desktop, and that transparency has to come
 * from Compose alone.
 *
 * `transparent = true` makes Compose ask Skiko which AWT background the renderer in use needs. On
 * Windows that answer is a transparent colour for Direct3D and `null` for OpenGL and Software: those
 * two make the window see-through with a DWM call (`DwmEnableBlurBehindWindow`) instead, and an AWT
 * background set from application code undoes that call — the card then draws nothing at all, while
 * the OCR keeps logging the word it found. Setting `window.background` was exactly that line, so the
 * guard here reads the source: drawing needs a display, which a unit test does not have (the same
 * reason `ScrollbarCoverageTest` reads sources).
 */
class HoverCardTransparencyTest {

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")

    private fun tooltipSource(): String = sourceFile("HoverTooltipWindow.kt").readText()

    @Test
    fun `the card is a transparent window`() {
        assertTrue(
            "the hover card must ask for a transparent window, or it draws an opaque rectangle",
            Regex("""transparent\s*=\s*true""").containsMatchIn(tooltipSource()),
        )
    }

    @Test
    fun `the card does not override the window background Compose chose`() {
        val source = tooltipSource()
        assertFalse(
            "HoverTooltipWindow.kt sets a window background: under OpenGL and Software that undoes " +
                "the DWM transparency and the card draws nothing",
            Regex("""\bwindow\s*\.\s*background\s*=""").containsMatchIn(source),
        )
    }
}
