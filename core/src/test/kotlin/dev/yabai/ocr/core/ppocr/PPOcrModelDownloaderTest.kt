package dev.yabai.ocr.core.ppocr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The downloader's offline half: what it does when the files are already there, and what it leaves
 * behind when a download fails.
 */
class PPOcrModelDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `installed files are reported and not downloaded again`() = runBlocking {
        val dir = tempFolder.newFolder("ppocr")
        val store = PPOcrModelStore(dir)
        store.detectorFile.writeText("detector")
        store.recognizerFile.writeText("recognizer")
        store.dictionaryFile.writeText("dict")

        val labels = mutableListOf<String>()
        PPOcrModelDownloader(store).downloadModels { label, read, total ->
            labels += label
            assertEquals(read, total)
        }

        assertEquals(listOf("Detector", "Recognizer", "Dictionary"), labels)
        assertTrue(store.isAvailable())
    }

    @Test
    fun `a store without the dictionary is not available`() {
        val dir = tempFolder.newFolder("partial")
        val store = PPOcrModelStore(dir)
        store.detectorFile.writeText("detector")
        store.recognizerFile.writeText("recognizer")

        assertFalse(store.isAvailable())
    }

}
