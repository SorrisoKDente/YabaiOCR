package dev.yabai.ocr.desktop

import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog

/**
 * Minimal surface of the hidden window that owns the custom tray menu.
 *
 * The JDK only breaks the popup's X11 grab on Wayland when the *invoker* window loses focus
 * (JDK-8307529). A hidden, unfocused 1x1 dialog never receives focus in the first place, so the
 * outside click never produces a focus-loss event. This abstraction keeps that fix testable without
 * a display.
 */
internal interface TrayMenuInvoker {
    var focusableWindowState: Boolean
    var autoRequestFocus: Boolean
    var isVisible: Boolean

    fun requestFocus()

    /** Registers the single focus-loss callback used to close the menu. */
    fun setFocusLostHandler(handler: () -> Unit)
}

internal fun showTrayMenuInvoker(
    invoker: TrayMenuInvoker,
    showPopup: () -> Unit,
    isMenuVisible: () -> Boolean,
    closeMenu: () -> Unit,
) {
    // Focus must be allowed before the dialog is mapped. Otherwise KWin keeps focus on the previous
    // window, no WindowEvent is delivered, and the popup survives every click outside it.
    invoker.focusableWindowState = true
    invoker.autoRequestFocus = true
    invoker.isVisible = true

    showPopup()

    // Register after mapping: some compositors briefly report focus loss while the popup is being
    // created, and that must not dismiss a menu the user just opened.
    invoker.setFocusLostHandler {
        if (isMenuVisible()) closeMenu()
    }
    invoker.requestFocus()
}

internal class JDialogTrayMenuInvoker(
    private val dialog: JDialog,
) : TrayMenuInvoker {
    override var focusableWindowState: Boolean
        get() = dialog.isFocusableWindow
        set(value) {
            dialog.setFocusableWindowState(value)
        }

    override var autoRequestFocus: Boolean
        get() = dialog.isAutoRequestFocus
        set(value) {
            dialog.isAutoRequestFocus = value
        }

    override var isVisible: Boolean
        get() = dialog.isVisible
        set(value) {
            dialog.isVisible = value
        }

    override fun requestFocus() {
        dialog.requestFocus()
    }

    override fun setFocusLostHandler(handler: () -> Unit) {
        dialog.addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent?) {
                handler()
            }
        })
    }
}
