package dev.yabai.ocr.core.anki

import dev.yabai.ocr.core.dict.Furigana

/**
 * The HTML of a card: escape, ruby and the plain-text twin used when there is no Anki to talk to.
 *
 * Two rules come from the picker's readings and must not be softened here: a reading that does not
 * line up with the characters is not written, and the ruby goes over the word **as the sentence
 * prints it** — 食べました, not 食べる — because the card is about the text the user saw.
 */
object AnkiHtml {

    /** `& < > "`, in the order that keeps an escape from escaping another. */
    fun escape(text: String): String = buildString(text.length + 8) {
        for (character in text) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(character)
            }
        }
    }

    /**
     * [surface] with a `<ruby>` over each kanji run the dictionary reading accounts for, or null
     * when the reading cannot be pinned to the surface.
     *
     * [split] works on the dictionary form ([term]); the run's kanji is searched in [surface] in
     * order, so an inflected word still gets its ruby (食 in 食べました). A run that is not in the
     * surface — an old form such as 兩膝 whose entry is 両膝 — makes the whole word go without ruby:
     * annotating a character that is not there would teach the wrong reading.
     */
    fun ruby(surface: String, term: String, reading: String): String? {
        if (surface.isEmpty() || reading.isBlank()) return null
        val runs = Furigana.split(term, reading) ?: return null
        if (runs.none { it.reading.isNotBlank() }) return null

        val out = StringBuilder(surface.length + 24)
        var cursor = 0
        for (run in runs) {
            if (run.reading.isBlank()) continue
            val at = surface.indexOf(run.kanji, cursor)
            if (at < 0) return null
            out.append(escape(surface.substring(cursor, at)))
            out.append("<ruby>").append(escape(run.kanji))
            out.append("<rt>").append(escape(run.reading)).append("</rt></ruby>")
            cursor = at + run.kanji.length
        }
        out.append(escape(surface.substring(cursor)))
        return out.toString()
    }

    /** A definition escaped with one sense per line: the body both cards print. */
    fun lines(definition: String): String = escape(definition.trim()).replace("\n", "<br>")

    /** The definition as the word card shows it: escaped, one sense per line, with the tags last. */
    fun definition(definition: String, tags: String = ""): String {
        val body = lines(definition)
        val tagLine = tags.trim()
        return if (tagLine.isBlank()) body
        else body + (if (body.isBlank()) "" else "<br>") + "<i>" + escape(tagLine) + "</i>"
    }

    /**
     * [text] escaped with `text[start, end)` in bold — the word inside the sentence.
     *
     * An out-of-range pair is ignored (a match whose offsets do not belong to this text), and the
     * sentence is still written whole.
     */
    fun boldAt(text: String, start: Int, end: Int): String {
        if (start < 0 || end <= start || end > text.length) return escape(text)
        return escape(text.substring(0, start)) +
            "<b>" + escape(text.substring(start, end)) + "</b>" +
            escape(text.substring(end))
    }

    /** The card without markup, for the clipboard and for sharing a note with no collector. */
    fun plain(note: AnkiNote): String {
        val out = StringBuilder()
        for (name in AnkiFields.ORDER) {
            val value = note.fields[name]?.let(::strip).orEmpty().trim()
            if (value.isBlank()) continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(value)
        }
        return out.toString()
    }

    /** Removes tags and turns the entity escapes back into characters. */
    private fun strip(html: String): String {
        var text = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        val entities = listOf(
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'",
            "&amp;" to "&",
        )
        for ((entity, character) in entities) text = text.replace(entity, character)
        return text
    }
}
