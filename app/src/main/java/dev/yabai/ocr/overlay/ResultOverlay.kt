package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import dev.yabai.ocr.R
import dev.yabai.ocr.ocr.OcrOutcome
import dev.yabai.ocr.util.dp

/**
 * The panel that shows the recognised text once a capture finishes.
 *
 * Its window is focusable on purpose: that is what allows long-press text selection and lets the
 * BACK key dismiss the panel instead of leaking to the app underneath.
 */
class ResultOverlay(context: Context) : FrameLayout(context) {

    interface Callbacks {
        fun onCopy(text: String, closeAfter: Boolean)
        fun onRetryOffline()
        fun onDismiss()
    }

    var callbacks: Callbacks? = null

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBackground(context.getColor(R.color.panel_bg), dp(18).toFloat(), 0x22FFFFFF)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private val maxCardHeight = (context.resources.displayMetrics.heightPixels * 0.68f).toInt()

    init {
        setBackgroundColor(context.getColor(R.color.scrim))
        isClickable = true
        setOnClickListener { callbacks?.onDismiss() }

        addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
                bottomMargin = dp(14)
            },
        )

        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                callbacks?.onDismiss()
                true
            } else {
                false
            }
        }
    }

    fun showLoading() {
        card.removeAllViews()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            addView(ProgressBar(context).apply { isIndeterminate = true })
            addView(
                textLabel(context.getString(R.string.recognizing), 16f, context.getColor(R.color.text_primary)).apply {
                    setPadding(dp(14), 0, 0, 0)
                },
            )
        }
        card.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun showOutcome(outcome: OcrOutcome, showLines: Boolean, offlineAvailable: Boolean) {
        card.removeAllViews()

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            textLabel(context.getString(R.string.result_title), 17f, context.getColor(R.color.text_primary), bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            textLabel(
                context.getString(
                    R.string.result_seconds,
                    outcome.engineLabel,
                    outcome.elapsedMs.toInt(),
                ),
                12f,
                context.getColor(R.color.text_secondary),
            ),
        )
        header.addView(
            actionButton("✕", background = 0x00000000) { callbacks?.onDismiss() }.apply {
                setPadding(dp(10), dp(4), dp(4), dp(4))
            },
        )
        card.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        if (outcome.fallback) {
            card.addView(note(context.getString(R.string.result_fallback_note)))
        }
        if (outcome.warning != null) {
            card.addView(note(outcome.warning))
        }

        val body = if (outcome.text.isBlank()) {
            textLabel(context.getString(R.string.result_empty), 15f, context.getColor(R.color.text_secondary))
        } else {
            val display = if (showLines && outcome.lines.isNotEmpty()) {
                outcome.lines.joinToString("\n")
            } else {
                outcome.text
            }
            textLabel(display, 17f, context.getColor(R.color.text_primary)).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(dp(4).toFloat(), 1f)
                setPadding(dp(2), dp(10), dp(2), dp(10))
            }
        }

        val scroller = MaxHeightScrollView(context, maxCardHeight).apply { addView(body) }
        card.addView(
            scroller,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        if (offlineAvailable) {
            buttons.addView(
                actionButton(context.getString(R.string.result_retry_offline)) { callbacks?.onRetryOffline() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }
        buttons.addView(
            actionButton(context.getString(R.string.result_close)) { callbacks?.onDismiss() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(6) },
        )
        if (outcome.text.isNotBlank()) {
            buttons.addView(
                actionButton(
                    context.getString(R.string.result_copy),
                    textColor = Color.WHITE,
                    background = context.getColor(R.color.accent),
                ) { callbacks?.onCopy(outcome.text, false) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(6) },
            )
        }
        card.addView(buttons, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun showError(message: String, offlineAvailable: Boolean) {
        card.removeAllViews()
        card.addView(
            textLabel(message, 16f, context.getColor(R.color.text_primary)).apply {
                setPadding(dp(2), dp(6), dp(2), dp(12))
            },
        )
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        if (offlineAvailable) {
            buttons.addView(actionButton(context.getString(R.string.result_retry_offline)) { callbacks?.onRetryOffline() })
        }
        buttons.addView(
            actionButton(context.getString(R.string.result_close)) { callbacks?.onDismiss() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(6) },
        )
        card.addView(buttons)
    }

    private fun note(message: String): TextView =
        textLabel(message, 12f, context.getColor(R.color.text_secondary)).apply {
            setPadding(dp(2), dp(6), dp(2), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

    private class MaxHeightScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, cap)
        }
    }
}
