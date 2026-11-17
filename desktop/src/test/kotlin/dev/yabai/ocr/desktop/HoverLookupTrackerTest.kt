package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A newer hover read supersedes the older one (ROADMAP D83).
 *
 * Before this the hover was locked for the whole duration of a read: on a manga page the Lens call
 * took 3–4 s, and every word the pointer reached in that window was dropped — the user had to leave
 * the word and come back for it to be recognized. The tracker makes the newest lookup the one that
 * gets drawn, and keeps at most [HoverLookupTracker] reads in the air.
 */
class HoverLookupTrackerTest {

    @Test
    fun `a newer lookup makes the older one stale`() {
        val tracker = HoverLookupTracker()
        val first = tracker.begin()
        assertNotNull(first)
        assertTrue(tracker.isCurrent(first!!))

        val second = tracker.begin()

        assertNotNull(second)
        assertFalse("the first answer must not be drawn any more", tracker.isCurrent(first))
        assertTrue(tracker.isCurrent(second!!))
    }

    @Test
    fun `finishing the stale read frees its slot without reviving it`() {
        val tracker = HoverLookupTracker()
        val first = tracker.begin()!!
        tracker.begin()

        tracker.finish(first)

        assertEquals(1, tracker.running)
        assertFalse(tracker.isCurrent(first))
    }

    @Test
    fun `a read may overlap one still running`() {
        val tracker = HoverLookupTracker(maxInFlight = 2)

        val first = tracker.begin()
        val second = tracker.begin()

        assertNotNull("the word the pointer moved to must not wait for the old read", second)
        assertNotNull(first)
        assertEquals(2, tracker.running)
    }

    @Test
    fun `past the limit no new read starts`() {
        val tracker = HoverLookupTracker(maxInFlight = 2)
        tracker.begin()
        tracker.begin()

        assertNull(tracker.begin())
        assertEquals(2, tracker.running)
    }

    @Test
    fun `a finished read lets the next one start`() {
        val tracker = HoverLookupTracker(maxInFlight = 1)
        val first = tracker.begin()!!
        assertNull(tracker.begin())

        tracker.finish(first)
        val second = tracker.begin()

        assertNotNull(second)
        assertTrue(tracker.isCurrent(second!!))
    }
}
