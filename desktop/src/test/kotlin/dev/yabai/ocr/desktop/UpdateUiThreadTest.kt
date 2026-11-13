package dev.yabai.ocr.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dictionary update/download callbacks run on `Dispatchers.IO` and write Compose state. A state
 * write from a worker thread does not schedule a recomposition in practice, which is why the update
 * check looked dead (the button label changed to "checking" and then nothing ever appeared).
 */
class UpdateUiThreadTest {

    @Test
    fun `a worker thread hands the state write to the event thread`() {
        val done = CountDownLatch(1)
        val ranOnEdt = AtomicBoolean(false)
        val ranOffEdt = AtomicBoolean(false)

        val worker = Thread {
            ranOffEdt.set(!SwingUtilities.isEventDispatchThread())
            onUiThread {
                ranOnEdt.set(SwingUtilities.isEventDispatchThread())
                done.countDown()
            }
        }
        worker.start()

        assertTrue("the write never reached the event thread", done.await(5, TimeUnit.SECONDS))
        assertTrue("the test must call it from a worker thread", ranOffEdt.get())
        assertTrue("the block must run on the event thread", ranOnEdt.get())
    }
}
