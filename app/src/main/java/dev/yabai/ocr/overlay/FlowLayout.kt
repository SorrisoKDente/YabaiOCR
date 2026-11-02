package dev.yabai.ocr.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import dev.yabai.ocr.util.dp

/**
 * A row of views that wraps onto the next line instead of squeezing its children.
 *
 * A [android.widget.LinearLayout] never wraps: when its children add up to more than the screen is
 * wide it shrinks the last ones down to their minimum width, and a TextView at its minimum width
 * draws its label one character per line — which is exactly how the result panel's «Fechar» button
 * ended up as a vertical column of letters. The panels here are a loose set of buttons that keeps
 * growing, so a container that cannot overflow is worth the extra lines of code.
 *
 * Children are placed left to right and wrap when they run out of room. Set [alignEnd] to push each
 * line to the right edge, which is what `gravity = END` used to do.
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    var alignEnd: Boolean = true

    private val lines = ArrayList<MutableList<View>>()
    private val verticalGap = context.dp(4)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val available = maxOf(0, widthSize - paddingLeft - paddingRight)

        lines.clear()
        var line = ArrayList<View>()
        var lineWidth = 0
        var lineHeight = 0
        var totalHeight = 0
        var widest = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)

            val isFirst = line.isEmpty()
            val childWidth = outerWidth(child, isFirst)
            val childHeight = outerHeight(child)

            // Wrap when this child would not fit after the ones already on the line. A child wider
            // than the container goes on a line of its own and is left to be measured as it wants.
            if (!isFirst && lineWidth + childWidth > available) {
                lines.add(line)
                widest = maxOf(widest, lineWidth)
                totalHeight += lineHeight + verticalGap
                line = ArrayList()
                line.add(child)
                lineWidth = outerWidth(child, true)
                lineHeight = outerHeight(child)
            } else {
                line.add(child)
                lineWidth += childWidth
                lineHeight = maxOf(lineHeight, childHeight)
            }
        }

        if (line.isNotEmpty()) {
            lines.add(line)
            widest = maxOf(widest, lineWidth)
            totalHeight += lineHeight
        }

        val content = widest + paddingLeft + paddingRight
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(widthSize, content)
            else -> content
        }
        setMeasuredDimension(width, resolveSize(totalHeight + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var y = paddingTop
        for ((index, line) in lines.withIndex()) {
            var lineHeight = 0
            var lineWidth = 0
            for ((childIndex, child) in line.withIndex()) {
                lineHeight = maxOf(lineHeight, outerHeight(child))
                lineWidth += outerWidth(child, childIndex == 0)
            }

            var x = if (alignEnd) (r - l - paddingRight - lineWidth) else paddingLeft
            for ((childIndex, child) in line.withIndex()) {
                val params = child.layoutParams as MarginLayoutParams
                val effectiveLeftMargin = if (childIndex == 0) 0 else params.leftMargin
                child.layout(
                    x + effectiveLeftMargin,
                    y + params.topMargin,
                    x + effectiveLeftMargin + child.measuredWidth,
                    y + params.topMargin + child.measuredHeight,
                )
                x += outerWidth(child, childIndex == 0)
            }
            y += lineHeight + if (index < lines.size - 1) verticalGap else 0
        }
    }

    private fun outerWidth(child: View, isFirstInLine: Boolean): Int {
        val params = child.layoutParams as MarginLayoutParams
        val left = if (isFirstInLine) 0 else params.leftMargin
        return child.measuredWidth + left + params.rightMargin
    }

    private fun outerHeight(child: View): Int {
        val params = child.layoutParams as MarginLayoutParams
        return child.measuredHeight + params.topMargin + params.bottomMargin
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams = MarginLayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams?): Boolean = params is MarginLayoutParams
}
