package dev.yabai.ocr.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card that runs a catalogue install has one rule that matters more than the rest: it must come
 * back from «baixando», whatever happened.
 *
 * Cancelling used to break exactly that. The reset lived in a `finally` that dispatched to the UI
 * thread with a plain `withContext(Dispatchers.Main)`, which throws immediately once the job has been
 * cancelled — the progress bar stayed on screen forever with nothing running behind it.
 */
class DictionaryInstallRunTest {

    @Test
    fun `a cancelled install still resets the card`() = runBlocking {
        val events = mutableListOf<String>()
        val job = launch {
            runCatalogueInstall(
                install = { awaitCancellation() },
                hooks = CatalogueInstallHooks(
                    cancelled = { events += "cancelled" },
                    // Dispatched like the UI reset is, so a cancelled context behaves as it does in
                    // the app.
                    finished = { withContext(Dispatchers.Default) { events += "finished" } },
                ),
            )
        }
        yield()
        job.cancelAndJoin()

        assertTrue("the card never got its final state: $events", events.contains("finished"))
        assertTrue("the card never learned the install was cancelled: $events", events.contains("cancelled"))
    }

    @Test
    fun `cancelling reports a cancellation, not a failure`() = runBlocking {
        val events = mutableListOf<String>()
        runCatalogueInstall(
            install = { throw DictionaryDownloadCancelled() },
            hooks = CatalogueInstallHooks(
                installed = { events += "installed" },
                cancelled = { events += "cancelled" },
                failed = { events += "failed" },
                finished = { events += "finished" },
            ),
        )

        assertEquals(listOf("cancelled", "finished"), events)
    }

    @Test
    fun `nothing runs after a finished install and the reset comes last`() = runBlocking {
        val events = mutableListOf<String>()
        runCatalogueInstall(
            install = {},
            hooks = CatalogueInstallHooks(
                installed = { events += "installed" },
                cancelled = { events += "cancelled" },
                finished = { events += "finished" },
            ),
        )

        assertEquals(listOf("installed", "finished"), events)
    }

    @Test
    fun `an error is reported as a failure and still resets`() = runBlocking {
        val events = mutableListOf<String>()
        val messages = mutableListOf<String>()
        runCatalogueInstall(
            install = { throw IllegalStateException("sem rede") },
            hooks = CatalogueInstallHooks(
                installed = { events += "installed" },
                failed = { message ->
                    messages += message
                    events += "failed"
                },
                finished = { events += "finished" },
            ),
        )

        assertEquals(listOf("failed", "finished"), events)
        assertEquals("sem rede", messages.single())
        assertFalse("a failure must not be reported as an install", "installed" in events)
    }
}
