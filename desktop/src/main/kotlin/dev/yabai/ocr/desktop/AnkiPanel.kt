package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.desktop.i18n.DesktopStrings

/** What a panel says after a card went to Anki, and the note its «Desfazer» removes. */
internal data class AnkiPanelNotice(val text: String, val undoId: Long? = null)

/**
 * The one path from a panel to Anki.
 *
 * The result window, the quick dictionary and the hover card all send through here, so the three
 * buttons cannot disagree about what happened or how it is worded — including the deck the card
 * really went to, which is the sender's default when the setting is empty.
 *
 * The sender is a parameter so a test drives the whole path with a fake client, with no Anki
 * installed.
 */
internal fun sendToAnki(
    settings: DesktopSettings,
    note: AnkiNote,
    sender: AnkiSender = AnkiSender(settings),
): AnkiPanelNotice {
    val result = sender.send(note)
    val deck = settings.ankiDeck.ifBlank { AnkiSender.DEFAULT_DECK }
    return when (result.status) {
        AnkiSendStatus.ADDED -> AnkiPanelNotice(DesktopStrings.ankiAdded(deck), result.noteId)
        AnkiSendStatus.ALREADY_IN_ANKI -> AnkiPanelNotice(DesktopStrings.ankiAlreadyThere)
        AnkiSendStatus.QUEUED -> AnkiPanelNotice(DesktopStrings.ankiQueued(result.detail))
    }
}

/**
 * The card a per-word button makes.
 *
 * The quick dictionary and the hover card have no recognized sentence to keep — the word is all the
 * context there is — so the sentence field stays empty instead of repeating the word as if it were
 * its own example.
 */
internal fun wordNote(match: TokenMatch): AnkiNote = AnkiNote.word(match, sentence = "")
