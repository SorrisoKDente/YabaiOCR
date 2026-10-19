package dev.yabai.ocr.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Owns every floating window the app draws.
 *
 * Windows are added to `WindowManager` only while they are actually needed, because the result
 * panel takes input focus and a leftover invisible focusable window would swallow touches meant
 * for the app underneath.
 */
class OverlayController(private val context: Context) {

    interface Callbacks {
        fun onFullScreenRequested()
        fun onRegionRequested()
        fun onRegionConfirmed(rect: Rect?)
        fun onRegionCancelled()
        fun onStopRequested()
        fun onOpenSettingsRequested()
        fun onCopyRequested(text: String, closeAfter: Boolean)
        fun onAnalyseRequested(text: String)
        fun onRetryOfflineRequested()
        fun onResultDismissed()

        /** The user tapped one of the recognised text boxes after a whole-screen capture. */
        fun onBlockPicked(index: Int)

        /** The user asked for the whole capture instead of a single box. */
        fun onPickAllRequested()

        /** The user wants the boxes back, to pick a different piece of the same capture. */
        fun onRepickRequested()

        /** The user wants the captured image shown beside the text, or hidden again. */
        fun onImageToggleRequested()

        /** The user narrowed the dictionary lookup to one dictionary, or asked for all of them. */
        fun onDictionaryPicked(title: String?)

        /** The user wants an AI app to answer about the recognised text. */
        fun onAskAiRequested(text: String)
    }

    var callbacks: Callbacks? = null

    private val windowManager = context.getSystemService(WindowManager::class.java)
    internal val bubbleSize = dp(58)

    /**
     * The ✕ the bubble is dragged onto to close the service: its side, and how far it sits from the
     * bottom of the screen.
     */
    private val closeTargetSize = dp(64)
    private val closeTargetMargin = dp(40)

    /** Side of one icon button in the action strip, and the gap between two of them. */
    private val menuItemSize = dp(46)
    private val menuItemGap = dp(2)

    /** «Encerrar» keeps the red the text row it replaced used to have. */
    private val menuStopColor = 0xFFFF6B6B.toInt()

    /** Local shorthand so the layout code below stays readable. */
    private fun dp(value: Number): Int = context.dp(value)

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null
    private var closeTargetView: ImageView? = null
    private var closeTargetArmed = false
    private var confirmView: View? = null
    private var regionView: RegionOverlay? = null
    private var resultView: ResultOverlay? = null
    private var pickerView: TextPickerOverlay? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    // --- bubble --------------------------------------------------------------------------------

    fun showBubble() {
        if (bubbleView != null) {
            bubbleView?.visibility = View.VISIBLE
            return
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_scan)
            setPadding(dp(15), dp(15), dp(15), dp(15))
        }
        val bubble = FrameLayout(context).apply {
            background = context.getDrawable(R.drawable.bg_bubble)
            elevation = dp(10).toFloat()
            isClickable = true
            isFocusable = false
        }
        bubble.addView(icon, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
        bubble.setOnTouchListener { view, event -> onBubbleTouch(view, event) }

        val params = baseParams(bubbleSize, bubbleSize).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultBubbleX()
            y = defaultBubbleY()
        }

