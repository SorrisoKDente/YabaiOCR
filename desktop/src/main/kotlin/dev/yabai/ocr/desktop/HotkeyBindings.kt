package dev.yabai.ocr.desktop

/**
 * One shortcut as the portal sees it: a stable id, a description shown by the system dialog and the
 * combination the user configured in the app.
 */
internal data class HotkeyBinding(
    val id: String,
    val description: String,
    val configured: String,
) {
    /** XDG trigger for [configured], or null when the portal cannot represent that combination. */
    val preferredTrigger: String? get() = toXdgTrigger(configured)
}

/** Stable ids: they are persisted by the portal and must never change between releases. */
internal object HotkeyIds {
    const val FULL_SCREEN = "capture-fullscreen"
    const val REGION = "capture-region"
    const val QUICK_DICT = "quick-dict"
    const val HOVER_TOGGLE = "hover-toggle"
    const val HOVER_CYCLE = "dict-cycle"
}

internal enum class HotkeyAction { FULL_SCREEN, REGION, QUICK_DICT, HOVER_TOGGLE, HOVER_CYCLE, UNKNOWN }

internal fun hotkeyActionForId(id: String): HotkeyAction = when (id) {
    HotkeyIds.FULL_SCREEN -> HotkeyAction.FULL_SCREEN
    HotkeyIds.REGION -> HotkeyAction.REGION
    HotkeyIds.QUICK_DICT -> HotkeyAction.QUICK_DICT
    HotkeyIds.HOVER_TOGGLE -> HotkeyAction.HOVER_TOGGLE
    HotkeyIds.HOVER_CYCLE -> HotkeyAction.HOVER_CYCLE
    else -> HotkeyAction.UNKNOWN
}

internal fun hotkeyBindings(
    fullScreen: String,
    region: String,
    quickDict: String,
    hover: String,
    hoverCycle: String,
): List<HotkeyBinding> = listOf(
    HotkeyBinding(HotkeyIds.FULL_SCREEN, "Capturar a tela inteira", fullScreen),
    HotkeyBinding(HotkeyIds.REGION, "Recortar uma área da tela", region),
    HotkeyBinding(HotkeyIds.QUICK_DICT, "Abrir a busca rápida de dicionário", quickDict),
    HotkeyBinding(HotkeyIds.HOVER_TOGGLE, "Ligar/desligar o modo hover", hover),
    HotkeyBinding(HotkeyIds.HOVER_CYCLE, "Alternar o dicionário do hover", hoverCycle),
)

/** What the app does when a shortcut fires, regardless of which backend delivered it. */
internal data class HotkeyCallbacks(
    val onFullScreen: () -> Unit = {},
    val onRegion: () -> Unit = {},
    val onQuickDict: () -> Unit = {},
    val onHoverToggle: () -> Unit = {},
    val onHoverCycle: () -> Unit = {},
) {
    fun dispatch(id: String) {
        when (hotkeyActionForId(id)) {
            HotkeyAction.FULL_SCREEN -> onFullScreen()
            HotkeyAction.REGION -> onRegion()
            HotkeyAction.QUICK_DICT -> onQuickDict()
            HotkeyAction.HOVER_TOGGLE -> onHoverToggle()
            HotkeyAction.HOVER_CYCLE -> onHoverCycle()
            HotkeyAction.UNKNOWN -> Unit
        }
    }
}
