package dev.yabai.ocr.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The list of apps the settings screen draws (R18).
 *
 * It used to be the raw package-manager answer — Gmail, Bluetooth, «Converter para JPEG» — while the
 * panel already offered only AI and translation apps, with a footer to the full list. Two criteria in
 * one app mean the app fixed in the settings is not the app the panel would have offered.
 */
class AskAiSettingsListTest {

    private val chatgpt = AskAi.Target("ChatGPT", "com.openai.chatgpt")
    private val deepl = AskAi.Target("DeepL", "com.deepl.mobiletranslator")
    private val gmail = AskAi.Target("Gmail", "com.google.android.gm")
    private val bluetooth = AskAi.Target("Bluetooth", "com.android.bluetooth")

    private val all = listOf(chatgpt, deepl, gmail, bluetooth)

    @Test
    fun `the list starts with the automatic choice`() {
        assertEquals(AskAi.Row.Automatic, AskAi.rows(all, showingAll = false).first())
    }

    @Test
    fun `the settings offer the same short list as the panel`() {
        assertEquals(
            listOf(AskAi.Row.Automatic, AskAi.Row.App(chatgpt), AskAi.Row.App(deepl), AskAi.Row.Toggle),
            AskAi.rows(all, showingAll = false),
        )
    }

    @Test
    fun `the footer opens the full list`() {
        assertEquals(
            listOf(
                AskAi.Row.Automatic,
                AskAi.Row.App(chatgpt),
                AskAi.Row.App(deepl),
                AskAi.Row.App(gmail),
                AskAi.Row.App(bluetooth),
                AskAi.Row.Toggle,
            ),
            AskAi.rows(all, showingAll = true),
        )
    }

    @Test
    fun `a phone with no ai app at all shows everything and no footer`() {
        assertEquals(
            listOf(AskAi.Row.Automatic, AskAi.Row.App(gmail), AskAi.Row.App(bluetooth)),
            AskAi.rows(listOf(gmail, bluetooth), showingAll = false),
        )
    }

    @Test
    fun `picking an app gives the package the settings store`() {
        val rows = AskAi.rows(all, showingAll = false)

        assertEquals("com.openai.chatgpt", (rows[1] as AskAi.Row.App).target.packageName)
    }

    @Test
    fun `an app chosen from the full list is still a choice the settings can store`() {
        val rows = AskAi.rows(all, showingAll = true)

        assertEquals("com.google.android.gm", (rows[3] as AskAi.Row.App).target.packageName)
    }
}
