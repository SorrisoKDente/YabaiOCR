package dev.yabai.ocr.capture

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The camera screen's wait for the service (R48c).
 *
 * The screen closes itself when the reading answers; a service that died, a notice the system
 * dropped or an engine that never returns would leave the frozen photo and the spinner up for good.
 * The clock here is the real one — a handler on the main looper — so the limit is measured, not
 * described.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoWaitTest {

    private fun idle(millis: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `a reading that never answers hands the screen back after the limit`() {
        var fired = 0
        val wait = PhotoWait(timeoutMs = 20_000, onTimeout = { fired++ })

        wait.arm()
        idle(19_000)

        assertEquals("the screen gave up before the limit", 0, fired)
        assertTrue("the wait stopped on its own", wait.isWaiting())

        idle(1_500)

        assertEquals("the screen never gave the control back", 1, fired)
        assertFalse("the wait fired and is still armed", wait.isWaiting())
    }

    @Test
    fun `the answer stops the wait, so the screen is not closed twice`() {
        var fired = 0
        val wait = PhotoWait(timeoutMs = 20_000, onTimeout = { fired++ })

        wait.arm()
        idle(5_000)
        wait.settle()
        idle(60_000)

        assertEquals("the timeout fired after the service had answered", 0, fired)
        assertFalse(wait.isWaiting())
    }

    @Test
    fun `the default limit is long enough for an engine to answer`() {
        assertTrue(
            "a photo can spend longer than ${PhotoWait.DEFAULT_TIMEOUT_MS} ms being read",
            PhotoWait.DEFAULT_TIMEOUT_MS >= 15_000,
        )
    }
}
