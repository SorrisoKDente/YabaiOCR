package dev.yabai.ocr

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSettingsEnginePriorityTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `default priority list contains LENS, PP_OCR and MLKIT`() {
        val settings = AppSettings(context)
        assertEquals(listOf(EngineChoice.LENS, EngineChoice.PP_OCR, EngineChoice.MLKIT), settings.enginePriorityList)
    }

    @Test
    fun `parsing valid string restores priority order`() {
        val settings = AppSettings(context)
        val parsed = settings.parseEnginePriority("MLKIT,LENS")
        assertEquals(listOf(EngineChoice.MLKIT, EngineChoice.LENS, EngineChoice.PP_OCR), parsed)
    }

    @Test
    fun `parsing string with missing engine appends missing engine at the end`() {
        val settings = AppSettings(context)
        val parsed = settings.parseEnginePriority("MLKIT")
        assertEquals(listOf(EngineChoice.MLKIT, EngineChoice.LENS, EngineChoice.PP_OCR), parsed)
    }

    @Test
    fun `a priority list from the manga-ocr days becomes the engines that exist`() {
        val settings = AppSettings(context)
        // What an install from before the migration has stored.
        val parsed = settings.parseEnginePriority("LENS,MANGA_OCR,MLKIT")
        assertEquals(listOf(EngineChoice.LENS, EngineChoice.MLKIT, EngineChoice.PP_OCR), parsed)
    }

    @Test
    fun `a stored MANGA_OCR engine falls back to automatic`() {
        val settings = AppSettings(context)
        settings.appContext.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("engine", "MANGA_OCR").commit()

        assertEquals(EngineChoice.AUTO, settings.engine)
    }

    @Test
    fun `the model folder is the pp-ocr one`() {
        val settings = AppSettings(context)
        assertEquals("ppocr_v6", settings.ppOcrDir.name)
    }
}
