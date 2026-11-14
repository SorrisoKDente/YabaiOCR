package dev.yabai.ocr.desktop

import java.awt.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePointerSourceTest {

    @Test
    fun `the source has no pointer before the first bridge event`() {
        assertNull(BridgePointerSource().current())
    }

    @Test
    fun `the latest position is kept and repeated positions are dropped`() {
        val source = BridgePointerSource()

        assertTrue(source.update(Point(10, 20)))
        assertEquals(Point(10, 20), source.current())

        assertFalse(source.update(Point(10, 20)))
        assertTrue(source.update(Point(11, 20)))
        assertEquals(Point(11, 20), source.current())
    }

    @Test
    fun `the awt source degrades to null instead of throwing off screen`() {
        // On a headless CI there is no pointer at all; either way the contract is a nullable point.
        assertTrue(runCatching { AwtPointerSource().current() }.isSuccess)
    }
}
