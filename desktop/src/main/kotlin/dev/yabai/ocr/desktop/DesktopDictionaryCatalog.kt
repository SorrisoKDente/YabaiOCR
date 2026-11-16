package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings

object DesktopDictionaryCatalog {

    enum class Kind { TERMS, KANJI }

    data class Entry(
        val id: String,
        val namePt: String,
        val nameEn: String,
        val descPt: String,
        val descEn: String,
        val language: String,
        val sizeLabel: String,
        val kind: Kind,
        val url: String? = null,
        val indexUrl: String? = null,
        val attributionPt: String,
        val attributionEn: String,
    ) {
        val name: String get() = if (DesktopStrings.isEnglish) nameEn else namePt
        val description: String get() = if (DesktopStrings.isEnglish) descEn else descPt
        val attribution: String get() = if (DesktopStrings.isEnglish) attributionEn else attributionPt
    }

    private const val JMDICT_RELEASES =
        "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download"

    private const val WTY_RELEASES =
        "https://github.com/yomidevs/wiktionary-to-yomitan/releases/latest/download"

    private const val SURASURA_URL =
        "https://github.com/MarvNC/yomitan-dictionaries/raw/master/dl/%5BMonolingual%5D%20surasura.zip"

    val entries: List<Entry> = listOf(
        Entry(
            id = "jmdict-en",
            namePt = "JMdict (inglês)",
            nameEn = "JMdict (English)",
            descPt = "O dicionário japonês clássico. Cobre praticamente tudo que aparece em mangá, com leituras, acepções numeradas e marcas de uso.",
            descEn = "The classic Japanese dictionary. Covers practically everything that appears in manga, with readings, numbered senses, and usage marks.",
            language = "en",
            sizeLabel = "15 MB",
            kind = Kind.TERMS,
            url = "$JMDICT_RELEASES/JMdict_english.zip",
            attributionPt = "JMdict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
            attributionEn = "JMdict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
        Entry(
            id = "jitendex",
            namePt = "Jitendex (inglês)",
            nameEn = "Jitendex (English)",
            descPt = "JMdict retrabalhado: definições mais claras, exemplos e marcação de qual acepção se aplica. É o dicionário recomendado pela comunidade.",
            descEn = "Reworked JMdict: clearer definitions, examples, and sense markings. Recommended by the community.",
            language = "en",
            sizeLabel = "37 MB",
            kind = Kind.TERMS,
            url = "https://github.com/stephenmk/stephenmk.github.io/releases/latest/download/jitendex-yomitan.zip",
            attributionPt = "Jitendex by Stephen Kraus (CC BY-SA 4.0)",
            attributionEn = "Jitendex by Stephen Kraus (CC BY-SA 4.0)",
        ),
        Entry(
            id = "ggjz-pt",
            namePt = "Jitendex em português (ggjz.org)",
            nameEn = "Jitendex in Portuguese (ggjz.org)",
            descPt = "O Jitendex com as acepções traduzidas para o português brasileiro, mantendo a cobertura e os exemplos do dicionário original.",
            descEn = "Jitendex with definitions translated to Brazilian Portuguese, maintaining original coverage and examples.",
            language = "pt",
            sizeLabel = "39 MB",
            kind = Kind.TERMS,
            indexUrl = "https://ggjz.org/yomitan-ptbr.json",
            attributionPt = "Jitendex by Stephen Kraus; tradução PT-BR por ggjz.org (CC BY-SA 4.0)",
            attributionEn = "Jitendex by Stephen Kraus; PT-BR translation by ggjz.org (CC BY-SA 4.0)",
        ),
        Entry(
            id = "jmnedict",
            namePt = "JMnedict (nomes próprios)",
            nameEn = "JMnedict (proper names)",
            descPt = "Nomes de pessoas, lugares, empresas e obras. Essencial para nomes de personagens e locais de mangás e jogos.",
            descEn = "Names of people, places, companies, and works. Essential for character names and locations in manga and games.",
            language = "en",
            sizeLabel = "11 MB",
            kind = Kind.TERMS,
            url = "$JMDICT_RELEASES/JMnedict.zip",
            attributionPt = "JMnedict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
            attributionEn = "JMnedict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
                Entry(
            id = "wty-ja-ja",
            namePt = "Wikcionário: japonês (monolíngue)",
            nameEn = "Wiktionary: Japanese (monolingual)",
            descPt = "Definições escritas diretamente em japonês.",
            descEn = "Definitions written directly in Japanese.",
            language = "ja",
            sizeLabel = "12 MB",
            kind = Kind.TERMS,
            indexUrl = "$WTY_RELEASES/kty-ja-ja-index.json",
            attributionPt = "Wikcionário via wiktionary-to-yomitan (CC BY-SA 3.0)",
            attributionEn = "Wiktionary via wiktionary-to-yomitan (CC BY-SA 3.0)",
        ),
                Entry(
            id = "surasura-ja",
            namePt = "Onomatopeias (surasura)",
            nameEn = "Onomatopeias (surasura)",
            descPt = "Onomatopeias e palavras miméticas (ドキドキ, ぴくりぴくり) com explicações detalhadas de uso em mangás.",
            descEn = "Onomatopoeias and mimetic words (ドキドキ, ぴくりぴくり) with detailed explanations of manga usage.",
            language = "ja",
            sizeLabel = "0,1 MB",
            kind = Kind.TERMS,
            url = SURASURA_URL,
            attributionPt = "surasura.com, conversão de MarvNC (licença aberta)",
            attributionEn = "surasura.com, conversion by MarvNC (open license)",
        ),
        Entry(
            id = "kanjidic-en",
            namePt = "KANJIDIC (inglês)",
            nameEn = "KANJIDIC (English)",
            descPt = "Ficha de cada kanji: leituras (ON/KUN), significados e ordem de traços. Cobre cerca de 10 mil caracteres.",
            descEn = "Kanji records: readings (ON/KUN), meanings, and stroke order. Covers about 10,000 characters.",
            language = "en",
            sizeLabel = "0,7 MB",
            kind = Kind.KANJI,
            url = "$JMDICT_RELEASES/KANJIDIC_english.zip",
            attributionPt = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
            attributionEn = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
    )
}
