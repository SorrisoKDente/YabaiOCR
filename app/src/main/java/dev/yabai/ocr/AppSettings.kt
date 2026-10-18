package dev.yabai.ocr

import android.content.Context
import androidx.core.content.edit

enum class EngineChoice { AUTO, LENS, MLKIT }

/** Everything the floating service and the main screen share. */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var engine: EngineChoice
        get() = when (prefs.getString(KEY_ENGINE, null)) {
            EngineChoice.LENS.name -> EngineChoice.LENS
            EngineChoice.MLKIT.name -> EngineChoice.MLKIT
            else -> EngineChoice.AUTO
        }
        set(value) = prefs.edit { putString(KEY_ENGINE, value.name) }

    var autoCopy: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_COPY, value) }

    var showSegments: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SEGMENTS, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_SEGMENTS, value) }

    var upscaleSmall: Boolean
        get() = prefs.getBoolean(KEY_UPSCALE, true)
        set(value) = prefs.edit { putBoolean(KEY_UPSCALE, value) }

    /**
     * Longest side sent to Lens. The reference client uses 1200; a bit more headroom keeps small
     * furigana legible when capturing a whole screen.
     */
    var maxDimension: Int
        get() = prefs.getInt(KEY_MAX_DIMENSION, DEFAULT_MAX_DIMENSION)
        set(value) = prefs.edit { putInt(KEY_MAX_DIMENSION, value.coerceIn(600, 3000)) }

    /** Locale advertised to Lens. "ja" biases it towards Japanese recognition. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ja") ?: "ja"
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    companion object {
        const val PREFS_NAME = "yabai_ocr_settings"
        const val DEFAULT_MAX_DIMENSION = 1600

        private const val KEY_ENGINE = "engine"
        private const val KEY_AUTO_COPY = "auto_copy"
        private const val KEY_SHOW_SEGMENTS = "show_segments"
        private const val KEY_UPSCALE = "upscale_small"
        private const val KEY_MAX_DIMENSION = "max_dimension"
        private const val KEY_LANGUAGE = "language"
    }
}
