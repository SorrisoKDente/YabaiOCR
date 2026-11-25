package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Shortcuts that are not the defaults, so a hardcoded default in the manual stands out. */
    private val shortcuts = HelpShortcuts(
        fullScreen = "F9",
        region = "F10",
        hover = "F11",
        hoverCycle = "F7",
        quickDict = "F12",
    )

    @Test
    fun `the manual covers the same actions in both languages`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            val portuguese = helpTopics(shortcuts)
            DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
            val english = helpTopics(shortcuts)

            val expected = listOf(
                "capture",
                "region",
                "hover",
                "quick-search",
                "dictionaries",
                "result",
                "renderers",
                "engines",
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

    /**
     * R52: the manual names the shortcuts the user configured, not the defaults.
     *
     * A reader who had changed a shortcut was sent to a key that no longer does anything.
     */
    @Test
    fun `the manual prints the configured shortcuts, not the defaults`() {
        val previous = DesktopStrings.currentChoice
        try {
            // Both languages: each one has its own copy of the titles and bodies.
            for (language in listOf(DesktopLanguageChoice.PT_BR, DesktopLanguageChoice.EN_US)) {
                DesktopStrings.updateLanguage(language)
                val topics = helpTopics(shortcuts).associateBy { it.id }

                val text = listOf("capture", "region", "hover", "quick-search")
                    .joinToString("\n") { id -> topics.getValue(id).title + "\n" + topics.getValue(id).body }

                listOf("F9", "F10", "F11", "F7", "F12").forEach { key ->
                    assertTrue(
                        "the $language manual does not print the configured shortcut $key",
                        text.contains(key),
                    )
                }
                // Every one of the defaults the manual used to print, not just the first: a body
                // left with its old key would still show the configured one in the title.
                listOf("Ctrl + Shift + O", "Ctrl + Shift + A", "Ctrl + Shift + H", "Ctrl + Shift + X", "Ctrl + Shift + D")
                    .forEach { key ->
                        assertFalse(
                            "the $language manual still prints the default shortcut $key",
                            text.contains(key),
                        )
                    }
            }
        } finally {
            DesktopStrings.updateLanguage(previous)
        }

        // The window has to read the settings, otherwise the parameter is just a different default.
        val window = sourceFile("HelpWindow.kt").readText()
        assertTrue(
            "the manual is built without the shortcuts that are saved",
            window.contains("helpShortcuts(settings)") && window.contains("settings.globalHotkey"),
        )
    }

    /**
     * R52: «gráfico» and «motor» are choices with trade-offs, and the manual has to explain each one
     * instead of naming it.
     */
    @Test
    fun `the manual explains every renderer and every engine, with both sides`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            val topics = helpTopics(shortcuts).associateBy { it.id }
            val renderers = topics.getValue("renderers").body
            val engines = topics.getValue("engines").body

            SkikoRenderer.entries.forEach { level ->
                assertTrue(
                    "the renderer help does not name ${DesktopStrings.rendererName(level)}",
                    renderers.contains(DesktopStrings.rendererName(level)),
                )
            }
            assertTrue("the renderer help has no advantages line", renderers.contains("\n+ "))
            assertTrue("the renderer help has no drawbacks line", renderers.contains("\n− "))
            assertEquals(
                "the renderer help is missing a draw",
                SkikoRenderer.entries.size,
                renderers.split("\n+ ").size - 1,
            )

            listOf("Google Lens", "PP-OCRv6").forEach { engine ->
                assertTrue("the engine help does not name $engine", engines.contains(engine))
            }
            assertEquals("the engine help is missing a drawback", 3, engines.split("\n− ").size - 1)
        } finally {
            DesktopStrings.updateLanguage(previous)
        }
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

    /**
     * R53: the manual is a destination of the main window, with a way back — not a window of its
     * own that the reader had to close.
     */
    @Test
    fun `the manual is a screen of the window, with a way back`() {
        val help = sourceFile("HelpWindow.kt").readText()
        assertTrue("the manual lost its way back", help.contains("onClick = onBackRequested"))
        assertFalse("the manual is a window of its own again", help.contains("Window("))
        assertFalse(
            "the manual opens a window instead of being one of the main window's screens",
            help.contains("androidx.compose.ui.window"),
        )
        assertTrue(
            "the manual ignores the configured shortcuts",
            help.contains("helpShortcuts(settings)"),
        )

        val main = sourceFile("Main.kt").readText()
        assertTrue("the main window has no manual screen", main.contains("MainScreen.HELP"))
        assertTrue("the main window no longer draws the manual", main.contains("HelpContent("))

        val settings = sourceFile("SettingsWindow.kt").readText()
        assertTrue(
            "Settings lost the Help button",
            settings.contains("DesktopStrings.btnHelp") && settings.contains("onHelpRequested"),
        )
    }

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")
}
