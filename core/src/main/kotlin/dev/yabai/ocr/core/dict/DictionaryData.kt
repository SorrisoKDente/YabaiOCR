package dev.yabai.ocr.core.dict

/** One dictionary sense, already rendered to text. */
data class DictionaryEntry(
    /** The headword as the dictionary spells it (may differ from what was looked up). */
    val term: String,
    val reading: String,
    /** Part-of-speech tags, e.g. "n vs". */
    val tags: String,
    /** Part-of-speech bitmask, used to accept or reject deinflected lookups. */
    val rulesFlags: Int,
    val score: Int,
    /** Rendered definition lines; senses are already numbered. */
    val definition: String,
)

/** A kanji entry: readings, meanings and the useful statistics. */
data class KanjiEntry(
    val character: String,
    val body: String,
    /** The on'yomi as the dictionary writes them (「・」-separated), or empty. */
    val onYomi: String = "",
    /** The kun'yomi as the dictionary writes them (「・」-separated), or empty. */
    val kunYomi: String = "",
)

/** Everything read from the dictionary's `index.json`. */
data class DictionaryInfo(
    val title: String,
    val revision: String,
    val format: Int,
    val author: String?,
    val url: String?,
    val description: String?,
    val attribution: String?,
    val termCount: Int,
    val kanjiCount: Int,
)

/** A hit produced by the tokenizer. */
data class TokenMatch(
    /** The text as it appears in the recognised sentence. */
    val surface: String,
    val start: Int,
    val end: Int,
    val entry: DictionaryEntry,
    val dictionaryTitle: String,
    /** Human readable deinflection path, e.g. ["past", "polite"]; empty for exact hits. */
    val reasons: List<String>,
)
