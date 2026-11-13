package dev.yabai.ocr.update

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import dev.yabai.ocr.core.md.MarkdownBlock
import dev.yabai.ocr.core.md.MarkdownSpan
import dev.yabai.ocr.core.md.parseMarkdown

/**
 * Renders the Markdown of a GitHub release into text a plain [android.widget.TextView] can show.
 *
 * The blocks come from the shared parser in `:core`, so the phone and the desktop preview agree on
 * what the release notes say; this function only decides how each block looks.
 */
fun markdownToSpanned(
    markdown: String,
    accentColor: Int,
    secondaryColor: Int,
    codeBackgroundColor: Int,
): Spanned {
    val out = SpannableStringBuilder()
    val blocks = parseMarkdown(markdown)

    blocks.forEachIndexed { index, block ->
        if (index > 0) out.append("\n")
        when (block) {
            is MarkdownBlock.Heading -> {
                val start = out.length
                appendSpans(out, block.spans)
                val size = when (block.level) {
                    1 -> 1.35f
                    2 -> 1.2f
                    3 -> 1.1f
                    else -> 1f
                }
                out.setSpan(RelativeSizeSpan(size), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(
                    ForegroundColorSpan(accentColor),
                    start,
                    out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                out.append("\n")
            }

            is MarkdownBlock.Paragraph -> {
                appendSpans(out, block.spans)
                out.append("\n")
            }

            is MarkdownBlock.Bullets -> {
                block.items.forEach { item ->
                    out.append("• ")
                    appendSpans(out, item)
                    out.append("\n")
                }
            }

            is MarkdownBlock.Quote -> {
                val start = out.length
                out.append("▎ ")
                appendSpans(out, block.spans)
                out.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    start,
                    out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                out.setSpan(
                    ForegroundColorSpan(secondaryColor),
                    start,
                    out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                out.append("\n")
            }

            is MarkdownBlock.Code -> {
                val start = out.length
                out.append(block.text)
                out.setSpan(
                    TypefaceSpan("monospace"),
                    start,
                    out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                out.setSpan(
                    ForegroundColorSpan(codeBackgroundColor),
                    start,
                    out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                out.append("\n")
            }

            MarkdownBlock.Divider -> out.append("──────────\n")
        }
    }

    return out
}

private fun appendSpans(out: SpannableStringBuilder, spans: List<MarkdownSpan>) {
    spans.forEach { span ->
        val start = out.length
        when (span) {
            is MarkdownSpan.Text -> out.append(span.text)
            is MarkdownSpan.Bold -> {
                out.append(span.text)
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            is MarkdownSpan.Italic -> {
                out.append(span.text)
                out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            is MarkdownSpan.Code -> {
                out.append(span.text)
                out.setSpan(
                    TypefaceSpan("monospace"),
                    start,
                    out.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }

            // URLSpan is what makes the link tappable in a TextView with LinkMovementMethod.
            is MarkdownSpan.Link -> {
                out.append(span.text)
                out.setSpan(URLSpan(span.url), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
