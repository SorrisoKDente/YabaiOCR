package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hover card must keep one window for the whole session instead of creating one per word.
 *
 * Each word used to create and destroy its own `ComposeWindow` and the Skiko surface under it, and
 * the native allocations of those left the process with RSS that no heap trim gave back (measured:
 * the app stayed ~400 MB above its resting size after a session of hover reads). Compose decides
 * whether a window exists by whether the `Window(...)` composable is in the tree, so the reuse is
 * read from the source — drawing the card needs a display, which a unit test does not have, the
 * same reason [HoverCardResizeTest] and [HoverCardTransparencyTest] read sources.
 */
class HoverCardWindowReuseTest {

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")

    /** The overlay that places the card, from its declaration to the end of its window call. */
    private fun overlaySource(): String =
        sourceFile("Main.kt")
            .readText()
            .replace("\r\n", "\n")
            .substringAfter("private fun HoverTooltipOverlay(")

    @Test
    fun `the card is shown and hidden instead of created per word`() {
        val overlay = overlaySource().substringBefore("private val LAST_HOVER_POINT")
        assertTrue(
            "the overlay no longer tells the card whether it is on screen: $overlay",
            Regex("""visible\s*=\s*cardShown""").containsMatchIn(overlay),
        )
        // Whatever guards the call decides whether the window exists, and Compose destroys the
        // window of a composable that leaves the tree: the guard may only be «was the card ever
        // shown», never the content of the current word.
        val call = overlay.indexOf("HoverTooltipWindow(")
        assertTrue("the overlay does not call the card at all: $overlay", call >= 0)
        val guardStart = overlay.substring(0, call).lastIndexOf("if (")
        val guard = if (guardStart >= 0) overlay.substring(guardStart, call) else ""
        assertFalse(
            "the card is still created inside a «is there something to show?» branch, so every " +
                "word builds and destroys a window (guard: ${guard.trim()}): $overlay",
            Regex("""hPoint|hMatches|hMissing|cardShown""").containsMatchIn(guard),
        )
    }

    @Test
    fun `the card window follows the visible flag`() {
        val source = sourceFile("HoverTooltipWindow.kt").readText().replace("\r\n", "\n")
        val call = source.substringAfter("    Window(\n").substringBefore("        state = state,")
        assertTrue(
            "the card window is not hidden between words, so it is still created per word: $call",
            Regex("""visible\s*=\s*visible\s*,""").containsMatchIn(call),
        )
    }

    @Test
    fun `a hidden card holds no bounds`() {
        val source = sourceFile("HoverTooltipWindow.kt").readText().replace("\r\n", "\n")
        assertTrue(
            "a hidden card keeps the previous word's rectangle, so the hover thinks the pointer " +
                "is on a card that is not on screen",
            Regex("""if\s*\(!visible\)\s*\{[^}]*onBoundsChanged\(null\)""").containsMatchIn(source),
        )
    }
}
