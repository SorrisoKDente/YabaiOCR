package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.LookupEngine

/**
 * The picker borrows the engine that is kept open between windows, and gives it back when it goes.
 *
 * `DesktopDictLoader.cachedEngine` keeps the dictionaries mapped so the picker can answer a click
 * right after a capture without paying the ~430 ms of opening the index. The picker used to keep
 * that engine for the whole session: closing the window only dropped the screenshot, and the
 * 123 MB of key index (plus the translation index of a Portuguese dictionary) stayed alive in an
 * app that otherwise sits in the tray. [close] is called while the picker is in composition, so
 * every way out — a paragraph, the hand-drawn crop, Escape — gives the engine back.
 */
internal class PickerDictionaryLease(
    private val opener: (DesktopSettings) -> LookupEngine? = { DesktopDictLoader.cachedEngine(it) },
    private val closer: () -> Unit = { DesktopDictLoader.closeCached() },
) {
    private var borrowed = false

    @Synchronized
    fun engine(settings: DesktopSettings): LookupEngine? {
        borrowed = true
        return opener(settings)
    }

    @Synchronized
    fun close() {
        // The picker may be opened and closed without ever resolving a box (a click on empty
        // background, a capture cancelled): closing then would throw away an engine this window
        // never opened and that another window may be using.
        if (!borrowed) return
        borrowed = false
        closer()
    }
}
