package dev.yabai.ocr.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line grammar the dictionary renderer writes (R19) read back as style ranges.
 *
 * The panel is handed one string, so this is the only place that knows a `ℹ` line is a note and a
 * `例` line an example; if the offsets drift, the panel paints the wrong line.
 */
class DefinitionStyleTest {

    private val definition = "1. to eat\n" +
        "ℹ passive form of 呼ぶ\n" +
        "例 もっと果物を食べるべきです。\n" +
        "→ You should eat more fruit.\n" +
        "2. to live on (e.g. a salary)"

    private fun markOf(text: String, at: Int): DefinitionStyle.Mark? =
        DefinitionStyle.marks(text).firstOrNull { at in it.start until it.end }

    @Test
    fun `a note an example and a translation are secondary`() {
        val marks = DefinitionStyle.marks(definition)
        for (mark in listOf("ℹ ", "例 ", "→ ")) {
            val start = definition.indexOf(mark)
            assertEquals(
                "$mark line should be styled as secondary",
                DefinitionStyle.Kind.SECONDARY,
                markOf(definition, start)?.kind,
            )
        }
    }

    @Test
    fun `a secondary mark covers the whole line`() {
        val start = definition.indexOf("ℹ ")
        val end = definition.indexOf('\n', start)
        val mark = markOf(definition, start)!!

        assertEquals(start, mark.start)
        assertEquals(end, mark.end)
    }

    @Test
    fun `a sense number is marked without the definition that follows it`() {
        val marks = DefinitionStyle.marks(definition).filter { it.kind == DefinitionStyle.Kind.NUMBER }

        assertEquals(2, marks.size)
        assertEquals("1. ", definition.substring(marks[0].start, marks[0].end))
        assertEquals("2. ", definition.substring(marks[1].start, marks[1].end))
    }

    @Test
    fun `the second sense starts after the japanese lines, not after their byte length`() {
        val second = definition.indexOf("2. ")
        assertEquals(
            DefinitionStyle.Kind.NUMBER,
            markOf(definition, second)?.kind,
        )
        assertEquals(second, markOf(definition, second)!!.start)
    }

    @Test
    fun `a translation is not mistaken for a note`() {
        val start = definition.indexOf("→ ")
        assertTrue(DefinitionStyle.isSecondary(definition.substring(start, definition.indexOf('\n', start))))
        assertFalse(DefinitionStyle.isSecondary("1. to eat"))
        assertFalse(DefinitionStyle.isSecondary("to eat"))
    }

    @Test
    fun `a plain definition has nothing to style`() {
        assertEquals(emptyList<DefinitionStyle.Mark>(), DefinitionStyle.marks("funcionário; empregado"))
    }
}
