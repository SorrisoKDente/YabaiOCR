package dev.yabai.ocr.core.dict

import dev.yabai.ocr.core.jp.JapaneseText

/**
 * Writes a reading after the kanji it belongs to: `職員さんに聞いた` → `職員（しょくいん）さんに聞（き）いた`.
 *
 * This is the other half of the furigana question. When the manga *prints* the reading beside the
 * kanji, it arrives as text and `RubyGeometry` is what finds it; when it does not print it — which is
 * most of the time — the reading is still known, because the dictionary engine already segmented the
 * sentence into words and every word it found carries its own reading. Nothing here guesses at how a
 * word is read; the only guesses are for a kanji that stands entirely alone and is not a word at all,
 * and those are marked and counted so the panel can say so.
 *
 * Where the reading is written is decided by the word, not by the sentence: the reading of 食べる is
 * `たべる`, but in 食べました only 食 takes the furigana — the `べました` is printed in the sentence
 * already. [split] is what works that out, and a word whose reading does not line up is left alone
 * rather than annotated wrongly.
 */
object Furigana {

    /** A kanji run inside a word, with the part of the reading that belongs to it. */
    data class Run(val kanji: String, val reading: String)

    /** The annotated sentence, and how many of its readings are guesses rather than words. */
    data class Annotated(val text: String, val uncertain: Int)

    /** One reading to write at a character position. */
    private data class Mark(val at: Int, val reading: String, val certain: Boolean)

    /** A stretch of the sentence: where it is and what it says. */
    private data class Span(val start: Int, val end: Int, val text: String)

    /** How many readings of a kanji standing on its own are worth writing before it is noise. */
    private const val MAX_LONE_READINGS = 3

    /**
     * How a dictionary separates the readings it lists in one field. KANJIDIC writes `テン デン`
     * (a space) and `よど.む` (a dot for where the okurigana starts); other builds use 「・」, and a
     * comma turns up in hand-made ones. Splitting on all of them costs nothing and means a reading
     * is never written with a separator inside it.
     */
    private val SEPARATORS = Regex("[\\s・，,、/]+")

    private const val OPEN = '（'
    private const val CLOSE = '）'

    /**
     * Splits [reading] into one reading per kanji run of [term]:
     *
     * - 食べる / たべる → 食 = た (the `べる` is printed in the sentence, so it is not annotated)
     * - 職員 / しょくいん → 職員 = しょくいん
     * - 申し込み / もうしこみ → 申 = もう, 込 = こ
     * - お客様 / おきゃくさま → 客様 = きゃくさま
     *
     * Returns null when the two do not line up — a deinflected hit whose surface lost kanji, or a
     * reading that is not kana at all. An annotation built on a guess about *where* the reading goes
     * would be worse than no annotation: it would teach the wrong character.
     */
    fun split(term: String, reading: String): List<Run>? {
        if (term.isEmpty()) return null
        if (reading.isNotEmpty() && !JapaneseText.isKanaOnly(reading)) return null

        val flat = normaliseKana(reading)
        val runs = ArrayList<Run>(2)
        var index = 0
        var position = 0

        while (index < term.length) {
            val codePoint = term.codePointAt(index)
            val width = Character.charCount(codePoint)

            if (!JapaneseText.isKanji(codePoint)) {
                // Kana printed inside the word translates itself: it takes no reading, but the
                // reading must have it right here or the split is not about this word at all.
                val kana = normaliseKana(term.substring(index, index + width))
                if (!flat.startsWith(kana, position)) return null
                position += kana.length
                index += width
                continue
            }

            var end = index
            while (end < term.length && JapaneseText.isKanji(term.codePointAt(end))) {
                end += Character.charCount(term.codePointAt(end))
            }

            // The run reads everything up to the next kana of the word, or to the end of it.
            val nextKana = kanaRunAfter(term, end)
            val stop = if (nextKana == null) {
                flat.length
            } else {
                val at = flat.indexOf(nextKana, position)
                if (at < 0) return null
                at
            }
            if (stop < position) return null

            runs.add(Run(term.substring(index, end), reading.substring(position, stop)))
            position = stop
            index = end
        }

        if (position != flat.length) return null
        return runs
    }

