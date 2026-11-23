package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import dev.yabai.ocr.util.dpFloat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen "pick a piece of the text" overlay, the way Lens works: after a whole-screen capture
 * the recognised paragraphs are drawn as boxes over the frozen capture, and tapping one sends only
 * that paragraph to the result panel.
 *
 * The boxes are placed in display pixels and the capture has one pixel per display pixel, so a box's
 * coordinates are also its coordinates on the page — which is what lets a pinch scale the drawing of
 * the page and the boxes with the same transform (R15). Without the frozen page there would be
 * nothing to zoom: the manga underneath belongs to another app and cannot be magnified from here.
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

    /** The page is a photo, not a screen capture: the user can take another one (R47a). */
    var onRetake: (() -> Unit)? = null

    /** Shown only while the page under the boxes came from the camera. */
    private val retakeButton by lazy {
        actionButton(context.getString(R.string.camera_retake)) { onRetake?.invoke() }.apply {
            visibility = View.GONE
        }
    }

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
                retakeButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(6) },
            )
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

    fun show(
        pickerBlocks: List<PickerBlock>,
        mode: PickerReadingMode,
        page: Bitmap? = null,
        /** Whether the page came from the camera, which is the only source with a retake. */
        retake: Boolean = false,
    ) {
        retakeButton.visibility = if (retake) View.VISIBLE else View.GONE
        blocks.setBlocks(pickerBlocks, mode)
        blocks.setPage(page)
    }

    /** The zoom the boxes are drawn with; the picker's touch tests read it too. */
    internal val zoomScale: Float get() = blocks.zoomScale

    /** Zooms the picker as a pinch would, for the tests that check what a tap means once zoomed. */
    internal fun zoomBy(factor: Float, focusX: Float, focusY: Float) = blocks.zoomBy(factor, focusX, focusY)
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
     * The frozen capture under the boxes (R15). A transparent picker over the live screen cannot
     * magnify the page — the page belongs to another app — so the picker shows the screen as it was
     * captured and lets the user pinch that.
     */
    private var page: Bitmap? = null

    private val zoom = PickerZoom()

    private val pagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var lastX = 0f
    private var lastY = 0f

    /** The finger is dragging the page, so lifting it must not pick the box it happens to be over. */
    private var dragging = false

    /** A second finger touched down: the gesture is a pinch, never a tap. */
    private var pinched = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom.zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                pressed = -1
                invalidate()
                return true
            }
        },
    )

    /**
     * Where each strip ended up when it was drawn, so a tap on a strip picks its balloon instead of
     * being read as a tap outside the picker — which would close it.
     */
    private val strips = HashMap<Int, RectF>()

    /** Strips already placed in this draw pass, so the next one can be kept off them. */
    private val placed = ArrayList<Strip>()

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
        // A box is placed for the screen the capture was taken on; a zoom from the previous page
        // would move it away from its text.
        zoom.reset(width.toFloat(), height.toFloat())
        invalidate()
    }

    fun setPage(bitmap: Bitmap?) {
        page = bitmap
        invalidate()
    }

    /** View pixels per page pixel: 1 when the window is exactly the capture, less when it is not. */
    private val pageScaleX: Float
        get() = page?.let { if (it.width > 0 && width > 0) width.toFloat() / it.width else 1f } ?: 1f

    private val pageScaleY: Float
        get() = page?.let { if (it.height > 0 && height > 0) height.toFloat() / it.height else 1f } ?: 1f

    /** A point of the window in page pixels: undo the zoom, then the stretch to the window (R23). */
    private fun pageX(x: Float): Float = zoom.toPageX(x) / pageScaleX

    private fun pageY(y: Float): Float = zoom.toPageY(y) / pageScaleY

    internal val zoomScale: Float get() = zoom.scale

    internal fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        zoom.zoomBy(factor, focusX, focusY)
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        zoom.reset(width.toFloat(), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Page, boxes and readings share one coordinate system — the screen as captured — so one
        // scale moves all of them together and a box can never drift off its text.
        canvas.save()
        canvas.translate(zoom.offsetX, zoom.offsetY)
        canvas.scale(zoom.scale, zoom.scale)
        // …and that system is the capture, not the window. The page is drawn stretched into the
        // window (a full-screen capture into a window that the system made shorter when the status
        // bar came back), so the boxes have to take the same stretch. Without this, every box sits
        // a few pixels off its text and the error grows toward the side the bars took (R23).
        canvas.scale(pageScaleX, pageScaleY)

        page?.let { bitmap ->
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                pagePaint,
            )
        }

        for ((index, block) in blocks.withIndex()) {
            val box = RectF(block.rect)
            val hovered = index == pressed
            canvas.drawRoundRect(box, radius, radius, if (hovered) pressedFillPaint else fillPaint)
            canvas.drawRoundRect(box, radius, radius, if (hovered) pressedBorderPaint else borderPaint)
        }
        when (mode) {
            PickerReadingMode.OFF -> Unit
            PickerReadingMode.RUBY -> blocks.forEach { drawRuby(canvas, it) }
            PickerReadingMode.LABEL -> {
                // Fresh each pass: both the hit areas and the strips already placed.
                placed.clear()
                strips.clear()
                blocks.forEachIndexed { index, block -> drawLabel(canvas, index, block) }
            }
        }
        canvas.restore()
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
        // Page pixels, like the boxes: the canvas stretch takes them to the window.
        val pageWidth = page?.width?.toFloat() ?: width.toFloat()
        val maxWidth = min(pageWidth * 0.62f, pageWidth - labelPad * 2)
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
            screenWidth = page?.width ?: width,
            screenHeight = page?.height ?: height,
            taken = placed,
        )
        placed += Strip(chip.left, chip.top, chip.left + chipWidth, chip.top + chipHeight)
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
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                pinched = false
                lastX = event.x
                lastY = event.y
                pressed = hitTest(pageX(event.x), pageY(event.y))
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger means a pinch: whatever this gesture turns out to be, it is not a tap.
                pinched = true
                dragging = false
                pressed = -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // The finger that stays continues the pan from where it is. Without this, the next
                // move would be measured from the finger that just left and the page would jump.
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    // The scale detector owns the zoom; a second finger is not a tap either.
                    lastX = event.x
                    lastY = event.y
                    return true
                }
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                // Only a zoomed page has somewhere to go, so at 1× a wobbly finger is still a tap.
                if (!dragging && zoom.scale > PickerZoom.MIN && abs(dx) + abs(dy) > touchSlop) {
                    dragging = true
                    pressed = -1
                }
                if (dragging) {
                    zoom.panBy(dx, dy)
                    invalidate()
                    return true
                }
                val index = hitTest(pageX(event.x), pageY(event.y))
                if (index != pressed) {
                    pressed = index
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tapped = !dragging && !pinched
                val index = if (tapped) {
                    hitTest(pageX(event.x), pageY(event.y)).takeIf { it >= 0 }
                        ?: stripHit(pageX(event.x), pageY(event.y))
                } else {
                    -1
                }
                pressed = -1
                dragging = false
                pinched = false
                invalidate()
                // Lifting the finger after a pan or a pinch is not a choice: it must not pick a box,
                // and it must not close the picker either.
                if (tapped) onTapped?.invoke(index)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                dragging = false
                pinched = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * The strip of a balloon, when the tap landed on one. A strip is drawn beside its box and can
     * reach outside it, so without this a tap on it would be «outside» and would close the picker.
     * Both [x] and [y] are page coordinates, the same the strips were laid out in.
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
     * and would otherwise be a cruel tap target. [x] and [y] are page coordinates, not view ones.
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
