package dev.yabai.ocr.core.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Compiles real dictionary archives and queries them.
 *
 * Skipped unless a folder of Yomitan ZIPs is provided, because the fixtures are 15 MB downloads and
 * have no business being committed:
 *
 *     gradle :core:test -Pdicts="C:\path\to\zips"
 */
class DictionaryCompileTest {

    private val zipDir: File? = System.getProperty("yabai.dicts")?.takeIf { it.isNotBlank() }?.let { File(it) }

    private fun zip(name: String): File? {
        val dir = zipDir ?: return null
        return File(dir, name).takeIf { it.isFile }
    }

    private fun tempDir(prefix: String): File =
        kotlin.io.path.createTempDirectory(prefix).toFile()

    private fun compileInto(zipName: String): Pair<CompactDictionary, File>? {
        val source = zip(zipName) ?: return null
        val target = tempDir("yabai-dict-test")
        DictionaryCompiler(source, target, zipName).compile()
        val dictionary = CompactDictionary.open(target)
        assertNotNull("compiled dictionary should open", dictionary)
        return dictionary!! to target
    }

    @Test
    fun `compiles jmdict and finds the one punch man kanji`() {
        val compiled = compileInto("JMdict_english_without_proper_names.zip")
        assumeTrue("JMdict fixture not available", compiled != null)
        compiled!!.first.use { jmdict ->
            println("JMdict compiled: ${jmdict.termCount} terms, ${jmdict.kanjiCount} kanji")
            assertTrue("expected a large dictionary", jmdict.termCount > 100_000)
            assertEquals(
                "a fresh compile must carry the version it was written with",
                DictionaryCompiler.FORMAT_VERSION,
                jmdict.info.compiledWith,
            )

            val yodo = jmdict.termEntries("淀")
            assertFalse("淀 must be in JMdict", yodo.isEmpty())
            println("淀 -> ${yodo.first().reading} : ${yodo.first().definition.take(120)}")

            val neko = jmdict.termEntries("猫")
            assertFalse(neko.isEmpty())
            println("猫 -> ${neko.first().reading} : ${neko.first().definition.take(120)}")

            // A reading-only lookup must also land on the kanji headword.
            val byReading = jmdict.termEntries("たべる")
            assertTrue(byReading.any { it.term == "食べる" })
        }
    }

    @Test
    fun `deinflects conjugated verbs to their dictionary form`() {
        val compiled = compileInto("JMdict_english_without_proper_names.zip")
        assumeTrue("JMdict fixture not available", compiled != null)
        compiled!!.first.use { jmdict ->
            val engine = LookupEngine(listOf(jmdict))

            suspendUnit("食べました", engine) { matches ->
                assertEquals(1, matches.size)
                assertEquals("食べる", matches[0].entry.term)
            }

            suspendUnit("書いた", engine) { matches ->
                assertEquals(1, matches.size)
                assertEquals("書く", matches[0].entry.term)
            }

            suspendUnit("面白くなかった", engine) { matches ->
                assertTrue(
                    "expected 面白い, got ${matches.map { it.entry.term }}",
                    matches.any { it.entry.term == "面白い" },
                )
            }
        }
    }

    @Test
    fun `segments a full sentence`() {
        val compiled = compileInto("JMdict_english_without_proper_names.zip")
        assumeTrue("JMdict fixture not available", compiled != null)
        compiled!!.first.use { jmdict ->
            val engine = LookupEngine(listOf(jmdict))
            val sentence = "私は昨日、友達と一緒に寿司を食べました。"

            val started = System.nanoTime()
            val matches = engine.lookup(sentence)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000

            println("$sentence  ->  ${matches.size} words in ${elapsedMs}ms")
            for (match in matches) {
                println("  ${match.surface} = ${match.entry.term} [${match.reasons.joinToString(">")}]")
            }

            val terms = matches.map { it.entry.term }
            assertTrue("expected 食べる, got $terms", terms.contains("食べる"))
            assertTrue("expected 私, got $terms", terms.contains("私"))
            assertTrue("expected 寿司, got $terms", terms.contains("寿司"))
            assertTrue("segmentation is too slow: ${elapsedMs}ms", elapsedMs < 3_000)
        }
    }

