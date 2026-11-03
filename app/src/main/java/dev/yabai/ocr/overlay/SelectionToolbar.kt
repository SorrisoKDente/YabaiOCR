package dev.yabai.ocr.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import kotlin.math.abs

/**
 * The text menu of the panel: Copiar, Recortar, Colar and Selecionar tudo (R33b).
 *
 * Android's own floating toolbar belongs to an activity's window. The panel is a
 * `TYPE_APPLICATION_OVERLAY` window owned by the service, and in that window the toolbar usually
 * never appears — on many phones the text is not even given the selection handles. The actions
 * themselves are simple, so the panel draws them in its own window, where the window manager cannot
 * refuse them, and the four labels are the ones the system toolbar uses in Portuguese.
 *
 * It goes above the selected word, or below it when there is no room, and a tap anywhere else takes
 * it down before the panel does anything with that tap.
 */
internal class SelectionToolbar(
    private val panel: FrameLayout,
    private val context: Context,
) : LinearLayout(context) {

    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var target: TextView? = null
    private var scrim: View? = null

    /**
     * The two round grips at the ends of the selection (R36).
     *
     * Android draws and moves its own handles inside an activity's window; in the overlay the
     * selection came out stuck to the word the long press found — there was no way to take the rest
     * of the phrase. These are plain views in the panel, placed on the ends of the selection and
     * dragged the way the system's are: the bar steps aside while a grip moves, so it cannot cover
     * the text being selected, and comes back on release.
     */
    private var startHandle: View? = null
    private var endHandle: View? = null

    /**
     * The text and the range the long press chose, remembered while the menu is up (R35a).
     *
     * The live selection cannot be trusted between the two taps: on the phone the IME rewrites it
     * when the field is focused or the selection is touched, and then «Copiar» would silently have
     * nothing to copy — which is exactly what «the menu does nothing» looked like. The remembered
     * range is used only while the text is still the one the user selected in.
     */
    private var rememberedText: String = ""
    private var rememberedStart = 0
    private var rememberedEnd = 0

    private val copy = item("Copiar", COPY_TAG) { copySelection() }
    private val cut = item("Recortar", CUT_TAG) { cutSelection() }
    private val paste = item("Colar", PASTE_TAG) { pasteSelection() }
    private val selectAll = item("Selecionar tudo", SELECT_ALL_TAG) { selectEverything() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundedBackground(0xFF1B1D24.toInt(), context.dp(12).toFloat(), 0x33FFFFFF)
        // Above the result card, which has 12 dp (R35a). Android orders drawing **and touch dispatch**
        // by Z: at 10 dp this bar sat under the card, so every tap on Copiar/Colar landed on the card
        // and the menu looked broken.
        elevation = context.dp(20).toFloat()
        setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))
        tag = TAG
        // A tap on the bar's own padding belongs to the bar, not to whatever is underneath.
        isClickable = true
        addView(copy)
        addView(cut)
        addView(paste)
        addView(selectAll)
    }

    /** Whether the menu is on screen. */
    internal val shown: Boolean get() = parent != null

    /**
     * Selects the word under ([x], [y]) and puts the menu over it.
     *
     * Returns false when the view has never been laid out, which is the one case where there is no
     * word to find: the caller's long click is then left to the view.
     */
    internal fun showFor(view: TextView, x: Float, y: Float): Boolean {
        val layout = view.layout ?: return false
        hide()

        val text = view.text
        if (text.isNotEmpty()) {
            val line = layout.getLineForVertical(
                (y - view.totalPaddingTop + view.scrollY).toInt()
                    .coerceIn(0, (layout.height - 1).coerceAtLeast(0)),
            )
            val offset = layout.getOffsetForHorizontal(
                line,
                (x - view.totalPaddingLeft + view.scrollX)
                    .coerceIn(0f, layout.getLineWidth(line)),
            ).coerceIn(0, text.length)
            val range = wordRange(text, offset)
            select(view, range.first, range.last + 1)
        } else {
            select(view, 0, 0)
        }
        // Before the focus request: focusing the field is one of the things that can rewrite the
        // selection on the phone, and the range has to be captured while it is still the user's.
        rememberSelection(view)
        // A selectable label has to hold the focus for the highlight to be drawn.
        view.requestFocus()

        target = view
        addScrim()
        panel.addView(
            this,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ),
        )

        val editable = view.text is Editable && view is EditText
        cut.visibility = if (editable) VISIBLE else GONE
        paste.visibility = if (editable && clipboardText() != null) VISIBLE else GONE
        val hasSelection = view.selectionEnd > view.selectionStart
        copy.alpha = if (hasSelection) 1f else DISABLED_ALPHA
        copy.isEnabled = hasSelection
        selectAll.isEnabled = view.text.isNotEmpty()
        selectAll.alpha = if (selectAll.isEnabled) 1f else DISABLED_ALPHA

        position(view)
        showHandles(view)
        requestFocus()
        return true
    }

    /** Takes the menu down without touching the selection. */
    internal fun hide() {
        removeHandles()
        target = null
        // A drag can be interrupted while the bar is stepped aside (alpha = 0); the next opening
        // must not inherit that, or the menu would come back invisible.
        alpha = 1f
        rememberedText = ""
        rememberedStart = 0
        rememberedEnd = 0
        if (parent != null) panel.removeView(this)
        scrim?.let { panel.removeView(it) }
        scrim = null
    }

    private fun copySelection() {
        val view = target ?: return
        val range = actionRange(view, needSelection = true) ?: return
        val text = view.text.subSequence(range.first, range.last + 1).toString()
        clipboard?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        hide()
    }

    private fun cutSelection() {
        val view = target ?: return
        val editable = view.text as? Editable ?: return
        val range = actionRange(view, needSelection = true) ?: return
        val text = editable.subSequence(range.first, range.last + 1).toString()
        // A cut is a copy and a delete (R36). Without this the word vanished from the field and
        // never reached the clipboard, so the «Colar» that followed pasted something older.
        clipboard?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        editable.delete(range.first, range.last + 1)
        hide()
    }

    private fun pasteSelection() {
        val view = target ?: return
        val editable = view.text as? Editable ?: return
        val text = clipboardText() ?: return
        // A lost selection falls back to what the long press chose; with neither, the text is
        // appended, which is still what «Colar» means in an empty field.
        val range = actionRange(view, needSelection = true)
            ?: liveRange(view)
            ?: (editable.length until editable.length)
        val start = range.first.coerceIn(0, editable.length)
        val end = (range.last + 1).coerceIn(start, editable.length)
        editable.replace(start, end, text)
        select(view, start + text.length, start + text.length)
        hide()
    }

    private fun selectEverything() {
        val view = target ?: return
        select(view, 0, view.text.length)
        rememberSelection(view)
        position(view)
        showHandles(view)
    }

    /** Drops and rebuilds the two grips, so they follow a new selection (R36). */
    internal fun showHandles(view: TextView) {
        removeHandles()
        if (selectionBounds(view) == null) return
        val size = context.dp(HANDLE_SIZE)
        val params = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START)
        val start = buildHandle(isStart = true, view = view).also {
            startHandle = it
            panel.addView(it, params)
        }
        val end = buildHandle(isStart = false, view = view).also {
            endHandle = it
            panel.addView(it, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START))
        }
        placeHandles(view)
        // The grips are added last so they stay above the scrim and above the bar.
        start.bringToFront()
        end.bringToFront()
    }

    internal fun removeHandles() {
        startHandle?.let { panel.removeView(it) }
        endHandle?.let { panel.removeView(it) }
        startHandle = null
        endHandle = null
    }

    /**
     * One grip: a small circle with a larger transparent touch area.
     *
     * A real circle is 16 dp, too small to catch a finger; the 28 dp view around it is what the
     * touch listener receives. [start] decides which end of the selection it moves.
     */
    private fun buildHandle(isStart: Boolean, view: TextView): View =
        FrameLayout(context).apply {
            tag = if (isStart) START_HANDLE_TAG else END_HANDLE_TAG
            isClickable = true
            elevation = context.dp(22).toFloat()
            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(context.getColor(R.color.accent))
                        setStroke(context.dp(1), 0xFFFFFFFF.toInt())
                    }
                },
                FrameLayout.LayoutParams(
                    context.dp(HANDLE_DOT),
                    context.dp(HANDLE_DOT),
                    Gravity.CENTER,
                ),
            )
            var anchorOffset = 0
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.requestFocus()
                        val bounds = selectionBounds(view)
                        anchorOffset = if (isStart) {
                            bounds?.last?.plus(1) ?: view.selectionEnd
                        } else {
                            bounds?.first ?: view.selectionStart
                        }
                        this@SelectionToolbar.alpha = 0f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        view.requestFocus()
                        val panelX = x + event.x
                        val panelY = y + event.y
                        val currentOffset = offsetAt(view, panelX, panelY)
                        val min = minOf(anchorOffset, currentOffset)
                        val max = maxOf(anchorOffset, currentOffset)
                        select(view, min, max)
                        rememberSelection(view)
                        placeHandles(view)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        this@SelectionToolbar.alpha = 1f
                        position(view)
                        placeHandles(view)
                        true
                    }
                    else -> false
                }
            }
        }



    private fun placeHandles(view: TextView) {
        val start = startHandle ?: return
        val end = endHandle ?: return
        val bounds = selectionBounds(view) ?: return
        val layout = view.layout ?: return
        val origin = Rect()
        panel.offsetDescendantRectToMyCoords(view, origin)
        val firstLine = layout.getLineForOffset(bounds.first)
        val lastLine = layout.getLineForOffset(bounds.last + 1)
        val left = origin.left + view.totalPaddingLeft
        val top = origin.top + view.totalPaddingTop
        place(
            start,
            left + layout.getPrimaryHorizontal(bounds.first),
            (top + layout.getLineTop(firstLine)).toFloat(),
        )
        place(
            end,
            left + layout.getPrimaryHorizontal(bounds.last + 1),
            (top + layout.getLineBottom(lastLine)).toFloat(),
        )
    }

    /** Puts a grip's centre at ([x], [y]), kept inside the panel. */
    private fun place(handle: View, x: Float, y: Float) {
        val size = context.dp(HANDLE_SIZE)
        val half = size / 2f
        handle.x = (x - half).coerceIn(0f, (panel.width - size).coerceAtLeast(0).toFloat())
        handle.y = (y - half).coerceIn(0f, (panel.height - size).coerceAtLeast(0).toFloat())
    }

    /**
     * The text under a point given in the panel's coordinates.
     *
     * The grip is a direct child of the panel, so its own position plus the touch inside it is a
     * panel point; from there the text view's origin and padding lead to the layout's coordinates,
     * which is what [android.text.Layout.getOffsetForPosition] expects.
     */
    private fun offsetAt(view: TextView, xInPanel: Float, yInPanel: Float): Int {
        val layout = view.layout ?: return 0
        val origin = Rect()
        panel.offsetDescendantRectToMyCoords(view, origin)
        val x = xInPanel - origin.left - view.totalPaddingLeft + view.scrollX
        val y = yInPanel - origin.top - view.totalPaddingTop + view.scrollY
        // `Layout.getOffsetForPosition` is hidden in the SDK, so the line is chosen first; that is
        // also what the long press does, so a grip and a finger land on the same character.
        val line = layout.getLineForVertical(
            y.toInt().coerceIn(0, (layout.height - 1).coerceAtLeast(0)),
        )
        return layout.getOffsetForHorizontal(line, x).coerceIn(0, view.text.length)
    }

    /** The selection the grips belong to: the live one, or the remembered one when it was lost. */
    private fun selectionBounds(view: TextView): IntRange? {
        liveRange(view)?.let { if (!it.isEmpty()) return it }
        if (view.text.toString() != rememberedText) return null
        val start = rememberedStart.coerceIn(0, view.text.length)
        val end = rememberedEnd.coerceIn(start, view.text.length)
        return if (start == end) null else start until end
    }

    internal fun select(view: TextView, start: Int, end: Int) {
        val safeStart = start.coerceIn(0, view.text.length)
        val safeEnd = end.coerceIn(safeStart, view.text.length)
        if (view is EditText) {
            view.setSelection(safeStart, safeEnd)
        } else {
            val text = view.text
            if (text is Spannable) {
                Selection.setSelection(text, safeStart, safeEnd)
            }
        }
    }

    /**
     * Where an action applies: the live selection when it survived, otherwise the remembered one.
     *
     * The remembered range is only used while the text is still the text it was taken from —
     * overwriting a correction the user made in the meantime would be worse than doing nothing.
     */
    private fun actionRange(view: TextView, needSelection: Boolean): IntRange? {
        val text = view.text
        val live = liveRange(view)
        if (live != null && (!needSelection || !live.isEmpty())) return live
        if (text.toString() != rememberedText) return null
        val start = rememberedStart.coerceIn(0, text.length)
        val end = rememberedEnd.coerceIn(start, text.length)
        if (start == end) return null
        return start until end
    }

    /** The view's selection right now, or null when it no longer names a place in the text. */
    private fun liveRange(view: TextView): IntRange? {
        val length = view.text.length
        val start = view.selectionStart
        val end = view.selectionEnd
        if (start !in 0..length || end !in 0..length) return null
        return minOf(start, end) until maxOf(start, end)
    }

    private fun rememberSelection(view: TextView) {
        val text = view.text
        rememberedText = text.toString()
        rememberedStart = view.selectionStart.coerceIn(0, text.length)
        rememberedEnd = view.selectionEnd.coerceIn(rememberedStart, text.length)
    }

    private fun clipboardText(): String? {
        val clip = clipboard?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
    }

    /** A transparent layer that turns the next tap anywhere into «close the menu». */
    private fun addScrim() {
        val layer = View(context).apply {
            isClickable = true
            tag = SCRIM_TAG
            setBackgroundColor(Color.TRANSPARENT)
            // Above the card, below the bar (20) and the grips (22). The card has 12 dp, and Android
            // orders touch dispatch by Z: at 0 dp the scrim was under the card, so a tap on the text
            // went to the text and the menu stayed open (R37). The tap that closes a selection is the
            // same one the system uses — anywhere but the selection itself.
            elevation = context.dp(19).toFloat()
            setOnClickListener { dismissSelection() }
        }
        scrim = layer
        // Added before the bar, so the bar stays above it.
        panel.addView(
            layer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /**
     * A tap outside the bar: the menu and the highlight go away, as they do in any field (R37).
     *
     * [hide] alone would leave the word marked — the next tap would still be «on» it, and the panel
     * would look stuck. In an editable the caret collapses to the end of the range; in a selectable
     * label the selection is removed, which is what takes the highlight away.
     */
    private fun dismissSelection() {
        target?.let { view ->
            val text = view.text
            if (view is EditText) {
                view.setSelection(view.selectionEnd.coerceIn(0, text.length))
            } else if (text is Spannable) {
                Selection.removeSelection(text)
            }
        }
        hide()
    }

    /** Centres the bar over the selected line, above it when the line is low enough. */
    internal fun position(view: TextView) {
        val anchor = anchorOf(view)
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val margin = context.dp(8)
        val gap = context.dp(8)
        val maxX = (panel.width - measuredWidth - margin).coerceAtLeast(margin)
        x = (anchor.centerX() - measuredWidth / 2).coerceIn(margin, maxX).toFloat()

        val above = anchor.top - measuredHeight - gap
        val below = anchor.bottom + gap
        val maxY = (panel.height - measuredHeight - margin).coerceAtLeast(margin)
        y = (if (above >= margin) above else below).coerceIn(margin, maxY).toFloat()
    }

    /** The selected line in the panel's coordinates. */
    private fun anchorOf(view: TextView): Rect {
        val layout = view.layout ?: return Rect()
        val start = view.selectionStart.coerceAtLeast(0)
        val end = view.selectionEnd.coerceAtLeast(start)
        val firstLine = layout.getLineForOffset(start)
        val lastLine = layout.getLineForOffset(end.coerceAtMost(view.text.length))
        val left = layout.getPrimaryHorizontal(start).toInt()
        val right = layout.getPrimaryHorizontal(end.coerceAtMost(view.text.length)).toInt()
        val rect = Rect(
            left + view.totalPaddingLeft,
            layout.getLineTop(firstLine) + view.totalPaddingTop,
            right.coerceAtLeast(left + 1) + view.totalPaddingLeft,
            layout.getLineBottom(lastLine) + view.totalPaddingTop,
        )
        panel.offsetDescendantRectToMyCoords(view, rect)
        return rect
    }

    private fun item(label: String, tag: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        val h = context.dp(10)
        val v = context.dp(8)
        setPadding(h, v, h, v)
        this.tag = tag
        isClickable = true
        setOnClickListener { action() }
    }

    companion object {
        const val TAG = "yabai.selectionToolbar"
        const val COPY_TAG = "yabai.selection.copy"
        const val CUT_TAG = "yabai.selection.cut"
        const val PASTE_TAG = "yabai.selection.paste"
        const val SELECT_ALL_TAG = "yabai.selection.selectAll"
        const val START_HANDLE_TAG = "yabai.selection.handleStart"
        const val END_HANDLE_TAG = "yabai.selection.handleEnd"
        const val SCRIM_TAG = "yabai.selection.scrim"

        /** The grip's touch area, and the circle drawn inside it, in dp (R36). */
        private const val HANDLE_SIZE = 32
        private const val HANDLE_DOT = 16

        /** What the buttons of a bar with nothing to do look like. */
        private const val DISABLED_ALPHA = 0.4f

        private const val CLIP_LABEL = "yabai_ocr"

        /**
         * The word around [offset], as an inclusive range.
         *
         * Japanese has no spaces, so «word» here is the run of letters and digits around the finger;
         * in a Portuguese definition that is the word itself, and in a Japanese one it is the run of
         * kanji or kana. Punctuation and whitespace end the run. An offset on a separator selects
         * nothing, and the menu still offers «Selecionar tudo» and «Colar».
         */
        internal fun wordRange(text: CharSequence, offset: Int): IntRange {
            if (text.isEmpty()) return 0..-1
            val position = offset.coerceIn(0, text.length - 1)
            if (!isWordChar(text[position])) return position..position
            var start = position
            var end = position
            while (start > 0 && isWordChar(text[start - 1])) start--
            while (end + 1 < text.length && isWordChar(text[end + 1])) end++
            return start..end
        }

        private fun isWordChar(char: Char): Boolean =
            Character.isLetterOrDigit(char) || char == '\'' || char == '’' || char == '-' ||
                char == 'ー' || char == '々'
    }
}

/**
 * Gives [view] the panel's text menu: a long press selects the word under the finger and opens the
 * bar, instead of asking Android for a toolbar the overlay window cannot show (R33b).
 */
internal fun installSelection(view: TextView, toolbar: SelectionToolbar) {
    if (view !is EditText) {
        view.setTextIsSelectable(true)
    } else {
        view.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (view.selectionStart == view.selectionEnd) {
                    toolbar.removeHandles()
                }
            }
        })
    }

    var downX = 0f
    var downY = 0f
    var startOffset = -1
    var isDragging = false
    val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    view.setOnTouchListener { _, event ->
        val layout = view.layout
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isDragging = false
                startOffset = -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (layout != null && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                    if (!isDragging) {
                        isDragging = true
                        val line = layout.getLineForVertical(
                            (downY - view.totalPaddingTop + view.scrollY).toInt()
                                .coerceIn(0, (layout.height - 1).coerceAtLeast(0))
                        )
                        startOffset = layout.getOffsetForHorizontal(
                            line,
                            (downX - view.totalPaddingLeft + view.scrollX)
                                .coerceIn(0f, layout.getLineWidth(line))
                        ).coerceIn(0, view.text.length)
                    }
                    if (isDragging) {
                        val line = layout.getLineForVertical(
                            (event.y - view.totalPaddingTop + view.scrollY).toInt()
                                .coerceIn(0, (layout.height - 1).coerceAtLeast(0))
                        )
                        val currentOffset = layout.getOffsetForHorizontal(
                            line,
                            (event.x - view.totalPaddingLeft + view.scrollX)
                                .coerceIn(0f, layout.getLineWidth(line))
                        ).coerceIn(0, view.text.length)
                        val min = minOf(startOffset, currentOffset)
                        val max = maxOf(startOffset, currentOffset)
                        if (min != max) {
                            toolbar.select(view, min, max)
                            if (!toolbar.shown) {
                                toolbar.showFor(view, event.x, event.y)
                            } else {
                                toolbar.position(view)
                                toolbar.showHandles(view)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging && view.selectionEnd > view.selectionStart) {
                    if (!toolbar.shown) {
                        toolbar.showFor(view, event.x, event.y)
                    }
                }
            }
        }
        false
    }
    view.setOnLongClickListener {
        toolbar.showFor(view, downX, downY)
    }
}
