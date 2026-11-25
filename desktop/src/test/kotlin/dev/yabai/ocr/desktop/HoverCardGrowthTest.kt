package dev.yabai.ocr.desktop

import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.io.File
import javax.swing.JFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * End-to-end measurement of the hover card's position across the click that opens the dictionary
 * list (D102).
 *
 * The only thing that click changes geometrically is the height of the card: the list is drawn
 * inside it (see [HoverTooltipHeightTest]). This test uses a real undecorated AWT window at the
 * placement the hover chose near the taskbar, applies the same [planCardPlacement] the window uses,
 * and reads the bounds AWT reports — so it covers the window move/resize, not just the arithmetic.
 *
 * The work area is built by the test (1280×700 at the display's origin, clamped to it), not read
 * from the desktop: the scenario needs the closed card's bottom near the taskbar, and a taller
 * display turned that into «the card still fits», which measured nothing.
 *
 * A display is needed; on a headless machine the window test is skipped, and so is a display too
 * small to host the work area.
 */
class HoverCardGrowthTest {

    /** Height of the card with its list closed, and with it open. */
    private val closedHeight = 200
    private val openHeight = 360

    @Test
    fun `the card header stays under the pointer when the dictionary list opens near the taskbar`() {
        assumeFalse("needs a display", GraphicsEnvironment.isHeadless())

        // The work area is built here instead of read from the machine. Read from the machine, the
        // pointer under a 200 px card sat 206 px above the taskbar of this 1920×892 screen, the
        // taller card still fitted below it, and the move this test exists for never happened — the
        // last assertion then failed on the screen size, not on a regression. With fixed numbers the
        // scenario is the same on every machine, and only «is the display big enough» varies.
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.bounds
        // The work area the card is placed in: everything above the taskbar.
        val workArea = Rectangle(
            screen.x,
            screen.y,
            minOf(1280, screen.width),
            minOf(700, screen.height),
        )
        assumeTrue(
            "the display is too small for this scenario: $workArea",
            workArea.width >= 620 && workArea.height >= 620,
        )
        // Below the pointer there is room for the closed card and 40 px more; the open card does not
        // fit in that strip, so opening the list has to be answered by moving the card up.
        val pointer = Point(
            workArea.x + workArea.width - 520,
            workArea.y + workArea.height - closedHeight - HoverGeometry.TOOLTIP_GAP - 40,
        )

        val before = HoverGeometry.placeTooltip(
            pointer = pointer,
            size = java.awt.Dimension(460, closedHeight),
            screen = workArea,
        )
        // The card is below the pointer, with its bottom already close to the taskbar.
        assertEquals(pointer.y + HoverGeometry.TOOLTIP_GAP, before.y)
        assertTrue(
            "the closed card must sit near the bottom ($before in $workArea), or opening the list " +
                "would not move it and nothing would be measured",
            before.y + closedHeight < workArea.y + workArea.height &&
                before.y + openHeight > workArea.y + workArea.height,
        )
        val headerClick = Point(before.x + 60, before.y + 15)

        val window = JFrame().apply {
            isUndecorated = true
            isAlwaysOnTop = true
            setBounds(before.x, before.y, 460, closedHeight)
        }
        try {
            window.isVisible = true
            assertEquals("AWT did not honour the placement before the click", before, window.location)

            // Same word, taller card: exactly what opening the list does.
            val placement = planCardPlacement(
                bounds = window.bounds,
                pointer = pointer,
                height = openHeight,
                screen = workArea,
                sameAnchor = true,
            )
            window.setSize(460, openHeight)
            window.setLocation(placement.location)

            val after = window.bounds
            assertTrue(
                "the clicked header (before=$before, click=$headerClick) left the card: $after",
                after.contains(headerClick),
            )
            assertTrue("the card left the work area: $after", workArea.contains(after))
            assertEquals(
                "the card should only move as far as the work area requires",
                workArea.y + workArea.height,
                after.y + after.height,
            )
        } finally {
            window.dispose()
        }
    }

    /**
     * Display-dependent geometry is covered above; this reads the wiring, because a card that never
     * tells [planCardPlacement] it is the same anchor would regress to the jumping placement and no
     * unit test of the pure helper would notice.
     */
    @Test
    fun `the card remembers the word it was placed for`() {
        val source = sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/HoverTooltipWindow.kt"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/HoverTooltipWindow.kt"),
        ).firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("HoverTooltipWindow.kt not found", source.isNotEmpty())

        assertTrue(
            "the card must derive «same anchor» from the pointer it was placed for",
            source.contains("val sameAnchor = placedFor == mousePoint"),
        )
        assertTrue(
            "the placement must receive «same anchor»",
            Regex("""applySizeAndPlacement\([^)]*sameAnchor""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source),
        )
        assertTrue(
            "the pointer of a performed placement must be remembered",
            source.contains("placedFor = mousePoint"),
        )
    }
}
