package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hover card must not offer itself to be resized by the user.
 *
 * `Window(undecorated = true)` leaves `resizable` at Compose's default (true), and Compose answers
 * that with a resize border of its own: `ComposeWindow.setResizable` sets
 * `undecoratedWindowResizer.enabled = isUndecorated && isResizable`, which turns an 8 dp band along
 * every edge of the window into a resize handle with a resize cursor. The dictionary list's
 * scrollbar is the last 12 dp on the right, inside that band, so hovering the scrollbar showed the
 * resize cursor and pressing on it resized the card instead of scrolling the list.
 *
 * Drawing the card needs a display, which a unit test does not have, so the flag is read from the
 * source — the same reason [HoverCardTransparencyTest] and `ScrollbarCoverageTest` read sources.
 */
class HoverCardResizeTest {

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")

    /** The `Window(...)` call of the card, from its name to the window state it receives. */
    private fun cardWindowCall(): String =
        sourceFile("HoverTooltipWindow.kt")
            .readText()
            .replace("\r\n", "\n")
            .substringAfter("    Window(\n")
            .substringBefore("        state = state,")

    @Test
    fun `the card window is not user-resizable`() {
        val call = cardWindowCall()
        assertTrue(
            "the hover card must be undecorated, or the dictionary list has nowhere to live: $call",
            Regex("""undecorated\s*=\s*true""").containsMatchIn(call),
        )
        assertTrue(
            "the card keeps Compose's resize border, whose 8 dp band sits on the scrollbar: $call",
            Regex("""resizable\s*=\s*false""").containsMatchIn(call),
        )
    }

    @Test
    fun `nothing drag-resizes the card but the content`() {
        val source = sourceFile("HoverTooltipWindow.kt").readText()
        assertFalse(
            "the card must be sized by applySizeAndPlacement, not by a user drag",
            Regex("""setResizable\s*\(""").containsMatchIn(source),
        )
    }
}
