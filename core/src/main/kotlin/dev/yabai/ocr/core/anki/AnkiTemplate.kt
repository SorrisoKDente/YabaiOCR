package dev.yabai.ocr.core.anki

/**
 * The one card template of the `Yabai` note type.
 *
 * Either platform can create the note type — AnkiConnect on the desktop, AnkiDroid on the phone —
 * so both have to write the same template; otherwise the same deck would get two different card
 * layouts depending on where the card was made.
 */
object AnkiTemplate {
    const val NAME = "Card 1"
    const val FRONT = "{{Palavra}}"
    const val BACK =
        "{{FrontSide}}<hr id=answer>{{Leitura}}<br>{{Significado}}<br>{{Frase}}<br><small>{{Fonte}}</small>"
}
