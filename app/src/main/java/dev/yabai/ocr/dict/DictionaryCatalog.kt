package dev.yabai.ocr.dict

/**
 * Dictionaries the app offers to download by itself.
 *
 * Every URL here was checked by hand and points at a publisher's "latest release" asset, so it keeps
 * working as the dictionaries are updated. Nothing is bundled in the APK: the smallest useful
 * dictionary is a third of a megabyte and the largest is 37 MB, and shipping them would also mean
 * shipping someone else's licence inside this app.
 *
 * The Wiktionary entries ([indexUrl] instead of [url]) are a two-step download: the publisher only
 * hosts small descriptor files on GitHub, and the archive itself lives behind a URL recorded in the
 * descriptor. Reading the descriptor keeps the app working even if that storage URL changes.
 */
object DictionaryCatalog {

    enum class Kind { TERMS, KANJI }

    data class Entry(
        val id: String,
        val name: String,
        val description: String,
        /** BCP-47-ish tag of the definitions: "pt", "en" or "ja". */
        val language: String,
        val sizeLabel: String,
        val kind: Kind,
        val url: String? = null,
        /** Descriptor JSON that carries the real download URL in its `downloadUrl` field. */
        val indexUrl: String? = null,
        val attribution: String,
    )

    private const val JMDICT_RELEASES =
        "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download"

    private const val WTY_RELEASES =
        "https://github.com/yomidevs/wiktionary-to-yomitan/releases/latest/download"

    val entries: List<Entry> = listOf(
        Entry(
            id = "jmdict-en",
            name = "JMdict (inglês)",
            description = "O dicionário japonês clássico. Cobre praticamente tudo que aparece em mangá, " +
                "com leituras, acepções numeradas e marcas de uso.",
            language = "en",
            sizeLabel = "15 MB",
            kind = Kind.TERMS,
            url = "$JMDICT_RELEASES/JMdict_english.zip",
            attribution = "JMdict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
        Entry(
            id = "jitendex",
            name = "Jitendex (inglês)",
            description = "JMdict retrabalhado: definições mais claras, exemplos e marcação de qual acepção " +
                "se aplica. É o dicionário que a comunidade costuma recomendar hoje.",
            language = "en",
            sizeLabel = "37 MB",
            kind = Kind.TERMS,
            url = "https://github.com/stephenmk/stephenmk.github.io/releases/latest/download/jitendex-yomitan.zip",
            attribution = "Jitendex by Stephen Kraus (CC BY-SA 4.0)",
        ),
        Entry(
            id = "jmnedict",
            name = "JMnedict (nomes próprios)",
            description = "Nomes de pessoas, lugares, empresas e obras. Em mangá aparece nome o tempo todo " +
                "e o JMdict comum não tem quase nenhum.",
            language = "en",
            sizeLabel = "11 MB",
            kind = Kind.TERMS,
            url = "$JMDICT_RELEASES/JMnedict.zip",
            attribution = "JMnedict, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
        Entry(
            id = "wty-ja-pt",
            name = "Wikcionário: japonês → português",
            description = "Acepções em português, extraídas do Wikcionário lusófono. Menor que o JMdict, " +
                "mas é o único com definições em português.",
            language = "pt",
            sizeLabel = "0,4 MB",
            kind = Kind.TERMS,
            indexUrl = "$WTY_RELEASES/kty-ja-pt-index.json",
            attribution = "Wikcionário via wiktionary-to-yomitan (CC BY-SA 3.0)",
        ),
        Entry(
            id = "wty-ja-ja",
            name = "Wikcionário: japonês (monolíngue)",
            description = "Definições escritas em japonês. Só vale a pena se você já lê japonês; para " +
                "começar, prefira um dicionário em português ou inglês.",
            language = "ja",
            sizeLabel = "12 MB",
            kind = Kind.TERMS,
            indexUrl = "$WTY_RELEASES/kty-ja-ja-index.json",
            attribution = "Wikcionário via wiktionary-to-yomitan (CC BY-SA 3.0)",
        ),
        Entry(
            id = "wty-ja-en",
            name = "Wikcionário: japonês → inglês",
            description = "Acepções em inglês vindas do Wikcionário. Complementa o JMdict em gírias e " +
                "palavras novas que ainda não entraram no dicionário principal.",
            language = "en",
            sizeLabel = "21 MB",
            kind = Kind.TERMS,
            indexUrl = "$WTY_RELEASES/kty-ja-en-index.json",
            attribution = "Wikcionário via wiktionary-to-yomitan (CC BY-SA 3.0)",
        ),
        Entry(
            id = "kanjidic-en",
            name = "KANJIDIC (inglês)",
            description = "Ficha de cada kanji: leituras, significados e série escolar. Cobre cerca de " +
                "10 mil caracteres, incluindo os raros.",
            language = "en",
            sizeLabel = "0,7 MB",
            kind = Kind.KANJI,
            url = "$JMDICT_RELEASES/KANJIDIC_english.zip",
            attribution = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
        Entry(
            id = "kanjidic-pt",
            name = "KANJIDIC (português)",
            description = "Significados dos kanji em português, mas só para os cerca de 2 mil kanji " +
                "usuais. Kanji raro (como 淀) só aparece na versão em inglês.",
            language = "pt",
            sizeLabel = "0,3 MB",
            kind = Kind.KANJI,
            url = "$JMDICT_RELEASES/KANJIDIC_portuguese.zip",
            attribution = "KANJIDIC, Electronic Dictionary Research and Development Group (CC BY-SA 4.0)",
        ),
    )

    fun find(id: String): Entry? = entries.firstOrNull { it.id == id }
}
