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
    fun `the packaged version wins over the environment`() {
        assertEquals("1.4.2", DesktopAppUpdateChecker.resolveVersion("version=1.4.2\n", "1.4.0"))
    }

    @Test
    fun `the environment is used when the packaged resource is missing`() {
        assertEquals("1.3.0", DesktopAppUpdateChecker.resolveVersion(null, "v1.3.0"))
    }

    @Test
    fun `a blank resource and environment fall back to the constant`() {
        assertEquals("1.0.6", DesktopAppUpdateChecker.resolveVersion("", "  "))
    }

    @Test
    fun testCurrentVersion() {
        // The test build passes no -PyabaiVersion, so the generated resource stamps the default;
        // a release build stamps the tag instead of the hardcoded constant it used to report.
        assertEquals("1.0.6", DesktopAppUpdateChecker.getCurrentVersion())
    }
}
