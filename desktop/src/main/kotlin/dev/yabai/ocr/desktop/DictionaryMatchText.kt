package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * The line under a headword that says which word the meanings belong to (R46e).
 *
 * 兩膝 in the text is the old form of 両膝: the card shows the modern word and its meanings, and this
 * line is what keeps that from reading as an answer about the characters that were actually seen. The
 * hover and the result window draw the same line, from the same place.
 *
 * Null when the entry already is exactly the word that was read.
 */
internal fun dictionaryFormLine(match: TokenMatch): String? {
    if (match.surface == match.entry.term && !match.oldForm) return null
    val pair = "${match.surface} → ${match.entry.term}"
    return if (match.oldForm) "$pair · ${DesktopStrings.oldForm}" else pair
}
