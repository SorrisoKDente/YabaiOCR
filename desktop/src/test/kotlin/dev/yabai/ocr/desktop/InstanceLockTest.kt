package dev.yabai.ocr.desktop

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
    fun `acquiring twice in the same process is not a conflict`() {
        val lock = lock()

        assertTrue(lock.acquire())
        assertTrue(lock.acquire())

        lock.release()
    }
}
