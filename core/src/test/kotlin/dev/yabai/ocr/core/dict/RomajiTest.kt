package dev.yabai.ocr.core.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O conversor Hepburn romaji → hiragana (R31 + romaji). */
class RomajiTest {

    @Test
    fun `vogais sozinhas`() {
        assertEquals("あいうえお", Romaji.toHiragana("aiueo"))
    }

    @Test
    fun `sílabas k`() {
        assertEquals("かきくけこ", Romaji.toHiragana("kakikukeko"))
    }

    @Test
    fun `dakuten`() {
        assertEquals("がぎぐげご", Romaji.toHiragana("gagigugego"))
    }

    @Test
    fun `shi chi tsu fu`() {
        assertEquals("しちつふ", Romaji.toHiragana("shichitsufu"))
    }

    @Test
    fun `dígrafos kya kyu kyo`() {
        assertEquals("きゃ きゅ きょ", Romaji.toHiragana("kya kyu kyo"))
    }

    @Test
    fun `dígrafos sha shu sho`() {
        assertEquals("しゃ しゅ しょ", Romaji.toHiragana("sha shu sho"))
    }

    @Test
    fun `consoante dupla sokuon`() {
        assertEquals("がっこう", Romaji.toHiragana("gakkou"))
        assertEquals("べっこ", Romaji.toHiragana("bekko"))
    }

    @Test
    fun `vogal longa com acento`() {
        assertEquals("とうきょう", Romaji.toHiragana("tōkyō"))
        assertEquals("おかあさん", Romaji.toHiragana("okāsan"))
    }

    @Test
    fun `n moraico`() {
        assertEquals("こんにちは", Romaji.toHiragana("konnichiha"))
        assertEquals("ほん", Romaji.toHiragana("hon"))
    }

    @Test
    fun `texto que não é romaji é inalterado`() {
        assertEquals("日本", Romaji.toHiragana("日本"))
        assertEquals("こんにちは!", Romaji.toHiragana("こんにちは!"))
    }

    @Test
    fun `consulta mista romaji e kana`() {
        assertEquals("にほんご", Romaji.toHiragana("nihongo"))
        assertEquals("日本 (にほん)", Romaji.toHiragana("日本 (nihon)"))
    }

    @Test
    fun `isRomaji aceita só latinos com acento`() {
        assertTrue(Romaji.isRomaji("nihon"))
        assertTrue(Romaji.isRomaji("tōkyō"))
        assertTrue(Romaji.isRomaji("nihon go"))
        assertFalse(Romaji.isRomaji("日本"))
        assertFalse(Romaji.isRomaji("にほん"))
        assertFalse(Romaji.isRomaji(""))
        assertFalse(Romaji.isRomaji("   "))
    }
}
