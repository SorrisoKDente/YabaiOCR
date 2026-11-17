package dev.yabai.ocr.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.Dimension
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

/**
 * Size, in device pixels, of the native variant to ask `Robot` for.
 *
 * Asking with the user-space size returns the downscaled variant — the very image that forced the
 * picker to upscale and resample the screen. On an unscaled display the two are the same.
 */
internal fun nativeCaptureSize(bounds: Rectangle, scaleX: Double, scaleY: Double): Dimension =
    Dimension(
        (bounds.width * scaleX).roundToInt(),
        (bounds.height * scaleY).roundToInt(),
    )

internal enum class CaptureAvailability { READY, NEEDS_PERMISSION, DENIED, UNSUPPORTED }

internal fun captureAvailabilityMessage(state: CaptureAvailability): String = when (state) {
    CaptureAvailability.READY ->
        "Captura de tela pronta."
    CaptureAvailability.NEEDS_PERMISSION ->
        "Autorize o compartilhamento da tela no diálogo do sistema para capturar."
    CaptureAvailability.DENIED ->
        "A permissão de captura foi negada. Tente de novo e autorize o diálogo do sistema."
    CaptureAvailability.UNSUPPORTED ->
        "Captura de tela indisponível nesta sessão. É preciso JDK 17.0.13+ (ou 21+) e um portal de tela compatível."
}

/**
 * Whether this JVM has the `dbusScreencast` Robot implementation.
 *
 * It shipped in JDK 21 and was backported to 17.0.13 (JDK-8335485) — the line the packaged Linux
 * build runs on. The desktop build targets Java 17 bytecode, so older lines cannot run this code at
 * all; gating on `feature >= 21` rejected a runtime that captures the screen perfectly.
 */
internal fun screencastSupported(featureVersion: Int, updateVersion: Int): Boolean =
    featureVersion >= 21 || (featureVersion == 17 && updateVersion >= 13)