    @Test
    fun `kanji dictionary answers a single kanji lookup`() {
        val compiled = compileInto("KANJIDIC_portuguese.zip")
        assumeTrue("KANJIDIC fixture not available", compiled != null)
        compiled!!.first.use { kanjidic ->
            println("KANJIDIC(pt) compiled: ${kanjidic.termCount} terms, ${kanjidic.kanjiCount} kanji")
            // The Portuguese build only carries the kanji whose meanings were translated, so this
            // is a few thousand, not the full 13k of KANJIDIC.
            assertTrue("expected kanji entries, got ${kanjidic.kanjiCount}", kanjidic.kanjiCount > 1_000)

            val entry = kanjidic.kanjiEntry("猫")
            assertNotNull("猫 must be in KANJIDIC", entry)
            println("猫 ->  ${entry!!.body.replace("\n", " | ")}")

            val engine = LookupEngine(listOf(kanjidic))
            val breakdown = engine.kanjiBreakdown("猫")
            assertEquals(1, breakdown.size)
            assertTrue("breakdown should be empty for a whole sentence", engine.kanjiBreakdown("私は猫だ").isEmpty())

            // 淀 is not a jouyou kanji, so the Portuguese build has no meaning for it. The English
            // build does, which is why both are worth offering.
            assertNull("pt build should not carry 淀", kanjidic.kanjiEntry("淀"))
        }
    }

    @Test
    fun `english kanji dictionary covers rare kanji`() {
        val compiled = compileInto("KANJIDIC_english.zip")
        assumeTrue("KANJIDIC english fixture not available", compiled != null)
        compiled!!.first.use { kanjidic ->
            println("KANJIDIC(en) compiled: ${kanjidic.kanjiCount} kanji")
            assertTrue("expected the full kanji set, got ${kanjidic.kanjiCount}", kanjidic.kanjiCount > 10_000)
            val entry = kanjidic.kanjiEntry("淀")
            assertNotNull("淀 must be in the English KANJIDIC", entry)
            println("淀 ->  ${entry!!.body.replace("\n", " | ")}")
        }
    }

    @Test
    fun `imports the portuguese wiktionary dictionary`() {
        val compiled = compileInto("kty-ja-pt.zip")
        assumeTrue("kty-ja-pt fixture not available", compiled != null)
        compiled!!.first.use { pt ->
            println("kty-ja-pt compiled: ${pt.termCount} terms")
            assertTrue("expected some entries", pt.termCount > 100)

            val engine = LookupEngine(listOf(pt))
            val matches = engine.lookup("本")
            println("本 -> ${matches.map { it.entry.definition.take(80) }}")
            assertFalse("本 should be present in the Portuguese dictionary", matches.isEmpty())
        }
    }

    @Test
    fun `deinflector reaches the dictionary form of a polite past verb`() {
        val deinflector = Deinflector.get()
        val politePast = deinflector.baseForms("食べました")
        println("食べました -> ${politePast.map { "${it.term}(${it.reasons.joinToString(">")})" }}")
        assertTrue(
            "expected 食べる among ${politePast.map { it.term }}",
            politePast.any { it.term == "食べる" },
        )

        val plainPast = deinflector.baseForms("書いた")
        println("書いた -> ${plainPast.map { "${it.term}(${it.reasons.joinToString(">")})" }}")
        assertTrue("expected 書く among ${plainPast.map { it.term }}", plainPast.any { it.term == "書く" })

        val progressive = deinflector.baseForms("食べています")
        println("食べています -> ${progressive.take(12).map { it.term }}")
        assertTrue("expected 食べる", progressive.any { it.term == "食べる" })
    }

