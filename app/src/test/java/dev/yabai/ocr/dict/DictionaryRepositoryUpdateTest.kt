package dev.yabai.ocr.dict

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Updating a dictionary is installing it again over itself (R43).
 *
 * There is no patch to apply: the definitions are rendered into the compiled files at import time,
 * so the only way to get new ones is to download the new archive and compile it. What makes it an
 * *update* instead of a *reinstall* is that the id, the enable flag and the priority stay where the
 * user put them, and the new copy is live as soon as the install returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryRepositoryUpdateTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** A minimal Yomitan archive: the compiler only needs index.json and a term bank. */
    private fun archive(name: String, revision: String, definition: String): File {
        val file = File(context.cacheDir, "$name.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(entry: String, text: String) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(text.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "index.json",
                """{"title":"JMdict","revision":"$revision","format":3,"author":"EDRDG"}""",
            )
            put(
                "term_bank_1.json",
                """[["食べる","たべる","v1","",100,[["$definition"]],1,""]]""",
            )
        }
        return file
    }

    private val firstSignature = RemoteSignature(
        etag = "\"old\"",
        lastModified = "Fri, 01 Jan 2026 10:00:00 GMT",
        length = 100,
    )
    private val newSignature = RemoteSignature(
        etag = "\"new\"",
        lastModified = "Fri, 30 Oct 2026 10:00:00 GMT",
        length = 200,
    )

    @Test
    fun `installing over the same id keeps the priority and the enable flag`() = runBlocking {
        val repository = DictionaryRepository.get(context)
        val other = archive("other", "2026-01-01", "outra coisa")

        repository.install(other, "surasura-ja", "Onomatopeias")
        repository.install(archive("v1", "2026-01-01", "comer"), "jmdict-en", "JMdict (inglês)")
        repository.move("jmdict-en", -1)
        repository.setEnabled("jmdict-en", false)
        val before = repository.installed().map { it.id to it.enabled }
        assertEquals(listOf("jmdict-en" to false, "surasura-ja" to true), before)

        val updated = repository.install(
            archive("v2", "2026-10-30", "comer (novo)"),
            "jmdict-en",
            "JMdict (inglês)",
            DictionarySource("jmdict-en", "https://example.test/JMdict.zip", newSignature),
        )

        val items = repository.installed()
        assertEquals("an update must not add a second copy", 2, items.size)
        assertEquals(
            "the order the user chose has to survive the update",
            listOf("jmdict-en", "surasura-ja"),
            items.map { it.id },
        )
        val item = items.first { it.id == "jmdict-en" }
        assertFalse("a disabled dictionary must stay disabled after an update", item.enabled)
        assertEquals("2026-10-30", item.info.revision)
        assertEquals("jmdict-en", item.sourceId)
        assertEquals("\"new\"", item.sourceSignature?.etag)
        assertEquals(200L, item.sourceSignature?.length)
        assertEquals("the returned item has to describe the stored one", false, updated.enabled)
    }

    @Test
    fun `the new definitions are live right after the install`() = runBlocking {
        val repository = DictionaryRepository.get(context)

        repository.install(archive("v1", "2026-01-01", "comer"), "jmdict-en", "JMdict (inglês)")
        val before = repository.lookupEngine()!!.lookup("食べる").first()
        assertTrue("the first import should answer: ${before.entry.definition}", before.entry.definition.contains("comer"))

        repository.install(archive("v2", "2026-10-30", "alimentar-se"), "jmdict-en", "JMdict (inglês)")
        val after = repository.lookupEngine()!!.lookup("食べる").first()

        assertTrue(
            "the old definition is still being served after the update: ${after.entry.definition}",
            after.entry.definition.contains("alimentar-se"),
        )
        assertEquals("2026-10-30", repository.installed().single().info.revision)
    }

    @Test
    fun `a check right after an update says the dictionary is current`() = runBlocking {
        val repository = DictionaryRepository.get(context)
        repository.install(archive("v1", "2026-01-01", "comer"), "jmdict-en", "JMdict (inglês)")
        repository.install(
            archive("v2", "2026-10-30", "comer (novo)"),
            "jmdict-en",
            "JMdict (inglês)",
            DictionarySource("jmdict-en", "https://example.test/JMdict.zip", newSignature),
        )

        val entry = DictionaryCatalog.find("jmdict-en")!!
        val remote = object : RemoteSource {
            override suspend fun download(
                entry: DictionaryCatalog.Entry,
                target: File,
                onProgress: (Long, Long) -> Unit,
            ): DownloadedDictionary = throw UnsupportedOperationException("a check never downloads")

            override suspend fun head(url: String): RemoteSignature = newSignature

            override suspend fun readText(url: String): String = ""
        }
        val result = DictionaryUpdateChecker(remote).check(repository.installed().single())

        assertEquals(
            "the signature saved by the update must make the next check answer «current»",
            UpdateState.CURRENT,
            result.state,
        )
    }

    @Test
    fun `an index dictionary updated with a newer archive is not offered its stale label`() = runBlocking {
        // The archive is compiled with 2026.03.05 while the descriptor still announces 2025.08.12 —
        // exactly what the device showed for the three Wiktionary dictionaries (R43).
        val repository = DictionaryRepository.get(context)
        repository.install(
            archive("wty", "2026.03.05", "comer"),
            "wty-ja-pt",
            "Wikcionário japonês → português",
            DictionarySource("wty-ja-pt", "https://example.test/wty", RemoteSignature(length = 10)),
        )

        val remote = object : RemoteSource {
            override suspend fun download(
                entry: DictionaryCatalog.Entry,
                target: File,
                onProgress: (Long, Long) -> Unit,
            ): DownloadedDictionary = throw UnsupportedOperationException("a check never downloads")

            override suspend fun head(url: String): RemoteSignature = RemoteSignature()

            override suspend fun readText(url: String): String = """{"revision":"2025.08.12"}"""
        }
        val result = DictionaryUpdateChecker(remote).check(repository.installed().single())

        assertEquals(UpdateState.CURRENT, result.state)
        assertFalse(result.canRefresh)
    }

    @Test
    fun `an empty answer is not cached as the list`() {
        val repository = DictionaryRepository.get(context)
        assertTrue("the test starts with no dictionaries", repository.installed().isEmpty())

        // A dictionary can appear without going through install(): restored from a backup, copied
        // in by hand, or seen by a scan that ran while the folder was transiently empty. Caching the
        // empty answer hid every dictionary until the process restarted (R43, device report).
        val directory = File(context.filesDir, "dictionaries/restaurado")
        directory.mkdirs()
        File(directory, "meta.json").writeText(
            """
            {"title":"Restaurado","revision":"2026-01-01","format":3,
             "compiledWith":${dev.yabai.ocr.core.dict.DictionaryCompiler.FORMAT_VERSION},
             "termCount":1,"kanjiCount":0}
            """.trimIndent(),
        )

        assertEquals(
            "an empty scan must not be remembered: the dictionary would stay hidden",
            1,
            repository.installed().size,
        )
    }

    @Test
    fun `a dictionary imported from a file has no source to record`() = runBlocking {
        val repository = DictionaryRepository.get(context)

        repository.install(archive("manual", "2026-01-01", "comer"), "meu-dicionario", "Meu dicionário")

        val item = repository.installed().single()
        assertEquals("", item.sourceId)
        assertNotNull("a copy without a recorded signature still has to load", item.info.title)
    }
}
