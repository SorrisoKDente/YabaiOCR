package dev.yabai.ocr.core.dict

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Builds the translation index of a **real** compiled dictionary and measures it.
 *
 * Skipped unless a folder of `.yabai` dictionaries is provided, because the fixture is 50 MB of
 * user data and has no business being committed:
 *
 *     gradle :core:test -PcompiledDicts="C:\path\to\compiled"
 *
 * The dictionary is copied to a temporary folder first: the measurement is allowed to write its
 * sidecar, but not into the folder the app reads from.
 */
class TranslationIndexRealTest {

    private val source: File? = System.getProperty("yabai.compiled")
        ?.takeIf { it.isNotBlank() }
        ?.let { File(it, "jitendex.yabai") }
        ?.takeIf { it.isDirectory }

    private fun copyToTemp(from: File): File {
        val target = createTempDirectory("yabai-real-index").toFile()
        from.listFiles()?.forEach { file ->
            if (file.isFile) file.copyTo(File(target, file.name), overwrite = true)
        }
        return target
    }

    @Test
    fun `builds the jitendex index once and answers an english word`() {
        assumeTrue("compiled dictionaries not available", source != null)
        val dir = copyToTemp(source!!)
        val dictionary = CompactDictionary.open(dir)
        assertTrue("real dictionary must open", dictionary != null)
        dictionary!!.use { jitendex ->
            println("Jitendex: ${jitendex.entryCount} entries, ${jitendex.termCount} headwords")

            val progress = mutableListOf<TranslationProgress>()
            val started = System.nanoTime()
            val index = TranslationIndex.ensure(jitendex) { progress.add(it) }
            val buildMs = (System.nanoTime() - started) / 1_000_000
            index.use {
                val tokens = it.tokenCount
                val postBytes = File(dir, DictFiles.INV_POST).length()
                val keyBytes = File(dir, DictFiles.INV_KEYS).length()
                println("index built in ${buildMs}ms: $tokens tokens, postings ${postBytes / 1024} KiB, keys ${keyBytes / 1024} KiB")
                assertTrue("expected a real index, got $tokens tokens", tokens > 50_000)
                assertTrue("build reports the one-time scan", progress.any { it.building })

                val sword = it.postings("sword")
                println("「sword」 -> ${sword.size} entries")
                assertTrue("Jitendex must know «sword»", sword.isNotEmpty())

                val terms = sword.take(40).mapNotNull { id -> jitendex.entryById(id)?.term }
                println("first terms: $terms")
                assertTrue(
                    "剣 must be among the words defined with «sword»",
                    sword.any { id -> jitendex.entryById(id)?.term == "剣" },
                )
            }

            // The second open must not rebuild: that is the whole point of the sidecar.
            val startedAgain = System.nanoTime()
            val reopened = mutableListOf<TranslationProgress>()
            val second = TranslationIndex.ensure(jitendex) { reopened.add(it) }
            val openMs = (System.nanoTime() - startedAgain) / 1_000_000
            second.use {
                println("sidecar reopened in ${openMs}ms")
                assertFalse("a ready sidecar must not be rebuilt", reopened.any { progress -> progress.building })
                assertTrue(it.postings("sword").isNotEmpty())
            }
        }
    }
}
