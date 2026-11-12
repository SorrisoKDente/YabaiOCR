package dev.yabai.ocr.desktop

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopDictLoaderSizeTest {

    private val root = Files.createTempDirectory("yabai-dict-size").toFile()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun aCompiledDictionaryFolderReportsTheTotalOfItsParts() {
        // A `.yabai` entry is a directory: File.length() answers 0 for it, and the manager showed
        // "0.0 MB" for every installed dictionary until the folder was walked.
        val dict = root.resolve("jiten.yabai")
        dict.mkdirs()
        dict.resolve("terms.bin").writeBytes(ByteArray(4096))
        dict.resolve("index.bin").writeBytes(ByteArray(1024))
        val nested = dict.resolve("nested")
        nested.mkdirs()
        nested.resolve("meta.bin").writeBytes(ByteArray(512))

        assertEquals(4096L + 1024L + 512L, DesktopDictLoader.sizeBytes(dict))
    }

    @Test
    fun aSingleDictionaryFileReportsItsOwnLength() {
        val file = root.resolve("plain.yabai")
        file.writeBytes(ByteArray(2048))

        assertEquals(2048L, DesktopDictLoader.sizeBytes(file))
    }
}
