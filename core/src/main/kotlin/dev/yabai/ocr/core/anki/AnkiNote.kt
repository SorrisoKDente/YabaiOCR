package dev.yabai.ocr.core.anki

import dev.yabai.ocr.core.dict.TokenMatch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The fields of the `Yabai` note type, in the order the Anki shows them.
 *
 * The names are part of the note type that the desktop client creates (and that the card template
 * refers to), so they are data here, not strings spread through the two senders.
 */
object AnkiFields {
    const val WORD = "Palavra"
    const val READING = "Leitura"
    const val MEANING = "Significado"
    const val SENTENCE = "Frase"
    const val SOURCE = "Fonte"

    val ORDER = listOf(WORD, READING, MEANING, SENTENCE, SOURCE)
}

/**
 * One card, already rendered, ready to be handed to Anki or AnkiDroid.
 *
 * The card is the same concept on both platforms, so the model lives in `:core`; what differs is
 * only the adapter that submits it. HTML fields are produced here — with the ruby on the word — so
 * the two adapters cannot disagree about what a card looks like.
 */
data class AnkiNote(
    val fields: Map<String, String>,
    val tags: List<String> = emptyList(),
) {
    /** The word field; it is what makes two cards the same card. */
    val firstField: String get() = fields[AnkiFields.WORD].orEmpty()

    fun field(name: String): String = fields[name].orEmpty()

    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        /** The date the card was made, as the `Fonte` field prints it. */
        fun today(): String = LocalDate.now().format(DATE)

        /**
         * A card for one word of a sentence.
         *
         * [sentence] is the recognized text the match came from; [date] is injected so the test does
         * not depend on the clock.
         */
        fun word(
            match: TokenMatch,
            sentence: String,
            date: String = today(),
        ): AnkiNote {
            val entry = match.entry
            val surface = match.surface.ifBlank { entry.term }
            val fields = linkedMapOf(
                AnkiFields.WORD to (AnkiHtml.ruby(surface, entry.term, entry.reading)
                    ?: AnkiHtml.escape(surface)),
                AnkiFields.READING to AnkiHtml.escape(entry.reading),
                AnkiFields.MEANING to AnkiHtml.definition(entry.definition, entry.tags),
                AnkiFields.SENTENCE to AnkiHtml.boldAt(sentence, match.start, match.end),
                AnkiFields.SOURCE to AnkiHtml.escape(source(match.dictionaryTitle, date)),
            )
            return AnkiNote(fields)
        }

        /**
         * A card for the whole recognized sentence, with the first matched word in bold.
         *
         * It keeps the context the word appeared in, which is the point of a sentence card; the
         * meanings are the ones already on screen, at most three of them.
         */
        fun sentence(
            text: String,
            matches: List<TokenMatch>,
            date: String = today(),
        ): AnkiNote {
            val first = matches.minByOrNull { it.start }
            val meaning = matches
                .map { it.entry.definition }
                .filter { it.isNotBlank() }
                .distinct()
                .take(3)
                .joinToString("<br><br>") { AnkiHtml.lines(it) }
            val dictionaries = matches
                .map { it.dictionaryTitle }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")

            val fields = linkedMapOf(
                AnkiFields.WORD to (
                    first?.let { AnkiHtml.ruby(it.surface, it.entry.term, it.entry.reading) }
                        ?: ""
                    ),
                AnkiFields.READING to AnkiHtml.escape(first?.entry?.reading.orEmpty()),
                AnkiFields.MEANING to meaning,
                AnkiFields.SENTENCE to AnkiHtml.boldAt(
                    text,
                    first?.start ?: -1,
                    first?.end ?: -1,
                ),
                AnkiFields.SOURCE to AnkiHtml.escape(source(dictionaries, date)),
            )
            return AnkiNote(fields)
        }

        private fun source(dictionary: String, date: String): String =
            listOf(dictionary.trim(), date).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
