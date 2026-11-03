package dev.yabai.ocr.desktop

import java.awt.Point

/**
 * Decides when a still pointer has earned an OCR request.
 *
 * A raw mouse-motion listener would call Lens on every pixel; this gate only fires after the pointer
 * has rested inside a few pixels for [stableMillis]. It also remembers where it last fired, so a
 * pointer parked on one word does not send a request every frame.
 *
 * The memory has to expire on its own. An earlier version cleared it when the tooltip closed, which
 * looked right but made words whose on-screen box does not perfectly contain the pointer (a link,
 * whose underline sits below the glyphs; a line with an English prefix, where the box estimate is
 * off) loop forever: card opens, box says «pointer is not here», card closes, gate fires again.
 * Now the trigger is only forgotten once the pointer really walks [repeatRadiusPx] away, so the same
 * spot stays quiet and every other word still opens a card.
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
        lastPoint = point

        // Walking to another word must be able to open a new card, and coming back to a word that
        // was left must work too: the suppression only covers the spot that actually fired. This
        // runs before the resting check, because the walk away is exactly one big step.
        val trigger = lastTrigger
        if (trigger != null && trigger.distance(point) > repeatRadiusPx) {
            lastTrigger = null
        }

        if (previous == null || previous.distance(point) > stableRadiusPx) {
            stableSince = now
            return false
        }

        if (now - stableSince < stableMillis) return false
        if (lastTrigger != null) return false

        lastTrigger = point
        return true
    }

    /** Marks a point as already handled, for one-shot triggers that bypass [observe]. */
    fun markTriggered(point: Point) {
        lastPoint = point
        lastTrigger = point
        stableSince = 0L
    }

    fun reset() {
        lastPoint = null
        stableSince = 0L
        lastTrigger = null
    }
}
