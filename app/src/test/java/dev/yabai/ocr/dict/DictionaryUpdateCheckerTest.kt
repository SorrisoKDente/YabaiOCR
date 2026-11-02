package dev.yabai.ocr.dict

import dev.yabai.ocr.core.dict.DictionaryInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Knowing whether a publisher has something newer, without downloading the dictionary (R43).
 *
 * The two families are checked differently and both are easy to get wrong in opposite directions:
 * an index-backed dictionary names its version in a 1 KB descriptor, so comparing strings is exact;
 * a direct download only has the HTTP identity of the file, which the app has to have recorded when
 * it installed it. A check that cannot answer must say «unknown», never «current» — hiding an update
 * is worse than offering a download that turns out to be unnecessary.
 */
class DictionaryUpdateCheckerTest {

    private val ggjz = DictionaryCatalog.find("ggjz-pt")!!
    private val jmdict = DictionaryCatalog.find("jmdict-en")!!

    /** The network, without the network: records what was asked and answers from a table. */
    private class FakeRemote(
        val heads: Map<String, RemoteSignature> = emptyMap(),
        val descriptors: Map<String, String> = emptyMap(),
    ) : RemoteSource {

        val headCalls = mutableListOf<String>()
        val textCalls = mutableListOf<String>()
        var failure: Throwable? = null

        override suspend fun download(
            entry: DictionaryCatalog.Entry,
            target: File,
            onProgress: (Long, Long) -> Unit,
        ): DownloadedDictionary = throw UnsupportedOperationException("the check never downloads")

        override suspend fun head(url: String): RemoteSignature {
            headCalls += url
            failure?.let { throw it }
            return heads[url] ?: throw IOException("sem resposta para $url")
        }

        override suspend fun readText(url: String): String {
            textCalls += url
            failure?.let { throw it }
            return descriptors[url] ?: throw IOException("sem resposta para $url")
        }
    }

    private fun installed(
        id: String,
        revision: String = "",
        sourceId: String = "",
        signature: RemoteSignature? = null,
    ) = InstalledDictionary(
        id = id,
        directory = File("."),
        info = DictionaryInfo(
            title = "T",
            revision = revision,
            format = 3,
            author = null,
            url = null,
            description = null,
            attribution = null,
            termCount = 1,
            kanjiCount = 0,
        ),
        sourceName = "T",
        enabled = true,
        sizeBytes = 1,
        sourceId = sourceId,
        sourceSignature = signature,
    )

