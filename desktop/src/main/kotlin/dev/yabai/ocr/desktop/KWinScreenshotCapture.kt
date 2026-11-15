package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import org.freedesktop.dbus.FileDescriptor as DBusFileDescriptor
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.newsclub.net.unix.FileDescriptorCast

/** D-Bus surface of `org.kde.KWin.ScreenShot2` (only the capture methods the app uses). */
@DBusInterfaceName("org.kde.KWin.ScreenShot2")
internal interface KWinScreenshotDbus : DBusInterface {

    @DBusMemberName("CaptureArea")
    fun captureArea(
        x: Int,
        y: Int,
        width: UInt32,
        height: UInt32,
        options: Map<String, Variant<*>>,
        pipe: DBusFileDescriptor,
    ): Map<String, Variant<*>>

    @DBusMemberName("CaptureWorkspace")
    fun captureWorkspace(
        options: Map<String, Variant<*>>,
        pipe: DBusFileDescriptor,
    ): Map<String, Variant<*>>
}

/** Isolates the KWin transport from raw-image conversion and backend selection. */
internal interface KWinScreenshotTransport {
    val lastError: String? get() = null

    fun isAvailable(): Boolean
    fun captureWorkspace(): BufferedImage
    fun captureArea(rect: Rectangle): BufferedImage
    fun close()
}

/**
 * KDE Wayland capture through `org.kde.KWin.ScreenShot2`.
 *
 * This is the non-interactive path: unlike the Screenshot portal, KWin does not present a permission
 * dialog for every call. The compositor writes a raw QImage through a file descriptor; the request
 * is non-blocking, so there is no reader thread and no pipe-buffer deadlock.
 */
internal class KWinScreenshotCapture(
    private val transportFactory: () -> KWinScreenshotTransport? = { DbusKWinScreenshotTransport() },
    private val virtualBounds: () -> Rectangle = ::virtualScreenBounds,
    private val primaryBounds: () -> Rectangle = ::primaryScreenBounds,
) : ScreenCaptureBackend {

    @Volatile
    private var failure: CaptureAvailability? = null

    private var transport: KWinScreenshotTransport? = null

    override fun availability(): CaptureAvailability {
        if (failure == CaptureAvailability.DENIED) return CaptureAvailability.DENIED
        return if (ensureTransport() != null) {
            CaptureAvailability.READY
        } else {
            CaptureAvailability.UNSUPPORTED
        }
    }

    override fun screenBounds(): Rectangle = primaryBounds()

    override fun captureScreen(): BufferedImage = guard {
        cropWorkspace(captureWorkspace(), virtualBounds(), primaryBounds()).also { failure = null }
    }

    override fun captureRegion(rect: Rectangle): BufferedImage = guard {
        val active = ensureTransport()
            ?: throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                captureAvailabilityMessage(CaptureAvailability.UNSUPPORTED),
            )
        active.captureArea(rect).also { failure = null }
    }

    /**
     * A whole monitor. The primary keeps the workspace capture it always used, because cropping the
     * workspace is what produced this app's own pixels so far; any other monitor asks KWin for that
     * area directly, which is the same call the region capture already makes.
     */
    override fun captureMonitor(bounds: Rectangle): BufferedImage =
        if (bounds == primaryBounds()) captureScreen() else captureRegion(bounds)

    private fun captureWorkspace(): BufferedImage {
        val active = ensureTransport()
            ?: throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                captureAvailabilityMessage(CaptureAvailability.UNSUPPORTED),
            )
        return active.captureWorkspace()
    }

    private fun ensureTransport(): KWinScreenshotTransport? {
        transport?.let { return it }
        val created = runCatching(transportFactory).getOrNull() ?: return null
        if (!runCatching { created.isAvailable() }.getOrDefault(false)) {
            runCatching { created.close() }
            return null
        }
        transport = created
        return created
    }

    private inline fun <T> guard(action: () -> T): T = try {
        action()
    } catch (e: ScreenCaptureException) {
        if (e.availability == CaptureAvailability.DENIED) failure = CaptureAvailability.DENIED
        throw e
    } catch (e: SecurityException) {
        failure = CaptureAvailability.DENIED
        throw ScreenCaptureException(
            CaptureAvailability.DENIED,
            captureAvailabilityMessage(CaptureAvailability.DENIED),
            e,
        )
    } catch (e: Exception) {
        throw ScreenCaptureException(
            CaptureAvailability.UNSUPPORTED,
            DesktopStrings.kwinCaptureFailed(e.message ?: e::class.simpleName.orEmpty()),
            e,
        )
    }
}

/**
 * Direct KDE Wayland path with the portal as a compatibility fallback.
 *
 * If ScreenShot2 is absent (older KWin) or refuses the call, the portal still captures; the app never
 * silently falls back to the X11 island. A failed direct call pauses the direct path for
 * [retryDelayMillis] instead of disabling it forever: KWin only authorizes a caller whose desktop
 * entry is already indexed, and that index (KSycoca) can catch up a moment after the app wrote its
 * entry. Retrying costs one rejected D-Bus call — no dialog — while giving up permanently would keep
 * every hover on the portal, which presents a permission window per capture.
 */
