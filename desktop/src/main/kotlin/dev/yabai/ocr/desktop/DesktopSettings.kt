package dev.yabai.ocr.desktop

import java.io.File
import java.net.URLEncoder
import java.util.Properties

enum class DesktopEngineChoice { AUTO, LENS, PP_OCR }
enum class DesktopPickerReadingMode { OFF, RUBY, LABEL }
enum class DesktopUpdateInterval(val days: Long) {
    OFF(0),
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
}
enum class DesktopAiProvider { CHATGPT, GEMINI, DEEPSEEK, CLAUDE, CUSTOM }
enum class DesktopLanguageChoice { SYSTEM, PT_BR, EN_US }

/**
 * How long the pointer must rest before the hover reads the screen.
 *
 * The hover used to wait 420 ms, which is a third of the whole latency the user feels on a slow
 * machine. What the gate has to be sure of is that the pointer **stopped** — not that the reader has
 * finished a sentence — so the default is 250 ms, and the value stays configurable for the reader
 * who moves word to word and does not want the card opening under a pointer still on its way.
 */
internal const val HOVER_REST_DEFAULT_MS = 250L

/** Below this the card opens while the hand is still landing, which is guessing, not resting. */
internal const val HOVER_REST_MIN_MS = 120L

/** Above this the hover stops feeling like a hover. */
internal const val HOVER_REST_MAX_MS = 1_000L

/** The values the Settings window offers, in the order it lists them. */
internal val HOVER_REST_PRESETS = listOf(150L, HOVER_REST_DEFAULT_MS, 420L)

