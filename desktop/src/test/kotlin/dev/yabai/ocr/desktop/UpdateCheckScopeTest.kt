package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The periodic dictionary check belongs to the application scope, not to the settings window.
 *
 * It used to be a one-shot inside the settings window's `LaunchedEffect`, so a `--minimized` start —
 * the way the app actually lives — never checked. Moving it back inside the `Window` would bring the
 * bug back silently: nothing would fail, the check would simply never run for a reader who does not
 * open the window. Drawing needs a display, so this guard reads the source, the same approach
 * `NoticeWindowLayoutTest` uses.
 */
class UpdateCheckScopeTest {

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File("").absolutePath}")

    @Test
    fun `the dictionary monitor is created and run outside the main window`() {
        val main = sourceFile("Main.kt").readText()

        // Four spaces of indentation is the application's own scope; anything inside the `Window`
        // block is deeper.
        val declaration = main.indexOf(
            "    val dictionaryMonitor = remember { DictionaryUpdateMonitor(settings) }",
        )
        assertTrue(
            "the monitor must be created in the application scope, not inside a window",
            declaration >= 0,
        )

        val loop = main.indexOf("    LaunchedEffect(dictionaryMonitor)")
        assertTrue("the monitor must be run by the application scope", loop > declaration)

        val mainWindow = main.indexOf("// Main Window (Settings or Integrated Dictionary Manager)")
        assertTrue("the main window block was not found in Main.kt", mainWindow > 0)
        assertTrue(
            "the monitor must start before the main window exists, so --minimized runs still check",
            declaration < mainWindow && loop < mainWindow,
        )

        val run = main.indexOf("dictionaryMonitor.run {")
        assertTrue("the monitor must actually be started", run in loop until mainWindow)
    }
}
