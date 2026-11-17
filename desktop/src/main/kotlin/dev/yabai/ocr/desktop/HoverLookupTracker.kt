package dev.yabai.ocr.desktop

/**
 * Which hover lookup is still worth showing when the pointer keeps moving.
 *
 * A read used to lock the hover for its whole duration. On this Deck a manga page took 3–4 s, and
 * every word the pointer reached inside that window was silently dropped — which is why a new word
 * only answered after leaving it and coming back. Now a newer lookup supersedes the older one: the
 * older answer is thrown away instead of being drawn over the word the pointer is on, and the new
 * word is read immediately.
 *
 * [maxInFlight] is what keeps that from turning into a flood: with a 250 ms rest and a ~1 s read, a
 * pointer walked across a manga page would otherwise keep four uploads in the air on a wifi link
 * that is already the bottleneck. A second read may start while the first is against Lens; past
 * that, [begin] refuses and the caller resets its gate so the pointer's new word is picked up as
 * soon as a slot frees.
 */
internal class HoverLookupTracker(private val maxInFlight: Int = 2) {

    private var generation = 0
    private var inFlight = 0

    /** A lookup's ticket: its answer is only drawn while it is still the newest one. */
    internal class Token(val generation: Int)

    /** Starts a lookup, or returns null when [maxInFlight] reads are already running. */
    @Synchronized
    fun begin(): Token? {
        if (inFlight >= maxInFlight) return null
        generation += 1
        inFlight += 1
        return Token(generation)
    }

    /** Releases the slot held by [token]; safe to call once per [begin] that returned it. */
    @Synchronized
    fun finish(token: Token) {
        if (inFlight > 0) inFlight -= 1
    }

    /** True while [token] is the newest lookup — false as soon as another one started. */
    @Synchronized
    fun isCurrent(token: Token): Boolean = token.generation == generation

    /** Reads in flight right now. */
    @get:Synchronized
    val running: Int get() = inFlight
}
