package dev.yabai.ocr.desktop

import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusBoundProperty
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal

// KDE/Plasma talks to modern tray icons through org.kde.StatusNotifierItem. AWT's TrayIcon is
// still XEmbed; Plasma's xembedsniproxy loses the click events on Wayland (KDE bug 498824), so the
// native menu never appears. This file publishes the standard SNI object, a com.canonical.dbusmenu
// for the host to draw and dismiss, and keeps ContextMenu() as the fallback for hosts without
// DBusMenu support.

internal const val SNI_OBJECT_PATH = "/StatusNotifierItem"
internal const val SNI_WATCHER_SERVICE = "org.kde.StatusNotifierWatcher"
internal const val SNI_WATCHER_PATH = "/StatusNotifierWatcher"

internal fun statusNotifierBusName(pid: Long): String = "org.kde.StatusNotifierItem-$pid-1"

/**
 * AWT is the fallback for every non-Wayland session. On Linux Wayland the tray must be SNI,
 * because Plasma never delivers the XEmbed click to the Java process.
 */
internal enum class TrayBackend { AWT, SNI }

internal fun trayBackendFor(family: OsFamily, session: SessionType): TrayBackend =
    if (family == OsFamily.LINUX && session == SessionType.WAYLAND) TrayBackend.SNI
    else TrayBackend.AWT

/** One `(iiay)` entry of the SNI `IconPixmap`/`ToolTip` properties. */
internal class StatusNotifierIconPixmap(
    @Position(0) @JvmField val width: Int,
    @Position(1) @JvmField val height: Int,
    @Position(2) @JvmField val data: ByteArray,
) : Struct()

/** `(sa(iiay)ss)` from the SNI `ToolTip` property. */
internal class StatusNotifierToolTip(
    @Position(0) @JvmField val iconName: String,
    @Position(1) @JvmField val iconPixmap: List<StatusNotifierIconPixmap>,
    @Position(2) @JvmField val title: String,
    @Position(3) @JvmField val subtitle: String,
) : Struct()

/**
 * Converts an AWT image to the ARGB32 network-order pixels required by `IconPixmap`.
 *
 * KDE does not read the app's PNG resource from disk; it asks the SNI object for pixels. Drawing
 * once into a 32px image keeps the tray sharp at the usual panel sizes and avoids sending a
 * 256px PNG on every property read.
 */
internal fun statusNotifierPixmap(image: Image, size: Int = 32): List<StatusNotifierIconPixmap> {
    val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaled.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        graphics.drawImage(image, 0, 0, size, size, null)
    } finally {
        graphics.dispose()
    }

    val pixels = scaled.getRGB(0, 0, size, size, null, 0, size)
    val data = ByteArray(size * size * 4)
    for (index in pixels.indices) {
        val argb = pixels[index]
        val offset = index * 4
        data[offset] = ((argb ushr 24) and 0xFF).toByte()
        data[offset + 1] = ((argb ushr 16) and 0xFF).toByte()
        data[offset + 2] = ((argb ushr 8) and 0xFF).toByte()
        data[offset + 3] = (argb and 0xFF).toByte()
    }
    return listOf(StatusNotifierIconPixmap(size, size, data))
}

@Suppress("FunctionName")
@DBusInterfaceName("org.kde.StatusNotifierItem")
internal interface StatusNotifierItemDbus : DBusInterface {

    @DBusBoundProperty(name = "Category", access = DBusProperty.Access.READ)
    fun getCategory(): String

    @DBusBoundProperty(name = "Id", access = DBusProperty.Access.READ)
    fun getId(): String

    @DBusBoundProperty(name = "Title", access = DBusProperty.Access.READ)
    fun getTitle(): String

    @DBusBoundProperty(name = "Status", access = DBusProperty.Access.READ)
    fun getStatus(): String

    // The annotation type is required: a raw `List` return type loses its generic argument and
    // dbus-java would advertise `av`, but KDE expects the concrete `a(iiay)` from the SNI spec.
    @DBusBoundProperty(
        name = "IconPixmap",
        type = Array<StatusNotifierIconPixmap>::class,
        access = DBusProperty.Access.READ,
    )
    fun getIconPixmap(): List<StatusNotifierIconPixmap>

    @DBusBoundProperty(name = "ToolTip", access = DBusProperty.Access.READ)
    fun getToolTip(): StatusNotifierToolTip

    @DBusBoundProperty(name = "ItemIsMenu", access = DBusProperty.Access.READ)
    fun getItemIsMenu(): Boolean

    /**
     * Let Plasma draw the context menu itself. A host that understands DBusMenu shows it natively
     * and never calls [ContextMenu]; the old Swing path stays as the fallback for hosts that do.
     */
    @DBusBoundProperty(name = "Menu", access = DBusProperty.Access.READ)
    fun getMenu(): DBusPath

