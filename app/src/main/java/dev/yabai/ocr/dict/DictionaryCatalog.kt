package dev.yabai.ocr.dict

import java.util.Locale

/**
 * Dictionaries the app offers to download by itself.
 *
 * Every URL here was checked by hand and points at a publisher's "latest release" asset, so it keeps
 * working as the dictionaries are updated — the one exception is the onomatopoeia dictionary, which
 * exists only as a file in its maintainer's repository (see [SURASURA_URL]). Nothing is bundled in
 * the APK: the smallest useful dictionary is a tenth of a megabyte and the largest is 37 MB, and
 * shipping them would also mean shipping someone else's licence inside this app.
 *
 * The Wiktionary entries ([indexUrl] instead of [url]) are a two-step download: the publisher only
 * hosts small descriptor files on GitHub, and the archive itself lives behind a URL recorded in the
 * descriptor. Reading the descriptor keeps the app working even if that storage URL changes.
 */
object DictionaryCatalog {

    enum class Kind { TERMS, KANJI }

    data class Entry(
        val id: String,
        val namePt: String,
        val nameEn: String,
        val descPt: String,
        val descEn: String,
        /** BCP-47-ish tag of the definitions: "pt", "en" or "ja". */
        val language: String,
        val sizeLabel: String,
        val kind: Kind,
        val url: String? = null,
        /** Descriptor JSON that carries the real download URL in its `downloadUrl` field. */
        val indexUrl: String? = null,
        val attributionPt: String,
        val attributionEn: String,
    ) {
        val isEnglish: Boolean get() = Locale.getDefault().language.equals("en", ignoreCase = true)
        val name: String get() = if (isEnglish) nameEn else namePt
        val description: String get() = if (isEnglish) descEn else descPt
        val attribution: String get() = if (isEnglish) attributionEn else attributionPt
    }

    private const val JMDICT_RELEASES =
        "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download"

    private const val WTY_RELEASES =
        "https://github.com/yomidevs/wiktionary-to-yomitan/releases/latest/download"

    /**
     * The onomatopoeia dictionary has no release of its own: it is one file inside the maintainer's
     * repository, and that path is the only address it has ever had (checked by hand; the file is
     * still there). It is 0,1 MB, so a stale copy costs nothing to replace.
     */
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
            descPt = "JMdict retrabalhado: definições mais claras, exemplos e marcação de qual acepção se aplica. É o dicionário que a comunidade costuma recomendar hoje.",
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
            descPt = "O Jitendex com as acepções traduzidas para o português brasileiro, mantendo a cobertura e os exemplos do dicionário original. É a melhor opção para ler a definição em português sem abrir mão do JMdict.",
            descEn = "Jitendex with definitions translated to Brazilian Portuguese, maintaining original coverage and examples. Best option to read definitions in Portuguese without giving up JMdict.",
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
            descPt = "Nomes de pessoas, lugares, empresas e obras. Em mangá aparece nome o tempo todo e o JMdict comum não tem quase nenhum.",
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
            descPt = "Definições escritas em japonês. Só vale a pena se você já lê japonês; para começar, prefira um dicionário em português ou inglês.",
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
            nameEn = "Onomatopoeias (surasura)",
            descPt = "Só onomatopeias e palavras miméticas (ドキドキ, ぴくりぴくり), com uma explicação mais longa e exemplos. O JMdict e o Jitendex cobrem boa parte delas, mas não todas — e as que faltam são justamente as que aparecem em mangá. As definições são em japonês.",
            descEn = "Onomatopoeias and mimetic words (ドキドキ, ぴくりぴくり) with detailed explanations of manga usage.",
            language = "ja",
            sizeLabel = "0,1 MB",
            kind = Kind.TERMS,
            url = "$SURASURA_URL",
            attributionPt = "surasura.com, conversão de MarvNC (licença não declarada)",
            attributionEn = "surasura.com, conversion by MarvNC (open license)",
        ),
        Entry(
            id = "kanjidic-en",
            namePt = "KANJIDIC (inglês)",
            nameEn = "KANJIDIC (English)",
            descPt = "Ficha de cada kanji: leituras, significados e série escolar. Cobre cerca de 10 mil caracteres, incluindo os raros.",
            descEn = "Kanji records: readings (ON/KUN), meanings, and stroke order. Covers about 10,000 characters.",
            language = "en",
            sizeLabel = "0,7 MB",
            kind = Kind.KANJI,
            url = "$JMDICT_RELEASES/KANJIDIC_english.zip",
            attributionPt = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
            attributionEn = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
        Entry(
            id = "kanjidic-pt",
            namePt = "KANJIDIC (português)",
            nameEn = "KANJIDIC (Portuguese)",
            descPt = "Significados dos kanji em português, mas só para os cerca de 2 mil kanji usuais. Kanji raro (como 淀) só aparece na versão em inglês.",
            descEn = "Kanji meanings in Portuguese, but only for the ~2,000 common kanji.",
            language = "pt",
            sizeLabel = "0,3 MB",
            kind = Kind.KANJI,
            url = "$JMDICT_RELEASES/KANJIDIC_portuguese.zip",
            attributionPt = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
            attributionEn = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
    )

    fun find(id: String): Entry? = entries.firstOrNull { it.id == id }
}
