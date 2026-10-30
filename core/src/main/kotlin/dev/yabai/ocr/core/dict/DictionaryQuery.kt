package dev.yabai.ocr.core.dict

import dev.yabai.ocr.core.jp.JapaneseText

/**
 * Cleans a **typed query** without touching the text the user sees (R35d).
 *
 * A word copied from a reader often carries its printed reading: `職員（しょくいん）`. The reading is
 * helpful on the page and noise in a dictionary box — no dictionary has a headword with a kana
 * bracket glued to it, so the search either fails or falls back to segmenting the brackets as
 * characters. Removing the brackets before the lookup is what makes the pasted word find its entry.
 *
 * Only brackets whose **whole** content is kana are removed, and both the full-width `（）` and the
 * ASCII `()` are recognised. Anything else — `（職員）`, `(cat)`, `（）`, a bracket without its pair —
 * is left exactly where it is: a bracket is only a reading when the characters inside it say so,
 * which is the same rule [Furigana] uses to decide that a reading is already printed on the page.
 */
object DictionaryQuery {

    private const val OPEN_FULL = '（'
    private const val CLOSE_FULL = '）'
    private const val OPEN_ASCII = '('
    private const val CLOSE_ASCII = ')'

    /**
     * [text] without its kana-only bracketed groups: `職員（しょくいん）さん` → `職員さん`.
     *
     * The text is not trimmed or otherwise normalised here: the caller already trims the query, and
     * this function has exactly one job.
     */
    fun stripReadings(text: String): String {
        if (text.isEmpty()) return text

        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val width = Character.charCount(codePoint)
            val close = when (codePoint) {
                OPEN_FULL.code -> CLOSE_FULL
                OPEN_ASCII.code -> CLOSE_ASCII
                else -> null
            }
            if (close != null) {
                val end = readingEnd(text, index + width, close)
                if (end >= 0) {
                    index = end
                    continue
                }
            }
            out.appendCodePoint(codePoint)
            index += width
        }
        return out.toString()
    }

    /**
     * The index just past [close] when `text[from, …)` is one or more kana followed by it, or -1.
     *
     * An empty group is not a reading, and a group holding a single non-kana character is not one
     * either: in both cases the bracket stays, because removing it would be a guess about the text.
     */
    private fun readingEnd(text: String, from: Int, close: Char): Int {
        var index = from
        var kana = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (JapaneseText.isKana(codePoint)) {
                kana++
                index += Character.charCount(codePoint)
                continue
            }
            return if (codePoint == close.code && kana > 0) index + 1 else -1
        }
        return -1
    }
}
