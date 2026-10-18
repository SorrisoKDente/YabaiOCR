package dev.yabai.ocr.util

import android.content.Context
import android.graphics.Rect
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

fun Context.screenWidth(): Int = resources.displayMetrics.widthPixels

fun Context.screenHeight(): Int = resources.displayMetrics.heightPixels

/** Clamps [rect] into the bounds of a [width] x [height] surface. */
fun Rect.clampTo(width: Int, height: Int): Rect {
    val l = left.coerceIn(0, maxOf(0, width - 1))
    val t = top.coerceIn(0, maxOf(0, height - 1))
    val r = right.coerceIn(l + 1, width)
    val b = bottom.coerceIn(t + 1, height)
    return Rect(l, t, r, b)
}
