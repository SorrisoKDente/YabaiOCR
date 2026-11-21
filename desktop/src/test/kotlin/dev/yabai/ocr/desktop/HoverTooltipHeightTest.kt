package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card sizes its own window from [estimateHeight], so a height that forgets part of the content
 * is not cosmetic: the part below is cut off and cannot be clicked.
 *
 * The dictionary list used to be a popup — a window of its own — which opened off the bottom of the
 * screen. It is drawn inside the card now, so its rows have to be counted here.
 */
class HoverTooltipHeightTest {

    /** One entry, tall enough that the card is not clamped to its minimum height. */
    private fun entry() = TokenMatch(
        surface = "日本語",
        start = 0,
        end = 3,
        entry = DictionaryEntry(
            term = "日本語",
            reading = "にほんご",
            tags = "n",
            rulesFlags = 0,
            score = 0,
            definition = "the Japanese language, " + "as spoken and written. ".repeat(12),
        ),
        dictionaryTitle = "Teste",
        reasons = emptyList(),
    )

    @Test
    fun `the open dictionary list is counted in the height`() {
        val closed = estimateHeight(listOf(entry()), dictMenuOpen = false, dictionaryCount = 0)
        val open = estimateHeight(listOf(entry()), dictMenuOpen = true, dictionaryCount = 3)

        // One row per installed dictionary plus «all dictionaries».
        assertTrue(
            "the dictionary list added $open-$closed px for 4 rows",
            open - closed >= 4 * 24,
        )
    }

    @Test
    fun `more dictionaries grow the card`() {
        val two = estimateHeight(listOf(entry()), dictMenuOpen = true, dictionaryCount = 2)
        val five = estimateHeight(listOf(entry()), dictMenuOpen = true, dictionaryCount = 5)

        assertTrue("the card did not grow with the number of dictionaries", five > two)
    }

    @Test
    fun `a long dictionary list stops growing the card and scrolls instead`() {
        val six = estimateHeight(listOf(entry()), dictMenuOpen = true, dictionaryCount = 6)
        val many = estimateHeight(listOf(entry()), dictMenuOpen = true, dictionaryCount = 40)

        // Past six rows the list scrolls inside the card; before this it grew to the 640 px ceiling
        // and the rows below it were unreachable, which is what the user saw on a full install.
        assertEquals("the card still grows with the dictionary count", six, many)
        assertTrue("even a huge install must stay a readable card: $many", many <= 640)
    }

    @Test
    fun `the in-card notice is counted in the height`() {
        // One entry, so the card is not clamped to its 130 px floor: the notice has to fit inside a
        // card that would otherwise be taller anyway.
        val without = estimateHeight(listOf(entry()), notice = false)
        val with = estimateHeight(listOf(entry()), notice = true)

        // «No entry in this dictionary» is drawn inside the card (a window of its own would open on
        // every hover, over the text being read); an uncounted block would be cut at the bottom.
        assertTrue("the notice added $with-$without px", with - without >= 40)
        assertTrue("even with the notice the card stays readable: $with", with <= 640)
    }

    @Test
    fun `the card is only resized when its height really changed`() {
        val screen = Rectangle(0, 0, 1920, 1080)
        val bounds = Rectangle(100, 100, 460, 300)

        // The pointer moving from word to word used to resize the window every time, because the
        // placement was reapplied with the same height. On Windows with the Direct3D renderer a
        // burst of those resizes is a native crash, so a same-size placement must only move.
        assertFalse("the card was resized for the same height", planCardPlacement(bounds, Point(200, 200), 300, screen).resize)
        assertTrue("a taller card has to be resized", planCardPlacement(bounds, Point(200, 200), 358, screen).resize)
        // A width left over from a window-manager adjustment is a resize too.
        assertTrue(planCardPlacement(Rectangle(0, 0, 500, 300), Point(200, 200), 300, screen).resize)

        assertEquals(Point(218, 218), planCardPlacement(bounds, Point(200, 200), 300, screen).location)
    }

    /**
     * Regression (D102): with the card flipped above the pointer near the taskbar, opening the
     * dictionary list made it grow by the list's height, and the corner was recomputed from the
     * pointer with the new height — so the header moved up by exactly that growth, left the pointer,
     * and the hover dismissal closed the card the user had just clicked.
     */
    @Test
    fun `a card that still fits above its pointer does not move when the list opens`() {
        val workArea = Rectangle(0, 0, 1920, 1040)
        val pointer = Point(1500, 900)
        val beforeLocation = HoverGeometry.placeTooltip(
            pointer = pointer,
            size = java.awt.Dimension(460, 200),
            screen = workArea,
        )
        val before = Rectangle(beforeLocation, java.awt.Dimension(460, 200))
        // The card is already flipped above the pointer, and there is room above it.
        assertTrue("the card did not flip above the pointer: $before", before.y + before.height < pointer.y)

        val opened = planCardPlacement(before, pointer, 300, workArea, sameAnchor = true)

        assertTrue(opened.resize)
        assertEquals("the card moved although it only grew", beforeLocation, opened.location)
    }

