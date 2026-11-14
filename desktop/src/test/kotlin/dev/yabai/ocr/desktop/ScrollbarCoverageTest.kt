package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the scrollbar on every window whose content can overflow.
 *
 * Compose Desktop scrolls with the wheel but draws no scrollbar, so a `LazyColumn` that ends at the
 * window edge gives no sign that there is more below. `AppScrollbar.kt` provides the two wrappers
 * that draw it; a window that goes back to a bare `LazyColumn` silently loses the scrollbar, and
 * that is invisible to a normal unit test because drawing needs a display. The guard reads the
 * sources instead — the same approach `NativeRuntimeModulesTest` uses for the jlink modules.
 */
class ScrollbarCoverageTest {

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")

    @Test
    fun `the scrollable wrappers draw a desktop scrollbar`() {
        val source = sourceFile("AppScrollbar.kt").readText()
        assertTrue(
            "AppScrollbar.kt must draw a VerticalScrollbar",
            Regex("""VerticalScrollbar\s*\(""").containsMatchIn(source),
        )
        assertTrue(
            "AppScrollbar.kt must bind the scrollbar to the list state",
            Regex("""rememberScrollbarAdapter\s*\(""").containsMatchIn(source),
        )
    }

    @Test
    fun `every window with scrollable content uses the wrapper`() {
        val windows = listOf(
            "SettingsWindow.kt",
            "ResultWindow.kt",
            "DictionaryManagerWindow.kt",
            "LicenseWindow.kt",
            "ReleaseNotesWindow.kt",
            "QuickDictWindow.kt",
        )
        val missing = windows.filter { name ->
            !sourceFile(name).readText().contains("ScrollableLazyColumn(")
        }
        assertTrue(
            "these windows lost the scrollbar wrapper: ${missing.joinToString()}",
            missing.isEmpty(),
        )
    }
}
