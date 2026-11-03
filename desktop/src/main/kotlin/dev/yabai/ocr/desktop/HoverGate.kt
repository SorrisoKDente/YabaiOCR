package dev.yabai.ocr.desktop

import java.awt.Point

/**
 * Decides when a still pointer has earned an OCR request.
 *
 * A raw mouse-motion listener would call Lens on every pixel; this gate only fires after the pointer
 * has rested inside a few pixels for [stableMillis]. It also remembers where it last fired, so a
 * pointer parked on one word does not send a request every frame. Moving farther than
 * [repeatRadiusPx] allows a new request; coming back to the same word after the tooltip was closed
 * does too, because [forgetTrigger] is called on dismissal.
 */
class HoverGate(
    private val stableMillis: Long = 420L,
    private val stableRadiusPx: Int = 5,
    private val repeatRadiusPx: Int = 14,
) {
    private var lastPoint: Point? = null
    private var stableSince = 0L
    private var lastTrigger: Point? = null

    /** Feeds the current pointer position; true exactly when a lookup should start. */
    fun observe(point: Point, now: Long): Boolean {
        val previous = lastPoint
        if (previous == null || previous.distance(point) > stableRadiusPx) {
            lastPoint = point
            stableSince = now
            return false
        }

        lastPoint = point
        if (now - stableSince < stableMillis) return false

        val trigger = lastTrigger
        if (trigger != null && trigger.distance(point) <= repeatRadiusPx) return false

        lastTrigger = point
        return true
    }

    /** Marks a point as already handled, for one-shot triggers that bypass [observe]. */
    fun markTriggered(point: Point) {
        lastPoint = point
        lastTrigger = point
        stableSince = 0L
    }

    /**
     * Allows this spot to fire again. Called when the tooltip is dismissed: the user is coming back
     * to the word, not staying on it, and a new lookup is exactly what should happen.
     */
    fun forgetTrigger() {
        lastTrigger = null
    }

    fun reset() {
        lastPoint = null
        stableSince = 0L
        lastTrigger = null
    }
}
