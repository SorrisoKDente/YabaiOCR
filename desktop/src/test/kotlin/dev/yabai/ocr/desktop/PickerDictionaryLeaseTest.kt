package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.LookupEngine
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The picker borrows the engine that is kept open between windows. Closing the window has to give
 * it back, or the 123 MB of dictionary index outlives the screenshot it was opened for (D97).
 */
class PickerDictionaryLeaseTest {

    private val engine = LookupEngine(emptyList())

    @Test
    fun theEngineIsClosedWhenThePickerGoesAway() {
        val closes = AtomicInteger()
        val lease = PickerDictionaryLease(opener = { engine }, closer = { closes.incrementAndGet() })

        assertSame(engine, lease.engine(DesktopSettings()))
        lease.close()

        assertEquals("closing the picker must close the dictionaries it opened", 1, closes.get())
    }

    @Test
    fun aPickerThatNeverLookedAnythingUpClosesNothing() {
        val closes = AtomicInteger()
        val lease = PickerDictionaryLease(opener = { engine }, closer = { closes.incrementAndGet() })

        // Cancelled over the empty background: the engine was never borrowed.
        lease.close()

        assertEquals("an unused picker must not close someone else's engine", 0, closes.get())
    }

    @Test
    fun theEngineIsClosedOnlyOnce() {
        val closes = AtomicInteger()
        val lease = PickerDictionaryLease(opener = { engine }, closer = { closes.incrementAndGet() })

        lease.engine(DesktopSettings())
        lease.close()
        lease.close()

        assertEquals("a second close must not close an engine another window owns", 1, closes.get())
    }
}
