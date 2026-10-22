package dev.yabai.ocr.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * A download can be taken back (R21).
 *
 * A 37 MB archive on mobile data is exactly the thing somebody wants to cancel halfway, and the
 * copy loop is where «cancel» has to mean something: the loop is between the network and the disk,
 * and a coroutine that only stops *after* the last byte has done nothing for the user.
 *
 * The socket is closed by [DictionaryDownloader.download] when the job is cancelled; what is tested
 * here is the other half — that the loop notices the cancellation and stops writing.
 */
class DictionaryDownloaderTest {

    /** An input that hands out [chunks] pieces of [size] bytes, slowly, like a mobile connection. */
    private class SlowInput(private val size: Int, private val chunks: Int) : InputStream() {
        var delivered = 0
            private set

        override fun read(): Int = error("the downloader reads in blocks")

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (delivered >= size * chunks) return -1
            Thread.sleep(5)
            val count = minOf(size, length)
            System.arraycopy(ByteArray(size), 0, buffer, offset, count)
            delivered += count
            return count
        }
    }

    @Test
    fun `cancelling the job stops the copy between chunks`() = runBlocking {
        val size = 64 * 1024
        val chunks = 60
        val total = size.toLong() * chunks
        val input = SlowInput(size, chunks)
        val output = ByteArrayOutputStream()
        var written = 0L
        val downloader = DictionaryDownloader()

        val job = launch(Dispatchers.IO) {
            downloader.copyWithProgress(input, output, total) { read, _ -> written = read }
        }
        delay(120)
        job.cancelAndJoin()

        assertTrue("the job was not cancelled", job.isCancelled)
        assertTrue(
            "the copy ran to the end after being cancelled: $written of $total",
            written < total,
        )
        assertTrue("nothing was written before the cancel, so the test proves nothing", written > 0)
        assertTrue(
            "the output kept far more than the cancelled copy had read",
            output.size().toLong() <= written + size,
        )
    }

    @Test
    fun `a copy left alone keeps every byte`() = runBlocking {
        val size = 4096
        val chunks = 4
        val input = SlowInput(size, chunks)
        val output = ByteArrayOutputStream()
        var last = 0L

        DictionaryDownloader().copyWithProgress(input, output, (size * chunks).toLong()) { read, _ ->
            last = read
        }

        assertEquals("the copy reported less than the file holds", (size * chunks).toLong(), last)
        assertEquals("a finished copy lost bytes", size * chunks, output.size())
    }
}
