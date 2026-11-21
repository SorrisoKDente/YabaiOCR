package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tray-only app must announce a dictionary update once, not on every wake-up.
 *
 * The Android side keeps the same rule (`UpdateStateStore.notifiedKey()`): a set of «dictionary at
 * remote revision» that was already shown is not shown again, and a newer upstream revision earns a
 * new notice.
 */
class DictionaryUpdateOffersTest {

    private fun result(
        fileName: String,
        installed: String,
        remote: String,
    ) = DesktopDictCheckResult(
        checkedAt = 1_700_000_000_000L,
        updatesAvailableCount = 1,
        updateAvailableMap = mapOf(fileName to true),
        remoteRevisionMap = mapOf(fileName to remote),
        installedRevisionMap = mapOf(fileName to installed),
    )

    @Test
    fun `an update is offered with the revisions the notice prints`() {
        val offers = dictionaryUpdateOffers(result("jmdict-en.yabai", "2026-10-30", "2026-11-15"))

        assertEquals(1, offers.size)
        assertEquals("jmdict-en", offers.single().entry.id)
        assertEquals("2026-10-30", offers.single().installedRevision)
        assertEquals("2026-11-15", offers.single().remoteRevision)
    }

    @Test
    fun `the same remote revision is announced only once`() {
        val check = result("jmdict-en.yabai", "2026-10-30", "2026-11-15")
        val first = dictionaryAnnouncement(check, alreadyShownKey = "")
        assertTrue("the first check must show the update", first is DictionaryAnnouncement.Show)

        val second = dictionaryAnnouncement(
            check,
            dictionaryNoticeKey((first as DictionaryAnnouncement.Show).offers),
        )
        assertEquals(
            "the same news popped a second time, and clearing the key re-announced it on every check",
            DictionaryAnnouncement.Keep,
            second,
        )
    }

    @Test
    fun `a newer remote revision is announced again`() {
        val shown = dictionaryNoticeKey(
            (
                dictionaryAnnouncement(
                    result("jmdict-en.yabai", "2026-10-30", "2026-11-15"),
                    alreadyShownKey = "",
                ) as DictionaryAnnouncement.Show
                ).offers,
        )

        val newer = dictionaryAnnouncement(
            result("jmdict-en.yabai", "2026-11-15", "2026-11-20"),
            alreadyShownKey = shown,
        )

        assertTrue(newer is DictionaryAnnouncement.Show)
        assertEquals("2026-11-20", (newer as DictionaryAnnouncement.Show).offers.single().remoteRevision)
    }

    @Test
    fun `a dictionary with no catalogue entry is not offered`() {
        val offers = dictionaryUpdateOffers(result("meu-dicionario.yabai", "", "2026-11-15"))

        assertTrue("there is no source to download it from", offers.isEmpty())
    }

    @Test
    fun `a check with nothing available ends the announcement`() {
        val quiet = DesktopDictCheckResult(1_700_000_000_000L, 0, emptyMap(), emptyMap())

        assertEquals(
            "without an installable update the notice and the tooltip must be cleared",
            DictionaryAnnouncement.Clear,
            dictionaryAnnouncement(quiet, alreadyShownKey = "jmdict-en.yabai:2026-11-15"),
        )
    }

    @Test
    fun `a dictionary whose installed revision caught up ends the announcement`() {
        val caughtUp = DesktopDictCheckResult(
            checkedAt = 1_700_000_000_000L,
            updatesAvailableCount = 0,
            updateAvailableMap = mapOf("jmdict-en.yabai" to false),
            remoteRevisionMap = mapOf("jmdict-en.yabai" to "2026-11-15"),
            installedRevisionMap = mapOf("jmdict-en.yabai" to "2026-11-15"),
        )

        assertEquals(
            "an installed update must stop being announced",
            DictionaryAnnouncement.Clear,
            dictionaryAnnouncement(caughtUp, alreadyShownKey = "jmdict-en.yabai:2026-11-15"),
        )
    }
}
