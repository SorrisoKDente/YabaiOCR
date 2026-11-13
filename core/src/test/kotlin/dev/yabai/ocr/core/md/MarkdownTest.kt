package dev.yabai.ocr.core.md

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    private fun text(spans: List<MarkdownSpan>): String =
        spans.joinToString("") { span ->
            when (span) {
                is MarkdownSpan.Text -> span.text
                is MarkdownSpan.Bold -> span.text
                is MarkdownSpan.Italic -> span.text
                is MarkdownSpan.Code -> span.text
                is MarkdownSpan.Link -> span.text
            }
        }

    @Test
    fun `headings keep their level and text`() {
        val blocks = parseMarkdown("# Título\n\n### Correções")

        assertEquals(2, blocks.size)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals("Título", text((blocks[0] as MarkdownBlock.Heading).spans))
        assertEquals(3, (blocks[1] as MarkdownBlock.Heading).level)
    }

    @Test
    fun `a hash inside a word is not a heading`() {
        val blocks = parseMarkdown("#tag não é título")

        assertTrue(blocks.single() is MarkdownBlock.Paragraph)
        assertEquals("#tag não é título", text((blocks.single() as MarkdownBlock.Paragraph).spans))
    }

    @Test
    fun `consecutive bullet lines become one list`() {
        val blocks = parseMarkdown("- um\n- dois\n* três")

        val list = blocks.single() as MarkdownBlock.Bullets
        assertEquals(3, list.items.size)
        assertEquals(listOf("um", "dois", "três"), list.items.map(::text))
    }

    @Test
    fun `a paragraph ends at a blank line`() {
        val blocks = parseMarkdown("primeira linha\nsegunda linha\n\noutro parágrafo")

        assertEquals(2, blocks.size)
        assertEquals("primeira linha segunda linha", text((blocks[0] as MarkdownBlock.Paragraph).spans))
        assertEquals("outro parágrafo", text((blocks[1] as MarkdownBlock.Paragraph).spans))
    }

    @Test
    fun `a fenced code block keeps its lines verbatim`() {
        val blocks = parseMarkdown("```bash\ngradle :desktop:test\n# não é título\n```")

        val code = blocks.single() as MarkdownBlock.Code
        assertEquals("bash", code.language)
        assertEquals("gradle :desktop:test\n# não é título", code.text)
    }

    @Test
    fun `an unterminated code fence still renders what was written`() {
        val blocks = parseMarkdown("```\nlinha sem fechamento")

        assertEquals("linha sem fechamento", (blocks.single() as MarkdownBlock.Code).text)
    }

    @Test
    fun `quotes and dividers are recognised`() {
        val blocks = parseMarkdown("> cuidado\n\n---")

        assertEquals("cuidado", text((blocks[0] as MarkdownBlock.Quote).spans))
        assertEquals(MarkdownBlock.Divider, blocks[1])
    }

    @Test
    fun `bold italic code and links become spans`() {
        val spans = parseInline("Use **negrito**, *itálico*, `código` e [link](https://example.com/a?b=1).")

        assertTrue(spans.contains(MarkdownSpan.Bold("negrito")))
        assertTrue(spans.contains(MarkdownSpan.Italic("itálico")))
        assertTrue(spans.contains(MarkdownSpan.Code("código")))
        assertTrue(spans.contains(MarkdownSpan.Link("link", "https://example.com/a?b=1")))
        assertTrue(spans.contains(MarkdownSpan.Text(".")))
        assertEquals("Use negrito, itálico, código e link.", text(spans))
    }

    @Test
    fun `an unclosed marker stays visible as text`() {
        val spans = parseInline("**sem fechamento")

        assertEquals(listOf(MarkdownSpan.Text("**sem fechamento")), spans)
    }

    @Test
    fun `a real release note parses into readable blocks`() {
        val notes = """
            ## Novidades

            - **Hover** sem diálogo no KDE
            - Seletor de monitor

            ### Correções

            1. Nada aqui é uma lista numerada, então fica parágrafo.
        """.trimIndent()

        val blocks = parseMarkdown(notes)

        assertEquals(MarkdownBlock.Heading::class.java, blocks[0].javaClass)
        val bullets = blocks[1] as MarkdownBlock.Bullets
        assertEquals(2, bullets.items.size)
        assertTrue(bullets.items[0].first() is MarkdownSpan.Bold)
        assertEquals(3, (blocks[2] as MarkdownBlock.Heading).level)
        assertTrue(blocks[3] is MarkdownBlock.Paragraph)
    }
}
