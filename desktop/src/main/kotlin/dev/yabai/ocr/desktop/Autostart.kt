package dev.yabai.ocr.desktop

/**
 * "Start with the system" implemented per platform. The public facade is [StartupManager]; the
 * Windows registry code and the XDG entry live behind this interface so callers never branch on OS.
 */
internal interface Autostart {
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}

internal object NoopAutostart : Autostart {
    override fun setEnabled(enabled: Boolean) = Unit
    override fun isEnabled(): Boolean = false
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
