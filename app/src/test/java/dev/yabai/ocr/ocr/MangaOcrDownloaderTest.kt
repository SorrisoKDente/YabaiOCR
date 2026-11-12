package dev.yabai.ocr.ocr

import dev.yabai.ocr.core.manga.MangaOcrModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MangaOcrDownloaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDownloaderInitialization() {
        val dir = tempFolder.newFolder("manga")
        val store = MangaOcrModelStore(dir)
        val downloader = MangaOcrDownloader(store)
        assertNotNull(downloader)
    }

    @Test
    fun testSkipsExistingFiles() = runBlocking {
        val dir = tempFolder.newFolder("manga_existing")
        val store = MangaOcrModelStore(dir)
        File(dir, MangaOcrModelStore.DETECTOR_FILENAME).writeText("fake detector content")
        File(dir, MangaOcrModelStore.RECOGNIZER_FILENAME).writeText("fake recognizer content")
        File(dir, MangaOcrModelStore.VOCAB_FILENAME).writeText("fake vocab content")

        val downloader = MangaOcrDownloader(store)
        val reportedLabels = mutableListOf<String>()
        downloader.downloadModels { label, read, total ->
            reportedLabels.add(label)
            assertEquals(read, total)
        }

        assertEquals(3, reportedLabels.size)
        assertEquals(true, store.isAvailable())
    }

    @Test
    fun testDownloadFailureCleansUpTempFiles() = runBlocking {
        val dir = tempFolder.newFolder("manga_fail")
        val store = MangaOcrModelStore(dir)
        val downloader = MangaOcrDownloader(store)
        runCatching { downloader.downloadModels { _, _, _ -> } }
        assertEquals(0, dir.listFiles()?.filter { it.name.endsWith(".tmp") }?.size ?: 0)
    }
}
