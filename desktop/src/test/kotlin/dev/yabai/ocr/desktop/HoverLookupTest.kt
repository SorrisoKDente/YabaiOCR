package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.core.lens.LensSegment
import dev.yabai.ocr.core.lens.PixelBox
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.Point
import java.awt.Rectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing a dictionary in the hover card has to mean something.
 *
 * The lookup used to repeat the search over every dictionary when the chosen one had no entry, so
 * the card answered with a definition the user had not asked for — and nothing said the choice had
 * been ignored. These tests hold the rule: the chosen dictionary answers, even when the answer is
 * «not here», and the notice names it.
 */
class HoverLookupTest {

    private val line = LensSegment("日本語", PixelBox(100, 100, 360, 40))

    private fun result() =
        LensResult(language = "ja", segments = listOf(line), blocks = emptyList(), elapsedMs = 1)

    private fun match(title: String) = TokenMatch(
        surface = "日本語",
        start = 0,
        end = 3,
        entry = DictionaryEntry(
            term = "日本語",
            reading = "にほんご",
            tags = "n",
            rulesFlags = 0,
            score = 0,
            definition = "the Japanese language",
        ),
        dictionaryTitle = title,
        reasons = emptyList(),
    )

    /** Only «Jitendex» has the word; «Wikcionário» does not. */
    private val lookup: (String, Set<String>) -> List<TokenMatch> = { _, only ->
        if (only.isEmpty() || only.contains("Jitendex")) listOf(match("Jitendex")) else emptyList()
    }

    @Test
    fun `the chosen dictionary answers and nothing else is searched`() {
        val point = Point(120, 120)

        val scoped = scopedHoverHit(result(), point, lookup, "Wikcionário")

        assertNull(
            "the hover fell back to another dictionary the user had not chosen",
            scoped,
        )
    }

    @Test
    fun `the chosen dictionary still answers when it has the entry`() {
        val hit = scopedHoverHit(result(), Point(120, 120), lookup, "Jitendex")

        assertNotNull("the chosen dictionary was not consulted", hit)
        assertEquals("Jitendex", hit?.match?.dictionaryTitle)
    }

    @Test
    fun `no filter means every dictionary`() {
        assertEquals(emptySet<String>(), dictionaryScope(""))
        assertEquals(emptySet<String>(), dictionaryScope("   "))
        assertEquals(setOf("Jitendex"), dictionaryScope("Jitendex"))

        val hit = scopedHoverHit(result(), Point(120, 120), lookup, "")
        assertNotNull("with no filter the word has to be found somewhere", hit)
    }

    @Test
    fun `a dictionary change with no entry answers from the text already read`() {
        // The card has just answered from «Jitendex»; the user picks «Wikcionário», which does not
        // have the word. Capturing the screen again would repeat the same OCR for the same answer.
        val answer = cachedHoverAnswer(result(), Point(120, 120), lookup, "Wikcionário")

        val missing = answer as? CachedHoverAnswer.MissingEntry
        assertNotNull("the card asked for a new capture to say the dictionary has no entry: $answer", missing)
        assertEquals("Wikcionário", missing!!.filter)
        assertTrue("the card has no anchor to hold on to: ${missing.word}", contains(missing.word, Point(120, 120)))
    }

    /** Whether [point] is inside [box]. */
    private fun contains(box: PixelBox, point: Point): Boolean =
        point.x >= box.x && point.x < box.right && point.y >= box.y && point.y < box.bottom

    @Test
    fun `a pointer over latin text is never told the dictionary has no entry`() {
        val latin = LensResult(
            language = "pt",
            segments = listOf(LensSegment("Configurações do aplicativo", PixelBox(100, 100, 420, 40))),
            blocks = emptyList(),
            elapsedMs = 1,
        )

        val answer = classifyHoverMiss(latin, Point(120, 120), lookup, "Wikcionário")

        // This is what made the card blink over the whole desktop: any text on screen, in any
        // language, was reported as «this dictionary has no entry».
        assertTrue("the card reported a missing entry over Latin text: $answer", answer !is HoverMiss.MissingEntry)
        assertTrue("the miss was not reported as text without a dictionary word: $answer", answer is HoverMiss.NoMatch)
    }

