package dev.yabai.ocr.desktop

import java.awt.Point

/**
 * Decides when a still pointer has earned an OCR request.
 *
 * A raw mouse-motion listener would call Lens on every pixel; this gate only fires after the pointer
 * has rested inside a few pixels for [stableMillis]. It also remembers where it last fired, so a
 * pointer parked on one word does not send a request every frame.
 *
 * [stableMillis] is a preference (`DesktopSettings.hoverRestMillis`): it is a third of the latency
 * the user feels, and how much rest is «enough» depends on how they read — word by word or line by
 * line. Changing it while the pointer rests applies to the very next tick.
 *
 * The memory has to expire on its own. An earlier version cleared it when the tooltip closed, which
 * looked right but made words whose on-screen box does not perfectly contain the pointer (a link,
 * whose underline sits below the glyphs; a line with an English prefix, where the box estimate is
 * off) loop forever: card opens, box says «pointer is not here», card closes, gate fires again.
 * Now the trigger is only forgotten once the pointer really walks [repeatRadiusPx] away, so the same
 * spot stays quiet and every other word still opens a card.
 */
class HoverGate(
    stableMillis: Long = HOVER_REST_DEFAULT_MS,
    private val stableRadiusPx: Int = 5,
    private val repeatRadiusPx: Int = 14,
) {
    /** How long the pointer must rest inside [stableRadiusPx] before a lookup starts. */
    @Volatile
    var stableMillis: Long = stableMillis.coerceIn(MIN_STABLE_MILLIS, MAX_STABLE_MILLIS)
        set(value) {
            field = value.coerceIn(MIN_STABLE_MILLIS, MAX_STABLE_MILLIS)
        }

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

    companion object {
        /** Even a deliberate setting cannot make the gate fire on the same frame the hand lands. */
        const val MIN_STABLE_MILLIS = 80L
        const val MAX_STABLE_MILLIS = 2_000L
    }
}
