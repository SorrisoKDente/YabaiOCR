package dev.yabai.ocr.core.lens

/**
 * Turns the decoded protobuf tree into the flat list of text lines the UI consumes.
 *
 * The join rules replicate the reference implementation: a word contributes its own text and,
 * when the `text_separator` field is present (even if empty, which is what happens for Japanese),
 * that separator is appended verbatim. Only when the field is absent is a plain space inserted
 * between words, which is the Latin-script case.
 */
object LensResultBuilder {

    private val WHITESPACE = Regex("\\s+")

    fun build(
        parsed: LensProto.ParsedResponse,
        imageWidth: Int,
        imageHeight: Int,
    ): Pair<String, List<LensSegment>> {
        val segments = ArrayList<LensSegment>()

        for (paragraph in parsed.paragraphs) {
            for (line in paragraph.lines) {
                val text = joinLine(line)
                if (text.isEmpty()) continue
                val geometry = line.geometry?.takeIf { it.isNormalized }
                    ?: paragraph.geometry?.takeIf { it.isNormalized }
                val box = geometry?.toPixels(imageWidth, imageHeight)
                    ?: PixelBox(0, 0, imageWidth, imageHeight)
                segments += LensSegment(text, box)
            }
        }

        val language = parsed.language
            ?: parsed.paragraphs.firstNotNullOfOrNull { it.language }
            ?: ""

        return language to segments
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