    @Test
    fun `a pointer on empty screen says nothing about dictionaries`() {
        // The line's box starts at x = 100: 20 px is far away, and 15 px is inside the tolerance
        // that still lets a *lookup* answer. A miss has no such tolerance — that slack is what let
        // the card say «no entry» for a spot of screen with no text on it.
        for (point in listOf(Point(20, 20), Point(85, 120), Point(120, 200))) {
            val answer = classifyHoverMiss(result(), point, lookup, "Wikcionário")
            assertTrue("the card opened over empty screen at $point: $answer", answer !is HoverMiss.MissingEntry)
        }
    }

    @Test
    fun `the missing entry is anchored on the word under the pointer`() {
        // No dictionary knows the word, so the anchor is the character's own cell — the pointer is
        // inside it by construction, which is what keeps the card open.
        val answer = classifyHoverMiss(result(), Point(120, 120), lookupNothing, "Wikcionário")
        val missing = answer as? HoverMiss.MissingEntry
        assertNotNull("the chosen dictionary's miss was not reported: $answer", missing)

        assertEquals(
            HoverTextSelector.tokenBox("日本語", line.box!!, 0, 1),
            missing!!.word,
        )
        assertTrue("the anchor does not contain the pointer: ${missing.word}", contains(missing.word, Point(120, 120)))
    }

    @Test
    fun `the card anchored on that word does not close while the pointer rests on it`() {
        val missing = classifyHoverMiss(result(), Point(120, 120), lookupNothing, "Wikcionário")
            as HoverMiss.MissingEntry
        val anchor = Rectangle(missing.word.x, missing.word.y, missing.word.width, missing.word.height)
        val gate = HoverDismissGate()

        // The card used to be shown with no anchor at all, so it counted as abandoned after the
        // grace period, closed, and the gate reopened it on the same spot — the blink.
        assertFalse("the card closed with the pointer on the word", gate.shouldClose(Point(120, 120), anchor, null, 0L))
        assertFalse("the card closed with the pointer on the word", gate.shouldClose(Point(120, 120), anchor, null, 60_000L))
    }



    @Test
    fun `a dictionary change that finds the word answers with that dictionary`() {
        val answer = cachedHoverAnswer(result(), Point(120, 120), lookup, "Jitendex")

        assertTrue("the cached answer is not a hit: $answer", answer is CachedHoverAnswer.Hit)
        assertEquals("Jitendex", (answer as CachedHoverAnswer.Hit).hit.match.dictionaryTitle)
    }

    /** A reading whose word is in none of the dictionaries. */
    private val lookupNothing: (String, Set<String>) -> List<TokenMatch> = { _, _ -> emptyList() }

    @Test
    fun `with every dictionary selected the cached answer is used as it is`() {
        val hit = cachedHoverAnswer(result(), Point(120, 120), lookup, "")

        assertTrue("with no filter the word is found and shown: $hit", hit is CachedHoverAnswer.Hit)
        assertEquals(
            "there is nothing to say about a dictionary nobody chose",
            CachedHoverAnswer.NeedsLookup,
            cachedHoverAnswer(result(), Point(120, 120), lookupNothing, ""),
        )
    }

    @Test
    fun `a filter of spaces means every dictionary too`() {
        // The card writes «all dictionaries» as a blank string; treating spaces as a chosen
        // dictionary would answer «no entry» for a choice the user never made.
        assertEquals(
            CachedHoverAnswer.NeedsLookup,
            cachedHoverAnswer(result(), Point(120, 120), lookupNothing, "   "),
        )
    }

    @Test
    fun `without a kept reading there is nothing to answer from`() {
        assertEquals(
            CachedHoverAnswer.NeedsLookup,
            cachedHoverAnswer(null, Point(120, 120), lookup, "Wikcionário"),
        )
        assertEquals(
            CachedHoverAnswer.NeedsLookup,
            cachedHoverAnswer(result(), null, lookup, "Wikcionário"),
        )
        assertEquals(
            CachedHoverAnswer.NeedsLookup,
            cachedHoverAnswer(result(), Point(120, 120), null, "Wikcionário"),
        )
    }

    @Test
    fun `the notice names the dictionary that has no entry`() {
        val notice = DesktopStrings.hoverDictionaryMissingEntry("Wikcionário")

        assertTrue("the notice does not name the dictionary: $notice", notice.contains("Wikcionário"))
        assertTrue(
            "the notice has to say the dictionary lacks the word: $notice",
            notice.contains("verbete"),
        )
    }
}
