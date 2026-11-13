package dev.yabai.ocr.desktop

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.newsclub.net.unix.FileDescriptorCast

class KWinScreenshotCaptureTest {

    private class FakeTransport(
        private val available: Boolean = true,
        private val workspace: BufferedImage = testImage(100, 100),
        private val region: BufferedImage = testImage(10, 10),
        private var failure: RuntimeException? = null,
    ) : KWinScreenshotTransport {
        var workspaceCalls = 0
        var regionCalls = 0
        var lastRegion: Rectangle? = null

        override fun isAvailable(): Boolean = available

        override fun captureWorkspace(): BufferedImage {
            workspaceCalls++
            failure?.let { throw it }
            return workspace
        }

        override fun captureArea(rect: Rectangle): BufferedImage {
            regionCalls++
            lastRegion = rect
            failure?.let { throw it }
            return region
        }

        override fun close() = Unit
    }

    private fun rawRgbx(width: Int, height: Int, stride: Int, pixels: List<Triple<Int, Int, Int>>): ByteArray {
        val bytes = ByteArray(stride * height)
        pixels.forEachIndexed { index, (r, g, b) ->
            val offset = index * 4
            bytes[offset] = r.toByte()
            bytes[offset + 1] = g.toByte()
            bytes[offset + 2] = b.toByte()
            bytes[offset + 3] = 0xFF.toByte()
        }
        return bytes
    }

    @Test
    fun `raw rgbx bytes become the exact awt colors`() {
        val bytes = rawRgbx(
            width = 2,
            height = 1,
            stride = 12,
            pixels = listOf(Triple(255, 0, 0), Triple(0, 255, 0)),
        )

        val image = kwinRawImage(bytes, width = 2, height = 1, stride = 12, format = 16)

        assertEquals(0xFFFF0000.toInt(), image.getRGB(0, 0))
        assertEquals(0xFF00FF00.toInt(), image.getRGB(1, 0))
    }

    @Test
    fun `argb32 premultiplied bytes are bgra and keep the colors`() {
        // Plasma 6 on the Steam Deck answers CaptureArea with QImage::Format_ARGB32_Premultiplied
        // (6), whose bytes are B,G,R,A. Decoding those as RGBX swapped red and blue.
        val bytes = byteArrayOf(
            0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(),
            0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(),
        )

        val image = kwinRawImage(bytes, width = 2, height = 1, stride = 8, format = 6)

        assertEquals(0xFFFF0000.toInt(), image.getRGB(0, 0))
        assertEquals(0xFF00FF00.toInt(), image.getRGB(1, 0))
    }

    @Test
    fun `an unknown qimage format is rejected instead of swapping channels`() {
        val bytes = rawRgbx(1, 1, 4, listOf(Triple(1, 2, 3)))

        val error = runCatching { kwinRawImage(bytes, 1, 1, 4, format = 3) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `a refused direct capture is retried after the cooldown instead of staying disabled`() {
        val direct = TestBackend("direct", capture = { testImage(5, 5) })
        var now = 0L
        val backend = KdeWaylandCapture(
            direct = object : ScreenCaptureBackend by direct {
                var failures = 1
                override fun captureScreen() = if (failures-- > 0) {
                    throw ScreenCaptureException(CaptureAvailability.UNSUPPORTED, "NoAuthorized")
                } else {
                    direct.captureScreen()
                }
            },
            portal = TestBackend("portal", capture = { testImage(9, 9) }),
            clock = { now },
            retryDelayMillis = 30_000L,
        )

        assertEquals(9, backend.captureScreen().width)
        // During the cooldown the rejected call is not repeated on every hover.
        assertEquals(9, backend.captureScreen().width)
        now += 30_001L
        assertEquals(5, backend.captureScreen().width)
    }

    @Test
    fun `the backend captures the workspace and crops the primary screen`() {
        val transport = FakeTransport()
        val backend = KWinScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { Rectangle(0, 0, 100, 100) },
            primaryBounds = { Rectangle(10, 10, 50, 50) },
        )

        val image = backend.captureScreen()

        assertEquals(50, image.width)
        assertEquals(50, image.height)
        assertEquals(1, transport.workspaceCalls)
        assertEquals(0, transport.regionCalls)
    }

    @Test
    fun `the backend uses screen shot 2 area for a region`() {
        val transport = FakeTransport()
        val backend = KWinScreenshotCapture({ transport })
        val rect = Rectangle(20, 30, 10, 10)

        val image = backend.captureRegion(rect)

        assertEquals(10, image.width)
        assertEquals(rect, transport.lastRegion)
        assertEquals(1, transport.regionCalls)
        assertEquals(0, transport.workspaceCalls)
    }

    @Test
    fun `a missing screen shot 2 service is unsupported`() {
        val backend = KWinScreenshotCapture({ FakeTransport(available = false) })
        assertEquals(CaptureAvailability.UNSUPPORTED, backend.availability())
    }

    @Test
    fun `kde wayland prefers the direct backend and falls back once when it fails`() {
        val direct = TestBackend("direct", capture = { throw ScreenCaptureException(CaptureAvailability.UNSUPPORTED, "no") })
        val portal = TestBackend("portal", capture = { testImage(7, 7) })
        val backend = KdeWaylandCapture(direct, portal)

        assertEquals(7, backend.captureScreen().width)
        assertEquals(1, direct.captures)
        assertEquals(1, portal.captures)

        // While the cooldown lasts, hover does not pay the refused call again.
        assertEquals(7, backend.captureScreen().width)
        assertEquals(1, direct.captures)
        assertEquals(2, portal.captures)
    }

    @Test
    fun `kde wayland uses the direct backend while it is available`() {
        val direct = TestBackend("direct", capture = { testImage(3, 3) })
        val portal = TestBackend("portal", capture = { testImage(9, 9) })
        val backend = KdeWaylandCapture(direct, portal)

        assertEquals(3, backend.captureScreen().width)
        assertEquals(1, direct.captures)
        assertEquals(0, portal.captures)
    }

    @Test
    fun `junixsocket converts a java file descriptor without add-opens`() {
        assumeTrue(
            "junixsocket FD passing is the Linux/Wayland path",
            System.getProperty("os.name").lowercase().contains("linux"),
        )
        val file = Files.createTempFile("yabai-fd-test-", ".bin")
        try {
            RandomAccessFile(file.toFile(), "rw").use { raf ->
                val fd = FileDescriptorCast.using(raf.fd).`as`(Integer::class.java)
                assertTrue("the native descriptor must be valid, got $fd", fd > 0)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `the classpath keeps only the fd capable unix transport`() {
        Class.forName("org.freedesktop.dbus.transport.junixsocket.JUnixSocketUnixTransport")
        try {
            Class.forName("org.freedesktop.dbus.transport.jre.NativeUnixSocketTransport")
            fail("the JDK native transport cannot pass SCM_RIGHTS and must not be on the classpath")
        } catch (_: ClassNotFoundException) {
            // Expected: the junixsocket transport is the only UNIX transport provider.
        }
    }

    private class TestBackend(
        private val name: String,
        private val available: CaptureAvailability = CaptureAvailability.READY,
        private val capture: () -> BufferedImage,
    ) : ScreenCaptureBackend {
        var captures = 0

        override fun availability(): CaptureAvailability = available
        override fun screenBounds(): Rectangle = Rectangle(0, 0, 1, 1)
        override fun captureScreen(): BufferedImage {
            captures++
            return capture()
        }

        override fun captureRegion(rect: Rectangle): BufferedImage = captureScreen()
        override fun toString(): String = name
    }
}

private fun testImage(width: Int, height: Int): BufferedImage =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
