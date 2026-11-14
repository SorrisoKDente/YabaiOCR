package dev.yabai.ocr.desktop

import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `Dispatchers.Main` only exists when an artifact installs a main dispatcher.
 *
 * The desktop build shipped without `kotlinx-coroutines-swing`, so every `withContext(Dispatchers.Main)`
 * — the update check in Settings among them — threw «Module with the Main dispatcher is missing».
 * The coroutine died in a worker thread and the download/result UI never updated.
 */
class DesktopMainDispatcherTest {

    @Test
    fun `the main dispatcher posts to the AWT event thread`() {
        val ranOnEventThread = runBlocking {
            try {
                withContext(Dispatchers.Main) { SwingUtilities.isEventDispatchThread() }
            } catch (error: Throwable) {
                fail("Dispatchers.Main is not installed: ${error.message}")
                false
            }
        }

        assertTrue("the main dispatcher did not use the event thread", ranOnEventThread)
    }
}
