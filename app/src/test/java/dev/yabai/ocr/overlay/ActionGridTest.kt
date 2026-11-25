package dev.yabai.ocr.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.util.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The grid the result panel's actions sit in (R6).
 *
 * The panel used to let its buttons flow and hang right: seven actions came out as rows of 3 / 1 / 2 /
 * 1 buttons of five widths, ragged on the left. What is held down here is what makes the block read as
 * a panel: equal columns, in the order the buttons were added, with an odd last button taking the
 * whole row — a half-width button with a hole beside it reads as a mistake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class ActionGridTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `actions go two per row, in the order they were added`() {
        val grid = layout(grid(7), 300)

        assertEquals(listOf(2, 2, 2, 1), rows(grid).map { it.size })
        assertEquals((1..7).map { "$it" }, labels(grid))
    }

    @Test
    fun `the two columns are the same width`() {
        val grid = layout(grid(6), 300)

        for (row in rows(grid)) {
            assertEquals("a row does not have two buttons", 2, row.size)
            assertEquals(
                "the columns of a row are ${row[0].width} and ${row[1].width} px",
                row[0].width.toDouble(),
                row[1].width.toDouble(),
                1.0,
            )
        }
    }

    /** A long label must not steal width from its neighbour: the columns are fixed, the text wraps. */
    @Test
    fun `a long label does not push the other column out`() {
        val grid = layout(
            ActionGrid(context).apply {
                add(context.actionButton("Perguntar à IA sobre a página inteira"))
                add(context.actionButton("Fechar"))
                add(context.actionButton("Curto"))
                add(context.actionButton("Outro"))
            },
            300,
        )

        for (row in rows(grid)) {
            assertEquals(row[0].width.toDouble(), row[1].width.toDouble(), 1.0)
            assertTrue("the columns overlap", row[1].left >= row[0].right)
        }
    }

    @Test
    fun `an odd last action takes the whole row`() {
        val grid = layout(grid(7), 300)
        val last = rows(grid).last().single()
        val rowsOfTwo = rows(grid).first()

        assertEquals(
            "the last button stops at ${last.width} px instead of filling the 300 px row",
            300.0,
            last.width.toDouble(),
            1.0,
        )
        assertTrue(
            "the last button is not as wide as two columns and the gap",
            last.width > rowsOfTwo[0].width + rowsOfTwo[1].width,
        )
    }

    @Test
    fun `a single action fills the row`() {
        val grid = layout(grid(1), 300)

        assertEquals(300.0, rows(grid).single().single().width.toDouble(), 1.0)
    }

    @Test
    fun `fewer actions reflow without leaving a gap in the middle`() {
        val grid = layout(grid(3), 300)

        assertEquals(listOf(2, 1), rows(grid).map { it.size })
        assertEquals(3, labels(grid).size)
    }

    /**
     * R47g: «Fechar» keeps the bottom-left corner whatever the other actions are. R50: it fills the
     * free cell of the last row instead of opening one, which is a whole row of dictionary text won.
     */
    @Test
    fun `the closing button starts the last row`() {
        val grid = layout(
            ActionGrid(context).apply {
                add(context.actionButton("1"))
                add(context.actionButton("2"))
                add(context.actionButton("3"))
                addClosing(context.actionButton("Fechar"))
            },
            300,
        )

        assertEquals(listOf(2, 2), rows(grid).map { it.size })
        assertEquals("Fechar", rows(grid).last().first().text.toString())
    }

    /** With no free cell left, the way out keeps the corner on a row of its own. */
    @Test
    fun `a full last row still leaves the closing button at the bottom-left`() {
        val grid = layout(
            ActionGrid(context).apply {
                add(context.actionButton("1"))
                add(context.actionButton("2"))
                addClosing(context.actionButton("Fechar"))
            },
            300,
        )

        assertEquals(listOf(2, 1), rows(grid).map { it.size })
        assertEquals("Fechar", rows(grid).last().single().text.toString())
    }

    /**
     * R50: a phone on its side has width to spare and no height at all, so the actions get a fourth
     * column and smaller boxes — six of them fit in two rows instead of three.
     */
    @Test
    @Config(qualifiers = "w780dp-h360dp-xxhdpi")
    fun `a sideways phone puts four actions on a row`() {
        val grid = layout(grid(8), context.dp(780))

        assertEquals(listOf(4, 4), rows(grid).map { it.size })
        for (button in rows(grid).flatten()) {
            assertEquals("a grid action is ${button.textSize}px tall of text", 12f, button.textSize / context.resources.displayMetrics.scaledDensity, 0.1f)
            assertEquals("a grid action is ${button.minHeight}px tall", context.dp(26), button.minHeight)
        }
    }

    /** Upright, the buttons keep the size that reads well above a whole card of dictionary text. */
    @Test
    fun `a phone held up keeps the larger grid buttons`() {
        val button = rows(layout(grid(1), 300)).single().single()

        assertEquals(13f, button.textSize / context.resources.displayMetrics.scaledDensity, 0.1f)
        assertEquals(context.dp(30), button.minHeight)
    }

    /** The sideways closing button shares the last row, so six actions cost two rows, not three. */
    @Test
    @Config(qualifiers = "w780dp-h360dp-xxhdpi")
    fun `sideways, six actions and the way out fit in two rows`() {
        val grid = layout(
            ActionGrid(context).apply {
                for (index in 1..6) add(context.actionButton("$index"))
                addClosing(context.actionButton("Fechar"))
            },
            context.dp(780),
        )

        assertEquals(listOf(4, 3), rows(grid).map { it.size })
        assertEquals("Fechar", rows(grid).last().first().text.toString())
    }

    /** Two buttons in a row are separated: touching backgrounds read as one wide button. */
    @Test
    fun `the columns have a gap between them`() {
        val grid = layout(grid(2), 300)
        val row = rows(grid).single()

        assertTrue("the columns touch: ${row[0].right} and ${row[1].left}", row[1].left > row[0].right)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun grid(count: Int): ActionGrid = ActionGrid(context).apply {
        for (index in 1..count) add(context.actionButton("$index"))
    }

    /** The rows of the grid, top to bottom. */
    private fun rows(grid: ActionGrid): List<List<TextView>> {
        val found = ArrayList<List<TextView>>()
        for (index in 0 until grid.childCount) {
            val row = grid.getChildAt(index) as ViewGroup
            found.add(
                (0 until row.childCount)
                    .map { row.getChildAt(it) }
                    .filterIsInstance<TextView>(),
            )
        }
        return found
    }

    private fun labels(grid: ActionGrid): List<String> = rows(grid).flatten().map { it.text.toString() }

    private fun <T : View> layout(view: T, width: Int): T {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, width, view.measuredHeight)
        return view
    }
}
