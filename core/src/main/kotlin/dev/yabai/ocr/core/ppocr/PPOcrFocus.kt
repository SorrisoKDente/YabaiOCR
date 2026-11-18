package dev.yabai.ocr.core.ppocr

import dev.yabai.ocr.core.lens.PixelBox
import kotlin.math.hypot
import kotlin.math.max

/**
 * The detected lines a **focused** read has to recognise.
 *
 * The detector always sees the whole image — its output is what says where the text is, and a crop
 * that missed half of a vertical column would change the answer. The recogniser, on the other hand,
 * costs about a hundred milliseconds per line on a CPU, and a desktop hover running it over a page
 * with fifty lines took seconds to answer a question about one word. Recognising only the lines the
 * pointer can be on keeps every answer identical (each line is recognised on its own pixels either
 * way) and drops the rest of the page's bill.
 *
 * [radiusPx] has to cover more than the line under the pointer: a word is read across the boundary
 * of two printed lines/columns when the pointer is near their edge (`HoverTextSelector.BRIDGE_*`),
 * and those neighbours are one glyph — about 25 px — away. The desktop passes a comfortable
 * multiple of that, since recognising extra lines costs time and changes nothing else.
 */
fun linesToRecognize(
    lines: List<PPOcrLine>,
    focus: PixelBox?,
    radiusPx: Int = HOVER_FOCUS_RADIUS_PX,
): List<PPOcrLine> {
    if (focus == null) return lines
    val radius = max(0, radiusPx).toDouble()
    return lines.filter { boxDistance(focus, it) <= radius }
}

/** Distance between the edges of [focus] and [line]; 0 when they touch or overlap. */
private fun boxDistance(focus: PixelBox, line: PPOcrLine): Double {
    val dx = when {
        focus.right < line.left -> (line.left - focus.right).toDouble()
        focus.x > line.right -> (focus.x - line.right).toDouble()
        else -> 0.0
    }
    val dy = when {
        focus.bottom < line.top -> (line.top - focus.bottom).toDouble()
        focus.y > line.bottom -> (focus.y - line.bottom).toDouble()
        else -> 0.0
    }
    return hypot(dx, dy)
}

/** How far from the pointer a line may be and still be worth recognising. */
const val HOVER_FOCUS_RADIUS_PX = 160
