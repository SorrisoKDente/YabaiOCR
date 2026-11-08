package dev.yabai.ocr.desktop

internal const val AWT_SCREENSHOT_METHOD_PROPERTY = "awt.robot.screenshotMethod"

/**
 * On KDE Wayland the JDK must be told to use the ScreenCast portal; the X11 fallback would only see
 * the XWayland island. GNOME is intentionally left alone: newer JDKs pick RemoteDesktop there, and
 * overriding it with ScreenCast would be a regression. Windows and X11 keep the historical path.
 */
internal fun screenshotMethodOverride(
    isLinux: Boolean,
    session: SessionType,
    desktop: DesktopFamily,
): String? = if (isLinux && session == SessionType.WAYLAND && desktop == DesktopFamily.KDE) {
    "dbusScreencast"
} else {
    null
}

/**
 * Applies the property before the first AWT/Toolkit use. Returns the chosen method for logging and
 * for the tests; a null return means "leave the JVM default alone".
 */
internal fun configureScreenshotMethod(
    isLinux: Boolean,
    session: SessionType,
    desktop: DesktopFamily,
    setProperty: (String, String) -> Unit = { key, value -> System.setProperty(key, value) },
): String? {
    val method = screenshotMethodOverride(isLinux, session, desktop) ?: return null
    setProperty(AWT_SCREENSHOT_METHOD_PROPERTY, method)
    return method
}
