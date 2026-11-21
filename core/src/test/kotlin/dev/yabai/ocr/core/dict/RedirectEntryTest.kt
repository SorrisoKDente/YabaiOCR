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
 * The old kanji form of a word (R46e).
 *
 * Jitendex answers 兩膝 with the tag «old kanji form» and a link to 両膝 — no meaning of its own. The
 * link target is carried as data in the compiled blob, so the lookup can hand the reader the modern
 * entry's meanings and say which form was actually read. Both doors — the search box and the hover,
 * which segments a sentence — go through the same resolution in [LookupEngine].
 *
 * The fixtures are tiny Yomitan archives compiled on the fly: what matters is the redirect, not the
 * dictionary's size.
 */
class RedirectEntryTest {

    /** An entry that only points at [target], the way Jitendex writes an old kanji form. */
    private fun redirectEntry(term: String, target: String) = """
        ["$term","","old kanji form","",0,[{"type":"structured-content","content":[
          {"tag":"span","data":{"content":"cross-reference"},"content":[
            {"tag":"a","href":"?query=$target","content":"$target"}]}
        ]}],1,""]
    """.trimIndent()

    /** An ordinary entry with a sense and a part of speech inside its markup. */
    private fun plainEntry(term: String, reading: String, gloss: String) = """
        ["$term","$reading","","",200,[{"type":"structured-content","content":[
          {"tag":"div","data":{"content":"sense-group"},"content":[
            {"tag":"div","data":{"content":"sense"},"content":[
              {"tag":"span","data":{"content":"part-of-speech-info"},"content":"substantivo"},
              {"tag":"span","data":{"content":"glossary"},"content":"$gloss"}]}]}
        ]}],2,""]
    """.trimIndent()