    /**
     * Imports every archive the app offers for download. This is the regression that matters: a
     * dictionary that ships in the catalogue but does not compile leaves the user staring at a
     * failure they cannot do anything about.
     */
    @Test
    fun `compiles every dictionary offered in the catalogue`() {
        val offered = listOf(
            "JMdict_english.zip",
            "JMdict_english_without_proper_names.zip",
            "jitendex-yomitan.zip",
            "ggjz-jitendex-ptbr.zip",
            "JMnedict.zip",
            "KANJIDIC_english.zip",
            "KANJIDIC_portuguese.zip",
            "kty-ja-pt.zip",
            "kty-ja-ja.zip",
            "kty-ja-en.zip",
            "surasura.zip",
        )
        var checked = 0
        for (name in offered) {
            val source = zip(name)
            if (source == null) {
                println("skipping $name (not downloaded)")
                continue
            }
            val target = tempDir("yabai-dict-all")
            val started = System.nanoTime()
            val info = try {
                DictionaryCompiler(source, target, name).compile()
            } catch (t: Throwable) {
                throw AssertionError("$name failed to compile", t)
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000

            val sizeMb = target.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024.0 * 1024.0)
            println(
                "%-34s %8d terms %6d kanji  %7.1f MB compiled  %5d ms  [%s]".format(
                    name, info.termCount, info.kanjiCount, sizeMb, elapsedMs, info.title,
                ),
            )
            assertTrue("$name compiled to nothing", info.termCount > 0 || info.kanjiCount > 0)

            // And it must actually answer a lookup, on the JVM, before it is ever packaged.
            //
            // The phone has to hold this index while the panel is open, so measure what loading it
            // really costs. Only the sorted key arrays are resident; definitions stay on disk, which
            // is why these numbers are a fraction of the compiled size above.
            val (resident, engine) = residentBytes {
                val dictionary = CompactDictionary.open(target)
                    ?: throw AssertionError("$name did not reopen")
                LookupEngine(listOf(dictionary))
            }
            val sample = engine.lookup("猫").firstOrNull()
            val label = sample?.entry?.term ?: "—"
            println("  %-34s %6.1f MB resident index   lookup 猫 -> %s".format("", resident / (1024.0 * 1024.0), label))
            assertTrue(
                "$name needs ${resident / (1024 * 1024)} MB of index, too much for a phone",
                resident < MEMORY_BUDGET_BYTES,
            )
            engine.close()
            target.deleteRecursively()
            checked++
        }
        assumeTrue("no dictionary fixtures available", checked > 0)
        println("checked $checked archives")
    }

    /**
     * Heap the [body] leaves behind once the garbage collector has had a go, plus whatever the body
     * returned so the caller can keep it alive while the measurement is taken.
     *
     * This is the number that matters once a dictionary is installed: it is what the app has to keep
     * resident while a result panel is open.
     */
    private fun <T> residentBytes(body: () -> T): Pair<Long, T> {
        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(60)
        val before = runtime.totalMemory() - runtime.freeMemory()
        val value = body()
        System.gc()
        Thread.sleep(60)
        val after = runtime.totalMemory() - runtime.freeMemory()
        return (after - before).coerceAtLeast(0) to value
    }

    private companion object {
        /** A lookup index bigger than this would be a problem on a phone with a small heap. */
        const val MEMORY_BUDGET_BYTES = 80L * 1024 * 1024
    }

    @Test
    fun `jitendex definitions render to readable text`() {
        val compiled = compileInto("jitendex-yomitan.zip")
        assumeTrue("Jitendex fixture not available", compiled != null)
        compiled!!.first.use { jitendex ->
            val entry = jitendex.termEntries("食べる").firstOrNull()
            assertNotNull("食べる must be in Jitendex", entry)
            println("Jitendex 食べる:\n${entry!!.definition.take(400)}")
            assertTrue(
                "Jitendex definition should not be empty markup",
                entry.definition.length > 10 && !entry.definition.contains("\"tag\""),
            )

            val engine = LookupEngine(listOf(jitendex))
            val matches = engine.lookup("彼は毎日りんごを食べます。")
            println("Jitendex segmentation: ${matches.map { "${it.surface}=${it.entry.term}" }}")
            assertTrue(matches.any { it.entry.term == "食べる" })
        }
    }

