package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Locks the rule that made the light theme unusable: windows that follow the theme may only use
 * colours that also follow the theme.
 *
 * The bug: the «Padrão» buttons next to each hotkey were `outlinedButtonColors(contentColor =
 * Color.White)`. On the light theme the outlined button is transparent over a white surface, so the
 * label was white on white. The same happened to `Color.White` headings inside the settings cards.
 *
 * A Compose window is not rendered in this suite, so the check is on the source: the three windows
 * that follow the theme must not carry a fixed text colour. A hardcoded colour is only allowed when
 * the line picks it through `MaterialTheme.colors.isLight`.
 */
class ThemeWindowColorsTest {

    private val themeAwareWindows = listOf(
        "SettingsWindow.kt",
        "QuickDictWindow.kt",
        "DictionaryManagerWindow.kt",
    )

    private fun sourceFile(name: String): File {
        val candidates = listOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: fail("could not find $name (cwd=${File("").absolutePath})").let { File("") }
    }

    /** `color = Color.White` and `contentColor = Color.Black`, but not `uncheckedColor = if …`. */
    private val fixedTextColour = Regex("""(?<![A-Za-z])[a-zA-Z]*[Cc]olor\s*=\s*Color\.(White|Black)""")

    /** A raw hex assigned to a colour slot, e.g. `color = Color(0xFFA0A6B8)`. */
    private val fixedHexColour = Regex("""(?<![A-Za-z])[a-zA-Z]*[Cc]olor\s*=\s*Color\(0x""")

    @Test
    fun `theme-aware windows do not hardcode white or black text`() {
        for (name in themeAwareWindows) {
            val file = sourceFile(name)
            val offenders = file.readLines().withIndex()
                .filter { (_, line) -> fixedTextColour.containsMatchIn(line) }

            assertTrue(
                "$name has a fixed text colour that would vanish in one of the themes: " +
                    offenders.joinToString { (index, line) -> "line ${index + 1}: ${line.trim()}" },
                offenders.isEmpty(),
            )
        }
    }

    @Test
    fun `theme-aware windows only use a raw hex when they pick it by the theme`() {
        for (name in themeAwareWindows) {
            val file = sourceFile(name)
            val offenders = file.readLines().withIndex()
                .filter { (_, line) -> fixedHexColour.containsMatchIn(line) && !line.contains("isLight") }

            assertTrue(
                "$name paints a fixed colour regardless of the theme: " +
                    offenders.joinToString { (index, line) -> "line ${index + 1}: ${line.trim()}" },
                offenders.isEmpty(),
            )
        }
    }
}
