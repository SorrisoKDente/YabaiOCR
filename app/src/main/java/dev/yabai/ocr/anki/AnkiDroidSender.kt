package dev.yabai.ocr.anki

import android.content.Context
import dev.yabai.ocr.R
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiQueue
import java.io.File

/** What happened to a card handed to the AnkiDroid sender. */
sealed interface AnkiDroidOutcome {
    data class Added(val noteId: Long) : AnkiDroidOutcome
    data object Duplicate : AnkiDroidOutcome
    data class Queued(val failure: AnkiDroidFailure) : AnkiDroidOutcome
    data class Failed(val failure: AnkiDroidFailure) : AnkiDroidOutcome
}

/** Text for the panel, with the «Desfazer» action when the card can still be removed. */
data class AnkiDroidNotice(val text: String, val undo: (() -> Unit)? = null)

/**
 * Sends a card now and, when it cannot, keeps it for later.
 *
 * The queue is written **before** the attempt, so closing the app while AnkiDroid answers does not
 * lose the card; it is removed only after the provider confirmed it. The two operations are injected
 * so the delivery rules — dedupe, queue, retry, undo — are tested without AnkiDroid.
 */
class AnkiDroidSender(
    private val queue: AnkiQueue,
    private val deliver: (AnkiNote, String, String) -> AnkiDroidSendResult,
    private val removeNote: (Long) -> Unit,
) {

    constructor(context: Context) : this(
        AnkiQueue(File(context.filesDir, QUEUE_FILE)),
        { note, deck, model -> AnkiDroidGateway(context).send(note, deck, model) },
        { noteId -> AnkiDroidGateway(context).deleteNote(noteId) },
    )

    fun send(
        note: AnkiNote,
        deck: String = AnkiDroidGateway.DEFAULT_DECK,
        model: String = AnkiDroidGateway.DEFAULT_MODEL,
    ): AnkiDroidOutcome {
        val queued = queue.enqueue(deck, model, note)
        return try {
            val result = deliver(queued.note, deck, model)
            queue.remove(queued.id)
            if (result.duplicate) AnkiDroidOutcome.Duplicate
            else AnkiDroidOutcome.Added(result.noteId)
        } catch (e: AnkiDroidException) {
            // A missing field is a note type the user has to fix; retrying it unchanged would fail
            // forever. Everything else is kept, because the provider may be available next time.
            if (e.failure == AnkiDroidFailure.MISSING_FIELD) {
                queue.remove(queued.id)
                AnkiDroidOutcome.Failed(e.failure)
            } else {
                queue.markFailed(queued.id, e.message.orEmpty())
                AnkiDroidOutcome.Queued(e.failure)
            }
        } catch (e: Exception) {
            queue.markFailed(queued.id, e.message.orEmpty())
            AnkiDroidOutcome.Queued(AnkiDroidFailure.INSERT_FAILED)
        }
    }

    /** Sends everything that is waiting; returns how many cards arrived. */
    fun retryQueued(): Int = queue.pending().count { entry ->
        try {
            val result = deliver(entry.note, entry.deck, entry.model)
            queue.remove(entry.id)
            !result.duplicate
        } catch (e: AnkiDroidException) {
            if (e.failure == AnkiDroidFailure.MISSING_FIELD) queue.remove(entry.id)
            else queue.markFailed(entry.id, e.message.orEmpty())
            false
        } catch (e: Exception) {
            queue.markFailed(entry.id, e.message.orEmpty())
            false
        }
    }

    /** The «Desfazer» of a card that went to AnkiDroid; false when the provider refused. */
    fun undo(noteId: Long): Boolean = runCatching { removeNote(noteId) }.isSuccess

    private companion object {
        /** Where the cards wait, beside the settings, so a backup carries both. */
        const val QUEUE_FILE = "anki-queue.jsonl"
    }
}

/**
 * The sentence for [AnkiDroidOutcome], in the user's language.
 *
 * The failure is mapped to a localized reason instead of printing the provider's English exception
 * message: the panel is in Portuguese or English, and a raw message would switch language mid-card.
 */
fun AnkiDroidOutcome.notice(
    context: Context,
    label: String,
    deck: String,
    onUndo: (Long) -> Unit,
): AnkiDroidNotice = when (this) {
    is AnkiDroidOutcome.Added -> AnkiDroidNotice(
        context.getString(R.string.anki_added, label, deck),
        undo = { onUndo(noteId) },
    )

    AnkiDroidOutcome.Duplicate -> AnkiDroidNotice(
        context.getString(R.string.anki_already, label),
    )

    is AnkiDroidOutcome.Queued -> AnkiDroidNotice(
        context.getString(R.string.anki_queued, label, reason(context, failure)),
    )

    is AnkiDroidOutcome.Failed -> AnkiDroidNotice(
        context.getString(R.string.anki_failed, label, reason(context, failure)),
    )
}

private fun reason(context: Context, failure: AnkiDroidFailure): String = context.getString(
    when (failure) {
        AnkiDroidFailure.NOT_INSTALLED -> R.string.anki_reason_not_installed
        AnkiDroidFailure.PERMISSION -> R.string.anki_reason_permission
        AnkiDroidFailure.API_DISABLED -> R.string.anki_reason_api
        AnkiDroidFailure.MISSING_FIELD -> R.string.anki_reason_fields
        AnkiDroidFailure.INSERT_FAILED -> R.string.anki_reason_insert
        AnkiDroidFailure.DELETE_FAILED -> R.string.anki_reason_delete
    },
)