internal class KdeWaylandCapture(
    private val direct: ScreenCaptureBackend = KWinScreenshotCapture(),
    private val portal: ScreenCaptureBackend = PortalScreenshotCapture(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryDelayMillis: Long = DIRECT_RETRY_DELAY_MS,
    private val report: (String) -> Unit = { message -> System.err.println(message) },
) : ScreenCaptureBackend {

    @Volatile
    private var directRetryAt = 0L

    private fun directReady(): Boolean =
        clock() >= directRetryAt && direct.availability() == CaptureAvailability.READY

    override fun availability(): CaptureAvailability {
        if (directReady()) return CaptureAvailability.READY
        return portal.availability()
    }

    override fun screenBounds(): Rectangle = direct.screenBounds()

    override fun captureScreen(): BufferedImage = capture { it.captureScreen() }

    override fun captureRegion(rect: Rectangle): BufferedImage = capture { it.captureRegion(rect) }

    private inline fun <T> capture(action: (ScreenCaptureBackend) -> T): T {
        if (directReady()) {
            try {
                val captured = action(direct)
                directRetryAt = 0L
                return captured
            } catch (e: ScreenCaptureException) {
                reportRefusal(e.message)
                directRetryAt = clock() + retryDelayMillis
            } catch (e: Exception) {
                reportRefusal(e.describe())
                directRetryAt = clock() + retryDelayMillis
            }
        }
        return action(portal)
    }

    /**
     * Why the direct capture was refused, in the only log the packaged app has.
     *
     * Without this line the only symptom of a refused `ScreenShot2` call is the portal's permission
     * window on every hover — which says nothing about the cause (usually KWin not finding a desktop
     * entry that authorizes this caller).
     */
    private fun reportRefusal(detail: String?) {
        report(
            "Yabai OCR: captura direta pelo KWin recusada (${detail ?: "sem detalhe"}); usando o portal",
        )
    }

    private companion object {
        const val DIRECT_RETRY_DELAY_MS = 10_000L
    }
}

/**
 * Real ScreenShot2 transport.
 *
 * KWin writes `stride * height` bytes to the passed file descriptor after replying. A regular
 * temporary file is used instead of a pipe: KWin marks the descriptor non-blocking, and a pipe that
 * nobody drains would make the compositor drop the frame. Waiting for the file length gives the same
 * guarantee without a thread or a buffer size tied to the image.
 */
internal class DbusKWinScreenshotTransport : KWinScreenshotTransport {

    private var connection: DBusConnection? = null
    private var screenshot: KWinScreenshotDbus? = null

    override var lastError: String? = null
        private set

    override fun isAvailable(): Boolean = runCatching {
        ensureConnected()
        connection?.getDBusOwnerName(KWIN_BUS) != null
    }.getOrElse { error ->
        lastError = error.describe()
        logDbusFailure("consultar o KWin ScreenShot2", error)
        false
    }

    override fun captureWorkspace(): BufferedImage = capture {
        screenshot!!.captureWorkspace(captureOptions(), it)
    }

    override fun captureArea(rect: Rectangle): BufferedImage = capture {
        screenshot!!.captureArea(
            rect.x,
            rect.y,
            UInt32(rect.width.toLong()),
            UInt32(rect.height.toLong()),
            captureOptions(),
            it,
        )
    }

    override fun close() {
        runCatching { connection?.close() }
        connection = null
        screenshot = null
    }

    private fun capture(call: (DBusFileDescriptor) -> Map<String, Variant<*>>): BufferedImage {
        val temp = Files.createTempFile("yabai-kwin-shot-", ".raw")
        try {
            ensureConnected()
            RandomAccessFile(temp.toFile(), "rw").use { file ->
                file.setLength(0)
                val fd = fileDescriptor(file)
                    ?: throw ScreenCaptureException(
                        CaptureAvailability.UNSUPPORTED,
                        DesktopStrings.kwinCaptureNoDescriptor,
                    )
                val results = call(DBusFileDescriptor(fd))
                val width = results.uint("width")
                val height = results.uint("height")
                val stride = results.uint("stride")
                val format = results.uint("format")
                if (width == null || height == null || stride == null || format == null ||
                    width <= 0 || height <= 0 || stride <= 0
                ) {
                    throw ScreenCaptureException(
                        CaptureAvailability.UNSUPPORTED,
                        DesktopStrings.kwinCaptureNoMetadata,
                    )
                }

                val expected = stride.toLong() * height.toLong()
                if (expected <= 0 || expected > Int.MAX_VALUE) {
                    throw ScreenCaptureException(
                        CaptureAvailability.UNSUPPORTED,
                        DesktopStrings.kwinCaptureTooLarge,
                    )
                }
                awaitLength(file, temp, expected)
                val bytes = ByteArray(expected.toInt())
                file.seek(0)
                file.readFully(bytes)
                lastError = null
                return kwinRawImage(bytes, width, height, stride, format)
            }
        } catch (e: ScreenCaptureException) {
            lastError = e.message
            throw e
        } catch (e: Exception) {
            lastError = e.describe()
            logDbusFailure("capturar a tela pelo KWin", e)
            throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                DesktopStrings.kwinCaptureFailed(e.message ?: e::class.simpleName.orEmpty()),
                e,
            )
        } finally {
            runCatching { Files.deleteIfExists(temp) }
        }
    }

    private fun awaitLength(file: RandomAccessFile, path: Path, expected: Long) {
        val deadline = System.nanoTime() + WRITE_TIMEOUT_NANOS
        while (file.length() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        if (file.length() < expected) {
            throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                DesktopStrings.kwinCaptureIncomplete(path.toString()),
            )
        }
    }

    private fun fileDescriptor(file: RandomAccessFile): Int? = runCatching {
        // junixsocket reads the descriptor through its native helper instead of reflecting on
        // java.io.FileDescriptor.fd, so this keeps working in the strongly encapsulated JDK 21
        // runtime used by the packaged app.
        FileDescriptorCast.using(file.fd).`as`(Integer::class.java).toInt()
    }.getOrElse { error ->
        // A missing native transport must fall back to the portal, not abort the hover path.
        lastError = error.describe()
        null
    }

    private fun captureOptions(): Map<String, Variant<*>> = mapOf(
        "native-resolution" to Variant(true, "b"),
        "hide-caller-windows" to Variant(true, "b"),
        "include-cursor" to Variant(false, "b"),
    )

    private fun ensureConnected() {
        if (connection != null && screenshot != null) return
        val created = DBusConnectionBuilder.forSessionBus().build()
        connection = created
        screenshot = created.getRemoteObject(KWIN_BUS, KWIN_PATH, KWinScreenshotDbus::class.java)
    }

    private companion object {
        const val KWIN_BUS = "org.kde.KWin.ScreenShot2"
        const val KWIN_PATH = "/org/kde/KWin/ScreenShot2"
        const val WRITE_TIMEOUT_NANOS = 10_000_000_000L
    }
}

