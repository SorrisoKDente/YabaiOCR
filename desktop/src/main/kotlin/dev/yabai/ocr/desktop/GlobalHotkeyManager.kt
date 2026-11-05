package dev.yabai.ocr.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Global shortcuts through JNativeHook.
 *
 * The full-screen picker and the hand-drawn crop have **separate** shortcuts on purpose: they are
 * two actions with different results, and a hidden preference deciding which one a single key does
 * is a switch the user cannot see.
 */
class GlobalHotkeyManager(
    private val onFullScreenTriggered: () -> Unit,
    private val onRegionTriggered: () -> Unit,
    private val onQuickDictTriggered: () -> Unit,
    private val onHoverTriggered: () -> Unit,
    private val onHoverCycleTriggered: () -> Unit = {},
) : NativeKeyListener {

    @Volatile
    private var fullScreenSpec: HotkeySpec? = HotkeyMatcher.parse("Ctrl + Shift + O")

    @Volatile
    private var regionSpec: HotkeySpec? = HotkeyMatcher.parse("Ctrl + Shift + A")

    @Volatile
    private var quickDictSpec: HotkeySpec? = HotkeyMatcher.parse("Ctrl + Shift + D")

    @Volatile
    private var hoverSpec: HotkeySpec? = HotkeyMatcher.parse("Ctrl + Shift + H")

    @Volatile
    private var hoverCycleSpec: HotkeySpec? = HotkeyMatcher.parse("Ctrl + Shift + X")

    @Volatile
    private var started = false

    /** Replaces the shortcuts immediately; the listener reads these specs on the next key press. */
    fun updateHotkeys(
        fullScreen: String,
        region: String,
        quickDict: String,
        hover: String = "Ctrl + Shift + H",
        hoverCycle: String = "Ctrl + Shift + X"
    ) {
        fullScreenSpec = HotkeyMatcher.parse(fullScreen)
        regionSpec = HotkeyMatcher.parse(region)
        quickDictSpec = HotkeyMatcher.parse(quickDict)
        hoverSpec = HotkeyMatcher.parse(hover)
        hoverCycleSpec = HotkeyMatcher.parse(hoverCycle)
        if (fullScreenSpec == null) println("[Hotkey] atalho da tela inteira inválido: \"$fullScreen\"")
        if (regionSpec == null) println("[Hotkey] atalho do recorte inválido: \"$region\"")
        if (quickDictSpec == null) println("[Hotkey] atalho de busca inválido: \"$quickDict\"")
        if (hoverSpec == null) println("[Hotkey] atalho de hover inválido: \"$hover\"")
        if (hoverCycleSpec == null) println("[Hotkey] atalho de alternar dicionário no hover inválido: \"$hoverCycle\"")
    }

    /** Returns true when the native hook was installed. */
    fun start(): Boolean {
        if (started) return true
        return try {
            val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
            logger.level = Level.OFF
            logger.useParentHandlers = false

            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook()
            }
            GlobalScreen.addNativeKeyListener(this)
            started = true
            println("[Hotkey] gancho global de teclado ativo.")
            true
        } catch (e: Exception) {
            println("[Hotkey] não foi possível instalar o gancho global: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            GlobalScreen.removeNativeKeyListener(this)
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook()
            }
        }.onFailure { it.printStackTrace() }
    }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        val stroke = KeyStroke(
            keyCode = e.keyCode,
            keyText = NativeKeyEvent.getKeyText(e.keyCode),
            ctrl = (e.modifiers and NativeKeyEvent.CTRL_MASK) != 0,
            alt = (e.modifiers and NativeKeyEvent.ALT_MASK) != 0,
            shift = (e.modifiers and NativeKeyEvent.SHIFT_MASK) != 0,
            meta = (e.modifiers and NativeKeyEvent.META_MASK) != 0,
        )

        when {
            HotkeyMatcher.matches(fullScreenSpec, stroke) -> onFullScreenTriggered()
            HotkeyMatcher.matches(regionSpec, stroke) -> onRegionTriggered()
            HotkeyMatcher.matches(quickDictSpec, stroke) -> onQuickDictTriggered()
            HotkeyMatcher.matches(hoverSpec, stroke) -> onHoverTriggered()
            HotkeyMatcher.matches(hoverCycleSpec, stroke) -> onHoverCycleTriggered()
        }
    }
}
