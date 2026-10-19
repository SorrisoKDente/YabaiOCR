package dev.yabai.ocr.core.lens

import dev.yabai.ocr.core.jp.JapaneseText
import kotlin.math.roundToInt

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
 * | it is printed on the ruby side | right of a column, above a row | preferred |
 *
 * Everything a reading must be is also something ordinary text rarely is (kana only, half the size,
 * glued to a line with a kanji in it), so the rules are deliberately conservative: a line that is
 * *not* recognised stays in the sentence exactly as before, which is the safe way to be wrong.
 *
 * Two of those rules are about **which** line a reading belongs to, and both were learned the hard
 * way from a real print (vertical manga, five columns a few pixels apart, every reading printed in
 * the gutter to the right of the kanji it reads). There, a reading has a column on each side, both
 * thin enough, both touching, both covering it — the previous column is where ruby is printed, so
 * every reading came out after the wrong characters. What tells them apart is the **side**: ruby is
 * printed to the right of a column and above a row, never the other way round.
 *
 * The other rule is how much of the line a reading covers. A reading is typeset **fitted to the
 * kanji it reads** — five kana squeezed beside two kanji are a little taller than the two, but only
 * a hair — so the characters it reads are the ones it is **printed over**, not the ones a hair of
 * overhang touches. Rounding outwards (floor on the start, ceil on the end) turns that hair into a
 * whole extra character: five kana over 職員 became `職員さ（しょくいん）ん` in the same print.
 */
internal object RubyGeometry {

    /**
     * A reading and where it belongs in its line, in **characters** of that line: the characters
     * `[start, start + count)` are the ones it is printed over.
     */
    class Ruby(val text: String, val start: Int, val count: Int)

    /** A reading that was recognised: line [reading] annotates line [base]. */
    class Match(val reading: Int, val base: Int, val ruby: Ruby)

    /** A line that could carry a reading, and how well it fits. */
    private class Candidate(val base: Int, val onRubySide: Boolean, val coverage: Int, val gap: Int) {

        /** Ruby goes to the line it is printed against, then to the one it sits over most. */
        fun beats(other: Candidate): Boolean = when {
            onRubySide != other.onRubySide -> onRubySide
            coverage != other.coverage -> coverage > other.coverage
            else -> gap < other.gap
        }
    }

    fun matches(lines: List<Pair<String, PixelBox?>>): List<Match> {
        val found = ArrayList<Match>()
        for (reading in lines.indices) {
            val text = lines[reading].first
            val box = lines[reading].second ?: continue
            if (!JapaneseText.isKanaOnly(text)) continue

            // Several lines can sit close enough to pass the rules — the columns of one balloon are a
            // few glyphs apart, and ruby is printed in the gutter between two of them. Picking the
            // first one that fits is what wrote readings after the previous column's characters.
            var best: Candidate? = null
            for (base in lines.indices) {
                if (base == reading) continue
                val baseText = lines[base].first
                val baseBox = lines[base].second ?: continue
                // A reading explains a kanji; a line of kana alone has nothing to explain. (This is
                // also why a reading can never be a base: the two roles need different characters.)
                if (!JapaneseText.hasKanji(baseText)) continue
                if (!isPrintedBeside(box, baseBox)) continue
                if (!coversReading(box, baseBox)) continue

                val candidate = Candidate(
                    base = base,
                    onRubySide = onRubySide(box, baseBox),
                    coverage = overlap(box, baseBox),
                    gap = distance(box, baseBox),
                )
                if (best == null || candidate.beats(best)) best = candidate
            }
            if (best == null) continue
            found += Match(
                reading,
                best.base,
                rubyOver(text, box, lines[best.base].first, lines[best.base].second!!),
            )
        }
        return found
    }

    /**
     * True when the reading is printed on the side ruby is printed on: to the right of a column,
     * above a row. The other side is where the *next* line of the same balloon is, which is a line
     * the reading does not belong to however close it looks. It is a preference and not a rule,
     * because a reading printed on the other side is still better recognised than missed.
     */
    private fun onRubySide(reading: PixelBox, base: PixelBox): Boolean =
        if (isVertical(reading)) {
            2 * reading.x + reading.width > 2 * base.x + base.width
        } else {
            2 * reading.y + reading.height < 2 * base.y + base.height
        }

    /** How far apart the two boxes are across the line; 0 when they touch or overlap. */
    private fun distance(reading: PixelBox, base: PixelBox): Int = if (isVertical(reading)) {
        maxOf(base.x - reading.right, reading.x - base.right, 0)
    } else {
        maxOf(base.y - reading.bottom, reading.y - base.bottom, 0)
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
     * those two, not of the whole line.
     *
     * The two ends are rounded differently, on purpose:
     *
     *  * the **start** rounds down, so a reading that sits a hair before the characters it reads —
     *    kana are smaller than kanji and are not typeset on the same baseline — still counts from the
     *    first one. Rounding it up could drop the character it starts before, which attaches the
     *    reading to the wrong kanji, the very thing this exists to avoid.
     *  * the **end** rounds to the **nearest** boundary. A reading is typeset fitted to the characters
     *    it reads, so it is a hair longer than them at most; rounding up turns that hair into a whole
     *    extra character — five kana over 職員 came out as `職員さ（しょくいん）ん` in a real print.
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
        val start = kotlin.math.floor(from * characters).toInt().coerceIn(0, characters - 1)
        val end = (to * characters).roundToInt().coerceIn(start + 1, characters)
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

    private const val MAX_THICKNESS_PERCENT = 60
    private const val MAX_GAP_PERCENT = 50
    private const val MIN_COVERAGE_PERCENT = 50
}
