package dev.yabai.ocr.desktop

import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.lang.reflect.Type
import org.freedesktop.dbus.Marshalling
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusIgnore
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperties
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

// KDE/Plasma talks to modern tray icons through org.kde.StatusNotifierItem. AWT's TrayIcon is
// still XEmbed; Plasma's xembedsniproxy loses the click events on Wayland (KDE bug 498824), so the
// native menu never appears. This file publishes the standard SNI object, a com.canonical.dbusmenu
// for the host to draw and dismiss, and keeps ContextMenu() as the fallback for hosts without
// DBusMenu support.

internal const val SNI_OBJECT_PATH = "/StatusNotifierItem"
internal const val SNI_INTERFACE_NAME = "org.kde.StatusNotifierItem"
internal const val SNI_WATCHER_SERVICE = "org.kde.StatusNotifierWatcher"
internal const val SNI_WATCHER_PATH = "/StatusNotifierWatcher"

internal fun statusNotifierBusName(pid: Long): String = "org.kde.StatusNotifierItem-$pid-1"

/**
 * AWT is the fallback for every desktop without a StatusNotifier host.
 */
internal enum class TrayBackend { AWT, SNI }

/**
 * Linux always tries the standard StatusNotifierItem first.
 *
 * XEmbed tray icons are gone from GNOME (Ubuntu 22.04 no longer draws them at all) and on Wayland
 * AWT cannot even receive the click, so an AWT-first choice made the icon invisible on Ubuntu while
 * the app believed it had a tray. SNI is what Plasma draws natively and what the AppIndicator
 * extension reads on GNOME; the AWT path stays as the fallback for a desktop served by neither.
 */
internal fun trayBackendFor(family: OsFamily, session: SessionType): TrayBackend =
    if (family == OsFamily.LINUX) TrayBackend.SNI else TrayBackend.AWT

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

/**
 * The SNI object, exported together with an explicit `org.freedesktop.DBus.Properties`.
 *
 * The properties are deliberately *not* `@DBusBoundProperty` getters: dbus-java 5.1.1 answers
 * `Properties.Get` with the getter's own D-Bus type (`s`) instead of the `v` the spec requires, and
 * GNOME's AppIndicator reads every property with `Get` expecting `(v)` — it discards the reply as
 * mistyped and draws its generic gear instead of the app icon. Answering `Get`/`GetAll` here keeps
 * the variant on the wire. The `@DBusProperties` list is introspection only (the host and
 * `gdbus introspect` still see the properties).
 */
@Suppress("FunctionName")
@DBusInterfaceName(SNI_INTERFACE_NAME)
@DBusProperties(
    value = [
        DBusProperty(name = "Category", type = String::class, access = DBusProperty.Access.READ),
        DBusProperty(name = "Id", type = String::class, access = DBusProperty.Access.READ),
        DBusProperty(name = "IconName", type = String::class, access = DBusProperty.Access.READ),
        DBusProperty(name = "Title", type = String::class, access = DBusProperty.Access.READ),
        DBusProperty(name = "Status", type = String::class, access = DBusProperty.Access.READ),
        // A raw `List` would be advertised as `av`; the SNI spec and KDE want `a(iiay)`.
        DBusProperty(
            name = "IconPixmap",
            type = Array<StatusNotifierIconPixmap>::class,
            access = DBusProperty.Access.READ,
        ),
        DBusProperty(
            name = "ToolTip",
            type = StatusNotifierToolTip::class,
            access = DBusProperty.Access.READ,
        ),
        DBusProperty(name = "ItemIsMenu", type = Boolean::class, access = DBusProperty.Access.READ),
        DBusProperty(name = "Menu", type = DBusPath::class, access = DBusProperty.Access.READ),
        DBusProperty(name = "WindowId", type = Int::class, access = DBusProperty.Access.READ),
    ],
)
internal interface StatusNotifierItemDbus : DBusInterface, Properties {

