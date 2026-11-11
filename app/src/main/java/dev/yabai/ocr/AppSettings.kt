package dev.yabai.ocr

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.core.dict.DictionaryQuery
import dev.yabai.ocr.overlay.PickerReadingMode

enum class EngineChoice { AUTO, LENS, MANGA_OCR, MLKIT }
enum class AppThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * How often the app asks the publishers whether an installed dictionary changed (R43).
 *
 * Off by default: it is a background network request, and it should be the user's choice to make
 * the phone do it. The values are whole days because that is the unit the publishers work in —
 * Jitendex cuts a release every few weeks, JMdict every day.
 */
enum class UpdateInterval(val days: Long) {
    OFF(0),
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
}

/** Everything the floating service and the main screen share. */
class AppSettings(context: Context) {

    val appContext: Context = context.applicationContext
    val mangaOcrDir: java.io.File get() = java.io.File(appContext.filesDir, "manga_ocr")
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var engine: EngineChoice
        get() = when (prefs.getString(KEY_ENGINE, null)) {
            EngineChoice.LENS.name -> EngineChoice.LENS
            EngineChoice.MANGA_OCR.name -> EngineChoice.MANGA_OCR
            EngineChoice.MLKIT.name -> EngineChoice.MLKIT
            else -> EngineChoice.AUTO
        }
        set(value) = prefs.edit { putString(KEY_ENGINE, value.name) }

    var enginePriorityList: List<EngineChoice>
        get() {
            val raw = prefs.getString(KEY_ENGINE_PRIORITY, null) ?: "LENS,MANGA_OCR,MLKIT"
            return parseEnginePriority(raw)
        }
        set(value) = prefs.edit { putString(KEY_ENGINE_PRIORITY, value.joinToString(",")) }

