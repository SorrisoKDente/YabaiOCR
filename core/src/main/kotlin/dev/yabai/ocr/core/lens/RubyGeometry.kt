package dev.yabai.ocr.core.lens

import dev.yabai.ocr.core.jp.JapaneseText

/**
 * Finds furigana among the lines a recogniser reported as separate boxes.
 *
 * Lens reports a reading as its own line, printed beside the kanji it belongs to, so the sentence
 * comes out as `しょくいん職員さんに聞いた` — the reading glued to the word it explains. There is no
 * text-level tell for that (a reading is a perfectly good word), but there is a geometric one: a
 * reading is printed **smaller** than the text it annotates and **touching** it, while a separate
 * column of text is either the same size or separated by a gutter.
 *
 * The numbers below were measured from a live response for a page with furigana
 * (`test_furigana.png` / `lens_furigana.bin`), where a vertical reading came back as a 16×92 box
 * beside a 45×375 column — 36% as thick, 8px away — and a horizontal one as a 89×17 box above a
 * 185×41 row — 41% as thick, 16px away:
 *
 * | rule | measured | used |
 * | --- | --- | --- |
 * | a reading is thinner than its line | 0.36 / 0.41 | ≤ 0.6 |
 * | it is printed touching it | 8px of 45 / 16px of 41 | ≤ 0.5 of the thickness |
 * | it sits over the characters it reads | 95% / 100% | ≥ 0.5 of the reading |
 * | it does not run past its line | 5px of 45 | ≤ 1 glyph |
 *
 * Everything a reading must be is also something ordinary text rarely is (kana only, half the size,
 * glued to a line with a kanji in it), so the rules are deliberately conservative: a line that is
 * *not* recognised stays in the sentence exactly as before, which is the safe way to be wrong.
 */
internal object RubyGeometry {

    /**
     * A reading and where it belongs in its line, in **characters** of that line: the characters
     * `[start, start + count)` are the ones it is printed over.
     */
    class Ruby(val text: String, val start: Int, val count: Int)

    /** A reading that was recognised: line [reading] annotates line [base]. */
    class Match(val reading: Int, val base: Int, val ruby: Ruby)

    fun matches(lines: List<Pair<String, PixelBox?>>): List<Match> {
        val found = ArrayList<Match>()
        for (reading in lines.indices) {
            val text = lines[reading].first
            val box = lines[reading].second ?: continue
            if (!JapaneseText.isKanaOnly(text)) continue

            var best = -1
            var bestCoverage = 0
            for (base in lines.indices) {
                if (base == reading) continue
                val baseText = lines[base].first
                val baseBox = lines[base].second ?: continue
                // A reading explains a kanji; a line of kana alone has nothing to explain. (This is
                // also why a reading can never be a base: the two roles need different characters.)
                if (!JapaneseText.hasKanji(baseText)) continue
                if (!isPrintedBeside(box, baseBox)) continue
                if (!coversReading(box, baseBox)) continue

                val coverage = overlap(box, baseBox)
                if (coverage > bestCoverage) {
                    bestCoverage = coverage
                    best = base
                }
            }
            if (best < 0) continue
            found += Match(
                reading,
                best,
                rubyOver(text, box, lines[best].first, lines[best].second!!),
            )
        }
        return found
    }

    /** True when [reading] is smaller than [base] and touching it. */
    private fun isPrintedBeside(reading: PixelBox, base: PixelBox): Boolean {
        if (isVertical(reading) != isVertical(base)) return false
        if (thickness(reading) * 100 > thickness(base) * MAX_THICKNESS_PERCENT) return false

        // Across the line: a gutter means a column of its own, not a reading glued to this one.
        val gap = if (isVertical(reading)) {
            maxOf(reading.x - base.right, base.x - reading.right)
        } else {
            maxOf(reading.y - base.bottom, base.y - reading.bottom)
        }
        if (gap * 100 > thickness(base) * MAX_GAP_PERCENT) return false

        // Along the line: a reading may start a little early (kana sit lower than kanji) but it may
        // not stick out of the line it reads by more than one of that line's glyphs.
        val readingSpan = span(reading)
        val baseSpan = span(base)
        val overhang = maxOf(baseSpan.first - readingSpan.first, readingSpan.last - baseSpan.last)
        return overhang <= thickness(base)
    }

    /** True when the reading is printed over its line rather than beside its end. */
    private fun coversReading(reading: PixelBox, base: PixelBox): Boolean =
        overlap(reading, base) * 100 >= length(reading) * MIN_COVERAGE_PERCENT

    /**
     * Maps the reading's box onto the characters of [baseText].
     *
     * A line's box spans its characters evenly, so where the reading sits *along* the line says which
     * characters it reads: a reading printed over the first two of eight characters is the reading of
     * those two, not of the whole line. Rounding outwards (floor on the start, ceil on the end) is
     * what keeps a reading from being attached to too few characters.
     */
    private fun rubyOver(
        readingText: String,
        reading: PixelBox,
        baseText: String,
        base: PixelBox,
    ): Ruby {
        val characters = baseText.codePointCount(0, baseText.length)
        val readingSpan = span(reading)
        val baseSpan = span(base)
        val size = (baseSpan.last - baseSpan.first + 1).coerceAtLeast(1)

        val from = (readingSpan.first - baseSpan.first).toFloat() / size
        val to = (readingSpan.last - baseSpan.first + 1).toFloat() / size
        val start = floor(from * characters).coerceIn(0, characters - 1)
        val end = ceil(to * characters).coerceIn(start + 1, characters)
        return Ruby(readingText, start, end - start)
    }

    /** The extent of the box along the direction the text runs. */
    private fun span(box: PixelBox): IntRange =
        if (isVertical(box)) box.y until box.bottom else box.x until box.right

    /** How far the two boxes overlap along that direction. */
    private fun overlap(reading: PixelBox, base: PixelBox): Int {
        val a = span(reading)
        val b = span(base)
        return minOf(a.last, b.last) - maxOf(a.first, b.first) + 1
    }

    /** Vertical text is a column: taller than it is wide. */
    private fun isVertical(box: PixelBox): Boolean = box.height >= box.width

    /** The size of the glyphs: a column is as thick as it is wide, a row as thick as it is tall. */
    private fun thickness(box: PixelBox): Int = if (isVertical(box)) box.width else box.height

    /** How far the box runs. */
    private fun length(box: PixelBox): Int = if (isVertical(box)) box.height else box.width

    private fun floor(value: Float): Int = kotlin.math.floor(value).toInt()

    private fun ceil(value: Float): Int = kotlin.math.ceil(value).toInt()

    private const val MAX_THICKNESS_PERCENT = 60
    private const val MAX_GAP_PERCENT = 50
    private const val MIN_COVERAGE_PERCENT = 50
}
