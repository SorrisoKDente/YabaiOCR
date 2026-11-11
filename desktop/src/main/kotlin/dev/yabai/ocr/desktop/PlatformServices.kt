package dev.yabai.ocr.desktop

/**
 * Single place that picks the platform backends. New integrations (hotkeys, hover) plug in here as
 * they are implemented; the UI and the rest of the app never read `os.name` themselves.
 */
internal fun captureBackendFor(
    family: OsFamily,
    session: SessionType,
    robot: ScreenCaptureBackend,
    screencast: ScreenCaptureBackend,
    desktop: DesktopFamily = DesktopFamily.UNKNOWN,
    kdeScreenshot: ScreenCaptureBackend = screencast,
): ScreenCaptureBackend = when {
    // KDE has a non-interactive Screenshot portal that asks for a persistent permission once.
    // Robot's ScreenCast path would reopen the "choose a screen" dialog on every capture because
    // JDK-8396405 rejects KDE's non-UUID restore token.
    family == OsFamily.LINUX && session == SessionType.WAYLAND &&
        desktop == DesktopFamily.KDE -> kdeScreenshot
    family == OsFamily.LINUX && session == SessionType.WAYLAND -> screencast
    else -> robot
}

internal object PlatformServices {
    val autostart: Autostart = autostartFor(
        family = DesktopPlatform.family,
        windows = WindowsAutostart(),
        linux = LinuxAutostart(),
        other = NoopAutostart,
    )

    val capture: ScreenCaptureBackend = captureBackendFor(
        family = DesktopPlatform.family,
        session = DesktopPlatform.session,
        robot = RobotScreenCapture,
        screencast = ScreencastScreenCapture(),
        desktop = DesktopPlatform.desktop,
        kdeScreenshot = PortalScreenshotCapture(),
    )
}
