package dev.yabai.ocr.core.layout

import dev.yabai.ocr.core.lens.PixelBox

/**
 * Which recognised blocks are columns of the same vertical paragraph.
 *
 * Lens merges the columns of a balloon itself, but the offline engine reports vertical Japanese one
 * column at a time: a balloon printed as 「戦う術が」 / 「無い」 arrives as two blocks, each with its own
 * sentence, and the whole-screen picker offers them as two separate things to tap even though they
 * are one sentence. The boxes are the only thing that says otherwise, so the columns are put back
 * together from where they were printed.
 *
 * Two boxes are columns of the same paragraph when all of this holds:
 *
 * - **both are columns** — taller than wide, which is how vertical text is set;
 * - **they share a band** — at least [MIN_OVERLAP_RATIO] of the shorter one overlaps the other
 *   vertically, so the two columns of a balloon meet even when one of them starts lower;
 * - **they are a glyph apart at most** — the horizontal gap is no wider than [MAX_GAP_RATIO] of a
 *   column, which is the distance between columns inside a balloon and much less than the white
 *   space between two balloons.
 *
 * Horizontal text is deliberately left alone: ML Kit already reports wrapped rows as one block, and
 * rows of two different paragraphs sit close enough that a gap rule would join them wrongly.
 */
object BlockColumns {

    /** Widest gap between two columns of the same paragraph, as a fraction of the wider column. */
    const val MAX_GAP_RATIO = 0.6f

    /** Least vertical overlap, as a fraction of the shorter column. */
    const val MIN_OVERLAP_RATIO = 0.5f

    /**
     * The indices of [boxes], grouped by paragraph and already in reading order.
     *
     * Each group is read right to left (the columns of a vertical balloon), and the groups are
     * ordered the same way [ReadingOrder] orders the page. A box without geometry cannot be placed,
     * so it is a paragraph of its own and keeps the position it came in.
     */
    fun groups(boxes: List<PixelBox?>): List<List<Int>> {
        if (boxes.size < 2) return boxes.indices.map { listOf(it) }

        // A box with no geometry cannot be compared with anything, so it is a paragraph of its own
        // and is placed after the ones that could be ordered.
        val placeable = boxes.indices.filter { boxes[it] != null }
        val unplaceable = boxes.indices.filter { boxes[it] == null }

        val parent = IntArray(boxes.size) { it }

        fun find(index: Int): Int {
            var root = index
            while (parent[root] != root) root = parent[root]
            var walk = index
            while (parent[walk] != walk) {
                val next = parent[walk]
                parent[walk] = root
                walk = next
            }
            return root
        }

        for (i in placeable) {
            for (j in placeable) {
                if (i >= j) continue
                if (sameParagraph(boxes[i]!!, boxes[j]!!)) parent[find(j)] = find(i)
            }
        }

        // LinkedHashMap keeps the recogniser's own order for paragraphs that do not merge, which is
        // how a page with no usable geometry still reads the way the engine said it should.
        val merged = LinkedHashMap<Int, MutableList<Int>>()
        for (index in placeable) {
            merged.getOrPut(find(index)) { mutableListOf() } += index
        }

        val columns = merged.values.map { group ->
            group.sortedWith(
                compareByDescending<Int> { boxes[it]!!.x }.thenBy { boxes[it]!!.y },
            )
        }
        val paragraphBoxes = columns.map { group ->
            group.map { boxes[it]!! }.reduce { a, b -> union(a, b) }
        }
        return ReadingOrder.order(paragraphBoxes).map { columns[it] } +
            unplaceable.map { listOf(it) }
    }

    private fun sameParagraph(a: PixelBox, b: PixelBox): Boolean {
        if (!isColumn(a) || !isColumn(b)) return false
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.y, b.y)
        if (overlap <= 0) return false
        if (overlap < MIN_OVERLAP_RATIO * minOf(a.height, b.height)) return false

        val gap = when {
            a.right <= b.x -> b.x - a.right
            b.right <= a.x -> a.x - b.right
            else -> 0
        }
        return gap <= MAX_GAP_RATIO * maxOf(a.width, b.width)
    }

    private fun isColumn(box: PixelBox): Boolean = box.height > box.width && box.width > 0

    private fun union(a: PixelBox, b: PixelBox): PixelBox {
        val left = minOf(a.x, b.x)
        val top = minOf(a.y, b.y)
        val right = maxOf(a.right, b.right)
        val bottom = maxOf(a.bottom, b.bottom)
        return PixelBox(left, top, right - left, bottom - top)
    }
}
