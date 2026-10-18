package dev.yabai.ocr.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import dev.yabai.ocr.util.dp

/** Rounded solid background. */
fun roundedBackground(color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp
        if (strokeColor != null) setStroke(1, strokeColor)
    }

/**
 * Tag carried by every button built by [actionButton].
 *
 * The panels are built in code, so a layout test has no ids to look up. Tagging the buttons at the
 * one place they are made is cheaper — and less brittle — than guessing from their styling.
 */
const val ACTION_BUTTON_TAG = "yabai.actionButton"

/** The fill of a plain button, as opposed to one that is currently «on» and reads as pressed. */
const val ACTION_BUTTON_BACKGROUND = 0x22FFFFFF

/**
 * Plain TextView styled as a button.
 *
 * Deliberately not a MaterialButton: these views live in service-owned windows, and avoiding
 * Material widgets here means the overlay can never blow up on theme mismatch.
 */
fun Context.actionButton(
    label: String,
    textColor: Int = Color.WHITE,
    background: Int = ACTION_BUTTON_BACKGROUND,
    onClick: (() -> Unit)? = null,
): TextView = TextView(this).apply {
    text = label
    setTextColor(textColor)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    gravity = Gravity.CENTER
    val h = dp(14)
    val v = dp(10)
    setPadding(h, v, h, v)
    isClickable = true
    isFocusable = true
    tag = ACTION_BUTTON_TAG
    // `setBackground` rather than the `background` property: the parameter of the same name wins
    // name resolution inside this lambda, which would silently assign an Int to a Drawable slot.
    setBackground(roundedBackground(background, dp(12).toFloat()))
    if (onClick != null) setOnClickListener { onClick() }
}

fun Context.textLabel(
    content: CharSequence,
    sizeSp: Float,
    color: Int,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    text = content
    setTextColor(color)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
}

/*
 * View-shaped overloads for the factories above: the overlay views are `…Layout(context).apply { … }`
 * bodies, where the receiver is a View and not a Context.
 */

fun View.actionButton(
    label: String,
    textColor: Int = Color.WHITE,
    background: Int = ACTION_BUTTON_BACKGROUND,
    onClick: (() -> Unit)? = null,
): TextView = context.actionButton(label, textColor, background, onClick)

fun View.textLabel(
    content: CharSequence,
    sizeSp: Float,
    color: Int,
    bold: Boolean = false,
): TextView = context.textLabel(content, sizeSp, color, bold)
