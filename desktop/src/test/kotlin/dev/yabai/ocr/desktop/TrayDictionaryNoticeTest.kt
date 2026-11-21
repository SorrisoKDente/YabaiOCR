package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tray tooltip announces a dictionary update; it must stop announcing it.
 *
 * The tooltip was written when a check found something and never written again, so after installing
 * the dictionaries the icon still hovered «📚 1 atualização(ões) de dicionário disponível(is)» for
 * the rest of the session. Drawing needs a display, so this guard reads `Main.kt`, the same approach
 * `UpdateCheckScopeTest` uses.
 */
class TrayDictionaryNoticeTest {

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File("").absolutePath}")

    private fun block(source: String, startMarker: String, endMarker: String): String {
        val start = source.indexOf(startMarker)
        assertTrue("$startMarker was not found", start >= 0)
        val end = source.indexOf(endMarker, start)
        assertTrue("$endMarker was not found after $startMarker", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `installing the announced dictionaries clears the tray tooltip`() {
        val main = sourceFile("Main.kt").readText()
        val installed = block(
            main,
            "onDictionaryUpdatesInstalled = {",
            "updatesNotice = updateNotice.copy(dictionaries = emptyList())",
        )

        assertTrue(
            "the tooltip kept promising an update that had just been installed",
            installed.contains("trayManager.setStatus(trayTooltipAfterDictionaryNotice(appUpdateOffered))"),
        )
    }

    /**
     * The tooltip has one line for both notices. Clearing the dictionary one must not take down the
     * «new version available» line the startup check wrote, which a bare `setStatus("")` did.
     */
    @Test
    fun `the version notice survives the end of the dictionary one`() {
        val version = AppUpdateInfo(
            hasUpdate = true,
            latestVersion = "1.2.0",
            currentVersion = "1.1.1",
            releaseUrl = "",
            releaseNotes = "",
        )

        assertEquals(
            DesktopStrings.appVersionNotice("1.2.0"),
            trayTooltipAfterDictionaryNotice(version),
        )
        assertEquals(
            "with no version notice the tooltip must go back to the app's name",
            "",
            trayTooltipAfterDictionaryNotice(null),
        )
    }

    @Test
    fun `only a check with nothing installable or an install forgets the announcement`() {
        val main = sourceFile("Main.kt").readText()
        val clearStart = main.indexOf("DictionaryAnnouncement.Clear -> {")
        val showStart = main.indexOf("is DictionaryAnnouncement.Show ->")
        assertTrue("the two branches were not found", clearStart in 0 until showStart)

        val installedStart = main.indexOf("onDictionaryUpdatesInstalled = {")
        val installedEnd = main.indexOf(
            "updatesNotice = updateNotice.copy(dictionaries = emptyList())",
            installedStart,
        )
        assertTrue("the install callback was not found", installedStart in 0 until installedEnd)

        // Clearing the key on a check that found the same news again re-announced the same set on
        // every period; the install callback is the other moment where the news really ends.
        val needle = "settings.lastDictUpdateNotice = \"\""
        val resets = main.indices.filter { main.startsWith(needle, it) }.toList()
        assertEquals("the key was forgotten in an unexpected place", 2, resets.size)
        for (reset in resets) {
            assertTrue(
                "the key was forgotten outside the «nothing installable» branch and the install",
                reset in clearStart until showStart || reset in installedStart until installedEnd,
            )
        }
    }

    @Test
    fun `a check that finds nothing installable clears both the list and the tooltip`() {
        val main = sourceFile("Main.kt").readText()
        val cleared = block(
            main,
            "DictionaryAnnouncement.Clear -> {",
            "is DictionaryAnnouncement.Show ->",
        )

        assertTrue(
            "the pending offers outlived the update",
            cleared.contains("pendingDictionaryUpdates = emptyList()"),
        )
        assertTrue(
            "the tooltip outlived the update",
            cleared.contains("trayManager.setStatus(trayTooltipAfterDictionaryNotice(appUpdateOffered))"),
        )
    }
}
