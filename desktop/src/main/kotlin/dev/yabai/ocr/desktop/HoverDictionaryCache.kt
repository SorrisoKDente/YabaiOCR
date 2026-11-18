package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.LookupEngine

/**
 * The dictionaries the hover keeps open, and the one moment they must be let go.
 *
 * Opening the compiled dictionaries is the slow part of a lookup (431 ms for the nine dictionaries
 * of a real install), so the engine is kept between hovers. It is also **123 MB of key index** for
 * those nine — the reason an app that only sits in the tray reported over a gigabyte: turning the
 * hover off released nothing, and the reader who turned it off precisely to stop the work kept
 * paying for the whole index. [release] is called when the hover is turned off and when the app
 * closes; the next hover opens the engine again.
 *
 * The key is the folder path and its modification stamp, as before: an install, a removal or a
 * reordering in the dictionary folder changes it and the engine is rebuilt instead of answering
 * from a stale one.
 */
internal class HoverDictionaryCache(
    private val opener: (DesktopSettings) -> LookupEngine? = { DesktopDictLoader.loadEngine(it) },
    private val closer: (LookupEngine?) -> Unit = { engine -> runCatching { engine?.close() } },
) {
    private var engine: LookupEngine? = null
    private var dir: String? = null
    private var stamp = 0L

    @Synchronized
    fun get(settings: DesktopSettings): LookupEngine? {
        val directory = settings.dictDirectory()
        val path = directory.absolutePath
        val modified = directory.lastModified()

        if (dir == path && stamp == modified) return engine

        if (engine != null) closer(engine)
        engine = null
        dir = path
        stamp = modified

        engine = opener(settings)
        return engine
    }

    /** Closes the dictionaries and forgets them; the next [get] opens them again. */
    @Synchronized
    fun release() {
        closer(engine)
        engine = null
        dir = null
        stamp = 0L
    }
}
