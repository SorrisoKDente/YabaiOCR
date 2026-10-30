package dev.yabai.ocr.core.dict

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The kana readings a typed query may carry (R35d).
 *
 * `職員（しょくいん）` is what a reader shows and what a copy brings along; `職員` is what the
 * dictionary has. The rule is deliberately narrow — kana only, the pair matching, at least one kana
 * — because dropping anything else would be guessing at what the user meant.
 */
class DictionaryQueryTest {

    @Test
    fun `a kana reading in full-width brackets is dropped`() {
        assertEquals("職員さん", DictionaryQuery.stripReadings("職員（しょくいん）さん"))
    }

    @Test
    fun `a kana reading in ascii brackets is dropped too`() {
        assertEquals("職員さん", DictionaryQuery.stripReadings("職員(しょくいん)さん"))
    }

    @Test
    fun `every reading in a sentence-length query is dropped`() {
        assertEquals(
            "職員さんに聞いた",
            DictionaryQuery.stripReadings("職員（しょくいん）さんに聞（き）いた"),
        )
    }

    @Test
    fun `a bracket holding anything but kana stays`() {
        assertEquals("職員（職員）", DictionaryQuery.stripReadings("職員（職員）"))
        assertEquals("猫(cat)", DictionaryQuery.stripReadings("猫(cat)"))
        assertEquals("猫（ねこ1）", DictionaryQuery.stripReadings("猫（ねこ1）"))
        assertEquals("猫（ねこ ま）", DictionaryQuery.stripReadings("猫（ねこ ま）"))
    }

    @Test
    fun `an empty bracket stays`() {
        assertEquals("猫（）", DictionaryQuery.stripReadings("猫（）"))
        assertEquals("猫()", DictionaryQuery.stripReadings("猫()"))
    }

    @Test
    fun `a bracket without its pair stays`() {
        assertEquals("職員（しょくいん", DictionaryQuery.stripReadings("職員（しょくいん"))
        assertEquals("職員しょくいん）", DictionaryQuery.stripReadings("職員しょくいん）"))
        assertEquals("職員（しょくいん)", DictionaryQuery.stripReadings("職員（しょくいん)"))
    }

    @Test
    fun `katakana and the prolonged sound mark count as a reading`() {
        assertEquals("コーヒー", DictionaryQuery.stripReadings("コーヒー（こーひー）"))
        assertEquals("タバコ", DictionaryQuery.stripReadings("タバコ(タバコ)"))
    }

    @Test
    fun `a query that is only a reading becomes empty`() {
        assertEquals("", DictionaryQuery.stripReadings("（しょくいん）"))
    }

    @Test
    fun `text with no brackets is untouched`() {
        assertEquals("職員さんに聞いた。", DictionaryQuery.stripReadings("職員さんに聞いた。"))
        assertEquals("", DictionaryQuery.stripReadings(""))
    }
}
