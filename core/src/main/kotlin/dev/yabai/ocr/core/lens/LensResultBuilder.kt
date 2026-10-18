package dev.yabai.ocr.core.lens

/**
 * Turns the decoded protobuf tree into the flat list of text lines and the text blocks the UI
 * consumes.
 *
 * The join rules replicate the reference implementation: a word contributes its own text and,
 * when the `text_separator` field is present (even if empty, which is what happens for Japanese),
 * that separator is appended verbatim. Only when the field is absent is a plain space inserted
 * between words, which is the Latin-script case.
 */
object LensResultBuilder {

    private val WHITESPACE = Regex("\\s+")

    /**
     * Largest gap, as a fraction of the glyph size, that still counts as "this is the next column of
     * the same block".
     *
     * Lens segments a page down to single *columns* of vertical text, so a three-column speech
     * balloon arrives as three unrelated texts. Measured against the real endpoint, the gap between
     * two columns of one balloon is about half a glyph wide while the gutter between two balloons is
     * a full glyph or more, so the split sits comfortably between the two — and it errs towards
     * joining, because half a sentence translates to nonsense while two joined balloons just mean one
     * extra line to delete.
     */
    private const val MERGE_GAP_RATIO = 0.75f

    /** Everything one Lens response tells us, in the shapes the UI wants. */
    class Built(
        val language: String,
        val segments: List<LensSegment>,
        val blocks: List<LensBlock>,
    )

    /** One line as Lens reported it, with the box it was placed in. */
    internal class Line(val text: String, val box: PixelBox?)

    fun build(
        parsed: LensProto.ParsedResponse,
        imageWidth: Int,
        imageHeight: Int,
    ): Built {
        val segments = ArrayList<LensSegment>()
        val lines = ArrayList<Line>()

        for (paragraph in parsed.paragraphs) {
            val paragraphBox = paragraph.geometry?.takeIf { it.isNormalized }
                ?.toPixels(imageWidth, imageHeight)

            for (line in paragraph.lines) {
                val text = joinLine(line)
                if (text.isEmpty()) continue
                val box = line.geometry?.takeIf { it.isNormalized }
                    ?.toPixels(imageWidth, imageHeight)
                    ?: paragraphBox
                segments += LensSegment(text, box ?: PixelBox(0, 0, imageWidth, imageHeight))
                lines += Line(text, box)
            }
        }

        val language = parsed.language
            ?: parsed.paragraphs.firstNotNullOfOrNull { it.language }
            ?: ""

        return Built(language, segments, groupIntoBlocks(lines))
    }

    /**
     * Groups the lines into the text blocks the picker offers.
     */
    internal fun groupIntoBlocks(lines: List<Line>): List<LensBlock> =
        merge(lines).map { group ->
            LensBlock(
                // Columns are joined with a space rather than a newline: JapaneseText.normalize
                // drops the spaces between two Japanese characters and keeps the ones between Latin
                // words, which turns a balloon's columns back into the one sentence they are.
                text = group.joinToString(" ") { it.text },
                lines = group.map { it.text },
                box = union(group.mapNotNull { it.box }),
            )
        }

    /**
     * Groups the lines that belong to the same text block, so that the boxes offered on screen are
     * speech balloons and paragraphs rather than the individual columns Lens splits them into.
     *
     * Lines with no geometry cannot be placed, so they never join anything and stay on their own.
     */
    private fun merge(lines: List<Line>): List<List<Line>> {
        if (lines.isEmpty()) return emptyList()
        if (lines.size == 1) return listOf(lines)

        val parent = IntArray(lines.size) { it }

        fun root(index: Int): Int {
            var node = index
            while (parent[node] != node) node = parent[node]
            var walk = index
            while (parent[walk] != node) {
                val next = parent[walk]
                parent[walk] = node
                walk = next
            }
            return node
        }

        for (i in lines.indices) {
            val a = lines[i].box ?: continue
            for (j in i + 1 until lines.size) {
                val b = lines[j].box ?: continue
                if (!belongsToSameBlock(a, b)) continue
                val rootA = root(i)
                val rootB = root(j)
                if (rootA != rootB) parent[rootB] = rootA
            }
        }

        val groups = LinkedHashMap<Int, ArrayList<Line>>()
        for (i in lines.indices) groups.getOrPut(root(i)) { ArrayList() } += lines[i]
        return groups.values.toList()
    }

    /**
     * Two lines are the same block when they run in the same direction, share a band across it, and
     * sit closer along it than one glyph is wide.
     */
    private fun belongsToSameBlock(a: PixelBox, b: PixelBox): Boolean {
        val aVertical = a.height >= a.width
        val bVertical = b.height >= b.width
        if (aVertical != bVertical) return false

        return if (aVertical) {
            val shared = overlap(a.y, a.bottom, b.y, b.bottom)
            if (shared < 0.5f * minOf(a.height, b.height)) return false
            val gap = maxOf(b.x - a.right, a.x - b.right)
            gap <= MERGE_GAP_RATIO * maxOf(a.width, b.width)
        } else {
            val shared = overlap(a.x, a.right, b.x, b.right)
            if (shared < 0.5f * minOf(a.width, b.width)) return false
            val gap = maxOf(b.y - a.bottom, a.y - b.bottom)
            gap <= MERGE_GAP_RATIO * maxOf(a.height, b.height)
        }
    }

    /** Overlap of two intervals. Negative when they are apart. */
    private fun overlap(startA: Int, endA: Int, startB: Int, endB: Int): Int =
        minOf(endA, endB) - maxOf(startA, startB)

    private fun union(boxes: List<PixelBox>): PixelBox? {
        if (boxes.isEmpty()) return null
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (box in boxes) {
            if (box.x < left) left = box.x
            if (box.y < top) top = box.y
            if (box.right > right) right = box.right
            if (box.bottom > bottom) bottom = box.bottom
        }
        return PixelBox(left, top, right - left, bottom - top)
    }

    private fun joinLine(line: LensProto.ParsedLine): String {
        if (line.words.isEmpty()) return ""
        val sb = StringBuilder()
        line.words.forEachIndexed { index, word ->
            sb.append(word.plainText)
            when {
                word.hasSeparator -> sb.append(word.separator)
                index < line.words.size - 1 -> sb.append(' ')
            }
        }
        return WHITESPACE.replace(sb, " ").trim()
    }
}
