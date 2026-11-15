package dev.yabai.ocr.desktop

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryReleaseFallbackTest {

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("yabai-yomitan", ".zip")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun bank(size: Int): String = "[" + "{\"k\":1}".padEnd(size - 2, ' ') + "]"

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
    }

    @Test
    fun `an upstream stub release has no entries and a real one does`() {
        val stub = zip(
            "index.json" to "{\"title\":\"JMdict [unknown]\",\"format\":3}",
            "tag_bank_1.json" to "[[\"name\",\"n\",\"name\",\"name\",0]]",
        )
        val real = zip(
            "index.json" to "{\"title\":\"JMdict\",\"format\":3}",
            "tag_bank_1.json" to "[]",
            "term_bank_1.json" to bank(8192),
        )
        val kanjiOnly = zip(
            "index.json" to "{\"title\":\"KANJIDIC\",\"format\":3}",
            "kanji_bank_1.json" to bank(4096),
        )

        assertFalse("the 2026-11-14 publication of the JMdict family", yomitanArchiveHasEntries(stub))
        assertTrue(yomitanArchiveHasEntries(real))
        assertTrue("KANJIDIC is kanji-only", yomitanArchiveHasEntries(kanjiOnly))
    }

    @Test
    fun `a file that is not a zip is not a dictionary`() {
        val file = File.createTempFile("yabai-not-a-zip", ".zip")
        file.deleteOnExit()
        file.writeText("404: Not Found")

        assertFalse(yomitanArchiveHasEntries(file))
    }

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
    fun `the previous good publication is offered when the newest is a stub`() {
        val json = releasesJson(
            Triple("2026-11-14", "JMdict_english.zip", 2303),
            Triple("2026-11-13", "JMdict_english.zip", 15628711),
            Triple("2026-11-12", "JMdict_english.zip", 2303),
            Triple("2026-11-11", "JMdict_english.zip", 15626183),
        )

        assertEquals(
            listOf(
                "https://example.invalid/2026-11-13/JMdict_english.zip",
                "https://example.invalid/2026-11-11/JMdict_english.zip",
            ),
            fallbackAssetUrls(json, "JMdict_english.zip"),
        )
    }

    @Test
    fun `releases without the asset are ignored`() {
        val json = releasesJson(
            Triple("2026-11-14", "other.zip", 9_000_000),
            Triple("2026-11-13", "JMnedict.zip", 11432840),
        )

        assertEquals(
            listOf("https://example.invalid/2026-11-13/JMnedict.zip"),
            fallbackAssetUrls(json, "JMnedict.zip"),
        )
        assertEquals(emptyList<String>(), fallbackAssetUrls("not json", "JMnedict.zip"))
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
        // No release list (offline, or a URL that is not a GitHub release): the primary alone.
        assertEquals(listOf(primary), dictionaryDownloadUrls(primary, null))
        assertEquals(
            listOf("https://ggjz.org/jitendex.zip"),
            dictionaryDownloadUrls("https://ggjz.org/jitendex.zip", json),
        )
    }
}