    @Test
    fun `imports the portuguese jitendex from ggjz`() {
        val compiled = compileInto("ggjz-jitendex-ptbr.zip")
        assumeTrue("ggjz fixture not available", compiled != null)
        compiled!!.first.use { pt ->
            println("Jitendex PT-BR compiled: ${pt.termCount} terms, title «${pt.info.title}»")
            assertTrue("expected the whole Jitendex word list, got ${pt.termCount}", pt.termCount > 200_000)
            assertTrue(
                "this must be the translated build, not the English one: ${pt.info.title}",
                pt.info.title.startsWith("Jitendex PT-BR"),
            )

            val japan = pt.termEntries("日本").firstOrNull()
            assertNotNull("日本 must be in the dictionary", japan)
            println("日本 -> ${japan!!.reading} : ${japan.definition.take(160)}")
            assertTrue(
                "the definition is not the Portuguese one: ${japan.definition.take(160)}",
                japan.definition.contains("Japão"),
            )

            // R42: the real build puts the tags and the senses directly inside the sense group, and
            // the old renderer numbered the part of speech as if it were a meaning.
            val keiken = pt.termEntries("経験").firstOrNull()
            assertNotNull("経験 must be in the dictionary", keiken)
            println("経験 -> tags=${keiken!!.tags} | ${keiken.definition.take(200)}")
            assertTrue(
                "the part of speech must be a tag, not a sense: ${keiken.tags}",
                keiken.tags.contains("substantivo") && keiken.tags.contains("transitivo"),
            )
            assertTrue(
                "the first sense is the meaning: ${keiken.definition.take(120)}",
                keiken.definition.startsWith("1. experiência"),
            )
            assertFalse(
                "part of speech leaked into the definition: ${keiken.definition.take(120)}",
                keiken.definition.contains("substantivo"),
            )

            // R42: the cross-reference is a link the panel cannot follow, so it is removed whole.
            val giseigo = pt.termEntries("擬声語").firstOrNull()
            assertNotNull("擬声語 must be in the dictionary", giseigo)
            println("擬声語 -> tags=${giseigo!!.tags} | ${giseigo.definition.take(240)}")
            assertTrue(
                "the field tag must be a tag: ${giseigo.tags}",
                giseigo.tags.contains("linguística"),
            )
            assertTrue(
                "the first sense is the meaning: ${giseigo.definition.take(120)}",
                giseigo.definition.startsWith("1. onomatopeia"),
            )
            assertFalse(
                "the xref label is not a meaning: ${giseigo.definition}",
                giseigo.definition.contains("Ver também"),
            )
            assertFalse(
                "the linked gloss is not a meaning: ${giseigo.definition}",
                giseigo.definition.contains("palavra onomatopaica"),
            )

            val engine = LookupEngine(listOf(pt))
            val matches = engine.lookup("私は日本語を勉強します。")
            println("PT-BR segmentation: ${matches.map { "${it.surface}=${it.entry.term}" }}")
            assertTrue(
                "expected 日本語, got ${matches.map { it.entry.term }}",
                matches.any { it.entry.term == "日本語" },
            )
        }
    }

    /**
     * R31: the search box's suggestions.
     *
     * A real dictionary is the only honest fixture here: the suggestion path walks the compiled key
     * index, where headwords and readings share one sorted list. 日本 has to offer other words that
     * begin that way, and the query itself is already the answer above the row.
     */
    @Test
    fun `suggestions follow a typed prefix in headwords and readings`() {
        val compiled = compileInto("ggjz-jitendex-ptbr.zip")
        assumeTrue("ggjz fixture not available", compiled != null)
        compiled!!.first.use { pt ->
            val engine = LookupEngine(listOf(pt))

            val suggestions = engine.suggest("日本")
            println("日本 -> $suggestions")
            assertTrue("expected 日本語 among $suggestions", suggestions.contains("日本語"))
            assertTrue("expected 日本人 among $suggestions", suggestions.contains("日本人"))
            assertFalse("the query is the result, not a suggestion", suggestions.contains("日本"))

            val limited = engine.suggest("日本", maxSuggestions = 3)
            assertEquals("the answer size was not respected: $limited", 3, limited.size)
            assertEquals("the answer has no duplicates: $limited", limited.distinct(), limited)

            // A reading is a key as well: typing kana reaches the same compounds, while 日本 itself
            // is the answer the search box already shows above the suggestions.
            val byReading = engine.suggest("にほん")
            println("にほん -> $byReading")
            assertTrue("expected 日本語 among $byReading", byReading.contains("日本語"))
            assertFalse("the exact reading is the answer, not a suggestion", byReading.contains("日本"))

            assertTrue("nothing can be suggested for an empty query", engine.suggest("").isEmpty())
        }
    }

