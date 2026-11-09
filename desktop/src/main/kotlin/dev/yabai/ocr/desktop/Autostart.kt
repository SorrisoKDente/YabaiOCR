package dev.yabai.ocr.desktop

/**
 * "Start with the system" implemented per platform. The public facade is [StartupManager]; the
 * Windows registry code and the XDG entry live behind this interface so callers never branch on OS.
 */
internal interface Autostart {
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean

    /**
     * Makes the OS entry match the stored preference: writes it when it is missing or points at an
     * executable that no longer exists, removes it when the preference is off. Returns true when the
     * entry ended up in the requested state.
     *
     * The entry and the preference can drift apart (folder moved, app rebuilt, registry cleaned,
     * entry deleted by an older build) while the Windows "Startup apps" page keeps showing the app
     * as enabled. Without this, the app silently stops starting with the system.
     */
    fun reconcile(enabled: Boolean): Boolean {
        if (isEnabled() != enabled) setEnabled(enabled)
        return isEnabled() == enabled
    }
}

internal object NoopAutostart : Autostart {
    override fun setEnabled(enabled: Boolean) = Unit
    override fun isEnabled(): Boolean = false

    /** Nothing to manage on this platform, so the preference is as good as it gets. */
    override fun reconcile(enabled: Boolean): Boolean = true
}

internal fun autostartFor(
    family: OsFamily,
    windows: Autostart,
    linux: Autostart,
    other: Autostart,
): Autostart = when (family) {
    OsFamily.WINDOWS -> windows
    OsFamily.LINUX -> linux
    else -> other
}
