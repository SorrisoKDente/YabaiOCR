package dev.yabai.ocr.overlay

import dev.yabai.ocr.core.dict.GlossaryRenderer

/**
 * The marks the dictionary renderer leaves in a definition (R19), turned into ranges the panel can
 * style.
 *
 * The renderer writes a small line grammar because a compiled dictionary is a plain string: a sense
 * starts with `1. `, a note with `ℹ`, an example with `例` and its translation with `→`. The panel
 * reads that grammar back here, in one place, so the drawing code only has to paint ranges.
 */
object DefinitionStyle {

    enum class Kind {
        /** The `1. ` in front of a sense: bold, so the eye can count the senses. */
        NUMBER,

        /** A note, an example or a translation: smaller, greyer, indented under its sense. */
        SECONDARY,
    }

    data class Mark(val start: Int, val end: Int, val kind: Kind)

    /** A sense's number: the digits and the dot, not the definition that follows. */
    private val SENSE_NUMBER = Regex("^\\d+\\. ")

    private val SECONDARY_MARKS = listOf(
        GlossaryRenderer.NOTE_MARK,
        GlossaryRenderer.EXAMPLE_MARK,
        GlossaryRenderer.TRANSLATION_MARK,
        "≠ ",
        "≈ ",
    )

    fun isSecondary(line: String): Boolean = SECONDARY_MARKS.any { line.startsWith(it) }

    /**
     * Every range of [text] worth styling. Offsets are characters, not bytes, so they stay right for
     * Japanese definitions; lines are split on `\n` exactly as the panel draws them.
     */
    fun marks(text: String): List<Mark> {
        val marks = ArrayList<Mark>()
        var start = 0
        for (line in text.split('\n')) {
            val end = start + line.length
            if (isSecondary(line)) marks += Mark(start, end, Kind.SECONDARY)
            val sense = SENSE_NUMBER.find(line)
            if (sense != null) marks += Mark(start, start + sense.value.length, Kind.NUMBER)
            start = end + 1
        }
        return marks
    }
}
