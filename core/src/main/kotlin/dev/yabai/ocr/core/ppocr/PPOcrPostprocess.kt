package dev.yabai.ocr.core.ppocr

/**
 * The DB post-processing of the PP-OCRv6_manga detector, on the probability map it returns.
 *
 * The reference implementation runs OpenCV `findContours` and `pyclipper`; the app ships neither, so
 * the same steps are done here on the map itself: threshold, connected regions (a region is a text
 * line), average confidence inside the region, and the unclip that grows the shrunk kernel back to
 * the size of the glyphs. The result is an axis-aligned box per line — the detection is nearly
 * rectangular on manga, and a rotated rectangle would only add corners the callers would ignore.
 */
object PPOcrPostprocess {

    /**
     * Boxes for every text region of [map], in the pixels of the original image.
     *
     * [map] is row-major `mapWidth` x `mapHeight`; [originalWidth]/[originalHeight] are the size the
     * detector crop came from, so the boxes come back in the caller's own coordinates.
     */
    fun boxes(
        map: FloatArray,
        mapWidth: Int,
        mapHeight: Int,
        originalWidth: Int,
        originalHeight: Int,
        threshold: Float = PPOcrSpec.MAP_THRESHOLD,
        boxThreshold: Float = PPOcrSpec.BOX_THRESHOLD,
        unclipRatio: Float = PPOcrSpec.UNCLIP_RATIO,
        minSide: Int = 3,
    ): List<PPOcrLine> {
        if (mapWidth <= 0 || mapHeight <= 0 || map.size < mapWidth * mapHeight) return emptyList()
        if (originalWidth <= 0 || originalHeight <= 0) return emptyList()

        val visited = BooleanArray(mapWidth * mapHeight)
        val stack = IntArray(mapWidth * mapHeight)
        val boxes = ArrayList<PPOcrLine>()

        for (start in 0 until mapWidth * mapHeight) {
            if (visited[start] || map[start] <= threshold) continue

            var head = 0
            var tail = 0
            stack[tail++] = start
            visited[start] = true

            var minX = start % mapWidth
            var maxX = minX
            var minY = start / mapWidth
            var maxY = minY
            var sum = 0.0
            var count = 0

            while (head < tail) {
                val index = stack[head++]
                val x = index % mapWidth
                val y = index / mapWidth
                sum += map[index]
                count++

                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                // 8-connectivity: a diagonal stroke is still one line.
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= mapHeight) continue
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        if (nx < 0 || nx >= mapWidth) continue
                        val next = ny * mapWidth + nx
                        if (visited[next] || map[next] <= threshold) continue
                        visited[next] = true
                        stack[tail++] = next
                    }
                }
            }

            if (count == 0) continue
            if (sum / count < boxThreshold) continue

            val boxWidth = maxX - minX + 1
            val boxHeight = maxY - minY + 1
            if (min(boxWidth, boxHeight) < minSide) continue

            // DB shrinks the text region by a fixed offset; growing it back by
            // area * ratio / perimeter is the reference unclip.
            val area = boxWidth.toFloat() * boxHeight
            val perimeter = 2f * (boxWidth + boxHeight)
            val grow = if (perimeter > 0f) area * unclipRatio / perimeter else 0f

            val left = ((minX - grow) * originalWidth / mapWidth).toInt().coerceIn(0, originalWidth - 1)
            val top = ((minY - grow) * originalHeight / mapHeight).toInt().coerceIn(0, originalHeight - 1)
            val right = ((maxX + 1 + grow) * originalWidth / mapWidth).toInt().coerceIn(left + 1, originalWidth)
            val bottom = ((maxY + 1 + grow) * originalHeight / mapHeight).toInt().coerceIn(top + 1, originalHeight)

            boxes += PPOcrLine(
                text = "",
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                vertical = bottom - top > right - left,
            )
        }

        return readingOrder(boxes)
    }

    /**
     * Manga reading order: right to left, top to bottom.
     *
     * Lines whose vertical extent overlaps most of the shorter one are a row; the rows come in the
     * order they appear down the page, and inside a row the rightmost column is read first. Sorting
     * by `top` alone would interleave two columns of the same balloon.
     */
    fun readingOrder(boxes: List<PPOcrLine>): List<PPOcrLine> {
        if (boxes.size <= 1) return boxes

        val bands = ArrayList<MutableList<PPOcrLine>>()
        for (box in boxes.sortedBy { it.top }) {
            val band = bands.lastOrNull()
            val belongs = band != null && band.any { member -> verticalOverlap(member, box) > 0.5f }
            if (belongs) band!!.add(box) else bands.add(mutableListOf(box))
        }

        return bands.flatMap { band -> band.sortedByDescending { it.left } }
    }

    private fun verticalOverlap(a: PPOcrLine, b: PPOcrLine): Float {
        val top = maxOf(a.top, b.top)
        val bottom = minOf(a.bottom, b.bottom)
        val overlap = (bottom - top).coerceAtLeast(0)
        val shorter = minOf(a.height, b.height).coerceAtLeast(1)
        return overlap.toFloat() / shorter
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
