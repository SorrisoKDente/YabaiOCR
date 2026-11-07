package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDictionaryUpdateCheckerTest {

    @Test
    fun testShouldCheckIntervals() {
        val settings = DesktopSettings()
        settings.dictionaryUpdateInterval = DesktopUpdateInterval.OFF
        assertFalse(DesktopDictionaryUpdateChecker.shouldCheck(settings))

        val now = 1_000_000_000_000L
        settings.lastDictUpdateCheck = now - (25 * 3600 * 1000L) // 25 hours ago

        settings.dictionaryUpdateInterval = DesktopUpdateInterval.DAILY
        assertTrue(DesktopDictionaryUpdateChecker.shouldCheck(settings, now))

        settings.dictionaryUpdateInterval = DesktopUpdateInterval.WEEKLY
        assertFalse(DesktopDictionaryUpdateChecker.shouldCheck(settings, now))

        settings.lastDictUpdateCheck = now - (8 * 24 * 3600 * 1000L) // 8 days ago
        assertTrue(DesktopDictionaryUpdateChecker.shouldCheck(settings, now))
    }

    @Test
    fun testCompareRevisions() {
        val checker = DesktopDictionaryUpdateChecker
        assertEquals(-1, checker.compareRevisions("2025-01-01", "2026-01-01")!!)
        assertEquals(0, checker.compareRevisions("2026-01-01", "2026-01-01")!!)
        assertEquals(1, checker.compareRevisions("2026-05-01", "2026-01-01")!!)
        assertNull(checker.compareRevisions("abc", "def"))
    }
}