        bubbleView = bubble
        bubbleParams = params
        addWindow(bubble, params)
    }

    fun hideBubble() {
        val view = bubbleView ?: return
        removeWindow(view)
        bubbleView = null
        bubbleParams = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onBubbleTouch(view: View, event: MotionEvent): Boolean {
        val params = bubbleParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > dp(6) || abs(dy) > dp(6))) {
                    dragging = true
                    hideMenu()
                    // The ✕ only comes up once the finger is really moving: a tap still opens the menu.
                    showCloseTarget()
                }
                if (dragging) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    clampBubble(params)
                    updateWindow(view, params)
                    armCloseTarget(isOverCloseTarget(params.x, params.y))
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    endDrag(params.x, params.y)
                } else {
                    view.performClick()
                    toggleMenu()
                }
                dragging = false
                return true
            }

            // The gesture was taken away (a new window, a system panel): put everything back without
            // asking anything, because the user never let go.
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    hideCloseTarget()
                    putBubbleOnEdge()
                }
                dragging = false
                return true
            }
        }
        return false
    }

    private fun clampBubble(params: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        val margin = dp(4)
        params.x = params.x.coerceIn(-margin, metrics.widthPixels - bubbleSize + margin)
        params.y = params.y.coerceIn(margin, metrics.heightPixels - bubbleSize - margin)
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        val margin = dp(6)
        val left = params.x + bubbleSize / 2 < metrics.widthPixels / 2
        params.x = if (left) margin else metrics.widthPixels - bubbleSize - margin
        params.y = params.y.coerceIn(margin, metrics.heightPixels - bubbleSize - margin)
    }

    // --- action menu ---------------------------------------------------------------------------

    fun toggleMenu() = if (menuView != null) hideMenu() else showMenu()

    fun showMenu() {
        if (menuView != null || !canDrawOverlays()) return

        // No dimming scrim any more: the strip belongs to the bubble, and the user is usually looking
        // at the text underneath it. A tap anywhere else still closes the menu.
        val root = FrameLayout(context).apply {
            isClickable = true
            setOnClickListener { hideMenu() }
        }

        val strip = buildMenuStrip()
        val (x, y) = menuAnchor(
            bubbleX = bubbleParams?.x ?: defaultBubbleX(),
            bubbleY = bubbleParams?.y ?: defaultBubbleY(),
            strip = strip,
        )
        root.addView(
            strip,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply {
                leftMargin = x
                topMargin = y
            },
        )

        menuView = root
        addWindow(root, baseParams(MATCH_PARENT, MATCH_PARENT))
    }

    /**
     * The four actions as a row of icons. They live under the bubble instead of in a card at the
     * bottom of the screen: they are the bubble's menu, and icons keep them off the text that is
     * usually about to be captured.
     *
     * Each icon carries its wording in the content description, which is what an accessibility
     * service reads out — there is no label on screen to read.
     */
    internal fun buildMenuStrip(): LinearLayout {
        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(context.getColor(R.color.panel_bg), dp(16).toFloat(), 0x22FFFFFF)
            elevation = dp(8).toFloat()
            val pad = dp(4)
            setPadding(pad, pad, pad, pad)
            // A tap on the strip's own padding is a tap on the menu, not a tap outside it.
            isClickable = true
        }

        fun add(iconRes: Int, labelRes: Int, tint: Int = Color.WHITE, action: () -> Unit) {
            val icon = menuIcon(iconRes, labelRes, tint) {
                // Every action closes the menu, whether or not it opens something else.
                hideMenu()
                action()
            }
            icon.layoutParams = LinearLayout.LayoutParams(menuItemSize, menuItemSize).apply {
                if (strip.childCount > 0) marginStart = menuItemGap
            }
            strip.addView(icon)
        }

        add(R.drawable.ic_menu_whole_screen, R.string.overlay_full_screen) { callbacks?.onFullScreenRequested() }
        add(R.drawable.ic_menu_region, R.string.overlay_select_area) { callbacks?.onRegionRequested() }
        add(R.drawable.ic_menu_settings, R.string.overlay_settings) { callbacks?.onOpenSettingsRequested() }
        add(R.drawable.ic_menu_stop, R.string.overlay_close_service, menuStopColor) { callbacks?.onStopRequested() }
        return strip
    }

    /** One square icon button of the action strip. */
    private fun menuIcon(
        iconRes: Int,
        labelRes: Int,
        tint: Int = Color.WHITE,
        onClick: () -> Unit,
    ): ImageView = ImageView(context).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(tint)
        contentDescription = context.getString(labelRes)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = (menuItemSize - dp(24)) / 2
        setPadding(pad, pad, pad, pad)
        isClickable = true
        isFocusable = true
        val ripple = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            setBackgroundResource(ripple.resourceId)
        }
        setOnClickListener { onClick() }
    }

    /**
     * Where the action strip goes: centred under the bubble, pulled back on screen when the bubble
     * sits near an edge, and moved above the bubble when there is no room below.
     *
     * The strip is measured here rather than described by a constant, so a fifth icon — or a wider
     * one — cannot make the strip hang off the screen.
     */
    internal fun menuAnchor(bubbleX: Int, bubbleY: Int, strip: View): Pair<Int, Int> {
        strip.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val screen = context.resources.displayMetrics
        val margin = dp(4)
        val gap = dp(8)

        val maxX = (screen.widthPixels - strip.measuredWidth - margin).coerceAtLeast(margin)
        val x = (bubbleX + bubbleSize / 2 - strip.measuredWidth / 2).coerceIn(margin, maxX)

        val below = bubbleY + bubbleSize + gap
        val y = if (below + strip.measuredHeight <= screen.heightPixels - margin) {
            below
        } else {
            (bubbleY - gap - strip.measuredHeight).coerceAtLeast(margin)
        }
        return x to y
    }

    private fun defaultBubbleX(): Int = context.resources.displayMetrics.widthPixels - bubbleSize - dp(6)

    private fun defaultBubbleY(): Int = (context.resources.displayMetrics.heightPixels * 0.38f).roundToInt()

    fun hideMenu() {
        val view = menuView ?: return
        removeWindow(view)
        menuView = null
    }

    // --- drag to close -------------------------------------------------------------------------

    /**
     * Where the ✕ sits: centred, just above the bottom of the screen.
     *
     * In screen coordinates, the same space [bubbleParams] uses.
     */
    internal fun closeTargetRect(): Rect {
        val screen = context.resources.displayMetrics
        val left = (screen.widthPixels - closeTargetSize) / 2
        val top = screen.heightPixels - closeTargetSize - closeTargetMargin
        return Rect(left, top, left + closeTargetSize, top + closeTargetSize)
    }

    /**
     * Whether a bubble whose top-left corner is at ([bubbleX], [bubbleY]) counts as dropped on the ✕.
     *
     * The test is the circle the user sees: the bubble *centre* has to land inside the target, so the
     * highlight that comes up while dragging cannot lie about whether letting go will ask to close.
     * Judging by the centre also makes the drop forgiving — half the bubble can hang outside the ✕.
     */
    internal fun isOverCloseTarget(bubbleX: Int, bubbleY: Int): Boolean {
        val target = closeTargetRect()
        val dx = bubbleX + bubbleSize / 2f - target.exactCenterX()
        val dy = bubbleY + bubbleSize / 2f - target.exactCenterY()
        val radius = closeTargetSize / 2f
        return dx * dx + dy * dy <= radius * radius
    }

    internal fun isCloseTargetVisible(): Boolean = closeTargetView != null

    internal fun isCloseConfirmVisible(): Boolean = confirmView != null

    private fun showCloseTarget() {
        if (closeTargetView != null) return
        val target = buildCloseTarget()
        val rect = closeTargetRect()
        closeTargetView = target
        closeTargetArmed = false
        addWindow(
            target,
            baseParams(closeTargetSize, closeTargetSize).apply {
                gravity = Gravity.TOP or Gravity.START
                x = rect.left
                y = rect.top
                // Passive: the drag in progress belongs to the bubble, and the ✕ never takes a tap.
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            },
        )
    }

    private fun hideCloseTarget() {
        val target = closeTargetView ?: return
        removeWindow(target)
        closeTargetView = null
        closeTargetArmed = false
    }

    /** The ✕ as it first appears: a red cross on a dark disc, dark enough to read over any app. */
    internal fun buildCloseTarget(): ImageView = ImageView(context).apply {
        setImageResource(R.drawable.ic_menu_stop)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = (closeTargetSize - dp(28)) / 2
        setPadding(pad, pad, pad, pad)
        background = closeTargetBackground(armed = false)
        imageTintList = ColorStateList.valueOf(menuStopColor)
        contentDescription = context.getString(R.string.overlay_drop_to_close)
    }

    /**
     * Turns the ✕ solid red the moment the bubble is on it, so that letting go is a decision the user
     * sees coming rather than an accident they understand afterwards.
     */
    private fun armCloseTarget(armed: Boolean) {
        val target = closeTargetView ?: return
        if (armed == closeTargetArmed) return
        closeTargetArmed = armed
        target.background = closeTargetBackground(armed)
        target.imageTintList = ColorStateList.valueOf(if (armed) Color.WHITE else menuStopColor)
    }

    private fun closeTargetBackground(armed: Boolean): GradientDrawable = if (armed) {
        roundedBackground(0xFFE23B3B.toInt(), closeTargetSize / 2f)
    } else {
        roundedBackground(0xCC1B1D24.toInt(), closeTargetSize / 2f, 0x55FFFFFF)
    }

    /**
     * The end of a drag: the bubble was let go with its corner at ([bubbleX], [bubbleY]).
     *
     * A drop on the ✕ is only a *question*. Closing a floating reader is not something to do by
     * accident, so the service keeps running until the user answers. Anywhere else the bubble goes
     * back to the nearest edge, which is where it lives between uses.
     */
    internal fun endDrag(bubbleX: Int, bubbleY: Int) {
        val overTarget = isOverCloseTarget(bubbleX, bubbleY)
        hideCloseTarget()
        if (overTarget) {
            showCloseConfirm()
            return
        }
        val params = bubbleParams ?: return
        params.x = bubbleX
        params.y = bubbleY
        snapToEdge(params)
        bubbleView?.let { updateWindow(it, params) }
    }

    /**
     * «Encerrar o OCR flutuante?» — the last step, in a card that stands exactly where the ✕ was, so
     * the answer is under the finger that just let go.
     */
    private fun showCloseConfirm() {
        if (confirmView != null) return
        val root = FrameLayout(context).apply {
            // A tap outside is a «não»: the question goes away and the bubble goes back to its edge.
            isClickable = true
            setOnClickListener {
                hideCloseConfirm()
                putBubbleOnEdge()
            }
        }
        root.addView(
            buildCloseConfirm(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                bottomMargin = closeTargetMargin + closeTargetSize + dp(10)
            },
        )
        confirmView = root
        addWindow(root, baseParams(MATCH_PARENT, MATCH_PARENT))
    }

    fun hideCloseConfirm() {
        val view = confirmView ?: return
        removeWindow(view)
        confirmView = null
    }

    /** The question plus its two answers. «Encerrar» keeps the red of the menu's ✕ as text only. */
    internal fun buildCloseConfirm(): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(context.getColor(R.color.panel_bg), dp(16).toFloat(), 0x22FFFFFF)
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(8))
            isClickable = true
        }
        card.addView(
            context.textLabel(context.getString(R.string.overlay_close_confirm), 14f, Color.WHITE),
        )

        val answers = FlowLayout(context)
        answers.setPadding(0, dp(10), 0, 0)
        answers.addView(
            context.actionButton(context.getString(R.string.region_cancel)) {
                hideCloseConfirm()
                putBubbleOnEdge()
            },
            answerParams(start = 0),
        )
        answers.addView(
            // Red as text, not as a filled button: the point of asking is that closing should not be
            // the easy answer. A loud red button would sell exactly the opposite.
            context.actionButton(
                context.getString(R.string.overlay_close_service),
                textColor = context.getColor(R.color.warn),
            ) {
                hideCloseConfirm()
                callbacks?.onStopRequested()
            },
            answerParams(start = dp(6)),
        )
        card.addView(answers)
        return card
    }

    /** Room between the two answers, the same 6dp the result panel puts between its buttons. */
    private fun answerParams(start: Int): ViewGroup.MarginLayoutParams =
        ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = start }

    /** Puts the bubble back on the edge it belongs to: the answer to a drag that closed nothing. */
    private fun putBubbleOnEdge() {
        val params = bubbleParams ?: return
        snapToEdge(params)
        bubbleView?.let { updateWindow(it, params) }
    }

    // --- region selector -----------------------------------------------------------------------

    fun showRegionSelector() {
        if (regionView != null || !canDrawOverlays()) return
        val overlay = RegionOverlay(context).apply {
            onConfirm = { rect ->
                hideRegionSelector()
                callbacks?.onRegionConfirmed(rect)
            }
            onCancel = {
                hideRegionSelector()
                callbacks?.onRegionCancelled()
            }
        }
        regionView = overlay
        addWindow(overlay, baseParams(MATCH_PARENT, MATCH_PARENT))
    }

    fun hideRegionSelector() {
        val view = regionView ?: return
        removeWindow(view)
        regionView = null
    }

    // --- text picker ---------------------------------------------------------------------------

    /**
     * Draws the recognised paragraphs as boxes over the live screen.
     *
     * @return false when there is nothing to pick, so the caller can go straight to the result panel.
     */
    fun showTextPicker(rects: List<Rect>): Boolean {
        if (rects.isEmpty() || !canDrawOverlays()) return false
        hideTextPicker()

        val overlay = TextPickerOverlay(context).apply {
            onPick = { index -> callbacks?.onBlockPicked(index) }
            onPickAll = {
                hideTextPicker()
                callbacks?.onPickAllRequested()
            }
            onCancel = { hideTextPicker() }
        }
        // A full-screen window that swallows every touch, which is the point during selection.
        // Focusable so BACK cancels the selection instead of reaching the app underneath; while the
        // picker is up the app was not receiving touches anyway.
        if (!addWindow(overlay, baseParams(MATCH_PARENT, MATCH_PARENT, focusable = true))) return false
        overlay.show(rects)
        pickerView = overlay
        return true
    }

    fun hideTextPicker() {
        val view = pickerView ?: return
        removeWindow(view)
        pickerView = null
    }

    // --- result panel --------------------------------------------------------------------------

    private fun ensureResultPanel(): ResultOverlay? {
        if (!canDrawOverlays()) return null
        resultView?.let { return it }

        val panel = ResultOverlay(context).apply {
            callbacks = object : ResultOverlay.Callbacks {
                override fun onCopyOriginal(text: String) {
                    this@OverlayController.callbacks?.onCopyRequested(text, closeAfter = false)
                }

                override fun onCopyTranslation(text: String) {
                    this@OverlayController.callbacks?.onCopyRequested(text, closeAfter = false)
                }

                override fun onAnalyse(text: String) {
                    this@OverlayController.callbacks?.onAnalyseRequested(text)
                }

                override fun onRepick() {
                    hideResult()
                    this@OverlayController.callbacks?.onRepickRequested()
                }

                override fun onRetryOffline() {
                    this@OverlayController.callbacks?.onRetryOfflineRequested()
                }

                override fun onImageToggle() {
                    this@OverlayController.callbacks?.onImageToggleRequested()
                }

                override fun onDictionaryPicked(title: String?) {
                    this@OverlayController.callbacks?.onDictionaryPicked(title)
                }

                override fun onAskAi(text: String) {
                    this@OverlayController.callbacks?.onAskAiRequested(text)
                }

                override fun onDismiss() {
                    hideResult()
                    this@OverlayController.callbacks?.onResultDismissed()
                }
            }
        }
        resultView = panel
        // Focusable: the recognised text is editable and BACK has to reach the panel.
        //
        // The window covers the screen even though the card hangs from the top: the empty area is the
        // gesture. A tap there is the same as BACK — back to the boxes of a whole-screen capture, or
        // out of the panel when there is nothing behind it (see `ResultOverlay.stepBack`). It used to
        // be as tall as the card alone, which let touches through to the app underneath but left the
        // gesture with nowhere to happen.
        addWindow(
            panel,
            baseParams(MATCH_PARENT, MATCH_PARENT, focusable = true).apply {
                gravity = Gravity.TOP
                y = statusBarHeight()
                // The panel takes input focus so the text can be edited and BACK closes it, and a
                // focused EditText normally drags the keyboard up with it. It should only appear
                // when the user actually taps the field.
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            },
        )
        return panel
    }

    fun showResultLoading() {
        ensureResultPanel()?.showLoading()
    }

    /**
     * Rebuilds the panel for a finished capture; the analysis sections fill in afterwards.
     *
     * [image] is the frame the text was recognised from, when there is one, and [showImage] says
     * whether the user wants it on screen — the panel needs both to offer the toggle.
     */
    fun showResult(
        text: String,
        meta: String,
        notes: List<String>,
        offlineAvailable: Boolean,
        showLines: Boolean,
        lines: List<String>,
        canRepick: Boolean,
        image: Bitmap? = null,
        showImage: Boolean = false,
        canAskAi: Boolean = false,
    ) {
        ensureResultPanel()?.showResult(
            text,
            meta,
            notes,
            offlineAvailable,
            showLines,
            lines,
            canRepick,
            image,
            showImage,
            canAskAi,
        )
    }

    /** Shows or hides the captured image in the panel that is already open. */
    fun setResultImage(shown: Boolean) {
        resultView?.setImageShown(shown)
    }

    fun showResultTranslation(state: ResultOverlay.TranslationState) {
        resultView?.setTranslation(state)
    }

    fun showResultWords(state: ResultOverlay.WordsState) {
        resultView?.setWords(state)
    }

    fun showResultError(message: String, offlineAvailable: Boolean) {
        ensureResultPanel()?.showError(message, offlineAvailable)
    }

    /**
     * Writes a note in the panel that is already open — see [ResultOverlay.addNote]. The service uses
     * it for news that arrives after the capture, where a toast would be hidden behind another app.
     */
    fun showResultNote(message: String) {
        resultView?.addNote(message)
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }

    fun hideResult() {
        val view = resultView ?: return
        removeWindow(view)
        resultView = null
    }

    fun isResultVisible(): Boolean = resultView != null

    // --- capture helpers -----------------------------------------------------------------------

    /**
     * Removes or hides every window so none of them end up inside the screenshot we are about to
     * take. The bubble is only hidden, everything else is detached.
     */
    fun hideAllForCapture() {
        hideMenu()
        hideCloseTarget()
        hideCloseConfirm()
        hideRegionSelector()
        hideTextPicker()
        hideResult()
        bubbleView?.visibility = View.GONE
    }

    fun restoreAfterCapture() {
        bubbleView?.visibility = View.VISIBLE
    }

    fun destroy() {
        hideMenu()
        hideCloseTarget()
        hideCloseConfirm()
        hideRegionSelector()
        hideTextPicker()
        hideResult()
        hideBubble()
    }

    // --- window plumbing -----------------------------------------------------------------------

    private fun baseParams(
        width: Int,
        height: Int,
        focusable: Boolean = false,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        if (focusable) {
            // A focusable window is touch-modal unless told otherwise: touches outside it would go
            // to the window itself and never reach what is behind. Not every window here is
            // full-screen — the confirm card and the menu are anchored — so the flag stays.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun addWindow(view: View, params: WindowManager.LayoutParams): Boolean {
        return try {
            windowManager.addView(view, params)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "could not add overlay window", t)
            false
        }
    }

    private fun updateWindow(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (t: Throwable) {
            Log.w(TAG, "could not update overlay window", t)
        }
    }

    private fun removeWindow(view: View) {
        try {
            windowManager.removeViewImmediate(view)
        } catch (t: Throwable) {
            Log.w(TAG, "could not remove overlay window", t)
        }
    }

    companion object {
        private const val TAG = "OverlayController"
        private const val MATCH_PARENT = WindowManager.LayoutParams.MATCH_PARENT
        private const val WRAP_CONTENT = WindowManager.LayoutParams.WRAP_CONTENT
    }
}
