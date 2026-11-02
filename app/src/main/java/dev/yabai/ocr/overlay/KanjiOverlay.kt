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
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.core.layout.KanjiGrid
import dev.yabai.ocr.core.lens.PixelBox
import dev.yabai.ocr.util.dp
import kotlin.math.abs

/** One stretch of recognised text on the page — a column of vertical text, or a row — in screen pixels. */
data class KanjiPiece(val text: String, val rect: Rect, val vertical: Boolean)

/** Everything the card says about the kanji that was tapped. */
data class KanjiCard(
    val character: String,
    /** The reading the word around it has in this sentence, e.g. はら for 腹. */
    val reading: String?,
    /** The word that reading came from, when it is more than the character itself. */
    val word: String?,
    val onYomi: String,
    val kunYomi: String,
    /** The kanji dictionary's own text: meanings and statistics. */
    val meaning: String,
    /**
     * What the word dictionary says the word above means (R34a). The kanji entry explains the
     * character; this explains the compound the character was tapped inside.
     */
    val wordMeaning: String = "",
    /** The dictionary the entry came from, when one answered (R28). */
    val source: String? = null,
    /** The word's reading, one part per kanji, when the dictionaries justify a division (R29). */
    val parts: List<KanjiPart> = emptyList(),
    /** The kanji of the word to outline on the page, in the piece's characters (R29). */
    val span: IntRange? = null,
    /** Said instead of the dictionary part when the kanji dictionary could not answer. */
    val note: String? = null,
)

/**
 * Full-screen «which kanji is this?» overlay (R17).
 *
 * It shows the frozen capture with a thin outline around every recognised strip, and turns a tap
 * into a character: the strip is a grid of full-width cells, so the position of the finger along it
 * is the position of the character in the text ([KanjiGrid]). The card with the readings is filled
 * by the service, because that is where the dictionaries live.
 *
 * The page can be pinched and dragged exactly like the text picker (R15): a column of furigana-sized
 * text is a hard target at 1×, and this overlay is where hitting the right character matters most.
 */
class KanjiOverlay(context: Context) : FrameLayout(context) {

    /** [piece] indexes the list the overlay was shown with; [index] is the character inside it. */
    var onKanjiTapped: ((piece: Int, index: Int) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    /**
     * HOME and recents take the page away from under the reader (R41).
     *
     * Same hidden hook as [ResultOverlay.onSystemDialog]: the window manager calls
     * `closeSystemDialogs(reason)` on every window and the view root passes it to this view, which is
     * why the method is declared here without `override`. The controller decides what the reason
     * means, because this window is not the only one that has to go.
     */
    var onSystemDialog: ((String?) -> Unit)? = null

    @Suppress("unused")
    fun onCloseSystemDialogs(reason: String?) {
        onSystemDialog?.invoke(reason)
    }

    private val pageView = KanjiCanvasView(context)
    private val selectionToolbar by lazy { SelectionToolbar(this, context) }

    private fun selectableLabel(text: CharSequence, size: Float, color: Int, bold: Boolean = false): android.widget.EditText =
        android.widget.EditText(context).apply {
            setText(text)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = null
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = false
            isLongClickable = false
            setTextIsSelectable(true)
            showSoftInputOnFocus = false
            installSelection(this, selectionToolbar)
        }

    /** The page tap that asked for the card on screen; the card marks the page when it arrives (R29). */
    private var pressed: Pair<Int, Int>? = null

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBackground(context.getColor(R.color.panel_bg), dp(16).toFloat(), 0x22FFFFFF)
        val pad = dp(14)
        setPadding(pad, pad, pad, pad)
        // A tap on the card is not a tap on the page behind it.
        isClickable = true
        visibility = GONE
    }

    init {
        setBackgroundColor(0xFF000000.toInt())
        isClickable = true
        isFocusableInTouchMode = true

        addView(pageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        pageView.onTapped = { piece, index ->
            pressed = piece to index
            pageView.mark(piece, index)
            onKanjiTapped?.invoke(piece, index)
        }

        val hint = textLabel(context.getString(R.string.kanji_hint), 13f, android.graphics.Color.WHITE).apply {
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = roundedBackground(0xCC000000.toInt(), dp(20).toFloat())
        }
        addView(
            hint,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = dp(48) },
        )
        // Always there: before the first kanji is found there is no card, and a full-screen window
        // whose only way out is BACK is a trap.
        addView(
            actionButton("✕", background = 0xCC000000.toInt()) { onDismiss?.invoke() }.apply {
                contentDescription = context.getString(R.string.result_close)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                .apply {
                    topMargin = dp(40)
                    marginEnd = dp(10)
                },
        )

        addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
                .apply {
                    marginStart = dp(10)
                    marginEnd = dp(10)
                    bottomMargin = dp(28)
                },
        )
    }

