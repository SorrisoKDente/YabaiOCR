package dev.yabai.ocr.desktop

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DictionaryReleaseFallbackTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(directory: File, name: String, vararg entries: Pair<String, String>): File {
        val file = File(directory, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entryName, content) in entries) {
                out.putNextEntry(ZipEntry(entryName))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun bank(size: Int): String = "[" + "{\"k\":1}".padEnd(size - 2, ' ') + "]"

    private fun releasesJson(vararg entries: Triple<String, String, Long>): String =
        entries.joinToString(prefix = "[", postfix = "]") { (tag, name, size) ->
            """
            {"tag_name":"$tag","assets":[
              {"name":"index.json","size":10,"browser_download_url":"https://example.invalid/$tag/index.json"},
              {"name":"$name","size":$size,"browser_download_url":"https://example.invalid/$tag/$name"}
            ]}
            """.trimIndent()
        }

    @Test
    fun `release download urls expose the repository and the asset`() {
        assertEquals(
            "yomidevs/jmdict-yomitan" to "JMdict_english.zip",
            releaseDownloadTarget(
                "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english.zip",
            ),
        )
        assertEquals(
            "yomidevs/jmdict-yomitan" to "JMdict_english.zip",
            releaseDownloadTarget(
                "https://github.com/yomidevs/jmdict-yomitan/releases/download/2026-11-13/JMdict_english.zip",
            ),
        )
        assertNull(releaseDownloadTarget("https://ggjz.org/yomitan-ptbr.json"))
        assertEquals(
            "2026-11-13",
            releaseTagFromUrl(
                "https://github.com/yomidevs/jmdict-yomitan/releases/download/2026-11-13/JMdict_english.zip",
            ),
        )
        assertNull(
            releaseTagFromUrl(
                "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english.zip",
            ),
        )
    }

    @Test
    fun `an upstream stub release has no entries and a real one does`() {
        val stub = zip(
            temp.root, "stub.zip",
            "index.json" to "{\"title\":\"JMdict [unknown]\",\"format\":3}",
            "tag_bank_1.json" to "[[\"name\",\"n\",\"name\",\"name\",0]]",
        )
        val real = zip(
            temp.root, "real.zip",
            "index.json" to "{\"title\":\"JMdict\",\"format\":3}",
            "tag_bank_1.json" to "[]",
            "term_bank_1.json" to bank(8192),
        )
        val kanjiOnly = zip(
            temp.root, "kanji.zip",
            "index.json" to "{\"title\":\"KANJIDIC\",\"format\":3}",
            "kanji_bank_1.json" to bank(4096),
        )

        assertFalse("the 2026-11-14 publication of the JMdict family", yomitanArchiveHasEntries(stub))
        assertTrue(yomitanArchiveHasEntries(real))
        assertTrue("KANJIDIC is kanji-only", yomitanArchiveHasEntries(kanjiOnly))
    }

    @Test
    fun `a file that is not a zip is not a dictionary`() {
        val file = File(temp.root, "not-a-zip.zip")
        file.writeText("404: Not Found")

        assertFalse(yomitanArchiveHasEntries(file))
    }

    @Test
    fun `only publications with a usable asset are offered`() {
        val json = releasesJson(
            Triple("2026-11-14", "JMdict_english.zip", 2303),
            Triple("2026-11-13", "JMdict_english.zip", 15628711),
            Triple("2026-11-12", "JMdict_english.zip", 2303),
            Triple("2026-11-11", "JMdict_english.zip", 15626183),
        )

        assertEquals(
            listOf("2026-11-13", "2026-11-11"),
            usableReleaseAssets(json, "JMdict_english.zip").map { it.tag },
        )
        assertEquals(emptyList<ReleasePublication>(), usableReleaseAssets("not json", "JMdict_english.zip"))
        assertEquals(
            emptyList<ReleasePublication>(),
            usableReleaseAssets(releasesJson(Triple("2026-11-14", "other.zip", 9_000_000)), "JMnedict.zip"),
        )
    }

    @Test
    fun `the primary url comes first and the fallbacks follow it`() {
        val primary =
            "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/KANJIDIC_english.zip"
        val json = releasesJson(
            Triple("2026-11-14", "KANJIDIC_english.zip", 1369),
            Triple("2026-11-13", "KANJIDIC_english.zip", 721032),
        )

        assertEquals(
            listOf(primary, "https://example.invalid/2026-11-13/KANJIDIC_english.zip"),
            dictionaryDownloadUrls(primary, json),
        )
        assertEquals(listOf(primary), dictionaryDownloadUrls(primary, null))
        assertEquals(
            listOf("https://ggjz.org/jitendex.zip"),
            dictionaryDownloadUrls("https://ggjz.org/jitendex.zip", json),
        )
    }

    @Test
    fun `the newest release is resolved from the release list`() {
        val json = releasesJson(
            Triple("2026-11-14", "JMdict_english.zip", 2303),
            Triple("2026-11-13", "JMdict_english.zip", 15628711),
        )
        val requested = mutableListOf<String>()

        val release = newestReleaseFor(
            "https://github.com/yomidevs/jmdict-yomitan/releases/latest/download/JMdict_english.zip",
        ) { url ->
            requested += url
            json
        }

        assertEquals("2026-11-13", release?.tag)
        assertEquals(listOf(releasesApiUrl("yomidevs/jmdict-yomitan")), requested)
        assertNull(newestReleaseFor("https://ggjz.org/yomitan-ptbr.json") { json })
        assertNull(newestReleaseFor("https://github.com/a/b/releases/latest/download/x.zip") { null })
    }

    @Test
    fun `an install from the fallback is not offered the empty publication as an update`() {
        // The user's case: JMdict installed from 2026-11-13 while 2026-11-14 is an empty stub. The
        // update check must compare publications, not the size/ETag of whichever URL is `latest`.
        val releases = releasesJson(
            Triple("2026-11-14", "JMdict_english.zip", 2303),
            Triple("2026-11-13", "JMdict_english.zip", 15628711),
        )
        val remote = usableReleaseAssets(releases, "JMdict_english.zip").first()

        val installed = DictionarySignature(length = 15628711, releaseTag = "2026-11-13")
        assertFalse(dictionaryUpdateAvailable(installed, remote))

        assertTrue(
            "a genuine newer publication must still be offered",
            dictionaryUpdateAvailable(installed, remote.copy(tag = "2026-11-20")),
        )
        assertTrue("no signature at all means «not installed by us»", dictionaryUpdateAvailable(null, remote))

        // Installs made before the tag was recorded only have the size to compare.
        assertFalse(
            dictionaryUpdateAvailable(DictionarySignature(length = remote.size), remote),
        )
        assertTrue(
            dictionaryUpdateAvailable(DictionarySignature(length = remote.size - 1), remote),
        )
    }

    @Test
    fun `the signature round trips through the dictionary folder`() {
        val dir = temp.newFolder("jmdict.yabai")
        writeDictionarySignature(
            dir,
            DictionarySignature(
                etag = "\"abc\"",
                lastModified = "Sat, 14 Nov 2026 12:50:00 GMT",
                length = 15_628_711,
                releaseTag = "2026-11-13",
            ),
        )

        assertEquals(
            DictionarySignature(
                etag = "\"abc\"",
                lastModified = "Sat, 14 Nov 2026 12:50:00 GMT",
                length = 15_628_711,
                releaseTag = "2026-11-13",
            ),
            readDictionarySignature(dir),
        )
        assertNull(readDictionarySignature(temp.newFolder("no-signature.yabai")))
    }
}
