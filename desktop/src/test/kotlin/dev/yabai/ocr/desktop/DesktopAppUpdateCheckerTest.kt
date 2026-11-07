package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAppUpdateCheckerTest {

    @Test
    fun testVersionComparison() {
        assertTrue(DesktopAppUpdateChecker.compareVersions("1.0.0", "1.0.1") < 0)
        assertTrue(DesktopAppUpdateChecker.compareVersions("1.0.0", "1.1.0") < 0)
        assertTrue(DesktopAppUpdateChecker.compareVersions("1.0.0", "2.0.0") < 0)
        assertEquals(0, DesktopAppUpdateChecker.compareVersions("1.0.0", "1.0.0"))
        assertTrue(DesktopAppUpdateChecker.compareVersions("1.0.1", "1.0.0") > 0)
    }

    @Test
    fun testCurrentVersion() {
        assertEquals("1.0.0", DesktopAppUpdateChecker.getCurrentVersion())
    }
}
