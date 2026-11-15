package dev.yabai.ocr.desktop

import java.awt.Frame
import java.awt.Window

/**
 * Brings a window the user explicitly asked for to the screen.
 *
 * On KDE Wayland an AppImage runs as an XWayland client that is usually **not** the active window
 * (the user is hovering another app). A window created in that state is not raised: KWin leaves it
 * in the taskbar, which reads as «abriu minimizado». Clearing the iconified bit and asking the
 * compositor to activate the window once it exists is what makes it appear.
 *
 * Only user-invoked windows call this: the hover card must never steal focus.
 */
internal fun activateOnOpen(window: Window) {
    val frame = window as? Frame
    if (frame != null) {
        val state = frame.extendedState
        if (state and Frame.ICONIFIED != 0) frame.extendedState = unminimizedExtendedState(state)
    }
    window.isAutoRequestFocus = true
    window.toFront()
    window.requestFocus()
}

/** [state] without the iconified bit, so a window the compositor minimised comes back as it was. */
internal fun unminimizedExtendedState(state: Int): Int = state and Frame.ICONIFIED.inv()
