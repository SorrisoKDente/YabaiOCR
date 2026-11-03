package dev.yabai.ocr.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

class GlobalHotkeyManager(
    private val onTriggered: () -> Unit
) : NativeKeyListener {

    private var targetHotkey: String = "Ctrl + Shift + O"

    fun updateHotkey(hotkey: String) {
        targetHotkey = hotkey
    }

    fun start() {
        try {
            // Turn off noisy logging
            val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
            logger.level = Level.OFF
            logger.useParentHandlers = false

            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook()
            }
            GlobalScreen.addNativeKeyListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            GlobalScreen.removeNativeKeyListener(this)
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        val currentHotkey = targetHotkey
        val parts = currentHotkey.split(" + ").map { it.trim().lowercase() }
        if (parts.isEmpty()) return

        val mainKeyPart = parts.last()
        val requiresCtrl = parts.contains("ctrl")
        val requiresAlt = parts.contains("alt")
        val requiresShift = parts.contains("shift")
        val requiresMeta = parts.contains("meta")

        val ctrlPressed = (e.modifiers and NativeKeyEvent.CTRL_MASK) != 0
        val altPressed = (e.modifiers and NativeKeyEvent.ALT_MASK) != 0
        val shiftPressed = (e.modifiers and NativeKeyEvent.SHIFT_MASK) != 0
        val metaPressed = (e.modifiers and NativeKeyEvent.META_MASK) != 0

        if (requiresCtrl != ctrlPressed) return
        if (requiresAlt != altPressed) return
        if (requiresShift != shiftPressed) return
        if (requiresMeta != metaPressed) return

        val pressedKeyText = NativeKeyEvent.getKeyText(e.keyCode).lowercase()
        if (pressedKeyText == mainKeyPart || matchKeyName(e.keyCode, mainKeyPart)) {
            onTriggered()
        }
    }

    private fun matchKeyName(keyCode: Int, target: String): Boolean {
        return when (target) {
            "space", "espaço" -> keyCode == NativeKeyEvent.VC_SPACE
            "enter" -> keyCode == NativeKeyEvent.VC_ENTER
            "esc", "escape" -> keyCode == NativeKeyEvent.VC_ESCAPE
            else -> false
        }
    }
}
