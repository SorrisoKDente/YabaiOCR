package dev.yabai.ocr.desktop

import java.awt.Point
import org.junit.Assert.assertEquals
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

    /**
     * The rest time is a preference, and the gate is the one that has to honour it while it runs.
     *
     * A hover turned on from the tray at 250 ms must not keep the 420 ms it was built with, and a
     * user who changes it in the Ajustes window must not have to restart the app for it.
     */
    @Test
    fun `a shorter rest time fires sooner, without restarting anything`() {
        val tunable = HoverGate(stableMillis = 420, stableRadiusPx = 3, repeatRadiusPx = 10)
        assertFalse(tunable.observe(Point(100, 100), 0))
        assertFalse(tunable.observe(Point(101, 100), 250))

        tunable.stableMillis = 250

        assertTrue("250 ms of rest must fire once the setting says 250", tunable.observe(Point(101, 100), 251))
    }

    @Test
    fun `a rest time below the floor is clamped instead of firing on arrival`() {
        val tunable = HoverGate(stableMillis = 420, stableRadiusPx = 3, repeatRadiusPx = 10)

        tunable.stableMillis = 0

        assertEquals(HoverGate.MIN_STABLE_MILLIS, tunable.stableMillis)
        assertFalse(tunable.observe(Point(100, 100), 0))
        assertFalse("the hand is still landing", tunable.observe(Point(101, 100), 10))
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
    fun `the spot stays quiet after the card closes`() {
        gate.markTriggered(Point(100, 100))

        // No dismissal may re-arm this exact spot: the box estimate can be a few pixels off, and
        // re-arming it is what made 昭和 and 走った blink.
        assertFalse(gate.observe(Point(100, 100), 500))
        assertFalse(gate.observe(Point(101, 102), 900))
        assertFalse(gate.observe(Point(100, 100), 2_000))
    }

    @Test
    fun `walking away and coming back allows the same word again`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))
        assertFalse(gate.observe(Point(100, 100), 200))

        assertFalse(gate.observe(Point(160, 100), 300)) // walks away: the trigger is forgotten
        assertFalse(gate.observe(Point(100, 100), 400)) // back: the stability timer restarts
        assertTrue(gate.observe(Point(100, 100), 500))
    }

    @Test
    fun `marking a one-shot trigger suppresses the polling loop`() {
        gate.markTriggered(Point(100, 100))

        assertFalse(gate.observe(Point(100, 100), 500))
        assertFalse(gate.observe(Point(101, 100), 900))
    }
}
