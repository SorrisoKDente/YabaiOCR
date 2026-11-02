package dev.yabai.ocr.dict

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

/**
 * The last check has to survive the process (R43).
 *
 * The periodic check runs in a worker with no screen; its conclusion is only useful when the user
 * opens «Dicionários» later. The store is also what keeps the row's «Atualizar» from disappearing
 * between visits, and what stops the notifier from posting the same notification every day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateStateStoreTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val available = UpdateResult("jmdict-en", UpdateState.AVAILABLE, remoteLabel = "30/10/2026")
    private val current = UpdateResult("ggjz-pt", UpdateState.CURRENT)
    private val failed = UpdateResult("kanjidic-en", UpdateState.FAILED, message = "HTTP 500")

    @Test
    fun `results and the check time come back unchanged`() {
        val store = UpdateStateStore(context)
        store.save(listOf(available, current, failed), checkedAt = 1_700_000_000_000)

        val loaded = store.load()

        assertEquals(1_700_000_000_000, loaded.checkedAt)
        assertEquals(setOf("jmdict-en", "ggjz-pt", "kanjidic-en"), loaded.results.keys)
        assertEquals(UpdateState.AVAILABLE, loaded.results["jmdict-en"]!!.state)
        assertEquals("30/10/2026", loaded.results["jmdict-en"]!!.remoteLabel)
        assertEquals(UpdateState.FAILED, loaded.results["kanjidic-en"]!!.state)
        assertEquals("HTTP 500", loaded.results["kanjidic-en"]!!.message)
    }

    @Test
    fun `a store that was never written says nothing was checked`() {
        val loaded = UpdateStateStore(context).load()

        assertEquals(0, loaded.checkedAt)
        assertTrue(loaded.results.isEmpty())
    }

    @Test
    fun `a corrupted store does not crash the screen`() {
        context.getSharedPreferences("yabai_ocr_dictionary_updates", Context.MODE_PRIVATE)
            .edit()
            .putString("results", "{not json")
            .commit()

        val loaded = UpdateStateStore(context).load()

        assertEquals(0, loaded.checkedAt)
        assertTrue(loaded.results.isEmpty())
    }

    @Test
    fun `forgetting an id leaves the other conclusions alone`() {
        val store = UpdateStateStore(context)
        store.save(listOf(available, current), checkedAt = 123)

        store.forget("jmdict-en")

        val loaded = store.load()
        assertNull(loaded.results["jmdict-en"])
        assertEquals(UpdateState.CURRENT, loaded.results["ggjz-pt"]!!.state)
        assertEquals(123, loaded.checkedAt)
    }

    @Test
    fun `the notification key is remembered between checks`() {
        val store = UpdateStateStore(context)
        assertEquals("", store.notifiedKey())

        store.setNotifiedKey("jmdict-en:30/10/2026")

        assertEquals("jmdict-en:30/10/2026", UpdateStateStore(context).notifiedKey())
    }
}
