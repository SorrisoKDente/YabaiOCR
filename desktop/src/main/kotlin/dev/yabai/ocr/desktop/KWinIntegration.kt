package dev.yabai.ocr.desktop

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

    fun start(): Boolean {
        if (started) return true

        if (!service.start()) {
            statusMessage = "Não foi possível publicar o serviço D-Bus dev.yabai.Ocr."
            return false
        }

        if (!bridge.start(fallbackShortcuts())) {
            service.stop()
            statusMessage = "Não foi possível ativar o bridge do KWin."
            return false
        }

        started = true
        statusMessage = "Hover e atalhos do KWin ativos."
        return true
    }

    fun stop() {
        if (!started) return
        bridge.stop()
        service.stop()
        started = false
    }
}
