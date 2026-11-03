package dev.yabai.ocr.desktop

import java.awt.Point
import java.awt.Rectangle

/**
 * Decides when the card must disappear.
 *
 * The rule is not "the pointer left the word": the card is placed a few pixels away from the word,
 * so the pointer passes over empty space on its way to the card, and it also needs to rest on the
 * card to select and copy the definition. Closing on the first frame outside the word would make
 * the card impossible to read.
 *
 * So a departure starts a grace period. Coming back to the word or onto the card cancels it; only
 * staying away for the whole period closes the card. Pure state, no clock and no window, so the
 * behaviour is testable.
 */
class HoverDismissGate(
    private val graceMillis: Long = 220L,
    private val margin: Int = 6,
) {
    private var hideAt = 0L

    /** A new card is on screen; the pointer has not left it yet. */
    @Synchronized
    fun reset() {
        hideAt = 0L
    }

    /**
     * True when the card should be closed at [now].
     *
     * [target] is the word that produced the card and [tooltip] the card itself, both in screen
     * pixels. Either one keeps the card alive.
     */
    @Synchronized
    fun shouldClose(
        pointer: Point,
        target: Rectangle?,
        tooltip: Rectangle?,
        now: Long,
    ): Boolean {
        if (HoverGeometry.contains(target, pointer, margin) ||
            HoverGeometry.contains(tooltip, pointer)
        ) {
            hideAt = 0L
            return false
        }

        if (hideAt == 0L) {
            // First frame away from the card: start the grace period, do not close yet.
            hideAt = now + graceMillis
            return false
        }
        return now >= hideAt
    }
}
