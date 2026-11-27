package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiQueue
import dev.yabai.ocr.core.anki.AnkiQueueState
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Delivery rules: the card leaves the queue only after Anki confirmed it, a failure keeps the card
 * with its reason, and a retry uses the destination the card was made for.
 */
class AnkiSenderTest {

    private val dir: File = Files.createTempDirectory("anki-sender-test").toFile()
    private val queueFile = File(dir, "anki-queue.jsonl")
    private val settings = DesktopSettings(File(dir, "settings.properties"), dir)
    private val queue get() = AnkiQueue(queueFile)

    private class FakeAnki(var failure: AnkiException? = null) : AnkiClient {
        val added = mutableListOf<AnkiNote>()
        override fun version(): Int {
            failure?.let { throw it }
            return 6
        }
        override fun ensureDeck(deck: String) = Unit
        override fun ensureModel(model: String, fields: List<String>) = Unit
        override fun addNote(deck: String, model: String, note: AnkiNote): Long {
            failure?.let { throw it }
            added += note
            return 99L
        }
        override fun deleteNote(noteId: Long) = Unit
    }

    private fun note(word: String) = AnkiNote(
        linkedMapOf(
            AnkiFields.WORD to word,
            AnkiFields.READING to "たべる",
            AnkiFields.MEANING to "comer",
            AnkiFields.SENTENCE to word,
            AnkiFields.SOURCE to "Jitendex",
        ),
    )

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a card that arrives leaves the queue`() {
        val anki = FakeAnki()
        val result = AnkiSender(settings, queue) { anki }.send(note("食べる"))

        assertEquals(AnkiSendStatus.ADDED, result.status)
        assertEquals(99L, result.noteId)
        assertTrue("the sent card is still queued", queue.pending().isEmpty())
    }

    @Test
    fun `a card made while Anki is closed is queued with the reason`() {
        val anki = FakeAnki(AnkiException(AnkiFailure.NOT_RUNNING, "AnkiConnect is not answering"))
        val result = AnkiSender(settings, queue) { anki }.send(note("食べる"))

        assertEquals(AnkiSendStatus.QUEUED, result.status)
        val waiting = queue.pending().single()
        assertEquals(AnkiQueueState.FAILED, waiting.state)
        assertTrue(waiting.error.contains("not answering"))
        assertEquals("Yabai", waiting.deck)
    }

    @Test
    fun `the same word tapped twice while offline is one queued card`() {
        val anki = FakeAnki(AnkiException(AnkiFailure.NOT_RUNNING, "offline"))
        val sender = AnkiSender(settings, queue) { anki }

        sender.send(note("食べる"))
        sender.send(note("食べる"))

        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `a duplicate Anki refuses leaves the queue and is reported as already there`() {
        val anki = FakeAnki(AnkiException(AnkiFailure.DUPLICATE, "duplicate"))
        val result = AnkiSender(settings, queue) { anki }.send(note("食べる"))

        assertEquals(AnkiSendStatus.ALREADY_IN_ANKI, result.status)
        assertTrue(
            "a duplicate stayed in the queue, so every resend asks Anki for the same word again",
            queue.pending().isEmpty(),
        )
    }

    @Test
    fun `a card the note type refuses stays queued with the add-on reason`() {
        val anki = FakeAnki(
            AnkiException(AnkiFailure.UNKNOWN, "cannot create note because 'Palavra' is empty"),
        )
        val result = AnkiSender(settings, queue) { anki }.send(note("食べる"))

        assertEquals(AnkiSendStatus.QUEUED, result.status)
        val waiting = queue.pending().single()
        assertEquals(AnkiQueueState.FAILED, waiting.state)
        assertTrue("the reason Anki gave was lost: ${waiting.error}", waiting.error.contains("Palavra"))
    }

    @Test
    fun `retrying a card Anki calls a duplicate empties the queue`() {
        val anki = FakeAnki(AnkiException(AnkiFailure.NOT_RUNNING, "offline"))
        val sender = AnkiSender(settings, queue) { anki }
        sender.send(note("食べる"))
        assertEquals(1, queue.pending().size)

        anki.failure = AnkiException(AnkiFailure.DUPLICATE, "duplicate")
        sender.retryQueued()

        assertTrue("the duplicate stayed queued after the retry", queue.pending().isEmpty())
    }

    @Test
    fun `retrying sends what was waiting`() {
        val anki = FakeAnki(AnkiException(AnkiFailure.NOT_RUNNING, "offline"))
        val sender = AnkiSender(settings, queue) { anki }
        sender.send(note("食べる"))
        assertEquals(1, queue.pending().size)

        anki.failure = null
        assertEquals(1, sender.retryQueued())

        assertTrue(queue.pending().isEmpty())
        assertEquals(1, anki.added.size)
    }

    @Test
    fun `the tags typed in Settings are added to the tags the card already has`() {
        settings.ankiTags = "yabai, jlpt"
        val anki = FakeAnki()

        AnkiSender(settings, queue) { anki }.send(note("食べる").copy(tags = listOf("minha")))

        assertEquals(listOf("minha", "yabai", "jlpt"), anki.added.single().tags)
    }
}