    @Test
    fun `an index dictionary whose revision matches is current`() = runBlocking {
        val remote = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"2026.09.29.2119"}"""))
        val result = DictionaryUpdateChecker(remote).check(installed("ggjz-pt", revision = "2026.09.29.2119"))

        assertEquals(UpdateState.CURRENT, result.state)
        assertFalse(result.hasUpdate)
        assertEquals(listOf(ggjz.indexUrl), remote.textCalls)
    }

    @Test
    fun `a different revision is offered with the server's label`() = runBlocking {
        val remote = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"2026.10.30.1200"}"""))
        val result = DictionaryUpdateChecker(remote).check(installed("ggjz-pt", revision = "2026.09.29.2119"))

        assertEquals(UpdateState.AVAILABLE, result.state)
        assertTrue(result.hasUpdate)
        assertTrue(result.canRefresh)
        assertEquals("2026.10.30.1200", result.remoteLabel)
    }

    @Test
    fun `a remote revision older than the installed one is not an update`() = runBlocking {
        // The real case from the device: the Wiktionary PT-BR descriptor still announces
        // 2025.08.12, while the archive it points at (and therefore the installed copy) was
        // compiled with 2026.03.05. String inequality called that a downgrade «update».
        val wty = DictionaryCatalog.find("wty-ja-pt")!!
        val remote = FakeRemote(
            descriptors = mapOf(wty.indexUrl!! to """{"revision":"2025.08.12"}"""),
        )
        val result = DictionaryUpdateChecker(remote).check(installed("wty-ja-pt", revision = "2026.03.05"))

        assertEquals(UpdateState.CURRENT, result.state)
        assertFalse("an older label must never be offered as an update", result.hasUpdate)
        assertFalse(result.canRefresh)
    }

    @Test
    fun `a newer revision is still offered even when the installed copy is ahead by format`() = runBlocking {
        val remote = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"2026.03.05"}"""))
        val result = DictionaryUpdateChecker(remote).check(installed("ggjz-pt", revision = "2025.08.12"))

        assertEquals(UpdateState.AVAILABLE, result.state)
        assertTrue(result.canRefresh)
    }

    @Test
    fun `the same numbers with different separators are the same version`() = runBlocking {
        val remote = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"2026.03.05"}"""))
        val result = DictionaryUpdateChecker(remote).check(installed("ggjz-pt", revision = "2026-03-05"))

        assertEquals(UpdateState.CURRENT, result.state)
        assertFalse(result.hasUpdate)
    }

    @Test
    fun `revisions without numbers fall back to equality`() = runBlocking {
        val same = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"final"}"""))
        assertEquals(
            UpdateState.CURRENT,
            DictionaryUpdateChecker(same).check(installed("ggjz-pt", revision = "final")).state,
        )

        val different = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"final-2"}"""))
        assertEquals(
            "a label nobody can order is still worth offering when it changed",
            UpdateState.AVAILABLE,
            DictionaryUpdateChecker(different).check(installed("ggjz-pt", revision = "final")).state,
        )
    }

    @Test
    fun `revision labels are ordered by number, not by text`() {
        assertTrue(compareRevisions("2026.03.05", "2025.08.12")!! > 0)
        assertTrue(compareRevisions("2025.08.12", "2026.03.05")!! < 0)
        assertEquals(0, compareRevisions("2026.03.05", "2026-03-05"))
        assertEquals(0, compareRevisions("2026.09.29.2119", "2026.09.29.2119"))
        assertTrue(compareRevisions("2026.09.29", "2026.09.29.2119")!! < 0)
        assertEquals("no number on either side means no ordering", null, compareRevisions("final", "beta"))
        assertEquals(null, compareRevisions("", "2026.01.01"))
    }

    @Test
    fun `a descriptor without a revision is unknown, not current`() = runBlocking {
        val remote = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"title":"sem revisão"}"""))
        val result = DictionaryUpdateChecker(remote).check(installed("ggjz-pt", revision = "2026.09.29.2119"))

        assertEquals(UpdateState.UNKNOWN, result.state)
        assertFalse(result.hasUpdate)
        assertTrue(result.canRefresh)
        assertTrue("should explain why it could not answer: ${result.message}", result.message.isNotBlank())
    }

    @Test
    fun `a copy installed before revisions were stored is offered one refresh`() = runBlocking {
        val remote = FakeRemote(descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"2026.10.30.1200"}"""))
        val result = DictionaryUpdateChecker(remote).check(installed("ggjz-pt", revision = ""))

        assertEquals(UpdateState.AVAILABLE, result.state)
        assertTrue(result.canRefresh)
    }

    @Test
    fun `a direct dictionary compares the HTTP signature recorded at install time`() = runBlocking {
        val url = jmdict.url!!
        val remote = FakeRemote(
            heads = mapOf(
                url to RemoteSignature(
                    etag = "\"new\"",
                    lastModified = "Fri, 30 Oct 2026 10:00:00 GMT",
                    length = 200,
                ),
            ),
        )
        val old = installed(
            "jmdict-en",
            signature = RemoteSignature(etag = "\"old\"", lastModified = "Fri, 01 Jan 2026 10:00:00 GMT", length = 100),
        )

        val result = DictionaryUpdateChecker(remote).check(old)

        assertEquals(UpdateState.AVAILABLE, result.state)
        assertEquals(listOf(url), remote.headCalls)
        assertEquals("30/10/2026", result.remoteLabel)
    }

    @Test
    fun `the same ETag means the installed archive is current`() = runBlocking {
        val url = jmdict.url!!
        val remote = FakeRemote(heads = mapOf(url to RemoteSignature(etag = "\"same\"", length = 100)))
        val result = DictionaryUpdateChecker(remote).check(
            installed("jmdict-en", signature = RemoteSignature(etag = "\"same\"", length = 100)),
        )

        assertEquals(UpdateState.CURRENT, result.state)
        assertFalse(result.hasUpdate)
    }

    @Test
    fun `a legacy direct install has no signature to compare and can only be refreshed`() = runBlocking {
        val remote = FakeRemote(heads = mapOf(jmdict.url!! to RemoteSignature(etag = "\"new\"")))
        val result = DictionaryUpdateChecker(remote).check(installed("jmdict-en"))

        assertEquals(UpdateState.UNKNOWN, result.state)
        assertTrue(result.canRefresh)
        assertTrue("should explain the one refresh: ${result.message}", result.message.contains("antes"))
    }

    @Test
    fun `a server that answers nothing comparable is unknown`() = runBlocking {
        val remote = FakeRemote(heads = mapOf(jmdict.url!! to RemoteSignature()))
        val result = DictionaryUpdateChecker(remote).check(
            installed("jmdict-en", signature = RemoteSignature(etag = "\"old\"")),
        )

        assertEquals(UpdateState.UNKNOWN, result.state)
        assertFalse(result.hasUpdate)
    }

    @Test
    fun `a dictionary imported from a file has nowhere to be checked`() = runBlocking {
        val remote = FakeRemote()
        val result = DictionaryUpdateChecker(remote).check(installed("meu-dicionario"))

        assertEquals(UpdateState.MANUAL, result.state)
        assertFalse(result.hasUpdate)
        assertFalse("there is nothing to refresh either", result.canRefresh)
        assertTrue(remote.headCalls.isEmpty())
        assertTrue(remote.textCalls.isEmpty())
    }

    @Test
    fun `an old install is recognised by its id even without a stored source`() = runBlocking {
        val remote = FakeRemote(heads = mapOf(jmdict.url!! to RemoteSignature(etag = "\"new\"")))
        val result = DictionaryUpdateChecker(remote).check(
            installed("jmdict-en", sourceId = "", signature = RemoteSignature(etag = "\"old\"")),
        )

        assertEquals(UpdateState.AVAILABLE, result.state)
        assertEquals(listOf(jmdict.url), remote.headCalls)
    }

    @Test
    fun `a network failure is reported, not treated as up to date`() = runBlocking {
        val remote = FakeRemote().apply { failure = IOException("sem internet") }
        val result = DictionaryUpdateChecker(remote).check(installed("jmdict-en"))

        assertEquals(UpdateState.FAILED, result.state)
        assertFalse(result.hasUpdate)
        assertTrue("the reason should reach the screen: ${result.message}", result.message.contains("sem internet"))
    }

    @Test
    fun `checking all keeps one answer per dictionary`() = runBlocking {
        val remote = FakeRemote(
            descriptors = mapOf(ggjz.indexUrl!! to """{"revision":"2026.10.30.1200"}"""),
            heads = mapOf(jmdict.url!! to RemoteSignature(etag = "\"new\"")),
        )
        val results = DictionaryUpdateChecker(remote).checkAll(
            listOf(
                installed("ggjz-pt", revision = "2026.09.29.2119"),
                installed("jmdict-en", signature = RemoteSignature(etag = "\"old\"")),
                installed("importado"),
            ),
        )

        assertEquals(listOf("ggjz-pt", "jmdict-en", "importado"), results.map { it.id })
        assertEquals(
            listOf(UpdateState.AVAILABLE, UpdateState.AVAILABLE, UpdateState.MANUAL),
            results.map { it.state },
        )
    }

    @Test
    fun `the HTTP signature prefers ETag, then the date, and never trusts an equal size`() {
        assertEquals(
            true,
            RemoteSignature(etag = "\"a\"", lastModified = "x", length = 1)
                .sameAs(RemoteSignature(etag = "\"a\"", lastModified = "y", length = 2)),
        )
        assertEquals(
            false,
            RemoteSignature(etag = "\"a\"", lastModified = "same", length = 1)
                .sameAs(RemoteSignature(etag = "\"b\"", lastModified = "same", length = 1)),
        )

        assertEquals(
            true,
            RemoteSignature(lastModified = "Fri, 30 Oct 2026 10:00:00 GMT", length = 1)
                .sameAs(RemoteSignature(lastModified = "Fri, 30 Oct 2026 10:00:00 GMT", length = 2)),
        )
        assertEquals(
            false,
            RemoteSignature(lastModified = "Fri, 01 Jan 2026 10:00:00 GMT")
                .sameAs(RemoteSignature(lastModified = "Fri, 30 Oct 2026 10:00:00 GMT")),
        )

        assertEquals(
            false,
            RemoteSignature(length = 100).sameAs(RemoteSignature(length = 200)),
        )
        // Equal size is not an identity: it cannot make the copy current, only «could not tell».
        assertNull(RemoteSignature(length = 100).sameAs(RemoteSignature(length = 100)))
        assertNull(RemoteSignature().sameAs(RemoteSignature(length = 100)))
    }
}
