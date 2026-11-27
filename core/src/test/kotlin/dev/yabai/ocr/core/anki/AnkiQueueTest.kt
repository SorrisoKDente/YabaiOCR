package dev.yabai.ocr.core.anki

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A card is not lost because Anki was closed: the queue survives the app, refuses to hold the same
 * word twice, remembers why a send failed and cannot grow without a ceiling.
 */
class AnkiQueueTest {

    private val dir: File = Files.createTempDirectory("anki-queue-test").toFile()
    private val file = File(dir, "anki-queue.jsonl")
    private var now = 1_000L

    private fun queue(maxEntries: Int = 500) = AnkiQueue(file, clock = { now++ }, maxEntries = maxEntries)

    private fun note(word: String) = AnkiNote(
        linkedMapOf(
            AnkiFields.WORD to word,
            AnkiFields.READING to "よみ",
            AnkiFields.MEANING to "meaning",
            AnkiFields.SENTENCE to word,
            AnkiFields.SOURCE to "Jitendex · 27/11/2026",
        ),
    )

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a card survives a new instance of the queue`() {
        val queued = queue().enqueue("Yabai", "Yabai", note("食べる"))

        val reopened = queue().pending().single()
        assertEquals(queued.id, reopened.id)
        assertEquals("食べる", reopened.note.firstField)
        assertEquals("Yabai", reopened.deck)
        assertEquals(AnkiQueueState.PENDING, reopened.state)
    }

    @Test
    fun `the same word is one card, not two`() {
        val first = queue().enqueue("Yabai", "Yabai", note("食べる"))
        now += 10
        val second = queue().enqueue("Yabai", "Yabai", note("食べる"))

        assertEquals("the same word was queued twice", first.id, second.id)
        assertEquals(1, queue().pending().size)
    }

    @Test
    fun `the same word in another deck is another card`() {
        queue().enqueue("Yabai", "Yabai", note("食べる"))
        queue().enqueue("Japones", "Yabai", note("食べる"))

        assertEquals(2, queue().pending().size)
    }

    @Test
    fun `a failed send keeps the card and the reason`() {
        val queued = queue().enqueue("Yabai", "Yabai", note("食べる"))
        queue().markFailed(queued.id, "Anki fechado")

        val reopened = queue().pending().single()
        assertEquals(AnkiQueueState.FAILED, reopened.state)
        assertEquals("Anki fechado", reopened.error)
    }

    @Test
    fun `a sent card leaves the queue`() {
        val queued = queue().enqueue("Yabai", "Yabai", note("食べる"))
        queue().remove(queued.id)

        assertTrue(queue().pending().isEmpty())
        assertTrue("an empty queue still leaves the file behind", !file.exists())
    }

    @Test
    fun `the queue drops the oldest cards past its ceiling`() {
        val queue = queue(maxEntries = 3)
        listOf("一", "二", "三", "四").forEachIndexed { index, word ->
            now += 1
            queue.enqueue("Yabai", "Yabai", note(word))
            assertTrue("the queue grew past its ceiling at card $index", queue.pending().size <= 3)
        }

        val words = queue().pending().map { it.note.firstField }
        assertEquals(listOf("二", "三", "四"), words)
    }

    @Test
    fun `a broken line costs that line, not the queue`() {
        queue().enqueue("Yabai", "Yabai", note("食べる"))
        file.appendText("{not json\n")

        val entries = queue().pending()
        assertEquals(1, entries.size)
        assertNotNull(entries.single().note.firstField)
    }

    @Test
    fun `a card keeps the tags it was made with`() {
        queue().enqueue("Yabai", "Yabai", AnkiNote(note("食べる").fields, listOf("yabai", "jlpt")))

        assertEquals(listOf("yabai", "jlpt"), queue().pending().single().note.tags)
    }
}
