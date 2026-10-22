package dev.yabai.ocr.overlay

import dev.yabai.ocr.R
import dev.yabai.ocr.core.dict.Furigana
import dev.yabai.ocr.core.dict.KanjiEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.jp.JapaneseText

/** A word of the text with the reading the dictionary gives it. */
data class KanjiWord(val surface: String, val reading: String)

/** One kanji of a word and the part of the word's reading that belongs to it (R29). */
data class KanjiPart(val kanji: String, val reading: String)

/**
 * What the card says about a kanji, taken from the dictionaries the panel already uses (R17).
 *
 * Kept free of the lookup engine and of Android widgets, so the decisions that are easy to get wrong
 * — *which* word covers the tapped character, *which* words elsewhere on the page use it, and *what
 * to say when no kanji dictionary answered* — are fixed by plain tests instead of by looking at a
 * phone. Only resource **ids** are named here, never a `Context`.
 */
object KanjiCardText {

    /**
     * The word that covers character [index] of [text], or null when the dictionary found none.
     *
     * A sentence has overlapping hits — a whole compound and the single kanji inside it — and the
     * reading that belongs to the character is the **narrowest** one: for 職員, tapping 職 is asking
     * about 職員 (しょくいん), not about a 600-character stretch that happens to contain it.
     */
    fun wordAt(matches: List<TokenMatch>, index: Int): KanjiWord? =
        narrowestAt(matches, index)?.let { KanjiWord(it.surface, it.entry.reading) }

    /**
     * The kanji around [index] inside the word that reads it, or null when it is that character alone
     * (R29).
     *
     * The card speaks about the word — 同時に, read どうじに — and a mark over the 同 alone says the
     * opposite. The kana of the word are left out of the mark: they take no reading, and including に
     * would claim it is part of what is being read.
     */
    fun kanjiSpan(text: String, matches: List<TokenMatch>, index: Int): IntRange? {
        if (index !in text.indices) return null
        if (!JapaneseText.isKanji(text[index].code)) return null
        val word = narrowestAt(matches, index) ?: return null
        var start = index
        var end = index + 1
        while (start > word.start && JapaneseText.isKanji(text[start - 1].code)) start--
        while (end < word.end && end < text.length && JapaneseText.isKanji(text[end].code)) end++
        return if (end - start > 1) start until end else null
    }

    /**
     * The other words of the page that use [character], as «surface reading», without repeats.
     *
     * The compiled dictionary is indexed by headword, so «the most common compounds of a kanji» would
     * need an index this app does not keep. The words of the page are the honest version of the same
     * idea — they are compounds in the text the user is reading, and they come from the same lookup
     * that is already being drawn.
     */
    fun compounds(matches: List<TokenMatch>, character: String, limit: Int = 6): List<KanjiWord> =
        matches
            .asSequence()
            .filter { it.entry.reading.isNotBlank() && it.surface.contains(character) }
            .map { KanjiWord(it.surface, it.entry.reading) }
            .distinctBy { it.surface to it.reading }
            .take(limit)
            .toList()

    /** The on'yomi as the dictionary writes them, ready for the card; empty when it has none. */
    fun onYomi(entry: KanjiEntry?): String = entry?.onYomi?.trim().orEmpty()

    /** The kun'yomi as the dictionary writes them, ready for the card; empty when it has none. */
    fun kunYomi(entry: KanjiEntry?): String = entry?.kunYomi?.trim().orEmpty()

    /** The dictionary's own text for the kanji — meanings and statistics — trimmed. */
    fun meaning(entry: KanjiEntry?): String = entry?.body?.trim().orEmpty()

    /**
     * «同 どう · 時 じ» — the word's reading, one part per kanji, or empty when the card should not
     * show a division (R29).
     *
     * Worth a line only when there is more than one kanji to tell apart and every part is exactly one
     * kanji: a part holding two kanji says the word again (大人 おとな), which the header already says.
     */
    fun parts(runs: List<Furigana.Run>?): List<KanjiPart> {
        val parts = runs.orEmpty().map { KanjiPart(it.kanji, it.reading) }
        val usable = parts.size >= 2 &&
            parts.all { it.kanji.length == 1 && it.reading.isNotEmpty() }
        return if (usable) parts else emptyList()
    }

    /** The narrowest hit covering [index] — the word whose reading is the character's, not the page's. */
    private fun narrowestAt(matches: List<TokenMatch>, index: Int): TokenMatch? =
        matches
            .asSequence()
            .filter { it.entry.reading.isNotBlank() && index >= it.start && index < it.end }
            .minByOrNull { it.end - it.start }

    /**
     * What to say instead of the dictionary section when no kanji entry came back (R28).
     *
     * «Nenhum dicionário respondeu a este caractere» was true and useless: it is the same sentence
     * whether the user never installed a kanji dictionary, has one switched off, or simply picked a
     * word dictionary in the panel. [installed] and [enabled] come from the installed list, and the
     * first two cases are actionable, so they get their own sentence.
     */
    fun missingNote(installed: Boolean, enabled: Boolean): Int = when {
        !installed -> R.string.kanji_dictionary_missing
        !enabled -> R.string.kanji_dictionary_disabled
        else -> R.string.kanji_no_dictionary
    }
}
