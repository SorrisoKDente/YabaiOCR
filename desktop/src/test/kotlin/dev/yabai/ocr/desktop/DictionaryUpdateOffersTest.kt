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
        val first = dictionaryOffersToAnnounce(check, alreadyShownKey = "")
        assertEquals(1, first.size)

        val second = dictionaryOffersToAnnounce(check, dictionaryNoticeKey(first))
        assertTrue("the same news popped a second time", second.isEmpty())
    }

    @Test
    fun `a newer remote revision is announced again`() {
        val shown = dictionaryNoticeKey(
            dictionaryOffersToAnnounce(
                result("jmdict-en.yabai", "2026-10-30", "2026-11-15"),
                alreadyShownKey = "",
            ),
        )

        val newer = dictionaryOffersToAnnounce(
            result("jmdict-en.yabai", "2026-11-15", "2026-11-20"),
            alreadyShownKey = shown,
        )

        assertEquals(1, newer.size)
        assertEquals("2026-11-20", newer.single().remoteRevision)
    }

    @Test
    fun `a dictionary with no catalogue entry is not offered`() {
        val offers = dictionaryUpdateOffers(result("meu-dicionario.yabai", "", "2026-11-15"))

        assertTrue("there is no source to download it from", offers.isEmpty())
    }

    @Test
    fun `a check with nothing available announces nothing`() {
        val quiet = DesktopDictCheckResult(1_700_000_000_000L, 0, emptyMap(), emptyMap())

        assertTrue(dictionaryOffersToAnnounce(quiet, alreadyShownKey = "").isEmpty())
    }
}
