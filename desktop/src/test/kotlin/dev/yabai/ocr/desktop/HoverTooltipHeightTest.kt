package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
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
}
