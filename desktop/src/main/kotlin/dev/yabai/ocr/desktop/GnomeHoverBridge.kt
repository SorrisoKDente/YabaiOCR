package dev.yabai.ocr.desktop

import java.io.File
import java.util.concurrent.TimeUnit
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException

/**
 * GNOME Shell extension that forwards the global pointer, and the code that installs it.
 *
 * A Wayland client cannot read the global pointer: XWayland only knows it while the pointer is over
 * an X11 window, and no portal exposes the position. The shell can (`global.get_pointer()`), so the
 * extension polls inside the compositor and calls `dev.yabai.Ocr.Hover.CursorMoved` — the same
 * D-Bus contract the KWin script already uses.
 *
 * GNOME reads extensions only at startup, so a fresh install needs one log out. The extension owns
 * `dev.yabai.GnomePointer`, and its presence on the bus is what tells «loaded» from «installed».
 */
internal object GnomeShellExtension {
    const val UUID = "yabai-ocr@yabai.dev"
    const val BRIDGE_BUS_NAME = "dev.yabai.GnomePointer"

    private const val METADATA = "metadata.json"
    private const val SCRIPT = "extension.js"

    fun metadata(): String = readResource(METADATA)

    fun script(): String = readResource(SCRIPT)

    /**
     * Writes (or refreshes) the two files GNOME reads; unchanged content is not rewritten. A write
     * that fails throws, which the bridge reports as «the extension could not be installed».
     */
    fun install(directory: File) {
        writeIfChanged(File(directory, METADATA), metadata())
        writeIfChanged(File(directory, SCRIPT), script())
    }

    private fun writeIfChanged(file: File, content: String) {
        if (file.isFile && file.readText() == content) return
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun readResource(name: String): String {
        val stream = GnomeShellExtension::class.java
            .getResourceAsStream("/gnome/$UUID/$name")
            ?: error("recurso da extensão GNOME ausente: $name")
        return stream.bufferedReader().use { it.readText() }
    }
}

/** Folder GNOME Shell scans for user extensions. `XDG_DATA_HOME` wins, as the XDG base spec asks. */
internal fun gnomeExtensionDirectory(
    home: File = File(System.getProperty("user.home").orEmpty()),
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
): File {
    val base = xdgDataHome?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".local/share")
    return File(base, "gnome-shell/extensions/${GnomeShellExtension.UUID}")
}

/**
 * The shell side, separated so the install/enable flow can be tested without a running Shell.
 */
internal interface GnomePointerHost {
    /** True when the extension is loaded and owns its bus name. */
    fun isBridgeLoaded(): Boolean

    /** Enables [uuid] for the next session. */
    fun enableExtension(uuid: String): Boolean

    fun close() = Unit
}

/**
 * Value of `org.gnome.shell enabled-extensions` with [uuid] appended, in the GVariant form
 * `gsettings set` expects.
 *
 * `gnome-extensions enable` cannot be used for a fresh install: it asks the *running* Shell, which
 * only lists the extensions it found at startup, and answers “does not exist”. Writing the
 * preference is what GNOME reads on the next login, and it also keeps the ids already there.
 */
internal fun enabledExtensionsWith(current: String, uuid: String): String {
    val ids = Regex("'([^']*)'").findAll(current).map { it.groupValues[1] }.toMutableList()
    if (uuid !in ids) ids += uuid
    return ids.joinToString(", ", prefix = "[", postfix = "]") { "'$it'" }
}

/**
 * Installs the bundled extension and enables it for the user. No root, no write outside
 * `~/.local/share/gnome-shell/extensions`.
 */
