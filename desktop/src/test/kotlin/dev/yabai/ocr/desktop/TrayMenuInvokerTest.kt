package dev.yabai.ocr.desktop

import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

class TrayMenuInvokerTest {

    private class FakeInvoker : TrayMenuInvoker {
        override var focusableWindowState = false
        override var autoRequestFocus = false
        override var isVisible = false

        var focusRequests = 0
        var popupShown = false
        var registeredFocusLost: (() -> Unit)? = null

        override fun requestFocus() {
            focusRequests++
        }

        override fun setFocusLostHandler(handler: () -> Unit) {
            registeredFocusLost = handler
        }
    }

    @Test
    fun `the real invoker dialog is configured as focusable and auto focused`() {
        assumeFalse(GraphicsEnvironment.isHeadless())

        val dialog = JDialog()
        try {
            val invoker = JDialogTrayMenuInvoker(dialog)
            invoker.focusableWindowState = true
            invoker.autoRequestFocus = true

            assertTrue(dialog.isFocusableWindow)
            assertTrue(dialog.isAutoRequestFocus)
        } finally {
            dialog.dispose()
        }
    }

    @Test
    fun `the invoker becomes focusable and focused before the menu can dismiss`() {
        val invoker = FakeInvoker()
        var closed = 0

        showTrayMenuInvoker(
            invoker = invoker,
            showPopup = { invoker.popupShown = true },
            isMenuVisible = { true },
            closeMenu = { closed++ },
        )

        assertTrue(invoker.focusableWindowState)
        assertTrue(invoker.autoRequestFocus)
        assertTrue(invoker.isVisible)
        assertTrue(invoker.popupShown)
        assertEquals(1, invoker.focusRequests)
        assertNotNull("the focus-loss dismissal must be registered", invoker.registeredFocusLost)

        invoker.registeredFocusLost!!.invoke()
        assertEquals(1, closed)
    }

    @Test
    fun `focus loss after the menu already closed does not close it twice`() {
        val invoker = FakeInvoker()
        var menuVisible = true
        var closed = 0

        showTrayMenuInvoker(
            invoker = invoker,
            showPopup = {},
            isMenuVisible = { menuVisible },
            closeMenu = {
                closed++
                menuVisible = false
            },
        )

        invoker.registeredFocusLost!!.invoke()
        invoker.registeredFocusLost!!.invoke()

        assertEquals(1, closed)
    }

    @Test
    fun `a non visible menu ignores a stale focus loss`() {
        val invoker = FakeInvoker()
        var closed = 0

        showTrayMenuInvoker(
            invoker = invoker,
            showPopup = {},
            isMenuVisible = { false },
            closeMenu = { closed++ },
        )
        invoker.registeredFocusLost!!.invoke()

        assertEquals(0, closed)
        assertNotNull(invoker.registeredFocusLost)
    }
}
