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
    fun `default priority list contains LENS and MLKIT`() {
        val settings = AppSettings(context)
        assertEquals(listOf(EngineChoice.LENS, EngineChoice.MLKIT), settings.enginePriorityList)
    }

    @Test
    fun `parsing valid string restores priority order`() {
        val settings = AppSettings(context)
        val parsed = settings.parseEnginePriority("MLKIT,LENS")
        assertEquals(listOf(EngineChoice.MLKIT, EngineChoice.LENS), parsed)
    }

    @Test
    fun `parsing string with missing engine appends missing engine at the end`() {
        val settings = AppSettings(context)
        val parsed = settings.parseEnginePriority("MLKIT")
        assertEquals(listOf(EngineChoice.MLKIT, EngineChoice.LENS), parsed)
    }
}
