package dev.yabai.ocr.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the short list of apps a question is offered to.
 *
 * The package manager answers «every app that takes a shared string», which on a normal phone is
 * Gmail, Bluetooth, the JPEG converter and the download manager next to ChatGPT. None of the first
 * four can answer a question, and together they bury the one that can five rows down. What is held
 * down here is that the ones that answer stay, the ones that only file text go, and that the guess
 * never leaves the phone with an empty list.
 */
class AskAiRelevantTest {

    @Test
    fun `apps that answer a question are kept`() {
        val all = listOf(
            app("ChatGPT"),
            app("Gemini"),
            app("Google Tradutor"),
            app("DeepL"),
            app("Assistente"),
            app("Copilot"),
        )

        assertEquals(all, AskAi.relevant(all))
    }

    @Test
    fun `apps that only share text are left out`() {
        val all = listOf(
            app("ChatGPT"),
            app("Gmail"),
            app("Bluetooth"),
            app("Converter para JPEG"),
            app("Baixar arquivo"),
            app("Anotações"),
        )

        assertEquals(listOf(app("ChatGPT")), AskAi.relevant(all))
    }

    /** «Media», «Galeria» and «Fotografia» contain *ia* without being an AI: only whole words count. */
    @Test
    fun `a word is a whole word`() {
        val all = listOf(app("Media"), app("Galeria"), app("Fotografia"), app("Calculadora"))

        assertEquals(all, AskAi.relevant(all))
    }

    /**
     * A phone with an AI nobody wrote down keeps its whole list: filtering everything out would tell
     * the user «no app on this phone takes text», which is not what happened.
     */
    @Test
    fun `an unknown phone keeps every app`() {
        val all = listOf(app("Zé"), app("Yabai"), app("Navegar"))

        assertEquals(all, AskAi.relevant(all))
    }

    @Test
    fun `the order of the apps does not change`() {
        val all = listOf(
            app("Gmail"),
            app("Claude"),
            app("Bluetooth"),
            app("ChatGPT"),
            app("Galeria"),
            app("Google Tradutor"),
        )

        assertEquals(listOf("Claude", "ChatGPT", "Google Tradutor"), AskAi.relevant(all).map { it.label })
    }

    /** The name travels with the launcher's language pack; the package does not. */
    @Test
    fun `a known package counts even when its name says nothing`() {
        val all = listOf(app("ChatGPT"), AskAi.Target("App", "com.deepseek.chat"))

        assertEquals(all, AskAi.relevant(all))
    }

    /** A helper shipped inside the app's own namespace belongs to the app. */
    @Test
    fun `a helper of a known app counts as the app`() {
        val all = listOf(app("ChatGPT"), AskAi.Target("Compartilhar", "com.openai.chatgpt.helper"))

        assertEquals(all, AskAi.relevant(all))
    }

    @Test
    fun `accented and unaccented translators are both found`() {
        val all = listOf(app("Tradução"), app("Tradutor"), app("Intérprete"), app("Interprete"))

        assertEquals(all, AskAi.relevant(all))
    }

    private fun app(label: String) = AskAi.Target(label, "com.example.$label")
}