internal class GnomeHoverBridge(
    private val hostFactory: () -> GnomePointerHost? = { DbusGnomePointerHost() },
    private val extensionDirectory: File = gnomeExtensionDirectory(),
) {
    private var host: GnomePointerHost? = null

    var running: Boolean = false
        private set

    /** True when the files are in place and enabled, but this session still has to be restarted. */
    var needsRelogin: Boolean = false
        private set

    var lastError: String? = null
        private set

    fun start(): Boolean {
        if (running) return true
        lastError = null
        needsRelogin = false

        val created = runCatching(hostFactory).getOrElse { error ->
            lastError = "D-Bus da sessão: ${error.describe()}"
            logDbusFailure("abrir a conexão com o Shell do GNOME", error)
            return false
        } ?: run {
            lastError = "D-Bus da sessão indisponível"
            return false
        }

        if (runCatching { created.isBridgeLoaded() }.getOrDefault(false)) {
            host = created
            running = true
            return true
        }

        // Enabled for the next session: the running Shell cannot see an extension installed after it
        // started, and Wayland offers no reload. A throwing CLI (missing `gnome-extensions`, for
        // example) is a refusal, never a success.
        val enabled = try {
            GnomeShellExtension.install(extensionDirectory)
            runCatching { created.enableExtension(GnomeShellExtension.UUID) }.getOrDefault(false)
        } catch (error: Exception) {
            lastError = "não foi possível gravar a extensão: ${error.describe()}"
            false
        } finally {
            runCatching { created.close() }
        }

        if (!enabled) {
            if (lastError == null) lastError = "o GNOME recusou ativar ${GnomeShellExtension.UUID}"
            return false
        }

        needsRelogin = true
        lastError = "extensão ativa, mas só depois de sair e entrar na sessão"
        return false
    }

    fun stop() {
        runCatching { host?.close() }
        host = null
        running = false
    }
}

/**
 * Owner of [name] on [connection], or null when nobody owns it.
 *
 * `getNames()` cannot be used here: it lists the names **this** connection owns, so the extension
 * never showed up and the app kept telling the user to log out again on every start. Asking the bus
 * for the owner is the real check — it throws «no such name» while the Shell has not loaded the
 * extension yet, which is not an error.
 */
internal fun dbusOwnerOrNull(connection: DBusConnection, name: String): String? =
    try {
        connection.getDBusOwnerName(name)
    } catch (_: DBusExecutionException) {
        null
    }

/** Real host: owner check on the session bus plus the `gnome-extensions` CLI. */
internal class DbusGnomePointerHost : GnomePointerHost {

    private var connection: DBusConnection? = null

    override fun isBridgeLoaded(): Boolean = runCatching {
        ensureConnected()
        connection?.let { dbusOwnerOrNull(it, GnomeShellExtension.BRIDGE_BUS_NAME) != null } == true
    }.getOrElse { error ->
        logDbusFailure("consultar a extensão do GNOME", error)
        false
    }

    override fun enableExtension(uuid: String): Boolean = runCatching {
        // Reading is mandatory: without the current list the write would drop the user's extensions.
        val current = gsettings("get", "org.gnome.shell", "enabled-extensions") ?: return false
        gsettings("set", "org.gnome.shell", "enabled-extensions", enabledExtensionsWith(current, uuid)) != null
    }.getOrElse { error ->
        logDbusFailure("ativar a extensão do GNOME", error)
        false
    }

    /** Runs gsettings; null means it failed (already logged). */
    private fun gsettings(vararg arguments: String): String? = runCatching {
        val process = ProcessBuilder("gsettings", *arguments)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(ENABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            logDbusFailure("ler a preferência de extensões do GNOME", null, "gsettings não respondeu")
            return null
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.exitValue() != 0) {
            logDbusFailure("gravar a preferência de extensões do GNOME", null, "código ${process.exitValue()}: ${output.trim()}")
            null
        } else {
            output.trim()
        }
    }.getOrElse { error ->
        logDbusFailure("executar gsettings", error)
        null
    }

    override fun close() {
        runCatching { connection?.close() }
        connection = null
    }

    private fun ensureConnected() {
        if (connection != null) return
        connection = DBusConnectionBuilder.forSessionBus().build()
    }

    private companion object {
        const val ENABLE_TIMEOUT_SECONDS = 10L
    }
}
