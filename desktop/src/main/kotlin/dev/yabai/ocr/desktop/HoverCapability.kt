package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * Where the hover pointer comes from.
 *
 * Windows and X11 can ask `MouseInfo` directly. On Wayland an ordinary client cannot read the global
 * pointer, so KDE exposes it through a KWin script and GNOME through a Shell extension; compositors
 * without either bridge have no hover at all and must say so instead of polling a pointer that never
 * moves.
 */
internal enum class HoverBackendKind { AWT, KWIN, GNOME, UNSUPPORTED }

/**
 * Why the hover cannot start when the dictionaries installed are [titles], or null when it can.
 *
 * The hover answers with dictionaries: with none installed it had nothing to say and came up empty,
 * which reads as a broken feature rather than as a missing download. The refusal is a notice, not a
 * silent no-op, and the hover stays off until there is a dictionary to open (R57).
 */
internal fun hoverDictionaryRefusal(titles: List<String>): String? =
    if (titles.isEmpty()) DesktopStrings.hoverNeedsDictionary else null

internal fun hoverBackendKindFor(
    family: OsFamily,
    session: SessionType,
    desktop: DesktopFamily,
): HoverBackendKind = when {
    family == OsFamily.LINUX && session == SessionType.WAYLAND -> when (desktop) {
        DesktopFamily.KDE -> HoverBackendKind.KWIN
        DesktopFamily.GNOME -> HoverBackendKind.GNOME
        else -> HoverBackendKind.UNSUPPORTED
    }
    else -> HoverBackendKind.AWT
}

internal fun hoverBackendLabel(kind: HoverBackendKind): String = when (kind) {
    HoverBackendKind.AWT -> "Hover usa o ponteiro do sistema."
    HoverBackendKind.KWIN -> "Hover usa o ponteiro global do KWin."
    HoverBackendKind.GNOME -> "Hover usa o ponteiro global do GNOME Shell."
    HoverBackendKind.UNSUPPORTED ->
        "Hover indisponível nesta sessão: Wayland sem ponteiro global. Use a captura da tela inteira."
}

/** KWin and GNOME deliver the pointer through a compositor bridge; AWT needs none. */
internal val HoverBackendKind.usesCompositorBridge: Boolean
    get() = this == HoverBackendKind.KWIN || this == HoverBackendKind.GNOME

/**
 * Wayland backends replace `Robot` with the screencast path: on Wayland AWT can only see XWayland
 * windows, so the crop has to come from the portal.
 */
internal fun hoverCaptureProvider(
    kind: HoverBackendKind,
    screencast: (java.awt.Rectangle) -> java.awt.image.BufferedImage,
): ((java.awt.Rectangle) -> java.awt.image.BufferedImage)? =
    if (kind.usesCompositorBridge) screencast else null
