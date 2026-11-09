package dev.yabai.ocr.desktop

/**
 * Where the hover pointer comes from.
 *
 * Windows and X11 can ask `MouseInfo` directly. On Wayland an ordinary client cannot read the global
 * pointer, so KDE exposes it through a KWin script; compositors without that bridge have no hover at
 * all and must say so instead of polling a pointer that never moves.
 */
internal enum class HoverBackendKind { AWT, KWIN, UNSUPPORTED }

internal fun hoverBackendKindFor(
    family: OsFamily,
    session: SessionType,
    desktop: DesktopFamily,
): HoverBackendKind = when {
    family == OsFamily.LINUX && session == SessionType.WAYLAND ->
        if (desktop == DesktopFamily.KDE) HoverBackendKind.KWIN else HoverBackendKind.UNSUPPORTED
    else -> HoverBackendKind.AWT
}

internal fun hoverBackendLabel(kind: HoverBackendKind): String = when (kind) {
    HoverBackendKind.AWT -> "Hover usa o ponteiro do sistema."
    HoverBackendKind.KWIN -> "Hover usa o ponteiro global do KWin."
    HoverBackendKind.UNSUPPORTED ->
        "Hover indisponível nesta sessão: Wayland sem KWin. Use a captura da tela inteira."
}

internal fun hoverCaptureProvider(
    kind: HoverBackendKind,
    screencast: (java.awt.Rectangle) -> java.awt.image.BufferedImage,
): ((java.awt.Rectangle) -> java.awt.image.BufferedImage)? =
    if (kind == HoverBackendKind.KWIN) screencast else null
