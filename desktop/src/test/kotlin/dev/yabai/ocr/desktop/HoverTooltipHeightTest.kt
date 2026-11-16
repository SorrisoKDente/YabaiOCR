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
