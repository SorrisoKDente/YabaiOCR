package dev.yabai.ocr.desktop

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.UInt64
import org.freedesktop.dbus.types.Variant

/** One entry of `BindShortcuts`' `a(sa{sv})` argument. */
internal class PortalShortcutStruct(
    @JvmField val id: String,
    @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

/**
 * The D-Bus surface of `org.freedesktop.portal.GlobalShortcuts`.
 *
 * Only the members the app uses are declared. Signal classes must be nested in the interface so
 * dbus-java can find them while deserialising an incoming message.
 */
@DBusInterfaceName("org.freedesktop.portal.GlobalShortcuts")
internal interface PortalGlobalShortcutsDbus : DBusInterface {

    @DBusMemberName("CreateSession")
    fun createSession(options: Map<String, Variant<*>>): DBusPath

    @DBusMemberName("BindShortcuts")
    fun bindShortcuts(
        sessionHandle: DBusPath,
        shortcuts: List<PortalShortcutStruct>,
        parentWindow: String,
        options: Map<String, Variant<*>>,
    ): DBusPath

    class Activated(
        objectPath: String,
        val sessionHandle: String,
        val shortcutId: String,
        val timestamp: UInt64,
        val options: Map<String, Variant<*>>,
    ) : DBusSignal(objectPath, sessionHandle, shortcutId, timestamp, options)
}

/** Reply of every portal request; the actual result comes as this signal. */
@DBusInterfaceName("org.freedesktop.portal.Request")
internal interface PortalRequestDbus : DBusInterface {

    class Response(
        objectPath: String,
        val response: UInt32,
        val results: Map<String, Variant<*>>,
    ) : DBusSignal(objectPath, response, results)
}

/** The portal contract the app needs, separated so [PortalGlobalShortcuts] can be tested without D-Bus. */
internal interface PortalShortcutsTransport {
    /** Last failure, or null. Lets the Settings window show the real portal error. */
    val lastError: String? get() = null

    fun isAvailable(): Boolean
    fun createSession(): String?
    fun bindShortcuts(sessionHandle: String, shortcuts: List<PortalShortcutRequest>): Boolean
    fun setShortcutListener(listener: (String) -> Unit)
    fun close()
}

internal data class PortalShortcutRequest(
    val id: String,
    val description: String,
    val preferredTrigger: String,
)

/**
 * Wayland global shortcuts through `org.freedesktop.portal.GlobalShortcuts`.
 *
 * The portal owns the key combination: the app proposes `preferred_trigger`, but the user may change
 * it in the system dialog. That is why a failure to bind is reported instead of retried forever, and
 * why the app never overwrites the saved preference with what the portal chose.
 */
internal class PortalGlobalShortcuts(
    private val callbacks: HotkeyCallbacks,
    private val transportFactory: () -> PortalShortcutsTransport? = { DbusPortalShortcutsTransport() },
) : HotkeyBackend {
    override val kind = HotkeyBackendKind.PORTAL

    private var transport: PortalShortcutsTransport? = null
    private var sessionHandle: String? = null
    private var started = false

    override var lastError: String? = null
        private set

    override fun start(bindings: List<HotkeyBinding>): Boolean {
        if (started) return update(bindings)
        lastError = null

        val requests = portalRequests(bindings)
        if (requests.isEmpty()) {
            lastError = "nenhum atalho tem combinação configurada"
            return false
        }

        val created = runCatching(transportFactory).getOrElse { error ->
            lastError = error.describe()
            logDbusFailure("criar o transporte do portal", error)
            return false
        } ?: run {
            lastError = "transporte do portal indisponível"
            return false
        }
        if (!created.isAvailable()) {
            lastError = "o portal org.freedesktop.portal.GlobalShortcuts não está no barramento"
            runCatching { created.close() }
            return false
        }

        val session = created.createSession()
        if (session == null) {
            lastError = created.lastError ?: "o portal não criou a sessão de atalhos"
            runCatching { created.close() }
            return false
        }

        created.setShortcutListener { callbacks.dispatch(it) }
        if (!created.bindShortcuts(session, requests)) {
            lastError = created.lastError ?: "o portal recusou registrar os atalhos"
            runCatching { created.close() }
            return false
        }

        transport = created
        sessionHandle = session
        started = true
        return true
    }

    override fun update(bindings: List<HotkeyBinding>): Boolean {
        val active = transport ?: return false
        val session = sessionHandle ?: return false
        val requests = portalRequests(bindings)
        if (requests.isEmpty()) {
            lastError = "nenhum atalho tem combinação configurada"
            return false
        }
        return runCatching { active.bindShortcuts(session, requests) }.getOrDefault(false).also { ok ->
            lastError = if (ok) null else active.lastError ?: "o portal recusou atualizar os atalhos"
        }
    }

    override fun stop() {
        runCatching { transport?.close() }
        transport = null
        sessionHandle = null
        started = false
    }
}

/**
 * Drops shortcuts the portal cannot represent. If everything was dropped the caller must not open a
 * portal session just to bind nothing.
 */
internal fun portalRequests(bindings: List<HotkeyBinding>): List<PortalShortcutRequest> =
    bindings.mapNotNull { binding ->
        binding.preferredTrigger?.let { trigger ->
            PortalShortcutRequest(
                id = binding.id,
                description = binding.description,
                preferredTrigger = trigger,
            )
        }
    }

/**
 * Real D-Bus transport. Every portal method returns a *request* object; the outcome arrives later
 * as `org.freedesktop.portal.Request.Response`. The futures below bridge that to blocking calls.
 */
internal class DbusPortalShortcutsTransport : PortalShortcutsTransport {

    private var connection: DBusConnection? = null
    private var portal: PortalGlobalShortcutsDbus? = null
    private var listener: ((String) -> Unit)? = null

    override var lastError: String? = null
        private set

    private val token = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, CompletableFuture<PortalResponse>>()
    private val early = ConcurrentHashMap<String, PortalResponse>()

    private data class PortalResponse(val code: Int, val results: Map<String, Variant<*>>)

    override fun isAvailable(): Boolean = runCatching {
        ensureConnected()
        connection?.getDBusOwnerName(PORTAL_BUS) != null
    }.getOrElse { error ->
        lastError = error.describe()
        logDbusFailure("consultar o portal de atalhos", error)
        false
    }

    override fun createSession(): String? = runCatching {
        ensureConnected()
        val options = mapOf(
            "handle_token" to Variant(nextToken(), "s"),
            "session_handle_token" to Variant(nextToken(), "s"),
        )
        val request = portal!!.createSession(options).path
        val response = await(request, SESSION_TIMEOUT_MS)
        if (response == null) {
            lastError = "o portal não respondeu ao CreateSession"
            return@runCatching null
        }
        if (response.code != 0) {
            lastError = "CreateSession recusado (código ${response.code})"
            return@runCatching null
        }
        (response.results["session_handle"]?.value as? String).also {
            if (it == null) lastError = "CreateSession não devolveu session_handle"
        }
    }.getOrElse { error ->
        lastError = error.describe()
        logDbusFailure("criar a sessão do portal", error)
        null
    }

    override fun bindShortcuts(sessionHandle: String, shortcuts: List<PortalShortcutRequest>): Boolean =
        runCatching {
            ensureConnected()
            val entries = shortcuts.map { shortcut ->
                PortalShortcutStruct(
                    shortcut.id,
                    mapOf(
                        "description" to Variant(shortcut.description, "s"),
                        "preferred_trigger" to Variant(shortcut.preferredTrigger, "s"),
                    ),
                )
            }
            val options = mapOf("handle_token" to Variant(nextToken(), "s"))
            val request = portal!!.bindShortcuts(
                DBusPath(sessionHandle),
                entries,
                "",
                options,
            ).path
            val response = await(request, BIND_TIMEOUT_MS)
            if (response == null) {
                lastError = "o portal não respondeu ao BindShortcuts"
                return@runCatching false
            }
            if (response.code != 0) {
                lastError = "BindShortcuts recusado (código ${response.code})"
            }
            response.code == 0
        }.getOrElse { error ->
            lastError = error.describe()
            logDbusFailure("registrar os atalhos no portal", error)
            false
        }

    override fun setShortcutListener(listener: (String) -> Unit) {
        this.listener = listener
    }

    override fun close() {
        listener = null
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
        created.addSigHandler(PortalRequestDbus.Response::class.java) { signal ->
            val response = PortalResponse(signal.response.toInt(), signal.results)
            val future = pending[signal.path]
            if (future != null) {
                future.complete(response)
            } else {
                early[signal.path] = response
            }
        }
        created.addSigHandler(PortalGlobalShortcutsDbus.Activated::class.java) { signal ->
            listener?.invoke(signal.shortcutId)
        }

        connection = created
        portal = created.getRemoteObject(PORTAL_BUS, PORTAL_OBJECT, PortalGlobalShortcutsDbus::class.java)
    }

    private fun await(requestPath: String, timeoutMs: Long): PortalResponse? {
        early.remove(requestPath)?.let { return it }

        val future = CompletableFuture<PortalResponse>()
        pending[requestPath] = future
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            null
        } finally {
            pending.remove(requestPath)
        }
    }

    private fun nextToken(): String = "yabai_ocr_${token.incrementAndGet()}"

    private companion object {
        const val PORTAL_BUS = "org.freedesktop.portal.Desktop"
        const val PORTAL_OBJECT = "/org/freedesktop/portal/desktop"
        const val SESSION_TIMEOUT_MS = 30_000L
        const val BIND_TIMEOUT_MS = 120_000L
    }
}
