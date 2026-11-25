package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R53: the Settings screen is a set of tabs, not one column of every card.
 *
 * Drawing a Compose window needs a display, so the guard reads the source — the same approach
 * [ScrollbarCoverageTest] and [HelpManualTest] already use. What it protects is the split itself: a
 * card that ends up in no tab is a preference the user can no longer reach, and a card that ends up
 * in two tabs is a setting edited from two places.
 */
class SettingsTabsTest {

    private val source: String by lazy { sourceFile("SettingsWindow.kt").readText() }

    @Test
    fun `the settings open as five tabs, each with a scroll container`() {
        assertEquals("the screen lost or gained a tab", 5, SettingsTab.entries.size)
        assertEquals(
            "a tab has no scrollable body (TabColumn draws the scrollbar)",
            5,
            Regex("""\bTabColumn\s*\{""").findAll(source).count(),
        )
        // The shared body is the only place that draws the list, so no tab can skip the scrollbar.
        val body = source.substringAfter("private fun TabColumn(").substringBefore("\n@Composable")
        assertTrue("TabColumn no longer scrolls", body.contains("ScrollableLazyColumn("))
    }

    @Test
    fun `every card lives in exactly one tab`() {
        val expected = mapOf(
            "GeneralTab" to listOf(
                "sectionAppearance",
                "sectionRenderer",
                "sectionCapture",
                "sectionStartup",
                "sectionLinuxIntegration",
                "kdeCapturePermissionTitle",
            ),
            "ShortcutsTab" to listOf("sectionHotkeys"),
            "ReadingTab" to listOf(
                "sectionEngine",
                "sectionHover",
                "sectionBehaviour",
                "sectionTranslation",
                "sectionAi",
            ),
            "DictionariesTab" to listOf("sectionDicts"),
            "AboutTab" to listOf("sectionAbout"),
        )

        expected.forEach { (tab, sections) ->
            val body = tabBody(tab)
            sections.forEach { section ->
                assertTrue("$tab lost its $section card", body.contains("DesktopStrings.$section"))
            }
        }
        expected.values.flatten().forEach { section ->
            assertEquals(
                "$section is drawn in more than one place",
                1,
                Regex("""DesktopStrings\.$section\b""").findAll(source).count(),
            )
        }
    }

    /** The language used to be a card in the middle of the page; it is one click from every tab now. */
    @Test
    fun `the language picker sits in the header, next to the help button`() {
        val bar = source.substringAfter("private fun SettingsTopBar(").substringBefore("\n@Composable")
        assertTrue("the header has no language picker", bar.contains("LanguageMenu("))
        assertTrue("the header lost the Help button", bar.contains("DesktopStrings.btnHelp"))

        val menu = source.substringAfter("private fun LanguageMenu(").substringBefore("\n\n/**")
        listOf("PT_BR", "EN_US", "SYSTEM").forEach { choice ->
            assertTrue("the header cannot choose $choice", menu.contains(choice))
        }
        assertTrue(
            "picking a language no longer changes the interface",
            menu.contains("DesktopStrings.updateLanguage(choice)") && menu.contains("settings.appLanguage = choice"),
        )
    }

    @Test
    fun `every tab has a name in both languages`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            val portuguese = SettingsTab.entries.map { settingsTabLabel(it) }
            DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
            val english = SettingsTab.entries.map { settingsTabLabel(it) }

            assertEquals("a tab lost its label", SettingsTab.entries.size, portuguese.size)
            assertTrue("a tab has an empty name", portuguese.all { it.isNotBlank() } && english.all { it.isNotBlank() })
            portuguese.zip(english).forEach { (pt, en) ->
                assertNotEquals("a tab is written in only one language", pt, en)
            }
        } finally {
            DesktopStrings.updateLanguage(previous)
        }
    }

    /**
     * The PP-OCR line used to be stored as text in a `remember`, so switching the language left
     * «Models installed» in English under a Portuguese screen (R53 E2E).
     */
    @Test
    fun `the PP-OCR install line follows the interface language`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            val portuguese = ppOcrInstallLine(installed = true, message = null)
            DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
            val english = ppOcrInstallLine(installed = true, message = null)

            assertNotEquals("the install line is not translated", portuguese, english)
            assertNotEquals(
                "the «not installed» line is not translated",
                ppOcrInstallLine(installed = false, message = null),
                portuguese,
            )
            assertEquals(
                "a live download message must win over the install state",
                "Baixando… 40%",
                ppOcrInstallLine(installed = false, message = "Baixando… 40%"),
            )
        } finally {
            DesktopStrings.updateLanguage(previous)
        }

        // Drawn from the state, never stored as text: storing it is what froze the language.
        assertTrue(
            "the reading tab no longer draws the install line from the state",
            source.contains("ppOcrInstallLine(ppOcrInstalled, ppOcrMessage)"),
        )
        assertFalse("the install line is stored as text again", source.contains("ppOcrStatus"))
    }

    /** The body of one tab: from its declaration to the next composable in the file. */
    private fun tabBody(name: String): String {
        val start = source.indexOf("private fun $name(")
        check(start >= 0) { "$name was not found in SettingsWindow.kt" }
        val end = source.indexOf("\n@Composable", start)
        check(end > start) { "$name has no body" }
        return source.substring(start, end)
    }

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")
}
