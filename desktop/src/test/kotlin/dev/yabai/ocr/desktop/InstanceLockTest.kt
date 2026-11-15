package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstanceLockTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun lock(): InstanceLock = InstanceLock(temp.root.toPath().resolve("dir/instance.lock"))

    @Test
    fun `the first process may run and the second may not`() {
        val first = lock()
        val second = lock()

        assertTrue("the first instance must start", first.acquire())
        // This is the bug that cost hover and the shortcuts: the second process started, failed to
        // register dev.yabai.Ocr and kept running without them.
        assertFalse("a second instance must be refused", second.acquire())

        first.release()
    }

    @Test
    fun `after the holder exits the lock is free again`() {
        val first = lock()
        assertTrue(first.acquire())
        first.release()

        val next = lock()
        assertTrue("a crash or a normal exit must not leave the app locked out", next.acquire())
        next.release()
    }

    @Test
    fun `a start right after an update waits for the old process to exit`() {
        // After an in-app update the new build starts while the old process is still shutting down.
        // Without the wait the user was greeted with «Yabai OCR is already running» right after
        // updating the app.
        var attempts = 0
        var sleeps = 0

        val acquired = acquireInstanceLockWithRetry(
            attempts = 5,
            waitMs = 1L,
            sleep = { sleeps++ },
            tryAcquire = { attempts++; attempts == 3 },
        )

        assertTrue("the third attempt finds the lock free", acquired)
        assertEquals(3, attempts)
        assertEquals("it must not sleep after the successful attempt", 2, sleeps)
    }

    @Test
    fun `a real second instance is still refused after the bounded wait`() {
        var sleeps = 0

        val acquired = acquireInstanceLockWithRetry(
            attempts = 4,
            waitMs = 1L,
            sleep = { sleeps++ },
            tryAcquire = { false },
        )

        assertFalse(acquired)
        assertEquals("four attempts, three waits", 3, sleeps)
    }

    @Test
    fun `acquiring twice in the same process is not a conflict`() {
        val lock = lock()

        assertTrue(lock.acquire())
        assertTrue(lock.acquire())

        lock.release()
    }
}
