package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import dev.yabai.ocr.util.dpFloat
import dev.yabai.ocr.util.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen "drag to select a region" overlay.
 *
 * [onConfirm] receives the selection in this view's coordinates (which equal display pixels
 * because the window covers the whole screen), or null when the user asked for the whole screen.
 */
class RegionOverlay(context: Context) : FrameLayout(context) {

    var onConfirm: ((Rect?) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val selector = RegionSelectorView(context)

    private val confirmButton: View
    private val fullButton: View

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true

        addView(
            selector,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        val hint = textLabel(context.getString(R.string.region_hint), 13f, Color.WHITE).apply {
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = roundedBackground(0xCC000000.toInt(), dp(20).toFloat())
        }
        addView(
            hint,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(48)
            },
        )

        confirmButton = actionButton(
            context.getString(R.string.region_confirm),
            textColor = Color.WHITE,
            background = context.getColor(R.color.accent),
        ) { emitSelection() }

        fullButton = actionButton(context.getString(R.string.region_full)) {
            selector.selectEverything()
        }

        val cancelButton = actionButton(context.getString(R.string.region_cancel)) {
            onCancel?.invoke()
        }

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(context.getColor(R.color.panel_bg), dp(16).toFloat(), 0x22FFFFFF)
            addView(cancelButton)
            addView(fullButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6) })
            addView(confirmButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6) })
        }

        addView(
            bar,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(40)
            },
        )

        selector.onSelectionChanged = { refreshButtons(it) }
        refreshButtons(null)
    }

    private fun refreshButtons(rect: Rect?) {
        val enabled = rect != null && rect.width() > dp(8) && rect.height() > dp(8)
        confirmButton.isEnabled = enabled
        confirmButton.alpha = if (enabled) 1f else 0.4f
    }

    private fun emitSelection() {
        val rect = selector.selectionRect()
        if (rect == null || rect.width() <= 1 || rect.height() <= 1) {
            onConfirm?.invoke(null)
        } else {
            onConfirm?.invoke(rect)
        }
    }
}

/**
 * Draws the dimmed backdrop, the clear selection window and its handles, and turns drag gestures
 * into a rectangle.
 */
class RegionSelectorView(context: Context) : View(context) {

    var onSelectionChanged: ((Rect?) -> Unit)? = null

    private val selection = RectF()
    private var hasSelection = false

    private var mode = MODE_NONE
    private var lastX = 0f
    private var lastY = 0f

    private val edge = dpFloat(2.5f)
    private val touchSlop = dpFloat(26f)
    private val minSize = dpFloat(24f)

