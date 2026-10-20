package dev.yabai.ocr.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import dev.yabai.ocr.util.dp

/**
 * The panel's actions, two per row, in columns of equal width.
 *
 * The panel used to let its buttons flow: each row took as many as fitted and was pushed to the right
 * edge. With seven actions that came out as rows of 3 / 1 / 2 / 1 buttons of five different widths, all
 * ragged on the left — nothing overlapped, but the block read as a pile instead of a panel. Two equal
 * columns fix what the eye is following: every button starts on one of two lines, and every row has the
 * same two widths.
 *
 * A last button left alone in its row takes the whole width. A half-width button with an empty hole
 * beside it looks like a button that failed to load; a full-width one looks like the end of the list.
 */
class ActionGrid(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        tag = GRID_TAG
    }

    private var row: LinearLayout? = null
    private var filled = 0

    /** Puts [button] in the next free cell, left to right and top to bottom. */
    fun add(button: View) {
        var target = row
        if (target == null || filled >= COLUMNS) {
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

        /** Columns. Two is what fits a button label without wrapping on a 360 dp phone. */
        private const val COLUMNS = 2

        /** Both the gap between columns and the gap between rows. */
        private const val GAP_DP = 6
    }
}
