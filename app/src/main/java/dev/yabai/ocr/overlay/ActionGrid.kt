package dev.yabai.ocr.overlay

import android.content.Context
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
 * label to wrap, and three once the screen is wide enough for a row to hold three labels. A landscape
 * phone has very little height, and the third column is what keeps six actions in two rows instead of
 * three — rows that used to push the last button off the bottom of the card.
 *
 * A last row that does not fill every cell stretches its buttons across the row. A half-width button
 * with an empty hole beside it looks like a button that failed to load; one that fills the row looks
 * like the end of the list.
 */
class ActionGrid(context: Context) : LinearLayout(context) {

    /**
     * Chosen once, from the screen the panel is opening on. A capture closes and reopens the card, so
     * the grid never lives across a rotation.
     */
    private val columns: Int =
        if (context.resources.configuration.screenWidthDp >= THREE_COLUMN_WIDTH_DP) 3 else 2

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
        (button as? TextView)?.applyGridStyle()
        var target = row
        if (target == null || filled >= columns) {
            target = newRow()
            row = target
            filled = 0
        }
        target.addView(
            button,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                if (filled > 0) marginStart = context.dp(GAP_DP)
            },
        )
        filled++
    }

    /**
     * Puts [button] at the start of a row of its own.
     *
     * Used for the one action that has to keep its place in the panel: «Fechar» is the way out, and
     * it stays at the bottom-left whatever the other actions are. Simply adding it last would put it
     * on the right whenever the row before it ended on an odd count.
     */
    fun addOnOwnRow(button: View) {
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

        /** From this width on, three labels fit side by side without wrapping. */
        private const val THREE_COLUMN_WIDTH_DP = 600
    }
}
