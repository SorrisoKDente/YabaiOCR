package dev.yabai.ocr.desktop

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface

/**
 * The hover object the KWin script calls: `dev.yabai.Ocr.Hover.CursorMoved(x, y)`.
 */
@DBusInterfaceName(KWinScript.HOVER_INTERFACE)
internal class HoverDbusObject(
    private val onCursorMoved: (Int, Int) -> Unit,
) : DBusInterface {

    @DBusMemberName("CursorMoved")
    fun cursorMoved(x: Int, y: Int) {
        onCursorMoved(x, y)
    }

    override fun getObjectPath(): String = KWinScript.HOVER_PATH
}

/**
 * The shortcut object the KWin fallback script calls:
 * `dev.yabai.Ocr.Shortcuts.ShortcutPressed(id)`.
 */
@DBusInterfaceName(KWinScript.SHORTCUTS_INTERFACE)
internal class ShortcutsDbusObject(
    private val onShortcutPressed: (String) -> Unit,
) : DBusInterface {

    @DBusMemberName("ShortcutPressed")
    fun shortcutPressed(id: String) {
        onShortcutPressed(id)
    }

    override fun getObjectPath(): String = KWinScript.SHORTCUTS_PATH
}

/** D-Bus side separated so [YabaiDbusService] can be tested without a real session bus. */
internal interface YabaiDbusTransport {
    fun start(onCursorMoved: (Int, Int) -> Unit, onShortcutPressed: (String) -> Unit): Boolean
    fun stop()
}

/**
 * Publishes `dev.yabai.Ocr` on the session bus with the two objects the KWin script needs.
 *
 * The service must be running *before* the script is loaded, otherwise KWin's first `callDBus`
 * lands on a bus name nobody owns and the hover silently does nothing.
 */
internal class YabaiDbusService(
    private val onCursorMoved: (Int, Int) -> Unit,
    private val onShortcutPressed: (String) -> Unit,
    private val transportFactory: () -> YabaiDbusTransport? = { DbusYabaiTransport() },
) {
    private var transport: YabaiDbusTransport? = null

    var running: Boolean = false
        private set

    fun start(): Boolean {
        if (running) return true

        val created = runCatching(transportFactory).getOrNull() ?: return false
        if (!created.start(onCursorMoved, onShortcutPressed)) {
            runCatching { created.stop() }
            return false
        }

        transport = created
        running = true
        return true
    }

    fun stop() {
        runCatching { transport?.stop() }
        transport = null
        running = false
    }
}

/** Real implementation: one session-bus connection, bus name and two exported objects. */
internal class DbusYabaiTransport : YabaiDbusTransport {

    private var connection: DBusConnection? = null

    override fun start(onCursorMoved: (Int, Int) -> Unit, onShortcutPressed: (String) -> Unit): Boolean =
        runCatching {
            val created = DBusConnectionBuilder.forSessionBus().build()
            created.requestBusName(KWinScript.SERVICE_NAME)
            created.exportObject(KWinScript.HOVER_PATH, HoverDbusObject(onCursorMoved))
            created.exportObject(KWinScript.SHORTCUTS_PATH, ShortcutsDbusObject(onShortcutPressed))
            connection = created
            true
        }.getOrElse {
            runCatching { connection?.close() }
            connection = null
            false
        }

    override fun stop() {
        runCatching { connection?.close() }
        connection = null
    }
}
