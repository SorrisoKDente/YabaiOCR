package dev.yabai.ocr.core.jp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseTextTest {

    @Test
    fun `removes spaces between japanese characters`() {
        assertEquals("29巻きっとまいく!", JapaneseText.normalize("29巻 きっとまいく!"))
        assertEquals("淀みなき拳で", JapaneseText.normalize("淀 みなき 拳で"))
    }

    @Test
    fun `keeps spaces between latin words`() {
        assertEquals("STORY by ONE & ART by YUSUKE MUR.", JapaneseText.normalize("STORY by ONE & ART by YUSUKE MUR."))
    }

    @Test
    fun `keeps the space between latin and japanese`() {
        assertEquals("ONE の作品", JapaneseText.normalize("ONE の作品"))
    }

    @Test
    fun `collapses long space runs`() {
        assertEquals("あいう", JapaneseText.normalize("あい   う"))
        assertEquals("hello world", JapaneseText.normalize("hello     world"))
    }

    @Test
    fun `recognises cjk ranges`() {
        assertTrue(JapaneseText.isCjk('淀'))
        assertTrue(JapaneseText.isCjk('み'))
        assertTrue(JapaneseText.isCjk('ア'))
        assertTrue(JapaneseText.isCjk('。'))
        assertTrue(!JapaneseText.isCjk('A'))
        assertTrue(!JapaneseText.isCjk('7'))
    }

    @Test
    fun `tells kana from kanji`() {
        // A reading is kana and the characters it explains are kanji; that is the whole of the test.
        assertTrue(JapaneseText.isKanaOnly("しょくいん"))
        assertTrue(JapaneseText.isKanaOnly("ネオ"))
        assertTrue(JapaneseText.isKanaOnly("コーヒー"))
        assertTrue(!JapaneseText.isKanaOnly("職員"))
        assertTrue(!JapaneseText.isKanaOnly("職員さん"))
        assertTrue(!JapaneseText.isKanaOnly(""))
        assertTrue(!JapaneseText.isKanaOnly("ストーリー1"))

        assertTrue(JapaneseText.hasKanji("職員さんに聞いた"))
        assertTrue(JapaneseText.hasKanji("淀"))
        assertTrue(!JapaneseText.hasKanji("しょくいん"))
        assertTrue(!JapaneseText.hasKanji("STORY by ONE"))
    }

    @Test
    fun `tidyLines drops blank lines`() {
        assertEquals("a\nb", JapaneseText.tidyLines("  a  \n\n   \nb\n"))
    }
}
