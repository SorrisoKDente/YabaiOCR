package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiQueue
import java.io.File

/**
 * The outcomes the panel has to tell apart: the card arrived, it was already there, or it is waiting
 * in the queue.
 */
enum class AnkiSendStatus { ADDED, ALREADY_IN_ANKI, QUEUED }

data class AnkiSendResult(
    val status: AnkiSendStatus,
    /** Why it is waiting, when it is; empty for a card that arrived. */
    val detail: String = "",
    val noteId: Long = -1,
)

/**
 * Sends a card now and, when it cannot, keeps it for later.
 *
 * The queue is written **before** the attempt, so closing the app while Anki is answering does not
 * lose the card; it is removed only after Anki confirmed it. The client is injected so the delivery
 * rules — dedupe, queue, retry — are tested without an Anki.
 */
class AnkiSender(
    private val settings: DesktopSettings,
    private val queue: AnkiQueue = AnkiQueue(File(defaultDesktopHome(), "anki-queue.jsonl")),
    private val open: (String) -> AnkiClient = { AnkiConnect(it) },
) {
    fun send(note: AnkiNote): AnkiSendResult {
        val deck = settings.ankiDeck.ifBlank { DEFAULT_DECK }
        val model = settings.ankiModel.ifBlank { DEFAULT_MODEL }
        val tagged = if (note.tags.isEmpty()) note.copy(tags = tags()) else note
        val queued = queue.enqueue(deck, model, tagged)
        return deliver(queued.deck, queued.model, queued.id, queued.note)
    }

    /** Sends everything that is waiting; returns how many arrived. */
    fun retryQueued(): Int = queue.pending().count { entry ->
        deliver(entry.deck, entry.model, entry.id, entry.note).status == AnkiSendStatus.ADDED
    }

    /** A handshake the Settings button uses: the version the add-on answers, or why it did not. */
    fun testConnection(): Result<Int> = runCatching {
        open(settings.ankiUrl.ifBlank { DEFAULT_URL }).version()
    }

    private fun deliver(deck: String, model: String, queueId: String, note: AnkiNote): AnkiSendResult {
        return try {
            val client = open(settings.ankiUrl.ifBlank { DEFAULT_URL })
            client.version()
            client.ensureDeck(deck)
            client.ensureModel(model, AnkiFields.ORDER)
            val noteId = client.addNote(deck, model, note)
            queue.remove(queueId)
            AnkiSendResult(AnkiSendStatus.ADDED, noteId = noteId)
        } catch (e: AnkiException) {
            // A duplicate is Anki saying it already has the card: keeping it queued would make every
            // resend fail the same way. Any other refusal keeps the card, with Anki's reason.
            if (e.failure == AnkiFailure.DUPLICATE) {
                queue.remove(queueId)
                return AnkiSendResult(AnkiSendStatus.ALREADY_IN_ANKI, detail = e.message.orEmpty())
            }
            queue.markFailed(queueId, e.message.orEmpty())
            AnkiSendResult(AnkiSendStatus.QUEUED, detail = e.message.orEmpty())
        } catch (e: Exception) {
            queue.markFailed(queueId, e.message.orEmpty())
            AnkiSendResult(AnkiSendStatus.QUEUED, detail = e.message.orEmpty())
        }
    }

    /** `«yabai, jlpt»` in Settings is two tags, however the user spaced or separated them. */
    private fun tags(): List<String> =
        settings.ankiTags.split(',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        const val DEFAULT_DECK = "Yabai"
        const val DEFAULT_MODEL = "Yabai"
        const val DEFAULT_URL = "http://127.0.0.1:8765"
    }
}
