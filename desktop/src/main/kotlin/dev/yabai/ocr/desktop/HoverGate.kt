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

    /** The spot whose read broke, how many tries it already had, and when the next one may start. */
    private var failedPoint: Point? = null
    private var failedAttempts = 0
    private var retryNotBefore = 0L

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
            forgetFailures()
        } else {
            val failed = failedPoint
            if (failed != null && failed.distance(point) > repeatRadiusPx) forgetFailures()
        }

        if (previous == null || previous.distance(point) > stableRadiusPx) {
            stableSince = now
            return false
        }

        if (now - stableSince < stableMillis) return false
        // A failed read is on its pause: asking again sooner is what the pause exists to prevent.
        if (now < retryNotBefore) return false
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

    /**
     * Ends a card that went away while the pointer stayed where it was.
     *
     * The suppression of the spot that fired is kept on purpose; only the resting clock restarts.
     * Clearing it here is what made a pointer parked on background — or on a word the dictionary has
     * no entry for — re-fire forever: the read found nothing, the card closed, the gate forgot the
     * trigger, and a fraction of a second later the same spot was captured and OCR'd again. On GNOME
     * every one of those captures is a portal screenshot, so the user heard the shutter sound in a
     * loop and the machine never went idle. Walking [repeatRadiusPx] away still forgets the trigger,
     * so every other word opens its card as before.
     */
    fun keepQuiet() {
        stableSince = 0L
    }

    /**
     * The read at [point] broke on the way — the capture came back empty, the transport refused, the
     * engine threw — so nothing was learned about that spot and it must be allowed to try again.
     *
     * Most such failures are transient (a portal screenshot that timed out, a Lens request that
     * dropped); keeping the spot quiet as if it had answered left the word the user was resting on
     * unreadable until they walked the pointer away. The retries are paced and capped: a failure
     * that is *not* transient would otherwise re-capture the same pixel every rest interval, which
     * is exactly the shutter loop [keepQuiet] exists to stop. Once the budget is spent the spot stays
     * quiet until the pointer walks [repeatRadiusPx] away, like a spot that answered.
     */
    fun retryLater(point: Point, now: Long) {
        val previous = failedPoint
        val sameSpot = previous != null && previous.distance(point) <= repeatRadiusPx
        val attempts = if (sameSpot) failedAttempts + 1 else 1
        failedPoint = point
        failedAttempts = attempts
        stableSince = now

        // Out of budget: the spot is quiet like an answered one until the pointer walks away.
        if (attempts > MAX_RETRIES) {
            lastTrigger = point
            return
        }
        lastTrigger = null
        retryNotBefore = now + (RETRY_BASE_MILLIS shl (attempts - 1))
    }

    /** A spot that answered, or one the pointer walked away from, starts with a fresh budget. */
    private fun forgetFailures() {
        failedPoint = null
        failedAttempts = 0
        retryNotBefore = 0L
    }

    fun reset() {
        lastPoint = null
        stableSince = 0L
        lastTrigger = null
        forgetFailures()
    }

    companion object {
        /** Even a deliberate setting cannot make the gate fire on the same frame the hand lands. */
        const val MIN_STABLE_MILLIS = 80L
        const val MAX_STABLE_MILLIS = 2_000L

        /** Pause after the first failed read; it doubles on every further failure at that spot. */
        const val RETRY_BASE_MILLIS = 400L

        /** Tries a failing spot gets before it stays quiet until the pointer walks away. */
        const val MAX_RETRIES = 3
    }
}
