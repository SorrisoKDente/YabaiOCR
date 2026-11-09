package dev.yabai.ocr.desktop

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopDictionaryUpdateCheckerTest {

    // The real settings live in ~/.yabai_ocr; a test that mutates them would rewrite the user's
    // preferences (and the store timestamp) every time the suite runs.
    private val home = Files.createTempDirectory("yabai-settings").toFile()
    private val settings = DesktopSettings(
        file = home.resolve("settings.properties"),
        desktopHome = home,
    )

    @After
    fun cleanUp() {
        home.deleteRecursively()
    }

    @Test
    fun testShouldCheckIntervals() {
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