    /** R31 + romaji: uma consulta em romaji é convertida para hiragana e encontrada como leitura. */
    @Test
    fun `search finds Japanese words by romaji reading`() {
        val words = compileInto("JMdict_english_without_proper_names.zip")
        assumeTrue("JMdict fixture not available", words != null)
        words!!.first.use { jmdict ->
            val engine = LookupEngine(listOf(jmdict))

            // nihon → にほん → 日本 (leitura de 日本)
            val nihon = engine.search("nihon")
            println("nihon -> $nihon")
            assertTrue("nihon should find 日本: $nihon", nihon.any { it.entry.term == "日本" })

            // toukyou → とうきょう → 東京 (leitura de 東京, com o longo escrito como ou)
            val tokyo = engine.search("toukyou")
            println("toukyou -> $tokyo")
            assertTrue("toukyou should find 東京: $tokyo", tokyo.any { it.entry.term == "東京" })

            // tōkyō com acento também funciona
            val tokyoAccented = engine.search("tōkyō")
            println("tōkyō -> $tokyoAccented")
            assertTrue("tōkyō should find 東京: $tokyoAccented", tokyoAccented.any { it.entry.term == "東京" })

            // Consulta japonesa direta continua funcionando
            val nihongo = engine.search("日本語")
            assertTrue("日本語 should find 日本語: $nihongo", nihongo.any { it.entry.term == "日本語" })
        }
    }

    /** R31: sugestões incluem correspondência de leitura quando solicitado (romaji). */
    @Test
    fun `suggest with includeReadingMatch finds entries by reading`() {
        val words = compileInto("JMdict_english_without_proper_names.zip")
        assumeTrue("JMdict fixture not available", words != null)
        words!!.first.use { jmdict ->
            val engine = LookupEngine(listOf(jmdict))

            // Sem includeReadingMatch: 日本 não aparece porque a leitura é igual à consulta
            val without = engine.suggest("にほん")
            assertFalse("日本 should not be a suggestion without includeReadingMatch: $without",
                without.contains("日本"))

            // Com includeReadingMatch: 日本 aparece porque a leitura corresponde
            val withReading = engine.suggest("にほん", includeReadingMatch = true)
            assertTrue("日本 should be a suggestion with includeReadingMatch: $withReading",
                withReading.contains("日本"))
            assertTrue("日本語 should be among suggestions: $withReading",
                withReading.contains("日本語"))
        }
    }

    /** R31: a suggestion may not come from a dictionary the user left out. */
    @Test
    fun `suggestions consult only the chosen dictionaries`() {
        val words = compileInto("JMdict_english_without_proper_names.zip")
        val kanji = compileInto("KANJIDIC_english.zip")
        assumeTrue("dictionary fixtures not available", words != null && kanji != null)

        words!!.first.use { jmdict ->
            kanji!!.first.use { kanjidic ->
                val engine = LookupEngine(listOf(jmdict, kanjidic))
                val wordTitle = jmdict.info.title
                val kanjiTitle = kanjidic.info.title

                val fromWords = engine.suggest("日本", only = setOf(wordTitle))
                println("日本 (JMdict only) -> $fromWords")
                assertTrue("expected 日本語 among $fromWords", fromWords.contains("日本語"))
                assertTrue(
                    "a kanji dictionary has no headwords to suggest",
                    engine.suggest("日本", only = setOf(kanjiTitle)).isEmpty(),
                )
            }
        }
    }

    /**
     * R31: a whole query is a word before it is a sentence.
     *
     * にほん must answer 日本; segmenting it as a sentence would answer the particle に. A conjugated
     * query has no whole-word answer, and then the deinflector is still what makes it useful.
     */
    @Test
    fun `the search answers a whole word before segmenting it`() {
        val compiled = compileInto("ggjz-jitendex-ptbr.zip")
        assumeTrue("ggjz fixture not available", compiled != null)
        compiled!!.first.use { pt ->
            val engine = LookupEngine(listOf(pt))

            val byReading = engine.search("にほん")
            println("search にほん -> ${byReading.map { "${it.surface}=${it.entry.term}" }}")
            assertEquals(1, byReading.size)
            assertEquals("日本", byReading[0].entry.term)
            assertEquals("the query itself is the surface", "にほん", byReading[0].surface)

            val byKanji = engine.search("日本")
            assertEquals("日本", byKanji.firstOrNull()?.entry?.term)

            val inflected = engine.search("食べました")
            println("search 食べました -> ${inflected.map { "${it.surface}=${it.entry.term}" }}")
            assertTrue(
                "expected 食べる, got ${inflected.map { it.entry.term }}",
                inflected.any { it.entry.term == "食べる" },
            )
        }
    }

