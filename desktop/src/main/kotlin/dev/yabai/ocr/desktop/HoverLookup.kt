package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.LookupEngine
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.jp.JapaneseText
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.core.lens.PixelBox
import java.awt.Point

/**
 * The dictionaries a filter selects; empty means all the installed ones.
 *
 * Kept in one place because both hover paths and the tests have to agree on what «no filter» means.
 */
internal fun dictionaryScope(filter: String): Set<String> =
    filter.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()

/**
 * The hover's lookup over [engine]: the engine signature — the match cap and the scope — lives in
 * one place instead of being rebuilt at every call site.
 */
internal fun hoverQuery(engine: LookupEngine): (String, Set<String>) -> List<TokenMatch> =
    { text, only -> engine.lookup(text, maxMatches = 96, only = only) }

/**
 * Looks the pointer up inside the chosen dictionary and nowhere else.
 *
 * There used to be a second search over every dictionary when the chosen one had no entry: the card
 * then answered with a definition from a dictionary the user had not chosen, with no sign that their
 * choice had been ignored. The chosen dictionary is the answer even when the answer is «not here» —
 * the caller says so ([dev.yabai.ocr.desktop.i18n.DesktopStrings.hoverDictionaryMissingEntry])
 * instead of silently changing the source.
 *
 * The lookup is injected rather than an engine taken directly: the engine signature (the match cap,
 * the scope) lives in [hoverQuery], and the geometry can be tested without a dictionary on disk.
 */
internal fun scopedHoverHit(
    result: LensResult,
    imagePoint: Point,
    lookup: (String, Set<String>) -> List<TokenMatch>,
    filter: String,
): HoverHit? = HoverTextSelector.select(result, imagePoint, lookup, dictionaryScope(filter))

/** What a dictionary change made in the card can answer from the reading it already has. */
internal sealed interface CachedHoverAnswer {
    /** The new dictionary has the word: the card shows this. */
    data class Hit(val hit: HoverHit) : CachedHoverAnswer

    /**
     * The new dictionary has no entry here: the card says so, naming the dictionary, and holds
     * [word] — the box of the Japanese word under the pointer, which keeps the card open.
     */
    data class MissingEntry(val filter: String, val word: PixelBox) : CachedHoverAnswer

    /** Nothing usable was kept: the pointer has to be read again. */
    data object NeedsLookup : CachedHoverAnswer
}

/**
 * Decides what the card shows after the user picks a dictionary, without touching the screen.
 *
 * With a reading in hand the answer is either the new dictionary's hit or «this dictionary has no
 * entry» — capturing the screen and running OCR again would only produce the same text, a second
 * round trip and a flicker. Only a missing reading (a card opened some other way, a monitor that
 * changed) or no filter at all sends the pointer back to the recogniser.
 */
internal fun cachedHoverAnswer(
    result: LensResult?,
    localPoint: Point?,
    lookup: ((String, Set<String>) -> List<TokenMatch>)?,
    filter: String,
): CachedHoverAnswer {
    if (result == null || localPoint == null || lookup == null) return CachedHoverAnswer.NeedsLookup

    val hit = HoverTextSelector.select(result, localPoint, lookup, dictionaryScope(filter))
    if (hit != null) return CachedHoverAnswer.Hit(hit)
    return when (val miss = classifyHoverMiss(result, localPoint, lookup, filter)) {
        is HoverMiss.MissingEntry -> CachedHoverAnswer.MissingEntry(miss.filter, miss.word)
        else -> CachedHoverAnswer.NeedsLookup
    }
}

/** The Japanese word under the pointer and its box, whichever dictionary — if any — knows it. */
internal data class HoverWord(val text: String, val box: PixelBox)

/**
 * The Japanese word the pointer is resting on, whatever dictionary it belongs to.
 *
 * The miss path needs two facts the dictionary lookup cannot give. One: whether there really is a
 * Japanese word under the pointer — without it the card said «this dictionary has no entry» over
 * every menu, paragraph and patch of desktop, and opened and closed on all of them. Two: *where*
 * that word is, because the card is held open by the word's box; with no anchor it counted as
 * «the pointer left» after the grace period, closed, and the gate fired again on the same spot.
 *
 * The pointer has to be **inside** the line's box. A tolerance around the line is what answers a
 * word under the pointer, but it is no reason to claim a word is missing from a dictionary when
 * the pointer is on nothing.
 */
internal fun japaneseWordAt(
    result: LensResult,
    imagePoint: Point,
    lookup: (String, Set<String>) -> List<TokenMatch>,
): HoverWord? {
    val segment = result.segments
        .filter { it.text.isNotBlank() && it.box != null }
        .map { it to HoverTextSelector.distanceToBox(imagePoint, it.box!!) }
        .filter { it.second <= 0.0 }
        .minByOrNull { it.second }
        ?.first
        ?: return null
    val box = segment.box ?: return null

    val index = HoverTextSelector.characterIndex(segment.text, box, imagePoint)
    if (!isJapaneseLetter(segment.text.getOrNull(index))) return null

    // The word when a dictionary knows it, the character's own cell otherwise: either way the
    // box is what holds the card open, and either way the pointer is inside it.
    val covering = lookup(segment.text, emptySet()).firstOrNull { index >= it.start && index < it.end }
    val token = covering?.let { HoverTextSelector.tokenBox(segment.text, box, it.start, it.end) }
        ?: HoverTextSelector.tokenBox(segment.text, box, index, index + 1)
    return HoverWord(covering?.surface ?: segment.text.substring(index, index + 1), token)
}

/** A character that is a word on its own: kana, kanji and the iteration mark — no punctuation. */
private fun isJapaneseLetter(character: Char?): Boolean {
    if (character == null) return false
    return JapaneseText.isKana(character.code) ||
        JapaneseText.isKanji(character.code) ||
        character == '々'
}

/** What a lookup found when no entry came back for the chosen dictionary. */
internal sealed interface HoverMiss {
    /** Nothing was recognised near the pointer: nothing to say, the card goes away. */
    data object NoText : HoverMiss

    /** A Japanese word under the pointer and no entry in the chosen dictionary: said in the card. */
    data class MissingEntry(val filter: String, val word: PixelBox) : HoverMiss

    /** Text under the pointer but no chosen dictionary to blame for it: the card goes away. */
    data class NoMatch(val text: String) : HoverMiss
}

/**
 * Decides what a miss means, with the pointer still on the spot that was read.
 *
 * «No entry in this dictionary» is only worth a card when the pointer is on a Japanese word. Over
 * the rest of the screen — a menu, a Portuguese paragraph, empty desktop — there is no entry to
 * look for, and saying so turned the card into something that flashed wherever the pointer went.
 */
internal fun classifyHoverMiss(
    result: LensResult,
    imagePoint: Point,
    lookup: (String, Set<String>) -> List<TokenMatch>,
    filter: String,
): HoverMiss {
    val word = japaneseWordAt(result, imagePoint, lookup)
    if (word == null) {
        val text = result.joinLines().trim()
        return if (text.isEmpty()) HoverMiss.NoText else HoverMiss.NoMatch(text)
    }
    return if (filter.isNotBlank()) HoverMiss.MissingEntry(filter, word.box) else HoverMiss.NoMatch(result.joinLines().trim())
}
