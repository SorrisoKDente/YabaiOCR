package dev.yabai.ocr.desktop

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface

/**
 * The KGlobalAccel surface the fallback needs: dropping a component/action pair.
 *
 * KWin's fallback shortcuts are registered from the generated script through `registerShortcut`,
 * which only supplies the *default* key. KGlobalAccel keeps the value already stored in
 * `kglobalshortcutsrc` for that action, so rewriting and reloading the script alone left the old
 * combination firing — the user's edit showed in the app but never reached the keyboard. Removing
 * the action first makes the reload register the new default.
 */
internal interface KGlobalAccelShortcuts {
    /** Removes [actionNames] from [component]; false means KGlobalAccel could not be reached. */
    fun forget(component: String, actionNames: List<String>): Boolean

    fun close() = Unit
}

/** Component that owns shortcuts registered by a KWin script (the `[kwin]` section of the rc file). */
internal const val KWIN_SHORTCUT_COMPONENT = "kwin"

@DBusInterfaceName("org.kde.KGlobalAccel")
internal interface KGlobalAccelDbus : DBusInterface {
    fun unregister(componentUnique: String, actionUnique: String): Boolean
}

internal class DbusKGlobalAccelShortcuts : KGlobalAccelShortcuts {

    private var connection: DBusConnection? = null
    private var accel: KGlobalAccelDbus? = null

    override fun forget(component: String, actionNames: List<String>): Boolean = runCatching {
        if (actionNames.isEmpty()) return@runCatching true
        ensureConnected()
        // A missing action is not an error: the first edit after a start has nothing to forget, and
        // `unregister` answers false for it.
        actionNames.forEach { name -> runCatching { accel!!.unregister(component, name) } }
        true
    }.getOrElse { error ->
        logDbusFailure("limpar os atalhos do KGlobalAccel", error)
        false
    }

    override fun close() {
        runCatching { connection?.close() }
        connection = null
        accel = null
    }

    private fun ensureConnected() {
        if (connection != null && accel != null) return
        val created = DBusConnectionBuilder.forSessionBus().build()
        connection = created
        accel = created.getRemoteObject(KGLOBALACCEL_BUS, KGLOBALACCEL_PATH, KGlobalAccelDbus::class.java)
    }

    private companion object {
        const val KGLOBALACCEL_BUS = "org.kde.kglobalaccel"
        const val KGLOBALACCEL_PATH = "/kglobalaccel"
    }
}