internal class ScreenCaptureException(
    val availability: CaptureAvailability,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal interface ScreenCaptureBackend {
    fun availability(): CaptureAvailability
    fun screenBounds(): Rectangle
    fun captureScreen(): BufferedImage
    fun captureRegion(rect: Rectangle): BufferedImage

    /**
     * Name of the transport that served the last capture, or null when the backend cannot tell.
     *
     * Two backends can sit behind one [ScreenCaptureBackend] — KDE Wayland asks KWin directly and
     * falls back to the portal — and which one answered is the difference between a hover that
     * takes tens of milliseconds and one that opens a permission window per read. Naming it is what
     * lets the user (and the log) tell them apart; see [CaptureDiagnostics].
     */
    fun lastCaptureRoute(): String? = null

    /**
     * Captures exactly [bounds], the rectangle of one monitor.
     *
     * Backends that capture the whole workspace already crop a rectangle for the hover, so the
     * default reuses that path; the ones bound to a single graphics device override it. Keeping the
     * default here means a backend without monitor support still captures *something* for the
     * monitor it was told to use, instead of silently returning the primary screen.
     */
    fun captureMonitor(bounds: Rectangle): BufferedImage =
        if (bounds == screenBounds()) captureScreen() else captureRegion(bounds)
}

/**
 * The historical path: Robot reading the framebuffer directly. It is correct on Windows and on
 * Linux X11 and must stay byte-for-byte compatible with the behavior those platforms already had.
 */
internal object RobotScreenCapture : ScreenCaptureBackend {
    override fun availability(): CaptureAvailability = CaptureAvailability.READY
    override fun screenBounds(): Rectangle = primaryScreenBounds()
    override fun captureScreen(): BufferedImage = capturePrimaryScreenWithRobot()
    override fun captureRegion(rect: Rectangle): BufferedImage = captureRegionWithRobot(rect)
    override fun captureMonitor(bounds: Rectangle): BufferedImage = captureScreenWithRobot(bounds)
    override fun lastCaptureRoute(): String = ROUTE_ROBOT
}

/** Names the capture transports answer with, shared by the diagnostics and the session label. */
internal const val ROUTE_ROBOT = "Robot"
internal const val ROUTE_KWIN = "KWin ScreenShot2"
internal const val ROUTE_PORTAL = "portal Screenshot"
internal const val ROUTE_SCREENCAST = "ScreenCast do JDK"

internal fun primaryScreenBounds(): Rectangle {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return ge.defaultScreenDevice.defaultConfiguration.bounds
}

/**
 * Captures the primary screen at its **native** (device) resolution.
 *
 * `createScreenCapture` returns the user-space variant: on a display scaled at 150% that is only
 * two thirds of the pixels the screen really has, so drawing it back over the screen upscales — and
 * visibly resamples — every glyph. The multi-resolution variant labelled with the device size is the
 * framebuffer itself, which the picker can draw 1:1.
 */
internal fun capturePrimaryScreenWithRobot(): BufferedImage {
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val bounds = device.defaultConfiguration.bounds
    val transform = device.defaultConfiguration.defaultTransform
    val robot = Robot(device)
    val variants = robot.createMultiResolutionScreenCapture(bounds)
    val size = nativeCaptureSize(bounds, transform.scaleX, transform.scaleY)
    val native = variants.getResolutionVariant(size.width.toDouble(), size.height.toDouble())
    return native as? BufferedImage ?: robot.createScreenCapture(bounds)
}

internal fun captureRegionWithRobot(rect: Rectangle): BufferedImage {
    val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    return Robot(device).createScreenCapture(rect)
}

/**
 * Captures one monitor at its native resolution.
 *
 * `Robot` is bound to a graphics device: asking the primary device for a rectangle that lives on
 * another monitor returns black. The device whose bounds are [bounds] is the one to use; when the
 * rectangle does not match any device the primary path runs, which is what every caller got before
 * monitors could be chosen.
 */
internal fun captureScreenWithRobot(bounds: Rectangle): BufferedImage {
    val device = deviceWithBounds(screens(), bounds) { runCatching { it.defaultConfiguration.bounds }.getOrNull() }
        ?: return capturePrimaryScreenWithRobot()
    val configuration = device.defaultConfiguration
    val robot = Robot(device)
    val variants = robot.createMultiResolutionScreenCapture(bounds)
    val size = nativeCaptureSize(
        bounds,
        configuration.defaultTransform.scaleX,
        configuration.defaultTransform.scaleY,
    )
    val native = variants.getResolutionVariant(size.width.toDouble(), size.height.toDouble())
    return native as? BufferedImage ?: robot.createScreenCapture(bounds)
}

/**
 * The device whose bounds are exactly [bounds].
 *
 * Split from the capture itself so the «no device owns this rectangle» case — which must fall back
 * to the primary screen instead of capturing black — can be tested without a graphics device.
 */
internal fun <T> deviceWithBounds(
    devices: List<T>,
    bounds: Rectangle,
    boundsOf: (T) -> Rectangle?,
): T? = devices.firstOrNull { boundsOf(it) == bounds }

/**
 * One physical display, in AWT coordinates, with the label the settings window shows.
 *
 * [index] is the position in [captureDisplays] and is what the preference stores: the display ID
 * string differs between X11, Wayland and Windows, and a monitor that is unplugged must not turn the
 * stored preference into a capture of the wrong screen.
 */
internal data class CaptureDisplay(
    val index: Int,
    val label: String,
    val bounds: Rectangle,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
)

internal const val CAPTURE_MONITOR_PRIMARY = "PRIMARY"
internal const val CAPTURE_MONITOR_MOUSE = "MOUSE"
internal const val CAPTURE_MONITOR_DISPLAY_PREFIX = "DISPLAY:"

internal fun captureMonitorDisplayPreference(index: Int): String =
    "$CAPTURE_MONITOR_DISPLAY_PREFIX$index"

private fun screens(): List<GraphicsDevice> =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices?.toList().orEmpty()

/** Every connected display, primary first — the order the settings dropdown lists them in. */
internal fun captureDisplays(devices: List<GraphicsDevice>): List<CaptureDisplay> =
    devices.mapIndexed { index, device ->
        val configuration = runCatching { device.defaultConfiguration }.getOrNull()
        val bounds = configuration?.bounds ?: Rectangle(0, 0, 1, 1)
        CaptureDisplay(
            index = index,
            label = displayLabel(device, index),
            bounds = bounds,
            scaleX = configuration?.defaultTransform?.scaleX ?: 1.0,
            scaleY = configuration?.defaultTransform?.scaleY ?: 1.0,
        )
    }

private fun displayLabel(device: GraphicsDevice, index: Int): String {
    val rawId = runCatching { device.getIDstring() }.getOrNull().orEmpty().trim()
    val cleanedId = rawId
        .replace("\\\\.\\", "")
        .replace("\\", "")
        .trim()
    return cleanedId.ifBlank { "Monitor ${index + 1}" }
}

/**
 * Text the settings dropdown shows for a stored preference.
 *
 * Labels arrive as arguments so this stays independent of the i18n layer and can be tested without
 * a `DesktopStrings` initialisation.
 */
internal fun captureMonitorLabel(
    preference: String,
    displays: List<CaptureDisplay>,
    primaryLabel: String,
    mouseLabel: String,
): String {
    if (preference == CAPTURE_MONITOR_MOUSE) return mouseLabel
    if (preference.startsWith(CAPTURE_MONITOR_DISPLAY_PREFIX)) {
        val index = preference.removePrefix(CAPTURE_MONITOR_DISPLAY_PREFIX).toIntOrNull()
        val display = displays.firstOrNull { it.index == index }
        if (display != null) return "${display.label} (${display.bounds.width}×${display.bounds.height})"
    }
    return primaryLabel
}

/**
 * Display the preference asks for.
 *
 * `PRIMARY` and an unknown index both fall back to the first display — the one AWT reports as the
 * primary screen — so unplugging the monitor that was chosen keeps the app capturing instead of
 * failing. `MOUSE` follows the pointer, and without a pointer position it degrades to the primary
 * screen, which is the historical behaviour.
 */
internal fun resolveCaptureDisplay(
    preference: String,
    displays: List<CaptureDisplay>,
    pointer: Point? = null,
): CaptureDisplay? {
    if (displays.isEmpty()) return null
    if (preference == CAPTURE_MONITOR_MOUSE) {
        val point = pointer ?: return displays.first()
        val bounds = HoverGeometry.monitorAt(displays.map { it.bounds }, point)
        return displays.firstOrNull { it.bounds == bounds } ?: displays.first()
    }
    if (preference.startsWith(CAPTURE_MONITOR_DISPLAY_PREFIX)) {
        val index = preference.removePrefix(CAPTURE_MONITOR_DISPLAY_PREFIX).toIntOrNull()
        return displays.firstOrNull { it.index == index } ?: displays.first()
    }
    return displays.first()
}

/**
 * Restore-token paths used by the JDK ScreenCast implementation. The app never reads or copies the
 * tokens; their presence only lets the UI say "already authorized" without opening a dialog.
 */
internal fun screencastTokenCandidates(
    userHome: String = System.getProperty("user.home"),
): List<File> = listOf(
    File(userHome, ".java/robot/screencast-tokens.properties"),
    File(userHome, ".awt/robot/screencast-tokens.properties"),
)

/**
 * Wayland capture through `java.awt.Robot`'s ScreenCast implementation (JDK 21+).
 *
 * The first capture opens the compositor permission dialog; after that the JDK reuses its restore
 * token. A denial is recorded so the UI does not keep promising a capture that will never happen.
 */
internal class ScreencastScreenCapture(
    private val tokenCandidates: List<File> = screencastTokenCandidates(),
    private val jdkFeatureVersion: Int = Runtime.version().feature(),
    private val jdkUpdateVersion: Int = Runtime.version().update(),
    private val captureAction: () -> BufferedImage = { capturePrimaryScreenWithRobot() },
    private val regionAction: (Rectangle) -> BufferedImage = { captureRegionWithRobot(it) },
) : ScreenCaptureBackend {

    @Volatile
    private var failure: CaptureAvailability? = null

    private val supported: Boolean
        get() = screencastSupported(jdkFeatureVersion, jdkUpdateVersion)

    override fun availability(): CaptureAvailability {
        if (!supported) return CaptureAvailability.UNSUPPORTED
        if (failure == CaptureAvailability.DENIED) return CaptureAvailability.DENIED
        if (tokenCandidates.none { it.isFile }) return CaptureAvailability.NEEDS_PERMISSION
        return CaptureAvailability.READY
    }

    override fun screenBounds(): Rectangle = primaryScreenBounds()

    override fun lastCaptureRoute(): String = ROUTE_SCREENCAST

    override fun captureScreen(): BufferedImage = guard {
        captureAction().also { failure = null }
    }

    override fun captureRegion(rect: Rectangle): BufferedImage = guard {
        regionAction(rect).also { failure = null }
    }

    private inline fun <T> guard(action: () -> T): T {
        if (!supported) {
            throw ScreenCaptureException(
                CaptureAvailability.UNSUPPORTED,
                captureAvailabilityMessage(CaptureAvailability.UNSUPPORTED),
            )
        }
        return try {
            action()
        } catch (e: SecurityException) {
            failure = CaptureAvailability.DENIED
            throw ScreenCaptureException(
                CaptureAvailability.DENIED,
                captureAvailabilityMessage(CaptureAvailability.DENIED),
                e,
            )
        }
    }
}

/**
 * What the last screen capture cost, and which transport served it.
 *
 * The hover card is only as fast as the capture and the OCR behind it, and an AppImage on KDE
 * Wayland captures through the `Screenshot` portal (KWin refuses `ScreenShot2` to an `Exec` living
 * in `/tmp/.mount_*`), which costs a permission window and seconds instead of tens of milliseconds.
 * Nothing on screen said which one was in use. Now the first capture of each transport, and every
 * change between them, is written to the log, and the hover status line carries the same summary:
 *
 * ```
 * [Captura] captura: KWin ScreenShot2 · 1280×800 · 47 ms
 * [Hover] が — Jitendex PT-BR · captura: KWin ScreenShot2 · 1280×800 · 47 ms
 * ```
 */
internal object CaptureDiagnostics {
    private data class Measurement(
        val route: String,
        val width: Int,
        val height: Int,
        val millis: Long,
    )

    @Volatile
    private var last: Measurement? = null

    /** Route already announced in the log, so a steady path does not write a line per hover. */
    @Volatile
    private var announced: String? = null

    /** The last capture, as the status line shows it; null before the first capture. */
    fun lastSummary(): String? = last?.summary()

    fun record(route: String?, image: BufferedImage, millis: Long) {
        val measurement = Measurement(
            route = route ?: DesktopStrings.captureRouteUnknown,
            width = image.width,
            height = image.height,
            millis = millis,
        )
        last = measurement
        if (announced != measurement.route) {
            announced = measurement.route
            println("[Captura] ${measurement.summary()}")
        }
    }

    private fun Measurement.summary(): String =
        DesktopStrings.captureSummary(route, width, height, millis)
}

/**
 * Public facade used by the pickers and by [Main]. The backend is chosen once by [PlatformServices].
 */
internal object ScreenCapture {
    fun availability(): CaptureAvailability = PlatformServices.capture.availability()

    fun unavailableMessage(): String? = availability()
        .takeIf { it != CaptureAvailability.READY }
        ?.let(::captureAvailabilityMessage)

    /**
     * Bounds of the primary screen, in AWT coordinates.
     *
     * The picker sizes its window to this rectangle so the captured image is shown at the same
     * size it was taken, taskbar included — maximizing to the work area would squeeze the image and
     * resample every glyph.
     */
    fun screenBounds(): Rectangle = PlatformServices.capture.screenBounds()

    fun captureScreen(): BufferedImage = measured { PlatformServices.capture.captureScreen() }

    fun captureRegion(rect: Rectangle): BufferedImage = measured { PlatformServices.capture.captureRegion(rect) }

    /**
     * Runs one capture and records which transport served it and how long it took.
     *
     * The measurement is the only thing added here: the image, the rectangle and the backend are
     * exactly what the caller would have got.
     */
    private fun measured(capture: () -> BufferedImage): BufferedImage {
        val started = System.nanoTime()
        val image = capture()
        CaptureDiagnostics.record(
            route = PlatformServices.capture.lastCaptureRoute(),
            image = image,
            millis = (System.nanoTime() - started) / 1_000_000,
        )
        return image
    }

    /** Connected displays, primary first. */
    fun displays(): List<CaptureDisplay> = captureDisplays(screens())

    /** Display chosen by the stored preference, or null when there is nothing to capture. */
    fun resolveDisplay(preference: String, pointer: Point? = null): CaptureDisplay? =
        resolveCaptureDisplay(preference, displays(), pointer)

    /** Captures the whole of [display] at its native resolution. */
    fun captureDisplay(display: CaptureDisplay): BufferedImage =
        measured { PlatformServices.capture.captureMonitor(display.bounds) }

    fun captureScreenAsImageBitmap(): ImageBitmap {
        return captureScreen().toComposeImageBitmap()
    }

    fun cropImage(source: BufferedImage, x: Int, y: Int, width: Int, height: Int): BufferedImage {
        val safeX = x.coerceIn(0, source.width - 1)
        val safeY = y.coerceIn(0, source.height - 1)
        val safeWidth = width.coerceAtMost(source.width - safeX).coerceAtLeast(1)
        val safeHeight = height.coerceAtMost(source.height - safeY).coerceAtLeast(1)
        return source.getSubimage(safeX, safeY, safeWidth, safeHeight)
    }
}
