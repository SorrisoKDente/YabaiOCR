package dev.yabai.ocr.core.lens

/**
 * What to do with furigana — the small kana a manga prints beside a kanji to give its reading.
 *
 * Lens reports a reading as a line of its own, and a reading is not part of the sentence: joined to
 * the text it produces `しょくいん職員さんに聞いた`, which looks like a word nobody can look up and
 * translates worse than the sentence does. Both modes recognise it the same way; the difference is
 * only whether the reading is thrown away or kept where it was printed.
 */
enum class FuriganaMode {
    /** Leave the reading out: `職員さんに聞いた`. */
    REMOVE,

    /** Keep it after the characters it covers: `職員（しょくいん）さんに聞いた`. */
    PARENTHESES,
}
