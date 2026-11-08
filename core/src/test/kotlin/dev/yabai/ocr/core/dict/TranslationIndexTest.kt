package dev.yabai.ocr.core.dict

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Searching the definitions, not the headwords (translation mode).
 *
 * The fixtures are tiny Yomitan archives compiled on the fly, because the point is the index and
 * the query, not the dictionary text; real dictionaries are 15 MB downloads.
 */
class TranslationIndexTest {

    private data class Word(val term: String, val reading: String, val definition: String)

    private fun compile(dictionaryTitle: String, words: List<Word>): Pair<CompactDictionary, File> {
        val zip = File.createTempFile("yabai-tiny", ".zip")
        zip.deleteOnExit()
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            fun entry(name: String, text: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()
            }
            entry(
                "index.json",
                """{"title":"$dictionaryTitle","revision":"1","format":3}""",
            )
            val bank = words.joinToString(separator = ",", prefix = "[", postfix = "]") { word ->
                val escaped = word.definition.replace("\"", "\\\"")
                """["${word.term}","${word.reading}","","",100,[["$escaped"]],0,""]"""
            }
            entry("term_bank_1.json", bank)
        }
        val dir = createTempDirectory("yabai-dict-test").toFile()
        DictionaryCompiler(zip, dir, "tiny.zip").compile()
        val dictionary = CompactDictionary.open(dir)
        assertNotNull("compiled fixture should open", dictionary)
        return dictionary!! to dir
    }

    private fun fixture(): Pair<CompactDictionary, File> = compile(
        "Tiny",
        listOf(
            Word("剣", "けん", "espada longa; arma branca"),
            Word("刀", "かたな", "espada japonesa"),
            Word("水", "みず", "água"),
            Word("食べる", "たべる", "comer"),
        ),
    )

    @Test
    fun `tokens are lowercased and lose their accents`() {
        assertEquals(listOf("espada", "longa"), TranslationTokens.tokenize("A Espada LONGA"))
        assertEquals(listOf("lamina"), TranslationTokens.tokenize("lâmina"))
        assertEquals(listOf("agua"), TranslationTokens.tokenize("ÁGUA"))
    }

    @Test
    fun `stopwords and one-letter words never reach the index`() {
        assertEquals(emptyList<String>(), TranslationTokens.tokenize("a o de da the of"))
        assertEquals(listOf("espada", "rei"), TranslationTokens.tokenize("a espada do rei"))
    }

    @Test
    fun `the index answers which entries mention a word in their definition`() {
        val (dictionary, _) = fixture()
        dictionary.use {
            val index = TranslationIndex.ensure(dictionary)
            val expected = (0 until dictionary.entryCount)
                .filter { id -> dictionary.entryById(id)?.definition?.contains("espada") == true }
                .toSet()
            assertEquals(setOf("剣", "刀"), expected.mapNotNull { dictionary.entryById(it)?.term }.toSet())

            val ids = index.postings("espada")
            assertEquals(expected, ids.toSet())
            assertTrue("postings must be sorted", ids.toList() == ids.sorted())
        }
    }

    @Test
    fun `an accented query finds the unaccented definition and vice versa`() {
        val (dictionary, _) = fixture()
        dictionary.use {
            val index = TranslationIndex.ensure(dictionary)
            assertEquals(1, index.postings("água").size)
            assertEquals(1, index.postings("agua").size)
            assertEquals(0, index.postings("fogo").size)
        }
    }

    @Test
    fun `the sidecar is written beside the dictionary and reports a one-time build`() {
        val (dictionary, dir) = fixture()
        dictionary.use {
            val progress = mutableListOf<TranslationProgress>()
            TranslationIndex.ensure(dictionary) { progress.add(it) }
            assertTrue(File(dir, DictFiles.INV_META).isFile)
            assertTrue(File(dir, DictFiles.INV_POST).isFile)
            assertTrue(progress.any { it.building })
            assertEquals(1f, progress.last().fraction, 0.0001f)

            // Second call opens the sidecar; it must not announce another build.
            val again = mutableListOf<TranslationProgress>()
            TranslationIndex.ensure(dictionary) { again.add(it) }
            assertFalse("a ready index must not be rebuilt", again.any { it.building })
            assertEquals(1f, again.last().fraction, 0.0001f)
        }
    }

    @Test
    fun `a sidecar without its meta is not trusted and is rebuilt`() {
        val (dictionary, dir) = fixture()
        dictionary.use {
            TranslationIndex.ensure(dictionary).close()
            assertTrue(File(dir, DictFiles.INV_META).delete())

            val index = TranslationIndex.ensure(dictionary)
            index.use {
                assertEquals(2, it.postings("espada").size)
            }
            assertTrue("the rebuild must put the meta back", File(dir, DictFiles.INV_META).isFile)
        }
    }

    @Test
    fun `an index whose dictionary changed is rebuilt instead of trusted`() {
        val (dictionary, dir) = fixture()
        dictionary.use {
            TranslationIndex.ensure(dictionary).close()

            // A dictionary update rewrites terms.dat; the stored fingerprint must notice even though
            // every sidecar file and the meta are still there. Appending keeps the existing blobs
            // readable, which is exactly the «new revision, same layout» case.
            File(dir, DictFiles.TERM_DAT).appendBytes(byteArrayOf(0))

            val progress = mutableListOf<TranslationProgress>()
            val rebuilt = TranslationIndex.ensure(dictionary) { progress.add(it) }
            rebuilt.use {
                assertTrue("a changed dictionary must be re-indexed", progress.any { p -> p.building })
                assertEquals(2, it.postings("espada").size)
            }
        }
    }

    @Test
    fun `every query token must be in the definition`() {
        val (dictionary, _) = fixture()
        dictionary.use {
            val engine = LookupEngine(listOf(dictionary))

            val espada = engine.searchTranslation("espada")
            assertEquals(setOf("剣", "刀"), espada.map { it.entry.term }.toSet())

            val longa = engine.searchTranslation("espada longa")
            assertEquals(listOf("剣"), longa.map { it.entry.term })

            val japonesa = engine.searchTranslation("espada japonesa")
            assertEquals(listOf("刀"), japonesa.map { it.entry.term })

            assertTrue(engine.searchTranslation("espada fogo").isEmpty())
            assertTrue("stopwords alone cannot be a query", engine.searchTranslation("da").isEmpty())
        }
    }

    @Test
    fun `the answer carries the dictionary title like a normal lookup`() {
        val (dictionary, _) = fixture()
        dictionary.use {
            val engine = LookupEngine(listOf(dictionary))
            val match = engine.searchTranslation("comer").single()
            assertEquals("食べる", match.entry.term)
            assertEquals("Tiny", match.dictionaryTitle)
        }
    }
}
