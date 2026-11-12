package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictLookupTest {

    @Test
    fun aBrokenDictionaryIsReportedInsteadOfThrown() {
        // Changing the dictionary in the popup re-ran the query inside a LaunchedEffect; when the
        // dictionary could not be read the exception escaped and Compose showed «Fatal exception in
        // coroutines machinery» in a system dialog.
        val state = DictLookup.run("これはテストです", emptySet()) { _, _ ->
            throw IllegalStateException("closed dictionary")
        }

        assertEquals(emptyList<Any>(), state.matches)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("closed dictionary"))
    }

    @Test
    fun aWorkingDictionaryStillReturnsItsMatches() {
        val state = DictLookup.run("テスト", setOf("Jitendex")) { text, only ->
            assertEquals("テスト", text)
            assertEquals(setOf("Jitendex"), only)
            emptyList()
        }

        assertEquals(emptyList<Any>(), state.matches)
        assertEquals(null, state.error)
    }
}
