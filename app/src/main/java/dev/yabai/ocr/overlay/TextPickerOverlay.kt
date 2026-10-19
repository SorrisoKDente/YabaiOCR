package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import dev.yabai.ocr.util.dpFloat

/**
 * Full-screen "pick a piece of the text" overlay, the way Lens works: after a whole-screen capture
 * the recognised paragraphs are drawn as boxes over the live screen, and tapping one sends only that
 * paragraph to the result panel.
 *
 * The boxes are placed in display pixels and the window covers the whole screen, so a box's
 * coordinates in this view are also its coordinates on screen.
 */
class TextPickerOverlay(context: Context) : FrameLayout(context) {

    /** Index into the list of boxes the picker was shown with. */
    var onPick: ((Int) -> Unit)? = null

    /** The user asked for the whole capture instead of a single box. */
    var onPickAll: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val blocks = BlockCanvasView(context)

    init {
        setBackgroundColor(0x33000000)
        isClickable = true
        // BACK leaves the selection the same way «Cancelar» does. The window is focusable while the
        // picker is up (see `OverlayController.showTextPicker`), so the key arrives here instead of
        // turning into a «back» in the app underneath.
        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onCancel?.invoke()
                true
            } else {
                false
            }
        }

        addView(blocks, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        blocks.onTapped = { index ->
            if (index >= 0) onPick?.invoke(index) else onCancel?.invoke()
        }

        val hint = textLabel(context.getString(R.string.picker_hint), 13f, android.graphics.Color.WHITE).apply {
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = roundedBackground(0xCC000000.toInt(), dp(20).toFloat())
        }
        addView(
            hint,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = dp(48) },
        )

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(context.getColor(R.color.panel_bg), dp(16).toFloat(), 0x22FFFFFF)
            addView(actionButton(context.getString(R.string.picker_cancel)) { onCancel?.invoke() })
            addView(
                actionButton(context.getString(R.string.picker_all)) { onPickAll?.invoke() },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(6) },
            )
        }
        addView(
            bar,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply { bottomMargin = dp(40) },
        )
    }

    fun show(rects: List<Rect>) {
        blocks.setBlocks(rects)
    }
}

/**
 * Draws the boxes and turns taps into an index.
 *
 * The border is drawn in the app's accent colour rather than white: a white outline vanishes on a
 * manga page, which is white, and the boxes have to be visible on whatever happens to be underneath.
 */
private class BlockCanvasView(context: Context) : View(context) {

    var onTapped: ((Int) -> Unit)? = null

    private var rects: List<Rect> = emptyList()
    private var pressed = -1

    private val slop = dpFloat(4f)
    private val radius = dpFloat(4f)

    private val accent = context.getColor(R.color.accent)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (0x26 shl 24) or (accent and 0x00FFFFFF)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpFloat(1.5f)
        color = accent
    }
    private val pressedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (0x8C shl 24) or (accent and 0x00FFFFFF)
    }
    private val pressedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpFloat(3f)
        color = accent
    }

    fun setBlocks(rects: List<Rect>) {
        this.rects = rects
        pressed = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((index, rect) in rects.withIndex()) {
            val box = RectF(rect)
            val hovered = index == pressed
            canvas.drawRoundRect(box, radius, radius, if (hovered) pressedFillPaint else fillPaint)
            canvas.drawRoundRect(box, radius, radius, if (hovered) pressedBorderPaint else borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hitTest(event.x, event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = hitTest(event.x, event.y)
                if (index != pressed) {
                    pressed = index
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val index = hitTest(event.x, event.y)
                pressed = -1
                invalidate()
                onTapped?.invoke(index)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * The smallest box under the finger wins, so a label sitting inside a bigger one stays reachable.
     * Boxes are slightly inflated first: a single line of Latin text is only about twenty pixels tall
     * and would otherwise be a cruel tap target.
     */
    private fun hitTest(x: Float, y: Float): Int {
        var best = -1
        var bestArea = Int.MAX_VALUE
        for ((index, rect) in rects.withIndex()) {
            if (x < rect.left - slop || x > rect.right + slop) continue
            if (y < rect.top - slop || y > rect.bottom + slop) continue
            val area = rect.width() * rect.height()
            if (area < bestArea) {
                bestArea = area
                best = index
            }
        }
        return best
    }
}
