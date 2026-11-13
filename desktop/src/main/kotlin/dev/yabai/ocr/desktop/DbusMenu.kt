package dev.yabai.ocr.desktop

import javax.swing.SwingUtilities
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSerializable
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/** One entry in the tray menu, shared by the native DBusMenu and the context-menu fallback. */
internal data class StatusMenuItem(
    val id: Int,
    val label: String = "",
    val separator: Boolean = false,
    val action: () -> Unit = {},
)

/** Wire payload of `GetLayout`; a plain struct with the normal `a{sv}`/`av` fields. */
internal class DbusMenuPayload(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/**
 * `(u, (ia{sv}av))` returned by `com.canonical.dbusmenu.GetLayout`: the layout revision followed by
 * the recursive layout struct.
 *
 * dbus-java introspects the raw field classes of a struct return, so `Map` and `List` fields in the
 * payload fail `exportObject` ("Exporting non-exportable type"). Keeping the exported type as a
 * `DBusSerializable` wrapper makes the method signature come from [deserialize]'s parameters, where
 * the generic struct fields are read correctly. The two parameters are also what declares the two
 * D-Bus out arguments; returning only the struct makes Plasma reject the reply.
 */
internal class DbusMenuLayout(
    @JvmField var id: Int,
    @JvmField var properties: Map<String, Variant<*>>,
    @JvmField var children: List<Variant<*>>,
    @JvmField var revision: UInt32 = UInt32(0),
) : DBusSerializable {
    constructor() : this(0, emptyMap(), emptyList())

    override fun serialize(): Array<Any> = arrayOf(revision, DbusMenuPayload(id, properties, children))

    @Suppress("UNUSED_PARAMETER")
    fun deserialize(revision: UInt32, payload: DbusMenuPayload) {
        this.revision = revision
        id = payload.id
        properties = payload.properties
        children = payload.children
    }
}

/** Wire payload of `GetGroupProperties`; returned in a list, so the raw `Map` field is not introspected. */
internal class DbusMenuPropertiesPayload(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

/** `(isvu)` used by `EventGroup`. */
internal class DbusMenuEvent(
    @Position(0) @JvmField val id: Int,
    @Position(1) @JvmField val eventId: String,
    @Position(2) @JvmField val data: Variant<*>,
    @Position(3) @JvmField val timestamp: UInt32,
) : Struct()

/**
 * Two `ai` out arguments returned by `AboutToShowGroup`, wrapped for the same introspection reason as
 * the layout: the real method returns `updatesNeeded` and `idErrors`, not one struct.
 */
internal class DbusMenuAboutToShow(
    @JvmField var updatesNeeded: List<Int>,
    @JvmField var idErrors: List<Int>,
) : DBusSerializable {
    constructor() : this(emptyList(), emptyList())

    override fun serialize(): Array<Any> = arrayOf(updatesNeeded, idErrors)

    @Suppress("UNUSED_PARAMETER")
    fun deserialize(updatesNeeded: List<Int>, idErrors: List<Int>) {
        this.updatesNeeded = updatesNeeded
        this.idErrors = idErrors
    }
}

/**
 * The `com.canonical.dbusmenu` object Plasma reads through the SNI `Menu` property.
 *
 * Plasma draws and dismisses the popup itself, so its outside-click handling is the compositor's
 * rather than the JDK's X11 grab. The menu remains flat: `GetLayout(0, 1, ...)` returns the root and
 * every action, which is all the tray needs.
 */
@Suppress("FunctionName")
@DBusInterfaceName("com.canonical.dbusmenu")
internal interface DbusMenuDbus : DBusInterface {

    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DbusMenuLayout
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DbusMenuPropertiesPayload>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun EventGroup(events: List<DbusMenuEvent>): List<Int>
    fun AboutToShow(id: Int): Boolean
    fun AboutToShowGroup(ids: List<Int>): DbusMenuAboutToShow
}

internal const val SNI_MENU_PATH = "/StatusNotifierMenu"

internal class DbusMenu(
    private val objectPath: String = SNI_MENU_PATH,
    private val items: () -> List<StatusMenuItem>,
) : DbusMenuDbus {

    override fun getObjectPath(): String = objectPath

    override fun GetLayout(
        parentId: Int,
        recursionDepth: Int,
        propertyNames: List<String>,
    ): DbusMenuLayout {
        val snapshot = items()
        if (parentId != 0) {
            return DbusMenuLayout(parentId, emptyMap(), emptyList())
        }
        val children = snapshot.map { item ->
            // The concrete signature is required: a bare Variant(struct) cannot be marshalled.
            Variant(layoutFor(item), DBUS_MENU_LAYOUT_SIGNATURE)
        }
        return DbusMenuLayout(
            0,
            mapOf("children-display" to Variant("submenu", "s")),
            children,
        )
    }

    override fun GetGroupProperties(
        ids: List<Int>,
        propertyNames: List<String>,
    ): List<DbusMenuPropertiesPayload> = ids.map { id ->
        DbusMenuPropertiesPayload(id, propertiesFor(id, items()))
    }

    override fun GetProperty(id: Int, name: String): Variant<*> =
        propertiesFor(id, items())[name] ?: Variant("", "s")

    override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
        if (eventId != EVENT_CLICKED) return
        val action = items().firstOrNull { it.id == id && !it.separator }?.action ?: return
        // DBusMenu events arrive on the D-Bus thread; tray actions touch AWT/Compose state.
        SwingUtilities.invokeLater { action() }
    }

    override fun EventGroup(events: List<DbusMenuEvent>): List<Int> {
        events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
        return emptyList()
    }

    override fun AboutToShow(id: Int): Boolean = id == 0

    override fun AboutToShowGroup(ids: List<Int>): DbusMenuAboutToShow =
        DbusMenuAboutToShow(ids.filter { it == 0 }, emptyList())

    private fun layoutFor(item: StatusMenuItem): DbusMenuPayload =
        DbusMenuPayload(item.id, propertiesFor(item), emptyList())

    private fun propertiesFor(id: Int, snapshot: List<StatusMenuItem>): Map<String, Variant<*>> {
        if (id == 0) return mapOf("children-display" to Variant("submenu", "s"))
        return snapshot.firstOrNull { it.id == id }?.let(::propertiesFor) ?: emptyMap()
    }

    private fun propertiesFor(item: StatusMenuItem): Map<String, Variant<*>> =
        if (item.separator) {
            mapOf(
                "type" to Variant("separator", "s"),
                "visible" to Variant(true, "b"),
            )
        } else {
            mapOf(
                "type" to Variant("standard", "s"),
                "label" to Variant(item.label, "s"),
                "enabled" to Variant(true, "b"),
                "visible" to Variant(true, "b"),
            )
        }

    private companion object {
        const val DBUS_MENU_LAYOUT_SIGNATURE = "(ia{sv}av)"
        const val EVENT_CLICKED = "clicked"
    }
}
