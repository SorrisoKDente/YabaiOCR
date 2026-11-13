package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings

/**
 * Brings up the Wayland hover path in the right order: D-Bus object first, then the KWin script.
 *
 * If either step fails the integration rolls back and reports it. Keeping both halves together is
 * what makes the failure visible: a script without the bus name silently sends the pointer nowhere.
 */
internal class KWinIntegration(
    private val service: YabaiDbusService,
    private val bridge: KWinHoverBridge,
    private val fallbackShortcuts: () -> List<HotkeyBinding>,
) {
    private var started = false

    var statusMessage: String = ""
        private set

    val isRunning: Boolean
        get() = started && bridge.running

    fun start(): Boolean {
        if (started) return true

        if (!service.start()) {
            statusMessage = DesktopStrings.kwinDbusFailed(
                service.lastError?.takeIf { it.isNotBlank() },
            )
            return false
        }

        if (!bridge.start(fallbackShortcuts())) {
            service.stop()
            statusMessage = DesktopStrings.kwinBridgeFailed(
                bridge.lastError?.takeIf { it.isNotBlank() },
            )
            return false
        }

        started = true
        statusMessage = DesktopStrings.kwinIntegrationActive
        return true
    }

    /** Reapplies the fallback shortcuts after the portal failed or after a shortcut edit. */
    fun updateShortcuts(bindings: List<HotkeyBinding>): Boolean {
        if (!started) return false
        if (bridge.updateShortcuts(bindings)) {
            statusMessage = DesktopStrings.kwinIntegrationActive
            return true
        }

        statusMessage = DesktopStrings.kwinUpdateFailed(
            bridge.lastError?.takeIf { it.isNotBlank() },
        )
        return false
    }

    fun stop() {
        if (!started) return
        bridge.stop()
        service.stop()
        started = false
    }
}