    @Test
    fun `names dictionary resolves proper nouns`() {
        val compiled = compileInto("JMnedict.zip")
        assumeTrue("JMnedict fixture not available", compiled != null)
        compiled!!.first.use { names ->
            println("JMnedict compiled: ${names.termCount} names")
            assertTrue(names.termCount > 10_000)
            val entry = names.termEntries("ポケモン").firstOrNull()
            println("ポケモン -> ${entry?.definition?.take(120)}")
        }
    }

    /**
     * The panel's selector: a lookup narrowed to one dictionary must not be answered by another.
     *
     * This is what keeps «só o JMdict» meaning something when four dictionaries are installed.
     */
    @Test
    fun `a lookup only consults the dictionaries it was told to`() {
        val jmdict = compileInto("JMdict_english_without_proper_names.zip")
        val names = compileInto("JMnedict.zip")
        assumeTrue("dictionary fixtures not available", jmdict != null && names != null)

        jmdict!!.first.use { jmdictDictionary ->
            names!!.first.use { namesDictionary ->
                val engine = LookupEngine(listOf(jmdictDictionary, namesDictionary))
                val jmdictTitle = jmdictDictionary.info.title
                val namesTitle = namesDictionary.info.title

                // A word both dictionaries know — normally the *first* one answers it, which is what
                // makes the selector visible at all: with the mask, the other one has to.
                val word = listOf("東京", "日本", "山田", "ポケモン", "猫")
                    .firstOrNull { candidate ->
                        namesDictionary.termEntries(candidate).isNotEmpty() &&
                            jmdictDictionary.termEntries(candidate).isNotEmpty()
                    }
                assumeTrue("no sample word is in both dictionaries", word != null)

                val everyone = engine.lookup(word!!)
                assertEquals(
                    "$word is in both, so the first dictionary should answer",
                    jmdictTitle,
                    everyone.firstOrNull()?.dictionaryTitle,
                )

                val namesOnly = engine.lookup(word, only = setOf(namesTitle))
                assertEquals(
                    "the named dictionary should have answered",
                    namesTitle,
                    namesOnly.firstOrNull()?.dictionaryTitle,
                )
                assertTrue(
                    "every hit must come from the named dictionary",
                    namesOnly.all { it.dictionaryTitle == namesTitle },
                )

                val jmdictOnly = engine.lookup(word, only = setOf(jmdictTitle))
                assertTrue(
                    "every hit must come from the named dictionary",
                    jmdictOnly.all { it.dictionaryTitle == jmdictTitle },
                )

                // Naming nothing means every dictionary, and naming one that is not there is not an
                // error — it is simply no words.
                assertEquals(everyone, engine.lookup(word, only = emptySet()))
                assertTrue(engine.lookup(word, only = setOf("dicionário que não existe")).isEmpty())
            }
        }
    }

    /**
     * The kanji block follows the same choice, or the section would contradict the words above it.
     *
     * 淀 is exactly the case: the English KANJIDIC has it, the Portuguese one (jouyou only) does not.
     */
    @Test
    fun `the kanji breakdown follows the same choice`() {
        val english = compileInto("KANJIDIC_english.zip")
        val portuguese = compileInto("KANJIDIC_portuguese.zip")
        assumeTrue("kanji fixtures not available", english != null && portuguese != null)

        english!!.first.use { englishKanji ->
            portuguese!!.first.use { portugueseKanji ->
                val engine = LookupEngine(listOf(englishKanji, portugueseKanji))
                val englishTitle = englishKanji.info.title
                val portugueseTitle = portugueseKanji.info.title

                assertTrue("淀 should be found with both on", engine.kanjiBreakdown("淀").isNotEmpty())
                assertTrue(
                    "the English build carries 淀",
                    engine.kanjiBreakdown("淀", only = setOf(englishTitle)).isNotEmpty(),
                )
                assertTrue(
                    "the Portuguese build has no 淀: " +
                        engine.kanjiBreakdown("淀", only = setOf(portugueseTitle)).size,
                    engine.kanjiBreakdown("淀", only = setOf(portugueseTitle)).isEmpty(),
                )
            }
        }
    }

