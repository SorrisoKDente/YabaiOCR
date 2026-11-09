package dev.yabai.ocr.desktop

import java.io.File
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface

/**
 * Folder KWin scans for user scripts. `XDG_DATA_HOME` wins, as required by the XDG base spec.
 */
internal fun kwinScriptDirectory(
    home: File = File(System.getProperty("user.home").orEmpty()),
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
): File {
    val base = xdgDataHome?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".local/share")
    return File(base, "kwin/scripts/${KWinScript.PLUGIN_NAME}")
}

/** The KWin scripting API the bridge needs, separated so the flow can be tested without KWin. */
internal interface KWinScriptHost {
    fun isAvailable(): Boolean
    fun loadScript(path: String, pluginName: String): Boolean
    fun start(): Boolean
    fun isLoaded(pluginName: String): Boolean
    fun unload(pluginName: String)
    fun close() = Unit
}

/**
 * Installs the helper script under the user data folder and asks KWin to load it.
 *
 * No root, no write to `/usr`, no KWin restart. A failure is reported instead of falling back to
 * `MouseInfo`: on Wayland that pointer would never move and the hover would look broken.
 */
internal class KWinHoverBridge(
    private val hostFactory: () -> KWinScriptHost? = { DbusKWinScriptHost() },
    private val scriptDirectory: File = kwinScriptDirectory(),
) {
    private var host: KWinScriptHost? = null

    var running: Boolean = false
        private set

    /** Why [start] failed, so the Settings status line can say more than "indisponível". */
    var lastError: String? = null
        private set

    fun start(fallbackShortcuts: List<HotkeyBinding>): Boolean {
        if (running) return true
        lastError = null

        val created = runCatching(hostFactory).getOrElse { error ->
            lastError = "D-Bus do KWin: ${error.describe()}"
            logDbusFailure("abrir a conexão com o KWin", error)
            return false
        } ?: run {
            lastError = "D-Bus do KWin indisponível"
            return false
        }
        if (!created.isAvailable()) {
            lastError = "o KWin não expôs org.kde.KWin /Scripting"
            runCatching { created.close() }
            return false
        }

        val files = runCatching { KWinScript.install(scriptDirectory, fallbackShortcuts) }.getOrElse { error ->
            lastError = "não foi possível gravar o script: ${error.describe()}"
            runCatching { created.close() }
            return false
        }

        val script = files.firstOrNull { it.name == "main.js" }
        if (script == null || !created.loadScript(script.absolutePath, KWinScript.PLUGIN_NAME)) {
            lastError = "o KWin recusou carregar o script ${script?.absolutePath ?: "main.js"}"
            runCatching { created.close() }
            return false
        }
        if (!created.start() || !created.isLoaded(KWinScript.PLUGIN_NAME)) {
            lastError = "o script foi carregado mas não iniciou"
            runCatching { created.unload(KWinScript.PLUGIN_NAME) }
            runCatching { created.close() }
            return false
        }

        host = created
        running = true
        return true
    }

    fun stop() {
        runCatching { host?.unload(KWinScript.PLUGIN_NAME) }
        runCatching { host?.close() }
        host = null
        running = false
    }
}

/** D-Bus surface of `org.kde.KWin` `/Scripting`. Only the members the bridge uses. */
@DBusInterfaceName("org.kde.kwin.Scripting")
internal interface KWinScriptingDbus : DBusInterface {

    @DBusMemberName("loadScript")
    fun loadScript(path: String, pluginName: String): Int

    @DBusMemberName("start")
    fun startScript()

    @DBusMemberName("isScriptLoaded")
    fun isScriptLoaded(pluginName: String): Boolean

    @DBusMemberName("unloadScript")
    fun unloadScript(pluginName: String): Boolean
}

internal class DbusKWinScriptHost : KWinScriptHost {

    private var connection: DBusConnection? = null
    private var scripting: KWinScriptingDbus? = null

    override fun isAvailable(): Boolean = runCatching {
        ensureConnected()
        connection?.getDBusOwnerName(KWIN_BUS) != null
    }.getOrElse { error ->
        logDbusFailure("consultar o KWin", error)
        false
    }

    override fun loadScript(path: String, pluginName: String): Boolean = runCatching {
        ensureConnected()
        scripting!!.loadScript(path, pluginName) >= 0
    }.getOrElse { error ->
        logDbusFailure("carregar o script do KWin", error)
        false
    }

    override fun start(): Boolean = runCatching {
        ensureConnected()
        scripting!!.startScript()
        true
    }.getOrElse { error ->
        logDbusFailure("iniciar o script do KWin", error)
        false
    }

    override fun isLoaded(pluginName: String): Boolean = runCatching {
        ensureConnected()
        scripting!!.isScriptLoaded(pluginName)
    }.getOrElse { error ->
        logDbusFailure("consultar o estado do script do KWin", error)
        false
    }

    override fun unload(pluginName: String) {
        runCatching {
            ensureConnected()
            scripting!!.unloadScript(pluginName)
        }.onFailure { error ->
            logDbusFailure("descarregar o script do KWin", error)
        }
    }

    override fun close() {
        runCatching { connection?.close() }
        connection = null
        scripting = null
    }

    private fun ensureConnected() {
        if (connection != null && scripting != null) return

        val created = DBusConnectionBuilder.forSessionBus().build()
        connection = created
        scripting = created.getRemoteObject(KWIN_BUS, KWIN_PATH, KWinScriptingDbus::class.java)
    }

    private companion object {
        const val KWIN_BUS = "org.kde.KWin"
        const val KWIN_PATH = "/Scripting"
    }
}