    // Java-level accessors only: each one is served through `Get`/`GetAll` above, and exporting
    // them as D-Bus methods would also fail (`List` is not an exportable return type).
    @DBusIgnore
    fun getCategory(): String

    @DBusIgnore
    fun getId(): String

    // GNOME's AppIndicator host draws the theme icon named here. Empty means it goes straight to
    // `IconPixmap`, and the extension in practice showed its generic gear instead.
    @DBusIgnore
    fun getIconName(): String

    @DBusIgnore
    fun getTitle(): String

    @DBusIgnore
    fun getStatus(): String

    @DBusIgnore
    fun getIconPixmap(): List<StatusNotifierIconPixmap>

    @DBusIgnore
    fun getToolTip(): StatusNotifierToolTip

    @DBusIgnore
    fun getItemIsMenu(): Boolean

    /**
     * Let Plasma draw the context menu itself. A host that understands DBusMenu shows it natively
     * and never calls [ContextMenu]; the old Swing path stays as the fallback for hosts that do.
     */
    @DBusIgnore
    fun getMenu(): DBusPath

    @DBusIgnore
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

    override fun getIconName(): String = "yabai-ocr"

    override fun getTitle(): String = "Yabai OCR"

    override fun getStatus(): String = "Active"

    override fun getIconPixmap(): List<StatusNotifierIconPixmap> = pixmaps

    override fun getToolTip(): StatusNotifierToolTip =
        StatusNotifierToolTip("", emptyList(), "Yabai OCR", currentStatus())

    override fun getItemIsMenu(): Boolean = false

    override fun getMenu(): DBusPath = DBusPath(SNI_MENU_PATH)

    override fun getWindowId(): Int = 0

    /**
     * Every SNI property as a D-Bus variant, the only shape `Properties.Get`/`GetAll` accept.
     *
     * The `@DBusProperties` list is the single source: it already drives the introspection, and the
     * values are read back from it through `get<Name>()`, with the variant signature taken from the
     * declared type. Adding a property there (plus its getter) is enough; a getter that is missing
     * or a type that does not marshal fails loudly here instead of silently answering nothing.
     */
    private fun propertyVariants(): Map<String, Variant<*>> = buildMap {
        for (property in declaredProperties()) {
            val name = property.name
            val getter = runCatching { StatusNotifierItemDbus::class.java.getMethod("get$name") }
                .getOrElse { throw DBusExecutionException("property '$name' has no getter") }
            val value = getter.invoke(this@StatusNotifierItem)
            // `o` marshals from either a DBusPath or its string form; the signature is the one from
            // the annotation, so the path goes as a plain string.
            val wireValue = if (value is DBusPath) value.path else value
            put(property.name, Variant(wireValue, Marshalling.getDBusType(arrayOf<Type>(property.type.java))))
        }
    }

    private fun declaredProperties(): List<DBusProperty> =
        StatusNotifierItemDbus::class.java.getAnnotation(DBusProperties::class.java)?.value?.toList()
            ?: error("StatusNotifierItemDbus must declare its properties with @DBusProperties")

    // `Get` returns the variant itself: dbus-java marshals the generic return as `v`, which is what
    // the spec promises and what GDBus/GNOME require (`('yabai-ocr',)`), unlike the bound-property
    // path that answered with a bare `s`.
    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A {
        if (interfaceName != SNI_INTERFACE_NAME) {
            throw DBusExecutionException("interface '$interfaceName' does not exist")
        }
        return (propertyVariants()[propertyName]
            ?: throw DBusExecutionException("property '$propertyName' does not exist")) as A
    }

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        throw DBusExecutionException("property '$propertyName' is read-only")
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> {
        if (interfaceName != SNI_INTERFACE_NAME) {
            throw DBusExecutionException("interface '$interfaceName' does not exist")
        }
        return propertyVariants()
    }

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