class DesktopSettings(
    private val file: File = File(defaultDesktopHome(), "settings.properties"),
    private val desktopHome: File = defaultDesktopHome(),
) {
    private val props = Properties().apply {
        if (file.exists()) {
            runCatching { file.inputStream().use { load(it) } }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            java.io.BufferedOutputStream(tempFile.outputStream()).use { props.store(it, "Yabai OCR Desktop Settings") }
            if (tempFile.exists()) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
            }
        }
    }

    // 1. Atalho Global (Desktop)
    var globalHotkey: String
        get() = props.getProperty("global_hotkey", "Ctrl + Shift + O")
        set(value) { props.setProperty("global_hotkey", value); save() }

    /**
     * The hand-drawn crop. Kept apart from [globalHotkey] so the full-screen picker and the drag
     * overlay are two visible actions, not one shortcut with a hidden preference behind it.
     */
    var regionHotkey: String
        get() = props.getProperty("region_hotkey", "Ctrl + Shift + A")
        set(value) { props.setProperty("region_hotkey", value); save() }

    var quickDictHotkey: String
        get() = props.getProperty("quick_dict_hotkey", "Ctrl + Shift + D")
        set(value) { props.setProperty("quick_dict_hotkey", value); save() }

    var hoverHotkey: String
        get() = props.getProperty("hover_hotkey", "Ctrl + Shift + H")
        set(value) { props.setProperty("hover_hotkey", value); save() }

    var hoverCycleHotkey: String
        get() = props.getProperty("hover_cycle_hotkey", "Ctrl + Shift + X")
        set(value) { props.setProperty("hover_cycle_hotkey", value); save() }

    /**
     * Time, in milliseconds, the pointer has to stay still before the hover reads the screen.
     *
     * Clamped on the way in and on the way out: a hand-edited file with `0` or `99999` must not turn
     * the hover into a Lens flood or into something that never opens.
     */
    var hoverRestMillis: Long
        get() = (props.getProperty("hover_rest_millis")?.trim()?.toLongOrNull() ?: HOVER_REST_DEFAULT_MS)
            .coerceIn(HOVER_REST_MIN_MS, HOVER_REST_MAX_MS)
        set(value) {
            props.setProperty("hover_rest_millis", value.coerceIn(HOVER_REST_MIN_MS, HOVER_REST_MAX_MS).toString())
            save()
        }

    /**
     * How this machine draws its windows.
     *
     * Stored here rather than in the crash marker because the Settings window writes it too: it is a
     * preference, and the crash recovery only raises it a step (D79).
     */
    var skikoRenderer: SkikoRenderer
        get() = SkikoRenderer.fromStored(props.getProperty("skiko_renderer"))
        set(value) { props.setProperty("skiko_renderer", value.name); save() }

    // 2. Motor de Reconhecimento
    var engine: DesktopEngineChoice
        get() = when (props.getProperty("engine", "AUTO")) {
            "LENS" -> DesktopEngineChoice.LENS
            "PP_OCR" -> DesktopEngineChoice.PP_OCR
            // A settings file from before the migration still says MANGA_OCR; that engine is gone,
            // and AUTO is the only honest reading of a preference whose engine no longer exists.
            else -> DesktopEngineChoice.AUTO
        }
        set(value) { props.setProperty("engine", value.name); save() }

    var enginePriorityList: List<DesktopEngineChoice>
        get() {
            val raw = props.getProperty("engine_priority", "LENS,PP_OCR")
            return parseEnginePriority(raw)
        }
        set(value) {
            props.setProperty("engine_priority", value.joinToString(","))
            save()
        }

    fun parseEnginePriority(raw: String): List<DesktopEngineChoice> {
        val parsed = raw.split(",").mapNotNull { name ->
            runCatching { DesktopEngineChoice.valueOf(name.trim()) }.getOrNull()
        }.filter { it != DesktopEngineChoice.AUTO }.distinct()

        val missing = listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR) - parsed.toSet()
        return parsed + missing
    }

    // 3. Comportamento do OCR e Resultado
    var autoCopy: Boolean
        get() = props.getProperty("auto_copy", "true").toBoolean()
        set(value) { props.setProperty("auto_copy", value.toString()); save() }

    /**
     * Check for a new app release when the app starts.
     *
     * Off means the About card's button is the only way to look; the periodic dictionary update is a
     * separate preference and keeps its own interval.
     */
    var checkUpdatesOnStart: Boolean
        get() = props.getProperty("check_updates_on_start", "true").toBoolean()
        set(value) { props.setProperty("check_updates_on_start", value.toString()); save() }

    var startOnBoot: Boolean
        get() = props.getProperty("start_on_boot", "false").toBoolean()
        set(value) {
            props.setProperty("start_on_boot", value.toString())
            save()
            StartupManager.setStartOnBoot(value)
        }

    var closeToTray: Boolean
        get() = props.getProperty("close_to_tray", "true").toBoolean()
        set(value) { props.setProperty("close_to_tray", value.toString()); save() }

    var showSegments: Boolean
        get() = props.getProperty("show_segments", "false").toBoolean()
        set(value) { props.setProperty("show_segments", value.toString()); save() }

    var darkTheme: Boolean
        get() = props.getProperty("dark_theme", "true").toBoolean()
        set(value) { props.setProperty("dark_theme", value.toString()); save() }

    var appLanguage: DesktopLanguageChoice
        get() = when (props.getProperty("app_language", "SYSTEM")) {
            "PT_BR" -> DesktopLanguageChoice.PT_BR
            "EN_US" -> DesktopLanguageChoice.EN_US
            else -> DesktopLanguageChoice.SYSTEM
        }
        set(value) { props.setProperty("app_language", value.name); save() }

    var upscaleSmall: Boolean
        get() = props.getProperty("upscale_small", "true").toBoolean()
        set(value) { props.setProperty("upscale_small", value.toString()); save() }

    var showImage: Boolean
        get() = props.getProperty("show_image", "false").toBoolean()
        set(value) { props.setProperty("show_image", value.toString()); save() }

    var furiganaInParentheses: Boolean
        get() = props.getProperty("furigana_parentheses", "false").toBoolean()
        set(value) { props.setProperty("furigana_parentheses", value.toString()); save() }

    var showFurigana: Boolean
        get() = props.getProperty("show_furigana", "true").toBoolean()
        set(value) { props.setProperty("show_furigana", value.toString()); save() }

    /**
     * Which monitor the full-screen capture and the hand-drawn crop use.
     *
     * Values: `PRIMARY`, `MOUSE` or `DISPLAY:<index>`. Kept as text instead of an enum because the
     * per-display choice names a list that changes with the machine; an index that no longer exists
     * is resolved to the primary screen at capture time.
     */
    var captureMonitor: String
        get() = props.getProperty("capture_monitor", CAPTURE_MONITOR_PRIMARY).orEmpty()
            .ifBlank { CAPTURE_MONITOR_PRIMARY }
        set(value) { props.setProperty("capture_monitor", value); save() }

    var pickerReading: DesktopPickerReadingMode
        get() = when (props.getProperty("picker_reading", "OFF")) {
            "RUBY" -> DesktopPickerReadingMode.RUBY
            "LABEL" -> DesktopPickerReadingMode.LABEL
            else -> DesktopPickerReadingMode.OFF
        }
        set(value) { props.setProperty("picker_reading", value.name); save() }

    // 4. Dicionários e Localização
    var dictionaryLookup: Boolean
        get() = props.getProperty("dictionary_lookup", "true").toBoolean()
        set(value) { props.setProperty("dictionary_lookup", value.toString()); save() }

    var dictionaryFilter: String
        get() = props.getProperty("dictionary_filter", "") ?: ""
        set(value) { props.setProperty("dictionary_filter", value); save() }

    var hoverDictionaryFilter: String
        get() = props.getProperty("hover_dictionary_filter", "") ?: ""
        set(value) { props.setProperty("hover_dictionary_filter", value); save() }

    var dictionaryKeepLastSearch: Boolean
        get() = props.getProperty("dictionary_keep_last_search", "true").toBoolean()
        set(value) { props.setProperty("dictionary_keep_last_search", value.toString()); save() }

    /**
     * Search the definitions instead of the headwords (R35f).
     *
     * Kept as a preference because the first search in this mode builds a sidecar index per
     * dictionary; whoever turned it on is willing to pay for it, and turning it off goes back to
     * the key index with no build at all.
     */
    var searchByTranslation: Boolean
        get() = props.getProperty("search_by_translation", "false").toBoolean()
        set(value) { props.setProperty("search_by_translation", value.toString()); save() }

    var stripSearchReadings: Boolean
        get() = props.getProperty("strip_search_readings", "true").toBoolean()
        set(value) { props.setProperty("strip_search_readings", value.toString()); save() }

    var dictionaryUpdateInterval: DesktopUpdateInterval
        get() = when (props.getProperty("dictionary_update_interval", "WEEKLY")) {
            "OFF" -> DesktopUpdateInterval.OFF
            "DAILY" -> DesktopUpdateInterval.DAILY
            "MONTHLY" -> DesktopUpdateInterval.MONTHLY
            else -> DesktopUpdateInterval.WEEKLY
        }
        set(value) { props.setProperty("dictionary_update_interval", value.name); save() }

    var lastDictUpdateCheck: Long
        get() = props.getProperty("last_dict_update_check", "0").toLongOrNull() ?: 0L
        set(value) { props.setProperty("last_dict_update_check", value.toString()); save() }

    /**
     * The dictionary updates the reader was already shown, as `<file>:<remote revision>` entries.
     *
     * An app that lives in the tray wakes up every 15 minutes for the interval question and only hits
     * the network when the period really elapsed. Without this key, every period would re-open the
     * same notice for the update the reader chose to ignore — and the reader would end up turning the
     * check off. A newer upstream revision changes the key and earns a new notice. The Android side
     * keeps the same rule in `UpdateStateStore.notifiedKey()`.
     */
    var lastDictUpdateNotice: String
        get() = props.getProperty("last_dict_update_notice", "").orEmpty()
        set(value) { props.setProperty("last_dict_update_notice", value); save() }

    /**
     * Folder the dictionary import chooser was left in.
     *
     * Importing three dictionaries used to mean walking the same path three times: the chooser opens
     * in the last folder now, and nothing is opened when that folder is gone.
     */
    var lastDictionaryImportDirectory: String
        get() = props.getProperty("last_dict_import_dir", "") ?: ""
        set(value) { props.setProperty("last_dict_import_dir", value); save() }

    var customDictDirectoryPath: String
        get() = props.getProperty("custom_dict_dir")
            ?: props.getProperty("custom_dict_directory")
            ?: defaultDictDirectory().absolutePath
        set(value) {
            props.setProperty("custom_dict_dir", value)
            props.remove("custom_dict_directory")
            save()
        }

    fun defaultDictDirectory(): File {
        return File(desktopHome, "dicts")
    }

    /**
     * Where the PP-OCRv6_manga files live: the detector, the recogniser and the 19,053-symbol
     * dictionary, kept next to the settings and the dictionaries.
     */
    val ppOcrDir: File get() = File(desktopHome, "ppocr_v6")

    fun dictDirectory(): File {
        return File(customDictDirectoryPath)
    }

    // 5. Tradução
    var translateSentence: Boolean
        get() = props.getProperty("translate_sentence", "false").toBoolean()
        set(value) { props.setProperty("translate_sentence", value.toString()); save() }

    var translateTarget: String
        get() = props.getProperty("translate_target", "pt")
        set(value) { props.setProperty("translate_target", value); save() }

    // 6. IA
    var showAiButton: Boolean
        get() = props.getProperty("show_ai_button", "true").toBoolean()
        set(value) { props.setProperty("show_ai_button", value.toString()); save() }

    var aiPrompt: String
        get() = props.getProperty("ai_prompt", dev.yabai.ocr.desktop.i18n.DesktopStrings.defaultAiPrompt)
        set(value) { props.setProperty("ai_prompt", value); save() }

    var aiProvider: DesktopAiProvider
        get() = when (props.getProperty("ai_provider", "CHATGPT")) {
            "GEMINI" -> DesktopAiProvider.GEMINI
            "DEEPSEEK" -> DesktopAiProvider.DEEPSEEK
            "CLAUDE" -> DesktopAiProvider.CLAUDE
            "CUSTOM" -> DesktopAiProvider.CUSTOM
            else -> DesktopAiProvider.CHATGPT
        }
        set(value) { props.setProperty("ai_provider", value.name); save() }

    var aiCustomUrl: String
        get() = props.getProperty("ai_custom_url", "")
        set(value) { props.setProperty("ai_custom_url", value); save() }

    // Quick Dict window size & position persistence
    var quickDictWidth: Int
        get() = props.getProperty("quick_dict_w", "480").toIntOrNull() ?: 480
        set(value) { props.setProperty("quick_dict_w", value.toString()); save() }

    var quickDictHeight: Int
        get() = props.getProperty("quick_dict_h", "600").toIntOrNull() ?: 600
        set(value) { props.setProperty("quick_dict_h", value.toString()); save() }

    var quickDictX: Int?
        get() = props.getProperty("quick_dict_x")?.toIntOrNull()
        set(value) { if (value != null) props.setProperty("quick_dict_x", value.toString()) else props.remove("quick_dict_x"); save() }

    var quickDictY: Int?
        get() = props.getProperty("quick_dict_y")?.toIntOrNull()
        set(value) { if (value != null) props.setProperty("quick_dict_y", value.toString()) else props.remove("quick_dict_y"); save() }

    fun buildAiUrl(text: String): String {
        val encoded = URLEncoder.encode(aiPrompt + "\n\n" + text, "UTF-8")
        return when (aiProvider) {
            DesktopAiProvider.CHATGPT -> "https://chatgpt.com/?q=$encoded"
            DesktopAiProvider.GEMINI -> "https://gemini.google.com/app?text=$encoded"
            DesktopAiProvider.DEEPSEEK -> "https://chat.deepseek.com/?q=$encoded"
            DesktopAiProvider.CLAUDE -> "https://claude.ai/new?q=$encoded"
            DesktopAiProvider.CUSTOM -> {
                val custom = aiCustomUrl.ifBlank { "https://chatgpt.com/?q={text}" }
                val encodedText = URLEncoder.encode(text, "UTF-8")
                custom.replace("{text}", encodedText)
            }
        }
    }

    fun getDisabledDictionaries(): Set<String> {
        val raw = props.getProperty("disabled_dictionaries", "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setDisabledDictionaries(disabled: Set<String>) {
        props.setProperty("disabled_dictionaries", disabled.joinToString(","))
        save()
    }

    fun getDictionaryOrder(): List<String> {
        val raw = props.getProperty("dictionary_order", "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setDictionaryOrder(order: List<String>) {
        props.setProperty("dictionary_order", order.joinToString(","))
        save()
    }
}
