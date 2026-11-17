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

    /**
     * The card closing must not re-arm the spot it closed on.
     *
     * The read finds nothing («no text near the pointer», or no entry for the word), the manager
     * closes the card, and the pointer never moved. Clearing the trigger there re-opened the gate at
     * the same spot one rest interval later, so a pointer parked on background captured the screen,
     * OCR'd it and played GNOME's shutter sound in a loop — the whole machine kept busy while the
     * user was only resting the mouse.
     */
    @Test
    fun `a card closed on the same spot does not read it again`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))

        gate.keepQuiet()

        assertFalse("one read per rest, not one per rest interval", gate.observe(Point(100, 100), 1_000))
        assertFalse(gate.observe(Point(100, 100), 10_000))
        assertFalse(gate.observe(Point(102, 100), 30_000))
    }

    /** The quiet spot is the one that fired: the next word still opens its own card. */
    @Test
    fun `keeping the dismissed spot quiet still lets another word fire`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))

        gate.keepQuiet()

        assertFalse(gate.observe(Point(160, 100), 200))
        assertFalse(gate.observe(Point(160, 100), 299))
        assertTrue(gate.observe(Point(160, 100), 300))
    }

    /**
     * A read that broke on the way is not an answer: the spot must be able to try again.
     *
     * Capture (portal screenshot, empty crop) and OCR fail transiently; keeping the spot quiet as if
     * it had found «no text here» left the word the user was resting on unreadable until the pointer
     * was shaken away and back.
     */
    @Test
    fun `a failed read lets the same spot be tried again`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))

        gate.retryLater(Point(100, 100), 1_000)

        assertFalse("the retry fired before its pause", gate.observe(Point(100, 100), 1_399))
        assertTrue("a capture failure silenced the word for good", gate.observe(Point(100, 100), 1_400))
    }

    /**
     * The retries are capped, because a failure that is not transient would become the shutter loop.
     *
     * On GNOME every capture is a portal screenshot: a spot that fails forever has to stop being
     * captured, not retry at every rest interval for as long as the pointer rests on it.
     */
    @Test
    fun `a spot that keeps failing stops after the retry budget`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))

        var now = 1_000L
        var reads = 0
        repeat(20) {
            gate.retryLater(Point(100, 100), now)
            now += 5_000 // long past any pause: only the budget can stop it
            if (gate.observe(Point(100, 100), now)) reads++
        }

        assertEquals("the failing spot was captured $reads times", HoverGate.MAX_RETRIES, reads)
    }

    /** The budget belongs to the spot, not to the session: walking away makes it readable again. */
    @Test
    fun `walking away from an exhausted spot allows a read again`() {
        gate.observe(Point(100, 100), 0)
        assertTrue(gate.observe(Point(100, 100), 100))

        var now = 1_000L
        repeat(HoverGate.MAX_RETRIES + 1) {
            gate.retryLater(Point(100, 100), now)
            now += 5_000
            gate.observe(Point(100, 100), now)
        }
        assertFalse("the budget was never spent", gate.observe(Point(100, 100), now + 60_000))

        assertFalse(gate.observe(Point(160, 100), now + 60_100)) // walks away
        assertFalse(gate.observe(Point(100, 100), now + 60_200)) // back: the resting clock restarts
        assertTrue(
            "a spot that failed once stayed unreadable until the app restarted",
            gate.observe(Point(100, 100), now + 60_300),
        )
    }

    /**
     * The manager has to route the failure — the gate being able to retry is not enough.
     *
     * A broken capture or OCR carries the point it broke on; a read that found no text does not.
     * Drawing the card needs a display, so this is checked in the source, as the check below does.
     */
    @Test
    fun `a broken capture or OCR retries the same spot, an empty read does not`() {
        val source = sequenceOf(
            java.io.File("src/main/kotlin/dev/yabai/ocr/desktop/HoverOcrManager.kt"),
            java.io.File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/HoverOcrManager.kt"),
        ).firstOrNull { it.isFile }
            ?: error("HoverOcrManager.kt not found from ${java.io.File(".").absolutePath}")

        val lookup = source.readText()
            .substringAfter("private fun lookupAt(")
            .substringBefore("private fun showMissingEntry")

        assertTrue(
            "an empty crop is a capture failure and must be retried",
            Regex("""fail\([^)]*vazio\.",\s*point\)""").containsMatchIn(lookup),
        )
        assertTrue(
            "a failed OCR must be retried at the spot it failed on",
            Regex("""hoverOcrFailure\([^)]*\),\s*point\)""").containsMatchIn(lookup),
        )

        val noText = lookup.substringAfter("HoverMiss.NoText ->").substringBefore("HoverMiss.NoMatch")
        assertTrue("a read with no text has to close the card: $noText", noText.contains("dismiss()"))
        assertFalse("a read with no text must not capture again: $noText", noText.contains("retryLater"))
    }

    /**
     * The manager has to be the one asking for a quiet spot — not just the gate being able to.
     *
     * Drawing the card needs a display, so the dismissal keeps being checked in the source, like
     * [HoverCardTransparencyTest] checks the window's transparency.
     */
    @Test
    fun `dismissing the card keeps the gate quiet instead of re-arming it`() {
        val source = sequenceOf(
            java.io.File("src/main/kotlin/dev/yabai/ocr/desktop/HoverOcrManager.kt"),
            java.io.File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/HoverOcrManager.kt"),
        ).firstOrNull { it.isFile }
            ?: error("HoverOcrManager.kt not found from ${java.io.File(".").absolutePath}")

        val dismiss = source.readText().substringAfter("private fun dismiss()").substringBefore("private fun withCapture")

        assertTrue("dismiss must call gate.keepQuiet()", dismiss.contains("gate.keepQuiet()"))
        assertFalse("dismiss must not re-arm the gate", dismiss.contains("gate.reset()"))
    }

    @Test
    fun `marking a one-shot trigger suppresses the polling loop`() {
        gate.markTriggered(Point(100, 100))

        assertFalse(gate.observe(Point(100, 100), 500))
        assertFalse(gate.observe(Point(101, 100), 900))
    }
}
