package dev.yabai.ocr.core.layout

import dev.yabai.ocr.core.lens.PixelBox

/**
 * The order boxes are read in, taken from where they are on the page.
 *
 * Neither recogniser reports text in reading order. Lens reports one paragraph at a time and ML Kit
 * reports the page top to bottom, then left to right — which puts the leftmost column of a manga
 * balloon first, when it is the last one to be read. The boxes are the only thing that knows, so the
 * order is worked out from them, the same way for both engines (R1 for Lens, R14 for ML Kit):
 *
 * - **vertical** text reads in columns, right to left, and top to bottom inside a column;
 * - **horizontal** text reads in rows, top to bottom, and left to right inside a row.
 *
 * A list with a box that has no geometry cannot be placed, so it keeps the order it came in rather
 * than being half-sorted.
 */
object ReadingOrder {

    /**
     * The indices of [boxes], in the order a person reads them.
     *
     * The direction is decided by the majority: a box taller than it is wide is a column, and a list
     * that is half columns is a page of columns.
     */
    fun order(boxes: List<PixelBox?>): List<Int> {
        if (boxes.size < 2) return boxes.indices.toList()
        if (boxes.any { it == null }) return boxes.indices.toList()
        val solid = boxes.map { it!! }

        val vertical = solid.count { it.height >= it.width } * 2 >= solid.size
        return if (vertical) {
            stack(solid, descending = true, along = { it.x }, extent = { it.right }, size = { it.width })
                .flatMap { column -> column.sortedBy { solid[it].y } }
        } else {
            stack(solid, descending = false, along = { it.y }, extent = { it.bottom }, size = { it.height })
                .flatMap { row -> row.sortedBy { solid[it].x } }
        }
    }

    /**
     * Splits the boxes into the stacks they are made of — columns when [along] is the x axis, rows
     * when it is the y axis — in reading order.
     *
     * Two boxes are the same stack when their extents across it overlap, even if they do not touch
     * along it: that is what keeps the two halves of a column the recogniser split in one column. The
     * boxes are sorted first, so a pair that overlaps is never separated by an unrelated box.
     */
    private fun stack(
        boxes: List<PixelBox>,
        descending: Boolean,
        along: (PixelBox) -> Int,
        extent: (PixelBox) -> Int,
        size: (PixelBox) -> Int,
    ): List<MutableList<Int>> {
        val ordered = boxes.indices.sortedBy { along(boxes[it]) }
            .let { if (descending) it.reversed() else it }

        val stacks = ArrayList<MutableList<Int>>()
        for (index in ordered) {
            val box = boxes[index]
            val current = stacks.lastOrNull()
            val first = current?.let { boxes[it[0]] }
            val shared = if (first == null) {
                Int.MIN_VALUE
            } else {
                overlapOf(along(box), extent(box), along(first), extent(first))
            }
            val same = first != null && shared > 0.5f * minOf(size(box), size(first))
            if (current != null && same) current += index else stacks += mutableListOf(index)
        }
        return stacks
    }

    private fun overlapOf(startA: Int, endA: Int, startB: Int, endB: Int): Int =
        minOf(endA, endB) - maxOf(startA, startB)
}
