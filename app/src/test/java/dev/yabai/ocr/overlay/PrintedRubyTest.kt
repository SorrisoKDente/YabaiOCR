package dev.yabai.ocr.overlay

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R57: the ruby of the page picker must not double the furigana the page already prints.
 *
 * Both halves of the rule are wiring, not arithmetic: the printed stretches travel from the
 * recogniser into the picker's pieces, and the layout is told where the other text is. The
 * arithmetic itself is pinned by [PickerReadingLayoutTest] and by the core tests.
 */
class PrintedRubyTest {

    @Test
    fun `the mobile picker skips readings over printed furigana`() {
        val service = source("src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt")
        assertTrue(
            "the service draws every dictionary reading, including over the page's own furigana",
            service.contains("withoutPrintedReadings(piece.printed"),
        )

        val picker = source("src/main/java/dev/yabai/ocr/overlay/TextPickerOverlay.kt")
        assertTrue(
            "the picker draws ruby without knowing where the rest of the text is",
            picker.contains("blocked = occupied"),
        )

        val engine = source("src/main/java/dev/yabai/ocr/ocr/LensOcrEngine.kt")
        assertTrue(
            "normalizing the text can shift the printed offsets and skip the wrong reading",
            engine.contains("takeIf { pieceText.length == piece.text.length }"),
        )
    }

    /** The module's own sources, whether the test runs from the app folder or the repo root. */
    private fun source(path: String): String {
        val file = listOf(File(path), File("app/$path")).firstOrNull { it.exists() }
            ?: error("$path not found")
        return file.readText()
    }
}
