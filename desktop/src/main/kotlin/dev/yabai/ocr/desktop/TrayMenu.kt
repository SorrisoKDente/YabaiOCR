package dev.yabai.ocr.desktop

/**
 * How the tray menu is shown.
 *
 * Windows keeps the custom Swing popup, which can be styled and positioned with the real pointer.
 * On Linux/X11 the desktop already owns a native menu (StatusNotifier/AppIndicator). Under
 * KDE Wayland that native popup is not delivered by AWT, so the custom menu is the only one the
 * user can actually open; it is anchored to the pointer when available and to the work-area corner
 * otherwise.
 */
internal enum class TrayMenuKind { NATIVE, CUSTOM }

internal fun trayMenuKindFor(family: OsFamily, session: SessionType): TrayMenuKind = when {
    family != OsFamily.LINUX -> TrayMenuKind.CUSTOM
    session == SessionType.WAYLAND -> TrayMenuKind.CUSTOM
    else -> TrayMenuKind.NATIVE
}

/**
 * Which mouse buttons open the custom tray menu. On Linux the left click is already the capture
 * action, so only the right button opens the menu; Windows keeps its historical both-buttons menu.
 */
internal fun trayMenuOpensOn(button: Int, isPopupTrigger: Boolean, showOnLeftClick: Boolean): Boolean =
    isPopupTrigger || button == java.awt.event.MouseEvent.BUTTON3 ||
        (showOnLeftClick && button == java.awt.event.MouseEvent.BUTTON1)

/**
 * A minimized start is only safe when the tray icon really exists. If it failed, the window must
 * open or the app would run with no visible way to reach it.
 */
internal fun shouldShowMainWindowOnStart(startMinimized: Boolean, trayReady: Boolean): Boolean =
    !startMinimized || !trayReady
