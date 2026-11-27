package dev.yabai.ocr.ocr

import android.graphics.Rect
import dev.yabai.ocr.core.lens.PrintedReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R64: the ML Kit path must mark the furigana the page prints, so the picker's ruby steps aside
 * there instead of landing on the print.
 *
 * The geometry is the same Lens uses; what these tests hold down is that the engine's own lines are
 * handed to it, and that a normalised line never claims a stretch that moved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MlKitPiecesTest {

    @Test
    fun `a printed reading is marked on the line it annotates`() {
        val pieces = mlKitPieces(
            listOf(
                "職員さん" to Rect(0, 100, 200, 140),
                "しょくいん" to Rect(0, 80, 90, 96),
            ),
        )

        assertEquals(listOf(PrintedReading(0, 2)), pieces[0].printed)
        assertTrue("a reading has nothing to annotate itself", pieces[1].printed.isEmpty())
    }

    @Test
    fun `a normalised line that got shorter does not claim a moved stretch`() {
        // The space between the two kanji disappears in the normalised text: the offsets no longer
        // point at the same characters, and naming the wrong ones would hide a real reading.
        val pieces = mlKitPieces(
            listOf(
                "職 員さん" to Rect(0, 100, 200, 140),
                "しょくいん" to Rect(0, 80, 90, 96),
            ),
        )

        assertTrue(pieces[0].printed.isEmpty())
    }
}
