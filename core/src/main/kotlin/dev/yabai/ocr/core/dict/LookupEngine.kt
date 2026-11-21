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
 *
 * The engine holds every dictionary it was built with; a lookup can be narrowed to one of them by
 * title, which is how the panel's selector changes its mind without reloading a single index.
 */
class LookupEngine(private val dictionaries: List<CompactDictionary>) {

    /** Translation sidecars, keyed by dictionary folder; built on first use (see [searchTranslation]). */
    private val translationIndexes = HashMap<String, TranslationIndex>()
    private val translationLock = Any()

    /** [only] narrows the search to those dictionary titles; empty means every dictionary held. */
    fun lookup(text: String, maxMatches: Int = 600, only: Set<String> = emptySet()): List<TokenMatch> {
        val usable = selected(only)
        if (usable.isEmpty() || text.isBlank()) return emptyList()

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

            val match = findMatch(text, index, usable, deinflectionCache)
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
     * A dictionary **query**, as opposed to reading a sentence (R31).
     *
     * A search box has one word in it, and the greedy segmentation that is right for a sentence is
     * wrong for a whole query that happens to be a reading: にほん must answer 日本, not the particle
     * に followed by ほ. So the whole query is tried as a headword or reading first, and only a query
     * that is not a word at all — a conjugated form, a phrase — falls back to [lookup].
     *
     * Romaji é convertido para hiragana (`nihon` → `にほん`) para que a consulta possa ser procurada
     * como leitura nos dicionários (R31 + romaji).
     */
    fun search(text: String, maxMatches: Int = 60, only: Set<String> = emptySet()): List<TokenMatch> {
        val query = text.trim()
        if (query.isEmpty()) return emptyList()
        val usable = selected(only)
        if (usable.isEmpty()) return emptyList()

        val hiragana = Romaji.toHiragana(query)
        val converted = hiragana != query

        val results = LinkedHashMap<String, TokenMatch>()

        fun addMatches(matches: List<TokenMatch>) {
            for (m in matches) {
                val key = "${m.entry.term}\u0000${m.entry.reading}\u0000${m.dictionaryTitle}"
                if (!results.containsKey(key)) {
                    results[key] = m
                }
            }
        }

        addMatches(allExactMatches(query, 0, query.length, usable))
        if (converted) {
            addMatches(allExactMatches(hiragana, 0, hiragana.length, usable))
            for (term in suggest(hiragana, maxSuggestions = maxMatches, only = only, includeReadingMatch = true)) {
                addMatches(allExactMatches(term, 0, query.length, usable))
            }
        }

        if (results.isNotEmpty()) {
            return results.values.take(maxMatches)
        }

        return lookup(query, maxMatches, only)
    }

    /**
     * Headwords that begin with [text], for the search box's suggestions (R31).
     *
     * The index holds headwords and readings together, so 日本 offers 日本語 and 日本人, while にほん
     * reaches the same compounds through their readings. The query itself is left out as a headword
     * (it is already the answer above the suggestions), but when [includeReadingMatch] is true,
     * entries whose **reading** equals the query are kept — that is how a romaji query like `nihon`
     * finds 日本 (reading `にほん`).
     *
     * [only] narrows exactly like [lookup] — a suggestion must not come from a dictionary the user
     * told the panel to leave out.
     */
    fun suggest(
        text: String,
        maxSuggestions: Int = 10,
        only: Set<String> = emptySet(),
        includeReadingMatch: Boolean = false,
    ): List<String> {
        val query = text.trim()
        if (query.isEmpty() || maxSuggestions <= 0) return emptyList()
        val usable = selected(only)
        if (usable.isEmpty()) return emptyList()

        val found = LinkedHashMap<String, DictionaryEntry>()
        for (dictionary in usable) {
            // A prefix can be shared by many readings, so a few keys are read beyond the answer
            // size; entries are deduplicated by headword as they come.
            for (key in dictionary.keysStartingWith(query, maxSuggestions * 8)) {
                for (entry in dictionary.termEntries(key)) {
                    if (entry.term == query) continue
                    if (!includeReadingMatch && entry.reading == query) continue
                    if (!entry.term.startsWith(query) && !entry.reading.startsWith(query)) continue
                    found.putIfAbsent(entry.term, entry)
                    if (found.size >= maxSuggestions * CANDIDATE_FACTOR) break
                }
                if (found.size >= maxSuggestions * CANDIDATE_FACTOR) break
            }
        }
        // The dictionaries put their own commonness in the score (JMdict: 1999800 for a news1/ichi1
        // word, 999800 for the second tier, negative for the rest; Jitendex PT-BR: 200 / 0 / -1), so
        // it decides first. The byte order of the index would otherwise put 日本鼬, 日本鶉, 日本鰻 …
        // first just because their kanji have higher code points. Length and reading break the ties:
        // the nearest words, in the order a Japanese dictionary lists them.
        return found.values
            .sortedWith(
                compareByDescending<DictionaryEntry> { it.score }
                    .thenBy { it.term.length }
                    .thenBy { it.reading.ifEmpty { it.term } }
                    .thenBy { it.term },
            )
            .take(maxSuggestions)
            .map { it.term }
    }

    /**
     * Search the **definitions** instead of the headwords: «sword» finds 剣, 刀, サーベル.
     *
     * The normal [search] follows the sorted key index, so it only answers queries written in
     * Japanese (or romaji). A reader who knows the meaning but not the word needs the opposite
     * direction, and that cannot be answered by a key index: the definition has to be read. This
     * walks a sidecar inverted index ([TranslationIndex]) built once per dictionary, so only the
     * first query on a large dictionary pays the scan; later ones read just the postings of the
     * words they asked for.
     *
     * Every query token must be present in the definition (AND), because matching any word of
     * «espada longa» would return most of the dictionary. The result is ordered by the dictionary's
     * own commonness score, exactly like [suggest].
     *
     * [onProgress] reports the one-time index build, which is slow enough to need a line in the UI.
     */
    fun searchTranslation(
        text: String,
        maxMatches: Int = 60,
        only: Set<String> = emptySet(),
        onProgress: (TranslationProgress) -> Unit = {},
    ): List<TokenMatch> {
        val query = text.trim()
        if (query.isEmpty() || maxMatches <= 0) return emptyList()
        val tokens = TranslationTokens.tokenize(query).distinct()
        if (tokens.isEmpty()) return emptyList()
        val usable = selected(only)
        if (usable.isEmpty()) return emptyList()

        val matches = ArrayList<TokenMatch>(maxMatches)
        for (dictionary in usable) {
            val index = translationIndex(dictionary, onProgress) ?: continue
            // Intersect the postings, smallest first: the rarest token usually cuts the candidate
            // set down before the others are ever read.
            val postings = tokens
                .map { index.postings(it) }
                .sortedBy { it.size }
            if (postings.any { it.isEmpty() }) continue
            var ids = postings.first()
            for (other in postings.drop(1)) ids = intersect(ids, other)
            if (ids.isEmpty()) continue

            // Decoding a blob is the expensive part, and a common word («water») can be in tens of
            // thousands of definitions. The cap keeps a keystroke bounded; the exact order inside
            // the cap is settled by the commonness score below.
            val scan = minOf(ids.size, TRANSLATION_SCAN_LIMIT)
            for (i in 0 until scan) {
                val entry = dictionary.entryById(ids[i]) ?: continue
                matches.add(
                    TokenMatch(
                        surface = entry.term,
                        start = 0,
                        end = query.length,
                        entry = entry,
                        dictionaryTitle = dictionary.info.title,
                        reasons = emptyList(),
                    ),
                )
            }
        }

        return matches
            .sortedWith(
                compareByDescending<TokenMatch> { translationRelevance(it, query) }
                    .thenByDescending { it.entry.score }
                    .thenBy { it.entry.term.length }
                    .thenBy { it.entry.reading.ifEmpty { it.entry.term } }
                    .thenBy { it.entry.term },
            )
            .take(maxMatches)
    }

    private fun translationRelevance(match: TokenMatch, query: String): Int {
        val normQuery = TranslationTokens.normalize(query).trim()
        val def = TranslationTokens.normalize(match.entry.definition).trim()
        if (def == normQuery || def == "1. $normQuery" || def.startsWith("$normQuery\n") || def.startsWith("1. $normQuery\n") || def.startsWith("1. $normQuery;")) return 3
        if (def.contains("\n$normQuery\n") || def.contains("\n1. $normQuery")) return 2
        return 1
    }

    /** Sorted-array intersection; both inputs are ascending entry ids. */
    private fun intersect(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(minOf(a.size, b.size))
        var i = 0
        var j = 0
        var size = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] < b[j] -> i++
                a[i] > b[j] -> j++
                else -> {
                    out[size++] = a[i]
                    i++
                    j++
                }
            }
        }
        return out.copyOf(size)
    }

    /** The sidecar index for one dictionary, built on first use and kept for the engine's life. */
    private fun translationIndex(
        dictionary: CompactDictionary,
        onProgress: (TranslationProgress) -> Unit,
    ): TranslationIndex? = synchronized(translationLock) {
        val key = dictionary.dir.absolutePath
        translationIndexes[key]?.let {
            onProgress(TranslationProgress(dictionary.info.title, 1f, building = false))
            return it
        }
        val built = runCatching { TranslationIndex.ensure(dictionary, onProgress) }.getOrNull()
        if (built != null) translationIndexes[key] = built
        built
    }

    /**
     * Kanji breakdown, but only for a capture that is a single character.
     *
     * Showing a reading and stroke count for every kanji in a whole page of manga would bury the
     * definitions the panel exists to show; showing it for the one character you deliberately
     * framed is exactly what you want. [only] narrows it like [lookup] does, with one exception that
     * [kanjiSources] explains: a narrowing that holds no kanji dictionary cannot answer, so it is
     * widened instead of answering nothing (R28).
     */
    fun kanjiBreakdown(
        text: String,
        maxEntries: Int = 8,
        only: Set<String> = emptySet(),
    ): List<KanjiEntry> {
        val trimmed = text.trim()
        if (trimmed.length != 1) return emptyList()
        val out = ArrayList<KanjiEntry>(2)
        for (dictionary in kanjiSources(only)) {
            if (out.size >= maxEntries) break
            val entry = dictionary.kanjiEntry(trimmed) ?: continue
            out.add(entry)
        }
        return out
    }

    /**
     * The word's reading divided per kanji — 同時に どうじに → 同 どう, 時 じ (R29).
     *
     * The division is [Furigana.perKanji]'s: only one that adds up to the word's reading, and null
     * when there is none. [only] picks the dictionaries like [kanjiBreakdown] does, widening a
     * narrowing that holds no kanji dictionary.
     */
    fun wordReadings(
        term: String,
        reading: String,
        only: Set<String> = emptySet(),
    ): List<Furigana.Run>? {
        val sources = kanjiSources(only)
        return Furigana.perKanji(term, reading) { character ->
            sources.mapNotNull { it.kanjiEntry(character) }
                .flatMap { Furigana.allReadings(it) }
                .distinct()
        }
    }

    /**
     * The sentence with a reading written after each kanji the dictionaries can account for:
     *
     *     職員さんに聞いた   →   職員（しょくいん）さんに聞（き）いた
     *
     * [matches] is the segmentation [lookup] already produced — the reading of a word is part of the
     * hit, so nothing is looked up twice. Kanji that no word covered fall back to the kanji entries,
     * which is the only guess in the whole path, and [only] narrows that fallback like it narrows
     * [lookup]: the panel cannot be told to read one dictionary and be answered by another. The one
     * exception is [kanjiSources], which also keeps a word-only narrowing from silencing a reading
     * the panel never offered to hide (R28).
     */
    fun withFurigana(
        text: String,
        matches: List<TokenMatch>,
        only: Set<String> = emptySet(),
    ): Furigana.Annotated? = Furigana.sentence(text, matches) { character ->
        kanjiSources(only).firstNotNullOfOrNull { it.kanjiEntry(character) }
    }

    /**
     * The same readings [withFurigana] writes, with the characters each one sits on.
     *
     * For a caller that draws them where the text is — the picker, beside the kanji on the page —
     * rather than writing them into a sentence.
     */
    fun readings(
        text: String,
        matches: List<TokenMatch>,
        only: Set<String> = emptySet(),
    ): List<Furigana.Reading> = Furigana.spans(text, matches) { character ->
        kanjiSources(only).firstNotNullOfOrNull { it.kanjiEntry(character) }
    }

    /** Titles of the dictionaries that actually contributed at least one hit. */
    fun contributedTitles(matches: List<TokenMatch>): List<String> =
        matches.map { it.dictionaryTitle }.distinct()

    /** The dictionaries a lookup may consult: the named ones, or all of them when none is named. */
    private fun selected(only: Set<String>): List<CompactDictionary> =
        if (only.isEmpty()) dictionaries else dictionaries.filter { it.info.title in only }

    /**
     * The dictionaries that can answer a question about a kanji, given the user's narrowing (R28).
     *
     * The narrowing exists so the two sections of the panel speak with one voice: words and kanji
     * from the dictionary the user picked. But a dictionary with no kanji entries cannot answer at
     * all, and its silence is not a choice anybody made — nobody narrows the panel to JMdict in
     * order to read a kanji. So a narrowing that holds **no** kanji dictionary is widened to every
     * dictionary that has one, while a narrowing that holds a kanji dictionary is kept exactly as it
     * is: then the choice is real, and 「só o KANJIDIC em português» must stay silent about the rare
     * kanji that build does not carry.
     */
    private fun kanjiSources(only: Set<String>): List<CompactDictionary> {
        val chosen = selected(only)
        if (chosen.any { it.info.kanjiCount > 0 }) return chosen
        return dictionaries.filter { it.info.kanjiCount > 0 }
    }

    /** Releases the blob file handles held by the dictionaries. */
    fun close() {
        synchronized(translationLock) {
            for (index in translationIndexes.values) runCatching { index.close() }
            translationIndexes.clear()
        }
        for (dictionary in dictionaries) runCatching { dictionary.close() }
    }

    private fun findMatch(
        text: String,
        start: Int,
        usable: List<CompactDictionary>,
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

                exactMatch(surface, start, length, usable)?.let { return it }

                if (length <= MAX_INFLECTED_LENGTH && endsWithKana(surface)) {
                    val candidates = cache.getOrPut(surface) { Deinflector.get().baseForms(surface) }
                    for (candidate in candidates) {
                        if (candidate.term.isEmpty()) continue
                        inflectedMatch(surface, start, length, candidate, usable)?.let { return it }
                    }
                }
            }
            length--
        }

        return null
    }

    private fun allExactMatches(
        surface: String,
        start: Int,
        length: Int,
        usable: List<CompactDictionary>,
    ): List<TokenMatch> {
        val out = ArrayList<TokenMatch>()
        for (dictionary in usable) {
            val entries = dictionary.termEntries(surface, allowedFlags = 0)
            for (found in entries) {
                val (entry, oldForm) = follow(dictionary, found)
                out.add(
                    TokenMatch(
                        surface = surface,
                        start = start,
                        end = start + length,
                        entry = entry,
                        dictionaryTitle = dictionary.info.title,
                        reasons = emptyList(),
                        oldForm = oldForm,
                    ),
                )
            }
        }
        return out
    }

    private fun exactMatch(
        surface: String,
        start: Int,
        length: Int,
        usable: List<CompactDictionary>,
    ): TokenMatch? {
        for (dictionary in usable) {
            val entries = dictionary.termEntries(surface, allowedFlags = 0)
            if (entries.isEmpty()) continue
            val (entry, oldForm) = follow(dictionary, entries.first())
            return TokenMatch(
                surface = surface,
                start = start,
                end = start + length,
                entry = entry,
                dictionaryTitle = dictionary.info.title,
                reasons = emptyList(),
                oldForm = oldForm,
            )
        }
        return null
    }

    /**
     * The entry a redirect only points at, resolved inside the same dictionary (R46e).
     *
     * 兩膝 is the old form; the meanings the reader wants are 両膝's, and they are in the dictionary
     * that answered — crossing to another one would answer with a different revision and a different
     * rendering. When the target is missing, or is itself a redirect, the entry is left as it came: a
     * card that says «old kanji form» is worse than one with the meanings, but better than nothing.
     */
    private fun follow(
        dictionary: CompactDictionary,
        entry: DictionaryEntry,
    ): Pair<DictionaryEntry, Boolean> {
        val target = entry.redirect
        if (target.isEmpty() || target == entry.term) return entry to false
        val modern = dictionary.termEntries(target).firstOrNull { it.redirect.isEmpty() }
            ?: return entry to false
        if (modern.term == entry.term) return entry to false
        return modern to true
    }

    private fun inflectedMatch(
        surface: String,
        start: Int,
        length: Int,
        candidate: Deinflector.Result,
        usable: List<CompactDictionary>,
    ): TokenMatch? {
        for (dictionary in usable) {
            val entries = dictionary.termEntries(candidate.term, allowedFlags = candidate.rules)
            if (entries.isEmpty()) continue
            val (entry, oldForm) = follow(dictionary, entries.first())
            return TokenMatch(
                surface = surface,
                start = start,
                end = start + length,
                entry = entry,
                dictionaryTitle = dictionary.info.title,
                reasons = candidate.reasons,
                oldForm = oldForm,
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

        /** How many suggestions are looked at before the order is decided; see [suggest]. */
        private const val CANDIDATE_FACTOR = 8

        /** Definition hits decoded per keystroke before [searchTranslation] settles the order. */
        private const val TRANSLATION_SCAN_LIMIT = 2_000

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
