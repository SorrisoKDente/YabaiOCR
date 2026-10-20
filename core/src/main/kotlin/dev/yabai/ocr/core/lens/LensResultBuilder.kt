package dev.yabai.ocr.core.lens

/**
 * Turns the decoded protobuf tree into the flat list of text lines and the text blocks the UI
 * consumes.
 *
 * The join rules replicate the reference implementation: a word contributes its own text and,
 * when the `text_separator` field is present (even if empty, which is what happens for Japanese),
 * that separator is appended verbatim. Only when the field is absent is a plain space inserted
 * between words, which is the Latin-script case.
 *
 * Furigana is the one thing that has to be taken back out of that join: a reading is reported as a
 * line of its own and belongs to a kanji, not to the sentence. [RubyGeometry] finds it, and the
 * caller says in [FuriganaMode] whether to drop it or keep it in parentheses.
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
        furigana: FuriganaMode = FuriganaMode.REMOVE,
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

        // A reading arrives as a line of its own and says nothing on its own: groupIntoBlocks ties it
        // back to the line it explains before any sentence is put together from them.
        val language = parsed.language
            ?: parsed.paragraphs.firstNotNullOfOrNull { it.language }
            ?: ""

        return Built(language, segments, groupIntoBlocks(lines, furigana))
    }

    /**
     * Groups the lines into the text blocks the picker offers, with the furigana of each block
     * handled as [furigana] asks.
     */
    internal fun groupIntoBlocks(
        lines: List<Line>,
        furigana: FuriganaMode = FuriganaMode.REMOVE,
    ): List<LensBlock> {
        // A reading is reported as a line of its own: find those first, then leave them out of the
        // sentence and (when asked) write them back after the characters they read.
        val matches = RubyGeometry.matches(lines.map { it.text to it.box })
        val readings = matches.mapTo(HashSet()) { it.reading }
        val rubies = matches.groupBy({ it.base }, { it.ruby })

        return merge(lines).mapNotNull { raw ->
            val group = readingOrder(raw, lines)
            // A group that held nothing but readings has no sentence at all, so it is not offered as
            // a block the user could pick.
            val sentence = group
                .filterNot { readings.contains(it) }
                .joinToString(" ") { withReadings(lines[it], rubies[it].orEmpty(), furigana) }
            if (sentence.isBlank()) return@mapNotNull null

            LensBlock(
                // Columns are joined with a space rather than a newline: JapaneseText.normalize
                // drops the spaces between two Japanese characters and keeps the ones between Latin
                // words, which turns a balloon's columns back into the one sentence they are.
                text = sentence,
                // The lines stay as Lens reported them — readings included — because this is the raw
                // view of what the recogniser saw.
                lines = group.map { lines[it].text },
                box = union(group.mapNotNull { lines[it].box }),
                // The placed ones, in reading order and without the readings: the picker draws a
                // reading beside the characters it belongs to, and the readings belong to nothing.
                pieces = group.filterNot { readings.contains(it) }
                    .map { LensSegment(lines[it].text, lines[it].box) },
            )
        }
    }

    /**
     * One line as it goes into the sentence: its own text, followed by the readings it carries —
     * written in parentheses after the characters they cover — when the caller asked to keep them.
     *
     * `職員さんに聞いた` with the reading `しょくいん` over its first two characters becomes
     * `職員（しょくいん）さんに聞いた`.
     */
    private fun withReadings(
        line: Line,
        rubies: List<RubyGeometry.Ruby>,
        furigana: FuriganaMode,
    ): String {
        if (furigana == FuriganaMode.REMOVE || rubies.isEmpty()) return line.text
        var text = line.text
        // Back to front, so the positions of the readings still to be written stay valid.
        for (ruby in rubies.sortedByDescending { it.start }) {
            text = insertAfter(text, ruby.start + ruby.count, "（${ruby.text}）")
        }
        return text
    }

    /** Inserts [insertion] into [text] after [codePoints] characters. */
    private fun insertAfter(text: String, codePoints: Int, insertion: String): String {
        var chars = 0
        var seen = 0
        while (chars < text.length && seen < codePoints) {
            chars += Character.charCount(text.codePointAt(chars))
            seen++
        }
        return text.substring(0, chars) + insertion + text.substring(chars)
    }

    /**
     * The lines of one block, in the order they are read.
     *
     * Lens reports the lines of a block in its own order, and for vertical text that order is not the
     * reading order: the columns of a balloon can arrive as right, middle, left in any arrangement,
     * and a column the recogniser split in two can arrive bottom half first. The boxes know the
     * answer, so the sentence is put together from them:
     *
     * - **vertical** text reads in columns, right to left, and top to bottom inside a column;
     * - **horizontal** text reads in rows, top to bottom, and left to right inside a row.
     *
     * A group with a line that has no geometry cannot be placed, so it keeps the order it came in
     * rather than being half-sorted.
     */
    internal fun readingOrder(group: List<Int>, lines: List<Line>): List<Int> {
        if (group.size < 2) return group
        if (group.any { lines[it].box == null }) return group

        val boxes = group.map { lines[it].box!! }
        val vertical = boxes.count { it.height >= it.width } * 2 >= boxes.size
        return if (vertical) {
            stack(group, lines, descending = true, along = { it.x }, extent = { it.right }, size = { it.width })
                .flatMap { column -> column.sortedBy { lines[it].box!!.y } }
        } else {
            stack(group, lines, descending = false, along = { it.y }, extent = { it.bottom }, size = { it.height })
                .flatMap { row -> row.sortedBy { lines[it].box!!.x } }
        }
    }

    /**
     * Splits a block into the stacks it is made of — columns when [along] is the x axis, rows when it
     * is the y axis — in reading order.
     *
     * Two lines are the same stack when their extents across it overlap, even if they do not touch
     * along it: that is what keeps the two halves of a column the recogniser split in one column. The
     * lines are sorted first, so a pair that overlaps is never separated by an unrelated line.
     */
    private fun stack(
        group: List<Int>,
        lines: List<Line>,
        descending: Boolean,
        along: (PixelBox) -> Int,
        extent: (PixelBox) -> Int,
        size: (PixelBox) -> Int,
    ): List<MutableList<Int>> {
        val ordered = group.sortedBy { along(lines[it].box!!) }
            .let { if (descending) it.reversed() else it }

        val stacks = ArrayList<MutableList<Int>>()
        for (index in ordered) {
            val box = lines[index].box!!
            val current = stacks.lastOrNull()
            val first = current?.let { lines[it[0]].box!! }
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

    /**
     * Groups the lines that belong to the same text block, by index, so that the boxes offered on
     * screen are speech balloons and paragraphs rather than the individual columns Lens splits them
     * into.
     *
     * Lines with no geometry cannot be placed, so they never join anything and stay on their own.
     */
    private fun merge(lines: List<Line>): List<List<Int>> {
        if (lines.isEmpty()) return emptyList()
        if (lines.size == 1) return listOf(listOf(0))

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

        val groups = LinkedHashMap<Int, ArrayList<Int>>()
        for (i in lines.indices) groups.getOrPut(root(i)) { ArrayList() } += i
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
