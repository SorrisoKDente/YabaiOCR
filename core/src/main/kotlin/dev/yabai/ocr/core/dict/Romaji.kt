package dev.yabai.ocr.core.dict

/**
 * Hepburn romaji → hiragana, melhor esforço.
 *
 * O usuário pode digitar `nihon` ou `tokyo` no campo de busca e esperar encontrar 日本 ou 東京.
 * Este objeto converte romaji latino para hiragana para que a consulta possa ser procurada como
 * leitura nos dicionários. Não tenta ser um conversor completo: cobre as sílabas comuns, dígrafos
 * (kya, shu...), consoante dupla (kk → っ), vogais longas (ō → ou, aa → ah...), ん e as series
 * com dakuten/handakuten. Retorna o texto inalterado quando ele não é romaji.
 *
 * Só letras latinas são convertidas; tudo o mais (espaços, pontuação, kana, kanji) é copiado
 * tal como está. Isso permite consultas mistas como `nihon go` ou `東京 (tokyo)`.
 */
object Romaji {

    /** Sílabas básicas e dígrafos: romaji → hiragana. As chaves mais longas são tentadas primeiro. */
    private val SYLLABLES = mapOf(
        // Monossílabas (vogal sozinha)
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        // K series
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        // S series
        "sa" to "さ", "shi" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "za" to "ざ", "ji" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        // T series
        "ta" to "た", "chi" to "ち", "tsu" to "つ", "te" to "て", "to" to "と",
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        // N series
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        // H series
        "ha" to "は", "hi" to "ひ", "fu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        // M series
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        // Y series
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        // R series
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        // W series
        "wa" to "わ", "wo" to "を", "n" to "ん",
        // Dígrafos (consonante + y + vogal)
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
    )

    /** Consoantes que formam sokuon quando dobradas (kk, ss...). n nunca forma sokuon. */
    private val CONSONANTS = setOf('k', 's', 't', 'h', 'm', 'y', 'r', 'w', 'g', 'z', 'j', 'd', 'b', 'p')

    /**
     * Diz se [text] parece romaji: só letras latinas (com ou sem acento de vogal longa), apóstrofo,
     * hífen e espaços, e pelo menos uma letra latina.
     */
    fun isRomaji(text: String): Boolean {
        if (text.isBlank()) return false
        var hasLatin = false
        for (c in text) {
            when {
                c in 'a'..'z' || c in 'A'..'Z' -> hasLatin = true
                c == '’' || c == '\'' || c == '-' || c == ' ' -> Unit
                c in '\u00C0'..'\u024F' -> Unit // acentos latinos (ā, ō, ē...)
                else -> return false
            }
        }
        return hasLatin
    }

    /**
     * Converte [romaji] para hiragana. Caracteres que não são romaji são copiados inalterados.
     *
     * Vogais longas com acento (ā, ō, ū, é, í) são expandidas antes da varredura:
     * `ō → ou`, `ā → aa`, etc. Consoante dupla (kk, ss...) vira `っ` antes da sílaba seguinte.
     */
    fun toHiragana(romaji: String): String {
        val normalized = romaji
            .lowercase()
            .replace("ā", "aa").replace("ē", "ee").replace("ī", "ii").replace("ō", "ou").replace("ū", "uu")
            .replace("â", "aa").replace("ê", "ee").replace("î", "ii").replace("ô", "ou").replace("û", "uu")

        val result = StringBuilder()
        var i = 0
        while (i < normalized.length) {
            val c = normalized[i]

            // Não é letra latina simples: copia tal como está.
            if (c < 'a' || c > 'z') {
                result.append(c)
                i++
                continue
            }

            // Consoante dupla: kk, ss, tt... → っ + próxima sílaba.
            if (c in CONSONANTS && i + 1 < normalized.length && normalized[i + 1] == c) {
                result.append("っ")
                i++ // consome a primeira consoante; a segunda começa a próxima sílaba
                continue
            }

            // Tenta dígrafo de 3 letras (kya, sha...)
            if (i + 2 < normalized.length) {
                val triple = normalized.substring(i, i + 3)
                val hit3 = SYLLABLES[triple]
                if (hit3 != null) {
                    result.append(hit3)
                    i += 3
                    continue
                }
            }

            // Tenta sílaba de 2 letras (shi, chi, ts, fu...)
            if (i + 1 < normalized.length) {
                val pair = normalized.substring(i, i + 2)
                val hit2 = SYLLABLES[pair]
                if (hit2 != null) {
                    result.append(hit2)
                    i += 2
                    continue
                }
            }

            // Tenta sílaba de 1 letra (a, n...)
            val single = normalized.substring(i, i + 1)
            val hit1 = SYLLABLES[single]
            if (hit1 != null) {
                result.append(hit1)
                i += 1
                continue
            }

            // Não reconheceu: copia a letra tal como está.
            result.append(c)
            i++
        }

        return result.toString()
    }
}
