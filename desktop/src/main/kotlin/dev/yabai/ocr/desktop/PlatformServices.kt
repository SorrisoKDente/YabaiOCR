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
    kdeCapture: ScreenCaptureBackend = screencast,
    gnomeCapture: ScreenCaptureBackend = screencast,
): ScreenCaptureBackend = when {
    // KDE's own ScreenShot2 interface is non-interactive. The Screenshot portal stays behind it as a
    // compatibility fallback because KDE presents a permission dialog for every portal capture.
    family == OsFamily.LINUX && session == SessionType.WAYLAND &&
        desktop == DesktopFamily.KDE -> kdeCapture
    // GNOME never reuses the JDK's ScreenCast restore token: every capture opens a new session, so
    // the Shell shows its sharing indicator and asks for the screen again — seconds per hover, and
    // no text at all. The Screenshot portal is the one-shot path: no session, no dialog, ~130 ms.
    family == OsFamily.LINUX && session == SessionType.WAYLAND &&
        desktop == DesktopFamily.GNOME -> gnomeCapture
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
        kdeCapture = KdeWaylandCapture(),
        gnomeCapture = PortalScreenshotCapture(),
    )
}
