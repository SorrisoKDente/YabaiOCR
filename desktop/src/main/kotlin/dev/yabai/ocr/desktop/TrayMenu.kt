package dev.yabai.ocr.desktop

/**
 * How the tray menu is shown.
 *
 * Windows keeps the custom Swing popup, which can be styled and positioned with the real pointer.
 * On Linux the desktop already owns a native menu (StatusNotifier/AppIndicator); using it avoids
 * `MouseInfo`, which does not report a global pointer on Wayland.
 */
internal enum class TrayMenuKind { NATIVE, CUSTOM }

internal fun trayMenuKindFor(family: OsFamily, @Suppress("UNUSED_PARAMETER") session: SessionType): TrayMenuKind =
    if (family == OsFamily.LINUX) TrayMenuKind.NATIVE else TrayMenuKind.CUSTOM

/**
 * A minimized start is only safe when the tray icon really exists. If it failed, the window must
 * open or the app would run with no visible way to reach it.
 */
internal fun shouldShowMainWindowOnStart(startMinimized: Boolean, trayReady: Boolean): Boolean =
    !startMinimized || !trayReady
