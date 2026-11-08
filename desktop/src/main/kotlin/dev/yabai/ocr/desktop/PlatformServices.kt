package dev.yabai.ocr.desktop

/**
 * Single place that picks the platform backends. New integrations (capture, hotkeys, hover) plug in
 * here as they are implemented; the UI and the rest of the app never read `os.name` themselves.
 */
internal object PlatformServices {
    val autostart: Autostart = autostartFor(
        family = DesktopPlatform.family,
        windows = WindowsAutostart,
        linux = LinuxAutostart(),
        other = NoopAutostart,
    )
}
