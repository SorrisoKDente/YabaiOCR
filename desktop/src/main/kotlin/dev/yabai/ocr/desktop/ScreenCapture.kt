package dev.yabai.ocr.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Dimension
import java.awt.GraphicsEnvironment
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
        "Captura de tela indisponível nesta sessão. É preciso JDK 21+ e um portal de tela compatível."
}

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
}

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
    private val captureAction: () -> BufferedImage = { capturePrimaryScreenWithRobot() },
    private val regionAction: (Rectangle) -> BufferedImage = { captureRegionWithRobot(it) },
) : ScreenCaptureBackend {

    @Volatile
    private var failure: CaptureAvailability? = null

    override fun availability(): CaptureAvailability {
        if (jdkFeatureVersion < 21) return CaptureAvailability.UNSUPPORTED
        if (failure == CaptureAvailability.DENIED) return CaptureAvailability.DENIED
        if (tokenCandidates.none { it.isFile }) return CaptureAvailability.NEEDS_PERMISSION
        return CaptureAvailability.READY
    }

    override fun screenBounds(): Rectangle = primaryScreenBounds()

    override fun captureScreen(): BufferedImage = guard {
        captureAction().also { failure = null }
    }

    override fun captureRegion(rect: Rectangle): BufferedImage = guard {
        regionAction(rect).also { failure = null }
    }

    private inline fun <T> guard(action: () -> T): T {
        if (jdkFeatureVersion < 21) {
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

    fun captureScreen(): BufferedImage = PlatformServices.capture.captureScreen()

    fun captureRegion(rect: Rectangle): BufferedImage = PlatformServices.capture.captureRegion(rect)

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
