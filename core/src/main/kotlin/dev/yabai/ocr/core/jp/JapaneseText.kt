package dev.yabai.ocr.core.jp

/**
 * Cleanup pass for Japanese OCR output.
 *
 * Word-level recognisers insert ASCII spaces between tokens, which is right for Latin script but
 * wrong for Japanese: 「29巻 きっとまいく!」 should come back as 「29巻きっとまいく!」. This removes
 * space runs whose neighbours are both CJK, and leaves spaces that separate Latin words intact.
 */
object JapaneseText {

    private val SPACE_RUN = Regex("[ \\t\\u3000]+")

    fun normalize(raw: String): String {
        if (raw.isEmpty()) return raw

        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == ' ' || c == '\t' || c == '\u3000') {
                var j = i
                while (j < raw.length && (raw[j] == ' ' || raw[j] == '\t' || raw[j] == '\u3000')) j++
                val previous = out.lastOrNull()
                val next = raw.getOrNull(j)
                if (previous != null && next != null && isCjk(previous) && isCjk(next)) {
                    // Drop the whole run: Japanese does not separate words with spaces.
                } else {
                    out.append(' ')
                }
                i = j
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** Collapses space runs and trims, without the CJK-aware joining. */
    fun tidyLines(raw: String): String = raw
        .lineSequence()
        .map { SPACE_RUN.replace(it, " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    fun isCjk(ch: Char): Boolean {
        val c = ch.code
        return c in 0x3000..0x303F || // CJK symbols and punctuation
            c in 0x3040..0x309F || // hiragana
            c in 0x30A0..0x30FF || // katakana
            c in 0x31F0..0x31FF || // katakana phonetic extensions
            c in 0x3400..0x4DBF || // CJK unified ideographs extension A
            c in 0x4E00..0x9FFF || // CJK unified ideographs
            c in 0xF900..0xFAFF || // CJK compatibility ideographs
            c in 0xFF00..0xFFEF || // halfwidth and fullwidth forms
            c in 0x20000..0x2FA1F // CJK extensions B..F (supplementary planes)
    }

    /**
     * True for the two kana syllabaries and the marks printed with them.
     *
     * This is what a furigana (a reading) is made of, and what a base line never is: a reading is
     * kana and the kanji it annotates is not.
     */
    fun isKana(codePoint: Int): Boolean =
        codePoint in 0x3041..0x309F || // hiragana
            codePoint in 0x30A1..0x30FA || // katakana
            codePoint in 0x30FC..0x30FE || // ー prolonged sound mark, ヽヾ iteration marks
            codePoint in 0x31F0..0x31FF // katakana phonetic extensions

    /** True for CJK ideographs — the characters a reading can be printed beside. */
    fun isKanji(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF || // extension A
            codePoint in 0x4E00..0x9FFF || // CJK unified ideographs
            codePoint in 0xF900..0xFAFF || // compatibility ideographs
            codePoint in 0x20000..0x2FA1F // extensions B..F

    /** True when every character is kana, i.e. the line could be a reading. */
    fun isKanaOnly(text: String): Boolean =
        text.isNotEmpty() && text.codePoints().allMatch { isKana(it) }

    /** True when the text has at least one kanji, i.e. it is something a reading can hang off. */
    fun hasKanji(text: String): Boolean = text.codePoints().anyMatch { isKanji(it) }
}