    private val dimPaint = Paint().apply { color = 0xB3000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = edge
        color = Color.WHITE
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val thirdsPaint = Paint().apply {
        color = 0x4DFFFFFF
        strokeWidth = dpFloat(1f)
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9000000.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(12f)
    }

    fun selectionRect(): Rect? {
        if (!hasSelection) return null
        val rect = Rect(
            selection.left.toInt(),
            selection.top.toInt(),
            selection.right.toInt(),
            selection.bottom.toInt(),
        )
        return if (rect.width() <= 1 || rect.height() <= 1) null else rect
    }

    fun selectEverything() {
        selection.set(0f, 0f, width.toFloat(), height.toFloat())
        hasSelection = true
        invalidate()
        notifyChanged()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (!hasSelection || selection.width() < 1f || selection.height() < 1f) {
            canvas.drawRect(0f, 0f, w, h, dimPaint)
            return
        }

        // Four dimming bands instead of a CLEAR layer: works with hardware acceleration everywhere.
        canvas.drawRect(0f, 0f, w, selection.top, dimPaint)
        canvas.drawRect(0f, selection.bottom, w, h, dimPaint)
        canvas.drawRect(0f, selection.top, selection.left, selection.bottom, dimPaint)
        canvas.drawRect(selection.right, selection.top, w, selection.bottom, dimPaint)

        val thirdW = selection.width() / 3f
        val thirdH = selection.height() / 3f
        for (i in 1..2) {
            canvas.drawLine(selection.left + thirdW * i, selection.top, selection.left + thirdW * i, selection.bottom, thirdsPaint)
            canvas.drawLine(selection.left, selection.top + thirdH * i, selection.right, selection.top + thirdH * i, thirdsPaint)
        }

        canvas.drawRect(selection, borderPaint)

        val r = edge * 2.5f
        listOf(
            selection.left to selection.top,
            selection.right to selection.top,
            selection.left to selection.bottom,
            selection.right to selection.bottom,
        ).forEach { (cx, cy) -> canvas.drawCircle(cx, cy, r, handlePaint) }

        // Size badge, so the user can tell how big the capture is.
        val label = "${selection.width().toInt()} x ${selection.height().toInt()}"
        val textWidth = labelPaint.measureText(label)
        val pad = dpFloat(8f)
        val badgeTop = if (selection.top > dpFloat(40f)) {
            selection.top - dpFloat(30f)
        } else {
            selection.bottom + dpFloat(8f)
        }
        val badgeLeft = min(
            max(dpFloat(8f), selection.left),
            w - textWidth - pad * 2 - dpFloat(8f),
        )
        canvas.drawRoundRect(
            badgeLeft,
            badgeTop,
            badgeLeft + textWidth + pad * 2,
            badgeTop + dpFloat(22f),
            dpFloat(6f),
            dpFloat(6f),
            labelBackground,
        )
        canvas.drawText(label, badgeLeft + pad, badgeTop + dpFloat(16f), labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                mode = hitTest(x, y)
                if (mode == MODE_NEW) {
                    selection.set(x, y, x, y)
                    hasSelection = true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    MODE_NEW -> {
                        selection.set(min(lastX, x), min(lastY, y), max(lastX, x), max(lastY, y))
                    }

                    MODE_MOVE -> {
                        val dx = x - lastX
                        val dy = y - lastY
                        selection.offset(dx, dy)
                        constrainToBounds()
                        lastX = x
                        lastY = y
                    }

                    MODE_LEFT -> selection.left = x
                    MODE_RIGHT -> selection.right = x
                    MODE_TOP -> selection.top = y
                    MODE_BOTTOM -> selection.bottom = y

                    MODE_TOP_LEFT -> {
                        selection.left = x
                        selection.top = y
                    }

                    MODE_TOP_RIGHT -> {
                        selection.right = x
                        selection.top = y
                    }

                    MODE_BOTTOM_LEFT -> {
                        selection.left = x
                        selection.bottom = y
                    }

                    MODE_BOTTOM_RIGHT -> {
                        selection.right = x
                        selection.bottom = y
                    }

                    else -> Unit
                }
                normalize()
                invalidate()
                notifyChanged()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (hasSelection && (selection.width() < minSize || selection.height() < minSize)) {
                    hasSelection = false
                    selection.setEmpty()
                }
                mode = MODE_NONE
                invalidate()
                notifyChanged()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTest(x: Float, y: Float): Int {
        if (!hasSelection || selection.isEmpty) return MODE_NEW
        val nearLeft = abs(x - selection.left) <= touchSlop
        val nearRight = abs(x - selection.right) <= touchSlop
        val nearTop = abs(y - selection.top) <= touchSlop
        val nearBottom = abs(y - selection.bottom) <= touchSlop
        val insideX = x >= selection.left - touchSlop && x <= selection.right + touchSlop
        val insideY = y >= selection.top - touchSlop && y <= selection.bottom + touchSlop

        return when {
            nearLeft && nearTop -> MODE_TOP_LEFT
            nearRight && nearTop -> MODE_TOP_RIGHT
            nearLeft && nearBottom -> MODE_BOTTOM_LEFT
            nearRight && nearBottom -> MODE_BOTTOM_RIGHT
            nearLeft && insideY -> MODE_LEFT
            nearRight && insideY -> MODE_RIGHT
            nearTop && insideX -> MODE_TOP
            nearBottom && insideX -> MODE_BOTTOM
            selection.contains(x, y) -> MODE_MOVE
            else -> MODE_NEW
        }
    }

    private fun normalize() {
        if (selection.left > selection.right) {
            val tmp = selection.left
            selection.left = selection.right
            selection.right = tmp
        }
        if (selection.top > selection.bottom) {
            val tmp = selection.top
            selection.top = selection.bottom
            selection.bottom = tmp
        }
    }

    private fun constrainToBounds() {
        val dx = when {
            selection.left < 0f -> -selection.left
            selection.right > width -> width - selection.right
            else -> 0f
        }
        val dy = when {
            selection.top < 0f -> -selection.top
            selection.bottom > height -> height - selection.bottom
            else -> 0f
        }
        selection.offset(dx, dy)
    }

    private fun notifyChanged() = onSelectionChanged?.invoke(selectionRect())

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_NEW = 1
        private const val MODE_MOVE = 2
        private const val MODE_LEFT = 3
        private const val MODE_RIGHT = 4
        private const val MODE_TOP = 5
        private const val MODE_BOTTOM = 6
        private const val MODE_TOP_LEFT = 7
        private const val MODE_TOP_RIGHT = 8
        private const val MODE_BOTTOM_LEFT = 9
        private const val MODE_BOTTOM_RIGHT = 10
    }
}
