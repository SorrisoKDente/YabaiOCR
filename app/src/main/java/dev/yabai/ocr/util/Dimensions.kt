package dev.yabai.ocr.util

import android.content.Context
import android.util.TypedValue
import android.view.View
import kotlin.math.roundToInt

fun Context.dp(value: Number): Int =
    (value.toFloat() * resources.displayMetrics.density).roundToInt()

fun Context.dpFloat(value: Number): Float =
    value.toFloat() * resources.displayMetrics.density

fun Context.sp(value: Number): Float =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value.toFloat(),
        resources.displayMetrics,
    )

/*
 * View-shaped overloads: most of our custom views are built with `LinearLayout(context).apply { … }`
 * and their classes are not Contexts themselves, so the same helpers are needed with a View receiver.
 */

fun View.dp(value: Number): Int = context.dp(value)

fun View.dpFloat(value: Number): Float = context.dpFloat(value)

fun View.sp(value: Number): Float = context.sp(value)

/*
 * The height of one of the system bars, as the framework reports it.
 *
 * A floating overlay window is drawn over the whole screen — its page has to keep the size it was
 * captured at — which means the system bars are painted on top of it and no window inset ever says
 * how tall they are. The framework's dimen is the answer that works from a service, and the
 * fallback is the AOSP default for the bar.
 */
fun Context.systemBarHeight(name: String, fallbackDp: Number): Int {
    val id = resources.getIdentifier(name, "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else dp(fallbackDp)
}

fun Context.screenWidth(): Int = resources.displayMetrics.widthPixels

fun Context.screenHeight(): Int = resources.displayMetrics.heightPixels
