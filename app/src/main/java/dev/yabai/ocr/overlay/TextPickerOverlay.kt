package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import dev.yabai.ocr.util.dpFloat
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen "pick a piece of the text" overlay, the way Lens works: after a whole-screen capture
 * the recognised paragraphs are drawn as boxes over the live screen, and tapping one sends only that
 * paragraph to the result panel.
 *
 * The boxes are placed in display pixels and the window covers the whole screen, so a box's
 * coordinates in this view are also its coordinates on screen.
 *
 * When the readings are switched on, they are drawn where the text is: a column of kana to the right
 * of a vertical column, a small line above a horizontal one ([PickerReadingMode.RUBY]), or as a
 * readable strip beside each balloon ([PickerReadingMode.LABEL]). Nothing has to be tapped for them
 * to appear — that was the point of the request.
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

    fun show(pickerBlocks: List<PickerBlock>, mode: PickerReadingMode) {
        blocks.setBlocks(pickerBlocks, mode)
    }
}

/**
 * Draws the boxes and turns taps into an index.
 *
 * The border is drawn in the app's accent colour rather than white: a white outline vanishes on a
 * manga page, which is white, and the boxes have to be visible on whatever happens to be underneath.
 *
 * Readings are drawn with a dark halo under a light fill. They land on top of the artwork — that is
 * the whole idea of ruby — and a thin white glyph over a screentone disappears without it.
 */
private class BlockCanvasView(context: Context) : View(context) {

    var onTapped: ((Int) -> Unit)? = null

    private var blocks: List<PickerBlock> = emptyList()
    private var mode = PickerReadingMode.OFF
    private var pressed = -1

    /**
     * Where each strip ended up when it was drawn, so a tap on a strip picks its balloon instead of
     * being read as a tap outside the picker — which would close it.
     */
    private val strips = HashMap<Int, RectF>()

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

