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

    /**
     * After a whole-screen capture, draw boxes over the recognised paragraphs and wait for the user
     * to pick one, instead of dumping the whole screen into the result panel. On by default: it is
     * what makes the whole-screen capture usable on a page with several text blocks.
     */
    var pickText: Boolean
        get() = prefs.getBoolean(KEY_PICK_TEXT, true)
        set(value) = prefs.edit { putBoolean(KEY_PICK_TEXT, value) }

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

    /** Look the recognised text up in the installed dictionaries. */
    var dictionaryLookup: Boolean
        get() = prefs.getBoolean(KEY_DICTIONARY, true)
        set(value) = prefs.edit { putBoolean(KEY_DICTIONARY, value) }

    /** Translate the whole recognised sentence with the offline ML Kit model. */
    var translateSentence: Boolean
        get() = prefs.getBoolean(KEY_TRANSLATE, true)
        set(value) = prefs.edit { putBoolean(KEY_TRANSLATE, value) }

    /**
     * Show the captured image beside the recognised text.
     *
     * Off by default: the image takes a share of the panel's width, and what loses it is the
     * recognised text — precisely the field the user edits to fix an OCR mistake. Whoever wants the
     * comparison keeps it on, and the button in the panel and the switch in the settings write the
     * same flag, so the two can never disagree.
     */
    var showImage: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IMAGE, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_IMAGE, value) }

    /** Target language for the offline translation, as an ML Kit tag ("pt", "en"). */
    var translateTarget: String
        get() = prefs.getString(KEY_TRANSLATE_TARGET, "pt") ?: "pt"
        set(value) = prefs.edit { putString(KEY_TRANSLATE_TARGET, value) }

    companion object {
        const val PREFS_NAME = "yabai_ocr_settings"
        const val DEFAULT_MAX_DIMENSION = 1600

        private const val KEY_ENGINE = "engine"
        private const val KEY_AUTO_COPY = "auto_copy"
        private const val KEY_SHOW_SEGMENTS = "show_segments"
        private const val KEY_PICK_TEXT = "pick_text"
        private const val KEY_UPSCALE = "upscale_small"
        private const val KEY_MAX_DIMENSION = "max_dimension"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DICTIONARY = "dictionary_lookup"
        private const val KEY_TRANSLATE = "translate_sentence"
        private const val KEY_TRANSLATE_TARGET = "translate_target"
        private const val KEY_SHOW_IMAGE = "show_image"
    }
}
