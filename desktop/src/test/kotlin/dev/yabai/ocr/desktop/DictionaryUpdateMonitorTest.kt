package dev.yabai.ocr.desktop

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic dictionary check has to answer to the interval, not to a window.
 *
 * It used to be a one-shot inside the Settings window's `LaunchedEffect`, so a `--minimized` run (the
 * one that lives in the tray) never checked, and opening the window once postponed the next check by
 * a whole period. These tests drive the monitor itself: no window, no network, no clock of the
 * machine.
 */
class DictionaryUpdateMonitorTest {

    private val home = Files.createTempDirectory("yabai-monitor").toFile()
    private val settings = DesktopSettings(
        file = home.resolve("settings.properties"),
        desktopHome = home,
    )

    @After
    fun cleanUp() {
        home.deleteRecursively()
    }

    private fun result(at: Long) = DesktopDictCheckResult(at, 0, emptyMap(), emptyMap())

    @Test
    fun `the periodic check runs when the interval elapses and again on the next period`() {
        var clock = 1_700_000_000_000L
        val checks = AtomicInteger()
        settings.dictionaryUpdateInterval = DesktopUpdateInterval.DAILY
        settings.lastDictUpdateCheck = clock

        val monitor = DictionaryUpdateMonitor(
            settings = settings,
            now = { clock },
            check = { current ->
                checks.incrementAndGet()
                current.lastDictUpdateCheck = clock
                result(clock)
            },
        )

        assertTrue(
            "a check right after the last one must wait, not hit the network",
            monitor.step() is DictionaryUpdateStep.Wait,
        )
        assertEquals(0, checks.get())

        clock += 25 * 3600 * 1000L
        val first = monitor.step()
        assertTrue("the elapsed period must check without any window", first is DictionaryUpdateStep.Checked)
        assertEquals(1, checks.get())

        clock += 25 * 3600 * 1000L
        assertTrue("the next period must check again", monitor.step() is DictionaryUpdateStep.Checked)
        assertEquals(2, checks.get())
    }

    @Test
    fun `turning the periodic check off never reaches the network`() {
        var clock = 1_700_000_000_000L
        val checks = AtomicInteger()
        settings.dictionaryUpdateInterval = DesktopUpdateInterval.OFF
        settings.lastDictUpdateCheck = 0L

        val monitor = DictionaryUpdateMonitor(
            settings = settings,
            now = { clock },
            check = { current ->
                checks.incrementAndGet()
                result(clock)
            },
        )

        clock += 365L * 24 * 3600 * 1000L
        assertTrue(monitor.step() is DictionaryUpdateStep.Wait)
        assertEquals("the preference off is not a suggestion", 0, checks.get())
    }

    @Test
    fun `a failed check backs off instead of retrying on every wake-up`() {
        var clock = 1_700_000_000_000L
        val attempts = AtomicInteger()
        settings.dictionaryUpdateInterval = DesktopUpdateInterval.DAILY
        settings.lastDictUpdateCheck = 0L

        val monitor = DictionaryUpdateMonitor(
            settings = settings,
            now = { clock },
            check = {
                attempts.incrementAndGet()
                throw IllegalStateException("offline")
            },
        )

        val failed = monitor.step()
        assertTrue("a failure waits instead of throwing at the caller", failed is DictionaryUpdateStep.Wait)
        assertEquals(1, attempts.get())

        clock += 10 * 60 * 1000L
        assertTrue(monitor.step() is DictionaryUpdateStep.Wait)
        assertEquals("ten minutes later it must still be backing off", 1, attempts.get())

        clock += 51 * 60 * 1000L
        assertTrue(monitor.step() is DictionaryUpdateStep.Wait)
        assertEquals("after the backoff it may try again", 2, attempts.get())
    }

    @Test
    fun `the monitor keeps checking on its own while the app stays open`() = runBlocking {
        var clock = 1_700_000_000_000L
        settings.dictionaryUpdateInterval = DesktopUpdateInterval.DAILY
        settings.lastDictUpdateCheck = 0L

        // Every read of the clock moves a day forward, so each pass is due: what is under test is
        // that the loop keeps going, not the schedule (covered above).
        val monitor = DictionaryUpdateMonitor(
            settings = settings,
            now = {
                clock += 24 * 3600 * 1000L
                clock
            },
            check = { current ->
                current.lastDictUpdateCheck = clock
                result(clock)
            },
        )

        var seen = 0
        val twoChecks = CountDownLatch(1)
        val job = launch(Dispatchers.Default) {
            monitor.run {
                seen++
                if (seen == 2) twoChecks.countDown()
            }
        }
        try {
            assertTrue("the loop stopped after a single pass", twoChecks.await(5, TimeUnit.SECONDS))
        } finally {
            job.cancel()
        }
        assertTrue("the loop stopped after a single pass", seen >= 2)
    }
}
