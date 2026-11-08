package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopHomeSettingsTest {

    private fun tempDir(): File =
        Files.createTempDirectory("yabai-settings-home").toFile().apply { deleteOnExit() }

    @Test
    fun `the default dictionary folder lives under the injected desktop home`() {
        val home = tempDir()
        val settings = DesktopSettings(
            file = File(tempDir(), "settings.properties"),
            desktopHome = home,
        )

        assertEquals(File(home, "dicts").absolutePath, settings.defaultDictDirectory().absolutePath)
    }

    @Test
    fun `the default custom directory follows the injected desktop home`() {
        val home = tempDir()
        val settings = DesktopSettings(
            file = File(tempDir(), "settings.properties"),
            desktopHome = home,
        )

        assertEquals(File(home, "dicts").absolutePath, settings.customDictDirectoryPath)
    }
}