private fun Map<String, Variant<*>>.uint(key: String): Int? = when (val value = this[key]?.value) {
    is Number -> value.toInt()
    else -> null
}

/** Byte order of the three color channels inside one raw pixel, as Qt stores it. */
internal enum class RawChannelOrder { RGBA, BGRA }

/**
 * Accepted `QImage::Format` values, mapped to their memory layout.
 *
 * KWin writes whatever format the compositor's scene produced: on a Plasma 6 / Steam Deck session
 * that is `ARGB32_Premultiplied` (6), while a boxed `RGBX8888` (16) also happens. Both are four
 * bytes per pixel, only the channel order differs — decoding them with one fixed order would swap
 * red and blue and corrupt every glyph the OCR reads.
 */
internal fun rawChannelOrder(format: Int): RawChannelOrder? = when (format) {
    QIMAGE_RGBX8888, QIMAGE_RGBA8888 -> RawChannelOrder.RGBA
    QIMAGE_RGB32, QIMAGE_ARGB32, QIMAGE_ARGB32_PREMULTIPLIED, QIMAGE_BGRA8888 -> RawChannelOrder.BGRA
    else -> null
}

/**
 * Converts the raw QImage written by KWin. Only the formats the compositor actually creates are
 * accepted; guessing another layout would silently swap channels and corrupt the OCR text.
 */
internal fun kwinRawImage(
    bytes: ByteArray,
    width: Int,
    height: Int,
    stride: Int,
    format: Int,
): BufferedImage {
    require(width > 0 && height > 0 && stride >= width * 4) { "invalid raw image size" }
    require(bytes.size >= stride * height) { "raw image is truncated" }
    val order = rawChannelOrder(format)
    require(order != null) { "unsupported QImage format: $format" }

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        var offset = y * stride
        val row = y * width
        for (x in 0 until width) {
            val first = bytes[offset].toInt() and 0xFF
            val second = bytes[offset + 1].toInt() and 0xFF
            val third = bytes[offset + 2].toInt() and 0xFF
            val (r, g, b) = when (order) {
                RawChannelOrder.RGBA -> Triple(first, second, third)
                RawChannelOrder.BGRA -> Triple(third, second, first)
            }
            pixels[row + x] = (r shl 16) or (g shl 8) or b
            offset += 4
        }
    }
    image.setRGB(0, 0, width, height, pixels, 0, width)
    return image
}

// QImage::Format values used above.
private const val QIMAGE_RGB32 = 4
private const val QIMAGE_ARGB32 = 5
private const val QIMAGE_ARGB32_PREMULTIPLIED = 6
private const val QIMAGE_RGBX8888 = 16
private const val QIMAGE_RGBA8888 = 17
private const val QIMAGE_BGRA8888 = 18
