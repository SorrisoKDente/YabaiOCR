package dev.yabai.ocr.overlay

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.yabai.ocr.util.dp

/**
 * The panel's actions, in columns of equal width.
 *
 * The panel used to let its buttons flow: each row took as many as fitted and was pushed to the right
 * edge. With seven actions that came out as rows of 3 / 1 / 2 / 1 buttons of five different widths, all
 * ragged on the left — nothing overlapped, but the block read as a pile instead of a panel. Equal
 * columns fix what the eye is following: every button starts on one of the column lines, and every row
 * has the same widths.
 *
 * The number of columns follows the screen: two on a phone held up, where a wider button would need a
 * label to wrap, three on a tablet, and four on a phone turned sideways — where the window has width
 * to spare and no height at all, and a fourth column is what keeps the actions in two rows instead of
 * three, with the dictionary text winning the room back (R50).
 *
 * A last row that does not fill every cell stretches its buttons across the row. A half-width button
 * with an empty hole beside it looks like a button that failed to load; one that fills the row looks
 * like the end of the list.
 */
class ActionGrid(context: Context) : LinearLayout(context) {

    /** A phone on its side: less height for the text, so tighter rows and one more column (R50). */
    private val compact: Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * Chosen once, from the screen the panel is opening on. A capture closes and reopens the card, so
     * the grid never lives across a rotation.
     */
    private val columns: Int = when {
        compact -> 4
        context.resources.configuration.screenWidthDp >= THREE_COLUMN_WIDTH_DP -> 3
        else -> 2
    }

    private val gap: Int = context.dp(if (compact) GAP_COMPACT_DP else GAP_DP)

    init {
        orientation = VERTICAL
        tag = GRID_TAG
    }

    private var row: LinearLayout? = null
    private var filled = 0

    /** Puts [button] in the next free cell, left to right and top to bottom. */
    fun add(button: View) {
        // The grid is tighter than a button standing on its own: these sit under the text the user is
        // reading, so every pixel of height they take is a line of text they cover.
        (button as? TextView)?.applyGridStyle(compact)
        var target = row
        if (target == null || filled >= columns) {
            target = newRow()
            row = target
            filled = 0
        }
        target.addView(
            button,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                if (filled > 0) marginStart = gap
            },
        )
        filled++
    }

    /**
     * Puts the way out at the bottom-left, in a row of its own only when there is no room left.
     *
     * «Fechar» must be found in the same corner every time, and adding it last would put it on the
     * right whenever the row before it ended on an odd count. Filling the free cell of the last row
     * instead of opening another one is what keeps a sideways phone to two rows of actions instead
     * of three — the row saved is a line of dictionary text (R50).
     */
    fun addClosing(button: View) {
        val target = row
        if (target != null && filled < columns) {
            (button as? TextView)?.applyGridStyle(compact)
            target.addView(button, 0, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            // The button that was first in the row is now second, and the first cell never has a gap.
            (target.getChildAt(1).layoutParams as LayoutParams).marginStart = gap
            filled++
            return
        }
        row = null
        filled = 0
        add(button)
    }

    private fun newRow(): LinearLayout {
        val created = LinearLayout(context).apply {
            orientation = HORIZONTAL
            // A long label that wraps must not leave the two buttons of a row top-aligned.
            gravity = Gravity.CENTER_VERTICAL
            tag = ROW_TAG
        }
        val first = childCount == 0
        addView(
            created,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (!first) topMargin = context.dp(GAP_DP)
            },
        )
        return created
    }

    companion object {
        /** Tag on the grid itself, so a layout test can measure it. */
        const val GRID_TAG = "yabai.actionGrid"

        /** Tag on each row of the grid. */
        const val ROW_TAG = "yabai.actionGridRow"

        /** Both the gap between columns and the gap between rows. */
        private const val GAP_DP = 6

        /** The same gap on a sideways phone, where every pixel of height is text. */
        private const val GAP_COMPACT_DP = 4

        /** From this width on, three labels fit side by side without wrapping. */
        private const val THREE_COLUMN_WIDTH_DP = 600
    }
}
