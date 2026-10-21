package dev.yabai.ocr.overlay

import dev.yabai.ocr.core.dict.KanjiEntry
import dev.yabai.ocr.core.dict.TokenMatch

/** A word of the text with the reading the dictionary gives it. */
data class KanjiWord(val surface: String, val reading: String)

/**
 * What the card says about a kanji, taken from the dictionaries the panel already uses (R17).
 *
 * Kept free of Android and of the lookup engine, so the two decisions that are easy to get wrong —
 * *which* word covers the tapped character, and *which* words elsewhere on the page use it — are
 * fixed by plain tests instead of by looking at a phone.
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
        matches
            .asSequence()
            .filter { it.entry.reading.isNotBlank() && index >= it.start && index < it.end }
            .minByOrNull { it.end - it.start }
            ?.let { KanjiWord(it.surface, it.entry.reading) }

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
}
