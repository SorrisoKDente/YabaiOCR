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
            "JMnedict.zip",
            "KANJIDIC_english.zip",
            "KANJIDIC_portuguese.zip",
            "kty-ja-pt.zip",
            "kty-ja-ja.zip",
            "kty-ja-en.zip",
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

    private fun suspendUnit(text: String, engine: LookupEngine, check: (List<TokenMatch>) -> Unit) {
        val matches = engine.lookup(text)
        println("$text -> ${matches.map { "${it.surface}=${it.entry.term}" }}")
        check(matches)
    }
}
