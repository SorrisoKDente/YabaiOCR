package dev.yabai.ocr.desktop

import java.awt.MouseInfo
import java.awt.Point

/** Source of the global pointer position used by the hover. */
internal interface PointerSource {
    fun current(): Point?
}

internal class AwtPointerSource : PointerSource {
    override fun current(): Point? =
        runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
}

/**
 * Last position reported by a compositor bridge (the KWin script or the GNOME Shell extension).
 *
 * The bridge already limits itself to ~10 Hz; this class only remembers the latest point and drops
 * repeats, so a stationary pointer does not trigger another OCR pass.
 */
internal class BridgePointerSource : PointerSource {
    @Volatile
    private var last: Point? = null

    override fun current(): Point? = last

    /** Returns true when the point differs from the previous one. */
    fun update(point: Point): Boolean {
        if (last == point) return false
        last = point
        return true
    }
}