    fun parseEnginePriority(raw: String): List<EngineChoice> {
        val parsed = raw.split(",").mapNotNull { name ->
            runCatching { EngineChoice.valueOf(name.trim()) }.getOrNull()
        }.filter { it != EngineChoice.AUTO }.distinct()

        val missing = listOf(EngineChoice.LENS, EngineChoice.MANGA_OCR, EngineChoice.MLKIT) - parsed.toSet()
        return parsed + missing
    }

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
     * Whether a drag always ends with the bubble stuck to the nearest side (R35c).
     *
     * Off by default: the bubble is left exactly where it was dropped, kept only inside the screen.
     * Pinning is for whoever wants the bubble out of the way of the page and always in the same
     * place; free placement is for whoever works on one part of the screen and wants it beside it.
     */
    var bubbleOnEdge: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE_ON_EDGE, false)
        set(value) = prefs.edit { putBoolean(KEY_BUBBLE_ON_EDGE, value) }

    /**
     * Whether the dictionary search ignores kana readings printed in brackets (R35d).
     *
     * Off by default: the box looks the text up exactly as typed. On, only the **lookup** drops
     * `（しょくいん）` — what the panel shows and what «Copiar» copies is still the text the user
     * typed, so the change is never silent.
     */
    var stripSearchReadings: Boolean
        get() = prefs.getBoolean(KEY_STRIP_SEARCH_READINGS, false)
        set(value) = prefs.edit { putBoolean(KEY_STRIP_SEARCH_READINGS, value) }

    /**
     * The text actually handed to the dictionary for a typed [query] (R35d).
     *
     * One place decides the policy, so the search box cannot half-obey the setting: the panel keeps
     * [query] on screen, this is what [DictionaryQuery.stripReadings] makes of it.
     */
    fun dictionaryQuery(query: String): String =
        if (stripSearchReadings) DictionaryQuery.stripReadings(query) else query

    /**
     * Longest side sent to Lens. The reference client uses 1200; 1600 is the size a whole phone
     * screen is brought down to, which is the size the recogniser was measured to read a stylised
     * kanji at (see [DEFAULT_MAX_DIMENSION]).
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

    /**
     * The one dictionary the panel was narrowed to, by **title**, or "" for all of them.
     *
     * The title, and not the repository id, because that is what a match carries and what the panel
     * has to show; and a title that no longer belongs to an enabled dictionary is dropped rather
     * than honoured (see [dev.yabai.ocr.dict.DictionaryRepository.resolveFilter]) — a filter that
     * matches nothing would look exactly like a broken dictionary.
     */
    var dictionaryFilter: String
        get() = prefs.getString(KEY_DICTIONARY_FILTER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_DICTIONARY_FILTER, value) }

    /**
     * The dictionary the kanji card asks for the kanji entry and the per-kanji readings (R30), by
     * installed id, or "" for every kanji dictionary in priority order.
     *
     * The **id**, not the title: titles carry a release date (`KANJIDIC [2026-290]`), so a title
     * stored today stops matching after the dictionary is updated, and the choice would silently
     * fall back to «todos». The id belongs to the catalogue entry and survives an update. See
     * [dev.yabai.ocr.dict.KanjiDefault].
     */
    var kanjiDictionary: String
        get() = prefs.getString(KEY_KANJI_DICTIONARY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_KANJI_DICTIONARY, value) }

    /**
     * When the dictionary search is opened, pre-fill the field with the last query typed there.
     *
     * Off by default: opening the dictionary starts empty, which is what a first-time user expects.
     * Whoever looks up several words in a row gets the last one back instead of an empty field.
     */
    var dictionaryKeepLastSearch: Boolean
        get() = prefs.getBoolean(KEY_DICTIONARY_KEEP_LAST, false)
        set(value) = prefs.edit { putBoolean(KEY_DICTIONARY_KEEP_LAST, value) }

    /**
     * Search the dictionary **definitions** instead of the headwords (R35f).
     *
     * Off by default: the first search in this mode builds a sidecar index for each dictionary,
     * which costs seconds and a few megabytes. The mode is remembered because whoever turned it on
     * chose to pay that once, not on every query.
     */
    var searchByTranslation: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_BY_TRANSLATION, false)
        set(value) = prefs.edit { putBoolean(KEY_SEARCH_BY_TRANSLATION, value) }

    /**
     * The last query typed in the dictionary search (R31), kept only when
     * [dictionaryKeepLastSearch] is on.
     */
    var dictionaryLastQuery: String
        get() = prefs.getString(KEY_DICTIONARY_LAST_QUERY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_DICTIONARY_LAST_QUERY, value) }

    /** Translate the whole recognised sentence with the offline ML Kit model. */
    var translateSentence: Boolean
        get() = prefs.getBoolean(KEY_TRANSLATE, true)
        set(value) = prefs.edit { putBoolean(KEY_TRANSLATE, value) }

    /**
     * Keep the furigana — the reading a manga prints beside a kanji — in the recognised text,
     * written after the characters it belongs to, instead of dropping it.
     *
     * Off by default, because the reading is *not* part of the sentence: `しょくいん職員さん` is not
     * a word anybody can look up, and it throws the translation off. Whoever wants the reading to
     * stay (to read a word aloud, say) gets it where it was printed: `職員（しょくいん）さん`.
     */
    var furiganaInParentheses: Boolean
        get() = prefs.getBoolean(KEY_FURIGANA, false)
        set(value) = prefs.edit { putBoolean(KEY_FURIGANA, value) }

    /**
     * Write the reading of the kanji in the panel, after the characters it reads:
     * `職員さんに聞いた` → `職員（しょくいん）さんに聞（き）いた`.
     *
     * This is the other half of the furigana question: manga does not print the reading most of the
     * time, but the dictionary knows it anyway, because it already segmented the sentence. On by
     * default — it is a reading aid and it costs one section, which folds away.
     */
    var showFurigana: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FURIGANA, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_FURIGANA, value) }

    /**
     * How the readings of the recognised kanji are shown **on the page**, in the picker, before
     * anything is tapped: as ruby beside each kanji, as a readable strip beside each balloon, or
     * not at all.
     *
     * Off by default: the two ways are on trial, and a page covered in small kana is not something
     * to switch on for somebody who did not ask for it. The panel's own furigana is
     * [showFurigana] and is a separate decision.
     */
    var pickerReading: PickerReadingMode
        get() = when (prefs.getString(KEY_PICKER_READING, null)) {
            PickerReadingMode.RUBY.name -> PickerReadingMode.RUBY
            PickerReadingMode.LABEL.name -> PickerReadingMode.LABEL
            else -> PickerReadingMode.OFF
        }
        set(value) = prefs.edit { putString(KEY_PICKER_READING, value.name) }

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

    /**
     * Offer «Perguntar à IA» in the panel: copy the text and hand it to an AI app the user already
     * has, with a question written in front of it.
     *
     * On by default, because it is one button and it costs nothing: no key, no account, no request
     * made by this app. Which app answers is the user's choice, not ours — see [aiPackage].
     */
    var showAiButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AI_BUTTON, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_AI_BUTTON, value) }

    /**
     * How often to check the publishers for new dictionary versions (R43).
     *
     * The check itself is a few HEAD requests or 1 KB descriptors; the download only happens when
     * the user taps «Atualizar», so a metered connection is never surprised by 39 MB.
     */
    var dictionaryUpdateInterval: UpdateInterval
        get() = when (prefs.getString(KEY_DICTIONARY_UPDATE_INTERVAL, null)) {
            UpdateInterval.DAILY.name -> UpdateInterval.DAILY
            UpdateInterval.WEEKLY.name -> UpdateInterval.WEEKLY
            UpdateInterval.MONTHLY.name -> UpdateInterval.MONTHLY
            else -> UpdateInterval.OFF
        }
        set(value) = prefs.edit { putString(KEY_DICTIONARY_UPDATE_INTERVAL, value.name) }

    /**
     * The question the AI app is asked, in front of the recognised text. An empty prompt sends the
     * text alone — for whoever would rather write the question once they are there.
     */
    var aiPrompt: String
        get() = prefs.getString(KEY_AI_PROMPT, AskAi.DEFAULT_PROMPT) ?: AskAi.DEFAULT_PROMPT
        set(value) = prefs.edit { putString(KEY_AI_PROMPT, value) }

    /**
     * The package of the AI app the question is handed to, or "" to let the system chooser ask every
     * time. Only the package is stored: the name shown in the settings is read off the phone, so an
     * app that was uninstalled leaves nothing but a package name that no longer resolves.
     */
    var aiPackage: String
        get() = prefs.getString(KEY_AI_PACKAGE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_AI_PACKAGE, value) }

    var appTheme: AppThemeChoice
        get() = when (prefs.getString(KEY_APP_THEME, null)) {
            AppThemeChoice.LIGHT.name -> AppThemeChoice.LIGHT
            AppThemeChoice.SYSTEM.name -> AppThemeChoice.SYSTEM
            else -> AppThemeChoice.DARK
        }
        set(value) {
            prefs.edit(commit = true) { putString(KEY_APP_THEME, value.name) }
            applyTheme()
        }

    fun applyTheme(@Suppress("UNUSED_PARAMETER") activity: AppCompatActivity? = null) {
        val mode = when (appTheme) {
            AppThemeChoice.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeChoice.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AppThemeChoice.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
        val nightMask = when (appTheme) {
            AppThemeChoice.LIGHT -> android.content.res.Configuration.UI_MODE_NIGHT_NO
            AppThemeChoice.DARK -> android.content.res.Configuration.UI_MODE_NIGHT_YES
            AppThemeChoice.SYSTEM -> android.content.res.Configuration.UI_MODE_NIGHT_UNDEFINED
        }
        if (nightMask != android.content.res.Configuration.UI_MODE_NIGHT_UNDEFINED) {
            val config = appContext.resources.configuration
            config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMask
            @Suppress("DEPRECATION")
            appContext.resources.updateConfiguration(config, appContext.resources.displayMetrics)
        }
    }

    companion object {
        const val PREFS_NAME = "yabai_ocr_settings"

        /**
         * The longest side an image is allowed to keep before it goes to Lens.
         *
         * 1600, so a 1080x2400 phone screen goes as 720x1600. This was 2400 for one day, to keep the
         * tiny furigana a page prints beside a kanji: at 720x1600 those readings are not recognised
         * at all, at 1080x2400 they come back as a segment of their own. Measured on a **drawn**
         * page, that was true — but a drawn page has no art under the lettering and no oversized
         * stylised glyph, and a real manga page does.
         *
         * Measured against the real endpoint on the page that reported the loss (two requests,
         * adding only the resize in between): at 720x1600 the column comes back whole, reading
         * `淀みなき拳で。`; at 1080x2400 the big stylised 淀 is **gone** from the recognition — the
         * line reads `みなきまで。` and its box starts below the 淀. The same happened on the phone
         * (the print shows the box covering `みなき拳で。` only). A word is not recoverable; the
         * printed furigana the larger payload gained is redundant, since `Furigana` writes the
         * reading from the dictionary anyway — and a *crop*, where a reading is looked at closely,
         * is upscaled, so it is still read there.
         *
         * So the cap is 1600 and what the larger payload bought is given back on purpose. See
         * `ROADMAP.md`, «N1 — A precisão do OCR caiu».
         */
        const val DEFAULT_MAX_DIMENSION = 1600

        private const val KEY_ENGINE = "engine"
        private const val KEY_ENGINE_PRIORITY = "engine_priority"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_AUTO_COPY = "auto_copy"
        private const val KEY_SHOW_SEGMENTS = "show_segments"
        private const val KEY_PICK_TEXT = "pick_text"
        private const val KEY_UPSCALE = "upscale_small"
        private const val KEY_BUBBLE_ON_EDGE = "bubble_on_edge"
        private const val KEY_STRIP_SEARCH_READINGS = "strip_search_readings"
        private const val KEY_MAX_DIMENSION = "max_dimension"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DICTIONARY = "dictionary_lookup"
        private const val KEY_DICTIONARY_FILTER = "dictionary_filter"
        private const val KEY_KANJI_DICTIONARY = "kanji_dictionary"
        private const val KEY_DICTIONARY_KEEP_LAST = "dictionary_keep_last_search"
        private const val KEY_SEARCH_BY_TRANSLATION = "search_by_translation"
        private const val KEY_DICTIONARY_LAST_QUERY = "dictionary_last_query"
        private const val KEY_TRANSLATE = "translate_sentence"
        private const val KEY_FURIGANA = "furigana_parentheses"
        private const val KEY_SHOW_FURIGANA = "show_furigana"
        private const val KEY_PICKER_READING = "picker_reading"
        private const val KEY_TRANSLATE_TARGET = "translate_target"
        private const val KEY_SHOW_IMAGE = "show_image"
        private const val KEY_SHOW_AI_BUTTON = "show_ai_button"
        private const val KEY_AI_PROMPT = "ai_prompt"
        private const val KEY_AI_PACKAGE = "ai_package"
        private const val KEY_DICTIONARY_UPDATE_INTERVAL = "dictionary_update_interval"
    }
}
