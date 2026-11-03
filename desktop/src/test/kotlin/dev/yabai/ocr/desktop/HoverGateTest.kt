package dev.yabai.ocr.desktop

import java.awt.Point
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverGateTest {

    private val gate = HoverGate(stableMillis = 100, stableRadiusPx = 3, repeatRadiusPx = 10)

    @Test
    fun `does not fire while the pointer is still moving`() {
        assertFalse(gate.observe(Point(100, 100), 0))
        assertFalse(gate.observe(Point(130, 100), 40))
        assertFalse(gate.observe(Point(160, 100), 80))
        assertFalse(gate.observe(Point(190, 100), 120))
    }

    @Test
    fun `fires once after the pointer rests for the stable time`() {
        assertFalse(gate.observe(Point(100, 100), 0))
        assertFalse(gate.observe(Point(101, 100), 50))
        assertFalse(gate.observe(Point(101, 100), 99))
        assertTrue(gate.observe(Point(101, 100), 100))
    }

    @Test
    fun `staying on the same word does not repeat the request`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))

        assertFalse(gate.observe(Point(100, 100), 500))
        assertFalse(gate.observe(Point(104, 100), 900))
        assertFalse(gate.observe(Point(104, 100), 1200))
    }

    @Test
    fun `moving far enough allows a new lookup`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))

        assertFalse(gate.observe(Point(140, 100), 200))
        assertFalse(gate.observe(Point(140, 100), 299))
        assertTrue(gate.observe(Point(140, 100), 300))
    }

    @Test
    fun `forgetting the trigger allows looking at the same word again`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))
        assertFalse(gate.observe(Point(100, 100), 200))

        gate.forgetTrigger()

        assertTrue(gate.observe(Point(100, 100), 250))
    }

    @Test
    fun `marking a one-shot trigger suppresses the polling loop`() {
        gate.markTriggered(Point(100, 100))

        assertFalse(gate.observe(Point(100, 100), 500))
        assertFalse(gate.observe(Point(101, 100), 900))
    }
}
