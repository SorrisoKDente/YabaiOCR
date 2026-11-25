package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manual is a promise that every action is explained, in both languages.
 *
 * Drawing a Compose window needs a display, so the guard has two halves: the topics themselves are
 * pure data and are checked in both languages, and the button that opens them is read from the
 * Settings source — the same approach `ReleaseNotesWindow` already has for its always-on-top rule.
 */
class HelpManualTest {

    @Test
    fun `the manual covers the same actions in both languages`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            val portuguese = helpTopics()
            DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
            val english = helpTopics()

            val expected = listOf(
                "capture",
                "region",
                "hover",
                "quick-search",
                "dictionaries",
                "result",
                "tray",
            )
            assertEquals("the manual lost or reordered an action", expected, portuguese.map { it.id })
            assertEquals("the manual lost or reordered an action", expected, english.map { it.id })
            assertTrue(
                "a manual entry is empty",
                portuguese.all { it.title.isNotBlank() && it.body.isNotBlank() } &&
                    english.all { it.title.isNotBlank() && it.body.isNotBlank() },
            )
            assertTrue(
                "an entry was written in only one language",
                portuguese.zip(english).all { (pt, en) -> pt.title != en.title && pt.body != en.body },
            )
        } finally {
            DesktopStrings.updateLanguage(previous)
        }
    }

    @Test
    fun `the button reference comes first and follows the interface language`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            val portuguese = helpButtons()
            DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
            val english = helpButtons()

            assertTrue("the manual opens without the button reference", portuguese.isNotEmpty())
            assertEquals("the two languages disagree on the buttons", portuguese.size, english.size)
            assertTrue(
                "a button row is missing its icon, name or explanation",
                portuguese.all { it.icon.isNotBlank() && it.name.isNotBlank() && it.what.isNotBlank() } &&
                    english.all { it.icon.isNotBlank() && it.name.isNotBlank() && it.what.isNotBlank() },
            )
            assertTrue(
                "a button was written in only one language",
                portuguese.zip(english).all { (pt, en) -> pt.what != en.what },
            )
        } finally {
            DesktopStrings.updateLanguage(previous)
        }

        // The manual draws the reference before the topic list; a reader meets it first.
        val window = sourceFile("HelpWindow.kt").readText()
        assertTrue(
            "the button reference is not the first section of the manual",
            window.indexOf("helpButtons().forEach") in 0 until window.indexOf("items(topics)"),
        )
    }

    @Test
    fun `the crash note offers the choice the platform actually has`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            val linux = DesktopStrings.rendererAfterCrash("Software", linux = true)
            assertTrue("the Linux note does not name the switch", linux.contains("Renderização por software"))
            assertTrue("the Linux note still points at an «Automático» the card does not have", !linux.contains("Automático"))

            val windows = DesktopStrings.rendererAfterCrash("OpenGL", linux = false)
            assertTrue("the Windows note lost the way back", windows.contains("Automático"))
        } finally {
            DesktopStrings.updateLanguage(previous)
        }
    }

    @Test
    fun `settings offers the Help button and opens the manual`() {
        val settings = sourceFile("SettingsWindow.kt").readText()
        assertTrue(
            "Settings lost the Help button",
            settings.contains("DesktopStrings.btnHelp") && settings.contains("showHelp = true"),
        )
        assertTrue(
            "the Help button no longer opens the manual",
            settings.contains("HelpWindow(onClose = { showHelp = false })"),
        )
    }

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")
}
