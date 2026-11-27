package dev.yabai.ocr.ocr

import android.content.Context
import android.graphics.Bitmap
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.EngineChoice
import dev.yabai.ocr.UsageTracker
import dev.yabai.ocr.core.lens.PrintedReading
import dev.yabai.ocr.core.ppocr.PPOcrBlock
import dev.yabai.ocr.core.ppocr.PPOcrLine
import dev.yabai.ocr.core.ppocr.PPOcrOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Android dispatcher on the PP-OCR path.
 *
 * The engine itself is the `:core` one, already covered end-to-end with the real models on the JVM
 * (`:desktop:test -Pppocr`, same classes). What is Android-specific — and what this checks — is that
 * the choice in the settings reaches the engine, and that a missing download says so instead of
 * quietly handing the image to Lens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PpOcrDispatchTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `pp-ocr chosen without models reports the missing download`() = runBlocking {
        val settings = AppSettings(context)
        settings.engine = EngineChoice.PP_OCR
        val dispatcher = OcrDispatcher(settings, UsageTracker(context))
        try {
            val bitmap = Bitmap.createBitmap(40, 120, Bitmap.Config.ARGB_8888)
            val outcome = dispatcher.recognize(bitmap)

            assertTrue("no text without models: ${outcome.text}", outcome.text.isBlank())
            assertEquals("PP-OCRv6 (Offline)", outcome.engineLabel)
            assertTrue("warning: ${outcome.warning}", outcome.warning.orEmpty().contains("PP-OCRv6"))
            assertTrue("Lens must not be used here", outcome.engineLabel != "Lens")
        } finally {
            dispatcher.close()
        }
    }

    /**
     * R64: the offline engine reports the furigana it finds as a line of its own. The dispatcher
     * must hand those stretches to the picker; dropping them here brings the doubled ruby back.
     */
    @Test
    fun `the pp-ocr answer keeps the printed furigana of its pieces`() {
        val outcome = PPOcrOutcome(
            text = "職員さんに聞いた",
            lines = listOf("職員さんに聞いた", "しょくいん"),
            elapsedMs = 0,
            blocks = listOf(
                PPOcrBlock(
                    text = "職員さんに聞いた",
                    lines = listOf("職員さんに聞いた", "しょくいん"),
                    left = 0,
                    top = 80,
                    right = 200,
                    bottom = 140,
                    pieces = listOf(
                        PPOcrLine(
                            "職員さんに聞いた",
                            0,
                            100,
                            200,
                            140,
                            printed = listOf(PrintedReading(0, 2)),
                        ),
                        PPOcrLine("しょくいん", 0, 80, 90, 96),
                    ),
                ),
            ),
        )

        val blocks = ppOcrBlocks(outcome)

        assertEquals(listOf(PrintedReading(0, 2)), blocks.single().pieces.first().printed)
    }

    @Test
    fun `the engine choice survives a round trip through the preferences`() {
        val settings = AppSettings(context)
        settings.engine = EngineChoice.PP_OCR

        assertEquals(EngineChoice.PP_OCR, AppSettings(context).engine)
    }

    @Test
    fun `automatic mode keeps pp-ocr in the priority list`() {
        val settings = AppSettings(context)
        assertEquals(listOf(EngineChoice.LENS, EngineChoice.PP_OCR, EngineChoice.MLKIT), settings.enginePriorityList)
    }
}
