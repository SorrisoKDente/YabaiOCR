package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Check for updates when the app starts» has to tell the user, not only paint a line in the tray
 * menu. These two rules decide when the check runs and when a window is worth opening.
 */
class AppUpdateStartupTest {

    private fun release(hasUpdate: Boolean, latest: String = "1.0.7") = AppUpdateInfo(
        hasUpdate = hasUpdate,
        latestVersion = latest,
        currentVersion = "1.0.6",
        releaseUrl = "https://example.test/release",
        releaseNotes = "",
        downloadUrl = "https://example.test/Yabai_OCR-1.0.7.AppImage",
    )

    @Test
    fun `the automatic check runs once, only when the user asked for it`() {
        assertTrue(shouldCheckAppUpdateOnStart(checkOnStart = true, alreadyStarted = false))
        assertFalse("ran again on a later composition", shouldCheckAppUpdateOnStart(true, true))
        assertFalse("ran even though the checkbox is off", shouldCheckAppUpdateOnStart(false, false))
    }

    @Test
    fun `a newer release opens the notice window`() {
        val notice = startupUpdateNotice(release(hasUpdate = true, latest = "1.0.7"))

        assertEquals("1.0.7", notice?.latestVersion)
    }

    @Test
    fun `an up-to-date answer stays silent`() {
        assertNull(startupUpdateNotice(release(hasUpdate = false)))
        assertNull("a failed check must not pop anything", startupUpdateNotice(null))
    }
}
