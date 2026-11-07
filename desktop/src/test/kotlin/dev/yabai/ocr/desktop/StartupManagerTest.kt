package dev.yabai.ocr.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupManagerTest {

    @Test
    fun testStartOnBootToggle() {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return

        StartupManager.setStartOnBoot(true)
        assertTrue(StartupManager.isStartOnBootEnabled())

        StartupManager.setStartOnBoot(false)
        assertFalse(StartupManager.isStartOnBootEnabled())
    }
}
