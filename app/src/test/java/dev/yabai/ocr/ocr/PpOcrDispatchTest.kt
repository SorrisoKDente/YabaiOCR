package dev.yabai.ocr.ocr

import android.content.Context
import android.graphics.Bitmap
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.EngineChoice
import dev.yabai.ocr.UsageTracker
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
