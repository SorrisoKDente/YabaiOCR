package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.anki.AnkiFields
import dev.yabai.ocr.core.anki.AnkiNote
import dev.yabai.ocr.core.anki.AnkiQueue
import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-word buttons of the quick dictionary and the hover card go through the same path as the
 * result window: the card, the queue and the words in the panel.
 *
 * The client is a fake, so these tests send a card with no Anki installed and no collection to
 * touch; the queue is a temporary file.
 */
class AnkiPanelTest {

    private val dir: File = Files.createTempDirectory("anki-panel-test").toFile()
    private val settings = DesktopSettings(File(dir, "settings.properties"), dir)
    private val queue get() = AnkiQueue(File(dir, "anki-queue.jsonl"))
    private val previousLanguage = DesktopStrings.currentChoice

    private class FakeAnki(var failure: AnkiException? = null) : AnkiClient {
        override fun version(): Int {
            failure?.let { throw it }
            return 6
        }
        override fun ensureDeck(deck: String) = Unit
        override fun ensureModel(model: String, fields: List<String>) = Unit
        override fun addNote(deck: String, model: String, note: AnkiNote): Long {
            failure?.let { throw it }
            return 77L
        }
        override fun deleteNote(noteId: Long) = Unit
    }

    private fun sentenceMatch() = TokenMatch(
        surface = "食べました",
        start = 0,
        end = 5,
        entry = DictionaryEntry(
            term = "食べる",
            reading = "たべる",
            tags = "v1",
            rulesFlags = 0,
            score = 1,
            definition = "comer",
        ),
        dictionaryTitle = "Jitendex",
        reasons = emptyList(),
    )

    private fun sender(anki: FakeAnki) = AnkiSender(settings, queue) { anki }

    @After
    fun cleanUp() {
        DesktopStrings.updateLanguage(previousLanguage)
        dir.deleteRecursively()
    }

    @Test
    fun `a word sent from the hover reaches Anki and can be undone`() {
        DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
        val anki = FakeAnki()

        val notice = sendToAnki(settings, wordNote(sentenceMatch()), sender(anki))

        assertTrue("the panel did not say it was sent: ${notice.text}", notice.text.contains("Enviado para o Anki"))
        assertEquals("the panel offers no undo for the card it just sent", 77L, notice.undoId)
        assertTrue("the sent card is still queued", queue.pending().isEmpty())
    }

    @Test
    fun `with Anki closed the word waits in the queue and the panel says so`() {
        DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
        val anki = FakeAnki(AnkiException(AnkiFailure.NOT_RUNNING, "AnkiConnect is not answering"))

        val notice = sendToAnki(settings, wordNote(sentenceMatch()), sender(anki))

        assertTrue("the panel did not say it was saved: ${notice.text}", notice.text.contains("Saved to the Anki queue"))
        assertTrue("a card that never arrived was offered an undo", notice.undoId == null)
        val waiting = queue.pending().single()
        assertTrue("the reason Anki gave was lost: ${waiting.error}", waiting.error.contains("not answering"))
    }

    @Test
    fun `a word Anki already has says so in both languages`() {
        val anki = FakeAnki(AnkiException(AnkiFailure.DUPLICATE, "duplicate"))

        DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
        val pt = sendToAnki(settings, wordNote(sentenceMatch()), sender(anki))
        assertTrue("the panel did not say it was already there: ${pt.text}", pt.text.contains("Já está no Anki"))

        DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
        val en = sendToAnki(settings, wordNote(sentenceMatch()), sender(anki))
        assertTrue("the panel did not say it was already there: ${en.text}", en.text.contains("Already in Anki"))

        assertTrue("a duplicate stayed in the queue", queue.pending().isEmpty())
    }

    @Test
    fun `the per-word card has no sentence it did not see`() {
        val note = wordNote(sentenceMatch())

        assertEquals("", note.field(AnkiFields.SENTENCE))
        assertEquals("<ruby>食<rt>た</rt></ruby>べました", note.field(AnkiFields.WORD))
        assertEquals("たべる", note.field(AnkiFields.READING))
        assertTrue(
            "the card forgets the dictionary it came from: ${note.field(AnkiFields.SOURCE)}",
            note.field(AnkiFields.SOURCE).startsWith("Jitendex"),
        )
    }
}
