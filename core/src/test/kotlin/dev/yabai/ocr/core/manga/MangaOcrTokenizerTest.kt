package dev.yabai.ocr.core.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class MangaOcrTokenizerTest {

    @Test
    fun `decodes Japanese tokens and strips special tokens`() {
        val vocab = listOf("<pad>", "<s>", "</s>", "<unk>", "私", "は", "猫", "です")
        val tokenizer = MangaOcrTokenizer(vocab)

        // Token IDs: <s> (1), 私 (4), は (5), 猫 (6), です (7), </s> (2)
        val tokenIds = listOf(1, 4, 5, 6, 7, 2)
        val decoded = tokenizer.decode(tokenIds)

        assertEquals("私は猫です", decoded)
    }

    @Test
    fun `decodes sentencepiece spaces and BERT prefixes`() {
        val vocab = listOf("<pad>", "<s>", "</s>", "<unk>", " 戦", "##う", " 術")
        val tokenizer = MangaOcrTokenizer(vocab)

        val tokenIds = listOf(4, 5, 6)
        val decoded = tokenizer.decode(tokenIds)

        assertEquals("戦う術", decoded)
    }

    @Test
    fun `stops decoding upon encountering EOS token`() {
        val vocab = listOf("<pad>", "<s>", "</s>", "<unk>", "あ", "い", "う")
        val tokenizer = MangaOcrTokenizer(vocab)

        // IDs: あ (4), </s> (2), い (5), う (6) -> should stop at </s>
        val tokenIds = listOf(4, 2, 5, 6)
        val decoded = tokenizer.decode(tokenIds)

        assertEquals("あ", decoded)
    }
}
