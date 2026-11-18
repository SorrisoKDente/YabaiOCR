package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.dict.LookupEngine
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The hover keeps the dictionaries open while it is on — opening them costs 431 ms on a real
 * install — and must let them go when it is off, because they are 123 MB of key index.
 */
class HoverDictionaryCacheTest {

    private fun settings(dir: File): DesktopSettings =
        DesktopSettings(file = File(dir, "settings.properties"), desktopHome = dir)

    @Test
    fun keepsOneEngineWhileTheFolderIsTheSame() {
        val home = Files.createTempDirectory("yabai-cache").toFile()
        val cache = HoverDictionaryCache({ LookupEngine(emptyList()) }, {})
        val settings = settings(home)

        val first = cache.get(settings)
        val second = cache.get(settings)

        assertSame(first, second)
        cache.release()
    }

    @Test
    fun releaseClosesTheEngineAndTheNextLookupOpensItAgain() {
        val home = Files.createTempDirectory("yabai-cache").toFile()
        var opened = 0
        var closed = 0
        val cache = HoverDictionaryCache(
            opener = { opened += 1; LookupEngine(emptyList()) },
            closer = { closed += 1 },
        )
        val settings = settings(home)

        cache.get(settings)
        assertEquals(1, opened)

        cache.release()
        assertEquals(1, closed)

        cache.get(settings)
        assertEquals(2, opened)
    }

    @Test
    fun aChangedFolderClosesTheOldEngineAndOpensTheNewOne() {
        val first = Files.createTempDirectory("yabai-cache-a").toFile()
        val second = Files.createTempDirectory("yabai-cache-b").toFile()
        var opened = 0
        var closed = 0
        val cache = HoverDictionaryCache(
            opener = { opened += 1; LookupEngine(emptyList()) },
            closer = { closed += 1 },
        )
        val a = settings(first).apply { customDictDirectoryPath = File(first, "dicts").absolutePath }
        val b = settings(second).apply { customDictDirectoryPath = File(second, "dicts").absolutePath }

        cache.get(a)
        cache.get(b)

        assertEquals(2, opened)
        assertEquals(1, closed)
        cache.release()
    }
}
