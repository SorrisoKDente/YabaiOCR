package dev.yabai.ocr.core.manga

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The decoder loop of manga-ocr.
 *
 * The model is an encoder-decoder: one call answers with the logits of the tokens fed so far, and
 * the text is what comes out of feeding each answer back. The recogniser was calling it once with a
 * single token and taking that argmax — usually `</s>`, which decodes to nothing at all.
 *
 * The model is scripted here on purpose: the rules under test are *when* the loop asks again, what
 * it feeds back and when it stops, none of which needs a 118 MB file to be wrong.
 */
class MangaOcrDecodingTest {

    @Test
    fun `every answer is fed back until the model says the line ended`() {
        val answers = listOf(5, 6, 7, MangaOcrDecoding.EOS_TOKEN_ID).iterator()
        val asked = mutableListOf<List<Long>>()

        val tokens = MangaOcrDecoding.generate { tokens ->
            asked += tokens.toList()
            answers.next()
        }

        assertEquals(listOf(5, 6, 7), tokens)
        assertEquals(
            listOf(
                listOf(MangaOcrDecoding.START_TOKEN_ID),
                listOf(MangaOcrDecoding.START_TOKEN_ID, 5L),
                listOf(MangaOcrDecoding.START_TOKEN_ID, 5L, 6L),
                listOf(MangaOcrDecoding.START_TOKEN_ID, 5L, 6L, 7L),
            ),
            asked,
        )
    }

    @Test
    fun `the end token is not part of the text`() {
        val answers = listOf(5, MangaOcrDecoding.EOS_TOKEN_ID).iterator()

        val tokens = MangaOcrDecoding.generate { answers.next() }

        assertEquals(listOf(5), tokens)
    }

    @Test
    fun `a model that cannot answer stops the loop instead of spinning`() {
        var calls = 0
        val tokens = MangaOcrDecoding.generate {
            calls += 1
            null
        }

        assertEquals(emptyList<Int>(), tokens)
        assertEquals(1, calls)
    }

    @Test
    fun `the loop is bounded even when the model never ends the line`() {
        var calls = 0
        val tokens = MangaOcrDecoding.generate {
            calls += 1
            5
        }

        assertEquals(MangaOcrDecoding.MAX_NEW_TOKENS, tokens.size)
        assertEquals(MangaOcrDecoding.MAX_NEW_TOKENS, calls)
    }
}
