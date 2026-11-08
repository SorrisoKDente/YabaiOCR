package dev.yabai.ocr.desktop

/**
 * Which OS family the desktop module is running on.
 *
 * Kept apart from the JVM properties so detection can be tested without touching the real machine.
 */
internal enum class OsFamily { WINDOWS, LINUX, MACOS, OTHER }

/**
 * The display server of the current Linux session.
 */
internal enum class SessionType { X11, WAYLAND, UNKNOWN }

/**
 * The desktop environment, used to pick the Wayland integration. Only KDE is a full target for now;
 * GNOME still gets capture and shortcuts through the XDG portals, but has no global pointer bridge.
 */
internal enum class DesktopFamily { KDE, GNOME, OTHER, UNKNOWN }

internal fun detectOsFamily(osName: String): OsFamily {
    val normalized = osName.lowercase()
    return when {
        normalized.contains("win") -> OsFamily.WINDOWS
        normalized.contains("linux") -> OsFamily.LINUX
        normalized.contains("mac") || normalized.contains("darwin") -> OsFamily.MACOS
        else -> OsFamily.OTHER
    }
}

/**
 * Detects Wayland without trusting a single variable.
 *
 * XWayland leaves both `DISPLAY` and `WAYLAND_DISPLAY` defined. `XDG_SESSION_TYPE` wins because it
 * describes the session, not just the compatibility layer; when it is missing, `WAYLAND_DISPLAY`
 * is the conservative choice: treating an XWayland session as Wayland still captures, while the
 * reverse would capture only the app itself.
 */
internal fun detectSessionType(env: Map<String, String>): SessionType {
    val session = env["XDG_SESSION_TYPE"]?.lowercase()
    if (session == "wayland") return SessionType.WAYLAND
    if (session == "x11") return SessionType.X11
    if (!env["WAYLAND_DISPLAY"].isNullOrBlank()) return SessionType.WAYLAND
    if (!env["DISPLAY"].isNullOrBlank()) return SessionType.X11
    return SessionType.UNKNOWN
}

internal fun detectDesktopFamily(env: Map<String, String>): DesktopFamily {
    val desktop = env["XDG_CURRENT_DESKTOP"].orEmpty().lowercase()
    return when {
        desktop.contains("kde") || desktop.contains("plasma") -> DesktopFamily.KDE
        desktop.contains("gnome") || desktop.contains("unity") -> DesktopFamily.GNOME
        desktop.isBlank() -> DesktopFamily.UNKNOWN
        else -> DesktopFamily.OTHER
    }
}

/**
 * SteamOS identifies itself in `/etc/os-release`; the environment is only a secondary hint.
 */
internal fun isSteamOs(env: Map<String, String>, osRelease: String?): Boolean {
    if (env["SteamOS"].equals("1", ignoreCase = true)) return true
    val release = osRelease.orEmpty()
    return release.lineSequence().any {
        val line = it.trim()
        line.equals("ID=steamos", ignoreCase = true) || line.equals("ID=steam", ignoreCase = true)
    }
}

internal object DesktopPlatform {
    val family: OsFamily = detectOsFamily(System.getProperty("os.name").orEmpty())
    val session: SessionType = detectSessionType(System.getenv())
    val desktop: DesktopFamily = detectDesktopFamily(System.getenv())
    val isSteamOs: Boolean = family == OsFamily.LINUX && isSteamOs(
        System.getenv(),
        runCatching { java.io.File("/etc/os-release").readText() }.getOrNull(),
    )

    val isWindows: Boolean get() = family == OsFamily.WINDOWS
    val isLinux: Boolean get() = family == OsFamily.LINUX
    val isMac: Boolean get() = family == OsFamily.MACOS
    val isWayland: Boolean get() = isLinux && session == SessionType.WAYLAND
    val isKde: Boolean get() = desktop == DesktopFamily.KDE
}
