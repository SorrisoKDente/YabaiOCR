package dev.yabai.ocr.anki

import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiQueue
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The rules around the AnkiDroid provider, without a provider.
 *
 * The client is two lambdas, so the test can make the send fail, refuse a duplicate or succeed
 * without AnkiDroid. What it holds down is the queue's policy: written before the attempt, removed
 * after confirmation, kept with a reason when the provider is unavailable, and dropped when the
 * note type cannot fit the card.
 */
class AnkiDroidSenderTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun newQueue(): AnkiQueue = AnkiQueue(File(temporary.root, "anki-queue.jsonl"))

    private val note = AnkiNote(mapOf(AnkiFields.WORD to "食べる"))

    @Test
    fun `a card that arrived leaves the queue`() {
        val queue = newQueue()
        val sender = AnkiDroidSender(
            queue,
            { _, _, _ -> AnkiDroidSendResult(noteId = 42, duplicate = false) },
            {},
        )

        val outcome = sender.send(note)

        assertTrue("the card was not reported as added", outcome is AnkiDroidOutcome.Added)
        assertEquals(42L, (outcome as AnkiDroidOutcome.Added).noteId)
        assertTrue("the sent card stayed in the queue", queue.pending().isEmpty())
    }

    @Test
    fun `a duplicate is reported and does not stay queued`() {
        val queue = newQueue()
        val sender = AnkiDroidSender(
            queue,
            { _, _, _ -> AnkiDroidSendResult(noteId = -1, duplicate = true) },
            {},
        )

        val outcome = sender.send(note)

        assertTrue("the duplicate was not reported as such", outcome is AnkiDroidOutcome.Duplicate)
        assertTrue("a duplicate stayed queued and would fail forever", queue.pending().isEmpty())
    }

    @Test
    fun `a card the note type cannot fit is not kept`() {
        val queue = newQueue()
        val sender = AnkiDroidSender(
            queue,
            { _, _, _ -> throw AnkiDroidException(AnkiDroidFailure.MISSING_FIELD, "no field") },
            {},
        )

        val outcome = sender.send(note)

        assertTrue("the refusal was not reported", outcome is AnkiDroidOutcome.Failed)
        assertEquals(
            AnkiDroidFailure.MISSING_FIELD,
            (outcome as AnkiDroidOutcome.Failed).failure,
        )
        assertTrue("a card that can never fit stayed queued", queue.pending().isEmpty())
    }

    @Test
    fun `an unavailable provider keeps the card and a retry sends it`() {
        val queue = newQueue()
        var available = false
        val sender = AnkiDroidSender(
            queue,
            { _, _, _ ->
                if (!available) throw AnkiDroidException(AnkiDroidFailure.NOT_INSTALLED, "absent")
                AnkiDroidSendResult(noteId = 7, duplicate = false)
            },
            {},
        )

        val first = sender.send(note)
        assertTrue("the card was not kept for later", first is AnkiDroidOutcome.Queued)
        assertEquals(AnkiDroidFailure.NOT_INSTALLED, (first as AnkiDroidOutcome.Queued).failure)
        assertEquals(1, queue.pending().size)

        available = true
        assertEquals("the queued card did not arrive on retry", 1, sender.retryQueued())
        assertTrue("the retried card stayed in the queue", queue.pending().isEmpty())
    }

    @Test
    fun `undo reaches the provider and reports its refusal`() {
        var removed = -1L
        val sender = AnkiDroidSender(newQueue(), { _, _, _ -> error("not used") }, { removed = it })

        assertTrue(sender.undo(9))
        assertEquals(9L, removed)

        val refusing = AnkiDroidSender(
            newQueue(),
            { _, _, _ -> error("not used") },
            { throw AnkiDroidException(AnkiDroidFailure.DELETE_FAILED, "no") },
        )
        assertFalse(refusing.undo(9))
    }
}
