package dev.yabai.ocr.core.manga

/**
 * Tokenizer for Manga-OCR transformer output decoding.
 *
 * Maps output token IDs (logits argmax) to Japanese characters according to the vocabulary dictionary.
 */
class MangaOcrTokenizer(
    private val vocab: List<String>,
    private val padTokenId: Int = 0,
    private val bosTokenId: Int = 1,
    private val eosTokenId: Int = 2,
    private val unkTokenId: Int = 3,
) {
    /**
     * Decodes a list of token IDs into a Japanese text string.
     */
    fun decode(tokenIds: List<Int>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            if (id == eosTokenId) break
            if (id == padTokenId || id == bosTokenId || id == unkTokenId) continue

            if (id in vocab.indices) {
                val token = vocab[id]
                if (!isSpecialToken(token)) {
                    sb.append(cleanToken(token))
                }
            }
        }
        return sb.toString()
    }

    private fun isSpecialToken(token: String): Boolean {
        return token == "<s>" || token == "</s>" || token == "<unk>" || token == "<pad>"
    }

    private fun cleanToken(token: String): String {
        // Strip sentencepiece/BERT space prefix if present (e.g. ' ')
        return token.replace(" ", "").replace("##", "")
    }

    companion object {
        fun fromLines(lines: List<String>): MangaOcrTokenizer {
            val vocab = lines.map { it.trim() }
            return MangaOcrTokenizer(vocab)
        }
    }
}