    /**
     * A narrowing that holds no kanji dictionary is widened instead of answering nothing (R28).
     *
     * The selector exists so the two sections of the panel speak with one voice, but picking a *word*
     * dictionary in it silenced the kanji card for every character: the narrowed list had nothing to
     * read a kanji from, so the card said «nenhum dicionário respondeu» while those same dictionaries
     * were answering the words of the very same screen. A narrowing that does hold a kanji dictionary
     * keeps its word, though — that is the previous test.
     */
    @Test
    fun `a word-only narrowing still answers about a kanji`() {
        val jmdict = compileInto("JMdict_english_without_proper_names.zip")
        val kanjidic = compileInto("KANJIDIC_english.zip")
        assumeTrue("dictionary fixtures not available", jmdict != null && kanjidic != null)

        jmdict!!.first.use { words ->
            kanjidic!!.first.use { kanji ->
                val engine = LookupEngine(listOf(words, kanji))
                val jmdictTitle = words.info.title
                val kanjiTitle = kanji.info.title

                val narrowed = engine.kanjiBreakdown("同", only = setOf(jmdictTitle))
                assertEquals(
                    "the kanji dictionary is the only one that can answer, so it has to",
                    listOf(kanjiTitle),
                    narrowed.map { it.dictionaryTitle },
                )
                assertEquals(
                    "the entry must name the dictionary it came from, so the card can say it",
                    kanjiTitle,
                    engine.kanjiBreakdown("同", only = setOf(kanjiTitle)).first().dictionaryTitle,
                )

                // The lone-kanji reading walks the same path: a kanji no word covers still gets its
                // furigana from a kanji dictionary the narrowing never mentioned.
                assertTrue(
                    "淀 should be read from KANJIDIC even with JMdict selected",
                    engine.withFurigana("淀", emptyList(), only = setOf(jmdictTitle))
                        ?.text?.contains("よど") == true,
                )
            }
        }
    }

    /**
     * R29: the pieces of a compound's reading, read from the kanji entries themselves.
     *
     * The word dictionary says 同時に is どうじに; only the kanji entries can say that どう is the 同
     * and じ the 時. When they cannot — 今日 きょう — there is nothing to divide and the card says the
     * word's reading whole.
     */
    @Test
    fun `a compound whose readings add up is divided per kanji`() {
        val kanjidic = compileInto("KANJIDIC_english.zip")
        assumeTrue("kanji fixture not available", kanjidic != null)

        kanjidic!!.first.use { kanji ->
            val engine = LookupEngine(listOf(kanji))

            assertEquals(
                listOf(Furigana.Run("同", "どう"), Furigana.Run("時", "じ")),
                engine.wordReadings("同時に", "どうじに"),
            )
            assertNull(
                "今日 きょう is read as a word, not as 今 + 日",
                engine.wordReadings("今日", "きょう"),
            )
        }
    }

    /**
     * The reading of a sentence, written from the words the lookup already found.
     *
     * 食べました is the case that makes this worth having: the dictionary knows 食べる, and only 食
     * takes the furigana — the `べました` is printed in the sentence. Anything that wrote たべる whole
     * would be teaching the wrong characters.
     */
    @Test
    fun `the reading of a sentence comes from the words it was segmented into`() {
        val compiled = compileInto("JMdict_english_without_proper_names.zip")
        assumeTrue("JMdict fixture not available", compiled != null)
        compiled!!.first.use { jmdict ->
            val engine = LookupEngine(listOf(jmdict))
            val sentence = "私は昨日、友達と一緒に寿司を食べました。"

            val annotated = engine.withFurigana(sentence, engine.lookup(sentence))
            assumeTrue("nothing was annotated", annotated != null)
            println("$sentence\n  -> ${annotated!!.text}")

            assertTrue(
                "the verb's okurigana must stay outside the reading: ${annotated.text}",
                annotated.text.contains("食（た）べました"),
            )
            assertTrue("expected 寿司（すし）", annotated.text.contains("寿司（すし）"))
            assertTrue("every reading here is a word's reading", annotated.uncertain == 0)
        }
    }

