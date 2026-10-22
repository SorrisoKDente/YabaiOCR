package dev.yabai.ocr.dict

/**
 * Which installed dictionary answers the kanji card by default (R30).
 *
 * The card asks two different questions — the kanji entry (音/訓, meanings) and the reading of each
 * kanji inside a compound — and only a dictionary with kanji data can answer either. The user can
 * choose one of them in «Dicionários»; the choice is stored as the installed **id**, and this object
 * turns it into the **title** the [dev.yabai.ocr.core.dict.LookupEngine] filters by, or into null for
 * «todos».
 *
 * A stored id that no longer names an enabled kanji dictionary is not an error and not a silent
 * choice either: it falls back to every kanji dictionary, because answering nothing would look like
 * a broken card. The screen that offers the choice clears the stale value when it notices it.
 */
object KanjiDefault {

    /** The title of the chosen dictionary, or null when every kanji dictionary may answer. */
    fun titleOf(installed: List<InstalledDictionary>, storedId: String): String? {
        if (storedId.isBlank()) return null
        return installed
            .firstOrNull { it.id == storedId && it.enabled && it.info.kanjiCount > 0 }
            ?.info
            ?.title
    }

    /** The enabled dictionaries that can answer a question about a kanji, in priority order. */
    fun candidates(installed: List<InstalledDictionary>): List<InstalledDictionary> =
        installed.filter { it.enabled && it.info.kanjiCount > 0 }
}