    /**
     * The sentence with a reading after every kanji run the dictionaries can account for.
     *
     * [matches] is the segmentation a lookup already produced; [kanji] answers for a single character
     * that is not a word (the KANJIDIC entries). A kanji run covered by a word takes the word's
     * reading and nothing else, even if the word covers only part of it: 職員室 with 職員 in the
     * dictionary is 職員（しょくいん）室, which is right.
     *
     * Returns null when there is nothing to say, so the panel can leave the section out entirely.
     */
    fun sentence(
        text: String,
        matches: List<TokenMatch>,
        kanji: (String) -> KanjiEntry? = { null },
    ): Annotated? {
        if (text.isEmpty() || !JapaneseText.hasKanji(text)) return null

        val marks = ArrayList<Mark>(8)
        val covered = BooleanArray(text.length)

        for (match in matches) {
            val start = match.start.coerceIn(0, text.length)
            val end = match.end.coerceIn(start, text.length)
            if (start >= end) continue

            val runs = split(match.entry.term, match.entry.reading) ?: continue
            val surface = kanjiRuns(text, start, end)
            // The word in the sentence must carry the same kanji, in the same runs, as the word in
            // the dictionary — otherwise the reading is being pinned to characters it does not read.
            if (surface.size != runs.size) continue
            if (runs.indices.any { surface[it].text != runs[it].kanji }) continue

            for (i in runs.indices) {
                if (runs[i].reading.isEmpty()) continue
                marks.add(Mark(surface[i].end, runs[i].reading, certain = true))
                for (at in surface[i].start until surface[i].end) covered[at] = true
            }
        }

        // Whatever no word explained. Two kanji read as if they were one word would be inventing a
        // word the dictionary does not have, so only a lone kanji is guessed at.
        for (run in kanjiRuns(text, 0, text.length)) {
            if ((run.start until run.end).any { covered[it] }) continue
            if (run.text.codePointCount(0, run.text.length) != 1) continue
            val readings = readingsOf(kanji(run.text) ?: continue)
            if (readings.isEmpty()) continue
            marks.add(Mark(run.end, readings.joinToString("・"), certain = false))
        }

        if (marks.isEmpty()) return null

        val out = StringBuilder(text.length + 8 * marks.size)
        var cursor = 0
        var uncertain = 0
        for (mark in marks.sortedBy { it.at }) {
            val at = mark.at.coerceIn(cursor, text.length)
            out.append(text, cursor, at)
            cursor = at
            // The reading is already in the sentence — the manga printed it, or the furigana keeper
            // wrote it. Saying it a second time would not add a reading, only noise.
            if (printsReading(text, at)) continue
            out.append(OPEN).append(mark.reading)
            if (!mark.certain) {
                out.append('?')
                uncertain++
            }
            out.append(CLOSE)
        }
        out.append(text, cursor, text.length)

        return Annotated(out.toString(), uncertain)
    }

    /**
     * The readings a kanji standing on its own could have, kun before on.
     *
     * A kanji alone in a sentence is read the Japanese way far more often than the Chinese way, so
     * 訓 first; and the kun readings are stripped of their okurigana marker (`た.べる` is た beside
     * the kanji, with べる printed in the sentence).
     */
    fun readingsOf(entry: KanjiEntry): List<String> =
        (kanaReadings(entry.kunYomi) + kanaReadings(entry.onYomi)).distinct().take(MAX_LONE_READINGS)

    // --- internals -------------------------------------------------------------------------------

    /** `み.る みられる` → [み, みられる]; anything that is not a reading is dropped. */
    private fun kanaReadings(raw: String): List<String> =
        raw.split(SEPARATORS)
            .map { it.substringBefore('.').trim().trim('-', '　', ' ') }
            .filter { it.isNotEmpty() && JapaneseText.isKanaOnly(it) }

    /** The maximal runs of kanji in `text[from, to)`; runs are clipped to the window. */
    private fun kanjiRuns(text: String, from: Int, to: Int): List<Span> {
        val out = ArrayList<Span>(4)
        var index = from
        while (index < to) {
            val codePoint = text.codePointAt(index)
            val width = Character.charCount(codePoint)
            if (!JapaneseText.isKanji(codePoint)) {
                index += width
                continue
            }
            val start = index
            index += width
            while (index < to && JapaneseText.isKanji(text.codePointAt(index))) {
                index += Character.charCount(text.codePointAt(index))
            }
            out.add(Span(start, index, text.substring(start, index)))
        }
        return out
    }

    /** The whole run of kana starting at [from], or null when the word has none there. */
    private fun kanaRunAfter(text: String, from: Int): String? {
        if (from >= text.length || !JapaneseText.isKana(text.codePointAt(from))) return null
        var end = from
        while (end < text.length && JapaneseText.isKana(text.codePointAt(end))) {
            end += Character.charCount(text.codePointAt(end))
        }
        return normaliseKana(text.substring(from, end))
    }

    /** Katakana and hiragana spell the same reading: タバコ and たばこ have to line up. */
    private fun normaliseKana(text: String): String = buildString(text.length) {
        for (character in text) {
            val code = character.code
            append(if (code in 0x30A1..0x30F6) (code - 0x60).toChar() else character)
        }
    }

    /** True when a reading is printed right here: `（しょくいん）`, and nothing else in brackets. */
    private fun printsReading(text: String, at: Int): Boolean {
        if (at >= text.length || text[at] != OPEN) return false
        var index = at + 1
        var kana = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (JapaneseText.isKana(codePoint)) {
                kana++
                index += Character.charCount(codePoint)
                continue
            }
            return codePoint == CLOSE.code && kana > 0
        }
        return false
    }
}
