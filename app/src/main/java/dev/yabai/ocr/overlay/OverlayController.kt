package dev.yabai.ocr.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dev.yabai.ocr.R
import dev.yabai.ocr.ocr.OcrOutcome
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
        fun onRetryOfflineRequested()
        fun onResultDismissed()
    }

    var callbacks: Callbacks? = null

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val bubbleSize = dp(58)

    /** Local shorthand so the layout code below stays readable. */
    private fun dp(value: Number): Int = context.dp(value)

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null
    private var regionView: RegionOverlay? = null
    private var resultView: ResultOverlay? = null

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

        val metrics = context.resources.displayMetrics
        val params = baseParams(bubbleSize, bubbleSize).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - bubbleSize - dp(6)
            y = (metrics.heightPixels * 0.38f).roundToInt()
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
                }
                if (dragging) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    clampBubble(params)
                    updateWindow(view, params)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    snapToEdge(params)
                    updateWindow(view, params)
                } else {
                    view.performClick()
                    toggleMenu()
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

        val root = FrameLayout(context).apply {
            setBackgroundColor(0x73000000)
            isClickable = true
            setOnClickListener { hideMenu() }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(context.getColor(R.color.panel_bg), dp(18).toFloat(), 0x22FFFFFF)
            isClickable = true

            addView(
                context.menuRow(
                    context.getString(R.string.overlay_full_screen),
                    null,
                    Color.WHITE,
                ) {
                    hideMenu()
                    callbacks?.onFullScreenRequested()
                },
            )
            addView(context.hairLine())
            addView(
                context.menuRow(
                    context.getString(R.string.overlay_select_area),
                    null,
                    Color.WHITE,
                ) {
                    hideMenu()
                    callbacks?.onRegionRequested()
                },
            )
            addView(context.hairLine())
            addView(
                context.menuRow(
                    context.getString(R.string.overlay_settings),
                    null,
                    context.getColor(R.color.text_secondary),
                ) {
                    hideMenu()
                    callbacks?.onOpenSettingsRequested()
                },
            )
            addView(context.hairLine())
            addView(
                context.menuRow(
                    context.getString(R.string.overlay_close_service),
                    null,
                    0xFFFF6B6B.toInt(),
                ) {
                    hideMenu()
                    callbacks?.onStopRequested()
                },
            )
        }

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
                bottomMargin = dp(28)
            },
        )

        menuView = root
        addWindow(root, baseParams(MATCH_PARENT, MATCH_PARENT))
    }

    fun hideMenu() {
        val view = menuView ?: return
        removeWindow(view)
        menuView = null
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

    // --- result panel --------------------------------------------------------------------------

    private fun ensureResultPanel(): ResultOverlay? {
        if (!canDrawOverlays()) return null
        resultView?.let { return it }

        val panel = ResultOverlay(context).apply {
            callbacks = object : ResultOverlay.Callbacks {
                override fun onCopy(text: String, closeAfter: Boolean) {
                    this@OverlayController.callbacks?.onCopyRequested(text, closeAfter)
                }

                override fun onRetryOffline() {
                    this@OverlayController.callbacks?.onRetryOfflineRequested()
                }

                override fun onDismiss() {
                    hideResult()
                    this@OverlayController.callbacks?.onResultDismissed()
                }
            }
        }
        resultView = panel
        // Focusable: text selection and the BACK key need it.
        addWindow(panel, baseParams(MATCH_PARENT, MATCH_PARENT, focusable = true))
        return panel
    }

    fun showResultLoading() {
        ensureResultPanel()?.showLoading()
    }

    fun showResult(outcome: OcrOutcome, showLines: Boolean, offlineAvailable: Boolean) {
        ensureResultPanel()?.showOutcome(outcome, showLines, offlineAvailable)
    }

    fun showResultError(message: String, offlineAvailable: Boolean) {
        ensureResultPanel()?.showError(message, offlineAvailable)
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
        hideRegionSelector()
        hideResult()
        bubbleView?.visibility = View.GONE
    }

    fun restoreAfterCapture() {
        bubbleView?.visibility = View.VISIBLE
    }

    fun destroy() {
        hideMenu()
        hideRegionSelector()
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
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun addWindow(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.addView(view, params)
        } catch (t: Throwable) {
            Log.e(TAG, "could not add overlay window", t)
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
    }
}
