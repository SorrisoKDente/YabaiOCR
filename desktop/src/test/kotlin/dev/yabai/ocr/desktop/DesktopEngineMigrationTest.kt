package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A `settings.properties` written while the offline engine was Manga-OCR.
 *
 * The engine was removed, so the stored value names something that no longer exists. Reading it as a
 * fixed engine would leave the app pointing at a reader it cannot run; the only honest migration is
 * to fall back to «Automático» and let the priority list (which does have an offline engine) decide.
 */
class DesktopEngineMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun settingsWith(vararg lines: String): DesktopSettings {
        val file = File(folder.root, "settings.properties")
        file.writeText(lines.joinToString("\n"))
        return DesktopSettings(file, folder.root)
    }

    @Test
    fun `a stale MANGA_OCR engine falls back to automatic`() {
        val settings = settingsWith("engine=MANGA_OCR")

        assertEquals(DesktopEngineChoice.AUTO, settings.engine)
    }

    @Test
    fun `the current values are still read`() {
        assertEquals(DesktopEngineChoice.PP_OCR, settingsWith("engine=PP_OCR").engine)
        assertEquals(DesktopEngineChoice.LENS, settingsWith("engine=LENS").engine)
        assertEquals(DesktopEngineChoice.AUTO, settingsWith("engine=AUTO").engine)
    }

    @Test
    fun `a stale priority list becomes the two engines that exist`() {
        val settings = settingsWith("engine_priority=MANGA_OCR,LENS")

        assertEquals(listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR), settings.enginePriorityList)
    }

    @Test
    fun `the default priority is online first, offline second`() {
        val settings = settingsWith("")

        assertEquals(listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR), settings.enginePriorityList)
    }

    @Test
    fun `the model folder is the PP-OCR one, not the old manga folder`() {
        val settings = settingsWith("")

        assertEquals(File(folder.root, "ppocr_v6"), settings.ppOcrDir)
    }
}