    /**
     * A kanji that is not a word at all falls back to the kanji entry — and is marked, because it is
     * a guess: the dictionary has no idea how this character is being used here.
     */
    @Test
    fun `a kanji no word covers falls back to the kanji entry, marked as a guess`() {
        val compiled = compileInto("KANJIDIC_english.zip")
        assumeTrue("KANJIDIC english fixture not available", compiled != null)
        compiled!!.first.use { kanjidic ->
            val engine = LookupEngine(listOf(kanjidic))
            val readings = Furigana.readingsOf(kanjidic.kanjiEntry("淀")!!)
            assumeTrue("淀 has no readings in this build", readings.isNotEmpty())

            val annotated = engine.withFurigana("淀", emptyList())
            println("淀 -> ${annotated?.text} (${annotated?.uncertain} palpite[s], leituras $readings)")

            assertEquals("淀（${readings.joinToString("・")}?）", annotated?.text)
            assertEquals(1, annotated?.uncertain)
        }
    }

    /**
     * The kanji fallback obeys the same dictionary choice as the words and the kanji block, or the
     * panel would answer from a dictionary it was told to leave out.
     */
    @Test
    fun `the reading fallback follows the same dictionary choice`() {
        val english = compileInto("KANJIDIC_english.zip")
        val portuguese = compileInto("KANJIDIC_portuguese.zip")
        assumeTrue("kanji fixtures not available", english != null && portuguese != null)

        english!!.first.use { englishKanji ->
            portuguese!!.first.use { portugueseKanji ->
                val engine = LookupEngine(listOf(englishKanji, portugueseKanji))
                val englishTitle = englishKanji.info.title
                val portugueseTitle = portugueseKanji.info.title

                assertTrue(
                    "the English build carries 淀",
                    engine.withFurigana("淀", emptyList(), only = setOf(englishTitle))?.text?.contains("よど") == true,
                )
                assertNull(
                    "the Portuguese build has no 淀 to read",
                    engine.withFurigana("淀", emptyList(), only = setOf(portugueseTitle)),
                )
            }
        }
    }

    /**
     * R33e: the onomatopoeia dictionary earns its place by answering what the word dictionary misses.
     *
     * JMdict and Jitendex list most of the mimetic words of manga, so a second dictionary is only
     * worth offering if it covers the rest. The check is therefore a pair: the word is in surasura,
     * and it is *not* in JMdict — a dictionary that answered nothing new would be dead weight in the
     * settings screen.
     */
    @Test
    fun `the onomatopoeia dictionary answers words jmdict does not have`() {
        val onomatopoeia = compileInto("surasura.zip")
        val words = compileInto("JMdict_english_without_proper_names.zip")
        assumeTrue("dictionary fixtures not available", onomatopoeia != null && words != null)

        onomatopoeia!!.first.use { surasura ->
            words!!.first.use { jmdict ->
                println("surasura compiled: ${surasura.termCount} terms")
                assertTrue(
                    "expected onomatopoeia entries, got ${surasura.termCount}",
                    surasura.termCount > 500,
                )

                val word = "ぴくりぴくり"
                val entry = surasura.termEntries(word).firstOrNull()
                assertNotNull("$word must be in surasura", entry)
                println("$word -> ${entry!!.definition.take(200)}")
                assertTrue(
                    "the definition came out as markup: ${entry.definition.take(200)}",
                    entry.definition.contains("ふるえ") && !entry.definition.contains("\"tag\""),
                )
                assertTrue(
                    "the word is in JMdict, so this dictionary adds nothing for it",
                    jmdict.termEntries(word).isEmpty(),
                )

                // In a sentence the dictionary has to be found by the same lookup the panel uses.
                val engine = LookupEngine(listOf(surasura, jmdict))
                val matches = engine.lookup("猫がぴくりぴくりと動いた。")
                println("sentence -> ${matches.map { "${it.surface}=${it.entry.term}" }}")
                assertTrue(
                    "the onomatopoeia was not found in a sentence: ${matches.map { it.surface }}",
                    matches.any { it.entry.term == word },
                )
            }
        }
    }

    private fun suspendUnit(text: String, engine: LookupEngine, check: (List<TokenMatch>) -> Unit) {
        val matches = engine.lookup(text)
        println("$text -> ${matches.map { "${it.surface}=${it.entry.term}" }}")
        check(matches)
    }
}
