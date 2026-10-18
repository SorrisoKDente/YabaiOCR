package dev.yabai.ocr.core.dict

/**
 * Finds dictionary words inside an unsegmented Japanese sentence.
 *
 * Japanese has no spaces, so "where does a word start" has to be answered by the search itself. The
 * strategy is greedy longest-match, with one important addition: exact hits are tried first and
 * deinflected hits only after, so a conjugated verb never beats the noun that happens to be written
 * the same way.
 *
 * The order matters for correctness, not just speed. 食べて is not a dictionary word, but it looks
 * enough like one that searching by length alone would happily cut 食べています in half; trying the
 * full surface first lets the deinflector turn the whole thing into 食べる.
 */
class LookupEngine(private val dictionaries: List<CompactDictionary>) {

    fun lookup(text: String, maxMatches: Int = 600): List<TokenMatch> {
        if (dictionaries.isEmpty() || text.isBlank()) return emptyList()

        val matches = ArrayList<TokenMatch>(64)
        val deinflectionCache = HashMap<String, List<Deinflector.Result>>()
        var index = 0

        while (index < text.length && matches.size < maxMatches) {
            val codePoint = text.codePointAt(index)
            val width = Character.charCount(codePoint)
            if (!isWordCodePoint(codePoint)) {
                index += width
                continue
            }

            val match = findMatch(text, index, deinflectionCache)
            if (match != null) {
                matches.add(match)
                index = match.end
            } else {
                index += width
            }
        }
        return matches
    }

    /**
     * Kanji breakdown, but only for a capture that is a single character.
     *
     * Showing a reading and stroke count for every kanji in a whole page of manga would bury the
     * definitions the panel exists to show; showing it for the one character you deliberately
     * framed is exactly what you want.
     */
    fun kanjiBreakdown(text: String, maxEntries: Int = 8): List<KanjiEntry> {
        val trimmed = text.trim()
        if (trimmed.length != 1) return emptyList()
        val out = ArrayList<KanjiEntry>(2)
        for (dictionary in dictionaries) {
            if (out.size >= maxEntries) break
            val entry = dictionary.kanjiEntry(trimmed) ?: continue
            out.add(entry)
        }
        return out
    }

    /** Titles of the dictionaries that actually contributed at least one hit. */
    fun contributedTitles(matches: List<TokenMatch>): List<String> =
        matches.map { it.dictionaryTitle }.distinct()

    /** Releases the blob file handles held by the dictionaries. */
    fun close() {
        for (dictionary in dictionaries) runCatching { dictionary.close() }
    }

    private fun findMatch(
        text: String,
        start: Int,
        cache: HashMap<String, List<Deinflector.Result>>,
    ): TokenMatch? {
        // A word never crosses punctuation, and stopping at the end of the run also keeps the
        // search window small. 私は昨日、 must not be considered one slice.
        var runEnd = start
        val scanLimit = minOf(text.length, start + MAX_TERM_LENGTH)
        while (runEnd < scanLimit) {
            val codePoint = text.codePointAt(runEnd)
            if (!isWordCodePoint(codePoint)) break
            runEnd += Character.charCount(codePoint)
        }
        val maxLength = minOf(MAX_TERM_LENGTH, runEnd - start)

        // Longest first, and exact before deinflected *at the same length*.
        //
        // The interleaving is the whole trick. Trying every exact length before any deinflection
        // looks harmless but breaks the most common case in the language: in 食べました the exact
        // single character 食 is a valid entry, so an exact-first order stops there and reports
        // 「食 + べ + ました」 instead of the verb 食べる.
        var length = maxLength
        while (length >= 1) {
            if (isCleanSlice(text, start, start + length)) {
                val surface = text.substring(start, start + length)

                exactMatch(surface, start, length)?.let { return it }

                if (length <= MAX_INFLECTED_LENGTH && endsWithKana(surface)) {
                    val candidates = cache.getOrPut(surface) { Deinflector.get().baseForms(surface) }
                    for (candidate in candidates) {
                        if (candidate.term.isEmpty()) continue
                        inflectedMatch(surface, start, length, candidate)?.let { return it }
                    }
                }
            }
            length--
        }

        return null
    }

    private fun exactMatch(surface: String, start: Int, length: Int): TokenMatch? {
        for (dictionary in dictionaries) {
            val entries = dictionary.termEntries(surface, allowedFlags = 0)
            if (entries.isEmpty()) continue
            return TokenMatch(
                surface = surface,
                start = start,
                end = start + length,
                entry = entries.first(),
                dictionaryTitle = dictionary.info.title,
                reasons = emptyList(),
            )
        }
        return null
    }

    private fun inflectedMatch(
        surface: String,
        start: Int,
        length: Int,
        candidate: Deinflector.Result,
    ): TokenMatch? {
        for (dictionary in dictionaries) {
            val entries = dictionary.termEntries(candidate.term, allowedFlags = candidate.rules)
            if (entries.isEmpty()) continue
            return TokenMatch(
                surface = surface,
                start = start,
                end = start + length,
                entry = entries.first(),
                dictionaryTitle = dictionary.info.title,
                reasons = candidate.reasons,
            )
        }
        return null
    }

    /**
     * Rejects slices that would split a surrogate pair, which would produce an unencodable key and
     * make the byte-wise binary search land on nonsense.
     */
    private fun isCleanSlice(text: String, from: Int, to: Int): Boolean {
        if (to <= from || to > text.length) return false
        return !Character.isHighSurrogate(text[to - 1])
    }

    private fun endsWithKana(text: String): Boolean {
        if (text.isEmpty()) return false
        val codePoint = text.codePointBefore(text.length)
        return codePoint in 0x3041..0x309F || codePoint in 0x30A1..0x30FA
    }

    companion object {
        /** Longest word worth testing; the longest entries in JMdict are well under this. */
        const val MAX_TERM_LENGTH = 16

        /** Conjugated forms are tried over a shorter window: nothing longer inflects usefully. */
        const val MAX_INFLECTED_LENGTH = 10

        fun isWordCodePoint(codePoint: Int): Boolean = when (codePoint) {
            in 0x3041..0x309F -> true // hiragana
            in 0x30A1..0x30FF -> true // katakana (includes ー and ・)
            in 0x31F0..0x31FF -> true // katakana phonetic extensions
            in 0x3400..0x4DBF -> true // CJK extension A
            in 0x4E00..0x9FFF -> true // CJK unified ideographs
            in 0xF900..0xFAFF -> true // CJK compatibility ideographs
            in 0xFF66..0xFF9F -> true // halfwidth katakana
            0x3005, 0x3006, 0x3007 -> true // 々 〆 〇
            in 0x20000..0x2FA1F -> true // CJK extensions B..F
            else -> false
        }
    }
}
