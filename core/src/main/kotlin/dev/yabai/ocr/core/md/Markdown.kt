package dev.yabai.ocr.core.md

/**
 * A release note is Markdown, and both apps have to *show* it, not dump the source. This is the
 * smallest parser that covers what GitHub release bodies actually use: headings, paragraphs, bullet
 * lists, quotes, fenced code, rules, and the inline emphasis/links/backticks.
 *
 * It is deliberately not a Markdown implementation: no tables, no HTML, no reference links, no
 * nested lists. Anything it does not understand stays visible as plain text, which is the honest
 * failure for a preview — dropping text would hide part of the release notes.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Paragraph(val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Bullets(val items: List<List<MarkdownSpan>>) : MarkdownBlock
    data class Quote(val spans: List<MarkdownSpan>) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

/** One run of inline text, already classified so each UI can style it. */
sealed interface MarkdownSpan {
    data class Text(val text: String) : MarkdownSpan
    data class Bold(val text: String) : MarkdownSpan
    data class Italic(val text: String) : MarkdownSpan
    data class Code(val text: String) : MarkdownSpan
    data class Link(val text: String, val url: String) : MarkdownSpan
}

/** Splits a Markdown document into blocks, in reading order. */
fun parseMarkdown(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> index++

            trimmed.startsWith("```") -> {
                val language = trimmed.removePrefix("```").trim()
                val body = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    body.appendLine(lines[index])
                    index++
                }
                // Skip the closing fence when there is one; an unterminated block still renders.
                if (index < lines.size) index++
                blocks += MarkdownBlock.Code(language, body.toString().trimEnd('\n'))
            }

            isDivider(trimmed) -> {
                blocks += MarkdownBlock.Divider
                index++
            }

            headingLevel(trimmed) > 0 -> {
                val level = headingLevel(trimmed)
                val text = trimmed.drop(level).trim()
                blocks += MarkdownBlock.Heading(level, parseInline(text))
                index++
            }

            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote += lines[index].trim().removePrefix(">").trim()
                    index++
                }
                blocks += MarkdownBlock.Quote(parseInline(quote.joinToString(" ")))
            }

            isBullet(trimmed) -> {
                val items = mutableListOf<List<MarkdownSpan>>()
                while (index < lines.size && isBullet(lines[index].trim())) {
                    val item = lines[index].trim().removePrefix("- ").removePrefix("* ")
                        .removePrefix("+ ").trim()
                    items += parseInline(item)
                    index++
                }
                blocks += MarkdownBlock.Bullets(items)
            }

            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index].trim()
                    if (current.isEmpty() || current.startsWith("```") || headingLevel(current) > 0 ||
                        current.startsWith(">") || isBullet(current) || isDivider(current)
                    ) {
                        break
                    }
                    paragraph += current
                    index++
                }
                blocks += MarkdownBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
            }
        }
    }

    return blocks
}

private fun headingLevel(line: String): Int {
    val hashes = line.takeWhile { it == '#' }.length
    if (hashes !in 1..6) return 0
    // `#tag` is not a heading; Markdown requires a space (or the end of the line) after the hashes.
    if (line.length > hashes && !line[hashes].isWhitespace()) return 0
    return hashes
}

private fun isBullet(line: String): Boolean =
    line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")

private fun isDivider(line: String): Boolean {
    val compact = line.replace(" ", "")
    return (compact.length >= 3) &&
        (compact.all { it == '-' } || compact.all { it == '*' } || compact.all { it == '_' })
}

/**
 * Splits one line of inline Markdown into styled runs.
 *
 * Bold is checked before italic so `**text**` does not become two italic markers, and an unclosed
 * marker is kept as text: a release note copied mid-sentence should still read correctly.
 */
fun parseInline(text: String): List<MarkdownSpan> {
    val spans = mutableListOf<MarkdownSpan>()
    val plain = StringBuilder()

    fun flush() {
        if (plain.isNotEmpty()) {
            spans += MarkdownSpan.Text(plain.toString())
            plain.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val match = INLINE.firstNotNullOfOrNull { pattern ->
            pattern.matchAt(text, i)?.let { pattern to it }
        }
        if (match == null) {
            plain.append(text[i])
            i++
            continue
        }

        val (pattern, result) = match
        flush()
        spans += pattern.spanFor(result)
        i = result.range.last + 1
    }
    flush()
    return spans
}

private class InlinePattern(
    private val regex: Regex,
    private val toSpan: (MatchResult) -> MarkdownSpan,
) {
    fun matchAt(text: String, index: Int): MatchResult? =
        regex.find(text, index)?.takeIf { it.range.first == index }

    fun spanFor(result: MatchResult): MarkdownSpan = toSpan(result)
}

private val INLINE = listOf(
    InlinePattern(Regex("""`([^`]+)`""")) { MarkdownSpan.Code(it.groupValues[1]) },
    InlinePattern(Regex("""\*\*([^*]+)\*\*""")) { MarkdownSpan.Bold(it.groupValues[1]) },
    InlinePattern(Regex("""__([^_]+)__""")) { MarkdownSpan.Bold(it.groupValues[1]) },
    InlinePattern(Regex("""\[([^\]]+)]\(([^)\s]+)\)""")) {
        MarkdownSpan.Link(it.groupValues[1], it.groupValues[2])
    },
    InlinePattern(Regex("""\*([^*\n]+)\*""")) { MarkdownSpan.Italic(it.groupValues[1]) },
    InlinePattern(Regex("""_([^_\n]+)_""")) { MarkdownSpan.Italic(it.groupValues[1]) },
)
