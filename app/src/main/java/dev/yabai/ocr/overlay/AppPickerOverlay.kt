package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.ai.AskAi
import dev.yabai.ocr.util.dp

/**
 * The apps a question can go to, in a window of their own.
 *
 * It is a separate window, not a row inside the result panel, because the list is as long as the phone
 * is full of apps that take text: a row of chips at the bottom of the panel ran past the edge of the
 * screen, and the apps below the fold could not be reached. A card of its own can scroll, can be
 * dismissed without touching the answer behind it, and has room for the icon — which is how a person
 * recognises ChatGPT or Gemini long before reading the name.
 *
 * Tapping outside the card, BACK and the ✕ all close only this window: the panel and the captured text
 * are still there underneath.
 */
class AppPickerOverlay(context: Context) : FrameLayout(context) {

    interface Callbacks {
        fun onAppPicked(packageName: String)

        /** The window was dismissed without a choice: outside tap, BACK or ✕. */
        fun onDismiss()
    }

    var callbacks: Callbacks? = null

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBackground(context.getColor(R.color.panel_bg), dp(18).toFloat(), 0x33FFFFFF)
        elevation = dp(16).toFloat()
        setPadding(dp(14), dp(14), dp(14), dp(12))
        // A tap on the card's own padding belongs to the card, not to the window behind it.
        isClickable = true
        tag = CARD_TAG
    }

    /** Everything the phone reported; the list the footer goes to when it is longer than [aiApps]. */
    private var allApps: List<AskAi.Target> = emptyList()

    /** The apps that look like an AI or a translator: what the picker opens on. */
    private var aiApps: List<AskAi.Target> = emptyList()

    init {
        // The window is the whole screen: everything outside the card dismisses it.
        isClickable = true
        setOnClickListener { callbacks?.onDismiss() }
        setBackgroundColor(SCRIM)
        addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                    bottomMargin = dp(24)
                },
        )
    }

    /**
     * Fills the window with one row per app: the icon, the name as the phone writes it, and the whole
     * row as the target. The list scrolls past [MAX_HEIGHT_FRACTION] of the screen instead of growing
     * off the bottom of it.
     *
     * [apps] is the short list the picker opens on — the apps that look like an AI or a translator —
     * and [all] is everything the phone reported. When the two differ, a footer takes the reader to
     * the full list and back: the phone's list of apps that take text is never taken away, because the
     * filter is a guess about names and the guess is not always right.
     */
    fun show(apps: List<AskAi.Target>, all: List<AskAi.Target> = apps) {
        aiApps = apps
        allApps = all
        render(apps)
    }

    private fun render(shown: List<AskAi.Target>) {
        card.removeAllViews()

        val title = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(4))
        }
        title.addView(
            context.textLabel(context.getString(R.string.result_ask_ai_choose), 15f, context.getColor(R.color.text_primary), bold = true),
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )
        title.addView(
            context.actionButton(
                "✕",
                textColor = context.getColor(R.color.text_primary),
                background = 0x00000000,
            ) { callbacks?.onDismiss() }.apply {
                contentDescription = context.getString(R.string.result_close)
            },
        )
        card.addView(title)

        // The rows are built per render, so asking for the list twice rebuilds it instead of trying to
        // add a view that still belongs to the previous scroll view.
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (app in shown) rows.addView(row(app))

        val list = CappedScrollView(context) { (resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).toInt() }
        list.isVerticalScrollBarEnabled = true
        list.addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        card.addView(list, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        if (allApps.size > aiApps.size) {
            val showingAll = shown.size > aiApps.size
            val label = if (showingAll) {
                context.getString(R.string.result_ask_ai_only_ai)
            } else {
                context.getString(R.string.result_ask_ai_all, allApps.size)
            }
            card.addView(
                context.actionButton(label, context.getColor(R.color.text_secondary), background = 0x00000000) {
                    render(if (showingAll) aiApps else allApps)
                },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) },
            )
        }
    }

    /**
     * One app: its icon on the left, its name filling the rest of the row.
     *
     * The fallback icon is not decoration — a package the phone cannot draw (an entry the package
     * manager reported but cannot load, or a launcher icon deleted with its app) would otherwise leave
     * a hole the width of the icon in the row.
     */
    private fun row(app: AskAi.Target): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(9), dp(8), dp(9))
            isClickable = true
            isFocusable = true
            tag = ROW_TAG
            val ripple = TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
                setBackgroundResource(ripple.resourceId)
            }
            setOnClickListener { callbacks?.onAppPicked(app.packageName) }
        }
        row.addView(
            ImageView(context).apply {
                tag = ICON_TAG
                setImageDrawable(iconOf(app.packageName))
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)),
        )
        row.addView(
            TextView(context).apply {
                text = app.label
                setTextColor(context.getColor(R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) },
        )
        return row
    }

    /** The app's own icon, or the system's generic one when the phone cannot draw it. */
    private fun iconOf(packageName: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: context.getDrawable(android.R.drawable.sym_def_app_icon)

    /** BACK closes this window only — the answer behind it is not thrown away. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            callbacks?.onDismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * A [ScrollView] that never grows past `cap`: a list of thirty apps would otherwise push the card's
     * title off the top of the screen while its bottom rows sat under the navigation bar.
     */
    private class CappedScrollView(context: Context, private val cap: () -> Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val limit = MeasureSpec.makeMeasureSpec(cap(), MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, limit)
        }
    }

    companion object {
        /** Tag on the card, so a layout test can measure what is on screen. */
        const val CARD_TAG = "yabai.appPickerCard"

        /** Tag on each app's row, in the order the apps are listed. */
        const val ROW_TAG = "yabai.appPickerRow"

        /** Tag on the icon inside a row. */
        const val ICON_TAG = "yabai.appPickerIcon"

        /** Side of the icon, in dp: the size a launcher uses, and big enough to recognise at a glance. */
        private const val ICON_DP = 36

        /** How much of the screen the scrolling list may take, title and margins aside. */
        private const val MAX_HEIGHT_FRACTION = 0.6f

        /** Behind the card, so the panel underneath reads as context and not as something to tap. */
        private const val SCRIM = 0x99000000.toInt()
    }
}
