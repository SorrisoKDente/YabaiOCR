package dev.yabai.ocr.desktop

import java.io.File
import java.net.URLEncoder
import java.util.Properties

enum class DesktopEngineChoice { AUTO, LENS, MLKIT }
enum class DesktopPickerReadingMode { OFF, RUBY, LABEL }
enum class DesktopUpdateInterval(val days: Long) {
    OFF(0),
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
}
enum class DesktopAiProvider { CHATGPT, GEMINI, DEEPSEEK, CLAUDE, CUSTOM }

class DesktopSettings(
    private val file: File = File(System.getProperty("user.home"), ".yabai_ocr/settings.properties"),
) {
    private val props = Properties().apply {
        if (file.exists()) {
            runCatching { file.inputStream().use { load(it) } }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Yabai OCR Desktop Settings") }
        }
    }

    // 1. Atalho Global (Desktop)
    var globalHotkey: String
        get() = props.getProperty("global_hotkey", "Ctrl + Shift + O")
        set(value) { props.setProperty("global_hotkey", value); save() }

    var quickDictHotkey: String
        get() = props.getProperty("quick_dict_hotkey", "Ctrl + Shift + D")
        set(value) { props.setProperty("quick_dict_hotkey", value); save() }

    var hoverHotkey: String
        get() = props.getProperty("hover_hotkey", "Ctrl + Shift + H")
        set(value) { props.setProperty("hover_hotkey", value); save() }

    var hoverCycleHotkey: String
        get() = props.getProperty("hover_cycle_hotkey", "Ctrl + Shift + X")
        set(value) { props.setProperty("hover_cycle_hotkey", value); save() }

    // 2. Motor de Reconhecimento
    var engine: DesktopEngineChoice
        get() = when (props.getProperty("engine", "AUTO")) {
            "LENS" -> DesktopEngineChoice.LENS
            "MLKIT" -> DesktopEngineChoice.MLKIT
            else -> DesktopEngineChoice.AUTO
        }
        set(value) { props.setProperty("engine", value.name); save() }

    // 3. Comportamento do OCR e Resultado
    var autoCopy: Boolean
        get() = props.getProperty("auto_copy", "true").toBoolean()
        set(value) { props.setProperty("auto_copy", value.toString()); save() }

    var startOnBoot: Boolean
        get() = props.getProperty("start_on_boot", "false").toBoolean()
        set(value) {
            props.setProperty("start_on_boot", value.toString())
            save()
            StartupManager.setStartOnBoot(value)
        }

    var showSegments: Boolean
        get() = props.getProperty("show_segments", "false").toBoolean()
        set(value) { props.setProperty("show_segments", value.toString()); save() }

    var pickText: Boolean
        get() = props.getProperty("pick_text", "true").toBoolean()
        set(value) { props.setProperty("pick_text", value.toString()); save() }

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
        return File(System.getProperty("user.home"), ".yabai_ocr/dicts")
    }

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
        get() = props.getProperty("ai_prompt", "Analise a frase em japonês abaixo, explique a gramática, o sentido dos kanjis e traduza para o português:")
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
}
