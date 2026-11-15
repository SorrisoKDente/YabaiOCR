package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
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
 * The shortcut object the KWin script and the GNOME extension call:
 * `ShortcutPressed(id)`, plus the accelerators the GNOME extension has to register.
 */
@DBusInterfaceName(KWinScript.SHORTCUTS_INTERFACE)
internal class ShortcutsDbusObject(
    private val onShortcutPressed: (String) -> Unit,
    private val bindings: () -> Map<String, String> = { emptyMap() },
) : DBusInterface {

    @DBusMemberName("ShortcutPressed")
    fun shortcutPressed(id: String) {
        onShortcutPressed(id)
    }

    /** `id → GNOME accelerator`; empty for the KWin script, which registers its own shortcuts. */
    @DBusMemberName("ShortcutBindings")
    fun shortcutBindings(): Map<String, String> = bindings()

    override fun getObjectPath(): String = KWinScript.SHORTCUTS_PATH
}

/** D-Bus side separated so [YabaiDbusService] can be tested without a real session bus. */
internal interface YabaiDbusTransport {
    /** Last failure, if any, so the UI can show why the bus name could not be published. */
    val lastError: String? get() = null

    fun start(onCursorMoved: (Int, Int) -> Unit, onShortcutPressed: (String) -> Unit): Boolean
    fun stop()

    /** Accelerators a Shell extension would register; ignored by transports that do not export them. */
    fun updateShortcutBindings(bindings: Map<String, String>) {}
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

    @Volatile
    private var shortcutBindings: Map<String, String> = emptyMap()

    var running: Boolean = false
        private set

    /** Why [start] failed, or null on success. Kept for the status line instead of a silent false. */
    var lastError: String? = null
        private set

    fun start(): Boolean {
        if (running) return true
        lastError = null

        val created = runCatching(transportFactory).getOrElse { error ->
            lastError = error.describe()
            logDbusFailure("criar o transporte D-Bus", error)
            return false
        } ?: run {
            lastError = "transporte D-Bus indisponível"
            return false
        }

        if (!created.start(onCursorMoved, onShortcutPressed)) {
            lastError = created.lastError ?: "o barramento da sessão recusou a conexão"
            logDbusFailure("publicar ${KWinScript.SERVICE_NAME}", null, lastError)
            runCatching { created.stop() }
            return false
        }

        transport = created
        running = true
        created.updateShortcutBindings(shortcutBindings)
        return true
    }

    /** Publishes the accelerators the Shell extension should own, now and after an edit. */
    fun updateShortcutBindings(bindings: Map<String, String>) {
        shortcutBindings = bindings
        transport?.updateShortcutBindings(bindings)
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
    private var shortcuts: ShortcutsDbusObject? = null

    @Volatile
    private var shortcutBindings: Map<String, String> = emptyMap()

    override var lastError: String? = null
        private set

    override fun updateShortcutBindings(bindings: Map<String, String>) {
        shortcutBindings = bindings
    }

    override fun start(onCursorMoved: (Int, Int) -> Unit, onShortcutPressed: (String) -> Unit): Boolean =
        runCatching {
            lastError = null
            val created = DBusConnectionBuilder.forSessionBus().build()
            created.requestBusName(KWinScript.SERVICE_NAME)
            created.exportObject(KWinScript.HOVER_PATH, HoverDbusObject(onCursorMoved))
            shortcuts = ShortcutsDbusObject(onShortcutPressed) { shortcutBindings }
            created.exportObject(KWinScript.SHORTCUTS_PATH, shortcuts)
            connection = created
            true
        }.getOrElse { error ->
            // A taken name means a second instance of the app, which the single-instance guard now
            // prevents — but if it ever happens the status line must say so instead of showing a
            // raw DBusException.
            val nameTaken = error.message.orEmpty().contains("register bus name", ignoreCase = true)
            lastError = if (nameTaken) DesktopStrings.dbusNameTaken else error.describe()
            logDbusFailure("conectar ao barramento da sessão", error)
            runCatching { connection?.close() }
            connection = null
            false
        }

    override fun stop() {
        runCatching { connection?.close() }
        connection = null
    }
}

/** Short, user-facing reason. The full stack trace goes to stderr for a bug report. */
internal fun Throwable.describe(): String {
    val type = this::class.simpleName ?: "erro"
    val detail = message?.takeIf { it.isNotBlank() }
    return if (detail == null) type else "$type: $detail"
}

/** D-Bus failures used to be swallowed by `runCatching`; stderr is the only log the packaged app has. */
internal fun logDbusFailure(step: String, error: Throwable?, detail: String? = null) {
    val text = detail ?: error?.describe().orEmpty()
    System.err.println("Yabai OCR: falha ao $step: $text")
    error?.printStackTrace(System.err)
}
