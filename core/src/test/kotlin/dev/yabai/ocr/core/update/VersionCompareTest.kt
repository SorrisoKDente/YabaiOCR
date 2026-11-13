package dev.yabai.ocr.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `a higher patch minor or major is newer`() {
        assertTrue(isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(isNewerVersion("1.0.0", "1.1.0"))
        assertTrue(isNewerVersion("1.9.9", "2.0.0"))
        assertFalse(isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertTrue(isNewerVersion("1.2", "1.2.1"))
    }

    @Test
    fun `a debug suffix is the same release, not a newer one`() {
        assertEquals(0, compareVersions("1.0.0-debug", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0.0-debug", "1.0.0"))
        assertTrue(isNewerVersion("1.4.0-debug", "1.5.0"))
    }

    @Test
    fun `a v prefix and a build suffix are ignored`() {
        assertEquals(0, compareVersions("v1.3.0", "1.3.0"))
        assertEquals(0, compareVersions("1.3.0+42", "1.3.0"))
    }

    @Test
    fun `an unparseable version compares as zero`() {
        assertEquals(0, compareVersions("nightly", "0.0.0"))
        assertFalse(isNewerVersion("nightly", "0.0.0"))
        assertTrue(isNewerVersion("nightly", "0.0.1"))
    }
}
