package dev.yabai.ocr.capture

import android.os.Handler
import android.os.Looper

/**
 * How long the camera screen waits for the service to put a photo's reading on screen (R48c).
 *
 * The screen closes itself when the service answers ([dev.yabai.ocr.service.FloatingOcrService]
 * sends `ACTION_PHOTO_READY`). A service that died, a broadcast the system dropped or an engine that
 * never returns would leave the user on a frozen photo behind a spinner, with nothing but the ✕ to
 * get out. The wait is the fallback: armed when the photo is handed over, cleared by the answer, and
 * fired when neither arrives, so the screen can put the camera back and say what happened.
 *
 * The clock is a [Handler] of its own so the limit can be measured without a camera or a service.
 */
internal class PhotoWait(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val onTimeout: () -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {

    private var waiting = false

    private val check = Runnable {
        waiting = false
        onTimeout()
    }

    /** Whether an answer is still being waited for. */
    fun isWaiting(): Boolean = waiting

    /** Starts the wait. A second photo replaces the first limit instead of adding another. */
    fun arm() {
        waiting = true
        handler.removeCallbacks(check)
        handler.postDelayed(check, timeoutMs)
    }

    /** The answer arrived — or the screen is going, and nothing more has to happen on it. */
    fun settle() {
        waiting = false
        handler.removeCallbacks(check)
    }

    companion object {
        /**
         * A photo is read before its boxes can be drawn: a few seconds on a normal phone, a little
         * more through Lens. Thirty covers a slow engine and still hands the screen back before a
         * dead one turns into a minute of a spinner over nothing.
         */
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
