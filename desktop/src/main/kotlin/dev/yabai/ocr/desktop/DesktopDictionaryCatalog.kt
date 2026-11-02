package dev.yabai.ocr.desktop

object DesktopDictionaryCatalog {

    enum class Kind { TERMS, KANJI }

    data class Entry(
        val id: String,
        val name: String,
        val description: String,
        val language: String,
        val sizeLabel: String,
        val kind: Kind,
        val url: String? = null,
        val indexUrl: String? = null,
        val attribution: String,
    )

    private const val JMDICT_RELEASES =
        "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download"

    private const val WTY_RELEASES =
        "https://github.com/yomidevs/wiktionary-to-yomitan/releases/latest/download"

    private const val SURASURA_URL =
        "https://github.com/MarvNC/yomitan-dictionaries/raw/master/dl/%5BMonolingual%5D%20surasura.zip"

    val entries: List<Entry> = listOf(
        Entry(
            id = "jmdict-en",
            name = "JMdict (inglês)",
            description = "O dicionário japonês clássico. Cobre praticamente tudo que aparece em mangá, com leituras, acepções numeradas e marcas de uso.",
            language = "en",
            sizeLabel = "15 MB",
            kind = Kind.TERMS,
            url = "$JMDICT_RELEASES/JMdict_english.zip",
            attribution = "JMdict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
        Entry(
            id = "jitendex",
            name = "Jitendex (inglês)",
            description = "JMdict retrabalhado: definições mais claras, exemplos e marcação de qual acepção se aplica.",
            language = "en",
            sizeLabel = "37 MB",
            kind = Kind.TERMS,
            url = "https://github.com/stephenmk/stephenmk.github.io/releases/latest/download/jitendex-yomitan.zip",
            attribution = "Jitendex by Stephen Kraus (CC BY-SA 4.0)",
        ),
        Entry(
            id = "ggjz-pt",
            name = "Jitendex em português (ggjz.org)",
            description = "O Jitendex com as acepções traduzidas para o português brasileiro. A melhor opção para ler definições em português.",
            language = "pt",
            sizeLabel = "39 MB",
            kind = Kind.TERMS,
            indexUrl = "https://ggjz.org/yomitan-ptbr.json",
            attribution = "Jitendex by Stephen Kraus; tradução PT-BR por ggjz.org (CC BY-SA 4.0)",
        ),
        Entry(
            id = "jmnedict",
            name = "JMnedict (nomes próprios)",
            description = "Nomes de pessoas, lugares, empresas e obras.",
            language = "en",
            sizeLabel = "11 MB",
            kind = Kind.TERMS,
            url = "$JMDICT_RELEASES/JMnedict.zip",
            attribution = "JMnedict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
        Entry(
            id = "kanjidic-en",
            name = "KANJIDIC (inglês)",
            description = "Ficha de cada kanji: leituras (ON/KUN), significados e ordem de traços.",
            language = "en",
            sizeLabel = "0,7 MB",
            kind = Kind.KANJI,
            url = "$JMDICT_RELEASES/KANJIDIC_english.zip",
            attribution = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
    )
}
