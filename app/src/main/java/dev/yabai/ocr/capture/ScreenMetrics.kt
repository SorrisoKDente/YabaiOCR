package dev.yabai.ocr.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

/**
 * The screen measured as the whole display (R48j).
 *
 * `resources.displayMetrics` is the area an app draws in: it already leaves the system bars out.
 * `getRealMetrics` is the whole display, and it is what places the bubble (R23) and measures a
 * capture. The camera screen and the picker's page have to agree on the rectangle, otherwise the
 * preview, the frozen photo and the page are three slightly different rectangles and the picture
 * jumps by a bar's width at the handover. `getRealMetrics` is deprecated in favour of
 * `WindowMetrics`, but it answers on every API level this app supports (26+) and in Robolectric.
 */
internal object ScreenMetrics {

    /** The whole display, in pixels; `resources.displayMetrics` only when the display says nothing. */
    fun real(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            val fallback = context.resources.displayMetrics
            metrics.widthPixels = fallback.widthPixels
            metrics.heightPixels = fallback.heightPixels
        }
        return metrics
    }
}