    /**
     * Regression (D102): the card was below the pointer near the taskbar; opening the dictionary
     * list made it taller than the work area below, and the corner was recomputed from the pointer
     * with the new height — the whole card flipped above the pointer, where the click that opened
     * the list no longer was, so the hover dismissal closed it.
     */
    @Test
    fun `opening the dictionary list near the taskbar keeps the clicked header inside the card`() {
        val workArea = Rectangle(0, 0, 1920, 1040)
        val pointer = Point(1500, 600)
        val beforeLocation = HoverGeometry.placeTooltip(
            pointer = pointer,
            size = java.awt.Dimension(460, 200),
            screen = workArea,
        )
        val before = Rectangle(beforeLocation, java.awt.Dimension(460, 200))
        // The premise of the bug: the card sits below/right of the pointer.
        assertEquals(pointer.y + HoverGeometry.TOOLTIP_GAP, before.y)

        // The click on «dictionaries» lands on the card's header; the list then makes it much taller.
        val headerClick = Point(before.x + 60, before.y + 15)
        assertTrue(before.contains(headerClick))

        val opened = planCardPlacement(before, pointer, 500, workArea, sameAnchor = true)
        val after = Rectangle(opened.location, java.awt.Dimension(460, 500))

        // The flip the old placement did: y = 600 - 500 - 18 = 82, which leaves the click at 633
        // outside the card and closed it.
        assertTrue("the card did not grow from its corner: $after", after.y > before.y - 100)
        assertTrue("the clicked header left the card, so the hover would close it: $after", after.contains(headerClick))
        assertTrue("the card left the work area: $after", workArea.contains(after))
        assertEquals("the card should only move as far as the work area requires", 1040, after.y + after.height)
    }

    @Test
    fun `a grown card only moves as far as the screen requires`() {
        // No room below at all: instead of flipping to the pointer-anchored position, the card moves
        // up exactly enough for its last row to stay clickable.
        val opened = planCardPlacement(
            bounds = Rectangle(0, 900, 460, 200),
            pointer = Point(500, 1000),
            height = 600,
            screen = Rectangle(0, 0, 1920, 1040),
            sameAnchor = true,
        )

        assertEquals(440, opened.location.y)
        assertEquals(1040, opened.location.y + 600)
    }

    @Test
    fun `a new word is still anchored to its own pointer`() {
        val byPointer = planCardPlacement(
            bounds = Rectangle(100, 100, 460, 300),
            pointer = Point(200, 200),
            height = 300,
            screen = Rectangle(0, 0, 1920, 1080),
        )

        assertEquals(Point(218, 218), byPointer.location)
    }

    @Test
    fun `placing the card is retried while the window is not on screen yet`() {
        // A new card is not showing when the effect first runs; the placement used to be dropped, and
        // the card stayed at the initial height for as long as it lived.
        assertTrue(shouldRetryCardPlacement(applied = false, attempts = 0))
        assertTrue(shouldRetryCardPlacement(applied = false, attempts = CARD_PLACEMENT_MAX_ATTEMPTS - 1))

        // …but not forever: a card that never becomes visible has to stop asking.
        assertFalse(shouldRetryCardPlacement(applied = false, attempts = CARD_PLACEMENT_MAX_ATTEMPTS))
        assertFalse(shouldRetryCardPlacement(applied = true, attempts = 0))
        assertTrue(
            "the retries have to end quickly enough not to be felt: " +
                "${CARD_PLACEMENT_MAX_ATTEMPTS * CARD_PLACEMENT_RETRY_MS} ms",
            CARD_PLACEMENT_MAX_ATTEMPTS * CARD_PLACEMENT_RETRY_MS <= 1_000,
        )
    }

    /**
     * The dialog that appeared while changing dictionaries. Compose renders the card's window from
     * inside a render when it becomes displayable and refuses the reentry; the next frame draws it,
     * so the report is dropped — but only that one.
     */
    @Test
    fun `the redraw reentry is recognised and nothing else is`() {
        assertTrue(
            isRedrawReentry(IllegalStateException("Reentry into ignoringRedrawRequests is not allowed")),
        )
        assertFalse(isRedrawReentry(IllegalStateException("anything else")))
        assertFalse(isRedrawReentry(RuntimeException("Reentry into ignoringRedrawRequests is not allowed")))
    }
}
