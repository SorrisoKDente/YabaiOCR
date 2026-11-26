package dev.yabai.ocr.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import dev.yabai.ocr.R
import dev.yabai.ocr.util.dp

/**
 * The wait for a reading: one thin line with the spinner, the label and the way out beside it.
 *
 * R50 put «Cancelar» under the text, which made the waiting card two lines tall and covered part of
 * the picture it sits on. The bar is one line instead: the label takes whatever the spinner leaves,
 * and the red «Cancelar» ends the line (R57). Both the result panel and the camera screen build the
 * wait here, so the two cannot drift apart — the camera had a card and a wording of its own.
 *
 * [background] is for the camera, which draws over a photo and needs its own fill; the panel already
 * sits inside its card, so it passes none.
 */
fun Context.loadingBar(
    label: CharSequence,
    textColor: Int,
    background: Drawable? = null,
    onCancel: () -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(12), dp(6), dp(8), dp(6))
    if (background != null) setBackground(background)

    addView(
        ProgressBar(this@loadingBar).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(textColor)
        },
        LinearLayout.LayoutParams(dp(20), dp(20)),
    )
    addView(
        textLabel(label, 15f, textColor).apply { setPadding(dp(10), 0, 0, 0) },
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
    )
    addView(
        actionButton(
            getString(R.string.recognition_cancel),
            background = getColor(R.color.warn),
        ) { onCancel() }.apply {
            // The wait is a line, not a section: the way out shrinks with it, but not below a
            // finger: it is the only way out of a reading that can take seconds.
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            minHeight = dp(36)
            minWidth = dp(48)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { leftMargin = dp(8) },
    )
}