    private val readingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFFF7F7F7.toInt()
    }
    private val readingHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        style = Paint.Style.STROKE
        color = 0xE6000000.toInt()
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F2F2.toInt()
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics,
        )
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6141414.toInt()
    }
    private val labelBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpFloat(1f)
        color = 0x33FFFFFF
    }
    private val labelPad = dpFloat(6f)
    private val labelRadius = dpFloat(6f)

    fun setBlocks(blocks: List<PickerBlock>, mode: PickerReadingMode) {
        this.blocks = blocks
        this.mode = mode
        pressed = -1
        strips.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((index, block) in blocks.withIndex()) {
            val box = RectF(block.rect)
            val hovered = index == pressed
            canvas.drawRoundRect(box, radius, radius, if (hovered) pressedFillPaint else fillPaint)
            canvas.drawRoundRect(box, radius, radius, if (hovered) pressedBorderPaint else borderPaint)
        }
        when (mode) {
            PickerReadingMode.OFF -> Unit
            PickerReadingMode.RUBY -> blocks.forEach { drawRuby(canvas, it) }
            PickerReadingMode.LABEL -> blocks.forEachIndexed { index, block -> drawLabel(canvas, index, block) }
        }
    }

    /** The reading beside the characters it belongs to, on every column and row of the page. */
    private fun drawRuby(canvas: Canvas, block: PickerBlock) {
        for (piece in block.pieces) {
            for (reading in piece.readings) {
                val spot = RubyLayout.spot(
                    left = piece.rect.left.toFloat(),
                    top = piece.rect.top.toFloat(),
                    width = piece.rect.width().toFloat(),
                    height = piece.rect.height().toFloat(),
                    vertical = piece.vertical,
                    from = reading.from,
                    to = reading.to,
                    characters = reading.text.length,
                ) ?: continue
                drawReading(canvas, reading.text, spot)
            }
        }
    }

    private fun drawReading(canvas: Canvas, text: String, spot: RubySpot) {
        readingPaint.textSize = spot.size
        readingHalo.textSize = spot.size
        readingHalo.strokeWidth = max(1.5f, spot.size * 0.24f)

        if (spot.stacked) {
            // Vertical ruby: one kana under the other, in the gutter to the right of the column.
            var baseline = spot.y + spot.size
            for (character in text) {
                val glyph = character.toString()
                canvas.drawText(glyph, spot.x, baseline, readingHalo)
                canvas.drawText(glyph, spot.x, baseline, readingPaint)
                baseline += spot.size
            }
            return
        }

        val baseline = spot.y + spot.size
        readingPaint.textAlign = Paint.Align.CENTER
        readingHalo.textAlign = Paint.Align.CENTER
        canvas.drawText(text, spot.x, baseline, readingHalo)
        canvas.drawText(text, spot.x, baseline, readingPaint)
        readingPaint.textAlign = Paint.Align.LEFT
        readingHalo.textAlign = Paint.Align.LEFT
    }

    /** The balloon's sentence with its readings, in a strip that fits beside it. */
    private fun drawLabel(canvas: Canvas, index: Int, block: PickerBlock) {
        val text = block.label ?: return
        val maxWidth = min(width * 0.62f, width - labelPad * 2)
        if (maxWidth < labelPad * 4) return
        val lines = wrap(text, labelPaint, maxWidth, MAX_LABEL_LINES)
        if (lines.isEmpty()) return

        val textWidth = lines.maxOf { labelPaint.measureText(it) }
        val chipWidth = (textWidth + labelPad * 2).toInt()
        val lineHeight = labelPaint.fontSpacing
        val chipHeight = (lineHeight * lines.size + labelPad * 2).toInt()

        val chip = LabelLayout.chip(
            boxLeft = block.rect.left,
            boxTop = block.rect.top,
            boxRight = block.rect.right,
            boxBottom = block.rect.bottom,
            width = chipWidth,
            height = chipHeight,
            screenWidth = width,
            screenHeight = height,
        )
        val box = RectF(
            chip.left.toFloat(),
            chip.top.toFloat(),
            chip.left + chipWidth.toFloat(),
            chip.top + chipHeight.toFloat(),
        )
        canvas.drawRoundRect(box, labelRadius, labelRadius, labelBackground)
        canvas.drawRoundRect(box, labelRadius, labelRadius, labelBorder)
        strips[index] = box

        var baseline = chip.top + labelPad - labelPaint.ascent()
        for (line in lines) {
            canvas.drawText(line, chip.left + labelPad, baseline, labelPaint)
            baseline += lineHeight
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
                val index = hitTest(event.x, event.y).takeIf { it >= 0 }
                    ?: stripHit(event.x, event.y)
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
     * The strip of a balloon, when the tap landed on one. A strip is drawn beside its box and can
     * reach outside it, so without this a tap on it would be «outside» and would close the picker.
     */
    private fun stripHit(x: Float, y: Float): Int {
        for ((index, rect) in strips) {
            if (x >= rect.left - slop && x <= rect.right + slop &&
                y >= rect.top - slop && y <= rect.bottom + slop
            ) {
                return index
            }
        }
        return -1
    }

    /**
     * The smallest box under the finger wins, so a label sitting inside a bigger one stays reachable.
     * Boxes are slightly inflated first: a single line of Latin text is only about twenty pixels tall
     * and would otherwise be a cruel tap target.
     */
    private fun hitTest(x: Float, y: Float): Int {
        var best = -1
        var bestArea = Int.MAX_VALUE
        for ((index, block) in blocks.withIndex()) {
            val rect: Rect = block.rect
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

    /**
     * Breaks [text] into at most [maxLines] lines that fit [maxWidth], ending in an ellipsis when
     * something had to be left out. Japanese has no spaces to break at, so it breaks between
     * characters — which is what a Japanese reader expects anyway.
     */
    private fun wrap(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val out = ArrayList<String>(maxLines)
        var start = 0
        while (start < text.length && out.size < maxLines) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count <= 0) break
            out.add(text.substring(start, start + count))
            start += count
        }
        if (start < text.length && out.isNotEmpty()) {
            val last = out[out.size - 1]
            out[out.size - 1] = if (last.length > 1) last.dropLast(1) + "…" else "…"
        }
        return out
    }

    private companion object {
        const val MAX_LABEL_LINES = 4
    }
}
