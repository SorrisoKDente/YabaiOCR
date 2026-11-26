package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R55: the way back from Help and from the dictionary manager is an arrow, not a sentence.
 *
 * Two bars that had the same button used to spend the width of a phrase on «◀ Back to Previous
 * Menu», pushing the screen title to the right for no gain. What it protects: the label must not
 * come back inside the button (the bar draws only the arrow) and the arrow must keep the label as
 * its accessible name, so the button is still announced instead of becoming an unnamed glyph.
 *
 * Drawing a Compose window needs a display, so the guard reads the source — the same approach
 * [SettingsTabsTest] and [HelpManualTest] use — and checks the two strings that feed it.
 */
class BackBarIconTest {

    private val bars = listOf("HelpWindow.kt", "DictionaryManagerWindow.kt")

    @Test
    fun `both back bars draw only the arrow and keep the label as the accessible name`() {
        bars.forEach { name ->
            val source = sourceFile(name).readText()

            assertTrue(
                "$name no longer draws the back arrow",
                Regex("""Text\(\s*DesktopStrings\.backToPreviousIcon""").containsMatchIn(source),
            )
            assertFalse(
                "$name still prints the back label inside the button",
                Regex("""Text\(\s*DesktopStrings\.btnBackToPrevious""").containsMatchIn(source),
            )
            assertTrue(
                "$name lost the accessible name of the back button",
                Regex("""contentDescription\s*=\s*DesktopStrings\.btnBackToPrevious""")
                    .containsMatchIn(source),
            )
        }
    }

    @Test
    fun `the arrow is the icon and the label is the name, in both languages`() {
        val previous = DesktopStrings.currentChoice
        try {
            listOf(DesktopLanguageChoice.PT_BR, DesktopLanguageChoice.EN_US).forEach { choice ->
                DesktopStrings.updateLanguage(choice)

                assertEquals("the arrow is not language dependent", "◀", DesktopStrings.backToPreviousIcon)
                assertFalse(
                    "$choice still puts the arrow inside the label",
                    DesktopStrings.btnBackToPrevious.contains("◀"),
                )
                assertTrue(
                    "$choice lost the meaning of the arrow",
                    DesktopStrings.btnBackToPrevious.isNotBlank(),
                )
            }
        } finally {
            DesktopStrings.updateLanguage(previous)
        }
    }

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")
}
