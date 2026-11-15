package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

/** D-Bus surface of `org.freedesktop.portal.Screenshot` (only the method the app uses). */
@DBusInterfaceName("org.freedesktop.portal.Screenshot")
internal interface PortalScreenshotDbus : DBusInterface {

    @DBusMemberName("Screenshot")
    fun screenshot(parentWindow: String, options: Map<String, Variant<*>>): DBusPath
}

/** Isolates the real portal from the crop/selection logic so the latter can be tested headlessly. */
internal interface ScreenshotPortalTransport {
    val lastError: String? get() = null

    fun isAvailable(): Boolean
    fun captureWorkspace(): BufferedImage
    fun close()
}

/**
 * Wayland capture through `org.freedesktop.portal.Screenshot` with `interactive=false`.
 *
 * This is the main path on GNOME and the compatibility fallback behind [KdeWaylandCapture]. It
 * deliberately does not use `Robot`'s ScreenCast path: no compositor reuses the JDK's restore token
 * (JDK-8396405 rejects KDE's outright), so every capture would open a new ScreenCast session and ask
 * for the screen again — the Shell sharing indicator blinking on every hover, seconds of latency and
 * usually no text at all. `Screenshot` is one-shot: no session to renew, no dialog, ~130 ms here.
 *
 * The `interactive=false` option does not make KDE capture silently: Plasma creates and presents a
 * `QuickDialog` permission window for every call. That is why the direct ScreenShot2 path is
 * preferred there and this portal is only used when the compositor does not expose it. GNOME honors
 * the option without any window.
 *
 * The portal always captures the whole workspace, so this backend crops the requested rectangle
 * from that image. On a single-monitor Steam Deck the crop is the whole screen.
 */
internal class PortalScreenshotCapture(
    private val transportFactory: () -> ScreenshotPortalTransport? = { DbusScreenshotPortalTransport() },
    private val virtualBounds: () -> Rectangle = ::virtualScreenBounds,
    private val primaryBounds: () -> Rectangle = ::primaryScreenBounds,
) : ScreenCaptureBackend {

    @Volatile
    private var failure: CaptureAvailability? = null

    private var transport: ScreenshotPortalTransport? = null

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
        val workspace = captureWorkspace()
        cropWorkspace(workspace, virtualBounds(), primaryBounds()).also { failure = null }
    }

    override fun captureRegion(rect: Rectangle): BufferedImage = guard {
        val workspace = captureWorkspace()
        cropWorkspace(workspace, virtualBounds(), rect).also { failure = null }
    }

    private fun captureWorkspace(): BufferedImage {
        val active = ensureTransport()
            ?: throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                captureAvailabilityMessage(CaptureAvailability.UNSUPPORTED),
            )
        return active.captureWorkspace()
    }

    private fun ensureTransport(): ScreenshotPortalTransport? {
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
            "Não foi possível capturar a tela pelo portal de captura: ${e.message ?: e::class.simpleName}",
            e,
        )
    }
}

/**
 * What a non-zero `Response` code means.
 *
 * 1 is the user cancelling the dialog (a refusal); 2 is «other error» — no permission dialog was
 * shown at all, so telling the user to authorize one is wrong.
 */
internal fun portalCaptureFailure(code: Int): CaptureAvailability =
    if (code == 1) CaptureAvailability.DENIED else CaptureAvailability.UNSUPPORTED

/** Options for `org.freedesktop.portal.Screenshot.Screenshot`. */
internal fun screenshotPortalOptions(
    handleToken: String,
    interactive: Boolean = false,
): Map<String, Variant<*>> = mapOf(
    "handle_token" to Variant(handleToken, "s"),
    "interactive" to Variant(interactive, "b"),
    "modal" to Variant(false, "b"),
)

internal fun virtualScreenBounds(): Rectangle {
    val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    if (devices.isEmpty()) return primaryScreenBounds()
    return devices.drop(1).fold(devices.first().defaultConfiguration.bounds) { bounds, device ->
        bounds.union(device.defaultConfiguration.bounds)
    }
}

/**
 * Crops [target] (AWT user coordinates) from a workspace image in native pixels.
 *
 * The portal image covers all monitors. The ratio between its width and the AWT virtual bounds is
 * the native scale; on a single display that is the display scale, so a 150% screen yields a 1.5x
 * image and the picker keeps drawing every glyph at native size.
 */
internal fun cropWorkspace(
    workspace: BufferedImage,
    virtualBounds: Rectangle,
    target: Rectangle,
): BufferedImage {
    require(virtualBounds.width > 0 && virtualBounds.height > 0) {
        "virtual screen bounds are empty"
    }
    val clipped = target.intersection(virtualBounds)
    require(!clipped.isEmpty) { "capture rectangle is outside the virtual screen" }

    val scaleX = workspace.width.toDouble() / virtualBounds.width
    val scaleY = workspace.height.toDouble() / virtualBounds.height

    val left = ((clipped.x - virtualBounds.x) * scaleX).roundToInt()
        .coerceIn(0, workspace.width - 1)
    val top = ((clipped.y - virtualBounds.y) * scaleY).roundToInt()
        .coerceIn(0, workspace.height - 1)
    val right = ((clipped.x + clipped.width - virtualBounds.x) * scaleX).roundToInt()
        .coerceIn(left + 1, workspace.width)
    val bottom = ((clipped.y + clipped.height - virtualBounds.y) * scaleY).roundToInt()
        .coerceIn(top + 1, workspace.height)

    return workspace.getSubimage(left, top, right - left, bottom - top)
}