    private fun compileInto(dir: File, vararg records: String): File {
        val zip = File.createTempFile("yabai-redirect", ".zip")
        zip.deleteOnExit()
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            fun entry(name: String, text: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()
            }
            entry("index.json", """{"title":"Jitendex tiny","revision":"1","format":3}""")
            entry("term_bank_1.json", records.joinToString(",", prefix = "[", postfix = "]"))
        }
        DictionaryCompiler(zip, dir, "tiny.zip").compile()
        return dir
    }

    private fun compile(vararg records: String): CompactDictionary {
        val dir = compileInto(createTempDirectory("yabai-dict-test").toFile(), *records)
        val dictionary = CompactDictionary.open(dir)
        assertNotNull("compiled fixture should open", dictionary)
        return dictionary!!
    }

    /**
     * Rewrites a freshly compiled fixture the way format 3 wrote term blobs: score, term, reading,
     * tags, then the definition — no redirect string in between. The meta keeps the format the
     * reader consults, so the copy looks exactly like one imported by the previous release.
     */
    private fun downgradeToFormat3(dir: File) {
        val offsets = DictionaryCompiler.readIntArray(File(dir, DictFiles.TERM_ENTOFF))
        val blobs = File(dir, DictFiles.TERM_DAT).readBytes()
        val out = java.io.ByteArrayOutputStream(blobs.size)
        val newOffsets = IntArray(offsets.size)

        fun writeInt(value: Int) {
            out.write(value ushr 24)
            out.write(value ushr 16)
            out.write(value ushr 8)
            out.write(value)
        }

        fun writeString(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            out.write(bytes.size ushr 8)
            out.write(bytes.size)
            out.write(bytes)
        }

        for (id in 0 until offsets.size - 1) {
            var position = offsets[id]
            val end = offsets[id + 1]

            fun int(): Int {
                val value = ((blobs[position].toInt() and 0xFF) shl 24) or
                    ((blobs[position + 1].toInt() and 0xFF) shl 16) or
                    ((blobs[position + 2].toInt() and 0xFF) shl 8) or
                    (blobs[position + 3].toInt() and 0xFF)
                position += 4
                return value
            }

            fun string(): String {
                val length = ((blobs[position].toInt() and 0xFF) shl 8) or
                    (blobs[position + 1].toInt() and 0xFF)
                position += 2
                val value = String(blobs, position, length, StandardCharsets.UTF_8)
                position += length
                return value
            }

            val score = int()
            val term = string()
            val reading = string()
            val tags = string()
            string() // the redirect only a format 4 copy carries
            val definition = String(blobs, position, end - position, StandardCharsets.UTF_8)

            writeInt(score)
            writeString(term)
            writeString(reading)
            writeString(tags)
            out.write(definition.toByteArray(StandardCharsets.UTF_8))
            newOffsets[id + 1] = out.size()
        }

        File(dir, DictFiles.TERM_DAT).writeBytes(out.toByteArray())
        DictionaryCompiler.writeIntArray(File(dir, DictFiles.TERM_ENTOFF), newOffsets)

        val meta = File(dir, DictFiles.META)
        val json = com.google.gson.JsonParser.parseString(meta.readText()).asJsonObject
        json.addProperty("compiledWith", 3)
        meta.writeText(json.toString())
    }

    private fun fixture() = compile(
        redirectEntry("兩膝", "両膝"),
        plainEntry("両膝", "りょうひざ", "ambos os joelhos"),
    )

    @Test
    fun `an entry that is only a link stores the link target`() {
        val glossary = com.google.gson.JsonParser.parseString(
            """[{"type":"structured-content","content":[
                 {"tag":"span","data":{"content":"cross-reference"},"content":[
                   {"tag":"a","href":"?query=両膝&offset=1","content":"両膝"}]}
               ]}]""",
        ).asJsonArray

        assertEquals("両膝", GlossaryRenderer.renderGlossary(glossary).redirect)
    }

    @Test
    fun `an entry with a meaning of its own has no redirect`() {
        val glossary = com.google.gson.JsonParser.parseString(
            """[{"type":"structured-content","content":[
                 {"tag":"div","data":{"content":"glossary"},"content":"ambos os joelhos"}
               ]}]""",
        ).asJsonArray

        assertEquals("", GlossaryRenderer.renderGlossary(glossary).redirect)
    }

    @Test
    fun `the search answers with the modern entry and says it is an old form`() {
        fixture().use { dictionary ->
            val match = LookupEngine(listOf(dictionary)).search("兩膝").firstOrNull()

            assertNotNull("the old form found nothing", match)
            assertEquals("the panel kept the redirect instead of following it", "両膝", match!!.entry.term)
            assertEquals("りょうひざ", match.entry.reading)
            assertTrue(
                "the meanings of the modern form are missing: ${match.entry.definition}",
                match.entry.definition.contains("ambos os joelhos"),
            )
            assertTrue("the card cannot say the form is old", match.oldForm)
            assertEquals("the surface is lost, so the card cannot show what was read", "兩膝", match.surface)
        }
    }

    /** The hover reads a sentence instead of a query: same resolution, through [LookupEngine.lookup]. */
    @Test
    fun `a sentence follows the old form too`() {
        fixture().use { dictionary ->
            val match = LookupEngine(listOf(dictionary)).lookup("兩膝が痛い").firstOrNull()

            assertNotNull("the old form found nothing in the sentence", match)
            assertEquals("両膝", match!!.entry.term)
            assertTrue("the hover cannot say the form is old", match.oldForm)
            assertEquals(0, match.start)
            assertEquals(2, match.end)
        }
    }

    /**
     * The copy a user already has was imported before format 4, so its blobs have no redirect string
     * between the tags and the definition. Opening it must keep the meanings readable: the panel
     * only stops showing them once the dictionary is imported again (the outdated banner says so).
     */
    @Test
    fun `a format 3 copy still reads its definitions`() {
        val dir = compileInto(createTempDirectory("yabai-dict-v3").toFile(), redirectEntry("兩膝", "両膝"), plainEntry("両膝", "りょうひざ", "ambos os joelhos"))
        downgradeToFormat3(dir)

        val dictionary = CompactDictionary.open(dir)
        assertNotNull("the format 3 copy should still open", dictionary)
        dictionary!!.use {
            assertEquals(3, it.info.compiledWith)
            val match = LookupEngine(listOf(it)).search("両膝").firstOrNull()

            assertNotNull("the format 3 copy answered nothing", match)
            assertEquals("りょうひざ", match!!.entry.reading)
            assertTrue(
                "the definition was lost: '${match.entry.definition}'",
                match.entry.definition.contains("ambos os joelhos"),
            )
            assertEquals("a format 3 entry cannot have a redirect", "", match.entry.redirect)
            assertFalse("a plain entry was reported as a redirect", match.oldForm)
        }
    }

    @Test
    fun `an ordinary entry is not marked as an old form`() {
        fixture().use { dictionary ->
            val match = LookupEngine(listOf(dictionary)).search("両膝").firstOrNull()

            assertNotNull(match)
            assertFalse("a plain entry was reported as a redirect", match!!.oldForm)
            assertEquals("両膝", match.entry.term)
        }
    }
}