    /** A new capture: the previous card and the previous highlight belong to a page that is gone. */
    fun show(pieces: List<KanjiPiece>, page: Bitmap?) {
        selectionToolbar.hide()
        pressed = null
        pageView.setPieces(pieces)
        pageView.setPage(page)
        card.visibility = GONE
    }

    fun showCard(data: KanjiCard) {
        card.removeAllViews()

        // R29: the mark covers the kanji the card talks about — 同時, not just the 同 — but only while
        // the tap that asked for this card is still the last one.
        val tap = pressed
        if (tap != null && pageView.characterAt(tap.first, tap.second)?.toString() == data.character) {
            pageView.mark(tap.first, data.span ?: (tap.second until tap.second + 1))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            selectableLabel(data.character, 30f, context.getColor(R.color.text_primary), bold = true),
        )
        // A reading beside the single character is that character's reading. The reading of a word is
        // written beside the word, on its own line: 同 next to どうじに read as if 同 were どうじに.
        if (data.word.isNullOrBlank() && !data.reading.isNullOrBlank()) {
            header.addView(
                selectableLabel(data.reading, 20f, context.getColor(R.color.accent), bold = true).apply {
                    val gap = dp(10)
                    setPadding(gap, 0, 0, 0)
                },
            )
        }
        header.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        header.addView(
            context.actionButton(context.getString(R.string.result_close), background = 0x00000000) {
                onDismiss?.invoke()
            },
        )
        card.addView(header)

        if (!data.word.isNullOrBlank()) {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(selectableLabel(data.word, 17f, context.getColor(R.color.accent), bold = true))
            if (!data.reading.isNullOrBlank()) {
                // Weight of 1 with no width: the reading takes what is left and is ellipsised if the
                // word is long, instead of being clipped off the edge of the card.
                line.addView(
                    selectableLabel(data.reading, 13f, context.getColor(R.color.text_secondary)).apply {
                        val gap = dp(8)
                        setPadding(gap, 0, 0, 0)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            card.addView(
                line,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }
        // R34a: the meaning of the compound the word line just named. Without it the card answered
        // «技 is skill; art; craft» for 剣技, which is the meaning of a third of the word.
        if (!data.word.isNullOrBlank() && data.wordMeaning.isNotBlank()) {
            card.addView(
                selectableLabel(data.wordMeaning, 12.5f, context.getColor(R.color.text_primary)).apply {
                    maxLines = 6
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }
        if (data.parts.isNotEmpty()) {
            card.addView(
                selectableLabel(
                    context.getString(
                        R.string.kanji_parts,
                        data.parts.joinToString(" ・ ") { "${it.kanji} ${it.reading}" },
                    ),
                    12.5f,
                    context.getColor(R.color.text_primary),
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }

        readings(data)?.let { value ->
            card.addView(
                value,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }
        if (!data.source.isNullOrBlank()) {
            card.addView(
                selectableLabel(
                    context.getString(R.string.dictionary_source, data.source),
                    11.5f,
                    context.getColor(R.color.text_secondary),
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }
        if (data.meaning.isNotBlank()) {
            card.addView(
                selectableLabel(data.meaning, 12.5f, context.getColor(R.color.text_primary)).apply {
                    maxLines = 8
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }
        if (!data.note.isNullOrBlank()) {
            card.addView(
                selectableLabel(data.note, 12f, context.getColor(R.color.warn)),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }

        card.visibility = VISIBLE
    }

    /** «音: フク　訓: はら», or null when the dictionaries gave neither. */
    private fun readings(data: KanjiCard): TextView? {
        val parts = ArrayList<String>(2)
        if (data.onYomi.isNotBlank()) parts += context.getString(R.string.kanji_on, data.onYomi)
        if (data.kunYomi.isNotBlank()) parts += context.getString(R.string.kanji_kun, data.kunYomi)
        if (parts.isEmpty()) return null
        return selectableLabel(parts.joinToString("　"), 13f, context.getColor(R.color.text_primary))
    }

    /** BACK leaves the overlay; a tap on the page does not, because missing a kanji is common. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onDismiss?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    internal val zoomScale: Float get() = pageView.zoomScale

    internal fun zoomBy(factor: Float, focusX: Float, focusY: Float) = pageView.zoomBy(factor, focusX, focusY)

    /** The cell currently highlighted, in page pixels; null before the first tap. */
    internal val highlighted: PixelBox? get() = pageView.highlight

    /** Where the card is, or null while it is hidden — the tests read it to know it appeared. */
    internal fun cardText(): String? =
        if (card.visibility == VISIBLE) {
            val parts = ArrayList<String>()
            fun walk(group: android.view.ViewGroup) {
                for (index in 0 until group.childCount) {
                    when (val child = group.getChildAt(index)) {
                        is TextView -> parts.add(child.text.toString())
                        is android.view.ViewGroup -> walk(child)
                    }
                }
            }
            walk(card)
            parts.joinToString("\n")
        } else {
            null
        }
}

/**
 * The page, its strips and the highlighted character, under one zoom transform.
 *
 * Same coordinate system as the text picker: the capture has one pixel per display pixel and the
 * pieces were placed on the screen, so scaling the canvas moves the page, the outlines and the
 * highlight together and the tap is converted back to page coordinates before it means anything.
 */
private class KanjiCanvasView(context: Context) : View(context) {

    var onTapped: ((piece: Int, index: Int) -> Unit)? = null

    private var pieces: List<KanjiPiece> = emptyList()
    private var page: Bitmap? = null

    var highlight: PixelBox? = null
        private set

    private val zoom = PickerZoom()
    private val pagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpFloat(1f)
        color = context.getColor(R.color.accent)
        alpha = 0x66
    }
    private val markFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = (0x33 shl 24) or (context.getColor(R.color.accent) and 0x00FFFFFF)
    }
    private val markBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpFloat(2f)
        color = context.getColor(R.color.accent)
    }

    private val slop = dpFloat(6f)

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var pinched = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom.zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                invalidate()
                return true
            }
        },
    )

    fun setPieces(pieces: List<KanjiPiece>) {
        this.pieces = pieces
        highlight = null
        zoom.reset(width.toFloat(), height.toFloat())
        invalidate()
    }

    fun setPage(bitmap: Bitmap?) {
        page = bitmap
        invalidate()
    }

    /** View pixels per page pixel: 1 when the window is exactly the capture. */
    private val pageScaleX: Float
        get() = page?.let { if (it.width > 0 && width > 0) width.toFloat() / it.width else 1f } ?: 1f

    private val pageScaleY: Float
        get() = page?.let { if (it.height > 0 && height > 0) height.toFloat() / it.height else 1f } ?: 1f

    /** Puts the accent border on the character that was just tapped. */
    fun mark(piece: Int, index: Int) = mark(piece, index until index + 1)

    /** Puts the accent border over a stretch of characters — the kanji of the word the card shows. */
    fun mark(piece: Int, range: IntRange) {
        val target = pieces.getOrNull(piece) ?: return
        if (range.isEmpty() || target.text.isEmpty()) return
        highlight = KanjiGrid.boxOf(target.text, boxOf(target), range.first, range.last + 1)
        invalidate()
    }

    /** The character printed at [index] of [piece]; null when there is no such cell. */
    fun characterAt(piece: Int, index: Int): Char? = pieces.getOrNull(piece)?.text?.getOrNull(index)

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

        canvas.save()
        canvas.translate(zoom.offsetX, zoom.offsetY)
        canvas.scale(zoom.scale, zoom.scale)
        // The page is stretched into the window, so the strips and the mark take the same stretch
        // (R23): the window is not always exactly the capture.
        canvas.scale(pageScaleX, pageScaleY)

        page?.let { bitmap ->
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                pagePaint,
            )
        }

        for (piece in pieces) {
            val box = RectF(piece.rect)
            canvas.drawRoundRect(box, dpFloat(3f), dpFloat(3f), outline)
        }
        highlight?.let { cell ->
            val box = RectF(cell.x.toFloat(), cell.y.toFloat(), cell.right.toFloat(), cell.bottom.toFloat())
            canvas.drawRoundRect(box, dpFloat(3f), dpFloat(3f), markFill)
            canvas.drawRoundRect(box, dpFloat(3f), dpFloat(3f), markBorder)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                pinched = false
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                pinched = true
                dragging = false
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    lastX = event.x
                    lastY = event.y
                    return true
                }
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                if (!dragging && zoom.scale > PickerZoom.MIN && abs(dx) + abs(dy) > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    zoom.panBy(dx, dy)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tapped = !dragging && !pinched
                dragging = false
                pinched = false
                if (tapped) {
                    tap(zoom.toPageX(event.x) / pageScaleX, zoom.toPageY(event.y) / pageScaleY)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                pinched = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Page coordinates: the smallest strip under the finger wins, so a column inside a balloon is reachable. */
    private fun tap(x: Float, y: Float) {
        var best = -1
        var bestArea = Int.MAX_VALUE
        for ((index, piece) in pieces.withIndex()) {
            val rect = piece.rect
            if (x < rect.left - slop || x > rect.right + slop) continue
            if (y < rect.top - slop || y > rect.bottom + slop) continue
            val area = rect.width() * rect.height()
            if (area < bestArea) {
                bestArea = area
                best = index
            }
        }
        val piece = pieces.getOrNull(best) ?: return
        val cell = KanjiGrid.cell(piece.text, boxOf(piece), x.toInt(), y.toInt()) ?: return
        onTapped?.invoke(best, cell.index)
    }

    private fun boxOf(piece: KanjiPiece) = PixelBox(piece.rect.left, piece.rect.top, piece.rect.width(), piece.rect.height())

    private fun dpFloat(value: Number): Float = resources.displayMetrics.density * value.toFloat()
}
