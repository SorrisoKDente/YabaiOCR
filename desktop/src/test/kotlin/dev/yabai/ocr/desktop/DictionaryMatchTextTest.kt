package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.DictionaryEntry
import dev.yabai.ocr.core.dict.TokenMatch
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The line the hover and the result window draw under a headword (R46e).
 *
 * 兩膝 was read, 両膝's meanings are on screen. The line is the only thing that joins them: without
 * it the card answers about characters nobody asked about. The same line has to appear in both
 * windows, so it comes from one place.
 */
class DictionaryMatchTextTest {

    @After
    fun restoreLanguage() {
        DesktopStrings.updateLanguage(DesktopLanguageChoice.SYSTEM)
    }

    private fun match(
        surface: String,
        term: String,
        oldForm: Boolean = false,
    ) = TokenMatch(
        surface = surface,
        start = 0,
        end = surface.length,
        entry = DictionaryEntry(
            term = term,
            reading = "りょうひざ",
            tags = "substantivo",
            rulesFlags = 0,
            score = 200,
            definition = "1. ambos os joelhos",
        ),
        dictionaryTitle = "Jitendex PT-BR",
        reasons = emptyList(),
        oldForm = oldForm,
    )

    @Test
    fun `an old form names both words, in the interface language`() {
        DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
        assertEquals("兩膝 → 両膝 · forma antiga", dictionaryFormLine(match("兩膝", "両膝", oldForm = true)))

        DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
        assertEquals("兩膝 → 両膝 · old form", dictionaryFormLine(match("兩膝", "両膝", oldForm = true)))
    }

    /** A deinflected verb also differs from its entry, but nothing about it is old. */
    @Test
    fun `a deinflected word keeps the pair without the old-form mark`() {
        DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
        assertEquals("食べました → 食べる", dictionaryFormLine(match("食べました", "食べる")))
    }

    @Test
    fun `the word that was read and answered is not repeated`() {
        assertNull(dictionaryFormLine(match("両膝", "両膝")))
    }
}
