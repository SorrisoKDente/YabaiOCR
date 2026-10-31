package dev.yabai.ocr.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
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
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.R
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.util.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Owns every floating window the app draws.
 *
 * Windows are added to `WindowManager` only while they are actually needed, because the result
 * panel takes input focus and a leftover invisible focusable window would swallow touches meant
 * for the app underneath.
 */
class OverlayController(
    private val context: Context,
    /**
     * Whether a drag ends with the bubble stuck to the nearest side (R35c).
     *
     * A lambda rather than a value: the settings screen can change it while the service is running,
     * and the next drag is what has to obey the new choice.
     */
    private val pinnedToEdge: () -> Boolean = { AppSettings(context).bubbleOnEdge },
) {

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

        /** The user wants boxes back, to pick a different piece of the same capture. */
        fun onRepickRequested()

        /** The user wants to read the kanji under a finger, instead of a whole piece of text. */
        fun onKanjiRequested()

        /** The user wants the dictionary search box, instead of a capture (R31). */
        fun onDictionarySearchRequested() {}

        /** The query in the dictionary search box changed (R31). */
        fun onDictionarySearchChanged(text: String) {}

        /** The finger is on character [index] of the [piece] the kanji overlay is showing. */
        fun onKanjiTapped(piece: Int, index: Int)

        /** The user wants the captured image shown beside the text, or hidden again. */
        fun onImageToggleRequested()

        /** The user narrowed the dictionary lookup to one dictionary, or asked for all of them. */
        fun onDictionaryPicked(title: String?)

        /** The user wants an AI app to answer about the recognised text. */
        fun onAskAiRequested(text: String)

        /** The user picked [packageName] from the list of apps in the panel. */
        fun onAskAiToRequested(packageName: String, text: String)
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

    /**
     * How far from the ✕ the pull starts. Three times the target's own size: the pull has to be felt
     * while the finger is still travelling, not only once it is already on the cross — at twice the
     * size the reach was so short that most of a drag happened with no pull at all.
     */
    internal val closePullRadius = closeTargetSize * 3

    /**
     * How much smaller the bubble is drawn when it is right on the ✕.
     *
     * The pull alone moves it a few dp at the end of a drag, which reads as a nudge; shrinking it is
     * what makes the bubble look *sucked into* the cross — the thing the Messenger bubble does — and
     * it is visible from across the screen even while the finger still covers the last stretch.
     */
    private val pullShrink = 0.3f

    /** Side of one icon button in the action strip, and the gap between two of them. */
    private val menuItemSize = dp(46)
    private val menuItemGap = dp(2)

    /** «Encerrar» keeps the red the text row it replaced used to have. */
    private val menuStopColor = 0xFFFF6B6B.toInt()

    /** Local shorthand so the layout code below stays readable. */
    private fun dp(value: Number): Int = context.dp(value)

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    /**
     * Whether the bubble is paused (R33c): the capture session is still alive, but nothing of the
     * reader is on screen until the notification asks it back. A capture that was already running
     * when the pause arrived must not bring the bubble back — [restoreAfterCapture] checks this.
     */
    private var paused = false
    private var menuView: View? = null
    private var closeTargetView: ImageView? = null
    private var closeTargetArmed = false
    private var confirmView: View? = null
    private var regionView: RegionOverlay? = null
    private var resultView: ResultOverlay? = null

    /**
     * The panel's ear for HOME and the recents screen (R40).
     *
     * The window focus is not enough here: a `TYPE_APPLICATION_OVERLAY` window sits above the launcher
     * and the recents screen, so it can keep the focus — and [ResultOverlay.onWindowLost] never fires —
     * while the panel floats over a screen the user has already left. What the system does announce is
     * [Intent.ACTION_CLOSE_SYSTEM_DIALOGS], with the reason «homekey»/«recentapps» (the swipe-to-home
     * gesture reports «recentapps»). It is registered only while a panel is on screen.
     */
    private var systemDialogReceiver: BroadcastReceiver? = null

    /** The list of apps a question can go to, when it is on screen; see [showAskAiApps]. */
    private var appPicker: AppPickerOverlay? = null

    /** The recognised text the open [appPicker] is asking about. */
    private var askAiText: String = ""
    private var pickerView: TextPickerOverlay? = null

    /** The kanji reader, when it is on screen (R17). */
    private var kanjiView: KanjiOverlay? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    /**
     * Where the bubble last came to rest, in window coordinates (R35c).
     *
     * A drag that is cancelled, or a question that is answered «não», has to put the bubble back
     * where it was — in free mode that is the last drop, not an edge. `Int.MIN_VALUE` means «no rest
     * position yet», which only happens before the first [showBubble].
     */
    private var restingX = Int.MIN_VALUE
    private var restingY = Int.MIN_VALUE

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    // --- bubble --------------------------------------------------------------------------------

    fun showBubble() {
        if (bubbleView != null) {
            bubbleView?.visibility = if (paused) View.GONE else View.VISIBLE
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
        restingX = params.x
        restingY = params.y
        addWindow(bubble, params)
        if (paused) bubble.visibility = View.GONE
    }

    fun hideBubble() {
        val view = bubbleView ?: return
        removeWindow(view)
        bubbleView = null
        bubbleParams = null
        restingX = Int.MIN_VALUE
        restingY = Int.MIN_VALUE
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
                    // The bubble follows the finger until the ✕ is close, and then it is drawn
                    // pulled towards it, smaller, as it is sucked in: what is about to happen on
                    // release is visible before the finger lifts, which is the whole point of the
                    // target turning red.
                    val fingerX = startX + dx.toInt()
                    val fingerY = startY + dy.toInt()
                    val pulled = pullTowardsCloseTarget(fingerX, fingerY)
                    params.x = pulled.x
                    params.y = pulled.y
                    val scale = bubblePullScale(pullStrength(fingerX, fingerY))
                    view.scaleX = scale
                    view.scaleY = scale
                    clampBubble(params)
                    updateWindow(view, params)
                    armCloseTarget(isOverCloseTarget(params.x, params.y))
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.scaleX = 1f
                view.scaleY = 1f
                if (dragging) {
                    // The drop is judged where the bubble is *drawn* (pulled onto the ✕), but in
                    // free mode it lands where the finger let go: the pull is a preview of closing,
                    // not a place to leave the bubble (R35c).
                    endDrag(
                        params.x,
                        params.y,
                        startX + (event.rawX - downRawX).toInt(),
                        startY + (event.rawY - downRawY).toInt(),
                    )
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
                view.scaleX = 1f
                view.scaleY = 1f
                if (dragging) {
                    hideCloseTarget()
                    restBubble()
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

        fun addGlyph(glyph: String, labelRes: Int, action: () -> Unit) {
            val icon = menuGlyph(glyph, labelRes) {
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
        // A kanji is recognised by its own shape, so the icon is the character itself: no drawing
        // survives being read as «a kanji» the way 漢 does (R17).
        addGlyph("漢", R.string.overlay_kanji) { callbacks?.onKanjiRequested() }
        add(R.drawable.ic_menu_dictionary, R.string.overlay_dictionary) { callbacks?.onDictionarySearchRequested() }
        add(R.drawable.ic_menu_settings, R.string.overlay_settings) { callbacks?.onOpenSettingsRequested() }
        add(R.drawable.ic_menu_stop, R.string.overlay_close_service, menuStopColor) { callbacks?.onStopRequested() }
        return strip
    }

    /** One square button of the action strip whose icon is a glyph instead of a drawable. */
    private fun menuGlyph(
        glyph: String,
        labelRes: Int,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = glyph
        setTextColor(Color.WHITE)
        textSize = 22f
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        contentDescription = context.getString(labelRes)
        isClickable = true
        isFocusable = true
        val ripple = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            setBackgroundResource(ripple.resourceId)
        }
        setOnClickListener { onClick() }
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

    /**
     * Where the bubble is actually drawn while the finger is at ([bubbleX], [bubbleY]).
     *
     * Far from the ✕ the answer is the finger's own position, unchanged. Inside [closePullRadius] the
     * bubble is drawn *pulled* towards the ✕, by more and more as it gets closer — nothing at the edge
     * of the reach, all of it right on the cross. The pull is what makes «letting go here closes» a
     * thing the user can see coming, and it is also what the drop is judged on: the released position
     * is the drawn one, so what armed the ✕ is what happens on release.
     */
    internal fun pullTowardsCloseTarget(bubbleX: Int, bubbleY: Int): Point {
        val strength = pullStrength(bubbleX, bubbleY)
        if (strength <= 0f) return Point(bubbleX, bubbleY)
        val target = closeTargetRect()
        val centreX = bubbleX + bubbleSize / 2f
        val centreY = bubbleY + bubbleSize / 2f
        val dx = target.exactCenterX() - centreX
        val dy = target.exactCenterY() - centreY
        return Point(
            (centreX + dx * strength - bubbleSize / 2f).roundToInt(),
            (centreY + dy * strength - bubbleSize / 2f).roundToInt(),
        )
    }

    /**
     * How much of the way to the ✕ the bubble is drawn, from 0 (following the finger) to 1 (on it).
     *
     * Nothing outside [closePullRadius]; then a smoothstep, `3c² − 2c³`, where `c` runs 0→1 as the
     * finger approaches. It replaced a plain square, which is barely felt: at 70 % of the reach a
     * square had moved the bubble 9 % of the way, and the pull went unnoticed until the finger was
     * almost on the ✕ — «o puxão mal se sente», from the phone. A smoothstep moves it 22 % there and
     * half the way at the middle of the reach, so the drag starts answering before the end.
     *
     * A finger already on the ✕ is drawn *exactly* on it, not 97 % of the way: the drop is judged on
     * the drawn position, and being short of the centre would leave the cross unarmed after the one
     * gesture that was obviously meant to arm it.
     */
    internal fun pullStrength(bubbleX: Int, bubbleY: Int): Float {
        if (isOverCloseTarget(bubbleX, bubbleY)) return 1f
        val target = closeTargetRect()
        val dx = target.exactCenterX() - (bubbleX + bubbleSize / 2f)
        val dy = target.exactCenterY() - (bubbleY + bubbleSize / 2f)
        val distance = hypot(dx, dy)
        if (distance >= closePullRadius) return 0f
        val closeness = 1f - distance / closePullRadius
        return closeness * closeness * (3f - 2f * closeness)
    }

    /** The scale the bubble is drawn at while [strength] of the pull is applied; 1 means untouched. */
    internal fun bubblePullScale(strength: Float): Float = 1f - pullShrink * strength

    internal fun isCloseTargetVisible(): Boolean = closeTargetView != null

    internal fun isCloseConfirmVisible(): Boolean = confirmView != null

    /** The bubble's window, so a test can drive the drag gesture through the real touch listener. */
    internal fun bubbleWindow(): View? = bubbleView

    /** Where the bubble's window sits, in the same screen coordinates as [closeTargetRect]. */
    internal fun bubbleOrigin(): Point = bubbleParams?.let { Point(it.x, it.y) } ?: Point(0, 0)

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
    /**
     * The end of a drag: the bubble is drawn with its corner at ([bubbleX], [bubbleY]) and the
     * finger let go at ([landX], [landY]).
     *
     * A drop on the ✕ is only a *question*, and it is judged on the drawn position — what the user
     * sees coming is what happens on release. Closing a floating reader is not something to do by
     * accident, so the service keeps running until the user answers. Anywhere else the bubble settles
     * where the finger let go (free mode) or at the nearest edge (pinned mode) — see [settleBubble].
     */
    internal fun endDrag(
        bubbleX: Int,
        bubbleY: Int,
        landX: Int = bubbleX,
        landY: Int = bubbleY,
    ) {
        val overTarget = isOverCloseTarget(bubbleX, bubbleY)
        hideCloseTarget()
        if (overTarget) {
            showCloseConfirm()
            return
        }
        settleBubble(landX, landY)
    }

    /**
     * «Encerrar o OCR flutuante?» — the last step, in a card that stands exactly where the ✕ was, so
     * the answer is under the finger that just let go.
     */
    private fun showCloseConfirm() {
        if (confirmView != null) return
        val root = FrameLayout(context).apply {
            // A tap outside is a «não»: the question goes away and the bubble goes back to rest.
            isClickable = true
            setOnClickListener {
                hideCloseConfirm()
                restBubble()
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
                restBubble()
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

    /** Puts the bubble where it belongs after an unanswered question or a cancelled drag (R35c). */
    private fun restBubble() {
        val params = bubbleParams ?: return
        bubbleView?.let {
            it.scaleX = 1f
            it.scaleY = 1f
        }
        if (pinnedToEdge() || restingX == Int.MIN_VALUE) {
            snapToEdge(params)
            restingX = params.x
            restingY = params.y
            bubbleView?.let { updateWindow(it, params) }
            return
        }
        settleBubble(restingX, restingY)
    }

    /**
     * Leaves the bubble at ([x], [y]), obeying the placement the user chose (R35c).
     *
     * Free mode only keeps it inside the screen, so a drop in the middle of the page stays there.
     * Pinned mode snaps it to the nearest side, which is where it always used to live.
     */
    private fun settleBubble(x: Int, y: Int) {
        val params = bubbleParams ?: return
        params.x = x
        params.y = y
        if (pinnedToEdge()) snapToEdge(params) else clampBubble(params)
        restingX = params.x
        restingY = params.y
        bubbleView?.let { updateWindow(it, params) }
    }

    // --- region selector -----------------------------------------------------------------------

    fun showRegionSelector() {
        if (regionView != null || !canDrawOverlays() || paused) return
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
     * Draws the recognised paragraphs as boxes over the live screen, with their readings when [mode]
     * asks for them.
     *
     * @return false when there is nothing to pick, so the caller can go straight to the result panel.
     */
    /**
     * Draws the boxes over the frozen capture, so the user can pinch the page itself (R15).
     *
     * [page] is the screen as it was captured; without it (a caller that only has boxes) the picker
     * still works, just with nothing to zoom.
     */
    fun showTextPicker(blocks: List<PickerBlock>, mode: PickerReadingMode, page: Bitmap?): Boolean {
        if (blocks.isEmpty() || !canDrawOverlays() || paused) return false
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
        overlay.show(blocks, mode, page)
        pickerView = overlay
        return true
    }

    fun hideTextPicker() {
        val view = pickerView ?: return
        removeWindow(view)
        pickerView = null
    }

    // --- kanji reader --------------------------------------------------------------------------

    /**
     * The frozen capture with every recognised strip outlined, waiting for a finger (R17).
     *
     * The pieces are the same ones the text picker draws, so the page and the boxes share one
     * coordinate system and the tap is a position in the text, not in the window.
     */
    fun showKanji(pieces: List<KanjiPiece>, page: Bitmap?): Boolean {
        if (pieces.isEmpty() || !canDrawOverlays() || paused) return false
        hideKanji()

        val overlay = KanjiOverlay(context).apply {
            onKanjiTapped = { piece, index -> callbacks?.onKanjiTapped(piece, index) }
            onDismiss = { hideKanji() }
        }
        if (!addWindow(overlay, baseParams(MATCH_PARENT, MATCH_PARENT, focusable = true))) return false
        overlay.show(pieces, page)
        kanjiView = overlay
        return true
    }

    fun showKanjiCard(card: KanjiCard) {
        kanjiView?.showCard(card)
    }

    fun hideKanji() {
        val view = kanjiView ?: return
        removeWindow(view)
        kanjiView = null
    }

    // --- result panel --------------------------------------------------------------------------

    private fun ensureResultPanel(): ResultOverlay? {
        if (!canDrawOverlays()) return null
        // A capture that was already running when the pause arrived finishes into nothing: the
        // panel must not come back without the bubble (R33c).
        if (paused) return null
        resultView?.let { return it }

        val panel = ResultOverlay(context).apply {
            onWindowLost = {
                // The list of apps is a window of this app, drawn over the panel, and it takes the
                // focus on purpose: dismissing it has to come back to the panel, not to nothing.
                if (appPicker == null) dismissResult()
            }
            onSystemDialog = { reason -> dismissForSystemDialog(reason) }
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

                override fun onSearchChanged(text: String) {
                    this@OverlayController.callbacks?.onDictionarySearchChanged(text)
                }

                override fun onAskAi(text: String) {
                    this@OverlayController.callbacks?.onAskAiRequested(text)
                }

                override fun onDismiss() {
                    dismissResult()
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
        val added = addWindow(
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
        // Only a panel that reached the screen needs the listening: if the window could not be
        // added there is nothing for HOME to take down, and a receiver left behind would outlive
        // the panel it belongs to.
        if (added) listenForSystemDialogs()
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

    /**
     * Opens the panel as the dictionary search (R31).
     *
     * The same window as a capture's result, so the word cards and the chooser come along; what
     * changes is the card itself (a search field, no translation, no capture behind it). The keyboard
     * is asked for after the window is attached — the result window hides it by design, and a search
     * with no field focused would look like a panel that forgot what it was for.
     */
    fun showDictionarySearch(
        titles: List<String>,
        selected: String?,
        dictionaryCount: Int,
        stale: List<String> = emptyList(),
        initialQuery: String = "",
        canAskAi: Boolean = false,
    ) {
        if (!canDrawOverlays()) return
        val panel = ensureResultPanel() ?: return
        panel.showSearch(titles, selected, dictionaryCount, stale, initialQuery, canAskAi)
        panel.post { panel.showKeyboard() }
    }

    fun showResultTranslation(state: ResultOverlay.TranslationState) {
        resultView?.setTranslation(state)
    }

    /**
     * The window that lists the apps a question can go to — see [AppPickerOverlay].
     *
     * [text] travels with the choice: the picker does not own the recognised text, so it is captured
     * here and handed to the service together with the package the user taps.
     */
    fun showAskAiApps(apps: List<AskAi.Target>, text: String) {
        askAiText = text
        val picker = appPicker ?: AppPickerOverlay(context).apply {
            callbacks = object : AppPickerOverlay.Callbacks {
                override fun onAppPicked(packageName: String) {
                    // The list has done its job: it goes away before the app opens, so nothing of it
                    // is left over whatever the answer looks like.
                    hideAppPicker()
                    this@OverlayController.callbacks?.onAskAiToRequested(packageName, askAiText)
                }

                override fun onDismiss() {
                    hideAppPicker()
                }
            }
        }.also { panel ->
            appPicker = panel
            addWindow(panel, baseParams(MATCH_PARENT, MATCH_PARENT, focusable = true).apply { gravity = Gravity.TOP })
        }
        // Opens on the apps that could answer a question, with the rest of the phone a tap away: the
        // package manager reports everything that takes text, from Gmail to «Converter para JPEG».
        picker.show(AskAi.relevant(apps), apps)
    }

    /** Takes the list of apps down; the result panel behind it stays where it was. */
    fun hideAppPicker() {
        val view = appPicker ?: return
        removeWindow(view)
        appPicker = null
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

    /**
     * Takes the panel down and says so, in that order: the service stops the analysis and drops the
     * frame it was holding, which is only worth doing once the panel is really gone.
     *
     * Two ways in, one exit: the «Fechar» button and the ✕ of the header on one side, the window losing
     * focus on the other (R9).
     */
    private fun dismissResult() {
        hideResult()
        callbacks?.onResultDismissed()
    }

    /**
     * Starts listening for the system closing the screen the panel is over (R40).
     *
     * Registered with `RECEIVER_EXPORTED` because the sender is the system: the action is protected,
     * so no other app can forge it without a signature permission. One registration per panel; the
     * receiver goes in [hideResult], so nothing of the panel outlives it.
     */
    private fun listenForSystemDialogs() {
        if (systemDialogReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
                dismissForSystemDialog(intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY))
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_EXPORTED,
        )
        systemDialogReceiver = receiver
    }

    private fun stopListeningForSystemDialogs() {
        val receiver = systemDialogReceiver ?: return
        systemDialogReceiver = null
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * Takes the panel down when HOME or recents takes the screen: the panel belongs to the page that
     * was left, and the list of apps — a window of this app drawn over it — would otherwise be left
     * with nothing behind it.
     *
     * Both routes end here (the broadcast and the window's `onCloseSystemDialogs`), and the reason is
     * checked in one place: the power menu and the other system dialogs carry reasons of their own,
     * and closing on those would take the panel away while the user is still reading.
     */
    private fun dismissForSystemDialog(reason: String?) {
        if (reason != SYSTEM_DIALOG_REASON_HOME && reason != SYSTEM_DIALOG_REASON_RECENTS) return
        if (resultView == null) return
        dismissResult()
    }

    fun hideResult() {
        stopListeningForSystemDialogs()
        // The list of apps belongs to the question the panel is showing: it cannot outlive it.
        hideAppPicker()
        val view = resultView ?: return
        // Disarmed before the window goes: taking a window down also takes its focus, and the panel
        // must not read that as the user walking away and ask to be dismissed a second time.
        view.onWindowLost = null
        view.onSystemDialog = null
        resultView = null
        removeWindow(view)
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
        hideKanji()
        hideAppPicker()
        hideResult()
        bubbleView?.visibility = View.GONE
    }

    fun restoreAfterCapture() {
        bubbleView?.visibility = if (paused) View.GONE else View.VISIBLE
    }

    /**
     * Pauses or resumes the floating reader (R33c).
     *
     * Pausing takes down everything the reader owns — the bubble, the result panel, the pickers, the
     * dragging target — because a «paused» OCR that still covers the page would be worse than none.
     * The bubble keeps its view and its position, so resuming puts it back exactly where the user
     * left it instead of at the default corner. Stopping is the other action: this one keeps the
     * capture session and the foreground service alive.
     */
    fun setPaused(paused: Boolean) {
        this.paused = paused
        if (paused) {
            hideMenu()
            hideCloseTarget()
            hideCloseConfirm()
            hideRegionSelector()
            hideTextPicker()
            hideKanji()
            hideAppPicker()
            hideResult()
        }
        bubbleView?.visibility = if (paused) View.GONE else View.VISIBLE
    }

    /** Whether the reader is paused; a test seam, the bubble's visibility is the visible effect. */
    internal fun isPaused(): Boolean = paused

    fun destroy() {
        hideMenu()
        hideCloseTarget()
        hideCloseConfirm()
        hideRegionSelector()
        hideTextPicker()
        hideKanji()
        hideAppPicker()
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

        /** The extra [Intent.ACTION_CLOSE_SYSTEM_DIALOGS] carries the reason in (R40). */
        private const val SYSTEM_DIALOG_REASON_KEY = "reason"

        /** HOME was pressed: the navigation button, or whatever gesture the ROM maps to it. */
        private const val SYSTEM_DIALOG_REASON_HOME = "homekey"

        /** The recents screen was opened; on gesture navigation, also the swipe-to-home gesture. */
        private const val SYSTEM_DIALOG_REASON_RECENTS = "recentapps"
    }
}
