package dev.yabai.ocr.core.layout

import dev.yabai.ocr.core.lens.PixelBox

/**
 * Which character of a printed strip a finger is on (R17).
 *
 * Japanese is set on a grid: a column of vertical text is a stack of full-width cells, a row of
 * horizontal text is a line of them. The recogniser reports the text of the strip and the box
 * around it, not the position of each character — but dividing the box by the number of characters
 * gives it back, which is what makes «tap the kanji you mean» possible without a second capture.
 *
 * No reading of the text is involved: the cell is pure geometry, so it works for kana, for kanji
 * and for the punctuation that shares the same square.
 */
object KanjiGrid {

    /** One character of a strip, and the part of the box it occupies. */
    data class Cell(val index: Int, val character: Char, val box: PixelBox)

    /**
     * The character of [text] printed under ([x], [y]), or null when there is nothing to read.
     *
     * A strip is taken as vertical when it is taller than wide — which is how a manga column is
     * drawn — and horizontal otherwise. The point is clamped into the strip, so a finger that lands
     * a little past the last character still gets that character instead of nothing: the strip was
     * hit, and saying «no character» there would be a lie about what the user touched.
     */
    fun cell(text: String, box: PixelBox, x: Int, y: Int): Cell? {
        if (text.isEmpty() || box.width <= 0 || box.height <= 0) return null
        val count = text.length
        val vertical = box.height >= box.width
        val index = if (vertical) {
            val step = box.height.toFloat() / count
            ((y - box.y) / step).toInt().coerceIn(0, count - 1)
        } else {
            val step = box.width.toFloat() / count
            ((x - box.x) / step).toInt().coerceIn(0, count - 1)
        }
        return Cell(index, text[index], boxOf(text, box, index))
    }

    /**
     * Where character [index] of [text] sits inside [box].
     *
     * The cells tile the box exactly — the n-th boundary is `total * n / count`, in integers — so
     * neighbouring cells share an edge and never overlap, which is what makes the highlight drawn
     * on the page land on one character and not on two.
     */
    fun boxOf(text: String, box: PixelBox, index: Int): PixelBox =
        boxOf(text, box, index, index + 1)

    /**
     * Where characters `[from, to)` of [text] sit inside [box] (R29).
     *
     * A mark over one character of a word whose card talks about the whole word contradicts the
     * card; the kanji of 同時に are marked together. The boundaries are the same integers as the
     * single cell, so a range that ends where the next character begins shares its edge with it.
     */
    fun boxOf(text: String, box: PixelBox, from: Int, to: Int): PixelBox {
        val count = text.length
        if (count == 0 || box.width <= 0 || box.height <= 0) return box
        val start = from.coerceIn(0, count - 1)
        val end = to.coerceIn(start + 1, count)
        return if (box.height >= box.width) {
            val top = box.y + (box.height.toLong() * start / count).toInt()
            val bottom = box.y + (box.height.toLong() * end / count).toInt()
            PixelBox(box.x, top, box.width, (bottom - top).coerceAtLeast(1))
        } else {
            val left = box.x + (box.width.toLong() * start / count).toInt()
            val right = box.x + (box.width.toLong() * end / count).toInt()
            PixelBox(left, box.y, (right - left).coerceAtLeast(1), box.height)
        }
    }
}
