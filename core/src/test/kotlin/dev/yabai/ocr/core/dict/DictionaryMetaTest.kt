package dev.yabai.ocr.core.dict

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a compiled dictionary says which version wrote it.
 *
 * The definitions are rendered once, when the dictionary is imported, and stored as text. Fixing the
 * renderer therefore does nothing for a dictionary already on the phone until it is imported again —
 * the app says so, and this is the flag it asks (R19).
 */
class DictionaryMetaTest {

    private fun read(json: String): DictionaryInfo {
        val file = File.createTempFile("meta", ".json")
        file.writeText(json)
        try {
            return CompactDictionary.readMeta(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a dictionary compiled with the current version is up to date`() {
        val info = read("""{"title":"JMdict","compiledWith":${DictionaryCompiler.FORMAT_VERSION}}""")

        assertEquals(DictionaryCompiler.FORMAT_VERSION, info.compiledWith)
        assertFalse(info.outdated)
    }

    @Test
    fun `a dictionary from an older version asks to be imported again`() {
        val info = read("""{"title":"JMdict","compiledWith":1}""")

        assertEquals(1, info.compiledWith)
        assertTrue(info.outdated)
    }

    @Test
    fun `a dictionary with no version stamp counts as old`() {
        assertTrue(read("""{"title":"JMdict"}""").outdated)
    }
}