/**
 * The portal saves the PNG under `~/Pictures` and returns its URI. The app owns that one file: read
 * it into memory and remove it so repeated OCR captures do not fill the user's picture folder.
 */
internal fun readPortalScreenshot(uri: String): BufferedImage {
    val file = when {
        uri.startsWith("file:") -> File(URI(uri))
        else -> File(uri)
    }
    try {
        return ImageIO.read(file) ?: throw IllegalStateException("o portal devolveu uma imagem vazia")
    } finally {
        runCatching { Files.deleteIfExists(file.toPath()) }
    }
}

/**
 * Real D-Bus transport. `Screenshot` returns a request object; the PNG URI arrives later in the
 * `org.freedesktop.portal.Request.Response` signal.
 */
internal class DbusScreenshotPortalTransport : ScreenshotPortalTransport {

    private var connection: DBusConnection? = null
    private var portal: PortalScreenshotDbus? = null

    override var lastError: String? = null
        private set

    private val token = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, CompletableFuture<PortalResponse>>()
    private val early = ConcurrentHashMap<String, PortalResponse>()
    private val signalLock = Any()

    private data class PortalResponse(val code: Int, val results: Map<String, Variant<*>>)

    override fun isAvailable(): Boolean = runCatching {
        ensureConnected()
        connection?.getDBusOwnerName(PORTAL_BUS) != null
    }.getOrElse { error ->
        lastError = error.describe()
        logDbusFailure("consultar o portal de captura", error)
        false
    }

    override fun captureWorkspace(): BufferedImage = runCatching {
        ensureConnected()
        val request = portal!!.screenshot("", screenshotPortalOptions(nextToken())).path
        val response = await(request, CAPTURE_TIMEOUT_MS)
        if (response == null) {
            lastError = "o portal não respondeu à captura"
            throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                "O portal de captura não respondeu.",
            )
        }
        if (response.code != 0) {
            lastError = "Screenshot recusado (código ${response.code})"
            val availability = portalCaptureFailure(response.code)
            // A failure that is not a refusal must not be reported as «you denied the permission»:
            // that sends the user looking for a dialog that never appeared.
            throw ScreenCaptureException(
                availability,
                if (availability == CaptureAvailability.DENIED) {
                    captureAvailabilityMessage(CaptureAvailability.DENIED)
                } else {
                    DesktopStrings.portalCaptureFailed(response.code)
                },
            )
        }
        val uri = response.results["uri"]?.value as? String
            ?: throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                "O portal de captura não devolveu o arquivo da imagem.",
            )
        readPortalScreenshot(uri).also { lastError = null }
    }.getOrElse { error ->
        if (error is ScreenCaptureException) throw error
        lastError = error.describe()
        logDbusFailure("capturar a tela pelo portal", error)
        if (error is SecurityException) throw error
        throw ScreenCaptureException(
            CaptureAvailability.UNSUPPORTED,
            "Não foi possível capturar a tela pelo portal de captura: ${error.message ?: error::class.simpleName}",
            error,
        )
    }

    override fun close() {
        pending.values.forEach { it.cancel(false) }
        pending.clear()
        early.clear()
        runCatching { connection?.close() }
        connection = null
        portal = null
    }

    private fun ensureConnected() {
        if (connection != null && portal != null) return

        val created = DBusConnectionBuilder.forSessionBus().build()
        try {
            created.addSigHandler(PortalRequestDbus.Response::class.java) { signal ->
                val response = PortalResponse(signal.response.toInt(), signal.results)
                // Keep the early/await hand-off atomic: the response can arrive immediately after
                // Screenshot() returns, before await() registers its future.
                synchronized(signalLock) {
                    val future = pending[signal.path]
                    if (future != null) {
                        future.complete(response)
                    } else {
                        early[signal.path] = response
                    }
                }
            }
            val remote = created.getRemoteObject(
                PORTAL_BUS,
                PORTAL_OBJECT,
                PortalScreenshotDbus::class.java,
            )
            connection = created
            portal = remote
        } catch (error: Throwable) {
            runCatching { created.close() }
            throw error
        }
    }

    private fun await(requestPath: String, timeoutMs: Long): PortalResponse? {
        val future = CompletableFuture<PortalResponse>()
        synchronized(signalLock) {
            early.remove(requestPath)?.let { return it }
            pending[requestPath] = future
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        } finally {
            pending.remove(requestPath)
        }
    }

    private fun nextToken(): String = "yabai_screenshot_${token.incrementAndGet()}"

    private companion object {
        const val PORTAL_BUS = "org.freedesktop.portal.Desktop"
        const val PORTAL_OBJECT = "/org/freedesktop/portal/desktop"
        const val CAPTURE_TIMEOUT_MS = 60_000L
    }
}
