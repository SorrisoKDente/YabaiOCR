package dev.yabai.ocr.desktop

import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverDismissGateTest {

    private val gate = HoverDismissGate(graceMillis = 220, margin = 6)
    private val word = Rectangle(100, 100, 60, 40)
    private val card = Rectangle(180, 130, 460, 200)

    @Test
    fun `stays open while the pointer is on the word`() {
        assertFalse(gate.shouldClose(Point(120, 120), word, card, now = 1_000))
        assertFalse(gate.shouldClose(Point(125, 125), word, card, now = 5_000))
    }

    @Test
    fun `stays open while the pointer is on the card`() {
        // The pointer has to cross empty space to reach the card; the card is what keeps it alive.
        assertFalse(gate.shouldClose(Point(300, 200), word, card, now = 10_000))
        assertFalse(gate.shouldClose(Point(300, 200), word, card, now = 60_000))
    }

    @Test
    fun `crossing the gap to the card does not close it`() {
        assertFalse(gate.shouldClose(Point(120, 120), word, card, now = 0))
        // First frame in the gap: grace starts, still open.
        assertFalse(gate.shouldClose(Point(170, 120), word, card, now = 50))
        // 150 ms later the pointer is already on the card, which cancels the countdown.
        assertFalse(gate.shouldClose(Point(300, 200), word, card, now = 200))
        assertFalse(gate.shouldClose(Point(170, 120), word, card, now = 250))
    }

    @Test
    fun `closes only after the whole grace period away`() {
        assertFalse(gate.shouldClose(Point(120, 120), word, card, now = 0))
        assertFalse(gate.shouldClose(Point(600, 600), word, card, now = 100))
        assertFalse(gate.shouldClose(Point(600, 600), word, card, now = 319))
        assertTrue(gate.shouldClose(Point(600, 600), word, card, now = 320))
    }

    @Test
    fun `coming back to the word restarts the countdown`() {
        gate.shouldClose(Point(120, 120), word, card, now = 0)
        gate.shouldClose(Point(600, 600), word, card, now = 100)

        assertFalse(gate.shouldClose(Point(120, 120), word, card, now = 200))
        // The old deadline is gone: a new departure gets a full grace period.
        assertFalse(gate.shouldClose(Point(600, 600), word, card, now = 210))
        assertTrue(gate.shouldClose(Point(600, 600), word, card, now = 430))
    }

    @Test
    fun `a card without a known box closes right after the grace period`() {
        assertFalse(gate.shouldClose(Point(600, 600), null, null, now = 0))
        assertTrue(gate.shouldClose(Point(600, 600), null, null, now = 500))
    }
}
