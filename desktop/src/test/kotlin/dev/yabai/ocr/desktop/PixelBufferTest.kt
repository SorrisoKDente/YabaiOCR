package dev.yabai.ocr.desktop

import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The hover must not allocate a fresh pixels array for every read.
 *
 * A read copies the crop — the whole monitor height, ~8 MB at 1080p — into an `IntArray`, and the
 * continuous hover runs one every few hundred milliseconds. Allocating it per read filled the young
 * generation with buffers that were overwritten before being read. The buffer is kept and handed
 * back while the size matches, and replaced only when the image size really changed.
 */
class PixelBufferTest {

    @Test
    fun `the same size gives the same buffer back`() {
        val buffer = PixelBuffer()
        val first = buffer.of(1920 * 892)
        val second = buffer.of(1920 * 892)
        assertSame(
            "a read of the same size allocated a new pixels array: the hover is back to churning " +
                "8 MB per read",
            first,
            second,
        )
        assertEquals(1920 * 892, buffer.size)
    }

    @Test
    fun `a different size gets its own buffer`() {
        val buffer = PixelBuffer()
        val full = buffer.of(1920 * 892)
        val smaller = buffer.of(960 * 446)
        assertNotSame("a smaller image reused the bigger buffer and its stale pixels", full, smaller)
        assertEquals(960 * 446, smaller.size)
    }

    @Test
    fun `the buffer can come back after a size change`() {
        val buffer = PixelBuffer()
        buffer.of(1920 * 892)
        buffer.of(640 * 480)
        val back = buffer.of(1920 * 892)
        assertEquals(1920 * 892, back.size)
        assertSame("the buffer for a size must be stable, not rotated per call", back, buffer.of(1920 * 892))
    }

    @Test
    fun `a negative size is refused`() {
        assertThrows(IllegalArgumentException::class.java) { PixelBuffer().of(-1) }
    }
}
