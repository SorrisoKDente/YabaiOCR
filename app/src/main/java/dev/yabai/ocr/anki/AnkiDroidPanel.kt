package dev.yabai.ocr.anki

import android.content.Context
import dev.yabai.ocr.R
import dev.yabai.ocr.core.anki.AnkiNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one path from an Android panel to AnkiDroid.
 *
 * The result panel and the dictionary panel both send through here, so they cannot disagree about
 * what happened or how it is worded. The overlay calls happen on the main thread; the provider work
 * happens off it.
 */
class AnkiDroidPanel(
    context: Context,
    private val scope: CoroutineScope,
    private val show: (AnkiDroidNotice) -> Unit,
) {
    private val appContext = context.applicationContext
    private val sender = AnkiDroidSender(appContext)

    fun send(
        note: AnkiNote,
        label: String,
        deck: String = AnkiDroidGateway.DEFAULT_DECK,
    ) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { sender.send(note, deck) }
            show(outcome.notice(appContext, label, deck) { noteId -> undo(noteId) })
        }
    }

    /** Retries the queue when a service starts; no notice, because no panel may be open. */
    fun retryQueued() {
        scope.launch(Dispatchers.IO) { sender.retryQueued() }
    }

    private fun undo(noteId: Long) {
        scope.launch {
            val undone = withContext(Dispatchers.IO) { sender.undo(noteId) }
            show(
                AnkiDroidNotice(
                    appContext.getString(
                        if (undone) R.string.anki_undone else R.string.anki_undo_failed,
                    ),
                ),
            )
        }
    }
}