    @DBusBoundProperty(name = "WindowId", access = DBusProperty.Access.READ)
    fun getWindowId(): Int

    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)

    class NewToolTip(objectPath: String) : DBusSignal(objectPath)
    class NewStatus(objectPath: String, status: String) : DBusSignal(objectPath, status)
}

@Suppress("FunctionName")
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
internal interface StatusNotifierWatcherDbus : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

internal class StatusNotifierItem(
    private val onActivate: () -> Unit,
    private val onSecondaryActivate: () -> Unit,
    private val onContextMenu: (Int, Int) -> Unit,
    private val currentStatus: () -> String,
) : StatusNotifierItemDbus {

    private val pixmaps by lazy { statusNotifierPixmap(AppIconGenerator.createTrayImage()) }

    override fun getObjectPath(): String = SNI_OBJECT_PATH

    override fun getCategory(): String = "ApplicationStatus"

    override fun getId(): String = "yabai-ocr"

    override fun getTitle(): String = "Yabai OCR"

    override fun getStatus(): String = "Active"

    override fun getIconPixmap(): List<StatusNotifierIconPixmap> = pixmaps

    override fun getToolTip(): StatusNotifierToolTip =
        StatusNotifierToolTip("", emptyList(), "Yabai OCR", currentStatus())

    override fun getItemIsMenu(): Boolean = false

    override fun getMenu(): DBusPath = DBusPath(SNI_MENU_PATH)

    override fun getWindowId(): Int = 0

    override fun Activate(x: Int, y: Int) = onActivate()

    override fun SecondaryActivate(x: Int, y: Int) = onSecondaryActivate()

    override fun ContextMenu(x: Int, y: Int) = onContextMenu(x, y)

    override fun Scroll(delta: Int, orientation: String) = Unit
}

/**
 * Publishes [SNI_OBJECT_PATH] and registers it with Plasma's StatusNotifierWatcher.
 *
 * The returned object stays connected; [stop] releases the bus name and the connection. Any error
 * is kept in [lastError] so the caller can fall back to AWT instead of running with an invisible
 * tray icon.
 */
internal class StatusNotifierTray(
    private val onActivate: () -> Unit,
    private val onSecondaryActivate: () -> Unit,
    private val onContextMenu: (Int, Int) -> Unit,
    private val menuItems: () -> List<StatusMenuItem> = { emptyList() },
    private val trayBusName: String = statusNotifierBusName(ProcessHandle.current().pid()),
    private val watcherService: String = SNI_WATCHER_SERVICE,
) {
    private var connection: DBusConnection? = null
    private var registeredBusName: String? = null
    private var item: StatusNotifierItem? = null
    private var status = ""

    var lastError: String? = null
        private set

    fun start(): Boolean {
        lastError = null
        val created = try {
            DBusConnectionBuilder.forSessionBus().build()
        } catch (error: Throwable) {
            lastError = error.describe()
            return false
        }

        val name = trayBusName
        return try {
            created.requestBusName(name)

            created.exportObject(SNI_MENU_PATH, DbusMenu(SNI_MENU_PATH, menuItems))
            val sni = StatusNotifierItem(
                onActivate = onActivate,
                onSecondaryActivate = onSecondaryActivate,
                onContextMenu = onContextMenu,
                currentStatus = { status },
            )
            created.exportObject(SNI_OBJECT_PATH, sni)

            val watcher = created.getRemoteObject(
                watcherService,
                SNI_WATCHER_PATH,
                StatusNotifierWatcherDbus::class.java,
            )
            watcher.RegisterStatusNotifierItem(name)

            connection = created
            registeredBusName = name
            item = sni
            true
        } catch (error: Throwable) {
            lastError = error.describe()
            // The bus name was requested before the watcher call. Release it explicitly before
            // closing: on a busy session bus the disconnect alone raced with the name being granted,
            // leaving an invisible SNI name owned until process exit.
            runCatching { created.releaseBusName(name) }
            runCatching { created.close() }
            connection = null
            registeredBusName = null
            item = null
            false
        }
    }

    fun updateStatus(message: String) {
        val text = message.trim()
        if (text == status) return
        status = text
        val connection = connection ?: return
        runCatching {
            connection.sendMessage(StatusNotifierItemDbus.NewToolTip(SNI_OBJECT_PATH))
        }
    }

    fun stop() {
        val current = connection
        connection = null
        registeredBusName?.let { name -> runCatching { current?.releaseBusName(name) } }
        registeredBusName = null
        item = null
        runCatching { current?.close() }
    }
}
